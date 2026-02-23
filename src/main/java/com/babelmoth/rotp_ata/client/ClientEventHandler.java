package com.babelmoth.rotp_ata.client;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.action.ThelaHunGinjeetBlock;
import com.babelmoth.rotp_ata.client.render.particle.ResolveAuraPseudoParticle;
import com.babelmoth.rotp_ata.client.render.particle.StandResolveAuraParticle;
import com.babelmoth.rotp_ata.init.InitStands;
import com.github.standobyte.jojo.client.particle.custom.FirstPersonHamonAura;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.github.standobyte.jojo.power.impl.stand.type.StandType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.IAnimatedSprite;
import net.minecraft.client.renderer.entity.model.BipedModel;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.HandSide;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEventHandler {

    private static final Random RANDOM = new Random();

    @SuppressWarnings("rawtypes")
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre event) {
        if (!(event.getEntity() instanceof PlayerEntity)) return;
        PlayerEntity player = (PlayerEntity) event.getEntity();

        IStandPower.getStandPowerOptional(player).ifPresent(power -> {
            if (power.getHeldAction() instanceof ThelaHunGinjeetBlock) {
                if (event.getRenderer().getModel() instanceof BipedModel) {
                    BipedModel<?> model = (BipedModel<?>) event.getRenderer().getModel();
                    model.rightArmPose = BipedModel.ArmPose.BLOCK;
                }
            }
        });
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;

        ClientWorld world = mc.level;

        for (PlayerEntity player : world.players()) {
            if (!player.isAlive()) continue;
            if (!player.hasEffect(ModStatusEffects.RESOLVE.get())) continue;

            IStandPower.getStandPowerOptional(player).ifPresent(power -> {
                if (!power.hasPower()) return;
                StandType<?> standType = power.getType();
                if (standType == null || standType.getRegistryName() == null) return;

                if (!AddonMain.MOD_ID.equals(standType.getRegistryName().getNamespace())) return;

                IAnimatedSprite sprites = StandResolveAuraParticle.getSavedSprites();
                if (sprites == null) return;

                int color = standType.getColor();
                float width = player.getBbWidth();
                float height = player.getBbHeight();

                int count = 7;
                for (int i = 0; i < count; i++) {
                    double px = player.getX() + (RANDOM.nextDouble() - 0.5) * (width + 0.5);
                    double py = player.getY() + RANDOM.nextDouble() * (height * 0.5);
                    double pz = player.getZ() + (RANDOM.nextDouble() - 0.5) * (width + 0.5);

                    StandResolveAuraParticle particle = StandResolveAuraParticle.create(
                            world, player, px, py, pz, color, sprites);
                    mc.particleEngine.add(particle);
                }

                if (player == mc.cameraEntity) {
                    float r = ((color >> 16) & 0xFF) / 255.0F;
                    float g = ((color >> 8) & 0xFF) / 255.0F;
                    float b = (color & 0xFF) / 255.0F;
                    float firstPersonPPT = 7.0F / 5.0F;
                    FirstPersonHamonAura fpAura = FirstPersonHamonAura.getInstance();
                    if (fpAura != null) {
                        for (HandSide handSide : HandSide.values()) {
                            int fpCount = (int) firstPersonPPT;
                            if (RANDOM.nextFloat() < (firstPersonPPT - fpCount)) fpCount++;
                            for (int i = 0; i < fpCount; i++) {
                                double fx = RANDOM.nextDouble() * 0.5 - 0.625;
                                double fy = RANDOM.nextDouble();
                                double fz = RANDOM.nextDouble() * 0.5 - 0.25;
                                if (handSide == HandSide.LEFT) fx = -fx;
                                fpAura.add(new ResolveAuraPseudoParticle(fx, fy, fz, sprites, handSide, r, g, b));
                            }
                        }
                    }
                }

            });
        }
    }
}
