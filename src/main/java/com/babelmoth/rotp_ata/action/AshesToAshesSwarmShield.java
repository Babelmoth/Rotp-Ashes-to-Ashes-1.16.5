package com.babelmoth.rotp_ata.action;

import javax.annotation.Nullable;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;
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

/**
 * Toggle-style swarm shield action. Auto-summons stand if needed, auto-disables on recall.
 */
public class AshesToAshesSwarmShield extends StandAction {

    private static final Map<UUID, Boolean> shieldStateMap = new ConcurrentHashMap<>();

    private final LazySupplier<ResourceLocation> onTexture =
            new LazySupplier<>(() -> new ResourceLocation(AddonMain.MOD_ID, "textures/action/ashes_to_ashes_swarm_shield_on.png"));
    private final LazySupplier<ResourceLocation> offTexture =
            new LazySupplier<>(() -> new ResourceLocation(AddonMain.MOD_ID, "textures/action/ashes_to_ashes_swarm_shield_off.png"));

    public AshesToAshesSwarmShield(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public String getTranslationKey(IStandPower power, ActionTarget target) {
        return "action.rotp_ata.ashes_to_ashes_swarm_shield_self";
    }

    @Override
    public ResourceLocation getIconTexturePath(@Nullable IStandPower power) {
        if (power != null && power.getUser() != null) {
            return isShieldEnabled(power.getUser()) ? onTexture.get() : offTexture.get();
        }
        return offTexture.get();
    }

    @Override
    public boolean greenSelection(IStandPower power, ActionConditionResult conditionCheck) {
        if (power != null && power.getUser() != null) {
            return isShieldEnabled(power.getUser());
        }
        return false;
    }

    @Override
    public ActionConditionResult checkConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        return ActionConditionResult.POSITIVE;
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (world.isClientSide) return;

        if (!power.isActive() && power.getType() instanceof com.github.standobyte.jojo.power.impl.stand.type.EntityStandType) {
            ((com.github.standobyte.jojo.power.impl.stand.type.EntityStandType<?>) power.getType())
                    .summon(user, power, entity -> {}, true, true);
        }

        com.github.standobyte.jojo.power.impl.stand.IStandManifestation manifestation = power.getStandManifestation();
        if (manifestation instanceof com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity) {
            com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity stand = (com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity) manifestation;
            boolean newState = !shieldStateMap.getOrDefault(user.getUUID(), false);
            shieldStateMap.put(user.getUUID(), newState);
            stand.setShieldActive(newState);
            if (!newState) {
                clearOwnerMothsShieldTarget(world, user);
            }
        }
    }

    private static void clearOwnerMothsShieldTarget(World world, LivingEntity owner) {
        List<FossilMothEntity> moths = MothQueryUtil.getShieldMoths(owner, AshesToAshesConstants.QUERY_RADIUS_GUARDIAN);
        for (FossilMothEntity moth : moths) {
            moth.setIsShieldMoth(false);
            moth.setShieldTarget(null);
        }
    }

    public static boolean isShieldEnabled(LivingEntity user) {
        if (user == null) return false;
        return shieldStateMap.getOrDefault(user.getUUID(), false);
    }

    public static void clearShieldState(UUID userId) {
        shieldStateMap.remove(userId);
    }

    /** Turn off shield and clear shield targets. */
    public static void turnOffShieldForUser(World world, LivingEntity user) {
        if (user != null) {
            shieldStateMap.remove(user.getUUID());
            clearOwnerMothsShieldTargetForUser(world, user);
        }
    }

    public static void clearOwnerMothsShieldTargetForUser(World world, LivingEntity owner) {
        if (world == null || owner == null) return;
        List<FossilMothEntity> moths = MothQueryUtil.getShieldMoths(owner, AshesToAshesConstants.QUERY_RADIUS_GUARDIAN);
        for (FossilMothEntity moth : moths) {
            moth.setIsShieldMoth(false);
            moth.setShieldTarget(null);
        }
    }
}
