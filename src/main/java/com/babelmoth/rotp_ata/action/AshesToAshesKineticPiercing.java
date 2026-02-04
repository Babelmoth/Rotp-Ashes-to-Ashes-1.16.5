package com.babelmoth.rotp_ata.action;

import java.util.Comparator;
import java.util.List;

import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.power.impl.stand.IStandManifestation;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;
import com.babelmoth.rotp_ata.capability.MothPoolProvider;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;

public class AshesToAshesKineticPiercing extends StandAction {

    public AshesToAshesKineticPiercing(AbstractBuilder<?> builder) {
        super(builder);
    }
    
    @Override
    public int getHoldDurationMax(IStandPower standPower) {
        return 40; // 2 seconds (40 ticks)
    }

    @Override
    public int getHoldDurationToFire(IStandPower standPower) {
        return 40; // 2 seconds to fully charge
    }
    
    @Override
    public ActionConditionResult checkConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        // Block if Stand exists AND is being manually controlled (Remote Control)
        IStandManifestation manifestation = power.getStandManifestation();
        if (manifestation instanceof StandEntity) {
            StandEntity stand = (StandEntity) manifestation;
            if (stand.isManuallyControlled()) {
                return ActionConditionResult.createNegative(new StringTextComponent("Cannot use in remote control"));
            }
        }
        
        // Check if there are any moths to use OR we can spawn one
        List<FossilMothEntity> moths = MothQueryUtil.getFreeMoths(user, AshesToAshesConstants.QUERY_RADIUS_CHARGING);
        List<FossilMothEntity> chargingMoths = MothQueryUtil.getChargingMoths(user, AshesToAshesConstants.QUERY_RADIUS_CHARGING);
        
        if (moths.isEmpty() && chargingMoths.isEmpty()) {
            // Check if we can spawn a new moth
            boolean canSpawn = user.getCapability(MothPoolProvider.MOTH_POOL_CAPABILITY)
                .map(pool -> pool.getTotalMoths() < com.babelmoth.rotp_ata.capability.IMothPool.MAX_MOTHS)
                .orElse(false);
            if (!canSpawn) {
                return ActionConditionResult.createNegative(new StringTextComponent("No moths available"));
            }
        }
        
