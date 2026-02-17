package com.babelmoth.rotp_ata.client;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.client.render.AshesToAshesStandRenderer;
import com.babelmoth.rotp_ata.client.render.FossilMothRenderer;
import com.babelmoth.rotp_ata.client.render.ThelaHunGinjeetSpearRenderer;
import com.babelmoth.rotp_ata.client.render.ThelaHunGinjeetStandRenderer;
import com.babelmoth.rotp_ata.init.InitEntities;
import com.babelmoth.rotp_ata.init.InitStands;

import com.babelmoth.rotp_ata.client.ui.marker.MothSwarmAttackMarker;
import com.babelmoth.rotp_ata.client.ui.marker.MothKineticDetonationMarker;
import com.babelmoth.rotp_ata.client.ui.marker.MothExfoliatingDetonationMarker;
import com.babelmoth.rotp_ata.client.ui.marker.SpearRecallMarker;
import com.github.standobyte.jojo.client.ui.marker.MarkerRenderer;

import com.babelmoth.rotp_ata.client.render.entity.layerrenderer.SpearStuckLayer;
import com.babelmoth.rotp_ata.client.render.entity.layerrenderer.SpearStuckMobLayer;

import com.babelmoth.rotp_ata.init.InitItems;
import com.github.standobyte.jojo.client.ClientUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.entity.PlayerRenderer;
import net.minecraft.item.IItemPropertyGetter;
import net.minecraft.item.ItemModelsProperties;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = AddonMain.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientInit {
    
    @SubscribeEvent
    public static void onFMLClientSetup(FMLClientSetupEvent event) {
        RenderingRegistry.registerEntityRenderingHandler(
                InitStands.STAND_ASHES_TO_ASHES.getEntityType(), AshesToAshesStandRenderer::new);

        RenderingRegistry.registerEntityRenderingHandler(
                InitStands.STAND_THELA_HUN_GINJEET.getEntityType(), ThelaHunGinjeetStandRenderer::new);
        
        RenderingRegistry.registerEntityRenderingHandler(
                InitEntities.FOSSIL_MOTH.get(), FossilMothRenderer::new);

        RenderingRegistry.registerEntityRenderingHandler(
                InitEntities.EXFOLIATING_ASH_CLOUD.get(), net.minecraft.client.renderer.entity.AreaEffectCloudRenderer::new);

        RenderingRegistry.registerEntityRenderingHandler(
                InitEntities.THELA_HUN_GINJEET_SPEAR_ENTITY.get(), ThelaHunGinjeetSpearRenderer::new);

        RenderingRegistry.registerEntityRenderingHandler(
                InitEntities.SPEAR_SPIKE_ENTITY.get(), com.babelmoth.rotp_ata.client.render.SpearSpikeRenderer::new);

        // Register stand_invisible item property for spear
        IItemPropertyGetter standItemInvisible = (itemStack, clientWorld, livingEntity) -> {
            return !ClientUtil.canSeeStands() ? 1 : 0;
        };
        event.enqueueWork(() -> {
            ItemModelsProperties.register(InitItems.THELA_HUN_GINJEET_SPEAR.get(),
                    new ResourceLocation(AddonMain.MOD_ID, "stand_invisible"),
                    standItemInvisible);
        });

        // Register markers
        event.enqueueWork(() -> {
            MarkerRenderer.Handler.addRenderer(new MothSwarmAttackMarker(Minecraft.getInstance()));
            MarkerRenderer.Handler.addRenderer(new MothKineticDetonationMarker(Minecraft.getInstance()));
            MarkerRenderer.Handler.addRenderer(new MothExfoliatingDetonationMarker(Minecraft.getInstance()));
            MarkerRenderer.Handler.addRenderer(new SpearRecallMarker(Minecraft.getInstance()));
            MarkerRenderer.Handler.addRenderer(new com.babelmoth.rotp_ata.client.ui.marker.ThornBurstMarker(Minecraft.getInstance()));

            // 注册长矛插入渲染层到玩家渲染器
            java.util.Map<String, PlayerRenderer> skinMap = Minecraft.getInstance().getEntityRenderDispatcher().getSkinMap();
            skinMap.get("default").addLayer(new SpearStuckLayer<>(skinMap.get("default")));
            skinMap.get("slim").addLayer(new SpearStuckLayer<>(skinMap.get("slim")));

            // 注册长矛插入渲染层到所有非玩家 LivingRenderer
            Minecraft.getInstance().getEntityRenderDispatcher().renderers.values().forEach(renderer -> {
                if (renderer instanceof LivingRenderer && !(renderer instanceof PlayerRenderer)) {
                    addSpearLayerToRenderer((LivingRenderer<?, ?>) renderer);
                }
            });
        });

        net.minecraftforge.fml.client.registry.ClientRegistry.bindTileEntityRenderer(
                com.babelmoth.rotp_ata.init.InitBlocks.FROZEN_BARRIER_TILE.get(),
                com.babelmoth.rotp_ata.client.render.FrozenBarrierRenderer::new);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addSpearLayerToRenderer(LivingRenderer<?, ?> renderer) {
        renderer.addLayer(new SpearStuckMobLayer(renderer));
    }
}
