package com.babelmoth.rotp_ata.action;

import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;

public class AshesToAshesSwarmShield extends StandAction {

    public AshesToAshesSwarmShield(AbstractBuilder<?> builder) {
        super(builder);
    }
    
    @Override
    public String getTranslationKey(IStandPower power, ActionTarget target) {
        return "action.rotp_ata.ashes_to_ashes_swarm_shield_self";
    }

    @Override
    public int getHoldDurationMax(IStandPower standPower) {
        return 0; // Instant toggle
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (!world.isClientSide) {
            com.github.standobyte.jojo.power.impl.stand.IStandManifestation manifestation = power.getStandManifestation();
            if (manifestation instanceof com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity) {
                ((com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity) manifestation).toggleShield();
            }
        }
    }
}
