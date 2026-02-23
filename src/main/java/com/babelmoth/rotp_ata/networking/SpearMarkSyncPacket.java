package com.babelmoth.rotp_ata.networking;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class SpearMarkSyncPacket {
    private final int targetEntityId;
    private final int ownerEntityId;
    private final int durationTicks;

    public SpearMarkSyncPacket(int targetEntityId, int ownerEntityId, int durationTicks) {
        this.targetEntityId = targetEntityId;
        this.ownerEntityId = ownerEntityId;
        this.durationTicks = durationTicks;
    }

    public static void encode(SpearMarkSyncPacket msg, PacketBuffer buf) {
        buf.writeInt(msg.targetEntityId);
        buf.writeInt(msg.ownerEntityId);
        buf.writeVarInt(msg.durationTicks);
    }

    public static SpearMarkSyncPacket decode(PacketBuffer buf) {
        return new SpearMarkSyncPacket(buf.readInt(), buf.readInt(), buf.readVarInt());
    }

    public int getTargetEntityId() { return targetEntityId; }
    public int getOwnerEntityId() { return ownerEntityId; }
    public int getDurationTicks() { return durationTicks; }

    private static Consumer<SpearMarkSyncPacket> clientHandler;
    public static void setClientHandler(Consumer<SpearMarkSyncPacket> handler) { clientHandler = handler; }

    public static void handle(SpearMarkSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> { if (clientHandler != null) clientHandler.accept(msg); });
        ctx.get().setPacketHandled(true);
    }
}
