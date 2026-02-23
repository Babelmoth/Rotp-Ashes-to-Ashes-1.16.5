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

    // 3个独立的飞蛾实体，各自拥有独立的GeckoLib动画状态
    private final FossilMothEntity[] cachedMoths = new FossilMothEntity[3];

    public FrozenBarrierRenderer(TileEntityRendererDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public void render(FrozenBarrierBlockEntity blockEntity, float partialTicks, MatrixStack matrixStack, 
                       IRenderTypeBuffer buffer, int combinedLight, int combinedOverlay) {
        
        if (blockEntity.getLevel() == null) return;

        Minecraft mc = Minecraft.getInstance();

        // 屏障内飞蛾只对替身使者可见
        ClientPlayerEntity localPlayer = mc.player;
        if (localPlayer == null || !StandUtil.clStandEntityVisibleTo(localPlayer)) return;
        long gameTime = blockEntity.getLevel().getGameTime();
        float time = gameTime + partialTicks;

        for (int i = 0; i < 3; i++) {
            // 为每只飞蛾创建独立实体（独立动画状态）
            if (cachedMoths[i] == null) {
                cachedMoths[i] = InitEntities.FOSSIL_MOTH.get().create(blockEntity.getLevel());
                // 不设置forceVisibleForAll，飞蛾可见性由渲染器根据替身使者判断控制
            }
            FossilMothEntity moth = cachedMoths[i];
            if (moth == null) continue;

            // 确保飞蛾的tickCount足够大以跳过淡入逻辑，并用偏移实现动画去同步
            moth.tickCount = (int) gameTime + 100 + i * 7;
            // 飞蛾动能 = 破坏进度比例 * 单只飞蛾最大动能
            int maxPerMoth = com.babelmoth.rotp_ata.util.AshesToAshesConstants.MAX_ENERGY_BASE;
            moth.setKineticEnergy(Math.round(blockEntity.getBreakProgress() * maxPerMoth));

            matrixStack.pushPose();

            // 居中到方块内
            matrixStack.translate(0.5, 0.5, 0.5);

            // 使用方块位置哈希生成每个屏障独有的随机种子
            long posSeed = blockEntity.getBlockPos().asLong();
            java.util.Random rng = new java.util.Random(posSeed * 31L + i * 7L);
            float seedPhase = rng.nextFloat() * 6.2832F; // 0~2π随机相位
            float seedFreqX = 0.85F + rng.nextFloat() * 0.3F; // 频率变化0.85~1.15
            float seedFreqZ = 0.85F + rng.nextFloat() * 0.3F;
            float seedFreqY = 0.85F + rng.nextFloat() * 0.3F;

            // 自然飞舞轨迹：多谐波叠加的Lissajous曲线变体，每个屏障独特
            float phase = i * 2.094F + seedPhase; // 120度相位差 + 屏障独有偏移
            float t = time * 0.04F;

            // 主运动 + 次谐波叠加，产生不对称飞行路径
            float r1 = 0.28F + MathHelper.sin(t * 0.7F * seedFreqX + phase) * 0.08F;
            float r2 = 0.12F + MathHelper.sin(t * 1.1F * seedFreqZ + phase * 1.5F) * 0.05F;
            float offsetX = MathHelper.sin(t * (0.9F + i * 0.13F) * seedFreqX + phase) * r1
                          + MathHelper.sin(t * (1.7F + i * 0.2F) * seedFreqX + phase * 2.3F) * r2;
            float offsetZ = MathHelper.cos(t * (0.6F + i * 0.11F) * seedFreqZ + phase) * r1
                          + MathHelper.cos(t * (1.4F + i * 0.17F) * seedFreqZ + phase * 1.8F) * r2;
            float offsetY = MathHelper.sin(t * (1.3F + i * 0.18F) * seedFreqY + phase * 0.7F) * 0.14F
                          + MathHelper.sin(t * (2.1F + i * 0.25F) * seedFreqY + phase * 1.4F) * 0.06F;

            matrixStack.translate(offsetX, offsetY, offsetZ);

            // 根据运动方向计算朝向（使用速度的近似导数）
            float dt = 0.02F;
            float nextX = MathHelper.sin((t + dt) * (0.9F + i * 0.13F) * seedFreqX + phase) * r1
                        + MathHelper.sin((t + dt) * (1.7F + i * 0.2F) * seedFreqX + phase * 2.3F) * r2;
            float nextZ = MathHelper.cos((t + dt) * (0.6F + i * 0.11F) * seedFreqZ + phase) * r1
                        + MathHelper.cos((t + dt) * (1.4F + i * 0.17F) * seedFreqZ + phase * 1.8F) * r2;
            float facingAngle = (float) Math.toDegrees(Math.atan2(nextX - offsetX, nextZ - offsetZ));
            matrixStack.mulPose(Vector3f.YP.rotationDegrees(facingAngle));

            // 身体倾斜（模拟转弯时的侧倾，幅度随运动变化）
            float roll = MathHelper.sin(t * 2.5F * seedFreqX + phase * 1.2F) * 10.0F;
            matrixStack.mulPose(Vector3f.ZP.rotationDegrees(roll));

            try {
                mc.getEntityRenderDispatcher().render(moth, 0, 0, 0, 0, partialTicks, matrixStack, buffer, combinedLight);
            } catch (Exception e) {
                // Ignore rendering errors
            }

            matrixStack.popPose();
        }
    }
}
