package com.babelmoth.rotp_ata.networking;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class SpearThornSyncPacket {
    private final int entityId;
    private final int thornCount;
    private final float damageDealt;
    private final float detachThreshold;
    private final boolean hasSpear;

    public SpearThornSyncPacket(int entityId, int thornCount, float damageDealt, float detachThreshold, boolean hasSpear) {
        this.entityId = entityId;
        this.thornCount = thornCount;
        this.damageDealt = damageDealt;
        this.detachThreshold = detachThreshold;
        this.hasSpear = hasSpear;
    }

    public static void encode(SpearThornSyncPacket msg, PacketBuffer buf) {
        buf.writeInt(msg.entityId);
        buf.writeVarInt(msg.thornCount);
        buf.writeFloat(msg.damageDealt);
        buf.writeFloat(msg.detachThreshold);
        buf.writeBoolean(msg.hasSpear);
    }

    public static SpearThornSyncPacket decode(PacketBuffer buf) {
        return new SpearThornSyncPacket(buf.readInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    public int getEntityId() { return entityId; }
    public int getThornCount() { return thornCount; }
    public float getDamageDealt() { return damageDealt; }
    public float getDetachThreshold() { return detachThreshold; }
    public boolean hasSpear() { return hasSpear; }

    private static Consumer<SpearThornSyncPacket> clientHandler;
    public static void setClientHandler(Consumer<SpearThornSyncPacket> handler) { clientHandler = handler; }

    public static void handle(SpearThornSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> { if (clientHandler != null) clientHandler.accept(msg); });
        ctx.get().setPacketHandled(true);
    }
}
