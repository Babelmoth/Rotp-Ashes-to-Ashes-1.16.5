package com.babelmoth.rotp_ata.networking;

import com.babelmoth.rotp_ata.capability.MothPoolProvider;
import com.babelmoth.rotp_ata.capability.SpearStuckProvider;
import com.babelmoth.rotp_ata.capability.SpearThornProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.OptionalInt;

public class ClientPacketHandler {

    public static void registerHandlers() {
        MothPoolSyncPacket.setClientHandler(ClientPacketHandler::handleMothPoolSync);
        SpearStuckSyncPacket.setClientHandler(ClientPacketHandler::handleSpearStuckSync);
        SpearMarkSyncPacket.setClientHandler(ClientPacketHandler::handleSpearMarkSync);
        SpearThornSyncPacket.setClientHandler(ClientPacketHandler::handleSpearThornSync);
        OpenMothConfigScreenPacket.setClientHandler(ClientPacketHandler::handleOpenMothConfigScreen);
    }

    public static void handleMothPoolSync(MothPoolSyncPacket msg) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.getCapability(MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
                pool.deserializeNBT(msg.getNbt());
            });
        }
    }

    public static void handleSpearStuckSync(SpearStuckSyncPacket msg) {
        Entity entity = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getEntity(msg.getEntityId()) : null;
        if (entity != null) {
            entity.getCapability(SpearStuckProvider.SPEAR_STUCK_CAPABILITY).ifPresent(
                    cap -> cap.setSpearCount(msg.getSpearCount()));
        }
    }

    private static final int PURPLE_COLOR = 0x8B00FF;

    public static void handleSpearMarkSync(SpearMarkSyncPacket msg) {
        if (Minecraft.getInstance().level == null) return;
        Entity clientPlayer = Minecraft.getInstance().player;
        if (clientPlayer == null || clientPlayer.getId() != msg.getOwnerEntityId()) return;

        Entity target = Minecraft.getInstance().level.getEntity(msg.getTargetEntityId());
        if (target instanceof LivingEntity) {
            target.getCapability(com.github.standobyte.jojo.capability.entity.EntityUtilCapProvider.CAPABILITY).ifPresent(cap -> {
                cap.setClGlowingColor(OptionalInt.of(PURPLE_COLOR), msg.getDurationTicks());
            });
        }
    }

    public static void handleSpearThornSync(SpearThornSyncPacket msg) {
        Entity entity = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getEntity(msg.getEntityId()) : null;
        if (entity != null) {
            entity.getCapability(SpearThornProvider.SPEAR_THORN_CAPABILITY).ifPresent(cap -> {
                cap.setThornCount(msg.getThornCount());
                cap.setDamageDealt(msg.getDamageDealt());
                cap.setDetachThreshold(msg.getDetachThreshold());
                cap.setHasSpear(msg.hasSpear());
            });
        }
    }

    public static void handleOpenMothConfigScreen(OpenMothConfigScreenPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new com.babelmoth.rotp_ata.client.screen.MothConfigScreen());
        }
    }
}
