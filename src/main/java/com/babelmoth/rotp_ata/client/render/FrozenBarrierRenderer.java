package com.babelmoth.rotp_ata.client.render;

import com.babelmoth.rotp_ata.block.FrozenBarrierBlockEntity;
import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.babelmoth.rotp_ata.init.InitEntities;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3f;

public class FrozenBarrierRenderer extends TileEntityRenderer<FrozenBarrierBlockEntity> {

    private FossilMothEntity cachedMoth;

    public FrozenBarrierRenderer(TileEntityRendererDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public void render(FrozenBarrierBlockEntity blockEntity, float partialTicks, MatrixStack matrixStack, 
                       IRenderTypeBuffer buffer, int combinedLight, int combinedOverlay) {
        
        if (blockEntity.getLevel() == null) return;

        // Create cached moth for rendering (like spawner)
        if (cachedMoth == null) {
            cachedMoth = InitEntities.FOSSIL_MOTH.get().create(blockEntity.getLevel());
        }
        if (cachedMoth == null) return;

        Minecraft mc = Minecraft.getInstance();
        EntityRenderer<?> entityRenderer = mc.getEntityRenderDispatcher().getRenderer(cachedMoth);
        if (entityRenderer == null) return;

        // Render 3 moths at different positions/rotations
        long gameTime = blockEntity.getLevel().getGameTime();
        
        // Update cached moth properties for rendering correctness
        cachedMoth.tickCount = (int) gameTime + 100; // Ensure > 15 for full opacity (fade-in logic)
        cachedMoth.setKineticEnergy(blockEntity.getKineticEnergy()); // Sync kinetic energy for amber glow
        
        // Manually update animation manager time if needed for GeckoLib (usually tickCount is enough)
        
        for (int i = 0; i < 3; i++) {
            matrixStack.pushPose();
            
            // Center the moth in the block
            matrixStack.translate(0.5, 0.5, 0.5);
            
            // Calculate rotation and position offset for each moth
            float baseAngle = (gameTime + partialTicks + i * 120) * 2.0F;
            float radius = 0.2F;
            float offsetX = MathHelper.sin((float) Math.toRadians(baseAngle)) * radius;
            float offsetZ = MathHelper.cos((float) Math.toRadians(baseAngle)) * radius;
            float offsetY = MathHelper.sin((float) Math.toRadians(baseAngle * 0.5F + i * 40)) * 0.1F;
            
            matrixStack.translate(offsetX, offsetY, offsetZ);
            
            // Scale Model
            float scale = 1.0F; // Match entity size
            matrixStack.scale(scale, scale, scale);
            
            // Rotate to face movement direction
            matrixStack.mulPose(Vector3f.YP.rotationDegrees(-baseAngle));
            
            // Render the moth entity
            // Use entityRenderDispatcher with full opacity forced by tickCount
            try {
                mc.getEntityRenderDispatcher().render(cachedMoth, 0, 0, 0, 0, partialTicks, matrixStack, buffer, combinedLight);
            } catch (Exception e) {
                // Ignore rendering errors
            }
            
            matrixStack.popPose();
        }
    }
}
