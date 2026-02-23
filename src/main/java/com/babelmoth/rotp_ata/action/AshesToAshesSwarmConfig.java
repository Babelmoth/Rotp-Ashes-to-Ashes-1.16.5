package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.networking.AshesToAshesPacketHandler;
import com.babelmoth.rotp_ata.networking.OpenMothConfigScreenPacket;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.World;

public class AshesToAshesSwarmConfig extends StandAction {

    public AshesToAshesSwarmConfig(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public TargetRequirement getTargetRequirement() {
        return TargetRequirement.NONE;
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (!world.isClientSide && user instanceof ServerPlayerEntity) {

            ServerPlayerEntity player = (ServerPlayerEntity) user;
            player.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY)
                    .ifPresent(pool -> pool.sync(player));

            AshesToAshesPacketHandler.sendToClient(new OpenMothConfigScreenPacket(), player);
        }
    }
}
