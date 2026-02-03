package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;

import net.minecraft.entity.LivingEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.world.World;

import java.util.List;
import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;

public class AshesToAshesMothRecall extends StandAction {

    public AshesToAshesMothRecall(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (!world.isClientSide) {
            List<FossilMothEntity> attachedMoths = MothQueryUtil.getAttachedMoths(user, AshesToAshesConstants.QUERY_RADIUS_GUARDIAN);
            List<FossilMothEntity> blockMoths = MothQueryUtil.getOwnerMoths(user, AshesToAshesConstants.QUERY_RADIUS_GUARDIAN);
            blockMoths.removeIf(m -> !m.isAttached());
            
            java.util.ArrayList<FossilMothEntity> activeMoths = new java.util.ArrayList<>(attachedMoths);
            activeMoths.addAll(blockMoths);
            
            if (!activeMoths.isEmpty()) {
                activeMoths.sort((m1, m2) -> Double.compare(m2.distanceToSqr(user), m1.distanceToSqr(user)));
                activeMoths.get(0).recall();
            }
        }
    }
}
