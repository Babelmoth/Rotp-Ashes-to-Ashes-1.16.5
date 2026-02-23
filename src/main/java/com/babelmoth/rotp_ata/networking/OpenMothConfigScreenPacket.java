package com.babelmoth.rotp_ata.networking;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class OpenMothConfigScreenPacket {

    public OpenMothConfigScreenPacket() {}

    public static void encode(OpenMothConfigScreenPacket msg, PacketBuffer buf) {
    }

    public static OpenMothConfigScreenPacket decode(PacketBuffer buf) {
        return new OpenMothConfigScreenPacket();
    }

    private static Consumer<OpenMothConfigScreenPacket> clientHandler;
    public static void setClientHandler(Consumer<OpenMothConfigScreenPacket> handler) { clientHandler = handler; }

    public static void handle(OpenMothConfigScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> { if (clientHandler != null) clientHandler.accept(msg); });
        ctx.get().setPacketHandled(true);
    }
}
