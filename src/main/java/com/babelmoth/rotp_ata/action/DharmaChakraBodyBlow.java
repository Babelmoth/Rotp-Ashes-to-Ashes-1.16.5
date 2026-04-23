package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.adaptation.AdaptationManager;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.player.ContinuousActionInstance;
import com.github.standobyte.jojo.action.player.IPlayerAction;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.capability.entity.PlayerUtilCap;
import com.github.standobyte.jojo.util.mc.damage.KnockbackCollisionImpact;
import com.github.standobyte.jojo.util.mod.JojoModUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.List;

public class DharmaChakraBodyBlow extends StandAction implements IPlayerAction<DharmaChakraBodyBlow.Instance, com.github.standobyte.jojo.power.impl.stand.IStandPower> {
    private static final float MAX_TRAINING_POINTS = 450.0F;
    private static final float TRAINING_POINTS_PER_HIT = 2.0F;
    private static final double FRONT_SEARCH_RANGE = 4.5D;
    private static final float BASE_DAMAGE = 4.0F;
    private static final float STAMINA_COST = 45.0F;

    private ActionTarget storedTarget = ActionTarget.EMPTY;

    public DharmaChakraBodyBlow(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public TargetRequirement getTargetRequirement() {
        return TargetRequirement.NONE;
    }

    @Override
    public float getStaminaCost(com.github.standobyte.jojo.power.impl.stand.IStandPower power) {
        return STAMINA_COST;
    }

    @Override
    protected ActionConditionResult checkSpecificConditions(LivingEntity user, com.github.standobyte.jojo.power.impl.stand.IStandPower power, ActionTarget target) {
        int tier = AdaptationManager.getBodyReinforcementTier(user);
        if (tier <= 0) {
            return ActionConditionResult.NEGATIVE;
        }

        if (power.getStamina() < getStaminaCost(power)) {
            return ActionConditionResult.NEGATIVE;
        }

        return ActionConditionResult.POSITIVE;
    }

    @Override
    protected void perform(World world, LivingEntity user, com.github.standobyte.jojo.power.impl.stand.IStandPower power, ActionTarget target) {
        if (!world.isClientSide) {
            storedTarget = target;
            setPlayerAction(user, power);
        }
    }

    @Override
    public Instance createContinuousActionInstance(LivingEntity user, PlayerUtilCap userCap, com.github.standobyte.jojo.power.impl.stand.IStandPower power) {
        ActionTarget targetToUse = storedTarget;
        storedTarget = ActionTarget.EMPTY;
        return new Instance(user, userCap, power, this, targetToUse);
    }

    void performStrike(World world, LivingEntity user, com.github.standobyte.jojo.power.impl.stand.IStandPower power, ActionTarget target) {
        int tier = AdaptationManager.getBodyReinforcementTier(user);
        if (tier <= 0) {
            return;
        }

        float damage = BASE_DAMAGE + AdaptationManager.getBodyDamageBonus(user) + (tier - 1) * 1.5F;
        boolean hitSuccessful = false;

        if (target.getType() == ActionTarget.TargetType.ENTITY && target.getEntity() instanceof LivingEntity) {
            hitSuccessful = punchPerformEntity(world, user, (LivingEntity) target.getEntity(), damage, tier);
        } else if (target.getType() == ActionTarget.TargetType.BLOCK) {
            hitSuccessful = punchPerformBlock(world, user, target, damage);
        }

        if (!hitSuccessful && target.getType() != ActionTarget.TargetType.BLOCK) {
            LivingEntity fallback = resolveFallbackTarget(world, user);
            if (fallback != null) {
                hitSuccessful = punchPerformEntity(world, user, fallback, damage, tier);
            }
        }

        if (!hitSuccessful) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundCategory.PLAYERS, 1.0F, 1.0F);
            return;
        }

