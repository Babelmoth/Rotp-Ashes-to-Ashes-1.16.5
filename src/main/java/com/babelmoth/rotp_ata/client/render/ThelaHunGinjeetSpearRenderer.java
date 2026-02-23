package com.babelmoth.rotp_ata.client.render;

import com.babelmoth.rotp_ata.entity.ThelaHunGinjeetSpearEntity;
import com.babelmoth.rotp_ata.init.InitItems;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.client.renderer.culling.ClippingHelper;
import com.github.standobyte.jojo.client.ClientUtil;

public class ThelaHunGinjeetSpearRenderer extends EntityRenderer<ThelaHunGinjeetSpearEntity> {

    @Override
    public boolean shouldRender(ThelaHunGinjeetSpearEntity entity, ClippingHelper pCamera, double pCamX, double pCamY, double pCamZ) {
        if (!ClientUtil.canSeeStands()) return false;
        return super.shouldRender(entity, pCamera, pCamX, pCamY, pCamZ);
    }

    private final ItemRenderer itemRenderer;

    public ThelaHunGinjeetSpearRenderer(EntityRendererManager renderManager) {
        super(renderManager);
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void render(ThelaHunGinjeetSpearEntity entity, float entityYaw, float partialTicks, MatrixStack matrixStack,
            IRenderTypeBuffer buffer, int packedLight) {
        if (!entity.isInvisible()) {
            matrixStack.pushPose();

            float yaw = MathHelper.lerp(partialTicks, entity.yRotO, entity.yRot) - 180.0F;
            float pitch = MathHelper.lerp(partialTicks, entity.xRotO, entity.xRot) + 90.0F;
            matrixStack.mulPose(Vector3f.YP.rotationDegrees(yaw));
            matrixStack.mulPose(Vector3f.XP.rotationDegrees(pitch));

            matrixStack.mulPose(Vector3f.XP.rotationDegrees(180.0F));

            matrixStack.scale(0.7F, 0.7F, 0.7F);

            ItemStack modelStack = new ItemStack(InitItems.THELA_HUN_GINJEET_SPEAR_RENDER.get());
            itemRenderer.renderStatic(modelStack, ItemCameraTransforms.TransformType.FIXED,
                    packedLight, OverlayTexture.NO_OVERLAY, matrixStack, buffer);

            matrixStack.popPose();
        }

        super.render(entity, entityYaw, partialTicks, matrixStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ThelaHunGinjeetSpearEntity entity) {
        return net.minecraft.client.renderer.texture.AtlasTexture.LOCATION_BLOCKS;
    }
}
