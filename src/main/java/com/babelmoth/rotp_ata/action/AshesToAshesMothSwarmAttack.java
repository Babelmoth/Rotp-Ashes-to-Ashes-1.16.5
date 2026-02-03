package com.babelmoth.rotp_ata.action;

import java.util.List;

import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.github.standobyte.jojo.power.impl.stand.IStandManifestation;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.world.World;

public class AshesToAshesMothSwarmAttack extends StandAction {

    public AshesToAshesMothSwarmAttack(AbstractBuilder<?> builder) {
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
            
            double range = AshesToAshesConstants.QUERY_RADIUS_SWARM;
            net.minecraft.util.math.vector.Vector3d eyePos;
            net.minecraft.util.math.vector.Vector3d lookVec;
            Entity viewEntity = user; // Default to player
            
            IStandManifestation stand = power.getStandManifestation();
            if (stand instanceof StandEntity) {
                StandEntity standEntity = (StandEntity) stand;
                if (standEntity.isManuallyControlled()) {
                    // Use Stand's perspective when in remote control mode
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
            // 选择器：生物（扑击/依附）或 物品实体（捡回）
            net.minecraft.util.math.EntityRayTraceResult result = net.minecraft.entity.projectile.ProjectileHelper.getEntityHitResult(
                viewEntity, 
                eyePos, 
                maxVec, 
                aabb, 
                entity -> {
                    // 排除观察者和自己
                    if (entity.isSpectator() || entity == user || entity == finalViewEntity || entity instanceof FossilMothEntity) {
                        return false;
                    }
                    // 检查是否为生物实体
                    if (entity instanceof LivingEntity) {
                        return entity.isPickable() && ((LivingEntity) entity).isAlive();
                    }
                    // 检查是否为物品实体
                    if (entity instanceof ItemEntity) {
                        ItemEntity itemEntity = (ItemEntity) entity;
                        // 确保物品实体有效：未移除、物品不为空、未被其他飞蛾领取
                        return !itemEntity.removed 
                            && !itemEntity.getItem().isEmpty()
                            && !itemEntity.getPersistentData().getBoolean("ata_retrieved");
                    }
                    return false;
                }, 
                range * range
            );
            
            if (result != null) {
                targetEntity = result.getEntity();
            }

            if (targetEntity != null) {
                List<FossilMothEntity> moths = MothQueryUtil.getMothsForSwarm(user, range);
                for (FossilMothEntity moth : moths) {
                    moth.swarmTo(targetEntity);
                }
            }
        }
    }
}

