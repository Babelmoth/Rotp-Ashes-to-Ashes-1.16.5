package com.babelmoth.rotp_ata.networking;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class MothPoolSyncPacket {
    private final CompoundNBT nbt;

    public MothPoolSyncPacket(CompoundNBT nbt) {
        this.nbt = nbt;
    }

    public static void encode(MothPoolSyncPacket msg, PacketBuffer buf) {
        buf.writeNbt(msg.nbt);
    }

    public static MothPoolSyncPacket decode(PacketBuffer buf) {
        return new MothPoolSyncPacket(buf.readNbt());
    }

    public CompoundNBT getNbt() { return nbt; }

    private static Consumer<MothPoolSyncPacket> clientHandler;
    public static void setClientHandler(Consumer<MothPoolSyncPacket> handler) { clientHandler = handler; }

    public static void handle(MothPoolSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> { if (clientHandler != null) clientHandler.accept(msg); });
        ctx.get().setPacketHandled(true);
    }
}
