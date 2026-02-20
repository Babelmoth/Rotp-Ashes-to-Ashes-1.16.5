package com.babelmoth.rotp_ata.action;

import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.babelmoth.rotp_ata.capability.SpearStuckProvider;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Grab Pull: Pull entities that have spears stuck in them towards the player.
 * Blocked by solid blocks in the path. Resolve level 2 to unlock.
 */
public class ThelaHunGinjeetGrabPull extends StandAction {
    private static final double PULL_RANGE = 50.0;
    private static final double PULL_SPEED = 2.5;
    private static final float STAMINA_COST = 100.0F;

    public ThelaHunGinjeetGrabPull(AbstractBuilder<?> builder) {
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
        // Check if there are any stuck entities in range
        if (!user.level.isClientSide && !hasStuckEntitiesInRange(user)) {
            return ActionConditionResult.NEGATIVE;
        }
        return ActionConditionResult.POSITIVE;
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (world.isClientSide) return;
        if (!(user instanceof PlayerEntity)) return;
        power.consumeStamina(STAMINA_COST);

        Vector3d userPos = user.position().add(0, user.getBbHeight() * 0.5, 0);
        List<LivingEntity> stuckEntities = getStuckEntitiesInRange(user);

        for (LivingEntity entity : stuckEntities) {
            Vector3d entityPos = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
            Vector3d direction = userPos.subtract(entityPos).normalize();
            double dist = userPos.distanceTo(entityPos);

            // Check for block obstruction
            if (isPathBlocked(world, entityPos, userPos)) continue;

            // Pull speed scales with distance
            double speed = Math.min(PULL_SPEED, dist * 0.5);
            entity.setDeltaMovement(direction.scale(speed));
            entity.hurtMarked = true;
        }

        user.playSound(SoundEvents.CHAIN_PLACE, 1.0F, 0.8F);
    }

    private boolean hasStuckEntitiesInRange(LivingEntity user) {
        return !getStuckEntitiesInRange(user).isEmpty();
    }

    private List<LivingEntity> getStuckEntitiesInRange(LivingEntity user) {
        List<LivingEntity> result = new ArrayList<>();
        AxisAlignedBB searchBox = user.getBoundingBox().inflate(PULL_RANGE);
        List<Entity> entities = user.level.getEntities(user, searchBox, e -> e instanceof LivingEntity && e.isAlive());

        for (Entity entity : entities) {
            if (entity instanceof LivingEntity) {
                LivingEntity living = (LivingEntity) entity;
                living.getCapability(SpearStuckProvider.SPEAR_STUCK_CAPABILITY).ifPresent(cap -> {
                    if (cap.getSpearCount() > 0) {
                        result.add(living);
                    }
                });
            }
        }
        return result;
    }

    /**
     * Check if path between two points is blocked by solid blocks.
     */
    private boolean isPathBlocked(World world, Vector3d from, Vector3d to) {
        Vector3d dir = to.subtract(from).normalize();
        double dist = from.distanceTo(to);
        for (double d = 1.0; d < dist; d += 1.0) {
            Vector3d check = from.add(dir.scale(d));
            BlockPos pos = new BlockPos(check);
            if (world.getBlockState(pos).getMaterial().isSolid()) {
                return true;
            }
        }
        return false;
    }
}
