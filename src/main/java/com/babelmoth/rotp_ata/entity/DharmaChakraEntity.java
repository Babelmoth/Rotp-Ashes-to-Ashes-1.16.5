package com.babelmoth.rotp_ata.entity;

import com.babelmoth.rotp_ata.adaptation.AdaptationManager;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.entity.stand.StandEntityType;

import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;

public class DharmaChakraEntity extends StandEntity {

    public DharmaChakraEntity(StandEntityType<DharmaChakraEntity> type, World world) {
        super(type, world);
        this.doOffsetLerp = false;
    }

    @Override
    public float getLeapStrength() {
        LivingEntity user = getUser();
        if (user == null) {
            return 0.0F;
        }

        int tier = AdaptationManager.getBodyReinforcementTier(user);
        if (tier <= 0) {
            return 0.0F;
        }

        return 2.8F + (tier - 1) * 0.55F;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }
}
