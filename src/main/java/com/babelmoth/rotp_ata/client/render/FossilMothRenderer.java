package com.babelmoth.rotp_ata.client.render;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.util.Direction;
import net.minecraft.util.math.vector.Vector3f;
import software.bernie.geckolib3.renderers.geo.GeoEntityRenderer;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.renderers.geo.GeoEntityRenderer;

/**
 * GeckoLib fossil moth entity renderer.
 */
public class FossilMothRenderer extends GeoEntityRenderer<FossilMothEntity> {
    
    public FossilMothRenderer(EntityRendererManager manager) {
        super(manager, new FossilMothModel());
        this.shadowRadius = 0.15F;
        this.addLayer(new com.babelmoth.rotp_ata.client.render.layer.FossilMothAmberLayer(this));
    }
    
    @Override
    protected void applyRotations(FossilMothEntity entity, MatrixStack matrixStack, float ageInTicks, float rotationYaw,
            float partialTicks) {
        if (entity.isAttached()) {
            Direction face = entity.getAttachedFace();
            if (face != null) {
                float yaw = 0;
                float pitch = 0;

                switch (face) {
                    case UP:
                        yaw = entity.yRot;
                        pitch = 0;
                        break;
                    case DOWN:
                        yaw = entity.yRot;
                        pitch = 180;
                        break;
                    case NORTH:
                        yaw = 180;
                        pitch = -90;
                        break;
                    case SOUTH:
                        yaw = 0;
                        pitch = -90;
                        break;
                    case WEST:
                        yaw = 90;
                        pitch = -90;
                        break;
                    case EAST:
                        yaw = -90;
                        pitch = -90;
                        break;
                }
                
                matrixStack.mulPose(Vector3f.YP.rotationDegrees(180.0F - yaw));
                matrixStack.mulPose(Vector3f.XP.rotationDegrees(pitch));

                float randomRot = entity.getEntityData().get(FossilMothEntity.ATTACHED_ROTATION);
                matrixStack.mulPose(Vector3f.YP.rotationDegrees(randomRot));
                return;
            }
        }
        
        super.applyRotations(entity, matrixStack, ageInTicks, rotationYaw, partialTicks);
    }

    @Override
    public ResourceLocation getTextureLocation(FossilMothEntity entity) {
        return new ResourceLocation(AddonMain.MOD_ID, "textures/entity/ashes_to_ashes.png");
    }
    
    @Override
    public RenderType getRenderType(FossilMothEntity animatable, float partialTicks, MatrixStack stack,
            IRenderTypeBuffer renderTypeBuffer, IVertexBuilder vertexBuilder, int packedLightIn,
            ResourceLocation textureLocation) {
        stack.scale(0.3F, 0.3F, 0.3F);
        return RenderType.entityTranslucentCull(textureLocation);
    }
    
    @Override
    public void render(FossilMothEntity entity, float entityYaw, float partialTicks, MatrixStack matrixStack,
            IRenderTypeBuffer bufferIn, int packedLightIn) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (entity.isInvisibleTo(mc.player)) {
            return;
        }

        float alpha = entity.getAlpha(partialTicks);

        // Handle spectator/invisibility effect alpha (inspired by StandEntityRenderer)
        if (entity.underInvisibilityEffect() && com.github.standobyte.jojo.util.mod.JojoModUtil.seesInvisibleAsSpectator(mc.player)) {
            alpha *= 0.15F;
        }
        
        if (alpha <= 0.01f) {
            return;
        }
        
        matrixStack.pushPose();
        this.applyRotations(entity, matrixStack, partialTicks, net.minecraft.util.math.MathHelper.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot), partialTicks);

        ResourceLocation modelLoc = this.getGeoModelProvider().getModelLocation(entity);
        software.bernie.geckolib3.geo.render.built.GeoModel model = this.getGeoModelProvider().getModel(modelLoc);
        ResourceLocation textureLocation = this.getTextureLocation(entity);
        matrixStack.scale(0.3F, 0.3F, 0.3F);
        RenderType renderType = RenderType.entityTranslucent(textureLocation);
        IVertexBuilder vertexBuffer = bufferIn.getBuffer(renderType);

        if (this.getGeoModelProvider() instanceof FossilMothModel) {
            ((FossilMothModel) this.getGeoModelProvider()).setLivingAnimations(entity, this.getUniqueID(entity), null);
        }

        this.render(
            model,
            entity,
            partialTicks,
            renderType,
            matrixStack,
            bufferIn,
            vertexBuffer,
            packedLightIn,
            getPackedOverlay(entity, 0),
            1.0f, 1.0f, 1.0f, alpha
        );

        for (software.bernie.geckolib3.renderers.geo.GeoLayerRenderer layer : this.layerRenderers) {
            layer.render(matrixStack, bufferIn, packedLightIn, entity, 0, 0, partialTicks, entity.tickCount + partialTicks, entity.yHeadRot, entity.xRot);
        }

        matrixStack.popPose();
    }
    
    protected int getPackedOverlay(FossilMothEntity entity, float uIn) {
        return net.minecraft.client.renderer.texture.OverlayTexture.pack(
            net.minecraft.client.renderer.texture.OverlayTexture.u(uIn), 
            net.minecraft.client.renderer.texture.OverlayTexture.v(entity.hurtTime > 0 || entity.deathTime > 0)
        );
    }
}
