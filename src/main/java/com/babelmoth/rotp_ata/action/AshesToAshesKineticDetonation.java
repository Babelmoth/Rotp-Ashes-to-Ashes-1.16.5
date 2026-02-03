package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;

import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import java.util.List;

public class AshesToAshesKineticDetonation extends StandAction {

    public AshesToAshesKineticDetonation(AbstractBuilder<?> builder) {
        super(builder);
    }
    
    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (!world.isClientSide) {
            List<FossilMothEntity> moths = MothQueryUtil.getMothsWithEnergy(user, AshesToAshesConstants.QUERY_RADIUS_SWARM);
            for (FossilMothEntity moth : moths) {
                moth.detonateKinetic();
            }
        }
    }
}
