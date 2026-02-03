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
 * FossilMothRenderer - GeckoLib 化石蛾渲染器
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
                // 完全接管旋转，忽略传入的 rotationYaw (防止AI导致的抖动)
                float yaw = 0;
                float pitch = 0;
                
                // 根据依附面确定固定的 Yaw 和 Pitch
                switch (face) {
                    case UP:
                        yaw = entity.yRot; // 保持实体设定的朝向 (South)
                        pitch = 0;
                        break;
                    case DOWN:
                        yaw = entity.yRot; 
                        pitch = 180; // 翻转
                        break;
                    case NORTH:
                        yaw = 180;
                        pitch = -90; // 垂直，腹部贴墙
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
                
                // 应用随机旋转 (Y轴旋转 - 绕着腹部轴心自转)
                // 之前的 Z轴旋转会导致侧滚(Barrel Roll)，导致翅膀穿模。
                // 我们需要在模型还未倾斜前(local space)绕其Y轴自转。
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
        // 缩放到合适大小
        stack.scale(0.3F, 0.3F, 0.3F);
        
        // 使用半透明渲染用于淡入效果
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
        
        // 如果alpha为0，跳过渲染但仍处理层
        if (alpha <= 0.01f) {
            return;
        }
        
        // 使用与 AmberLayer 相同的手动渲染技术
        matrixStack.pushPose();
        
        // 应用旋转
        this.applyRotations(entity, matrixStack, partialTicks, net.minecraft.util.math.MathHelper.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot), partialTicks);
        
        // 获取模型
        ResourceLocation modelLoc = this.getGeoModelProvider().getModelLocation(entity);
        software.bernie.geckolib3.geo.render.built.GeoModel model = this.getGeoModelProvider().getModel(modelLoc);
        
        // 获取纹理
        ResourceLocation textureLocation = this.getTextureLocation(entity);
        
        // 设置缩放
        matrixStack.scale(0.3F, 0.3F, 0.3F);
        
        // 创建支持透明度的渲染类型
        RenderType renderType = RenderType.entityTranslucent(textureLocation);
        IVertexBuilder vertexBuffer = bufferIn.getBuffer(renderType);
        
        // 更新动画 - 尝试调用 setLivingAnimations (需要3个参数)
        if (this.getGeoModelProvider() instanceof FossilMothModel) {
            ((FossilMothModel) this.getGeoModelProvider()).setLivingAnimations(entity, this.getUniqueID(entity), null);
        }
        
        // 渲染模型，使用alpha
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
            1.0f, 1.0f, 1.0f, alpha  // RGB = 1.0, Alpha = 淡入值
        );
        
        // Render Layers
        for (software.bernie.geckolib3.renderers.geo.GeoLayerRenderer layer : this.layerRenderers) {
            layer.render(matrixStack, bufferIn, packedLightIn, entity, 0, 0, partialTicks, entity.tickCount + partialTicks, entity.yHeadRot, entity.xRot);
        }

        matrixStack.popPose();
        
        // 渲染所有图层 (包括 AmberLayer)
        // 注意：图层需要单独处理，但它们会调用类似的 render 方法
    }
    
    protected int getPackedOverlay(FossilMothEntity entity, float uIn) {
        return net.minecraft.client.renderer.texture.OverlayTexture.pack(
            net.minecraft.client.renderer.texture.OverlayTexture.u(uIn), 
            net.minecraft.client.renderer.texture.OverlayTexture.v(entity.hurtTime > 0 || entity.deathTime > 0)
        );
    }
}
