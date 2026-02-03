package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;

public class AshesToAshesAdhesion extends StandAction {

    public AshesToAshesAdhesion(AbstractBuilder<?> builder) {
        super(builder);
    }
    
    @Override
    public TargetRequirement getTargetRequirement() {
        return TargetRequirement.ANY;
    }
    
    @Override
    public ActionConditionResult checkConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        // Shift+左键: 目标是自己 (不需要目标检测)
        if (user.isShiftKeyDown()) {
            return ActionConditionResult.POSITIVE;
        }
        
        // 普通左键: 需要目标是方块或实体
        if (target.getType() == ActionTarget.TargetType.BLOCK) {
            return ActionConditionResult.POSITIVE;
        }
        if (target.getType() == ActionTarget.TargetType.ENTITY) {
            // 禁止依附到化石蛾自己身上
            if (target.getEntity() instanceof com.babelmoth.rotp_ata.entity.FossilMothEntity) {
                return ActionConditionResult.NEGATIVE;
            }
            return ActionConditionResult.POSITIVE;
        }
        
        return ActionConditionResult.createNegative(new StringTextComponent("Need target"));
    }
    
    @Override
    protected void perform(net.minecraft.world.World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (!world.isClientSide) {
            // 1. 寻找空闲蛾子 (Prioritize Low Energy)
            java.util.List<com.babelmoth.rotp_ata.entity.FossilMothEntity> freeMoths = com.github.standobyte.jojo.util.mc.MCUtil.entitiesAround(
                com.babelmoth.rotp_ata.entity.FossilMothEntity.class, user, 64, false, 
                moth -> moth.isAlive() && moth.getOwner() == user && !moth.isAttached() && !moth.isAttachedToEntity());
            
            // Sort by Kinetic Energy (Ascending)
            freeMoths.sort(java.util.Comparator.comparingInt(com.babelmoth.rotp_ata.entity.FossilMothEntity::getKineticEnergy));
            
            com.babelmoth.rotp_ata.entity.FossilMothEntity activeMoth = null;
            boolean isNewMoth = false;
            
            if (!freeMoths.isEmpty()) {
                activeMoth = freeMoths.get(0);
            } else {
                // 2. 如果没有空闲蛾子，检查仓库容量
                boolean canSpawn = user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY)
                    .map(pool -> pool.getTotalMoths() < com.babelmoth.rotp_ata.capability.IMothPool.MAX_MOTHS)
                    .orElse(false);
                
                if (canSpawn) {
                    // 生成新蛾子
                    activeMoth = new com.babelmoth.rotp_ata.entity.FossilMothEntity(world, user);
                    isNewMoth = true;
                }
            }
            
            // 3. 执行依附
            if (activeMoth != null) {
                // Shift+左键: 依附到使用者自己身上
                if (user.isShiftKeyDown()) {
                    activeMoth.attachToEntity(user);
                } else if (target.getType() == ActionTarget.TargetType.BLOCK) {
                    activeMoth.attachTo(target.getBlockPos(), target.getFace());
                } else if (target.getType() == ActionTarget.TargetType.ENTITY) {
                    activeMoth.attachToEntity(target.getEntity());
                }
                
                // 如果是新生成的蛾子，现在加入世界（此时位置已被 attachTo 设置正确）
                if (isNewMoth) {
                    world.addFreshEntity(activeMoth);
                }
            }
        }
    }
}
