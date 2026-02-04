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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AshesToAshesKineticSensing extends StandAction {

    // Store sensing state per-user by UUID
    private static final Map<UUID, Boolean> sensingStateMap = new ConcurrentHashMap<>();
    
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
        if (power != null && power.getUser() != null) {
            return isSensingEnabled(power.getUser()) ? onTexture.get() : offTexture.get();
        }
        return offTexture.get();
    }
    
    @Override
    public boolean greenSelection(IStandPower power, ActionConditionResult conditionCheck) {
        if (power != null && power.getUser() != null) {
            return isSensingEnabled(power.getUser());
        }
        return false;
    }
    
    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (!world.isClientSide) {
            UUID userId = user.getUUID();
            // Toggle state for this specific user
            boolean newState = !sensingStateMap.getOrDefault(userId, false);
            sensingStateMap.put(userId, newState);
            
            // Apply to all owned moths in range
            for (FossilMothEntity moth : world.getEntitiesOfClass(FossilMothEntity.class, 
                    user.getBoundingBox().inflate(256), 
                    m -> m.getOwner() == user && m.isAlive())) {
                moth.setKineticSensingEnabled(newState);
            }
        }
    }
    
    /**
     * Check if kinetic sensing is enabled for a specific user.
     * Used by FossilMothEntity to sync state for newly spawned moths.
     */
    public static boolean isSensingEnabled(LivingEntity user) {
        if (user == null) return false;
        return sensingStateMap.getOrDefault(user.getUUID(), false);
    }
    
    /**
     * Clear sensing state for a user (e.g., when they log out).
     */
    public static void clearSensingState(UUID userId) {
        sensingStateMap.remove(userId);
    }
}

