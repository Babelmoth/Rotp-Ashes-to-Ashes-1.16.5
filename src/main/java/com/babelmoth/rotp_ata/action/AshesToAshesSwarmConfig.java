package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.networking.AshesToAshesPacketHandler;
import com.babelmoth.rotp_ata.networking.OpenMothConfigScreenPacket;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.World;

/**
 * Opens the Moth Swarm Configuration screen.
 * Placed at the end of the right-click hotbar.
 */
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
            // Sync pool data first so client has latest info
            ServerPlayerEntity player = (ServerPlayerEntity) user;
            player.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY)
                    .ifPresent(pool -> pool.sync(player));
            // Tell client to open config screen
            AshesToAshesPacketHandler.sendToClient(new OpenMothConfigScreenPacket(), player);
        }
    }
}
