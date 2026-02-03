package com.babelmoth.rotp_ata.client.render.layer;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.renderers.geo.GeoLayerRenderer;
import software.bernie.geckolib3.renderers.geo.IGeoRenderer;

public class FossilMothAmberLayer extends GeoLayerRenderer<FossilMothEntity> {
    private static final ResourceLocation AMBER = new ResourceLocation(AddonMain.MOD_ID, "textures/entity/ashes_to_ashes_amber.png");

    public FossilMothAmberLayer(IGeoRenderer<FossilMothEntity> entityRendererIn) {
        super(entityRendererIn);
    }
    
    @Override
    public RenderType getRenderType(ResourceLocation textureLocation) {
        return RenderType.entityTranslucent(textureLocation);
    }

    @Override
    public void render(MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn, FossilMothEntity entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        int energy = entity.getTotalEnergy(); // Combined kinetic + Hamon energy
        if (energy > 0) {
            float maxEnergy = (float) entity.getMaxEnergy();
            float alpha = (float) energy / maxEnergy;
            alpha = Math.max(0.05f, Math.min(1.0f, alpha)); // Min 5% visibility
            
            // Get the model from parent renderer
            ResourceLocation modelLoc = this.getRenderer().getGeoModelProvider().getModelLocation(entity);
            GeoModel model = this.getRenderer().getGeoModelProvider().getModel(modelLoc);
            
            // Create vertex buffer with translucent render type
            RenderType renderType = RenderType.entityTranslucent(AMBER);
            IVertexBuilder vertexBuffer = bufferIn.getBuffer(renderType);
            
            // Render the model with amber texture and alpha
            this.getRenderer().render(
                model,
                entity,
                partialTicks,
                renderType,
                matrixStackIn,
                bufferIn,
                vertexBuffer,
                15728880, // Full Brightness (MAX_LIGHT) for glowing effect
                OverlayTexture.NO_OVERLAY,
                1.0f, 0.85f, 0.4f, alpha  // Amber tint: yellow-orange
            );
        }
    }
}
