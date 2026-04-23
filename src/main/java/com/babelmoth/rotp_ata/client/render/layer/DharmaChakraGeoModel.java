package com.babelmoth.rotp_ata.client.render.layer;

import com.babelmoth.rotp_ata.AddonMain;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;

public class DharmaChakraGeoModel extends AnimatedGeoModel<DharmaChakraAnimatable> {

    private static final ResourceLocation MODEL = new ResourceLocation(AddonMain.MOD_ID, "geo/dharma_chakra.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(AddonMain.MOD_ID, "textures/entity/dharma_chakra.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(AddonMain.MOD_ID, "animations/empty.animation.json");

    @Override
    public ResourceLocation getModelLocation(DharmaChakraAnimatable object) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureLocation(DharmaChakraAnimatable object) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationFileLocation(DharmaChakraAnimatable object) {
        return ANIMATION;
    }
}
