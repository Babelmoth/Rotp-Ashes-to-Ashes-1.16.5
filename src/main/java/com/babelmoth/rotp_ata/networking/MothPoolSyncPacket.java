package com.babelmoth.rotp_ata.networking;

import com.babelmoth.rotp_ata.capability.MothPoolProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

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

    public static void handle(MothPoolSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.getCapability(MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
                    pool.deserializeNBT(msg.nbt);
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
