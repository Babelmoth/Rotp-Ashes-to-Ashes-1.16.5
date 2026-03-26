package com.babelmoth.rotp_ata.client.render;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.entity.ThelaHunGinjeetStandEntity;
import com.github.standobyte.jojo.client.render.entity.model.stand.StandEntityModel;
import com.github.standobyte.jojo.client.render.entity.model.stand.StandModelRegistry;
import com.github.standobyte.jojo.client.render.entity.renderer.stand.StandEntityRenderer;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Matrix3f;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.math.vector.Vector4f;
import software.bernie.geckolib3.geo.render.built.GeoBone;
import software.bernie.geckolib3.geo.render.built.GeoCube;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.geo.render.built.GeoQuad;
import software.bernie.geckolib3.geo.render.built.GeoVertex;
import software.bernie.geckolib3.resource.GeckoLibCache;
import software.bernie.geckolib3.util.RenderUtils;

public class ThelaHunGinjeetStandRenderer extends StandEntityRenderer<ThelaHunGinjeetStandEntity, StandEntityModel<ThelaHunGinjeetStandEntity>> {

    private static final ResourceLocation SPEAR_MODEL_LOC = new ResourceLocation(AddonMain.MOD_ID, "geo/thela_hun_ginjeet_spear_display.geo.json");
    private static final ResourceLocation SPEAR_TEXTURE_LOC = new ResourceLocation(AddonMain.MOD_ID, "textures/thela_hun_ginjeet_spear.png");

    public ThelaHunGinjeetStandRenderer(EntityRendererManager renderManager) {
        super(renderManager,
                StandModelRegistry.registerModel(new ResourceLocation(AddonMain.MOD_ID, "thela_hun_ginjeet"), ThelaHunGinjeetStandModel::new),
                new ResourceLocation(AddonMain.MOD_ID, "textures/entity/stand/thela_hun_ginjeet.png"), 0);
    }

    @Override
    public void render(ThelaHunGinjeetStandEntity entity, float entityYaw, float partialTicks,
            MatrixStack matrixStack, IRenderTypeBuffer buffer, int packedLight) {
        GeoModel geoModel = GeckoLibCache.getInstance().getGeoModels().get(SPEAR_MODEL_LOC);
        if (geoModel == null) return;

        matrixStack.pushPose();
        matrixStack.scale(0.4F, 0.4F, 0.4F);
        matrixStack.translate(0, 2.0, 0);

        RenderType renderType = RenderType.entityCutoutNoCull(SPEAR_TEXTURE_LOC);
        IVertexBuilder vertexBuilder = buffer.getBuffer(renderType);
        int packedOverlay = OverlayTexture.NO_OVERLAY;

        for (GeoBone bone : geoModel.topLevelBones) {
            renderBoneRecursive(bone, matrixStack, vertexBuilder, packedLight, packedOverlay, 1f, 1f, 1f, 1f);
        }

        matrixStack.popPose();
    }

    private void renderBoneRecursive(GeoBone bone, MatrixStack stack, IVertexBuilder bufferIn,
            int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        stack.pushPose();

        RenderUtils.translate(bone, stack);
        RenderUtils.moveToPivot(bone, stack);
        RenderUtils.rotate(bone, stack);
        RenderUtils.scale(bone, stack);
        RenderUtils.moveBackFromPivot(bone, stack);

        if (!bone.isHidden()) {
            for (GeoCube cube : bone.childCubes) {
                stack.pushPose();
                renderCube(cube, stack, bufferIn, packedLight, packedOverlay, red, green, blue, alpha);
                stack.popPose();
            }
        }

        if (!bone.childBonesAreHiddenToo()) {
            for (GeoBone child : bone.childBones) {
                renderBoneRecursive(child, stack, bufferIn, packedLight, packedOverlay, red, green, blue, alpha);
            }
        }

        stack.popPose();
    }

    private void renderCube(GeoCube cube, MatrixStack stack, IVertexBuilder bufferIn,
            int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        RenderUtils.moveToPivot(cube, stack);
        RenderUtils.rotate(cube, stack);
        RenderUtils.moveBackFromPivot(cube, stack);

        Matrix4f matrix4f = stack.last().pose();
        Matrix3f matrix3f = stack.last().normal();

        for (GeoQuad quad : cube.quads) {
            if (quad == null) continue;
            Vector3f normal = quad.normal.copy();
            normal.transform(matrix3f);

            for (GeoVertex vertex : quad.vertices) {
                Vector4f pos = new Vector4f(vertex.position.x(), vertex.position.y(), vertex.position.z(), 1.0F);
                pos.transform(matrix4f);
                bufferIn.vertex(pos.x(), pos.y(), pos.z(), red, green, blue, alpha,
                        vertex.textureU, vertex.textureV, packedOverlay, packedLight,
                        normal.x(), normal.y(), normal.z());
            }
        }
    }
}
