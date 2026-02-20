package com.babelmoth.rotp_ata.client.screen;

import com.babelmoth.rotp_ata.capability.IMothPool;
import com.babelmoth.rotp_ata.capability.MothPoolProvider;
import com.babelmoth.rotp_ata.networking.AshesToAshesPacketHandler;
import com.babelmoth.rotp_ata.networking.MothConfigUpdatePacket;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Moth swarm configuration screen for Ashes to Ashes.
 * Uses RotP's hamon_window.png frame + jojo.png tiled background.
 */
public class MothConfigScreen extends Screen {

    // RotP textures
    private static final ResourceLocation WINDOW = new ResourceLocation("jojo", "textures/gui/hamon_window.png");
    private static final ResourceLocation BACKGROUND = new ResourceLocation("jojo", "textures/gui/advancements/jojo.png");
    private static final ResourceLocation WIDGETS_TEX = new ResourceLocation("textures/gui/widgets.png");
    private static final int WINDOW_WIDTH = 230;
    private static final int WINDOW_HEIGHT = 227;
    private static final int WINDOW_THIN_BORDER = 9;
    private static final int WINDOW_UPPER_BORDER = 18;

    // Content area dimensions
    private static final int CONTENT_W = WINDOW_WIDTH - WINDOW_THIN_BORDER * 2;
    private static final int CONTENT_H = WINDOW_HEIGHT - WINDOW_UPPER_BORDER - WINDOW_THIN_BORDER;

    // Colors - unified RotP palette
    private static final int COL_TITLE = 0xFFD4AA55;
    private static final int COL_TEXT  = 0xFFFFFFFF;
    private static final int COL_DIM   = 0xFFAAAAAA;
    private static final int COL_SEP   = 0xFF5A4A35;

    // Resolve-based max limits: [noResolve, resolveI, resolveII, resolveIII+]
    private static final int[] ORBIT_MAX  = {20, 30, 40, 50};
    private static final int[] SHIELD_MAX = { 5, 10, 15, 20};
    private static final int[] SWARM_MAX  = {30, 50, 75, 100}; // percentage

    private IMothPool pool;
    private int orbitCount, shieldCount, swarmCount;
    private boolean barrierPassthrough, autoChargeShield, remoteFollow;
    private int remoteFollowRatio;
    private int resolveLevel = 0; // 0=none, 1=I, 2=II, 3=III+

    // Scrolling
    private double scrollY = 0;
    private int totalContentHeight = 0;
    private final List<ScrollableWidget> scrollableWidgets = new ArrayList<>();
    // Dynamic slider references for max update
    private VanillaSlider orbitSlider, shieldSlider, swarmSlider;
    private VanillaSlider followRatioSlider;
    private VanillaToggle remoteFollowToggle;

    public MothConfigScreen() {
        super(new TranslationTextComponent("rotp_ata.screen.moth_config.title"));
    }

    private int windowX() { return (this.width - WINDOW_WIDTH) / 2; }
    private int windowY() { return (this.height - WINDOW_HEIGHT) / 2; }

    private int getOrbitMax() { return ORBIT_MAX[Math.min(resolveLevel, 3)]; }
    private int getShieldMax() { return SHIELD_MAX[Math.min(resolveLevel, 3)]; }
    private int getSwarmMax() { return SWARM_MAX[Math.min(resolveLevel, 3)]; }