        if (power.getLearningProgressPoints(this) < MAX_TRAINING_POINTS) {
            power.addLearningProgressPoints(this, TRAINING_POINTS_PER_HIT);
        }
    }

    private boolean punchPerformEntity(World world, LivingEntity user, LivingEntity targetEntity, float damage, int tier) {
        DamageSource source = user instanceof PlayerEntity ? DamageSource.playerAttack((PlayerEntity) user) : DamageSource.mobAttack(user);
        if (!targetEntity.hurt(source, damage)) {
            return false;
        }

        float knockback = 1.8F + (tier - 1) * 0.25F;
        targetEntity.knockback(knockback, user.getX() - targetEntity.getX(), user.getZ() - targetEntity.getZ());
        Vector3d movement = targetEntity.getDeltaMovement();
        double minLift = 0.18D + tier * 0.04D;
        if (movement.y < minLift) {
            targetEntity.setDeltaMovement(movement.x, minLift, movement.z);
        }
        targetEntity.hurtMarked = true;
        KnockbackCollisionImpact.getHandler(targetEntity)
                .ifPresent(cap -> cap.onPunchSetKnockbackImpact(targetEntity.getDeltaMovement(), user));

        world.playSound(null, targetEntity.getX(), targetEntity.getEyeY(), targetEntity.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.1F, 0.85F);
        return true;
    }

    private boolean punchPerformBlock(World world, LivingEntity user, ActionTarget target, float damage) {
        if (target.getType() != ActionTarget.TargetType.BLOCK || !JojoModUtil.breakingBlocksEnabled(world)) {
            return false;
        }

        destroyBlock(world, user, target.getBlockPos(), damage);
        if (damage >= 12.0F) {
            destroyBlock(world, user, target.getBlockPos().offset(1, 0, 0), damage);
            destroyBlock(world, user, target.getBlockPos().offset(-1, 0, 0), damage);
            destroyBlock(world, user, target.getBlockPos().offset(0, 1, 0), damage);
            destroyBlock(world, user, target.getBlockPos().offset(0, 0, 1), damage);
            destroyBlock(world, user, target.getBlockPos().offset(0, 0, -1), damage);
        }
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ANVIL_LAND, SoundCategory.PLAYERS, 1.0F, 1.1F);
        return true;
    }

    private void destroyBlock(World world, LivingEntity user, BlockPos pos, float damage) {
        if (world.isClientSide || !JojoModUtil.canEntityDestroy((ServerWorld) world, pos, world.getBlockState(pos), user)) {
            return;
        }

        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return;
        }

        float hardness = state.getDestroySpeed(world, pos);
        if (hardness >= 0 && hardness <= 2.5F * (float) Math.sqrt(damage)) {
            com.github.standobyte.jojo.util.mc.MCUtil.destroyBlock(world, pos, true, user);
        } else {
            SoundType soundType = state.getSoundType(world, pos, user);
            world.playSound(null, pos, soundType.getHitSound(), SoundCategory.BLOCKS, (soundType.getVolume() + 1.0F) / 8.0F, soundType.getPitch() * 0.5F);
        }
    }

    private LivingEntity resolveFallbackTarget(World world, LivingEntity user) {
        List<LivingEntity> nearby = com.github.standobyte.jojo.util.mc.MCUtil.entitiesAround(LivingEntity.class, user, FRONT_SEARCH_RANGE, false,
                e -> e != user && com.github.standobyte.jojo.util.mc.MCUtil.canHarm(user, e));

        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;
        Vector3d lookVec = user.getLookAngle();
        Vector3d eyePos = user.getEyePosition(1.0F);

        for (LivingEntity target : nearby) {
            Vector3d toTarget = target.getBoundingBox().getCenter().subtract(eyePos).normalize();
            double dot = lookVec.dot(toTarget);
            if (dot < 0.65D) {
                continue;
            }
            double dist = user.distanceToSqr(target);
            if (dist < closestDist) {
                closestDist = dist;
                closest = target;
            }
        }
        return closest;
    }

    @Override
    public float getMaxTrainingPoints(com.github.standobyte.jojo.power.impl.stand.IStandPower power) {
        return MAX_TRAINING_POINTS;
    }

    @Override
    public void onMaxTraining(com.github.standobyte.jojo.power.impl.stand.IStandPower power) {
        power.unlockAction((StandAction) getShiftVariationIfPresent());
    }

    public static class Instance extends ContinuousActionInstance<DharmaChakraBodyBlow, com.github.standobyte.jojo.power.impl.stand.IStandPower> {
        private final ActionTarget target;

        public Instance(LivingEntity user, PlayerUtilCap userCap, com.github.standobyte.jojo.power.impl.stand.IStandPower power,
                        DharmaChakraBodyBlow action, ActionTarget target) {
            super(user, userCap, power, action);
            this.target = target != null ? target : ActionTarget.EMPTY;
        }

        @Override
        public void playerTick() {
            if (getTick() == 1) {
                user.swing(Hand.MAIN_HAND, true);
                user.level.playSound(user instanceof PlayerEntity ? (PlayerEntity) user : null, user.getX(), user.getEyeY(), user.getZ(),
                        SoundEvents.PLAYER_ATTACK_SWEEP, user.getSoundSource(), 1.0F, 1.1F);
            }
            if (getTick() == 4 && !user.level.isClientSide()) {
                action.performStrike(user.level, user, playerPower, target);
            }
            if (getTick() >= 8) {
                stopAction();
            }
        }

        @Override
        public boolean updateTarget() {
            return true;
        }

        @Override
        public float getWalkSpeed() {
            return 0.5F;
        }
    }
}
