package com.babelmoth.rotp_ata.action;

import java.util.List;

import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.github.standobyte.jojo.power.impl.stand.IStandManifestation;
import com.github.standobyte.jojo.util.mc.MCUtil;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;

public class AshesToAshesMothJet extends StandAction {

    public AshesToAshesMothJet(AbstractBuilder<?> builder) {
        super(builder);
    }
    
    @Override
    public TargetRequirement getTargetRequirement() {
        return TargetRequirement.NONE;
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (!world.isClientSide) {
            Entity targetEntity = null;
            
            double range = 64.0D;
            net.minecraft.util.math.vector.Vector3d eyePos;
            net.minecraft.util.math.vector.Vector3d lookVec;
            Entity viewEntity = user;
            
            IStandManifestation stand = power.getStandManifestation();
            if (stand instanceof StandEntity) {
                StandEntity standEntity = (StandEntity) stand;
                if (standEntity.isManuallyControlled()) {
                    viewEntity = standEntity;
                    eyePos = standEntity.getEyePosition(1.0F);
                    lookVec = standEntity.getViewVector(1.0F);
                } else {
                    eyePos = user.getEyePosition(1.0F);
                    lookVec = user.getViewVector(1.0F);
                }
            } else {
                eyePos = user.getEyePosition(1.0F);
                lookVec = user.getViewVector(1.0F);
            }
            
            net.minecraft.util.math.vector.Vector3d maxVec = eyePos.add(lookVec.x * range, lookVec.y * range, lookVec.z * range);
            net.minecraft.util.math.AxisAlignedBB aabb = viewEntity.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(1.0D, 1.0D, 1.0D);
            
            final Entity finalViewEntity = viewEntity;
            net.minecraft.util.math.EntityRayTraceResult result = net.minecraft.entity.projectile.ProjectileHelper.getEntityHitResult(
                viewEntity, 
                eyePos, 
                maxVec, 
                aabb, 
                entity -> !entity.isSpectator() && entity.isPickable() && entity instanceof LivingEntity && entity != user && entity != finalViewEntity && !(entity instanceof FossilMothEntity), 
                range * range
            );
            
            if (result != null) {
                targetEntity = result.getEntity();
            }

            if (targetEntity != null) {
                AshesToAshesStandEntity standEntity = (stand instanceof AshesToAshesStandEntity) ? (AshesToAshesStandEntity)stand : null;
                if (standEntity != null) {
                    final Entity finalTarget = targetEntity; 
                    int burstCount = 10;
                    user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
                        for (int i = 0; i < burstCount; i++) {
                            int slot = pool.allocateSlotWithPriority(true); // Draw from empty moths in warehouse
                            if (slot != -1) {
                                FossilMothEntity newMoth = new FossilMothEntity(world, user);
                                newMoth.setMothPoolIndex(slot);
                                
                                net.minecraft.util.math.vector.Vector3d dir = finalTarget.position().subtract(user.position()).normalize();
                                dir = dir.add((world.random.nextDouble()-0.5)*0.5, (world.random.nextDouble()-0.5)*0.5, (world.random.nextDouble()-0.5)*0.5);
                                
                                newMoth.setDeltaMovement(dir.scale(1.5));
                                newMoth.setPos(user.getX(), user.getEyeY(), user.getZ());
                                newMoth.swarmTo(finalTarget);
                                world.addFreshEntity(newMoth);
                            }
                        }
                        if (user instanceof net.minecraft.entity.player.ServerPlayerEntity) {
                            pool.sync((net.minecraft.entity.player.ServerPlayerEntity)user);
                        }
                    });
                }
            }
        }
    }
}
