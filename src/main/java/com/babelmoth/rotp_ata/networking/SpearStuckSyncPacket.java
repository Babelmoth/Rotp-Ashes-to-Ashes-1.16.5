package com.babelmoth.rotp_ata.networking;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class SpearStuckSyncPacket {
    private final int entityId;
    private final int spearCount;

    public SpearStuckSyncPacket(int entityId, int spearCount) {
        this.entityId = entityId;
        this.spearCount = spearCount;
    }

    public static void encode(SpearStuckSyncPacket msg, PacketBuffer buf) {
        buf.writeInt(msg.entityId);
        buf.writeVarInt(msg.spearCount);
    }

    public static SpearStuckSyncPacket decode(PacketBuffer buf) {
        return new SpearStuckSyncPacket(buf.readInt(), buf.readVarInt());
    }

    public int getEntityId() { return entityId; }
    public int getSpearCount() { return spearCount; }

    private static Consumer<SpearStuckSyncPacket> clientHandler;
    public static void setClientHandler(Consumer<SpearStuckSyncPacket> handler) { clientHandler = handler; }

    public static void handle(SpearStuckSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> { if (clientHandler != null) clientHandler.accept(msg); });
        ctx.get().setPacketHandled(true);
    }
}
