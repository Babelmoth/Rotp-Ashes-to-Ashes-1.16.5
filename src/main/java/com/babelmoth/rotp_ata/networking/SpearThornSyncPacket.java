package com.babelmoth.rotp_ata.networking;

import com.babelmoth.rotp_ata.capability.SpearThornProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

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

    public static void handle(SpearThornSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Entity entity = Minecraft.getInstance().level != null
                    ? Minecraft.getInstance().level.getEntity(msg.entityId) : null;
            if (entity != null) {
                entity.getCapability(SpearThornProvider.SPEAR_THORN_CAPABILITY).ifPresent(cap -> {
                    cap.setThornCount(msg.thornCount);
                    cap.setDamageDealt(msg.damageDealt);
                    cap.setDetachThreshold(msg.detachThreshold);
                    cap.setHasSpear(msg.hasSpear);
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
