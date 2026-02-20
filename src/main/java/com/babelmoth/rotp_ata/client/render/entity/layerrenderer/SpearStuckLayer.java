package com.babelmoth.rotp_ata.client.render.entity.layerrenderer;

import com.babelmoth.rotp_ata.capability.SpearStuckProvider;
import com.babelmoth.rotp_ata.entity.ThelaHunGinjeetSpearEntity;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.entity.layers.StuckInBodyLayer;
import net.minecraft.client.renderer.entity.model.PlayerModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import com.github.standobyte.jojo.client.ClientUtil;

public class SpearStuckLayer<T extends LivingEntity, M extends PlayerModel<T>> extends StuckInBodyLayer<T, M> {
    private final EntityRendererManager dispatcher;

    public SpearStuckLayer(LivingRenderer<T, M> renderer) {
        super(renderer);
        this.dispatcher = renderer.getDispatcher();
    }

    @Override
    protected int numStuck(T entity) {
        if (!ClientUtil.canSeeStands()) return 0;
        if (SpearStuckProvider.SPEAR_STUCK_CAPABILITY == null) return 0;
        return entity.getCapability(SpearStuckProvider.SPEAR_STUCK_CAPABILITY)
                .map(cap -> cap.getSpearCount()).orElse(0);
    }

    @Override
    protected void renderStuckItem(MatrixStack matrixStack, IRenderTypeBuffer buffer, int packedLight,
            Entity entity, float x, float y, float z, float partialTick) {
        float f = MathHelper.sqrt(x * x + z * z);
        ThelaHunGinjeetSpearEntity spear = new ThelaHunGinjeetSpearEntity(entity.level, entity.getX(), entity.getY(), entity.getZ());
        spear.yRot = (float) (Math.atan2((double) x, (double) z) * (double) (180F / (float) Math.PI));
        spear.xRot = (float) (Math.atan2((double) y, (double) f) * (double) (180F / (float) Math.PI));
        spear.yRotO = spear.yRot;
        spear.xRotO = spear.xRot;
        dispatcher.render(spear, 0.0D, 0.0D, 0.0D, 0.0F, partialTick, matrixStack, buffer, packedLight);
    }
}