    @Override
    protected void init() {
        super.init();
        scrollableWidgets.clear();
        scrollY = 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.getCapability(MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(p -> {
                this.pool = p;
                this.orbitCount = p.getOrbitMothCount();
                this.shieldCount = p.getShieldMothCount();
                this.swarmCount = p.getSwarmAttackCount();
                this.barrierPassthrough = p.isBarrierPassthrough();
                this.autoChargeShield = p.isAutoChargeShield();
                this.remoteFollow = p.isRemoteFollow();
                this.remoteFollowRatio = p.getRemoteFollowRatio();
            });
            // Read resolve level from IStandPower capability
            resolveLevel = IStandPower.getStandPowerOptional(mc.player)
                    .map(IStandPower::getResolveLevel).orElse(0);
        }

        // Clamp config values to current resolve-based max
        orbitCount = MathHelper.clamp(orbitCount, 5, getOrbitMax());
        shieldCount = MathHelper.clamp(shieldCount, 1, getShieldMax());
        swarmCount = MathHelper.clamp(swarmCount, 1, getSwarmMax());

        int contentTop = windowY() + WINDOW_UPPER_BORDER;
        int contentLeft = windowX() + WINDOW_THIN_BORDER + 4;
        int contentRight = windowX() + WINDOW_WIDTH - WINDOW_THIN_BORDER - 4;
        int sliderW = 90;
        int sliderX = contentRight - sliderW;

        // Layout: y offset relative to contentTop
        int yOff = 4;
        // Info section: 6 lines × 13px (total, active, reserve, kinetic, hamon, resolve)
        yOff += 6 * 13; // 78
        yOff += 6; // gap

        // Separator + settings header
        yOff += 16; // separator + title
        yOff += 4; // gap = 104

        // Orbit slider
        orbitSlider = new VanillaSlider(sliderX, contentTop + yOff, sliderW, 14,
                5, getOrbitMax(), orbitCount, val -> { orbitCount = val; sendConfigUpdate(); });
        addButton(orbitSlider);
        scrollableWidgets.add(new ScrollableWidget(orbitSlider, yOff));
        yOff += 20;

        // Shield slider
        shieldSlider = new VanillaSlider(sliderX, contentTop + yOff, sliderW, 14,
                1, getShieldMax(), shieldCount, val -> { shieldCount = val; sendConfigUpdate(); });
        addButton(shieldSlider);
        scrollableWidgets.add(new ScrollableWidget(shieldSlider, yOff));
        yOff += 20;

        // Swarm slider (percentage)
        swarmSlider = new VanillaSlider(sliderX, contentTop + yOff, sliderW, 14,
                1, getSwarmMax(), swarmCount, true, val -> { swarmCount = val; sendConfigUpdate(); });
        addButton(swarmSlider);
        scrollableWidgets.add(new ScrollableWidget(swarmSlider, yOff));
        yOff += 22;

        // Barrier passthrough toggle
        VanillaToggle barrierToggle = new VanillaToggle(sliderX, contentTop + yOff, sliderW, 14,
                barrierPassthrough, val -> { barrierPassthrough = val; sendConfigUpdate(); });
        addButton(barrierToggle);
        scrollableWidgets.add(new ScrollableWidget(barrierToggle, yOff));
        yOff += 20;

        // Auto-charge shield toggle
        VanillaToggle chargeToggle = new VanillaToggle(sliderX, contentTop + yOff, sliderW, 14,
                autoChargeShield, val -> { autoChargeShield = val; sendConfigUpdate(); });
        addButton(chargeToggle);
        scrollableWidgets.add(new ScrollableWidget(chargeToggle, yOff));
        yOff += 22;

        // Remote follow toggle
        remoteFollowToggle = new VanillaToggle(sliderX, contentTop + yOff, sliderW, 14,
                remoteFollow, val -> {
            remoteFollow = val;
            if (followRatioSlider != null) followRatioSlider.visible = val;
            sendConfigUpdate();
        });
        addButton(remoteFollowToggle);
        scrollableWidgets.add(new ScrollableWidget(remoteFollowToggle, yOff));
        yOff += 20;

        // Remote follow ratio slider (only visible if remoteFollow is on)
        followRatioSlider = new VanillaSlider(sliderX, contentTop + yOff, sliderW, 14,
                0, 100, remoteFollowRatio, true, val -> { remoteFollowRatio = val; sendConfigUpdate(); });
        followRatioSlider.visible = remoteFollow;
        addButton(followRatioSlider);
        scrollableWidgets.add(new ScrollableWidget(followRatioSlider, yOff));
        yOff += 22;

        totalContentHeight = yOff + 10;

        updateWidgetPositions();
    }

