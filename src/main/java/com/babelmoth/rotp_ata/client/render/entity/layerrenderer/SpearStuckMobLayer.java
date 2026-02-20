package com.babelmoth.rotp_ata.client.render.entity.layerrenderer;

import java.util.Random;

import com.babelmoth.rotp_ata.capability.SpearStuckProvider;
import com.babelmoth.rotp_ata.entity.ThelaHunGinjeetSpearEntity;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import com.github.standobyte.jojo.client.ClientUtil;

public class SpearStuckMobLayer<T extends LivingEntity, M extends EntityModel<T>> extends LayerRenderer<T, M> {
    private final EntityRendererManager dispatcher;

    public SpearStuckMobLayer(LivingRenderer<T, M> renderer) {
        super(renderer);
        this.dispatcher = renderer.getDispatcher();
    }

    @Override
    public void render(MatrixStack matrixStack, IRenderTypeBuffer buffer, int packedLight, T entity,
            float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!ClientUtil.canSeeStands()) return;
        if (SpearStuckProvider.SPEAR_STUCK_CAPABILITY == null) return;

        int count = entity.getCapability(SpearStuckProvider.SPEAR_STUCK_CAPABILITY)
                .map(cap -> cap.getSpearCount()).orElse(0);
        if (count <= 0) return;

        Random random = new Random((long) entity.getId());
        float entityHeight = entity.getBbHeight();
        float entityWidth = entity.getBbWidth();

        for (int i = 0; i < count; i++) {
            matrixStack.pushPose();

            // 随机水平角度（0~360度）+ 小幅偏移让刺入方向不完全对准中心
            float yawDeg = random.nextFloat() * 360.0F + (random.nextFloat() - 0.5F) * 30.0F;
            // pitch 在 -45° 到 45° 之间（有明显歪斜但仍像从外部刺入）
            float pitchDeg = (random.nextFloat() - 0.5F) * 90.0F;

            // 位置：在实体身体表面（Y轴已翻转：0=头顶，height=脚底）
            // 集中在上半身区域（0%~60% 高度，即头顶到胸部）
            float offY = random.nextFloat() * entityHeight * 0.6F;
            float yawRad = (float) Math.toRadians(yawDeg);
            float offX = MathHelper.sin(yawRad) * entityWidth * 0.5F;
            float offZ = MathHelper.cos(yawRad) * entityWidth * 0.5F;
            matrixStack.translate(offX, offY, offZ);

            ThelaHunGinjeetSpearEntity spear = new ThelaHunGinjeetSpearEntity(entity.level, entity.getX(), entity.getY(), entity.getZ());
            spear.yRot = yawDeg;
            spear.xRot = pitchDeg;
            spear.yRotO = spear.yRot;
            spear.xRotO = spear.xRot;
            dispatcher.render(spear, 0.0D, 0.0D, 0.0D, 0.0F, partialTicks, matrixStack, buffer, packedLight);

            matrixStack.popPose();
        }
    }
}
