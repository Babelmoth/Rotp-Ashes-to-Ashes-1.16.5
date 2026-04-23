package com.babelmoth.rotp_ata.networking;

import com.babelmoth.rotp_ata.init.InitSounds;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SAdaptationEffectsPacket {

    public C2SAdaptationEffectsPacket() {
    }

    public static void encode(C2SAdaptationEffectsPacket msg, PacketBuffer buffer) {
    }

    public static C2SAdaptationEffectsPacket decode(PacketBuffer buffer) {
        return new C2SAdaptationEffectsPacket();
    }

    public static void handle(C2SAdaptationEffectsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player != null && !player.level.isClientSide) {
                IStandPower.getStandPowerOptional(player).ifPresent(power -> {
                    if (power.getType() != com.babelmoth.rotp_ata.init.InitStands.STAND_DHARMA_CHAKRA.getStandType() || !power.isActive()) {
                        return;
                    }
                    player.setHealth(player.getMaxHealth());
                    power.consumeStamina(power.getMaxStamina() * 0.25f);
                    player.level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            InitSounds.DHARMA_WHEEL_SPIN.get(), SoundCategory.NEUTRAL, 2.0f, 1.0f);
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
