package com.babelmoth.rotp_ata.client.render;

import com.babelmoth.rotp_ata.block.FrozenBarrierBlockEntity;
import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.babelmoth.rotp_ata.init.InitEntities;
import com.github.standobyte.jojo.power.impl.stand.StandUtil;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3f;

public class FrozenBarrierRenderer extends TileEntityRenderer<FrozenBarrierBlockEntity> {

    private final FossilMothEntity[] cachedMoths = new FossilMothEntity[3];

    public FrozenBarrierRenderer(TileEntityRendererDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public void render(FrozenBarrierBlockEntity blockEntity, float partialTicks, MatrixStack matrixStack,
                       IRenderTypeBuffer buffer, int combinedLight, int combinedOverlay) {

        if (blockEntity.getLevel() == null) return;

        Minecraft mc = Minecraft.getInstance();

        ClientPlayerEntity localPlayer = mc.player;
        if (localPlayer == null || !StandUtil.clStandEntityVisibleTo(localPlayer)) return;
        long gameTime = blockEntity.getLevel().getGameTime();
        float time = gameTime + partialTicks;

        for (int i = 0; i < 3; i++) {

            if (cachedMoths[i] == null) {
                cachedMoths[i] = InitEntities.FOSSIL_MOTH.get().create(blockEntity.getLevel());

            }
            FossilMothEntity moth = cachedMoths[i];
            if (moth == null) continue;

            moth.tickCount = (int) gameTime + 100 + i * 7;

            int maxPerMoth = com.babelmoth.rotp_ata.util.AshesToAshesConstants.MAX_ENERGY_BASE;
            moth.setKineticEnergy(Math.round(blockEntity.getBreakProgress() * maxPerMoth));

            matrixStack.pushPose();

            matrixStack.translate(0.5, 0.5, 0.5);

            long posSeed = blockEntity.getBlockPos().asLong();
            java.util.Random rng = new java.util.Random(posSeed * 31L + i * 7L);
            float seedPhase = rng.nextFloat() * 6.2832F;
            float seedFreqX = 0.85F + rng.nextFloat() * 0.3F;
            float seedFreqZ = 0.85F + rng.nextFloat() * 0.3F;
            float seedFreqY = 0.85F + rng.nextFloat() * 0.3F;

            float phase = i * 2.094F + seedPhase;
            float t = time * 0.04F;

            float r1 = 0.28F + MathHelper.sin(t * 0.7F * seedFreqX + phase) * 0.08F;
            float r2 = 0.12F + MathHelper.sin(t * 1.1F * seedFreqZ + phase * 1.5F) * 0.05F;
            float offsetX = MathHelper.sin(t * (0.9F + i * 0.13F) * seedFreqX + phase) * r1
                          + MathHelper.sin(t * (1.7F + i * 0.2F) * seedFreqX + phase * 2.3F) * r2;
            float offsetZ = MathHelper.cos(t * (0.6F + i * 0.11F) * seedFreqZ + phase) * r1
                          + MathHelper.cos(t * (1.4F + i * 0.17F) * seedFreqZ + phase * 1.8F) * r2;
            float offsetY = MathHelper.sin(t * (1.3F + i * 0.18F) * seedFreqY + phase * 0.7F) * 0.14F
                          + MathHelper.sin(t * (2.1F + i * 0.25F) * seedFreqY + phase * 1.4F) * 0.06F;

            matrixStack.translate(offsetX, offsetY, offsetZ);

            float dt = 0.02F;
            float nextX = MathHelper.sin((t + dt) * (0.9F + i * 0.13F) * seedFreqX + phase) * r1
                        + MathHelper.sin((t + dt) * (1.7F + i * 0.2F) * seedFreqX + phase * 2.3F) * r2;
            float nextZ = MathHelper.cos((t + dt) * (0.6F + i * 0.11F) * seedFreqZ + phase) * r1
                        + MathHelper.cos((t + dt) * (1.4F + i * 0.17F) * seedFreqZ + phase * 1.8F) * r2;
            float facingAngle = (float) Math.toDegrees(Math.atan2(nextX - offsetX, nextZ - offsetZ));
            matrixStack.mulPose(Vector3f.YP.rotationDegrees(facingAngle));

            float roll = MathHelper.sin(t * 2.5F * seedFreqX + phase * 1.2F) * 10.0F;
            matrixStack.mulPose(Vector3f.ZP.rotationDegrees(roll));

            try {
                mc.getEntityRenderDispatcher().render(moth, 0, 0, 0, 0, partialTicks, matrixStack, buffer, combinedLight);
            } catch (Exception e) {

            }

            matrixStack.popPose();
        }
    }
}
