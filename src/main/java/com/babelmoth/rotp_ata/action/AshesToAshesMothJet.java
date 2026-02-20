package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.power.impl.stand.IStandManifestation;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;

import com.github.standobyte.jojo.init.ModStatusEffects;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;

/**
 * Moth jet: standalone skill, can be held to keep firing and consumes stamina.
 * Fired moths attach on entity hit; otherwise they despawn on block hit or after flight time (no pool decrement).
 */
public class AshesToAshesMothJet extends StandAction {

    private static final float STAMINA_COST_PER_MOTH = 35.0f;
    private static final int TICKS_PER_MOTH = 4; // One moth every 4 ticks
    private static final int TICKS_PER_MOTH_RESOLVE = 3; // Faster during Resolve
    private static final float JET_SPEED = 2.5f;
    private static final float JET_SPEED_RESOLVE = 3.5f;

    public AshesToAshesMothJet(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public TargetRequirement getTargetRequirement() {
        return TargetRequirement.NONE;
    }

    @Override
    public int getHoldDurationMax(IStandPower standPower) {
        return Integer.MAX_VALUE;
    }

    @Override
    public ActionConditionResult checkSpecificConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        IStandManifestation manifestation = power.getStandManifestation();
        if (manifestation instanceof StandEntity) {
            StandEntity stand = (StandEntity) manifestation;
            if (stand.isManuallyControlled()) {
                return ActionConditionResult.NEGATIVE;
            }
        }
        return ActionConditionResult.POSITIVE;
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        // Single click also fires one moth
        if (!world.isClientSide && power.getStamina() >= STAMINA_COST_PER_MOTH) {
            trySpawnJetMoth(world, user, power);
        }
    }

    @Override
    public void onHoldTick(World world, LivingEntity user, IStandPower power, int ticksHeld, ActionTarget target, boolean requirementsMet) {
        if (world.isClientSide || !requirementsMet) return;
        int interval = user.hasEffect(ModStatusEffects.RESOLVE.get()) ? TICKS_PER_MOTH_RESOLVE : TICKS_PER_MOTH;
        if (ticksHeld % interval != 0) return;
        if (power.getStamina() < STAMINA_COST_PER_MOTH) return;

        if (trySpawnJetMoth(world, user, power)) {
            power.consumeStamina(STAMINA_COST_PER_MOTH);
        }
    }

    private boolean trySpawnJetMoth(World world, LivingEntity user, IStandPower power) {
        Vector3d eyePos = user.getEyePosition(1.0F);
        Vector3d lookVec = user.getViewVector(1.0F);
        IStandManifestation stand = power.getStandManifestation();
        if (stand instanceof StandEntity) {
            StandEntity standEntity = (StandEntity) stand;
            if (standEntity.isManuallyControlled()) {
                eyePos = standEntity.getEyePosition(1.0F);
                lookVec = standEntity.getViewVector(1.0F);
            }
        }

        final Vector3d dir = lookVec.add(
            (world.random.nextDouble() - 0.5) * 0.15,
            (world.random.nextDouble() - 0.5) * 0.15,
            (world.random.nextDouble() - 0.5) * 0.15
        ).normalize();

        final Vector3d spawnPos = eyePos.add(dir.scale(0.5));
        final LivingEntity owner = user;

        return owner.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY)
            .map(pool -> {
                int slot = pool.allocateSlotWithPriority(true);
                if (slot == -1) return false;
                FossilMothEntity moth = new FossilMothEntity(world, owner);
                moth.setMothPoolIndex(slot);
                moth.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
                float speed = owner.hasEffect(ModStatusEffects.RESOLVE.get()) ? JET_SPEED_RESOLVE : JET_SPEED;
                moth.jetFire(dir, speed);
                world.addFreshEntity(moth);
                if (owner instanceof net.minecraft.entity.player.ServerPlayerEntity) {
                    pool.sync((net.minecraft.entity.player.ServerPlayerEntity) owner);
                }
                world.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z,
                    SoundEvents.BEE_LOOP, SoundCategory.PLAYERS, 0.25f, 1.2f + world.random.nextFloat() * 0.3f);
                return true;
            })
            .orElse(false);
    }
}
