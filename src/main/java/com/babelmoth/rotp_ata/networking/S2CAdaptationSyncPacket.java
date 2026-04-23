package com.babelmoth.rotp_ata.networking;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class S2CAdaptationSyncPacket {
    private final int entityId;
    private final CompoundNBT nbt;

    public S2CAdaptationSyncPacket(int entityId, CompoundNBT nbt) {
        this.entityId = entityId;
        this.nbt = nbt;
    }

    public static void encode(S2CAdaptationSyncPacket msg, PacketBuffer buffer) {
        buffer.writeInt(msg.entityId);
        buffer.writeNbt(msg.nbt);
    }

    public static S2CAdaptationSyncPacket decode(PacketBuffer buffer) {
        return new S2CAdaptationSyncPacket(buffer.readInt(), buffer.readNbt());
    }

    public int getEntityId() { return entityId; }
    public CompoundNBT getNbt() { return nbt; }

    private static Consumer<S2CAdaptationSyncPacket> clientHandler;
    public static void setClientHandler(Consumer<S2CAdaptationSyncPacket> handler) { clientHandler = handler; }

    public static void handle(S2CAdaptationSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> { if (clientHandler != null) clientHandler.accept(msg); });
        ctx.get().setPacketHandled(true);
    }
}