        return ActionConditionResult.POSITIVE;
    }

    @Override
    public void onClick(World world, LivingEntity user, IStandPower power) {
        if (!world.isClientSide && !power.isActive() && power.getType() instanceof com.github.standobyte.jojo.power.impl.stand.type.EntityStandType) {
            ((com.github.standobyte.jojo.power.impl.stand.type.EntityStandType<?>) power.getType())
                .summon(user, power, entity -> {}, true, true);
        }
        super.onClick(world, user, power);
    }

    @Override
    public void onHoldTick(World world, LivingEntity user, IStandPower power, int ticksHeld, ActionTarget target, boolean requirementsMet) {
        // Continuous Stamina Drain
        if (!world.isClientSide && requirementsMet) {
            power.consumeStamina(30.5f); // Drain stamina while holding
            
            // Find charging moth or select new one
            FossilMothEntity bestMoth = null;
            
            List<FossilMothEntity> chargingMoths = MothQueryUtil.getChargingMoths(user, AshesToAshesConstants.QUERY_RADIUS_CHARGING);
            if (!chargingMoths.isEmpty()) {
                bestMoth = chargingMoths.get(0);
            }
            
            if (bestMoth == null) {
                List<FossilMothEntity> moths = MothQueryUtil.getFreeMoths(user, AshesToAshesConstants.QUERY_RADIUS_CHARGING);
                
                if (!moths.isEmpty()) {
                    bestMoth = moths.stream()
                        .max(java.util.Comparator.comparingInt(FossilMothEntity::getTotalEnergy))
                        .orElse(moths.get(0));
                    bestMoth.piercingCharge(user); // Set charging state
                } else {
                    // Try to spawn a new moth
                    user.getCapability(MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
                        int slot = pool.allocateSlotWithPriority(true);
                        if (slot != -1) {
                            FossilMothEntity newMoth = new FossilMothEntity(world, user);
                            newMoth.setMothPoolIndex(slot);
                            newMoth.setPos(user.getX(), user.getY() + 1, user.getZ());
                            world.addFreshEntity(newMoth);
                            newMoth.piercingCharge(user);
                            if (user instanceof net.minecraft.entity.player.ServerPlayerEntity) {
                                pool.sync((net.minecraft.entity.player.ServerPlayerEntity)user);
                            }
                        }
                    });
                    // Re-fetch after spawn
                    chargingMoths = MothQueryUtil.getChargingMoths(user, AshesToAshesConstants.QUERY_RADIUS_CHARGING);
                    if (!chargingMoths.isEmpty()) {
                        bestMoth = chargingMoths.get(0);
                    }
                }
            }
            
            // Update position EVERY tick
            if (bestMoth != null) {
                final FossilMothEntity chargingMoth = bestMoth;
                
                // Auto-Charge from pool: if moth is below 10 energy, take from warehouse
                if (chargingMoth.getTotalEnergy() < 10) {
                    // Try to get energy from stand first
                    IStandManifestation manifestation = power.getStandManifestation();
                    boolean energyAdded = false;
                    
                    if (manifestation instanceof AshesToAshesStandEntity) {
                        AshesToAshesStandEntity stand = (AshesToAshesStandEntity) manifestation;
                        if (stand.getGlobalTotalEnergy() > 0) {
                            int hamonBefore = stand.getGlobalHamonEnergy();
                            stand.consumeEnergyPrioritizeHamon(1);
                            if (hamonBefore > 0) {
                                chargingMoth.setHamonEnergy(chargingMoth.getHamonEnergy() + 1);
                            } else {
                                chargingMoth.setKineticEnergy(chargingMoth.getKineticEnergy() + 1);
                            }
                            energyAdded = true;
                            
                            // Sync pool change
                            if (user instanceof net.minecraft.entity.player.ServerPlayerEntity) {
                                user.getCapability(MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(p -> p.sync((net.minecraft.entity.player.ServerPlayerEntity)user));
                            }
                        }
                    }
                    
                    // If no stand or stand has no energy, try to get from pool directly
                    if (!energyAdded) {
                        user.getCapability(MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
                            int totalEnergy = pool.getTotalKineticEnergy() + pool.getTotalHamonEnergy();
                            if (totalEnergy > 0) {
                                // Try to get hamon first, then kinetic
                                if (pool.getTotalHamonEnergy() > 0) {
                                    for (int i = 0; i < com.babelmoth.rotp_ata.capability.IMothPool.MAX_MOTHS; i++) {
                                        if (pool.getMothHamon(i) > 0) {
                                            pool.setMothHamon(i, pool.getMothHamon(i) - 1);
                                            chargingMoth.setHamonEnergy(chargingMoth.getHamonEnergy() + 1);
                                            break;
                                        }
                                    }
                                } else {
                                    for (int i = 0; i < com.babelmoth.rotp_ata.capability.IMothPool.MAX_MOTHS; i++) {
                                        if (pool.getMothKinetic(i) > 0) {
                                            pool.setMothKinetic(i, pool.getMothKinetic(i) - 1);
                                            chargingMoth.setKineticEnergy(chargingMoth.getKineticEnergy() + 1);
                                            break;
                                        }
                                    }
                                }
                                
                                // Sync pool change
                                if (user instanceof net.minecraft.entity.player.ServerPlayerEntity) {
                                    pool.sync((net.minecraft.entity.player.ServerPlayerEntity)user);
                                }
                            }
                        });
                    }
                }

                net.minecraft.util.math.vector.Vector3d view = user.getViewVector(1.0F);
                net.minecraft.util.math.vector.Vector3d pos = user.getEyePosition(1.0F).add(view.scale(1.5));
                
                // Use teleportTo for reliable position sync
                chargingMoth.teleportTo(pos.x, pos.y, pos.z);
                chargingMoth.setDeltaMovement(0, 0, 0);
                chargingMoth.yRot = user.yRot;
                chargingMoth.xRot = user.xRot;
                
                // Play charging sound periodically
                if (ticksHeld % 10 == 0) {
                    world.playSound(null, user.getX(), user.getY(), user.getZ(), 
                        SoundEvents.SMOKER_SMOKE, SoundCategory.PLAYERS, 0.5f, 0.5f + (ticksHeld / 40.0f));
                }
            }
        }
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (!world.isClientSide) {
            List<FossilMothEntity> chargingMoths = MothQueryUtil.getChargingMoths(user, AshesToAshesConstants.QUERY_RADIUS_CHARGING);
            
            if (!chargingMoths.isEmpty()) {
                FossilMothEntity moth = chargingMoths.get(0);
                
                // Try to consume energy from stand first, then from pool
                int toConsume = 10;
                IStandManifestation manifestation = power.getStandManifestation();
                if (manifestation instanceof AshesToAshesStandEntity) {
                    AshesToAshesStandEntity stand = (AshesToAshesStandEntity) manifestation;
                    int available = Math.min(stand.getGlobalTotalEnergy(), toConsume);
                    if (available > 0) {
                        stand.consumeEnergyPrioritizeHamon(available);
                        toConsume -= available;
                    }
                }
                
                // If still need more energy, consume from pool
                if (toConsume > 0) {
                    final int remaining = toConsume;
                    user.getCapability(MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
                        int leftToConsume = remaining;
                        // Consume hamon first
                        for (int i = 0; i < com.babelmoth.rotp_ata.capability.IMothPool.MAX_MOTHS && leftToConsume > 0; i++) {
                            int hamon = pool.getMothHamon(i);
                            if (hamon > 0) {
                                int take = Math.min(hamon, leftToConsume);
                                pool.setMothHamon(i, hamon - take);
                                leftToConsume -= take;
                            }
                        }
                        // Then kinetic
                        for (int i = 0; i < com.babelmoth.rotp_ata.capability.IMothPool.MAX_MOTHS && leftToConsume > 0; i++) {
                            int kinetic = pool.getMothKinetic(i);
                            if (kinetic > 0) {
                                int take = Math.min(kinetic, leftToConsume);
                                pool.setMothKinetic(i, kinetic - take);
                                leftToConsume -= take;
                            }
                        }
                        if (user instanceof net.minecraft.entity.player.ServerPlayerEntity) {
                            pool.sync((net.minecraft.entity.player.ServerPlayerEntity)user);
                        }
                    });
                }
                
                // Fire!
                net.minecraft.util.math.vector.Vector3d lookDir = user.getViewVector(1.0f);
                float speed = 2.5f;
                moth.piercingFire(lookDir, speed);
                
                world.playSound(null, user.getX(), user.getY(), user.getZ(), 
                    SoundEvents.GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 2.0f);
            }
        }
    }

    @Override
    public void stoppedHolding(World world, LivingEntity user, IStandPower power, int ticksHeld, boolean willFire) {
        if (!world.isClientSide) {
            List<FossilMothEntity> chargingMoths = MothQueryUtil.getChargingMoths(user, AshesToAshesConstants.QUERY_RADIUS_CHARGING);
             
            if (!chargingMoths.isEmpty()) {
                FossilMothEntity moth = chargingMoths.get(0);
                
                if (willFire) {
                    // Will be handled by perform()
                    return;
                }
                
                // Cancel - not enough charge
                moth.getEntityData().set(FossilMothEntity.IS_PIERCING_CHARGING, false);
                moth.detach();
                moth.setNoAi(false);
                moth.setNoGravity(true);
            }
        }
    }
}
