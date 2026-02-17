package com.babelmoth.rotp_ata.networking;

import com.babelmoth.rotp_ata.AddonMain;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

public class AshesToAshesPacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(AddonMain.MOD_ID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    private static int id = 0;

    public static void register() {
        CHANNEL.messageBuilder(MothPoolSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(MothPoolSyncPacket::encode)
            .decoder(MothPoolSyncPacket::decode)
            .consumer(MothPoolSyncPacket::handle)
            .add();

        CHANNEL.messageBuilder(SpearStuckSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SpearStuckSyncPacket::encode)
            .decoder(SpearStuckSyncPacket::decode)
            .consumer(SpearStuckSyncPacket::handle)
            .add();

        CHANNEL.messageBuilder(SpearMarkSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SpearMarkSyncPacket::encode)
            .decoder(SpearMarkSyncPacket::decode)
            .consumer(SpearMarkSyncPacket::handle)
            .add();

        CHANNEL.messageBuilder(SpearThornSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SpearThornSyncPacket::encode)
            .decoder(SpearThornSyncPacket::decode)
            .consumer(SpearThornSyncPacket::handle)
            .add();

    }

    public static void sendToClient(Object packet, ServerPlayerEntity player) {
        CHANNEL.sendTo(packet, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}