    private void sendConfigUpdate() {
        AshesToAshesPacketHandler.CHANNEL.sendToServer(
                new MothConfigUpdatePacket(orbitCount, shieldCount, swarmCount,
                        barrierPassthrough, autoChargeShield, remoteFollow, remoteFollowRatio));
    }

    private void updateWidgetPositions() {
        int contentTop = windowY() + WINDOW_UPPER_BORDER;
        int intScroll = MathHelper.floor(scrollY);
        for (ScrollableWidget sw : scrollableWidgets) {
            sw.widget.y = contentTop + sw.baseOffsetY + intScroll;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = Math.max(0, totalContentHeight - CONTENT_H);
        scrollY = MathHelper.clamp(scrollY + delta * 10, -maxScroll, 0);
        updateWidgetPositions();
        return true;
    }

    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(ms);

        int wx = windowX();
        int wy = windowY();
        int cx = wx + WINDOW_WIDTH / 2;

        // --- Render RotP window frame ---
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();

        // Tiled jojo.png background inside the content area
        int contentLeft = wx + WINDOW_THIN_BORDER;
        int contentTop = wy + WINDOW_UPPER_BORDER;
        this.minecraft.getTextureManager().bind(BACKGROUND);
        for (int ty = 0; ty < CONTENT_H; ty += 16) {
            for (int tx = 0; tx < CONTENT_W; tx += 16) {
                int tw = Math.min(16, CONTENT_W - tx);
                int th = Math.min(16, CONTENT_H - ty);
                blit(ms, contentLeft + tx, contentTop + ty, 0, 0, tw, th, 16, 16);
            }
        }

        // Window frame on top
        this.minecraft.getTextureManager().bind(WINDOW);
        this.blit(ms, wx, wy, 0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);

        // --- Title (on window border, not scrollable) ---
        drawCenteredString(ms, this.font,
                new TranslationTextComponent("rotp_ata.screen.moth_config.title"),
                cx, wy + 5, COL_TITLE);

        // --- Enable GL scissor for scrollable content ---
        int contentBottom = wy + WINDOW_HEIGHT - WINDOW_THIN_BORDER;
        int cLeft = contentLeft + 4;
        int cRight = wx + WINDOW_WIDTH - WINDOW_THIN_BORDER - 4;
        enableScissor(contentLeft, contentTop, contentLeft + CONTENT_W, contentBottom);

        int intScroll = MathHelper.floor(scrollY);

        // --- Info Section ---
        int y = contentTop + 4 + intScroll;
        int lineH = 13;

        if (pool != null && this.minecraft.player != null) {
            int totalMoths = pool.getTotalMoths();
            int deployed = MothQueryUtil.getOwnerMoths(this.minecraft.player, 128.0).size();
            int reserve = totalMoths - deployed;
            int kinetic = pool.getTotalKineticEnergy();
            int hamon = pool.getTotalHamonEnergy();

            drawInfoLine(ms, cLeft, cRight, y, "rotp_ata.screen.moth_config.total", String.valueOf(totalMoths)); y += lineH;
            drawInfoLine(ms, cLeft, cRight, y, "rotp_ata.screen.moth_config.active", String.valueOf(deployed)); y += lineH;
            drawInfoLine(ms, cLeft, cRight, y, "rotp_ata.screen.moth_config.reserve", String.valueOf(reserve)); y += lineH;
            drawInfoLine(ms, cLeft, cRight, y, "rotp_ata.screen.moth_config.kinetic", String.valueOf(kinetic)); y += lineH;
            drawInfoLine(ms, cLeft, cRight, y, "rotp_ata.screen.moth_config.hamon", String.valueOf(hamon)); y += lineH;
            // Resolve level
            String resolveName = resolveLevel == 0 ? "---" : ("Lv." + resolveLevel);
            drawInfoLine(ms, cLeft, cRight, y, "rotp_ata.screen.moth_config.resolve", resolveName);
        }

        // --- Separator ---
        int sepY = contentTop + 84 + intScroll;
        hLine(ms, cLeft, cRight, sepY, COL_SEP);
        drawCenteredString(ms, this.font,
                new TranslationTextComponent("rotp_ata.screen.moth_config.settings"),
                cx, sepY + 4, COL_TITLE);

        // --- Config Labels (scrolled) ---
        int configY = contentTop + 104 + intScroll;

        drawString(ms, this.font, new TranslationTextComponent("rotp_ata.screen.moth_config.orbit"),
                cLeft, configY + 3, COL_TEXT);
        configY += 20;
        drawString(ms, this.font, new TranslationTextComponent("rotp_ata.screen.moth_config.shield"),
                cLeft, configY + 3, COL_TEXT);
        configY += 20;
        drawString(ms, this.font, new TranslationTextComponent("rotp_ata.screen.moth_config.swarm"),
                cLeft, configY + 3, COL_TEXT);
        configY += 22;
        drawString(ms, this.font, new TranslationTextComponent("rotp_ata.screen.moth_config.barrier"),
                cLeft, configY + 3, COL_TEXT);
        configY += 20;
        drawString(ms, this.font, new TranslationTextComponent("rotp_ata.screen.moth_config.auto_charge"),
                cLeft, configY + 3, COL_TEXT);
        configY += 22;
        drawString(ms, this.font, new TranslationTextComponent("rotp_ata.screen.moth_config.remote_follow"),
                cLeft, configY + 3, COL_TEXT);
        configY += 20;
        if (remoteFollow) {
            drawString(ms, this.font, new TranslationTextComponent("rotp_ata.screen.moth_config.follow_ratio"),
                    cLeft, configY + 3, COL_DIM);
        }

        // --- Render scrollable widgets ---
        for (ScrollableWidget sw : scrollableWidgets) {
            sw.widget.render(ms, mouseX, mouseY, partialTicks);
        }

        disableScissor();
    }

