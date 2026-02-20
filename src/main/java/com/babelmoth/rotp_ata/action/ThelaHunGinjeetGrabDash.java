package com.babelmoth.rotp_ata.action;

import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.babelmoth.rotp_ata.capability.SpearStuckProvider;
import com.babelmoth.rotp_ata.entity.ThelaHunGinjeetSpearEntity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * Grab Dash (shift variant): Dash the player towards the nearest spear-stuck entity
 * or spear stuck in a block. Blocked by solid blocks in the path. Resolve level 2 to unlock.
 */
public class ThelaHunGinjeetGrabDash extends StandAction {
    private static final double DASH_RANGE = 50.0;
    private static final double DASH_SPEED = 3.0;
    private static final float STAMINA_COST = 100.0F;

    public ThelaHunGinjeetGrabDash(AbstractBuilder<?> builder) {
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
        if (!user.level.isClientSide && findNearestDashTarget(user) == null) {
            return ActionConditionResult.NEGATIVE;
        }
        return ActionConditionResult.POSITIVE;
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (world.isClientSide) return;
        if (!(user instanceof PlayerEntity)) return;
        power.consumeStamina(STAMINA_COST);

        Entity dashTarget = findNearestDashTarget(user);
        if (dashTarget == null) return;

        Vector3d userPos = user.position().add(0, user.getBbHeight() * 0.5, 0);
        Vector3d targetPos = dashTarget.position().add(0, dashTarget.getBbHeight() * 0.5, 0);
        Vector3d direction = targetPos.subtract(userPos).normalize();
        double dist = userPos.distanceTo(targetPos);

        // Check for block obstruction - find max safe distance
        double safeDist = dist;
        for (double d = 1.0; d < dist; d += 1.0) {
            Vector3d check = userPos.add(direction.scale(d));
            BlockPos pos = new BlockPos(check);
            if (world.getBlockState(pos).getMaterial().isSolid()) {
                safeDist = d - 1.0;
                break;
            }
        }

        if (safeDist < 1.0) return;

        // Dash towards target (stop 1.5 blocks before)
        double dashDist = Math.max(0, Math.min(safeDist, dist - 1.5));
        double speed = Math.min(DASH_SPEED, dashDist * 0.5);
        user.setDeltaMovement(direction.scale(speed));
        user.hurtMarked = true;

        user.playSound(SoundEvents.CHAIN_PLACE, 1.0F, 1.2F);
    }

    /**
     * Find the nearest dash target: either a LivingEntity with stuck spears,
     * or a ThelaHunGinjeetSpearEntity stuck in a block (owned by the user).
     */
    private Entity findNearestDashTarget(LivingEntity user) {
        AxisAlignedBB searchBox = user.getBoundingBox().inflate(DASH_RANGE);
        Entity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        // Search for living entities with stuck spears
        for (Entity entity : user.level.getEntities(user, searchBox, e -> e instanceof LivingEntity && e.isAlive())) {
            LivingEntity living = (LivingEntity) entity;
            boolean[] hasStuck = {false};
            living.getCapability(SpearStuckProvider.SPEAR_STUCK_CAPABILITY).ifPresent(cap -> {
                if (cap.getSpearCount() > 0) {
                    hasStuck[0] = true;
                }
            });
            if (hasStuck[0]) {
                double dist = user.distanceToSqr(entity);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = entity;
                }
            }
        }

        // Search for spear entities stuck in blocks (inGround, owned by user)
        for (ThelaHunGinjeetSpearEntity spear : user.level.getEntitiesOfClass(
                ThelaHunGinjeetSpearEntity.class, searchBox,
                e -> e.isAlive() && !e.isRecalled() && !e.isBurstMode() && e.isInvisible() == false && user.equals(e.getOwner()))) {
            double dist = user.distanceToSqr(spear);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = spear;
            }
        }

        return nearest;
    }
}
