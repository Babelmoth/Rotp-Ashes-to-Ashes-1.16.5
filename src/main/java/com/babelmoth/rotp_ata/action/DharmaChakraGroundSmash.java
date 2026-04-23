package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.adaptation.AdaptationManager;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.util.mc.damage.KnockbackCollisionImpact;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.FallingBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particles.BlockParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.List;

public class DharmaChakraGroundSmash extends StandAction {
    private static final int UNLOCK_TIER = 3;

    public DharmaChakraGroundSmash(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public TargetRequirement getTargetRequirement() {
        return TargetRequirement.NONE;
    }

    @Override
    protected ActionConditionResult checkSpecificConditions(LivingEntity user, com.github.standobyte.jojo.power.impl.stand.IStandPower power, ActionTarget target) {
        int tier = AdaptationManager.getBodyReinforcementTier(user);
        if (tier < UNLOCK_TIER) {
            return ActionConditionResult.NEGATIVE;
        }
        if (power.getStamina() < getStaminaCost(tier)) {
            return ActionConditionResult.NEGATIVE;
        }
        return ActionConditionResult.POSITIVE;
    }

    @Override
    protected void perform(World world, LivingEntity user, com.github.standobyte.jojo.power.impl.stand.IStandPower power, ActionTarget target) {
        if (world.isClientSide) {
            return;
        }

        if (!(world instanceof ServerWorld)) {
            return;
        }
        ServerWorld serverWorld = (ServerWorld) world;

        int tier = AdaptationManager.getBodyReinforcementTier(user);
        if (tier < UNLOCK_TIER) {
            return;
        }

        if (power.getStamina() < getStaminaCost(tier)) {
            return;
        }
        power.consumeStamina(getStaminaCost(tier));
        user.swing(Hand.MAIN_HAND, true);

        BlockPos centerPos = target.getType() == ActionTarget.TargetType.BLOCK
                ? target.getBlockPos()
                : user.blockPosition().below();
        Vector3d center = Vector3d.atCenterOf(centerPos);

        double radius = 4.0D + (tier - UNLOCK_TIER) * 0.75D;
        float damage = 3.0F + tier * 1.4F + AdaptationManager.getBodyDamageBonus(user) * 0.5F;

        launchNearbyBlocks(serverWorld, centerPos, radius, tier);

        AxisAlignedBB area = new AxisAlignedBB(centerPos).inflate(radius, 2.5D, radius);
        List<Entity> entities = world.getEntities(user, area, e -> e instanceof LivingEntity && e.isAlive());

        for (Entity entity : entities) {
            LivingEntity living = (LivingEntity) entity;
            DamageSource source = user instanceof PlayerEntity ? DamageSource.playerAttack((PlayerEntity) user) : DamageSource.mobAttack(user);
            if (living.hurt(source, damage)) {
                Vector3d dir = living.position().subtract(center);
                double len = Math.max(0.0001D, Math.sqrt(dir.x * dir.x + dir.z * dir.z));
                Vector3d norm = new Vector3d(dir.x / len, 0, dir.z / len);
                double push = 0.45D + 0.08D * tier;
                double lift = 0.45D + 0.07D * tier;
                living.setDeltaMovement(living.getDeltaMovement().add(norm.x * push, lift, norm.z * push));
                living.hurtMarked = true;
                KnockbackCollisionImpact.getHandler(living)
                        .ifPresent(cap -> cap.onPunchSetKnockbackImpact(living.getDeltaMovement(), user));
            }
        }

        world.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.9F, 1.0F);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ANVIL_LAND, SoundCategory.PLAYERS, 1.0F, 0.7F);
    }

    private static float getStaminaCost(int tier) {
        return 95.0F + (tier - UNLOCK_TIER) * 15.0F;
    }

    private static void launchNearbyBlocks(ServerWorld world, BlockPos center, double radius, int tier) {
        int r = MathHelper.ceil(radius);
        double chance = Math.min(0.65D, 0.20D + tier * 0.08D);

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > radius * radius) {
                    continue;
                }
                if (x == 0 && z == 0) {
                    continue;
                }
                if (world.random.nextDouble() > chance) {
                    continue;
                }

                BlockPos pos = center.offset(x, 0, z);
                BlockState state = world.getBlockState(pos);
                if (state.isAir(world, pos) || state.getMaterial().isLiquid()) {
                    continue;
                }

                float hardness = state.getDestroySpeed(world, pos);
                if (hardness < 0 || hardness > Math.min(2.5F, 0.8F + 0.3F * tier)) {
                    continue;
                }

                FallingBlockEntity falling = new FallingBlockEntity(world,
                        pos.getX() + 0.5D,
                        pos.getY(),
                        pos.getZ() + 0.5D,
                        state);
                double motionY = 0.14D + Math.abs(x) * 0.015D + Math.abs(z) * 0.015D + tier * 0.01D;
                falling.setDeltaMovement(0, motionY, 0);
                falling.time = 1;
                falling.dropItem = false;

                world.addFreshEntity(falling);
                world.removeBlock(pos, false);

                world.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        pos.getX() + 0.5D, pos.getY() + 1.15D, pos.getZ() + 0.5D,
                        2, 0.1D, 0.1D, 0.1D, 0.03D);
                world.sendParticles(new BlockParticleData(ParticleTypes.BLOCK, state),
                        pos.getX() + 0.5D, pos.getY() + 1.15D, pos.getZ() + 0.5D,
                        4, 0.2D, 0.2D, 0.2D, 0.1D);
            }
        }
    }
}
