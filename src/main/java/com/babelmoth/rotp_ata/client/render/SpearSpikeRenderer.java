package com.babelmoth.rotp_ata.client.render;

import com.babelmoth.rotp_ata.entity.SpearSpikeEntity;
import com.babelmoth.rotp_ata.init.InitItems;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3f;

/**
 * Renderer for decorative spear spike entities.
 * Uses the same spear model as the thrown spear.
 */
public class SpearSpikeRenderer extends EntityRenderer<SpearSpikeEntity> {
    private final ItemRenderer itemRenderer;

    public SpearSpikeRenderer(EntityRendererManager renderManager) {
        super(renderManager);
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void render(SpearSpikeEntity entity, float entityYaw, float partialTicks, MatrixStack matrixStack,
            IRenderTypeBuffer buffer, int packedLight) {
        matrixStack.pushPose();

        // Apply spike rotation (model tip is Y+, no flip needed for ground spikes)
        float yaw = entity.getSpikeYaw();
        float pitch = entity.getSpikePitch();
        matrixStack.mulPose(Vector3f.YP.rotationDegrees(yaw));
        matrixStack.mulPose(Vector3f.XP.rotationDegrees(pitch));

        // Scale to match thrown spear
        matrixStack.scale(0.7F, 0.7F, 0.7F);

        ItemStack modelStack = new ItemStack(InitItems.THELA_HUN_GINJEET_SPEAR_RENDER.get());
        itemRenderer.renderStatic(modelStack, ItemCameraTransforms.TransformType.FIXED,
                packedLight, OverlayTexture.NO_OVERLAY, matrixStack, buffer);

        matrixStack.popPose();
        super.render(entity, entityYaw, partialTicks, matrixStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SpearSpikeEntity entity) {
        return net.minecraft.client.renderer.texture.AtlasTexture.LOCATION_BLOCKS;
    }
}
