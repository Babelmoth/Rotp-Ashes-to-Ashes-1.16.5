package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.capability.SpearStuckProvider;
import com.babelmoth.rotp_ata.capability.SpearThornProvider;
import com.babelmoth.rotp_ata.entity.ThelaHunGinjeetSpearEntity;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.power.impl.stand.IStandManifestation;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.List;
import java.util.Random;

/**
 * Thorn Burst: Target an entity with thorn stacks, detonate all thorns.
 * Launches spears outward based on thorn count (max 30), deals damage based on stacks,
 * spawns blood particles, and auto-recalls all spears back to the owner.
 * Resolve level 3 to unlock.
 */
public class ThelaHunGinjeetThornBurst extends StandAction {
    private static final float BASE_DAMAGE = 8.0F;
    private static final float STAMINA_COST = 200.0F;
    private static final int MAX_BURST_SPEARS = 30;
    private static final double SEARCH_RADIUS = 30.0;
    private static final double MAX_RANGE = 30.0;

    public ThelaHunGinjeetThornBurst(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public TargetRequirement getTargetRequirement() {
        return TargetRequirement.NONE;
    }

    @Override
    public ActionConditionResult checkSpecificConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        if (power.getStamina() < STAMINA_COST) {
            return ActionConditionResult.NEGATIVE;
        }
        // Check if there are any entities with thorns/spears in range
        List<LivingEntity> targets = findBurstTargets(user);
        if (targets.isEmpty()) {
            return ActionConditionResult.NEGATIVE;
        }
        return ActionConditionResult.POSITIVE;
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (world.isClientSide) return;
        if (!(user instanceof PlayerEntity)) return;
        PlayerEntity player = (PlayerEntity) user;

        List<LivingEntity> targets = findBurstTargets(user);
        if (targets.isEmpty()) return;

        power.consumeStamina(STAMINA_COST);
        Random rand = new Random();

        for (LivingEntity living : targets) {
            int thornCount = living.getCapability(SpearThornProvider.SPEAR_THORN_CAPABILITY)
                    .map(cap -> cap.getThornCount()).orElse(0);
            int spearCount = living.getCapability(SpearStuckProvider.SPEAR_STUCK_CAPABILITY)
                    .map(cap -> cap.getSpearCount()).orElse(0);
            int totalStacks = Math.max(thornCount, spearCount);
            if (totalStacks <= 0) continue;

            // Deal burst damage based on stacks (always deal damage)
            float damage = getScaledDamage(power) * (1.0F + totalStacks * 0.2F);
            DamageSource source = new DamageSource("stand.thorn_burst") {
                @Override
                public Entity getEntity() {
                    return user;
                }
            };
            living.hurt(source, damage);

            // Blood particles
            if (world instanceof ServerWorld) {
                ServerWorld serverWorld = (ServerWorld) world;
                Vector3d pos = living.position().add(0, living.getBbHeight() * 0.5, 0);
                serverWorld.sendParticles(ParticleTypes.DAMAGE_INDICATOR, pos.x, pos.y, pos.z,
                        totalStacks * 3, 0.5, 0.5, 0.5, 0.1);
            }

            boolean shouldRecall = false;

            if (thornCount > 0) {
                // Launch burst spears based on thorn count (max 30)
                int burstCount = Math.min(thornCount, MAX_BURST_SPEARS);
                Vector3d burstCenter = living.position().add(0, living.getBbHeight() * 0.5, 0);
                double spawnOffset = Math.max(living.getBbWidth(), living.getBbHeight()) * 0.5 + 0.5;
                for (int i = 0; i < burstCount; i++) {
                    double angle = rand.nextDouble() * 360.0;
                    double rad = Math.toRadians(angle);
                    float pitchAngle = -10.0F + rand.nextFloat() * 50.0F;
                    double speed = 1.5 + rand.nextDouble() * 1.0;

                    double dirX = Math.cos(rad) * Math.cos(Math.toRadians(pitchAngle));
                    double dirY = Math.sin(Math.toRadians(pitchAngle));
                    double dirZ = Math.sin(rad) * Math.cos(Math.toRadians(pitchAngle));

                    double spawnX = burstCenter.x + dirX * spawnOffset;
                    double spawnY = burstCenter.y + dirY * spawnOffset;
                    double spawnZ = burstCenter.z + dirZ * spawnOffset;

                    ThelaHunGinjeetSpearEntity burstSpear = new ThelaHunGinjeetSpearEntity(world, spawnX, spawnY, spawnZ);
                    // No owner - burst spears are independent and cannot be recalled
                    burstSpear.setBurstMode(true);
                    burstSpear.pickup = net.minecraft.entity.projectile.AbstractArrowEntity.PickupStatus.DISALLOWED;
                    burstSpear.shootFromRotation(user, -pitchAngle, (float) angle, 0.0F, (float) speed, 5.0F);
                    world.addFreshEntity(burstSpear);
                }

                // Reduce thorn stacks by burst count; recall only if thorns fully consumed
                final int spearsFired = burstCount;
                int remaining = thornCount - spearsFired;
                living.getCapability(SpearThornProvider.SPEAR_THORN_CAPABILITY).ifPresent(cap -> {
                    if (thornCount - spearsFired <= 0) {
                        cap.reset();
                    } else {
                        cap.setThornCount(thornCount - spearsFired);
                    }
                });
                shouldRecall = (remaining <= 0);
            } else {
                // Thorns == 0, only stuck spears: recall without bursting
                shouldRecall = true;
            }

            // Recall spears only when thorns are fully consumed or there were no thorns
            if (shouldRecall) {
                AxisAlignedBB targetBox = living.getBoundingBox().inflate(5.0);
                for (ThelaHunGinjeetSpearEntity spear : world.getEntitiesOfClass(ThelaHunGinjeetSpearEntity.class, targetBox,
                        e -> e.isAlive() && !e.isRecalled() && !e.isBurstMode() && player.equals(e.getOwner()))) {
                    spear.setRecalled(true);
                }
            }
        }

        user.swing(Hand.MAIN_HAND, true);
        user.playSound(SoundEvents.GENERIC_EXPLODE, 1.0F, 1.2F);
        user.playSound(SoundEvents.TRIDENT_HIT, 1.5F, 0.8F);
    }

    /**
     * Find all living entities within SEARCH_RADIUS that have thorn stacks or stuck spears.
     */
    private static List<LivingEntity> findBurstTargets(LivingEntity user) {
        AxisAlignedBB searchBox = user.getBoundingBox().inflate(SEARCH_RADIUS);
        List<LivingEntity> result = new java.util.ArrayList<>();
        for (Entity e : user.level.getEntities(user, searchBox, ent -> ent instanceof LivingEntity && ent.isAlive())) {
            LivingEntity living = (LivingEntity) e;
            boolean hasThorns = living.getCapability(SpearThornProvider.SPEAR_THORN_CAPABILITY)
                    .map(cap -> cap.getThornCount() > 0).orElse(false);
            boolean hasSpears = living.getCapability(SpearStuckProvider.SPEAR_STUCK_CAPABILITY)
                    .map(cap -> cap.getSpearCount() > 0).orElse(false);
            if (hasThorns || hasSpears) {
                result.add(living);
            }
        }
        return result;
    }

    private static float getScaledDamage(IStandPower power) {
        IStandManifestation manifestation = power.getStandManifestation();
        if (manifestation instanceof StandEntity) {
            return (float) (BASE_DAMAGE * ((StandEntity) manifestation).getAttackDamage() / 8.0);
        }
        return BASE_DAMAGE;
    }
}
