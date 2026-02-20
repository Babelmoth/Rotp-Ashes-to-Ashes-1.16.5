package com.babelmoth.rotp_ata.networking;

import com.babelmoth.rotp_ata.capability.SpearStuckProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

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

    public static void handle(SpearStuckSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Entity entity = Minecraft.getInstance().level != null
                    ? Minecraft.getInstance().level.getEntity(msg.entityId) : null;
            if (entity != null) {
                entity.getCapability(SpearStuckProvider.SPEAR_STUCK_CAPABILITY).ifPresent(
                        cap -> cap.setSpearCount(msg.spearCount));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
