package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.github.standobyte.jojo.util.mc.MCUtil;

import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import java.util.List;

public class AshesToAshesSwarmRecall extends StandAction {

    public AshesToAshesSwarmRecall(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (!world.isClientSide) {
            // 扫描所有属于该用户的化石蛾并回收
            List<FossilMothEntity> activeMoths = MCUtil.entitiesAround(
                FossilMothEntity.class, user, 256, false, 
                moth -> moth.isAlive() && moth.getOwner() == user && (moth.isAttached() || moth.isAttachedToEntity()));
            
            for (FossilMothEntity moth : activeMoths) {
                moth.recall();
            }
        }
    }
}
