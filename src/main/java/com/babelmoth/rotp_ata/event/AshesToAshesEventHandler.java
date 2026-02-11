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
        }
    }

    // Big leap uses only RotP's shift+space logic (AshesToAshesStandEntity.getLeapStrength); no explosion sound on normal jump

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
        if (entity.level.isClientSide) return;
        
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
        
        // 2. Protection Logic (Block attached moths)
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
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
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
    public static int getProtectingMothCount(net.minecraft.world.IWorld world, net.minecraft.util.math.BlockPos pos) {
        if (world == null || pos == null) return 0;
        if (!(world instanceof net.minecraft.world.World)) return 0;
        
        net.minecraft.world.World level = (net.minecraft.world.World) world;
        int count = com.babelmoth.rotp_ata.util.ProtectedBlockRegistry.getMothCount(level, pos);
        
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
                count += com.babelmoth.rotp_ata.util.ProtectedBlockRegistry.getMothCount(level, otherHalf);
            }
        } catch (Exception e) {
            // Ignore state errors
        }
        
        return count;
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
    }
