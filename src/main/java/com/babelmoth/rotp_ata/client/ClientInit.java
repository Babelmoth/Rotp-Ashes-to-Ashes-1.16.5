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
import com.github.standobyte.jojo.client.ui.standstats.StandStatsRenderer;

import com.babelmoth.rotp_ata.client.render.entity.layerrenderer.SpearStuckLayer;
import com.babelmoth.rotp_ata.client.render.entity.layerrenderer.SpearStuckMobLayer;

import com.babelmoth.rotp_ata.init.InitItems;
import com.github.standobyte.jojo.client.ClientUtil;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;

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
        com.babelmoth.rotp_ata.networking.ClientPacketHandler.registerHandlers();

        com.babelmoth.rotp_ata.entity.ThelaHunGinjeetSpearEntity.setLocalPlayerOwnerCheck(ownerId -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            return mc.player != null && mc.player.getId() == ownerId;
        });
        com.babelmoth.rotp_ata.entity.FossilMothEntity.setLocalPlayerCheck(owner -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            return mc.player != null && mc.player == owner;
        });
        com.babelmoth.rotp_ata.entity.FossilMothEntity.setClientEffectsHandler(moth -> {
            net.minecraft.util.math.vector.Vector3d soundPos = moth.getBoundingBox().getCenter();
            com.github.standobyte.jojo.client.sound.HamonSparksLoopSound.playSparkSound(moth, soundPos, 1.0F, true);
            if (moth.tickCount % 5 == 0) {
                com.github.standobyte.jojo.client.particle.custom.CustomParticlesHelper.createHamonSparkParticles(
                        moth, moth.getRandomX(0.3), moth.getY(0.5), moth.getRandomZ(0.3), 1);
            }
        });
        com.babelmoth.rotp_ata.entity.ExfoliatingAshCloudEntity.setLocalPlayerCheck(owner -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            return mc.player != null && mc.player == owner;
        });

        RenderingRegistry.registerEntityRenderingHandler(
                InitStands.STAND_ASHES_TO_ASHES.getEntityType(), AshesToAshesStandRenderer::new);

        RenderingRegistry.registerEntityRenderingHandler(
                InitStands.STAND_THELA_HUN_GINJEET.getEntityType(), ThelaHunGinjeetStandRenderer::new);

        RenderingRegistry.registerEntityRenderingHandler(
                InitStands.STAND_DHARMA_CHAKRA.getEntityType(), com.babelmoth.rotp_ata.client.render.DharmaChakraRenderer::new);

        com.babelmoth.rotp_ata.networking.S2CAdaptationSyncPacket.setClientHandler(msg -> {
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.level != null) {
                net.minecraft.entity.Entity target = Minecraft.getInstance().player.level.getEntity(msg.getEntityId());
                if (target instanceof net.minecraft.entity.player.PlayerEntity) {
                    target.getCapability(com.babelmoth.rotp_ata.capability.AdaptationCapProvider.CAPABILITY).ifPresent(cap -> {
                        cap.deserializeNBT(msg.getNbt());
                    });
                }
            }
        });

        com.babelmoth.rotp_ata.networking.S2CAdaptationRotationPacket.setClientHandler(msg -> {
            if (Minecraft.getInstance().level != null) {
                net.minecraft.entity.player.PlayerEntity player = Minecraft.getInstance().level.getPlayerByUUID(msg.getPlayerId());
                if (player != null) {
                    com.babelmoth.rotp_ata.client.render.layer.DharmaChakraLayer.triggerRotation(msg.getPlayerId());
                }
            }
        });

        RenderingRegistry.registerEntityRenderingHandler(
                InitEntities.FOSSIL_MOTH.get(), FossilMothRenderer::new);

        RenderingRegistry.registerEntityRenderingHandler(
                InitEntities.EXFOLIATING_ASH_CLOUD.get(), net.minecraft.client.renderer.entity.AreaEffectCloudRenderer::new);

        RenderingRegistry.registerEntityRenderingHandler(
                InitEntities.THELA_HUN_GINJEET_SPEAR_ENTITY.get(), ThelaHunGinjeetSpearRenderer::new);

        RenderingRegistry.registerEntityRenderingHandler(
                InitEntities.SPEAR_SPIKE_ENTITY.get(), com.babelmoth.rotp_ata.client.render.SpearSpikeRenderer::new);

        IItemPropertyGetter standItemInvisible = (itemStack, clientWorld, livingEntity) -> {
            return !ClientUtil.canSeeStands() ? 1 : 0;
        };
        event.enqueueWork(() -> {
            ItemModelsProperties.register(InitItems.THELA_HUN_GINJEET_SPEAR.get(),
                    new ResourceLocation(AddonMain.MOD_ID, "stand_invisible"),
                    standItemInvisible);
        });

        event.enqueueWork(() -> {
            MarkerRenderer.Handler.addRenderer(new MothSwarmAttackMarker(Minecraft.getInstance()));
            MarkerRenderer.Handler.addRenderer(new MothKineticDetonationMarker(Minecraft.getInstance()));
            MarkerRenderer.Handler.addRenderer(new MothExfoliatingDetonationMarker(Minecraft.getInstance()));
            MarkerRenderer.Handler.addRenderer(new SpearRecallMarker(Minecraft.getInstance()));
            MarkerRenderer.Handler.addRenderer(new com.babelmoth.rotp_ata.client.ui.marker.ThornBurstMarker(Minecraft.getInstance()));

            java.util.Map<String, PlayerRenderer> skinMap = Minecraft.getInstance().getEntityRenderDispatcher().getSkinMap();
            skinMap.get("default").addLayer(new SpearStuckLayer<>(skinMap.get("default")));
            skinMap.get("slim").addLayer(new SpearStuckLayer<>(skinMap.get("slim")));

            for (PlayerRenderer renderer : skinMap.values()) {
                renderer.addLayer(new com.babelmoth.rotp_ata.client.render.layer.DharmaChakraLayer(renderer));
            }

            Minecraft.getInstance().getEntityRenderDispatcher().renderers.values().forEach(renderer -> {
                if (renderer instanceof LivingRenderer && !(renderer instanceof PlayerRenderer)) {
                    addSpearLayerToRenderer((LivingRenderer<?, ?>) renderer);
                }
            });
        });

        net.minecraftforge.fml.client.registry.ClientRegistry.bindTileEntityRenderer(
                com.babelmoth.rotp_ata.init.InitBlocks.FROZEN_BARRIER_TILE.get(),
                com.babelmoth.rotp_ata.client.render.FrozenBarrierRenderer::new);
                
        StandStatsRenderer.overrideCosmeticStats(
                InitStands.STAND_DHARMA_CHAKRA.getStandType().getRegistryName(),
                new StandStatsRenderer.ICosmeticStandStats() {
                    @Override
                    public String statRankLetter(StandStatsRenderer.StandStat stat, IStandPower standData, double statConvertedValue) {
                        if (stat == StandStatsRenderer.StandStat.DEV_POTENTIAL) {
                            return "∞";
                        }
                        return StandStatsRenderer.ICosmeticStandStats.super.statRankLetter(stat, standData, statConvertedValue);
                    }
                });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addSpearLayerToRenderer(LivingRenderer<?, ?> renderer) {
        renderer.addLayer(new SpearStuckMobLayer(renderer));
    }
}
