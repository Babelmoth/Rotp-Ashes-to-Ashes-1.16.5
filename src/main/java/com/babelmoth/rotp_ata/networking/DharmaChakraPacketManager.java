package com.babelmoth.rotp_ata.networking;

import com.babelmoth.rotp_ata.AddonMain;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

public class DharmaChakraPacketManager {
    private static final String PROTOCOL_VERSION = "1";
    public static SimpleChannel INSTANCE;
    private static int ID = 0;

    public static void init() {
        INSTANCE = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(AddonMain.MOD_ID, "dharma"))
                .clientAcceptedVersions(PROTOCOL_VERSION::equals)
                .serverAcceptedVersions(PROTOCOL_VERSION::equals)
                .networkProtocolVersion(() -> PROTOCOL_VERSION)
                .simpleChannel();

        INSTANCE.registerMessage(ID++, S2CAdaptationRotationPacket.class,
                S2CAdaptationRotationPacket::encode,
                S2CAdaptationRotationPacket::decode,
                S2CAdaptationRotationPacket::handle);

        INSTANCE.registerMessage(ID++, S2CAdaptationSyncPacket.class,
                S2CAdaptationSyncPacket::encode,
                S2CAdaptationSyncPacket::decode,
                S2CAdaptationSyncPacket::handle);

        INSTANCE.registerMessage(ID++, C2SAdaptationEffectsPacket.class,
                C2SAdaptationEffectsPacket::encode,
                C2SAdaptationEffectsPacket::decode,
                C2SAdaptationEffectsPacket::handle);
    }

    public static void sendToServer(Object msg) {
        INSTANCE.sendToServer(msg);
    }

    public static void sendToClient(Object msg, ServerPlayerEntity player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendToClientsTrackingAndSelf(Object msg, net.minecraft.entity.Entity entity) {
        INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), msg);
    }
}
