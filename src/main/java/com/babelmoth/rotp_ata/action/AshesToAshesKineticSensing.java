package com.babelmoth.rotp_ata.action;

import javax.annotation.Nullable;

import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.babelmoth.rotp_ata.AddonMain;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.github.standobyte.jojo.util.general.LazySupplier;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import java.util.List;

public class AshesToAshesKineticSensing extends StandAction {

    // Store sensing state per-user (for UI, not actual moth state)
    private static boolean sensingEnabled = false;
    
    // Lazy texture for "on" state
    private final LazySupplier<ResourceLocation> onTexture = 
            new LazySupplier<>(() -> new ResourceLocation(AddonMain.MOD_ID, "textures/action/kinetic_sensing_on.png"));
    private final LazySupplier<ResourceLocation> offTexture = 
            new LazySupplier<>(() -> new ResourceLocation(AddonMain.MOD_ID, "textures/action/kinetic_sensing_off.png"));

    public AshesToAshesKineticSensing(AbstractBuilder<?> builder) {
        super(builder);
    }
    
    @Override
    public ResourceLocation getIconTexturePath(@Nullable IStandPower power) {
        return sensingEnabled ? onTexture.get() : offTexture.get();
    }
    
    @Override
    public boolean greenSelection(IStandPower power, ActionConditionResult conditionCheck) {
        return sensingEnabled;
    }
    
    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (!world.isClientSide) {
            // Toggle state
            sensingEnabled = !sensingEnabled;
            
            // Apply to all owned moths efficiently (only nearby ones)
            for (FossilMothEntity moth : world.getEntitiesOfClass(FossilMothEntity.class, 
                    user.getBoundingBox().inflate(128), 
                    m -> m.getOwner() == user && m.isAlive())) {
                moth.setKineticSensingEnabled(sensingEnabled);
            }
        }
    }
    
    public static boolean isSensingEnabled() {
        return sensingEnabled;
    }
}

