package com.babelmoth.rotp_ata.client;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.capability.IMothPool;
import com.babelmoth.rotp_ata.capability.MothPoolProvider;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;
import com.github.standobyte.jojo.client.ClientUtil;
import com.github.standobyte.jojo.client.ui.actionshud.ActionsOverlayGui;
import com.github.standobyte.jojo.power.IPower;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraftforge.eventbus.api.EventPriority;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Kinetic bar HUD for Ashes to Ashes. Uses the same visibility logic as RotP's energy bars
 * (only when stand GUI / hotbars are shown) and the same resolve-bar style (frame + fill) with amber color.
 * Renders with LOW priority so we run after RotP's overlay and use the same frame's visibility state.
 */
@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AshesToAshesHUD {
    private static final Minecraft mc = Minecraft.getInstance();

    /** Amber color for kinetic bar (same tone as resolve / stand UI). */
    private static final int AMBER_COLOR = 0xE7801A;
    /** Bar length in pixels (middle segment only; +2 for caps = full width). */
    private static final int BAR_LENGTH = 80;
    private static final int BAR_HEIGHT = 8;
    /** Resolve bar fill texY in overlay. */
    private static final int TEX_Y_RESOLVE = 160;
    /** Border strip at y=128; RotP uses 202x8 (left 1px + middle 200 + right 1px). */
    private static final int BORDER_TEX_Y = 128;
    private static final int BORDER_LEFT_TEX_X = 0;
    private static final int BORDER_MIDDLE_TEX_X = 1;
    private static final int BORDER_RIGHT_TEX_X = 201;
    /** Scale texture: (1, 145), size (length, 6). */
    private static final int SCALE_TEX_X = 1;
    private static final int SCALE_TEX_Y = 145;
    private static final int MAX_KINETIC_DISPLAY = Math.max(1, IMothPool.MAX_MOTHS * AshesToAshesConstants.MOTH_MAX_KINETIC);

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderHUD(RenderGameOverlayEvent.Pre event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        if (ActionsOverlayGui.noHudRender(mc)) return;
        ActionsOverlayGui overlay = ActionsOverlayGui.getInstance();
        if (overlay == null || !overlay.areHotbarsEnabled()) return;
        // Only show when overlay is in stand mode (same as resolve/stamina bars context)
        if (overlay.getCurrentMode() == null || overlay.getCurrentMode() != IPower.PowerClassification.STAND) return;

        PlayerEntity player = mc.player;
        if (player == null) return;

        IStandPower.getStandPowerOptional(player).ifPresent(power -> {
            if (!power.hasPower() || power.getType().getRegistryName() == null
                    || !AddonMain.MOD_ID.equals(power.getType().getRegistryName().getNamespace())
                    || !"ashes_to_ashes".equals(power.getType().getRegistryName().getPath())) {
                return;
            }

            player.getCapability(MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
                int kinetic = pool.getTotalKineticEnergy();
                MatrixStack stack = event.getMatrixStack();
                int screenWidth = mc.getWindow().getGuiScaledWidth();
                int screenHeight = mc.getWindow().getGuiScaledHeight();

                int barX = screenWidth / 2 + 12;
                int barY = screenHeight / 2 - BAR_HEIGHT + 4;

                int fill = Math.min(BAR_LENGTH, (int) ((long) BAR_LENGTH * kinetic / MAX_KINETIC_DISPLAY));

                mc.getTextureManager().bind(ActionsOverlayGui.OVERLAY_LOCATION);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);

                // RotP-style 3-part border so left/right caps are visible (texture 202px: 1 + 200 + 1)
                AbstractGui.blit(stack, barX, barY, BORDER_LEFT_TEX_X, BORDER_TEX_Y, 1, BAR_HEIGHT, 256, 256);
                AbstractGui.blit(stack, barX + 1, barY, BORDER_MIDDLE_TEX_X, BORDER_TEX_Y, BAR_LENGTH, BAR_HEIGHT, 256, 256);
                AbstractGui.blit(stack, barX + 1 + BAR_LENGTH, barY, BORDER_RIGHT_TEX_X, BORDER_TEX_Y, 1, BAR_HEIGHT, 256, 256);
                if (fill > 0) {
                    float[] rgb = ClientUtil.rgb(AMBER_COLOR);
                    RenderSystem.color4f(rgb[0], rgb[1], rgb[2], 1.0F);
                    AbstractGui.blit(stack, barX + 1, barY + 1, 1, TEX_Y_RESOLVE + 1, fill, BAR_HEIGHT - 2, 256, 256);
                    RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                }
                AbstractGui.blit(stack, barX + 1, barY + 1, SCALE_TEX_X, SCALE_TEX_Y, BAR_LENGTH, BAR_HEIGHT - 2, 256, 256);

                int pct = Math.min(100, (int) ((long) 100 * kinetic / MAX_KINETIC_DISPLAY));
                String pctText = pct + "%";
                int textX = barX + BAR_LENGTH + 2 + 4;
                int textY = barY - 1;
                ClientUtil.drawBackdrop(stack, textX, textY, mc.font.width(pctText), 1.0F);
                mc.font.draw(stack, pctText, textX, textY, AMBER_COLOR);
            });
        });
    }
}
