package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import java.util.List;
import java.util.ArrayList;

public class AshesToAshesSwarmGuardian extends StandAction {

    private static final int GUARDIAN_MOTH_COUNT = 10;

    public AshesToAshesSwarmGuardian(AbstractBuilder<?> builder) {
        super(builder);
    }
    
    @Override
    public String getTranslationKey(IStandPower power, ActionTarget target) {
        return "action.rotp_ata.ashes_to_ashes_swarm_shield_target";
    }

    @Override
    public ActionConditionResult checkConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        // Allow action regardless of stand state - we'll summon in perform
        return ActionConditionResult.POSITIVE;
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (!world.isClientSide) {
            // Force summon stand if not active
            if (!power.isActive() && power.getType() instanceof com.github.standobyte.jojo.power.impl.stand.type.EntityStandType) {
                ((com.github.standobyte.jojo.power.impl.stand.type.EntityStandType<?>) power.getType())
                    .summon(user, power, entity -> {}, true, true);
            }
            
            // Get all owned moths
            List<FossilMothEntity> allMoths = MothQueryUtil.getOwnerMoths(user, AshesToAshesConstants.QUERY_RADIUS_GUARDIAN);
            
            // Count current guardian moths and available moths
            List<FossilMothEntity> currentGuardians = new ArrayList<>();
            List<FossilMothEntity> availableMoths = new ArrayList<>();
            
            for (FossilMothEntity moth : allMoths) {
                if (moth.isShieldPersistent()) {
                    currentGuardians.add(moth);
                } else if (!moth.isAttached() && !moth.isAttachedToEntity() && !moth.isRecalling() 
                        && !moth.isPiercingFiring() && !moth.isPiercingCharging()) {
                    availableMoths.add(moth);
                }
            }
            
            int currentCount = currentGuardians.size();
            int needMore = GUARDIAN_MOTH_COUNT - currentCount;
            
            // If we already have 10, toggle them off
            if (currentCount >= GUARDIAN_MOTH_COUNT) {
                // Disable all guardian moths
                for (FossilMothEntity moth : currentGuardians) {
                    moth.setShieldTarget(null);
                }
                return;
            }
            
            // Need to recruit more guardian moths
            // First, try to spawn new moths if we don't have enough
            int totalAvailable = availableMoths.size();
            if (totalAvailable < needMore) {
                int toSpawn = needMore - totalAvailable;
                user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
                    for (int i = 0; i < toSpawn; i++) {
                        int slot = pool.allocateSlotWithPriority(true);
                        if (slot != -1) {
                            FossilMothEntity moth = new FossilMothEntity(world, user);
                            moth.setMothPoolIndex(slot);
                            moth.setPos(user.getX(), user.getY() + 1, user.getZ());
                            world.addFreshEntity(moth);
                            availableMoths.add(moth);
                        }
                    }
                    if (user instanceof net.minecraft.entity.player.ServerPlayerEntity) {
                        pool.sync((net.minecraft.entity.player.ServerPlayerEntity)user);
                    }
                });
            }
            
            // Recruit available moths as guardians until we have 10
            int recruited = 0;
            for (FossilMothEntity moth : availableMoths) {
                if (currentCount + recruited >= GUARDIAN_MOTH_COUNT) break;
                moth.setShieldTarget(user, true); // Persistent shield on owner
                moth.refreshShield();
                moth.detach();
                recruited++;
            }
            
            // Refresh all existing guardian moths
            for (FossilMothEntity moth : currentGuardians) {
                moth.setShieldTarget(user, true);
                moth.refreshShield();
            }
        }
    }
}
