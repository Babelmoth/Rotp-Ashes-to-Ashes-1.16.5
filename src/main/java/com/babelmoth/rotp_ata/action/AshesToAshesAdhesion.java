package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.text.TranslationTextComponent;
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
        // Require a block or entity target
        if (target.getType() == ActionTarget.TargetType.BLOCK) {
            return ActionConditionResult.POSITIVE;
        }
        if (target.getType() == ActionTarget.TargetType.ENTITY) {
            // Do not attach to fossil moths
            if (target.getEntity() instanceof com.babelmoth.rotp_ata.entity.FossilMothEntity) {
                return ActionConditionResult.NEGATIVE;
            }
            return ActionConditionResult.POSITIVE;
        }
        
        return ActionConditionResult.createNegative(new TranslationTextComponent("jojo.ata.message.need_target"));
    }
    
    @Override
    protected void perform(net.minecraft.world.World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (!world.isClientSide) {
            java.util.List<com.babelmoth.rotp_ata.entity.FossilMothEntity> freeMoths =
                com.babelmoth.rotp_ata.util.MothQueryUtil.getViewpointFreeMoths(user, 64);
            
            freeMoths.sort(java.util.Comparator.comparingInt(com.babelmoth.rotp_ata.entity.FossilMothEntity::getKineticEnergy));
            
            com.babelmoth.rotp_ata.entity.FossilMothEntity activeMoth = null;
            boolean isNewMoth = false;
            
            if (!freeMoths.isEmpty()) {
                activeMoth = freeMoths.get(0);
            } else {
                boolean canSpawn = user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY)
                    .map(pool -> pool.getTotalMoths() < com.babelmoth.rotp_ata.capability.IMothPool.MAX_MOTHS)
                    .orElse(false);
                
                if (canSpawn) {
                    activeMoth = new com.babelmoth.rotp_ata.entity.FossilMothEntity(world, user);
                    isNewMoth = true;
                }
            }
            
            if (activeMoth != null) {
            if (target.getType() == ActionTarget.TargetType.BLOCK) {
                    activeMoth.attachTo(target.getBlockPos(), target.getFace());
                } else if (target.getType() == ActionTarget.TargetType.ENTITY) {
                    activeMoth.attachToEntity(target.getEntity());
                }
                
                if (isNewMoth) {
                    world.addFreshEntity(activeMoth);
                }
            }
        }
    }
}
