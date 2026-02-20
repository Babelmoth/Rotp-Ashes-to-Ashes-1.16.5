package com.babelmoth.rotp_ata.client.render;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.entity.ThelaHunGinjeetStandEntity;
import com.github.standobyte.jojo.client.render.entity.model.stand.StandEntityModel;
import com.github.standobyte.jojo.client.render.entity.model.stand.StandModelRegistry;
import com.github.standobyte.jojo.client.render.entity.renderer.stand.StandEntityRenderer;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.util.ResourceLocation;

public class ThelaHunGinjeetStandRenderer extends StandEntityRenderer<ThelaHunGinjeetStandEntity, StandEntityModel<ThelaHunGinjeetStandEntity>> {

    public ThelaHunGinjeetStandRenderer(EntityRendererManager renderManager) {
        super(renderManager,
                StandModelRegistry.registerModel(new ResourceLocation(AddonMain.MOD_ID, "thela_hun_ginjeet"), ThelaHunGinjeetStandModel::new),
                new ResourceLocation(AddonMain.MOD_ID, "textures/entity/stand/ashes_to_ashes.png"), 0);
    }

    @Override
    public void render(ThelaHunGinjeetStandEntity entity, float entityYaw, float partialTicks, MatrixStack matrixStack,
            IRenderTypeBuffer buffer, int packedLight) {
    }
}
