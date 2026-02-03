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

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
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
    public ActionConditionResult checkSpecificConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        // Only block if Stand exists AND is being manually controlled (Remote Control)
        // If Stand is not summoned, this should pass.
        IStandManifestation manifestation = power.getStandManifestation();
        if (manifestation instanceof com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity) {
            com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity stand = (com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity) manifestation;
            if (stand.isManuallyControlled()) {
                return ActionConditionResult.NEGATIVE;
            }
            // Require at least 1 energy to start charge
            if (stand.getGlobalTotalEnergy() <= 0) {
                return ActionConditionResult.NEGATIVE;
            }
        }
        return ActionConditionResult.POSITIVE;
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
                }
            }
            
                // Update position EVERY tick
                if (bestMoth != null) {
                    // Auto-Charge from pool: if moth is below 10 energy, take from warehouse
                    if (bestMoth.getTotalEnergy() < 10) {
                        IStandManifestation manifestation = power.getStandManifestation();
                        if (manifestation instanceof AshesToAshesStandEntity) {
                            AshesToAshesStandEntity stand = (AshesToAshesStandEntity) manifestation;
                            if (stand.getGlobalTotalEnergy() > 0) {
                                int hamonBefore = stand.getGlobalHamonEnergy();
                                stand.consumeEnergyPrioritizeHamon(1);
                                if (hamonBefore > 0) {
                                    bestMoth.setHamonEnergy(bestMoth.getHamonEnergy() + 1);
                                } else {
                                    bestMoth.setKineticEnergy(bestMoth.getKineticEnergy() + 1);
                                }
                                
                                // Sync pool change
                                if (user instanceof net.minecraft.entity.player.ServerPlayerEntity) {
                                    user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(p -> p.sync((net.minecraft.entity.player.ServerPlayerEntity)user));
                                }
                            }
                        }
                    }

                    net.minecraft.util.math.vector.Vector3d view = user.getViewVector(1.0F);
                net.minecraft.util.math.vector.Vector3d pos = user.getEyePosition(1.0F).add(view.scale(1.5));
                
                // Use teleportTo for reliable position sync
                bestMoth.teleportTo(pos.x, pos.y, pos.z);
                bestMoth.setDeltaMovement(0, 0, 0);
                bestMoth.yRot = user.yRot;
                bestMoth.xRot = user.xRot;
                
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
                
                IStandManifestation manifestation = power.getStandManifestation();
                if (manifestation instanceof com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity) {
                    com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity stand = (com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity) manifestation;
                    
                    // Consume up to 10 energy for the shot
                    int toConsume = Math.min(stand.getGlobalTotalEnergy(), 10);
                    stand.consumeEnergyPrioritizeHamon(toConsume);
                    
                    // Fire!
                    net.minecraft.util.math.vector.Vector3d lookDir = user.getViewVector(1.0f);
                    float speed = 2.5f;
                    moth.piercingFire(lookDir, speed);
                    
                    world.playSound(null, user.getX(), user.getY(), user.getZ(), 
                        SoundEvents.GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 2.0f);
                }
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
