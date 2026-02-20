package com.babelmoth.rotp_ata.networking;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.OptionalInt;
import java.util.function.Supplier;

public class SpearMarkSyncPacket {
    private static final int PURPLE_COLOR = 0x8B00FF;

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

    public static void handle(SpearMarkSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().level == null) return;
            // 只有长矛主人才能看到标记
            Entity clientPlayer = Minecraft.getInstance().player;
            if (clientPlayer == null || clientPlayer.getId() != msg.ownerEntityId) return;

            Entity target = Minecraft.getInstance().level.getEntity(msg.targetEntityId);
            if (target instanceof LivingEntity) {
                target.getCapability(com.github.standobyte.jojo.capability.entity.EntityUtilCapProvider.CAPABILITY).ifPresent(cap -> {
                    cap.setClGlowingColor(OptionalInt.of(PURPLE_COLOR), msg.durationTicks);
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
