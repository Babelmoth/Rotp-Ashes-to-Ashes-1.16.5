package com.babelmoth.rotp_ata.client;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.client.render.particle.FossilAshParticle;
import com.babelmoth.rotp_ata.init.InitParticles;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ParticleFactoryRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    
    @SubscribeEvent
    public static void onParticleFactoryRegister(ParticleFactoryRegisterEvent event) {
        Minecraft.getInstance().particleEngine.register(
            InitParticles.FOSSIL_ASH.get(),
            sprite -> new FossilAshParticle.Factory(sprite)
        );
        
        Minecraft.getInstance().particleEngine.register(
            InitParticles.FOSSIL_ASH_GATHER.get(),
            sprite -> new FossilAshParticle.Factory(sprite)
        );
        
        Minecraft.getInstance().particleEngine.register(
            InitParticles.ASH_SMOKE_LARGE.get(),
            sprite -> new com.babelmoth.rotp_ata.client.render.particle.AshSmokeLargeParticle.Factory(sprite) 
        );
    }
}
