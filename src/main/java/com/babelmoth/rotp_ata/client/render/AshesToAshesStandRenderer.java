package com.babelmoth.rotp_ata.client.render;

import com.github.standobyte.jojo.client.render.entity.model.stand.StandEntityModel;
import com.github.standobyte.jojo.client.render.entity.model.stand.StandModelRegistry;
import com.github.standobyte.jojo.client.render.entity.renderer.stand.StandEntityRenderer;
import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.util.ResourceLocation;

/**
 * Renderer for Ashes to Ashes Stand (灰烬归灰烬)
 * 
 * 群体型替身，本体完全不可见
 */
public class AshesToAshesStandRenderer extends StandEntityRenderer<AshesToAshesStandEntity, StandEntityModel<AshesToAshesStandEntity>> {
    
    public AshesToAshesStandRenderer(EntityRendererManager renderManager) {
        super(renderManager, 
                StandModelRegistry.registerModel(new ResourceLocation(AddonMain.MOD_ID, "ashes_to_ashes"), AshesToAshesStandModel::new), 
                new ResourceLocation(AddonMain.MOD_ID, "textures/entity/stand/ashes_to_ashes.png"), 0);
    }
    
    @Override
    public void render(AshesToAshesStandEntity entity, float entityYaw, float partialTicks, 
            MatrixStack matrixStack, IRenderTypeBuffer buffer, int packedLight) {
        // 群体替身不渲染本体，完全透明
        // 不调用 super.render() 以避免渲染任何东西
    }
}
