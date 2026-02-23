package com.babelmoth.rotp_ata.event;

import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;
import com.github.standobyte.jojo.util.mc.MCUtil;
import com.github.standobyte.jojo.init.ModStatusEffects;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.stream.Collectors;

import com.babelmoth.rotp_ata.AddonMain;

import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.TickEvent;

import java.util.UUID;

/**
 * Event handler for Ashes to Ashes mod.
 * Handles damage interception, energy absorption, and entity interactions.
 */
@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID)
public class AshesToAshesEventHandler {

    // Recursion guard to prevent infinite loops (Shield -> Moth hurt -> Owner hurt -> Shield)
    private static final ThreadLocal<Boolean> IS_PROCESSING_SHIELD = ThreadLocal.withInitial(() -> false);

    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("c0d8b2e1-4b1a-4c8d-9e1f-7a4b2c3d5e6f");
    private static final UUID ATTACK_DAMAGE_MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7a8b-9c0d-e1f2a3b4c5d6");
    private static final UUID ATTACK_SPEED_MODIFIER_UUID = UUID.fromString("f6e5d4c3-b2a1-0987-6543-21fedcba0987");
    
    @SubscribeEvent
    public static void attachCapabilities(net.minecraftforge.event.AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof LivingEntity) {
            event.addCapability(
                new net.minecraft.util.ResourceLocation(AddonMain.MOD_ID, "moth_pool"), 
                new com.babelmoth.rotp_ata.capability.MothPoolProvider()
            );
            event.addCapability(
                new net.minecraft.util.ResourceLocation(AddonMain.MOD_ID, "spear_stuck"),
                new com.babelmoth.rotp_ata.capability.SpearStuckProvider()
            );
            event.addCapability(
                new net.minecraft.util.ResourceLocation(AddonMain.MOD_ID, "spear_thorn"),
                new com.babelmoth.rotp_ata.capability.SpearThornProvider()
            );
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getPlayer() instanceof net.minecraft.entity.player.ServerPlayerEntity && event.getTarget() instanceof LivingEntity) {
            LivingEntity target = (LivingEntity) event.getTarget();
            net.minecraft.entity.player.ServerPlayerEntity player = (net.minecraft.entity.player.ServerPlayerEntity) event.getPlayer();
            target.getCapability(com.babelmoth.rotp_ata.capability.SpearStuckProvider.SPEAR_STUCK_CAPABILITY).ifPresent(cap -> {
                int count = cap.getSpearCount();
                if (count > 0) {
                    com.babelmoth.rotp_ata.networking.AshesToAshesPacketHandler.CHANNEL.send(
                            net.minecraftforge.fml.network.PacketDistributor.PLAYER.with(() -> player),
                            new com.babelmoth.rotp_ata.networking.SpearStuckSyncPacket(target.getId(), count));
                }
            });
            target.getCapability(com.babelmoth.rotp_ata.capability.SpearThornProvider.SPEAR_THORN_CAPABILITY).ifPresent(cap -> {
                if (cap.hasSpear()) {
                    com.babelmoth.rotp_ata.networking.AshesToAshesPacketHandler.CHANNEL.send(
                            net.minecraftforge.fml.network.PacketDistributor.PLAYER.with(() -> player),
                            new com.babelmoth.rotp_ata.networking.SpearThornSyncPacket(
                                    target.getId(), cap.getThornCount(), cap.getDamageDealt(),
                                    cap.getDetachThreshold(), cap.hasSpear()));
                }
            });
        }
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.world.isClientSide || !(event.world instanceof net.minecraft.world.server.ServerWorld)) return;
        com.babelmoth.rotp_ata.action.AshesToAshesMothSwarmAttack.tickPendingSwarmAttacks((net.minecraft.world.server.ServerWorld) event.world);
    }

    @SubscribeEvent
    public static void onLivingUpdate(LivingUpdateEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (entity.level.isClientSide) return;
        
        // Skip for FossilMoth itself
        if (entity instanceof FossilMothEntity) return;

        List<FossilMothEntity> attachedMoths = MothQueryUtil.getAttachedMoths(entity, AshesToAshesConstants.QUERY_RADIUS_ATTACHMENT);
        
        // Filter out full energy moths
        int validMothCount = 0;
        for (FossilMothEntity moth : attachedMoths) {
            if (moth.getTotalEnergy() < moth.getMaxEnergy()) {
                validMothCount++;
            }
        }
        
        // Apply/Update Debuffs & Buffs
        boolean isOwner = false;
        IStandPower ownerPower = null;
        if (entity instanceof PlayerEntity) {
            ownerPower = IStandPower.getStandPowerOptional((PlayerEntity)entity).resolve().orElse(null);
            isOwner = attachedMoths.stream().anyMatch(moth -> moth.getOwner() != null && moth.getOwner().is(entity));
        }

        if (isOwner) {
            // Count moths that actually have energy to provide buffs
            int energyMothCount = 0;
            for (FossilMothEntity moth : attachedMoths) {
                if (moth.getKineticEnergy() > 0) {
                    energyMothCount++;
                }
            }
            
            if (energyMothCount > 0) {
                // Apply Buffs for Owner
                // Note: Attack damage buff removed to not boost Momentum Strike
                updateDebuff(entity, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER_UUID, "Moth Speed Buff", 0.10 * energyMothCount, AttributeModifier.Operation.MULTIPLY_TOTAL);
                updateDebuff(entity, Attributes.ATTACK_SPEED, ATTACK_SPEED_MODIFIER_UUID, "Moth Attack Speed Buff", 0.10 * energyMothCount, AttributeModifier.Operation.MULTIPLY_TOTAL);
                
                // Stand Touch Effect
                if (!entity.hasEffect(ModStatusEffects.INTEGRATED_STAND.get())) {
                    entity.addEffect(new net.minecraft.potion.EffectInstance(ModStatusEffects.INTEGRATED_STAND.get(), 40, 0, false, false, true));
                }
                
                // --- Self-Adhesion Maintenance Cost ---
                // 1. Stamina Drain
                if (ownerPower != null && !((PlayerEntity)entity).isCreative()) {
                    ownerPower.consumeStamina(0.1F * energyMothCount); // 0.1 per moth per tick
                }
                
                // 2. Kinetic Energy Drain (Instead of Siphon)
                if (entity.tickCount % 20 == 0) { // Every second
                    for (FossilMothEntity moth : attachedMoths) {
                        if (moth.getKineticEnergy() > 0) {
                            moth.setKineticEnergy(Math.max(0, moth.getKineticEnergy() - 1));
                        }
                    }
                }
            } else {
                // No energy left in any moths -> Ability fails
                clearModifiers(entity);
            }
        } else {
            // Movement debuff on enemies: cap at -1.0 (5+ moths = immobile)
            double speedDebuff = Math.max(-1.0, -0.20 * validMothCount);
            updateDebuff(entity, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER_UUID, "Moth Speed Debuff", speedDebuff, AttributeModifier.Operation.MULTIPLY_TOTAL);
            updateDebuff(entity, Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_MODIFIER_UUID, "Moth Damage Buff", -1.0 * validMothCount, AttributeModifier.Operation.ADDITION);
            updateDebuff(entity, Attributes.ATTACK_SPEED, ATTACK_SPEED_MODIFIER_UUID, "Moth Attack Speed Debuff", -0.10 * validMothCount, AttributeModifier.Operation.MULTIPLY_TOTAL);
            
            if (validMothCount >= AshesToAshesConstants.OXYGEN_DEPLETION_THRESHOLD) {
                int currentAir = entity.getAirSupply();
                if (currentAir > -20) {
                    entity.setAirSupply(currentAir - 2);
                }
            }

            // Kinetic absorption from motion: per-entity budget every 20 ticks, distributed to moths with room; full moths recall
            double speedSqr = entity.getDeltaMovement().lengthSqr();
            if (speedSqr > 0.0001 && entity.tickCount % 20 == 0) {
                final int kineticBudgetPerPeriod = 2; // Max absorption per entity per 20 ticks
                java.util.List<FossilMothEntity> withRoom = new java.util.ArrayList<>();
                for (FossilMothEntity moth : attachedMoths) {
                    if (moth.getKineticEnergy() < moth.getMaxEnergy()) withRoom.add(moth);
                }
                for (int b = 0; b < kineticBudgetPerPeriod && !withRoom.isEmpty(); b++) {
                    withRoom.sort(java.util.Comparator.comparingInt(FossilMothEntity::getKineticEnergy));
                    FossilMothEntity moth = withRoom.get(0);
                    int next = Math.min(moth.getKineticEnergy() + 1, moth.getMaxEnergy());
                    moth.setKineticEnergy(next);
                    if (next >= moth.getMaxEnergy()) {
                        withRoom.remove(moth);
                        moth.recall();
                    }
                }
                // Any moth that reached max kinetic (from any source) recalls
                java.util.List<FossilMothEntity> attachedAgain = MothQueryUtil.getAttachedMoths(entity, AshesToAshesConstants.QUERY_RADIUS_ATTACHMENT);
                for (FossilMothEntity moth : new java.util.ArrayList<>(attachedAgain)) {
                    if (moth.getKineticEnergy() >= moth.getMaxEnergy() && moth.isAlive()) {
                        moth.recall();
                    }
                }
            }
        }
    }
    
    private static void clearModifiers(LivingEntity entity) {
        updateDebuff(entity, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER_UUID, "", 0, null);
        updateDebuff(entity, Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_MODIFIER_UUID, "", 0, null);
        updateDebuff(entity, Attributes.ATTACK_SPEED, ATTACK_SPEED_MODIFIER_UUID, "", 0, null);
    }

    private static void updateDebuff(LivingEntity entity, net.minecraft.entity.ai.attributes.Attribute attribute, UUID uuid, String name, double amount, AttributeModifier.Operation operation) {
        ModifiableAttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            AttributeModifier modifier = instance.getModifier(uuid);
            if (amount != 0) {
                if (modifier != null) {
                    // Update if changed
                    if (Math.abs(modifier.getAmount() - amount) > 0.001) {
                        instance.removeModifier(uuid);
                        instance.addTransientModifier(new AttributeModifier(uuid, name, amount, operation));
                    }
                } else {
                    // Add new
                    instance.addTransientModifier(new AttributeModifier(uuid, name, amount, operation));
                }
            } else {
                // Remove if amount is 0 (no moths)
                if (modifier != null) {
                    instance.removeModifier(uuid);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        LivingEntity entity = event.getEntityLiving();
        
        // 1. Attached moths on player (debuff/buff) - runs on both sides for sync
        List<FossilMothEntity> attachedMoths = MothQueryUtil.getAttachedMoths(entity, AshesToAshesConstants.QUERY_RADIUS_ATTACHMENT);
        
        int validDebuffCount = 0;
        int validBuffCount = 0;
        boolean isOwner = event.getPlayer() != null && attachedMoths.stream().anyMatch(moth -> moth.getOwner() != null && moth.getOwner().is(event.getPlayer()));

        for (FossilMothEntity moth : attachedMoths) {
            if (moth.getTotalEnergy() < moth.getMaxEnergy()) {
                if (isOwner) {
                    validBuffCount++;
                } else {
                    validDebuffCount++;
                }
            }
        }
        
        float modifier1 = 1.0f;
        if (validDebuffCount > 0) {
            modifier1 -= 0.10f * validDebuffCount;
        }
        if (validBuffCount > 0) {
            modifier1 += 0.10f * validBuffCount;
        }
        
        // 2. Protection Logic (Block attached moths) - runs on both sides for sync
        int protectingCount = getProtectingMothCount(event.getEntity().level, event.getPos());
        float modifier2 = 1.0f;
        
        if (protectingCount > 0) {
            if (protectingCount >= 5) {
                event.setCanceled(true); // Effectively unbreakable
                modifier2 = 0f;
            } else {
                modifier2 = Math.max(0.0f, 1.0f - (0.2f * protectingCount));
            }
        }
        
        // Combine modifiers
        if (modifier1 != 1.0f || modifier2 != 1.0f) {
            event.setNewSpeed(event.getOriginalSpeed() * modifier1 * modifier2);
        }

        // 3. FrozenBarrierBlock mining -> server-only: 每tick累加固定破坏进度
        if (!entity.level.isClientSide && event.getPos() != null) {
            net.minecraft.block.BlockState miningState = entity.level.getBlockState(event.getPos());
            if (miningState.getBlock() instanceof com.babelmoth.rotp_ata.block.FrozenBarrierBlock) {
                net.minecraft.tileentity.TileEntity te = entity.level.getBlockEntity(event.getPos());
                if (te instanceof com.babelmoth.rotp_ata.block.FrozenBarrierBlockEntity) {
                    com.babelmoth.rotp_ata.block.FrozenBarrierBlockEntity barrier =
                            (com.babelmoth.rotp_ata.block.FrozenBarrierBlockEntity) te;
                    barrier.addBreakProgress(com.babelmoth.rotp_ata.block.FrozenBarrierBlockEntity.PROGRESS_PER_TICK);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        // 0. FrozenBarrierBlock: vanilla永远不会破坏，但以防万一仍取消
        net.minecraft.block.BlockState breakState = event.getWorld().getBlockState(event.getPos());
        if (breakState.getBlock() instanceof com.babelmoth.rotp_ata.block.FrozenBarrierBlock) {
            event.setCanceled(true);
            return;
        }

        // 1. Protection Logic
        if (getProtectingMothCount(event.getWorld(), event.getPos()) > 0) {
            event.setCanceled(true);
            return; 
        }

        // 2. Siphon Logic
        LivingEntity entity = event.getPlayer();
        if (entity == null || entity.level.isClientSide) return;
        
        List<FossilMothEntity> attachedMoths = MothQueryUtil.getAttachedMoths(entity, AshesToAshesConstants.QUERY_RADIUS_ATTACHMENT);
        
        if (!attachedMoths.isEmpty()) {
            for (FossilMothEntity moth : attachedMoths) {
                 if (moth.getTotalEnergy() < moth.getMaxEnergy()) {
                     moth.setKineticEnergy(moth.getKineticEnergy() + 2);
                 }
            }
        }
    }

    // Run at HIGHEST priority to intercept before RotP stand damage immunity
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntityLiving();
        DamageSource source = event.getSource();
        
        // Attacker Siphon Logic: If attacker has attached moths, they gain energy
        // EXCEPTION: Self-adhered moths (owner == attacker) should NOT absorb owner's attack energy
        if (source.getEntity() instanceof LivingEntity) {
            LivingEntity attacker = (LivingEntity) source.getEntity();
            if (!attacker.level.isClientSide && !(attacker instanceof FossilMothEntity)) {
                 List<FossilMothEntity> attackerMoths = MothQueryUtil.getAttachedMoths(attacker, AshesToAshesConstants.QUERY_RADIUS_ATTACHMENT);
                 attackerMoths.removeIf(m -> m.getOwner() == attacker);
                for (FossilMothEntity moth : attackerMoths) {
                    if (moth.getTotalEnergy() < moth.getMaxEnergy()) {
                         moth.setKineticEnergy(moth.getKineticEnergy() + 1);
                    }
                }
            }
        }
        
        // Filter: Allow Physical Attacks AND Fall Damage for shield absorption
        // Also ALLOW Hamon damage for moth Hamon absorption
        boolean isFall = source == DamageSource.FALL;
        boolean isHamon = source.getMsgId() != null && source.getMsgId().startsWith("hamon");
        boolean isSendoOverdrive = source.getDirectEntity() instanceof com.github.standobyte.jojo.entity.HamonSendoOverdriveEntity;
        
        if (!isFall && !isHamon && !isSendoOverdrive && (source.isBypassArmor() || source.isMagic() || source == DamageSource.OUT_OF_WORLD)) {
            return;
        }
        
        // Friendly Fire / Self Damage check
        // Don't block attacks coming FROM the victim (e.g. self-inflicted or reflected?)
        // Don't block victim's own stand? (Source entity is Stand).
        if (source.getEntity() != null) {
            if (source.getEntity() == victim) return;
            // Check if source is victim's stand? 
            // Hard to check universally, but safe to assume source.getEntity() != victim covers most.
        }
        
        // Direct hits on the FossilMothEntity
        if (victim instanceof FossilMothEntity) {
             FossilMothEntity moth = (FossilMothEntity) victim;
             LivingEntity owner = moth.getOwner();
             
             // Calculate remaining space based on TOTAL energy (kinetic + hamon combined)
             int totalEnergy = moth.getTotalEnergy();
             int maxEnergy = moth.getMaxEnergy();
             int totalSpace = maxEnergy - totalEnergy;
             
             // Hamon damage absorption (including Sendo Overdrive)
             if (isHamon || isSendoOverdrive) {
                 int damage = (int) Math.ceil(event.getAmount());
                 
                 if (totalSpace > 0) {
                     int absorbed = Math.min(damage, totalSpace);
                     moth.setHamonEnergy(moth.getHamonEnergy() + absorbed);
                 }
                 
                 event.setCanceled(true);
                 return;
             }
             
             // Physical damage absorption (Kinetic Defense)
             if (!source.isBypassArmor() && !source.isMagic() && source != DamageSource.OUT_OF_WORLD) {
                  // Absorb logic - use total space for cap
                  int damage = (int) Math.ceil(event.getAmount());
                  int absorbed = 0;
                  
                  if (totalSpace > 0) {
                      absorbed = Math.min(damage, totalSpace);
                      moth.setKineticEnergy(moth.getKineticEnergy() + absorbed);
                      damage -= absorbed;
                  }
                  
                  if (damage > 0) {
                      // Overflow to owner
                      if (owner != null) {
                           float ratio = 2.0f / 50.0f;
                           float damageToOwner = damage * ratio;
                           
                           // Avoid infinite loop if owner hurts moth
                           if (damageToOwner > 0 && source.getEntity() != owner) {
                               owner.hurt(source, damageToOwner);
                           }
                      }
                      
                      // Also apply damage to moth (reduced by armor)
                      // We must MANUALLY update health because we are cancelling the event
                      // Using CombatRules to calculate damage after armor
                      float armor = (float) moth.getArmorValue();
                      float toughness = (float) moth.getAttributeValue(net.minecraft.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
                      float damagedReduced = net.minecraft.util.CombatRules.getDamageAfterAbsorb(damage, armor, toughness);
                      
                      if (damagedReduced > 0) {
                          moth.setHealth(moth.getHealth() - damagedReduced);
                          // Play hurt sound/effect?
                          moth.playHurtSoundEffect(source);
                      }
                  }
                  event.setCanceled(true);
                  return;
             }
        }
        
        List<FossilMothEntity> selfAdheredMoths = MothQueryUtil.getAttachedMoths(victim, 3.0);
        selfAdheredMoths.removeIf(m -> m.getOwner() != victim);
        
        if (!selfAdheredMoths.isEmpty()) {
            int totalDamage = (int) Math.ceil(event.getAmount());
            int mothCount = selfAdheredMoths.size();
            
            // Distribute damage evenly with remainder going to first moths
            int baseEnergyPerMoth = totalDamage / mothCount;
            int remainder = totalDamage % mothCount;
            
            // 1. Convert damage to kinetic energy, distributed evenly with remainder
            int mothIndex = 0;
            for (FossilMothEntity moth : selfAdheredMoths) {
                int currentEnergy = moth.getKineticEnergy();
                int maxEnergy = moth.getMaxEnergy();
                // First 'remainder' moths get +1 extra energy
                int energyToAdd = baseEnergyPerMoth + (mothIndex < remainder ? 1 : 0);
                int newEnergy = Math.min(currentEnergy + energyToAdd, maxEnergy);
                moth.setKineticEnergy(newEnergy);
                mothIndex++;
            }
            
            // 2. If damage > 15, excess * 0.5 dealt as actual damage to moths
            if (totalDamage > 15) {
                float excessDamage = (totalDamage - 15) * 0.5f;
                float damagePerMoth = excessDamage / mothCount;
                for (FossilMothEntity moth : selfAdheredMoths) {
                    moth.hurt(DamageSource.GENERIC, damagePerMoth);
                }
            }
            // Moths that reached max kinetic recall
            for (FossilMothEntity moth : new java.util.ArrayList<>(selfAdheredMoths)) {
                if (moth.getKineticEnergy() >= moth.getMaxEnergy() && moth.isAlive()) {
                    moth.recall();
                }
            }
            
            event.setCanceled(true);
            return;
        }
        
        List<FossilMothEntity> protectors = MCUtil.entitiesAround(
            FossilMothEntity.class, victim, AshesToAshesConstants.QUERY_RADIUS_SHIELD, false,
            moth -> moth.isAlive() && moth.getShieldTarget() != null && moth.getShieldTarget().is(victim)
        );
        
        if (protectors.isEmpty()) return;
        
        // Recursion Guard: If we are already processing shield logic (e.g. from moth hurt -> owner hurt),
        // do not run shield logic again for the owner.
        if (IS_PROCESSING_SHIELD.get()) {
            return;
        }
        
        try {
            IS_PROCESSING_SHIELD.set(true);
            
            LivingEntity owner = protectors.get(0).getOwner();
            
            // Friendly Fire Filter
            if (owner != null && source.getEntity() != null) {
                Entity attacker = source.getEntity();
                // If victim is owner, don't block owner's own damage (suicide/AOE)
                if (attacker == owner && victim == owner) return;
                
                // Ignore damage from owner's stand
                if (attacker instanceof com.github.standobyte.jojo.entity.stand.StandEntity) {
                    com.github.standobyte.jojo.entity.stand.StandEntity stand = (com.github.standobyte.jojo.entity.stand.StandEntity) attacker;
                    if (stand.getUser() == owner && victim == owner) return;
                }
                // Ignore damage from owner's moths
                if (attacker instanceof FossilMothEntity) {
                    FossilMothEntity attackerMoth = (FossilMothEntity) attacker;
                    if (attackerMoth.getOwner() == owner && victim == owner) return;
                }
            }
            
            // Hamon Backlash: If attacker is Undead and Moths have Hamon Energy
            if (source.getEntity() instanceof LivingEntity) {
                LivingEntity attacker = (LivingEntity) source.getEntity();
                if (attacker.getMobType() == net.minecraft.entity.CreatureAttribute.UNDEAD) {
                    float totalBacklash = 0;
                    boolean trigger = false;
                    for (FossilMothEntity moth : protectors) {
                        int hamon = moth.getHamonEnergy();
                        if (hamon > 0) {
                            totalBacklash += 1.5f; // Each moth adds damage
                            moth.setHamonEnergy(Math.max(0, hamon - 2)); // Consume 2 energy
                            trigger = true;
                        }
                    }
                    if (trigger) {
                        // Apply backlash damage to undead attacker
                        attacker.hurt(new net.minecraft.util.DamageSource("hamon_backlash").setMagic(), totalBacklash + 2.0f);
                        // Add some Hamon-like visual effects
                        if (attacker.level instanceof net.minecraft.world.server.ServerWorld) {
                            ((net.minecraft.world.server.ServerWorld)attacker.level).sendParticles(net.minecraft.particles.ParticleTypes.INSTANT_EFFECT, 
                                attacker.getX(), attacker.getY() + 1, attacker.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
                        }
                    }
                }
            }
            
            // Active Defense for Owner: Consume Energy instead of Harvesting
            // Passive Defense for Others: Harvest Energy
            int totalDamage = (int) Math.ceil(event.getAmount());
            int mothCount = protectors.size();
            int perMoth = totalDamage / mothCount;
            int remainder = totalDamage % mothCount;
            int damageRemaining = totalDamage;
            boolean isVictimOwner = victim instanceof PlayerEntity && owner != null && owner.is(victim);
            
            for (int i = 0; i < protectors.size(); i++) {
                FossilMothEntity moth = protectors.get(i);
                int shareAmount = perMoth + (i == 0 ? remainder : 0);
                
                if (isVictimOwner) {
                    // Active Defense (Owner): Must have energy to block.
                    int energy = moth.getKineticEnergy();
                    if (energy > 0) {
                        int absorb = Math.min(shareAmount, energy);
                        moth.setKineticEnergy(energy - absorb);
                        damageRemaining -= absorb;
                        shareAmount -= absorb;
                    }
                } else {
                    // Passive Defense (Others/Harvest): Accumulate energy.
                    int totalEnergy = moth.getTotalEnergy();
                    int max = moth.getMaxEnergy();
                    int space = max - totalEnergy;
                    
                    if (space > 0 && shareAmount > 0) {
                        int absorb = Math.min(shareAmount, space);
                        moth.setKineticEnergy(moth.getKineticEnergy() + absorb);
                        damageRemaining -= absorb;
                        shareAmount -= absorb;
                    }
                }
                
                // If there's still shareAmount left (no energy to block or moth is full), it hurts the moth.
                if (shareAmount > 0) {
                     moth.hurt(source, shareAmount);
                     damageRemaining -= shareAmount;
                }
            }
            
            // In all cases, the Moths intercepted the attack.
            event.setCanceled(true);
            
        } finally {
            IS_PROCESSING_SHIELD.set(false);
        }
    }
    
    // Fast lookup using ProtectedBlockRegistry (handles double blocks like doors)
    // On client side, falls back to entity search since registry is server-only
    public static int getProtectingMothCount(net.minecraft.world.IWorld world, net.minecraft.util.math.BlockPos pos) {
        if (world == null || pos == null) return 0;
        if (!(world instanceof net.minecraft.world.World)) return 0;
        
        net.minecraft.world.World level = (net.minecraft.world.World) world;
        
        int count;
        if (level.isClientSide) {
            // Client-side: ProtectedBlockRegistry is empty, use entity search fallback
            count = getProtectingMothCountByEntitySearch(level, pos);
        } else {
            // Server-side: use fast registry lookup
            count = com.babelmoth.rotp_ata.util.ProtectedBlockRegistry.getMothCount(level, pos);
        }
        
        // Handle double blocks (Doors) - check both halves
        try {
            net.minecraft.block.BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof net.minecraft.block.DoorBlock) {
                net.minecraft.util.math.BlockPos otherHalf;
                if (state.getValue(net.minecraft.block.DoorBlock.HALF) == net.minecraft.state.properties.DoubleBlockHalf.LOWER) {
                    otherHalf = pos.above();
                } else {
                    otherHalf = pos.below();
                }
                if (level.isClientSide) {
                    count += getProtectingMothCountByEntitySearch(level, otherHalf);
                } else {
                    count += com.babelmoth.rotp_ata.util.ProtectedBlockRegistry.getMothCount(level, otherHalf);
                }
            }
        } catch (Exception e) {
            // Ignore state errors
        }
        
        return count;
    }
    
    // Client-side fallback: search for attached moths near the block position
    // Uses synced entityData (ATTACHED_FACE) and entity position
    private static int getProtectingMothCountByEntitySearch(net.minecraft.world.World level, net.minecraft.util.math.BlockPos pos) {
        net.minecraft.util.math.AxisAlignedBB searchBox = new net.minecraft.util.math.AxisAlignedBB(pos).inflate(1.0);
        java.util.List<FossilMothEntity> moths = level.getEntitiesOfClass(FossilMothEntity.class, searchBox,
                moth -> moth.isAlive() && moth.isAttached());
        return moths.size();
    }

    @SubscribeEvent
    public static void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        // Run on both Client and Server to prevent prediction jitter
        
        int mothCount = getProtectingMothCount(event.getWorld(), event.getPos());
        
        if (mothCount > 0) {
            // Block interaction prevented by Moth
            event.setCanceled(true);
            event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
            event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);
            
            if (!event.getWorld().isClientSide) {
                List<FossilMothEntity> moths = MothQueryUtil.getMothsAtBlock((LivingEntity)event.getPlayer(), event.getPos(), 10.0);
                for (FossilMothEntity moth : moths) {
                    if (moth.getKineticEnergy() < moth.getMaxEnergy()) {
                         moth.setKineticEnergy(moth.getKineticEnergy() + 1);
                    }
                }
            }
        }
    }
    
    // Redstone / Physics Update Protection
    @SubscribeEvent
    public static void onNeighborNotify(net.minecraftforge.event.world.BlockEvent.NeighborNotifyEvent event) {
        // Prevent redstone signals from reaching protected blocks
        java.util.EnumSet<net.minecraft.util.Direction> notifiedSides = event.getNotifiedSides();
        java.util.Iterator<net.minecraft.util.Direction> it = notifiedSides.iterator();
        
        net.minecraft.util.math.BlockPos sourcePos = event.getPos();
        net.minecraft.world.IWorld world = event.getWorld();
        
        while (it.hasNext()) {
            net.minecraft.util.Direction dir = it.next();
            net.minecraft.util.math.BlockPos targetPos = sourcePos.relative(dir);
            
            if (getProtectingMothCount(world, targetPos) > 0) {
                // If target is protected, do not notify it
                it.remove(); 
            }
        }
    }

    // ======================================== 长矛荆棘系统 ========================================

    private static final UUID THORN_SLOW_UUID = UUID.fromString("d1e2f3a4-b5c6-7890-abcd-ef1234567890");
    private static final UUID THORN_WEAK_UUID = UUID.fromString("d1e2f3a4-b5c6-7890-abcd-ef1234567891");
    // 高荆棘层数阈值：超过此值后攻击/移动会扣血
    private static final int THORN_BLEED_THRESHOLD = 30;
    // 递归保护：防止荆棘扣血触发自身
    private static final ThreadLocal<Boolean> IS_PROCESSING_THORN = ThreadLocal.withInitial(() -> false);

    /**
     * 禁疗效果：被长矛插入的敌人受到的治疗被取消，转化为荆棘层数（1HP = 1层）
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHeal(net.minecraftforge.event.entity.living.LivingHealEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (entity.level.isClientSide) return;

        entity.getCapability(com.babelmoth.rotp_ata.capability.SpearThornProvider.SPEAR_THORN_CAPABILITY).ifPresent(cap -> {
            if (!cap.hasSpear()) return;

            float healAmount = event.getAmount();
            if (healAmount <= 0) return;

            // 取消治疗
            event.setCanceled(true);

            // 治疗量转化为荆棘层数（1HP = 1层）
            int thornToAdd = (int) Math.ceil(healAmount);
            cap.addThorns(thornToAdd);

            // 荆棘增加时造成等量伤害
            if (!IS_PROCESSING_THORN.get()) {
                IS_PROCESSING_THORN.set(true);
                try {
                    entity.hurt(DamageSource.GENERIC, thornToAdd);
                } finally {
                    IS_PROCESSING_THORN.set(false);
                }
            }

            // 荆棘增加时触发 RotP 流血粒子效果
            com.github.standobyte.jojo.potion.BleedingEffect.splashBlood(
                    entity.level, entity.getBoundingBox().getCenter(),
                    1.0 + thornToAdd * 0.2, thornToAdd,
                    java.util.OptionalInt.of(Math.min(thornToAdd / 2, 3)),
                    java.util.Optional.of(entity));

            // 荆棘层数同步到 stuck 数（1:1）
            syncThornToStuck(entity, cap);

            // 同步荆棘数据到客户端
            syncThornDataFromEvent(entity, cap);
        });

        // 替身实体的治疗也需要检查本体的荆棘系统（治疗效果根据本体血量计算）
        if (!event.isCanceled() && entity instanceof com.github.standobyte.jojo.entity.stand.StandEntity) {
            LivingEntity user = ((com.github.standobyte.jojo.entity.stand.StandEntity) entity).getUser();
            if (user != null) {
                user.getCapability(com.babelmoth.rotp_ata.capability.SpearThornProvider.SPEAR_THORN_CAPABILITY).ifPresent(cap -> {
                    if (!cap.hasSpear()) return;

                    float healAmount = event.getAmount();
                    if (healAmount <= 0) return;

                    // 取消替身治疗
                    event.setCanceled(true);

                    // 治疗量转化为本体的荆棘层数
                    int thornToAdd = (int) Math.ceil(healAmount);
                    cap.addThorns(thornToAdd);

                    // 荆棘增加时对本体造成等量伤害
                    if (!IS_PROCESSING_THORN.get()) {
                        IS_PROCESSING_THORN.set(true);
                        try {
                            user.hurt(DamageSource.GENERIC, thornToAdd);
                        } finally {
                            IS_PROCESSING_THORN.set(false);
                        }
                    }

                    // 流血粒子在本体身上
                    com.github.standobyte.jojo.potion.BleedingEffect.splashBlood(
                            user.level, user.getBoundingBox().getCenter(),
                            1.0 + thornToAdd * 0.2, thornToAdd,
                            java.util.OptionalInt.of(Math.min(thornToAdd / 2, 3)),
                            java.util.Optional.of(user));

                    syncThornToStuck(user, cap);
                    syncThornDataFromEvent(user, cap);
                });
            }
        }
    }

    /**
     * 荆棘 debuff 逻辑：在 LivingUpdateEvent 中每 tick 检查
     * - 缓慢、挖掘疲劳、虚弱（强度随荆棘层数增加）
     * - 高荆棘层数时移动/攻击扣血 + RotP 流血效果
     * - 削弱替身
     */
    @SubscribeEvent
    public static void onThornLivingUpdate(LivingUpdateEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (entity.level.isClientSide) return;
        if (entity instanceof FossilMothEntity) return;

        entity.getCapability(com.babelmoth.rotp_ata.capability.SpearThornProvider.SPEAR_THORN_CAPABILITY).ifPresent(cap -> {
            if (!cap.hasSpear()) return;

            // 检测绕过 heal() 的直接 setHealth 调用（如疯狂钻石修复）
            float currentHealth = entity.getHealth();
            float lastHealth = cap.getLastHealth();
            if (lastHealth >= 0 && currentHealth > lastHealth + 0.01F) {
                float healedAmount = currentHealth - lastHealth;
                // 撤销治疗
                entity.setHealth(lastHealth);
                // 转化为荆棘层数
                int thornToAdd = (int) Math.ceil(healedAmount);
                cap.addThorns(thornToAdd);
                // 造成等量伤害
                if (!IS_PROCESSING_THORN.get()) {
                    IS_PROCESSING_THORN.set(true);
                    try {
                        entity.hurt(DamageSource.GENERIC, thornToAdd);
                    } finally {
                        IS_PROCESSING_THORN.set(false);
                    }
                }
                // 流血粒子
                com.github.standobyte.jojo.potion.BleedingEffect.splashBlood(
                        entity.level, entity.getBoundingBox().getCenter(),
                        1.0 + thornToAdd * 0.2, thornToAdd,
                        java.util.OptionalInt.of(Math.min(thornToAdd / 2, 3)),
                        java.util.Optional.of(entity));
                syncThornToStuck(entity, cap);
                syncThornDataFromEvent(entity, cap);
            }
            cap.setLastHealth(entity.getHealth());

            if (cap.getThornCount() <= 0) return;

            int thorns = cap.getThornCount();

            // 100层处决：荆棘层数达到上限时造成致命伤害
            if (thorns >= 100 && !IS_PROCESSING_THORN.get()) {
                IS_PROCESSING_THORN.set(true);
                try {
                    entity.hurt(DamageSource.GENERIC, 100.0F);
                } finally {
                    IS_PROCESSING_THORN.set(false);
                }
                // 处决流血粒子爆发
                com.github.standobyte.jojo.potion.BleedingEffect.splashBlood(
                        entity.level, entity.getBoundingBox().getCenter(),
                        3.0, 20,
                        java.util.OptionalInt.of(3),
                        java.util.Optional.of(entity));
                return;
            }

            // 每 20 tick 刷新 debuff（1秒）
            if (entity.tickCount % 20 == 0) {
                // 缓慢：命中就有，每 10 层 +1 级，上限 5 级
                int slowLevel = Math.min(1 + thorns / 10, 5);
                entity.addEffect(new net.minecraft.potion.EffectInstance(
                        net.minecraft.potion.Effects.MOVEMENT_SLOWDOWN, 40, slowLevel - 1, false, false, true));

                // 挖掘疲劳：每 25 层 +1 级，上限 4 级
                int fatigueLevel = Math.min(thorns / 25, 4);
                if (fatigueLevel > 0) {
                    entity.addEffect(new net.minecraft.potion.EffectInstance(
                            net.minecraft.potion.Effects.DIG_SLOWDOWN, 40, fatigueLevel - 1, false, false, true));
                }

                // 虚弱：每 20 层 +1 级，上限 4 级
                int weakLevel = Math.min(thorns / 20, 4);
                if (weakLevel > 0) {
                    entity.addEffect(new net.minecraft.potion.EffectInstance(
                            net.minecraft.potion.Effects.WEAKNESS, 40, weakLevel - 1, false, false, true));
                }

                // 削弱替身：消耗体力 + 对替身实体施加速度/攻击力/攻速削弱
                if (entity instanceof PlayerEntity) {
                    IStandPower standPower = IStandPower.getStandPowerOptional((PlayerEntity) entity).resolve().orElse(null);
                    if (standPower != null && standPower.isActive()) {
                        float staminaDrain = thorns * 0.5F;
                        standPower.consumeStamina(staminaDrain);

                        // 对替身实体施加属性削弱
                        if (standPower.getStandManifestation() instanceof com.github.standobyte.jojo.entity.stand.StandEntity) {
                            com.github.standobyte.jojo.entity.stand.StandEntity standEntity =
                                    (com.github.standobyte.jojo.entity.stand.StandEntity) standPower.getStandManifestation();
                            double thornRatio = Math.min(thorns / 100.0, 1.0);
                            // 速度削弱：最多 -80%
                            updateDebuff(standEntity, Attributes.MOVEMENT_SPEED, THORN_SLOW_UUID,
                                    "Thorn Speed Debuff", -0.8 * thornRatio, AttributeModifier.Operation.MULTIPLY_TOTAL);
                            // 攻击力削弱：最多 -60%
                            updateDebuff(standEntity, Attributes.ATTACK_DAMAGE, THORN_WEAK_UUID,
                                    "Thorn Damage Debuff", -0.6 * thornRatio, AttributeModifier.Operation.MULTIPLY_TOTAL);
                        }
                    }
                }
            }

            // 移动扣血 + RotP 流血效果（命中就有，层数越高扣血越多，最高5）
            if (entity.tickCount % 20 == 0) {
                // 移动检测：速度超过阈值就扣血
                double speedSqr = entity.getDeltaMovement().x * entity.getDeltaMovement().x
                        + entity.getDeltaMovement().z * entity.getDeltaMovement().z;
                if (speedSqr > 0.001) {
                    float moveDamage = Math.min(1.0F + thorns * 0.09F, 10.0F);
                    if (!IS_PROCESSING_THORN.get()) {
                        IS_PROCESSING_THORN.set(true);
                        try {
                            entity.hurt(DamageSource.GENERIC, moveDamage);
                        } finally {
                            IS_PROCESSING_THORN.set(false);
                        }
                    }
                }

                // RotP 流血效果：层数越高持续越久
                int bleedDuration = Math.min(20 + thorns * 2, 200);
                int bleedLevel = Math.min(thorns / 25, 3);
                entity.addEffect(new net.minecraft.potion.EffectInstance(
                        ModStatusEffects.BLEEDING.get(), bleedDuration, bleedLevel, false, false, true));
            }
        });
    }

    /**
     * 追踪被长矛插入的敌人造成的伤害：
     * - 当敌人对其他生物造成的总伤害 >= 荆棘层数 + 8 时，长矛自动脱落
     * - 高荆棘层数时攻击也会扣血
     */
    @SubscribeEvent
    public static void onThornAttackDamage(LivingHurtEvent event) {
        if (event.getEntityLiving().level.isClientSide) return;

        // 1. 追踪攻击者造成的伤害（用于长矛脱落判定）
        Entity sourceEntity = event.getSource().getEntity();
        if (sourceEntity instanceof LivingEntity) {
            LivingEntity attacker = (LivingEntity) sourceEntity;
            attacker.getCapability(com.babelmoth.rotp_ata.capability.SpearThornProvider.SPEAR_THORN_CAPABILITY).ifPresent(cap -> {
                if (!cap.hasSpear()) return;

                float damage = event.getAmount();
                cap.addDamageDealt(damage);

                // 攻击扣血（有荆棘层数时才扣，层数越高扣血越多，最高10）
                int thorns = cap.getThornCount();
                if (thorns > 0 && !IS_PROCESSING_THORN.get()) {
                    float attackPenalty = Math.min(1.0F + thorns * 0.09F, 10.0F);
                    IS_PROCESSING_THORN.set(true);
                    try {
                        attacker.hurt(DamageSource.GENERIC, attackPenalty);
                    } finally {
                        IS_PROCESSING_THORN.set(false);
                    }
                }

                // 检查是否达到脱落阈值（动态：当前荆棘层数 + 8）
                float dynamicThreshold = cap.getThornCount() + 8.0F;
                if (cap.getDamageDealt() >= dynamicThreshold) {
                    detachSpearFromEntity(attacker, cap);
                } else {
                    syncThornDataFromEvent(attacker, cap);
                }
            });
        }
    }

    /**
     * 长矛脱落：清除所有荆棘和stuck层数，自动触发回收
     * 注意：荆棘在本体(entity)上，但stuck可能在替身实体上
     */
    private static void detachSpearFromEntity(LivingEntity entity, com.babelmoth.rotp_ata.capability.ISpearThorn thornCap) {
        // 清除荆棘数据
        thornCap.reset();
        syncThornDataFromEvent(entity, thornCap);

        // 清除本体的 stuck 数
        entity.getCapability(com.babelmoth.rotp_ata.capability.SpearStuckProvider.SPEAR_STUCK_CAPABILITY).ifPresent(stuckCap -> {
            stuckCap.setSpearCount(0);
            com.babelmoth.rotp_ata.networking.AshesToAshesPacketHandler.CHANNEL.send(
                    net.minecraftforge.fml.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                    new com.babelmoth.rotp_ata.networking.SpearStuckSyncPacket(entity.getId(), 0));
        });

        // 收集需要搜索的目标ID（本体 + 可能的替身实体）
        java.util.Set<Integer> searchIds = new java.util.HashSet<>();
        searchIds.add(entity.getId());

        // 如果本体是玩家，也检查其替身实体上的stuck
        if (entity instanceof PlayerEntity) {
            IStandPower.getStandPowerOptional((PlayerEntity) entity).ifPresent(power -> {
                if (power.isActive() && power.getStandManifestation() instanceof com.github.standobyte.jojo.entity.stand.StandEntity) {
                    com.github.standobyte.jojo.entity.stand.StandEntity stand =
                            (com.github.standobyte.jojo.entity.stand.StandEntity) power.getStandManifestation();
                    searchIds.add(stand.getId());
                    // 清除替身的 stuck 数
                    stand.getCapability(com.babelmoth.rotp_ata.capability.SpearStuckProvider.SPEAR_STUCK_CAPABILITY).ifPresent(stuckCap -> {
                        stuckCap.setSpearCount(0);
                        com.babelmoth.rotp_ata.networking.AshesToAshesPacketHandler.CHANNEL.send(
                                net.minecraftforge.fml.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> stand),
                                new com.babelmoth.rotp_ata.networking.SpearStuckSyncPacket(stand.getId(), 0));
                    });
                }
            });
        }

        // 找到插在本体或替身身上的所有长矛并触发回收
        net.minecraft.util.math.AxisAlignedBB box = entity.getBoundingBox().inflate(10.0);
        for (com.babelmoth.rotp_ata.entity.ThelaHunGinjeetSpearEntity spear :
                entity.level.getEntitiesOfClass(com.babelmoth.rotp_ata.entity.ThelaHunGinjeetSpearEntity.class, box,
                        e -> e.isAlive() && e.isInvisible() && searchIds.contains(e.getStuckTargetId()))) {
            spear.setRecalled(true);
        }
    }

    /**
     * 荆棘层数同步到 stuck 数（1:1）
     */
    private static void syncThornToStuck(LivingEntity entity, com.babelmoth.rotp_ata.capability.ISpearThorn thornCap) {
        entity.getCapability(com.babelmoth.rotp_ata.capability.SpearStuckProvider.SPEAR_STUCK_CAPABILITY).ifPresent(stuckCap -> {
            stuckCap.setSpearCount(thornCap.getThornCount());
            com.babelmoth.rotp_ata.networking.AshesToAshesPacketHandler.CHANNEL.send(
                    net.minecraftforge.fml.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                    new com.babelmoth.rotp_ata.networking.SpearStuckSyncPacket(entity.getId(), thornCap.getThornCount()));
        });
    }

    /**
     * 同步荆棘数据到客户端
     */
    private static void syncThornDataFromEvent(LivingEntity entity, com.babelmoth.rotp_ata.capability.ISpearThorn cap) {
        com.babelmoth.rotp_ata.networking.AshesToAshesPacketHandler.CHANNEL.send(
                net.minecraftforge.fml.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                new com.babelmoth.rotp_ata.networking.SpearThornSyncPacket(
                        entity.getId(), cap.getThornCount(), cap.getDamageDealt(),
                        cap.getDetachThreshold(), cap.hasSpear()));
    }

    /**
     * Spear block: reduce damage when player is holding block with spear.
     * Reduction based on stand durability and power via RotP's formula.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onSpearBlockDamage(LivingHurtEvent event) {
        LivingEntity target = event.getEntityLiving();
        if (target.level.isClientSide) return;
        if (!com.babelmoth.rotp_ata.action.ThelaHunGinjeetBlock.isBlocking(target)) return;

        IStandPower.getStandPowerOptional(target).ifPresent(power -> {
            float reduction = com.babelmoth.rotp_ata.action.ThelaHunGinjeetBlock.getBlockReduction(power, event.getAmount());
            float newDamage = event.getAmount() * (1.0F - reduction);
            event.setAmount(newDamage);
        });
    }

    /**
     * Fall damage protection for Thela Hun Ginjeet stand users.
     * Since stand leap is disabled, we provide fall protection directly.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onThelaFallDamage(net.minecraftforge.event.entity.living.LivingFallEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (entity.level.isClientSide) return;
        IStandPower.getStandPowerOptional(entity).ifPresent(power -> {
            if (power.hasPower()
                    && power.getType() == com.babelmoth.rotp_ata.init.InitStands.STAND_THELA_HUN_GINJEET.getStandType()) {
                // Reduce fall distance by 8 blocks (equivalent to leap strength ~3)
                event.setDistance(Math.max(event.getDistance() - 8.0F, 0));
            }
        });
    }

    /**
     * Update spear item NBT with stand-scaled damage when held by the owner.
     * This makes the spear's attack damage directly correspond to stand power.
     */
    @SubscribeEvent
    public static void onSpearDamageSync(LivingUpdateEvent event) {
        if (!(event.getEntityLiving() instanceof PlayerEntity)) return;
        PlayerEntity player = (PlayerEntity) event.getEntityLiving();
        if (player.level.isClientSide) return;
        // Only update every 10 ticks to reduce overhead
        if (player.tickCount % 10 != 0) return;

        net.minecraft.item.ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() != com.babelmoth.rotp_ata.init.InitItems.THELA_HUN_GINJEET_SPEAR.get()) return;

        IStandPower.getStandPowerOptional(player).ifPresent(power -> {
            if (power.hasPower() && power.getStandManifestation() instanceof com.github.standobyte.jojo.entity.stand.StandEntity) {
                double standDmg = ((com.github.standobyte.jojo.entity.stand.StandEntity) power.getStandManifestation()).getAttackDamage();
                mainHand.getOrCreateTag().putDouble("StandScaledDamage", standDmg);
            } else {
                // No active stand - remove the tag so default damage is used
                if (mainHand.hasTag()) {
                    mainHand.getTag().remove("StandScaledDamage");
                }
            }
        });
    }

    /**
     * Prevent non-owners from picking up the spear item.
     * Only the player whose stand is Thela Hun Ginjeet can pick it up.
     */
    @SubscribeEvent
    public static void onSpearItemPickup(net.minecraftforge.event.entity.player.EntityItemPickupEvent event) {
        if (event.getItem().getItem().getItem() != com.babelmoth.rotp_ata.init.InitItems.THELA_HUN_GINJEET_SPEAR.get()) return;

        PlayerEntity player = event.getPlayer();
        boolean isOwner = IStandPower.getStandPowerOptional(player).map(power -> {
            if (!power.hasPower()) return false;
            return power.getType() == com.babelmoth.rotp_ata.init.InitStands.STAND_THELA_HUN_GINJEET.getStandType();
        }).orElse(false);

        if (!isOwner) {
            event.setCanceled(true);
        }
    }

    /**
     * When a Thela Hun Ginjeet stand user dies, remove all their thrown spears
     * and clean up stuck/thorn data on targets.
     */
    @SubscribeEvent
    public static void onThelaHunGinjeetUserDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (entity.level.isClientSide) return;
        if (!(entity instanceof PlayerEntity)) return;

        PlayerEntity deadPlayer = (PlayerEntity) entity;
        boolean isThelaUser = IStandPower.getStandPowerOptional(deadPlayer).map(power -> {
            if (!power.hasPower()) return false;
            return power.getType() == com.babelmoth.rotp_ata.init.InitStands.STAND_THELA_HUN_GINJEET.getStandType();
        }).orElse(false);
        if (!isThelaUser) return;

        // 设置死亡保护标记，防止重生过渡期内附魔被tick检查误清除
        com.babelmoth.rotp_ata.util.SpearEnchantHelper.setDeathProtection(deadPlayer);

        net.minecraft.util.math.AxisAlignedBB searchBox = deadPlayer.getBoundingBox().inflate(200.0);
        for (com.babelmoth.rotp_ata.entity.ThelaHunGinjeetSpearEntity spear :
                entity.level.getEntitiesOfClass(com.babelmoth.rotp_ata.entity.ThelaHunGinjeetSpearEntity.class, searchBox,
                        e -> e.isAlive() && deadPlayer.equals(e.getOwner()))) {
            int targetId = spear.getStuckTargetId();
            if (targetId >= 0) {
                net.minecraft.entity.Entity target = entity.level.getEntity(targetId);
                if (target instanceof LivingEntity) {
                    LivingEntity livingTarget = (LivingEntity) target;
                    // 清除stuck数（stuck在命中实体上）
                    livingTarget.getCapability(com.babelmoth.rotp_ata.capability.SpearStuckProvider.SPEAR_STUCK_CAPABILITY).ifPresent(cap -> {
                        cap.setSpearCount(0);
                        com.babelmoth.rotp_ata.networking.AshesToAshesPacketHandler.CHANNEL.send(
                                net.minecraftforge.fml.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> livingTarget),
                                new com.babelmoth.rotp_ata.networking.SpearStuckSyncPacket(livingTarget.getId(), 0));
                    });
                    // 清除thorn：如果stuck在替身上，thorn在其本体上
                    LivingEntity thornEntity = livingTarget;
                    if (livingTarget instanceof com.github.standobyte.jojo.entity.stand.StandEntity) {
                        LivingEntity user = ((com.github.standobyte.jojo.entity.stand.StandEntity) livingTarget).getUser();
                        if (user != null) thornEntity = user;
                    }
                    final LivingEntity finalThornEntity = thornEntity;
                    finalThornEntity.getCapability(com.babelmoth.rotp_ata.capability.SpearThornProvider.SPEAR_THORN_CAPABILITY).ifPresent(cap -> {
                        cap.reset();
                        com.babelmoth.rotp_ata.networking.AshesToAshesPacketHandler.CHANNEL.send(
                                net.minecraftforge.fml.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> finalThornEntity),
                                new com.babelmoth.rotp_ata.networking.SpearThornSyncPacket(
                                        finalThornEntity.getId(), cap.getThornCount(), cap.getDamageDealt(),
                                        cap.getDetachThreshold(), cap.hasSpear()));
                    });
                }
            }
            spear.remove();
        }
    }

    /**
     * 附魔数据清除：当玩家失去赛拉·杭·金吉替身时，清除保存的附魔数据
     */
    @SubscribeEvent
    public static void onPlayerTickCheckEnchantClear(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level.isClientSide) return;
        // 每100tick检查一次
        if (event.player.tickCount % 100 != 0) return;

        PlayerEntity player = event.player;
        // 检查是否有保存的附魔数据
        if (!player.getPersistentData().contains("ThelaHunGinjeetEnchantments")) return;

        // 死亡保护期内跳过检查，防止重生过渡期误清除
        if (com.babelmoth.rotp_ata.util.SpearEnchantHelper.hasDeathProtection(player)) return;

        // 检查是否仍持有赛拉·杭·金吉替身
        boolean hasThela = IStandPower.getStandPowerOptional(player).map(power -> {
            if (!power.hasPower()) return false;
            return power.getType() == com.babelmoth.rotp_ata.init.InitStands.STAND_THELA_HUN_GINJEET.getStandType();
        }).orElse(false);

        if (!hasThela) {
            com.babelmoth.rotp_ata.util.SpearEnchantHelper.clearSavedEnchantments(player);
        }
    }

    /**
     * 抢夺附魔：长矛相关伤害源击杀时应用抢夺等级
     */
    @SubscribeEvent
    public static void onSpearLootingLevel(net.minecraftforge.event.entity.living.LootingLevelEvent event) {
        DamageSource source = event.getDamageSource();
        if (source == null || source.getMsgId() == null) return;
        String msgId = source.getMsgId();
        // 匹配长矛相关伤害源
        if (!msgId.startsWith("stand.spear") && !msgId.equals("arrow")) return;

        Entity attacker = source.getEntity();
        if (!(attacker instanceof PlayerEntity)) return;
        PlayerEntity player = (PlayerEntity) attacker;

        // 从主手或投掷的矛获取抢夺等级
        net.minecraft.item.ItemStack spear = com.babelmoth.rotp_ata.util.SpearEnchantHelper.getSpearFromPlayer(player);
        if (spear.isEmpty()) {
            // 可能是投掷状态，从投掷实体获取
            if (source.getDirectEntity() instanceof com.babelmoth.rotp_ata.entity.ThelaHunGinjeetSpearEntity) {
                spear = ((com.babelmoth.rotp_ata.entity.ThelaHunGinjeetSpearEntity) source.getDirectEntity()).getSpearItem();
            }
        }
        if (!spear.isEmpty()) {
            int lootingLevel = com.babelmoth.rotp_ata.util.SpearEnchantHelper.getLootingLevel(spear);
            if (lootingLevel > 0) {
                event.setLootingLevel(event.getLootingLevel() + lootingLevel);
            }
        }
    }

    }
