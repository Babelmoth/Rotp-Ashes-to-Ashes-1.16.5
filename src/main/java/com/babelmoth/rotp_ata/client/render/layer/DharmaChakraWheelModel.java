package com.babelmoth.rotp_ata.client.render.layer;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;

import com.babelmoth.rotp_ata.entity.DharmaChakraEntity;
import com.github.standobyte.jojo.client.render.entity.model.stand.StandEntityModel;
import com.github.standobyte.jojo.client.render.entity.pose.ModelPose;
import com.github.standobyte.jojo.client.render.entity.pose.RotationAngle;
import com.github.standobyte.jojo.power.impl.stand.StandInstance.StandPart;
import com.google.common.collect.ImmutableList;

import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.util.HandSide;

public class DharmaChakraWheelModel extends StandEntityModel<DharmaChakraEntity> {

    private final ModelRenderer armorHead;
    private final ModelRenderer wheel;
    private final ModelRenderer bone;

    public DharmaChakraWheelModel() {
        super(false, 0.0F, 0.0F);

        texWidth = 64;
        texHeight = 64;

        armorHead = new ModelRenderer(this);
        armorHead.setPos(0.0F, 24.0F, 0.0F);

        wheel = new ModelRenderer(this);
        wheel.setPos(0.0F, 10.0F, 1.0F);
        wheel.xRot = (float) Math.toRadians(-10);
        armorHead.addChild(wheel);

        wheel.texOffs(0, 26).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F, 0.0F);
        wheel.texOffs(0, 26).addBox(5.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F, 0.0F);
        wheel.texOffs(0, 26).addBox(-7.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F, 0.0F);

        ModelRenderer crossLine1 = new ModelRenderer(this);
        crossLine1.setPos(0.0F, 0.9F, -0.1F);
        crossLine1.yRot = (float) Math.toRadians(135);
        crossLine1.texOffs(34, 6).addBox(-6.7F, 0.0F, -0.5F, 13.4F, 0.0F, 1.0F, 0.0F);
        wheel.addChild(crossLine1);

        ModelRenderer crossLine2 = new ModelRenderer(this);
        crossLine2.setPos(0.0F, 0.9F, -0.1F);
        crossLine2.yRot = (float) Math.toRadians(45);
        crossLine2.texOffs(35, 6).addBox(-6.4F, 0.0F, -0.5F, 12.8F, 0.0F, 1.0F, 0.0F);
        wheel.addChild(crossLine2);

        ModelRenderer crossLine3 = new ModelRenderer(this);
        crossLine3.setPos(0.0F, 0.9F, -0.1F);
        crossLine3.yRot = (float) Math.toRadians(90);
        crossLine3.texOffs(36, 6).addBox(-5.0F, 0.0F, -0.5F, 10.0F, 0.0F, 1.0F, 0.0F);
        wheel.addChild(crossLine3);

        wheel.texOffs(36, 6).addBox(-5.0F, 0.9F, -0.6F, 10.0F, 0.0F, 1.0F, 0.0F);

        wheel.texOffs(0, 0).addBox(-5.0F, 1.0F, -5.0F, 10.0F, 0.0F, 10.0F, 0.0F);

        wheel.texOffs(0, 26).addBox(-6.0F, 0.0F, 4.0F, 2.0F, 2.0F, 2.0F, 0.0F);
        wheel.texOffs(0, 26).addBox(4.0F, 0.0F, 4.0F, 2.0F, 2.0F, 2.0F, 0.0F);
        wheel.texOffs(0, 26).addBox(-1.0F, 0.0F, 5.0F, 2.0F, 2.0F, 2.0F, 0.0F);
        wheel.texOffs(0, 26).addBox(4.0F, 0.0F, -6.0F, 2.0F, 2.0F, 2.0F, 0.0F);
        wheel.texOffs(0, 26).addBox(-6.0F, 0.0F, -6.0F, 2.0F, 2.0F, 2.0F, 0.0F);
        wheel.texOffs(0, 26).addBox(-1.0F, 0.0F, -7.0F, 2.0F, 2.0F, 2.0F, 0.0F);

        bone = new ModelRenderer(this);
        bone.setPos(0.0F, 0.0F, 0.0F);
        wheel.addChild(bone);

