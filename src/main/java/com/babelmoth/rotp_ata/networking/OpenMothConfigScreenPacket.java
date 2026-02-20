package com.babelmoth.rotp_ata.networking;

import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client: tells the client to open the Moth Config screen.
 */
public class OpenMothConfigScreenPacket {

    public OpenMothConfigScreenPacket() {}

    public static void encode(OpenMothConfigScreenPacket msg, PacketBuffer buf) {
        // No data needed
    }

    public static OpenMothConfigScreenPacket decode(PacketBuffer buf) {
        return new OpenMothConfigScreenPacket();
    }

    public static void handle(OpenMothConfigScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.setScreen(new com.babelmoth.rotp_ata.client.screen.MothConfigScreen());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
