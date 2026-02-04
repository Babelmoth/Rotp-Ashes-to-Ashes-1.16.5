package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.world.World;
import java.util.List;

public class AshesToAshesSwarmGuardian extends StandAction {

    public AshesToAshesSwarmGuardian(AbstractBuilder<?> builder) {
        super(builder);
    }
    
    @Override
    public String getTranslationKey(IStandPower power, ActionTarget target) {
        return "action.rotp_ata.ashes_to_ashes_swarm_shield_target";
    }

    @Override
    public void onClick(World world, LivingEntity user, IStandPower power) {
        if (!world.isClientSide) {
            // Force summon stand if not active
            if (!power.isActive() && power.getType() instanceof com.github.standobyte.jojo.power.impl.stand.type.EntityStandType) {
                ((com.github.standobyte.jojo.power.impl.stand.type.EntityStandType<?>) power.getType())
                    .summon(user, power, entity -> {}, true, false);
            }
        }
        super.onClick(world, user, power);
    }
    
    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (!world.isClientSide) {
            // Ensure at least 10 moths exist
            int activeCount = MothQueryUtil.getOwnerMoths(user, 128.0).size();
            if (activeCount < 10) {
               user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
                    int toSpawn = 10 - activeCount;
                    for (int i = 0; i < toSpawn; i++) {
                        int slot = pool.allocateSlotWithPriority(true);
                        if (slot != -1) {
                            FossilMothEntity moth = new FossilMothEntity(world, user);
                            moth.setMothPoolIndex(slot);
                            moth.setPos(user.getX(), user.getY() + 1, user.getZ());
                            world.addFreshEntity(moth);
                        }
                    }
                    if (user instanceof net.minecraft.entity.player.ServerPlayerEntity) {
                        pool.sync((net.minecraft.entity.player.ServerPlayerEntity)user);
                    }
                });
            }
            
            net.minecraft.util.math.vector.Vector3d start = user.getEyePosition(1.0F);
            net.minecraft.util.math.vector.Vector3d look = user.getViewVector(1.0F);
            double range = AshesToAshesConstants.QUERY_RADIUS_CHARGING;
            net.minecraft.util.math.vector.Vector3d end = start.add(look.x * range, look.y * range, look.z * range);
            net.minecraft.util.math.AxisAlignedBB bb = user.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D, 1.0D, 1.0D);
            
            EntityRayTraceResult result = net.minecraft.entity.projectile.ProjectileHelper.getEntityHitResult(
                user, start, end, bb, 
                e -> e instanceof LivingEntity && e != user && !(e instanceof FossilMothEntity) && !e.isSpectator(), 
                range * range
            );
            
            Entity persistentTarget = null;
            if (result != null) {
                persistentTarget = result.getEntity();
            }
            
            // Get all owner moths, but prioritize non-guardian moths for other tasks
            List<FossilMothEntity> allMoths = MothQueryUtil.getOwnerMoths(user, AshesToAshesConstants.QUERY_RADIUS_GUARDIAN);
            
            // Separate guardian moths (first 10, or those already in guardian mode)
            List<FossilMothEntity> guardianMoths = new java.util.ArrayList<>();
            List<FossilMothEntity> otherMoths = new java.util.ArrayList<>();
            
            for (FossilMothEntity moth : allMoths) {
                if (moth.isShieldPersistent() || guardianMoths.size() < 10) {
                    guardianMoths.add(moth);
                } else {
                    otherMoths.add(moth);
                }
            }
            
            // Ensure exactly 10 guardian moths
            while (guardianMoths.size() < 10 && !otherMoths.isEmpty()) {
                guardianMoths.add(otherMoths.remove(0));
            }
            
            // Set guardian moths to shield target
            for (FossilMothEntity moth : guardianMoths) {
                if (persistentTarget != null) {
                    moth.setShieldTarget(persistentTarget, true);
                    moth.refreshShield();
                    moth.detach();
                } else {
                    if (moth.isShieldPersistent()) {
                        moth.setShieldTarget(null);
                    }
                }
            }
        }
    }
}
