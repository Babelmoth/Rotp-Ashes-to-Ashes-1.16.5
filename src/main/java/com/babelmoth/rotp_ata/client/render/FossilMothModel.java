package com.babelmoth.rotp_ata.client.render;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.entity.FossilMothEntity;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;

/**
 * FossilMothModel - GeckoLib 化石蛾模型
 * 
 * 加载 ashes_to_ashes.geo.json 和 ashes_to_ashes.animation.json
 */
public class FossilMothModel extends AnimatedGeoModel<FossilMothEntity> {
    
    @Override
    public ResourceLocation getModelLocation(FossilMothEntity entity) {
        return new ResourceLocation(AddonMain.MOD_ID, "geo/ashes_to_ashes.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(FossilMothEntity entity) {
        return new ResourceLocation(AddonMain.MOD_ID, "textures/entity/ashes_to_ashes.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(FossilMothEntity entity) {
        return new ResourceLocation(AddonMain.MOD_ID, "animations/ashes_to_ashes.animation.json");
    }
}
