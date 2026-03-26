package com.babelmoth.rotp_ata.networking;

import com.babelmoth.rotp_ata.capability.MothPoolProvider;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class MothConfigUpdatePacket {

    private final int orbitCount;
    private final int shieldCount;
    private final int swarmCount;
    private final boolean barrierPassthrough;
    private final boolean autoChargeShield;
    private final boolean remoteFollow;
    private final int remoteFollowRatio;

    public MothConfigUpdatePacket(int orbitCount, int shieldCount, int swarmCount,
                                   boolean barrierPassthrough, boolean autoChargeShield,
                                   boolean remoteFollow, int remoteFollowRatio) {
        this.orbitCount = orbitCount;
        this.shieldCount = shieldCount;
        this.swarmCount = swarmCount;
        this.barrierPassthrough = barrierPassthrough;
        this.autoChargeShield = autoChargeShield;
        this.remoteFollow = remoteFollow;
        this.remoteFollowRatio = remoteFollowRatio;
    }

    public static void encode(MothConfigUpdatePacket msg, PacketBuffer buf) {
        buf.writeInt(msg.orbitCount);
        buf.writeInt(msg.shieldCount);
        buf.writeInt(msg.swarmCount);
        buf.writeBoolean(msg.barrierPassthrough);
        buf.writeBoolean(msg.autoChargeShield);
        buf.writeBoolean(msg.remoteFollow);
        buf.writeInt(msg.remoteFollowRatio);
    }

    public static MothConfigUpdatePacket decode(PacketBuffer buf) {
        return new MothConfigUpdatePacket(
                buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readInt());
    }

    public static void handle(MothConfigUpdatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player != null) {
                player.getCapability(MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
                    pool.setOrbitMothCount(Math.max(0, Math.min(msg.orbitCount, 100)));
                    pool.setShieldMothCount(Math.max(0, Math.min(msg.shieldCount, 100)));
                    pool.setSwarmAttackCount(Math.max(0, Math.min(msg.swarmCount, 100)));
                    pool.setBarrierPassthrough(msg.barrierPassthrough);
                    pool.setAutoChargeShield(msg.autoChargeShield);
                    pool.setRemoteFollow(msg.remoteFollow);
                    pool.setRemoteFollowRatio(Math.max(0, Math.min(msg.remoteFollowRatio, 100)));
                    pool.sync(player);
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
