package com.babelmoth.rotp_ata.networking;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class S2CAdaptationRotationPacket {
    private final UUID playerId;

    public S2CAdaptationRotationPacket(UUID playerId) {
        this.playerId = playerId;
    }

    public static void encode(S2CAdaptationRotationPacket msg, PacketBuffer buffer) {
        buffer.writeUUID(msg.playerId);
    }

    public static S2CAdaptationRotationPacket decode(PacketBuffer buffer) {
        return new S2CAdaptationRotationPacket(buffer.readUUID());
    }

    public UUID getPlayerId() { return playerId; }

    private static Consumer<S2CAdaptationRotationPacket> clientHandler;
    public static void setClientHandler(Consumer<S2CAdaptationRotationPacket> handler) { clientHandler = handler; }

    public static void handle(S2CAdaptationRotationPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> { if (clientHandler != null) clientHandler.accept(msg); });
        ctx.get().setPacketHandled(true);
    }
}
