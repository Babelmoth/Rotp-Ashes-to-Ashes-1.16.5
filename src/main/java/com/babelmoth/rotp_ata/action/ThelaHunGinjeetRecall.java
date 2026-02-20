package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.entity.ThelaHunGinjeetSpearEntity;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

public class ThelaHunGinjeetRecall extends StandAction {
    private static final double SEARCH_RADIUS = 128.0;

    public ThelaHunGinjeetRecall(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public TargetRequirement getTargetRequirement() {
        return TargetRequirement.NONE;
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (world.isClientSide) {
            return;
        }
        if (!(user instanceof PlayerEntity)) {
            return;
        }
        PlayerEntity player = (PlayerEntity) user;

        AxisAlignedBB box = player.getBoundingBox().inflate(SEARCH_RADIUS);
        boolean recalled = false;
        for (ThelaHunGinjeetSpearEntity spear : world.getEntitiesOfClass(ThelaHunGinjeetSpearEntity.class, box,
                e -> e.isAlive() && !e.isRecalled() && player.equals(e.getOwner()))) {
            spear.setRecalled(true);
            recalled = true;
        }
        if (recalled) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.util.SoundEvents.TRIDENT_RETURN, net.minecraft.util.SoundCategory.PLAYERS, 1.0F, 0.8F);
        }
    }
}