    private void drawInfoLine(MatrixStack ms, int labelX, int valueX, int y,
                              String translationKey, String valueStr) {
        drawString(ms, this.font, new TranslationTextComponent(translationKey), labelX, y, COL_TEXT);
        int w = this.font.width(valueStr);
        drawString(ms, this.font, valueStr, valueX - w, y, COL_TEXT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- GL Scissor helpers ---
    private void enableScissor(int x1, int y1, int x2, int y2) {
        MainWindow window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        int sx = (int)(x1 * scale);
        int sy = (int)((window.getGuiScaledHeight() - y2) * scale);
        int sw = (int)((x2 - x1) * scale);
        int sh = (int)((y2 - y1) * scale);
        RenderSystem.enableScissor(sx, sy, sw, sh);
    }

    private void disableScissor() {
        RenderSystem.disableScissor();
    }

    // --- Widget scroll tracking ---
    private static class ScrollableWidget {
        final Widget widget;
        final int baseOffsetY;
        ScrollableWidget(Widget widget, int baseOffsetY) {
            this.widget = widget;
            this.baseOffsetY = baseOffsetY;
        }
    }

    // ===================== Vanilla-textured Slider =====================
    public static class VanillaSlider extends Widget {
        private final int minVal;
        private int maxVal;
        private int currentVal;
        private double sliderPos;
        private boolean dragging = false;
        private final Consumer<Integer> onChange;
        private final boolean showPercent;

        public VanillaSlider(int x, int y, int width, int height,
                             int minVal, int maxVal, int currentVal, Consumer<Integer> onChange) {
            this(x, y, width, height, minVal, maxVal, currentVal, false, onChange);
        }

        public VanillaSlider(int x, int y, int width, int height,
                             int minVal, int maxVal, int currentVal, boolean showPercent, Consumer<Integer> onChange) {
            super(x, y, width, height, new StringTextComponent(""));
            this.minVal = minVal;
            this.maxVal = maxVal;
            this.currentVal = MathHelper.clamp(currentVal, minVal, maxVal);
            this.sliderPos = (maxVal > minVal) ? (double)(this.currentVal - minVal) / (maxVal - minVal) : 0;
            this.onChange = onChange;
            this.showPercent = showPercent;
        }

        @Override
        public void renderButton(MatrixStack ms, int mouseX, int mouseY, float partialTicks) {
            Minecraft mc = Minecraft.getInstance();
            mc.getTextureManager().bind(WIDGETS_TEX);

            RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            int halfH = height / 2;
            int texFullH = 20;
            // Track background (inactive button y=46) - 4-corner 9-slice
            blit(ms, x, y, 0, 46, width / 2, halfH);
            blit(ms, x + width / 2, y, 200 - width / 2, 46, width / 2, halfH);
            blit(ms, x, y + halfH, 0, 46 + texFullH - (height - halfH), width / 2, height - halfH);
            blit(ms, x + width / 2, y + halfH, 200 - width / 2, 46 + texFullH - (height - halfH), width / 2, height - halfH);

            // Handle - 4-corner 9-slice
            int handleW = 8;
            int handleX = x + (int)(sliderPos * (width - handleW));
            int handleTexY = this.isHovered() ? 86 : 66;
            blit(ms, handleX, y, 0, handleTexY, handleW / 2, halfH);
            blit(ms, handleX + handleW / 2, y, 200 - handleW / 2, handleTexY, handleW / 2, halfH);
            blit(ms, handleX, y + halfH, 0, handleTexY + texFullH - (height - halfH), handleW / 2, height - halfH);
            blit(ms, handleX + handleW / 2, y + halfH, 200 - handleW / 2, handleTexY + texFullH - (height - halfH), handleW / 2, height - halfH);

            // Value text centered
            FontRenderer font = mc.font;
            String text = showPercent ? currentVal + "%" : String.valueOf(currentVal);
            int tw = font.width(text);
            font.drawShadow(ms, text, x + (width - tw) / 2.0f, y + (height - 8) / 2.0f, COL_TEXT);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            dragging = true;
            updateSliderFromMouse(mouseX);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            if (dragging) updateSliderFromMouse(mouseX);
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            dragging = false;
        }

        private void updateSliderFromMouse(double mouseX) {
            int handleW = 8;
            sliderPos = MathHelper.clamp((mouseX - x - handleW / 2.0) / (width - handleW), 0.0, 1.0);
            int newVal = (int)Math.round(minVal + sliderPos * (maxVal - minVal));
            newVal = MathHelper.clamp(newVal, minVal, maxVal);
            if (newVal != currentVal) {
                currentVal = newVal;
                onChange.accept(currentVal);
            }
            sliderPos = (maxVal > minVal) ? (double)(currentVal - minVal) / (maxVal - minVal) : 0;
        }
    }

    // ===================== Vanilla-textured Toggle =====================
    public static class VanillaToggle extends Widget {
        private boolean enabled;
        private final Consumer<Boolean> onChange;

        public VanillaToggle(int x, int y, int width, int height,
                             boolean initialState, Consumer<Boolean> onChange) {
            super(x, y, width, height, new StringTextComponent(""));
            this.enabled = initialState;
            this.onChange = onChange;
        }

        @Override
        public void renderButton(MatrixStack ms, int mouseX, int mouseY, float partialTicks) {
            Minecraft mc = Minecraft.getInstance();
            mc.getTextureManager().bind(WIDGETS_TEX);

            RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            int texY = this.isHovered() ? 86 : 66;
            int halfH = height / 2;
            int texFullH = 20;
            // 4-corner 9-slice rendering
            blit(ms, x, y, 0, texY, width / 2, halfH);
            blit(ms, x + width / 2, y, 200 - width / 2, texY, width / 2, halfH);
            blit(ms, x, y + halfH, 0, texY + texFullH - (height - halfH), width / 2, height - halfH);
            blit(ms, x + width / 2, y + halfH, 200 - width / 2, texY + texFullH - (height - halfH), width / 2, height - halfH);

            FontRenderer font = mc.font;
            String label = enabled ? "ON" : "OFF";
            int color = enabled ? 0xFF55FF55 : 0xFFFF5555;
            int tw = font.width(label);
            font.drawShadow(ms, label, x + (width - tw) / 2.0f, y + (height - 8) / 2.0f, color);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            enabled = !enabled;
            onChange.accept(enabled);
        }
    }
}
