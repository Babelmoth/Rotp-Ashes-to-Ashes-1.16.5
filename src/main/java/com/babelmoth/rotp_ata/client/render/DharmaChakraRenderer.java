package com.babelmoth.rotp_ata.client.render;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.client.render.layer.DharmaChakraWheelModel;
import com.babelmoth.rotp_ata.entity.DharmaChakraEntity;
import com.github.standobyte.jojo.client.render.entity.renderer.stand.StandEntityRenderer;

import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.util.ResourceLocation;

public class DharmaChakraRenderer extends StandEntityRenderer<DharmaChakraEntity, DharmaChakraWheelModel> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(AddonMain.MOD_ID, "textures/entity/dharma_chakra.png");

    public DharmaChakraRenderer(EntityRendererManager renderManager) {
        super(renderManager, new DharmaChakraWheelModel(), TEXTURE, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(DharmaChakraEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(DharmaChakraEntity entity, float entityYaw, float partialTicks,
            com.mojang.blaze3d.matrix.MatrixStack matrixStack, net.minecraft.client.renderer.IRenderTypeBuffer buffer, int packedLight) {
    }
}
