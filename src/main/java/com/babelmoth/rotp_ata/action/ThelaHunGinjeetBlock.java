package com.babelmoth.rotp_ata.action;

import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.entity.stand.StandStatFormulas;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.github.standobyte.jojo.power.impl.stand.IStandManifestation;
import com.babelmoth.rotp_ata.init.InitItems;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.util.SoundEvents;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Block: Hold to raise spear and reduce incoming damage.
 * Damage reduction based on stand durability and power (like RotP's stand block).
 * Similar to RotP's stand block mechanic.
 */
public class ThelaHunGinjeetBlock extends StandAction {
    // Track which entities are currently blocking
    private static final Set<LivingEntity> BLOCKING_ENTITIES = Collections.newSetFromMap(new WeakHashMap<>());
    private static final UUID BLOCK_SLOW_UUID = UUID.fromString("e1f2a3b4-c5d6-7890-abcd-ef1234567894");

    public ThelaHunGinjeetBlock(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public TargetRequirement getTargetRequirement() {
        return TargetRequirement.NONE;
    }

    @Override
    public int getHoldDurationMax(com.github.standobyte.jojo.power.impl.stand.IStandPower power) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean holdOnly(com.github.standobyte.jojo.power.impl.stand.IStandPower power) {
        return true;
    }

    @Override
    public ActionConditionResult checkSpecificConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        if (user.getMainHandItem().getItem() != InitItems.THELA_HUN_GINJEET_SPEAR.get()) {
            return ActionConditionResult.NEGATIVE;
        }
        return ActionConditionResult.POSITIVE;
    }

    @Override
    public void startedHolding(World world, LivingEntity user, IStandPower power, ActionTarget target, boolean requirementsFulfilled) {
        if (!world.isClientSide && requirementsFulfilled) {
            BLOCKING_ENTITIES.add(user);
            applySlowModifier(user);
            user.playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 1.2F);
        }
    }

    @Override
    protected void holdTick(World world, LivingEntity user, IStandPower power, int ticksHeld, ActionTarget target, boolean requirementsFulfilled) {
        if (!world.isClientSide && requirementsFulfilled) {
            BLOCKING_ENTITIES.add(user);
        }
    }

    @Override
    public void stoppedHolding(World world, LivingEntity user, IStandPower power, int ticksHeld, boolean willFire) {
        if (!world.isClientSide) {
            BLOCKING_ENTITIES.remove(user);
            removeSlowModifier(user);
        }
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        // holdOnly mode: logic is in hold callbacks
    }

    private static void applySlowModifier(LivingEntity user) {
        ModifiableAttributeInstance speed = user.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(BLOCK_SLOW_UUID) == null) {
            speed.addTransientModifier(new AttributeModifier(BLOCK_SLOW_UUID, "Spear block slow", -0.6, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void removeSlowModifier(LivingEntity user) {
        ModifiableAttributeInstance speed = user.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(BLOCK_SLOW_UUID);
    }

    /**
     * Check if an entity is currently blocking with the spear.
     */
    public static boolean isBlocking(LivingEntity entity) {
        return BLOCKING_ENTITIES.contains(entity);
    }

    /**
     * Calculate damage reduction ratio based on stand durability and power.
     * Uses RotP's StandStatFormulas.getPhysicalResistance algorithm.
     */
    public static float getBlockReduction(IStandPower power, float damageAmount) {
        IStandManifestation manifestation = power.getStandManifestation();
        double durability;
        double strength;
        if (manifestation instanceof StandEntity) {
            StandEntity stand = (StandEntity) manifestation;
            durability = stand.getDurability();
            strength = stand.getAttackDamage();
        } else {
            // Fallback: use base stats
            durability = 16.0;
            strength = 12.0;
        }
        return StandStatFormulas.getPhysicalResistance(durability, strength, 1.0F, damageAmount);
    }
}