        bone.texOffs(0, 50).addBox(-1.1F, -0.1F, -1.1F, 2.2F, 2.2F, 2.2F, 0.0F);
        bone.texOffs(0, 50).addBox(-7.1F, -0.1F, -1.1F, 2.2F, 2.2F, 2.2F, 0.0F);
        bone.texOffs(0, 50).addBox(4.9F, -0.1F, -1.1F, 2.2F, 2.2F, 2.2F, 0.0F);
        bone.texOffs(0, 50).addBox(-6.1F, -0.1F, 3.9F, 2.2F, 2.2F, 2.2F, 0.0F);
        bone.texOffs(0, 50).addBox(3.9F, -0.1F, 3.9F, 2.2F, 2.2F, 2.2F, 0.0F);
        bone.texOffs(0, 50).addBox(-1.1F, -0.1F, 4.9F, 2.2F, 2.2F, 2.2F, 0.0F);
        bone.texOffs(0, 50).addBox(3.9F, -0.1F, -6.1F, 2.2F, 2.2F, 2.2F, 0.0F);
        bone.texOffs(0, 50).addBox(-6.1F, -0.1F, -6.1F, 2.2F, 2.2F, 2.2F, 0.0F);
        bone.texOffs(0, 50).addBox(-1.1F, -0.1F, -7.1F, 2.2F, 2.2F, 2.2F, 0.0F);
        bone.texOffs(27, 44).addBox(-5.1F, 0.9F, -5.1F, 10.2F, 0.2F, 10.2F, 0.0F);

        ModelRenderer boneLine1 = new ModelRenderer(this);
        boneLine1.setPos(0.0F, 0.9F, -0.1F);
        boneLine1.yRot = (float) Math.toRadians(45);
        boneLine1.texOffs(0, 53).addBox(-6.5F, -0.1F, -0.7F, 13.0F, 0.2F, 1.2F, 0.0F);
        bone.addChild(boneLine1);

        ModelRenderer boneLine2 = new ModelRenderer(this);
        boneLine2.setPos(0.0F, 0.9F, -0.1F);
        boneLine2.yRot = (float) Math.toRadians(135);
        boneLine2.texOffs(0, 53).addBox(-6.8F, -0.1F, -0.7F, 13.6F, 0.2F, 1.2F, 0.0F);
        bone.addChild(boneLine2);

        ModelRenderer boneLine3 = new ModelRenderer(this);
        boneLine3.setPos(0.0F, 0.9F, -0.1F);
        boneLine3.yRot = (float) Math.toRadians(90);
        boneLine3.texOffs(0, 53).addBox(-5.1F, -0.1F, -0.7F, 10.2F, 0.2F, 1.2F, 0.0F);
        bone.addChild(boneLine3);

        bone.texOffs(0, 53).addBox(-5.1F, 0.8F, -0.7F, 10.2F, 0.2F, 1.2F, 0.0F);

        putNamedModelPart("root", armorHead);
    }

    @Override
    protected void partMissing(StandPart standPart) {}

    @Override
    public ModelRenderer getArm(HandSide side) {
        return null;
    }

    @Override
    public void translateToHand(HandSide handSide, MatrixStack matrixStack) {
    }

    @Override
    public Iterable<ModelRenderer> headParts() {
        return ImmutableList.of();
    }

    @Override
    public Iterable<ModelRenderer> bodyParts() {
        return ImmutableList.of(armorHead);
    }

    @Override
    protected ModelPose<DharmaChakraEntity> initPoseReset() {
         return new ModelPose<>();
    }

    @Override
    public void renderToBuffer(MatrixStack matrixStack, IVertexBuilder buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        armorHead.render(matrixStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    public void renderWheelOnly(MatrixStack matrixStack, IVertexBuilder buffer, int packedLight, int packedOverlay, float r, float g, float b, float a) {
        bone.visible = false;
        armorHead.render(matrixStack, buffer, packedLight, packedOverlay, r, g, b, a);
        bone.visible = true;
    }

    public void renderBoneOnly(MatrixStack matrixStack, IVertexBuilder buffer, int packedLight, int packedOverlay, float r, float g, float b, float a) {
        matrixStack.pushPose();
        armorHead.translateAndRotate(matrixStack);
        wheel.translateAndRotate(matrixStack);
        bone.render(matrixStack, buffer, packedLight, packedOverlay, r, g, b, a);
        matrixStack.popPose();
    }
}
