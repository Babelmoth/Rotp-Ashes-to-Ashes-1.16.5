package com.babelmoth.rotp_ata.client;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.client.render.AshesToAshesStandRenderer;
import com.babelmoth.rotp_ata.client.render.FossilMothRenderer;
import com.babelmoth.rotp_ata.init.InitEntities;
import com.babelmoth.rotp_ata.init.InitStands;

import com.babelmoth.rotp_ata.client.ui.marker.MothSwarmAttackMarker;
import com.babelmoth.rotp_ata.client.ui.marker.MothKineticDetonationMarker;
import com.babelmoth.rotp_ata.client.ui.marker.MothExfoliatingDetonationMarker;
import com.github.standobyte.jojo.client.ui.marker.MarkerRenderer;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = AddonMain.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientInit {
    
    @SubscribeEvent
    public static void onFMLClientSetup(FMLClientSetupEvent event) {
        // 替身实体渲染器
        RenderingRegistry.registerEntityRenderingHandler(
                InitStands.STAND_ASHES_TO_ASHES.getEntityType(), AshesToAshesStandRenderer::new);
        
        // 化石蛾实体渲染器 (GeckoLib)
        RenderingRegistry.registerEntityRenderingHandler(
                InitEntities.FOSSIL_MOTH.get(), FossilMothRenderer::new);

        // 剥落爆破烟雾云渲染器
        RenderingRegistry.registerEntityRenderingHandler(
                InitEntities.EXFOLIATING_ASH_CLOUD.get(), net.minecraft.client.renderer.entity.AreaEffectCloudRenderer::new);
                
        // Register markers
        event.enqueueWork(() -> {
            MarkerRenderer.Handler.addRenderer(new MothSwarmAttackMarker(Minecraft.getInstance()));
            MarkerRenderer.Handler.addRenderer(new MothKineticDetonationMarker(Minecraft.getInstance()));
            MarkerRenderer.Handler.addRenderer(new MothExfoliatingDetonationMarker(Minecraft.getInstance()));
        });

        // 冻结屏障方块实体渲染器
        net.minecraftforge.fml.client.registry.ClientRegistry.bindTileEntityRenderer(
                com.babelmoth.rotp_ata.init.InitBlocks.FROZEN_BARRIER_TILE.get(),
                com.babelmoth.rotp_ata.client.render.FrozenBarrierRenderer::new);
    }
}
