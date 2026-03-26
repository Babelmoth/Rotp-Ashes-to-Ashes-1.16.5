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

@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID)
public class AshesToAshesEventHandler {

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

        if (entity instanceof FossilMothEntity) return;

        List<FossilMothEntity> attachedMoths = MothQueryUtil.getAttachedMoths(entity, AshesToAshesConstants.QUERY_RADIUS_ATTACHMENT);

        int validMothCount = 0;
        for (FossilMothEntity moth : attachedMoths) {
            if (moth.getTotalEnergy() < moth.getMaxEnergy()) {
                validMothCount++;
            }
        }

        boolean isOwner = false;
        IStandPower ownerPower = null;
        if (entity instanceof PlayerEntity) {
            ownerPower = IStandPower.getStandPowerOptional((PlayerEntity)entity).resolve().orElse(null);
            isOwner = attachedMoths.stream().anyMatch(moth -> moth.getOwner() != null && moth.getOwner().is(entity));
        }

        if (isOwner) {
            int energyMothCount = 0;
            for (FossilMothEntity moth : attachedMoths) {
                if (moth.getKineticEnergy() > 0) {
                    energyMothCount++;
                }
            }

            if (energyMothCount > 0) {
                updateDebuff(entity, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER_UUID, "Moth Speed Buff", 0.10 * energyMothCount, AttributeModifier.Operation.MULTIPLY_TOTAL);
                updateDebuff(entity, Attributes.ATTACK_SPEED, ATTACK_SPEED_MODIFIER_UUID, "Moth Attack Speed Buff", 0.10 * energyMothCount, AttributeModifier.Operation.MULTIPLY_TOTAL);

                if (!entity.hasEffect(ModStatusEffects.INTEGRATED_STAND.get())) {
                    entity.addEffect(new net.minecraft.potion.EffectInstance(ModStatusEffects.INTEGRATED_STAND.get(), 40, 0, false, false, true));
                }

                if (ownerPower != null && !((PlayerEntity)entity).isCreative()) {
                    ownerPower.consumeStamina(0.1F * energyMothCount);
                }

                if (entity.tickCount % 20 == 0) {
                    for (FossilMothEntity moth : attachedMoths) {
                        if (moth.getKineticEnergy() > 0) {
                            moth.setKineticEnergy(Math.max(0, moth.getKineticEnergy() - 1));
                        }
                    }
                }
            } else {
                clearModifiers(entity);
            }
        } else {
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

            double speedSqr = entity.getDeltaMovement().lengthSqr();
            if (speedSqr > 0.0001 && entity.tickCount % 20 == 0) {
                final int kineticBudgetPerPeriod = 2;
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
                    if (Math.abs(modifier.getAmount() - amount) > 0.001) {
                        instance.removeModifier(uuid);
                        instance.addTransientModifier(new AttributeModifier(uuid, name, amount, operation));
                    }
                } else {
                    instance.addTransientModifier(new AttributeModifier(uuid, name, amount, operation));
                }
            } else {
                if (modifier != null) {
                    instance.removeModifier(uuid);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        LivingEntity entity = event.getEntityLiving();

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

        int protectingCount = getProtectingMothCount(event.getEntity().level, event.getPos());
        float modifier2 = 1.0f;

        if (protectingCount > 0) {
            if (protectingCount >= 5) {
                event.setCanceled(true);
                modifier2 = 0f;
            } else {
                modifier2 = Math.max(0.0f, 1.0f - (0.2f * protectingCount));
            }
        }

        if (modifier1 != 1.0f || modifier2 != 1.0f) {
            event.setNewSpeed(event.getOriginalSpeed() * modifier1 * modifier2);
        }

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
        net.minecraft.block.BlockState breakState = event.getWorld().getBlockState(event.getPos());
        if (breakState.getBlock() instanceof com.babelmoth.rotp_ata.block.FrozenBarrierBlock) {
            event.setCanceled(true);
            return;
        }

        if (getProtectingMothCount(event.getWorld(), event.getPos()) > 0) {
            event.setCanceled(true);
            return;
        }

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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntityLiving();
        DamageSource source = event.getSource();

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

        boolean isFall = source == DamageSource.FALL;
        boolean isHamon = source.getMsgId() != null && source.getMsgId().startsWith("hamon");
        boolean isSendoOverdrive = source.getDirectEntity() instanceof com.github.standobyte.jojo.entity.HamonSendoOverdriveEntity;

        if (!isFall && !isHamon && !isSendoOverdrive && (source.isBypassArmor() || source.isMagic() || source == DamageSource.OUT_OF_WORLD)) {
            return;
        }

        if (source.getEntity() != null) {
            if (source.getEntity() == victim) return;
        }

        if (victim instanceof FossilMothEntity) {
             FossilMothEntity moth = (FossilMothEntity) victim;
             LivingEntity owner = moth.getOwner();
             boolean isOwnerSafeKineticDetonation = source.isExplosion() && "ashes_to_ashes_kinetic_detonation".equals(source.getMsgId()) && source.getEntity() == owner;

             int totalEnergy = moth.getTotalEnergy();
             int maxEnergy = moth.getMaxEnergy();
             int totalSpace = maxEnergy - totalEnergy;

             if (isHamon || isSendoOverdrive) {
                 int damage = (int) Math.ceil(event.getAmount());

                 if (totalSpace > 0) {
                     int absorbed = Math.min(damage, totalSpace);
                     moth.setHamonEnergy(moth.getHamonEnergy() + absorbed);
                 }

                 event.setCanceled(true);
                 return;
             }

             if (!source.isBypassArmor() && !source.isMagic() && source != DamageSource.OUT_OF_WORLD) {
                  int damage = (int) Math.ceil(event.getAmount());
                  int absorbed = 0;

                  if (totalSpace > 0 && !isOwnerSafeKineticDetonation) {
                      absorbed = Math.min(damage, totalSpace);
                      moth.setKineticEnergy(moth.getKineticEnergy() + absorbed);
                      damage -= absorbed;
                  }

                  if (damage > 0) {
                      if (owner != null) {
                           float ratio = 2.0f / 50.0f;
                           float damageToOwner = damage * ratio;

                           if (damageToOwner > 0 && source.getEntity() != owner) {
                               owner.hurt(source, damageToOwner);
                           }
                      }

                      float armor = (float) moth.getArmorValue();
                      float toughness = (float) moth.getAttributeValue(net.minecraft.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
                      float damagedReduced = net.minecraft.util.CombatRules.getDamageAfterAbsorb(damage, armor, toughness);

                      if (damagedReduced > 0) {
                          moth.setHealth(moth.getHealth() - damagedReduced);
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

            int baseEnergyPerMoth = totalDamage / mothCount;
            int remainder = totalDamage % mothCount;

            int mothIndex = 0;
            for (FossilMothEntity moth : selfAdheredMoths) {
                int currentEnergy = moth.getKineticEnergy();
                int maxEnergy = moth.getMaxEnergy();
                int energyToAdd = baseEnergyPerMoth + (mothIndex < remainder ? 1 : 0);
                int newEnergy = Math.min(currentEnergy + energyToAdd, maxEnergy);
                moth.setKineticEnergy(newEnergy);
                mothIndex++;
            }
            if (totalDamage > 15) {
                float excessDamage = (totalDamage - 15) * 0.5f;
                float damagePerMoth = excessDamage / mothCount;
                for (FossilMothEntity moth : selfAdheredMoths) {
                    moth.hurt(DamageSource.GENERIC, damagePerMoth);
                }
            }
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

        if (IS_PROCESSING_SHIELD.get()) {
            return;
        }

        try {
            IS_PROCESSING_SHIELD.set(true);

            LivingEntity owner = protectors.get(0).getOwner();

            if (owner != null && source.getEntity() != null) {
                Entity attacker = source.getEntity();

                if (attacker == owner && victim == owner) return;

                if (attacker instanceof com.github.standobyte.jojo.entity.stand.StandEntity) {
                    com.github.standobyte.jojo.entity.stand.StandEntity stand = (com.github.standobyte.jojo.entity.stand.StandEntity) attacker;
                    if (stand.getUser() == owner && victim == owner) return;
                }

                if (attacker instanceof FossilMothEntity) {
                    FossilMothEntity attackerMoth = (FossilMothEntity) attacker;
                    if (attackerMoth.getOwner() == owner && victim == owner) return;
                }
            }

            if (source.getEntity() instanceof LivingEntity) {
                LivingEntity attacker = (LivingEntity) source.getEntity();
                if (attacker.getMobType() == net.minecraft.entity.CreatureAttribute.UNDEAD) {
                    float totalBacklash = 0;
                    boolean trigger = false;
                    for (FossilMothEntity moth : protectors) {
                        int hamon = moth.getHamonEnergy();
                        if (hamon > 0) {
                            totalBacklash += 1.5f;
                            moth.setHamonEnergy(Math.max(0, hamon - 2));
                            trigger = true;
                        }
                    }
                    if (trigger) {

                        attacker.hurt(new net.minecraft.util.DamageSource("hamon_backlash").setMagic(), totalBacklash + 2.0f);

                        if (attacker.level instanceof net.minecraft.world.server.ServerWorld) {
                            ((net.minecraft.world.server.ServerWorld)attacker.level).sendParticles(net.minecraft.particles.ParticleTypes.INSTANT_EFFECT,
                                attacker.getX(), attacker.getY() + 1, attacker.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
                        }
                    }
                }
            }

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

                    int energy = moth.getKineticEnergy();
                    if (energy > 0) {
                        int absorb = Math.min(shareAmount, energy);
                        moth.setKineticEnergy(energy - absorb);
                        damageRemaining -= absorb;
                        shareAmount -= absorb;
                    }
                } else {

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

                if (shareAmount > 0) {
                     moth.hurt(source, shareAmount);
                     damageRemaining -= shareAmount;
                }
            }

            event.setCanceled(true);

        } finally {
            IS_PROCESSING_SHIELD.set(false);
        }
    }

    public static int getProtectingMothCount(net.minecraft.world.IWorld world, net.minecraft.util.math.BlockPos pos) {
        if (world == null || pos == null) return 0;
        if (!(world instanceof net.minecraft.world.World)) return 0;

        net.minecraft.world.World level = (net.minecraft.world.World) world;

        int count;
        if (level.isClientSide) {

            count = getProtectingMothCountByEntitySearch(level, pos);
        } else {

            count = com.babelmoth.rotp_ata.util.ProtectedBlockRegistry.getMothCount(level, pos);
        }

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

        }

        return count;
    }

    private static int getProtectingMothCountByEntitySearch(net.minecraft.world.World level, net.minecraft.util.math.BlockPos pos) {
        net.minecraft.util.math.AxisAlignedBB searchBox = new net.minecraft.util.math.AxisAlignedBB(pos).inflate(1.0);
        java.util.List<FossilMothEntity> moths = level.getEntitiesOfClass(FossilMothEntity.class, searchBox,
                moth -> moth.isAlive() && moth.isAttached());
        return moths.size();
    }

    @SubscribeEvent
    public static void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {

        int mothCount = getProtectingMothCount(event.getWorld(), event.getPos());

        if (mothCount > 0) {

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

    @SubscribeEvent
    public static void onNeighborNotify(net.minecraftforge.event.world.BlockEvent.NeighborNotifyEvent event) {

        java.util.EnumSet<net.minecraft.util.Direction> notifiedSides = event.getNotifiedSides();
        java.util.Iterator<net.minecraft.util.Direction> it = notifiedSides.iterator();

        net.minecraft.util.math.BlockPos sourcePos = event.getPos();
        net.minecraft.world.IWorld world = event.getWorld();

        while (it.hasNext()) {
            net.minecraft.util.Direction dir = it.next();
            net.minecraft.util.math.BlockPos targetPos = sourcePos.relative(dir);

            if (getProtectingMothCount(world, targetPos) > 0) {

                it.remove();
            }
        }
    }

    private static final UUID THORN_SLOW_UUID = UUID.fromString("d1e2f3a4-b5c6-7890-abcd-ef1234567890");
    private static final UUID THORN_WEAK_UUID = UUID.fromString("d1e2f3a4-b5c6-7890-abcd-ef1234567891");

    private static final int THORN_BLEED_THRESHOLD = 30;

    private static final ThreadLocal<Boolean> IS_PROCESSING_THORN = ThreadLocal.withInitial(() -> false);

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHeal(net.minecraftforge.event.entity.living.LivingHealEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (entity.level.isClientSide) return;

        entity.getCapability(com.babelmoth.rotp_ata.capability.SpearThornProvider.SPEAR_THORN_CAPABILITY).ifPresent(cap -> {
            if (!cap.hasSpear()) return;

            float healAmount = event.getAmount();
            if (healAmount <= 0) return;

            event.setCanceled(true);

            int thornToAdd = (int) Math.ceil(healAmount);
            cap.addThorns(thornToAdd);

            if (!IS_PROCESSING_THORN.get()) {
                IS_PROCESSING_THORN.set(true);
                try {
                    entity.hurt(DamageSource.GENERIC, thornToAdd);
                } finally {
                    IS_PROCESSING_THORN.set(false);
                }
            }

            com.github.standobyte.jojo.potion.BleedingEffect.splashBlood(
                    entity.level, entity.getBoundingBox().getCenter(),
                    1.0 + thornToAdd * 0.2, thornToAdd,
                    java.util.OptionalInt.of(Math.min(thornToAdd / 2, 3)),
                    java.util.Optional.of(entity));

            syncThornToStuck(entity, cap);

            syncThornDataFromEvent(entity, cap);
        });

        if (!event.isCanceled() && entity instanceof com.github.standobyte.jojo.entity.stand.StandEntity) {
            LivingEntity user = ((com.github.standobyte.jojo.entity.stand.StandEntity) entity).getUser();
            if (user != null) {
                user.getCapability(com.babelmoth.rotp_ata.capability.SpearThornProvider.SPEAR_THORN_CAPABILITY).ifPresent(cap -> {
                    if (!cap.hasSpear()) return;

                    float healAmount = event.getAmount();
                    if (healAmount <= 0) return;

                    event.setCanceled(true);

                    int thornToAdd = (int) Math.ceil(healAmount);
                    cap.addThorns(thornToAdd);

                    if (!IS_PROCESSING_THORN.get()) {
                        IS_PROCESSING_THORN.set(true);
                        try {
                            user.hurt(DamageSource.GENERIC, thornToAdd);
                        } finally {
                            IS_PROCESSING_THORN.set(false);
                        }
                    }

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

    @SubscribeEvent
    public static void onThornLivingUpdate(LivingUpdateEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (entity.level.isClientSide) return;
        if (entity instanceof FossilMothEntity) return;

        entity.getCapability(com.babelmoth.rotp_ata.capability.SpearThornProvider.SPEAR_THORN_CAPABILITY).ifPresent(cap -> {
            if (!cap.hasSpear()) return;

            float currentHealth = entity.getHealth();
            float lastHealth = cap.getLastHealth();
            if (lastHealth >= 0 && currentHealth > lastHealth + 0.01F) {
                float healedAmount = currentHealth - lastHealth;

                entity.setHealth(lastHealth);

                int thornToAdd = (int) Math.ceil(healedAmount);
                cap.addThorns(thornToAdd);

                if (!IS_PROCESSING_THORN.get()) {
                    IS_PROCESSING_THORN.set(true);
                    try {
                        entity.hurt(DamageSource.GENERIC, thornToAdd);
                    } finally {
                        IS_PROCESSING_THORN.set(false);
                    }
                }

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

            if (thorns >= 100 && !IS_PROCESSING_THORN.get()) {
                IS_PROCESSING_THORN.set(true);
                try {
                    entity.hurt(DamageSource.GENERIC, 100.0F);
                } finally {
                    IS_PROCESSING_THORN.set(false);
                }

                com.github.standobyte.jojo.potion.BleedingEffect.splashBlood(
                        entity.level, entity.getBoundingBox().getCenter(),
                        3.0, 20,
                        java.util.OptionalInt.of(3),
                        java.util.Optional.of(entity));
                return;
            }

            if (entity.tickCount % 20 == 0) {

                int slowLevel = Math.min(1 + thorns / 10, 5);
                entity.addEffect(new net.minecraft.potion.EffectInstance(
                        net.minecraft.potion.Effects.MOVEMENT_SLOWDOWN, 40, slowLevel - 1, false, false, true));

                int fatigueLevel = Math.min(thorns / 25, 4);
                if (fatigueLevel > 0) {
                    entity.addEffect(new net.minecraft.potion.EffectInstance(
                            net.minecraft.potion.Effects.DIG_SLOWDOWN, 40, fatigueLevel - 1, false, false, true));
                }

                int weakLevel = Math.min(thorns / 20, 4);
                if (weakLevel > 0) {
                    entity.addEffect(new net.minecraft.potion.EffectInstance(
                            net.minecraft.potion.Effects.WEAKNESS, 40, weakLevel - 1, false, false, true));
                }

                if (entity instanceof PlayerEntity) {
                    IStandPower standPower = IStandPower.getStandPowerOptional((PlayerEntity) entity).resolve().orElse(null);
                    if (standPower != null && standPower.isActive()) {
                        float staminaDrain = thorns * 0.5F;
                        standPower.consumeStamina(staminaDrain);

                        if (standPower.getStandManifestation() instanceof com.github.standobyte.jojo.entity.stand.StandEntity) {
                            com.github.standobyte.jojo.entity.stand.StandEntity standEntity =
                                    (com.github.standobyte.jojo.entity.stand.StandEntity) standPower.getStandManifestation();
                            double thornRatio = Math.min(thorns / 100.0, 1.0);

                            updateDebuff(standEntity, Attributes.MOVEMENT_SPEED, THORN_SLOW_UUID,
                                    "Thorn Speed Debuff", -0.8 * thornRatio, AttributeModifier.Operation.MULTIPLY_TOTAL);

                            updateDebuff(standEntity, Attributes.ATTACK_DAMAGE, THORN_WEAK_UUID,
                                    "Thorn Damage Debuff", -0.6 * thornRatio, AttributeModifier.Operation.MULTIPLY_TOTAL);
                        }
                    }
                }
            }

            if (entity.tickCount % 20 == 0) {

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

                int bleedDuration = Math.min(20 + thorns * 2, 200);
                int bleedLevel = Math.min(thorns / 25, 3);
                entity.addEffect(new net.minecraft.potion.EffectInstance(
                        ModStatusEffects.BLEEDING.get(), bleedDuration, bleedLevel, false, false, true));
            }
        });
    }

    @SubscribeEvent
    public static void onThornAttackDamage(LivingHurtEvent event) {
        if (event.getEntityLiving().level.isClientSide) return;

        Entity sourceEntity = event.getSource().getEntity();
        if (sourceEntity instanceof LivingEntity) {
            LivingEntity attacker = (LivingEntity) sourceEntity;
            attacker.getCapability(com.babelmoth.rotp_ata.capability.SpearThornProvider.SPEAR_THORN_CAPABILITY).ifPresent(cap -> {
                if (!cap.hasSpear()) return;

                float damage = event.getAmount();
                cap.addDamageDealt(damage);

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

                float dynamicThreshold = cap.getThornCount() + 8.0F;
                if (cap.getDamageDealt() >= dynamicThreshold) {
                    detachSpearFromEntity(attacker, cap);
                } else {
                    syncThornDataFromEvent(attacker, cap);
                }
            });
        }
    }

    private static void detachSpearFromEntity(LivingEntity entity, com.babelmoth.rotp_ata.capability.ISpearThorn thornCap) {

        thornCap.reset();
        syncThornDataFromEvent(entity, thornCap);

        entity.getCapability(com.babelmoth.rotp_ata.capability.SpearStuckProvider.SPEAR_STUCK_CAPABILITY).ifPresent(stuckCap -> {
            stuckCap.setSpearCount(0);
            com.babelmoth.rotp_ata.networking.AshesToAshesPacketHandler.CHANNEL.send(
                    net.minecraftforge.fml.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                    new com.babelmoth.rotp_ata.networking.SpearStuckSyncPacket(entity.getId(), 0));
        });

        java.util.Set<Integer> searchIds = new java.util.HashSet<>();
        searchIds.add(entity.getId());

        if (entity instanceof PlayerEntity) {
            IStandPower.getStandPowerOptional((PlayerEntity) entity).ifPresent(power -> {
                if (power.isActive() && power.getStandManifestation() instanceof com.github.standobyte.jojo.entity.stand.StandEntity) {
                    com.github.standobyte.jojo.entity.stand.StandEntity stand =
                            (com.github.standobyte.jojo.entity.stand.StandEntity) power.getStandManifestation();
                    searchIds.add(stand.getId());

                    stand.getCapability(com.babelmoth.rotp_ata.capability.SpearStuckProvider.SPEAR_STUCK_CAPABILITY).ifPresent(stuckCap -> {
                        stuckCap.setSpearCount(0);
                        com.babelmoth.rotp_ata.networking.AshesToAshesPacketHandler.CHANNEL.send(
                                net.minecraftforge.fml.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> stand),
                                new com.babelmoth.rotp_ata.networking.SpearStuckSyncPacket(stand.getId(), 0));
                    });
                }
            });
        }

        net.minecraft.util.math.AxisAlignedBB box = entity.getBoundingBox().inflate(10.0);
        for (com.babelmoth.rotp_ata.entity.ThelaHunGinjeetSpearEntity spear :
                entity.level.getEntitiesOfClass(com.babelmoth.rotp_ata.entity.ThelaHunGinjeetSpearEntity.class, box,
                        e -> e.isAlive() && e.isInvisible() && searchIds.contains(e.getStuckTargetId()))) {
            spear.setRecalled(true);
        }
    }

    private static void syncThornToStuck(LivingEntity entity, com.babelmoth.rotp_ata.capability.ISpearThorn thornCap) {
        entity.getCapability(com.babelmoth.rotp_ata.capability.SpearStuckProvider.SPEAR_STUCK_CAPABILITY).ifPresent(stuckCap -> {
            stuckCap.setSpearCount(thornCap.getThornCount());
            com.babelmoth.rotp_ata.networking.AshesToAshesPacketHandler.CHANNEL.send(
                    net.minecraftforge.fml.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                    new com.babelmoth.rotp_ata.networking.SpearStuckSyncPacket(entity.getId(), thornCap.getThornCount()));
        });
    }

    private static void syncThornDataFromEvent(LivingEntity entity, com.babelmoth.rotp_ata.capability.ISpearThorn cap) {
        com.babelmoth.rotp_ata.networking.AshesToAshesPacketHandler.CHANNEL.send(
                net.minecraftforge.fml.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                new com.babelmoth.rotp_ata.networking.SpearThornSyncPacket(
                        entity.getId(), cap.getThornCount(), cap.getDamageDealt(),
                        cap.getDetachThreshold(), cap.hasSpear()));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onSpearBlockDamage(LivingHurtEvent event) {
        LivingEntity target = event.getEntityLiving();
        if (target.level.isClientSide) return;
        if (!com.babelmoth.rotp_ata.action.ThelaHunGinjeetBlock.isBlocking(target)) return;

        IStandPower.getStandPowerOptional(target).ifPresent(power -> {
            float originalDamage = event.getAmount();
            float reduction = com.babelmoth.rotp_ata.action.ThelaHunGinjeetBlock.getBlockReduction(power, event.getAmount());
            float newDamage = event.getAmount() * (1.0F - reduction);
            event.setAmount(newDamage);
            if (newDamage < originalDamage) {
                target.level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        net.minecraft.util.SoundEvents.ANVIL_LAND, net.minecraft.util.SoundCategory.PLAYERS, 1.0F, 1.0F);
            }
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onThelaFallDamage(net.minecraftforge.event.entity.living.LivingFallEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (entity.level.isClientSide) return;
        IStandPower.getStandPowerOptional(entity).ifPresent(power -> {
            if (power.hasPower()
                    && power.getType() == com.babelmoth.rotp_ata.init.InitStands.STAND_THELA_HUN_GINJEET.getStandType()) {

                event.setDistance(Math.max(event.getDistance() - 8.0F, 0));
            }
        });
    }

    @SubscribeEvent
    public static void onSpearDamageSync(LivingUpdateEvent event) {
        if (!(event.getEntityLiving() instanceof PlayerEntity)) return;
        PlayerEntity player = (PlayerEntity) event.getEntityLiving();
        if (player.level.isClientSide) return;

        if (player.tickCount % 10 != 0) return;

        net.minecraft.item.ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() != com.babelmoth.rotp_ata.init.InitItems.THELA_HUN_GINJEET_SPEAR.get()) return;

        IStandPower.getStandPowerOptional(player).ifPresent(power -> {
            if (power.hasPower() && power.getStandManifestation() instanceof com.github.standobyte.jojo.entity.stand.StandEntity) {
                double standDmg = ((com.github.standobyte.jojo.entity.stand.StandEntity) power.getStandManifestation()).getAttackDamage();
                if (power.getResolveLevel() > 0) {
                    standDmg += 2.0;
                }
                mainHand.getOrCreateTag().putDouble("StandScaledDamage", standDmg);
            } else {

                if (mainHand.hasTag()) {
                    mainHand.getTag().remove("StandScaledDamage");
                }
            }
        });
    }

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

                    livingTarget.getCapability(com.babelmoth.rotp_ata.capability.SpearStuckProvider.SPEAR_STUCK_CAPABILITY).ifPresent(cap -> {
                        cap.setSpearCount(0);
                        com.babelmoth.rotp_ata.networking.AshesToAshesPacketHandler.CHANNEL.send(
                                net.minecraftforge.fml.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> livingTarget),
                                new com.babelmoth.rotp_ata.networking.SpearStuckSyncPacket(livingTarget.getId(), 0));
                    });

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

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        PlayerEntity oldPlayer = event.getOriginal();
        PlayerEntity newPlayer = event.getPlayer();

        oldPlayer.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(oldPool -> {
            newPlayer.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(newPool -> {
                newPool.deserializeNBT(oldPool.serializeNBT());
                newPool.clearAllDeployed();
                newPool.setOrbitMothCount(oldPool.getOrbitMothCount());
                newPool.setShieldMothCount(oldPool.getShieldMothCount());
                newPool.setSwarmAttackCount(oldPool.getSwarmAttackCount());
                newPool.setBarrierPassthrough(oldPool.isBarrierPassthrough());
                newPool.setAutoChargeShield(oldPool.isAutoChargeShield());
                newPool.setRemoteFollow(oldPool.isRemoteFollow());
                newPool.setRemoteFollowRatio(oldPool.getRemoteFollowRatio());
            });
        });

        net.minecraft.nbt.CompoundNBT oldData = oldPlayer.getPersistentData();
        net.minecraft.nbt.CompoundNBT newData = newPlayer.getPersistentData();
        for (String key : oldData.getAllKeys()) {
            if (key.startsWith("ThelaHunGinjeet")) {
                newData.put(key, oldData.get(key).copy());
            }
        }

        if (event.isWasDeath() && newPlayer instanceof net.minecraft.entity.player.ServerPlayerEntity) {
            newPlayer.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
                pool.sync((net.minecraft.entity.player.ServerPlayerEntity) newPlayer);
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerTickCheckEnchantClear(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level.isClientSide) return;

        if (event.player.tickCount % 100 != 0) return;

        PlayerEntity player = event.player;

        if (!player.getPersistentData().contains("ThelaHunGinjeetEnchantments")) return;

        if (com.babelmoth.rotp_ata.util.SpearEnchantHelper.hasDeathProtection(player)) return;

        boolean hasThela = IStandPower.getStandPowerOptional(player).map(power -> {
            if (!power.hasPower()) return false;
            return power.getType() == com.babelmoth.rotp_ata.init.InitStands.STAND_THELA_HUN_GINJEET.getStandType();
        }).orElse(false);

        if (!hasThela) {
            com.babelmoth.rotp_ata.util.SpearEnchantHelper.clearSavedEnchantments(player);
        }
    }

    @SubscribeEvent
    public static void onSpearLootingLevel(net.minecraftforge.event.entity.living.LootingLevelEvent event) {
        DamageSource source = event.getDamageSource();
        if (source == null || source.getMsgId() == null) return;
        String msgId = source.getMsgId();

        if (!msgId.startsWith("stand.spear") && !msgId.equals("arrow")) return;

        Entity attacker = source.getEntity();
        if (!(attacker instanceof PlayerEntity)) return;
        PlayerEntity player = (PlayerEntity) attacker;

        net.minecraft.item.ItemStack spear = com.babelmoth.rotp_ata.util.SpearEnchantHelper.getSpearFromPlayer(player);
        if (spear.isEmpty()) {

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
