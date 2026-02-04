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

    // Store sensing state per-user UUID
    private static final java.util.Map<java.util.UUID, Boolean> sensingStateMap = new java.util.HashMap<>();
    
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
        if (power == null || power.getUser() == null) return offTexture.get();
        boolean enabled = sensingStateMap.getOrDefault(power.getUser().getUUID(), false);
        return enabled ? onTexture.get() : offTexture.get();
    }
    
    @Override
    public boolean greenSelection(IStandPower power, ActionConditionResult conditionCheck) {
        if (power == null || power.getUser() == null) return false;
        return sensingStateMap.getOrDefault(power.getUser().getUUID(), false);
    }
    
    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (!world.isClientSide) {
            // Toggle state for this user
            java.util.UUID userId = user.getUUID();
            boolean newState = !sensingStateMap.getOrDefault(userId, false);
            sensingStateMap.put(userId, newState);
            
            // Apply to all owned moths (existing and future)
            for (FossilMothEntity moth : world.getEntitiesOfClass(FossilMothEntity.class, 
                    user.getBoundingBox().inflate(256), 
                    m -> m.getOwner() == user && m.isAlive())) {
                moth.setKineticSensingEnabled(newState);
            }
        }
    }
    
    public static boolean isSensingEnabled(LivingEntity user) {
        if (user == null) return false;
        return sensingStateMap.getOrDefault(user.getUUID(), false);
    }
}

