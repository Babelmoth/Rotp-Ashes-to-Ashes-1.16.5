package com.babelmoth.rotp_ata.action;

import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.power.impl.stand.IStandManifestation;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.babelmoth.rotp_ata.init.InitItems;

import com.babelmoth.rotp_ata.util.SpearEnchantHelper;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.List;

/**
 * Thorn Strike: Target a block to create extending ground spikes around it,
 * damaging nearby enemies. Requires holding spear. Max resolve to unlock.
 */
public class ThelaHunGinjeetThornStrike extends StandAction {
    private static final float BASE_DAMAGE = 8.0F;
    private static final double SPIKE_RANGE = 6.0;
    private static final float STAMINA_COST = 300.0F;

    public ThelaHunGinjeetThornStrike(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public TargetRequirement getTargetRequirement() {
        return TargetRequirement.BLOCK;
    }

    @Override
    public ActionConditionResult checkSpecificConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        if (user.getMainHandItem().getItem() != InitItems.THELA_HUN_GINJEET_SPEAR.get()) {
            return ActionConditionResult.NEGATIVE;
        }
        if (power.getStamina() < STAMINA_COST) {
            return ActionConditionResult.NEGATIVE;
        }
        return ActionConditionResult.POSITIVE;
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (world.isClientSide) return;
        if (!(user instanceof PlayerEntity)) return;
        power.consumeStamina(STAMINA_COST);

        BlockPos blockPos = target.getBlockPos();
        if (blockPos == null) return;

        Vector3d center = Vector3d.atCenterOf(blockPos);
        float damage = getScaledDamage(power);

        // Damage all living entities in range
        AxisAlignedBB aoe = new AxisAlignedBB(
                center.x - SPIKE_RANGE, center.y - 1, center.z - SPIKE_RANGE,
                center.x + SPIKE_RANGE, center.y + 3, center.z + SPIKE_RANGE);
        List<Entity> entities = world.getEntities(user, aoe, e -> e instanceof LivingEntity && e.isAlive());

        for (Entity entity : entities) {
            DamageSource source = new DamageSource("stand.spear_thorn") {
                @Override
                public Entity getEntity() {
                    return user;
                }
            };
            float finalDamage = damage;
            // 附魔加成
            ItemStack spear = user.getMainHandItem();
            if (entity instanceof LivingEntity) {
                finalDamage += SpearEnchantHelper.getTotalBonusDamage(spear, (LivingEntity) entity);
                SpearEnchantHelper.applyFireAspect(spear, (LivingEntity) entity);
            }
            entity.hurt(source, finalDamage);
            // Knockback upward
            entity.setDeltaMovement(entity.getDeltaMovement().add(0, 0.5, 0));
            entity.hurtMarked = true;
        }

        // Visual: spawn spear spike entities from the ground in expanding rings
        int spikeLifetime = 40; // 2 seconds
        for (int ring = 1; ring <= (int) SPIKE_RANGE; ring++) {
            int spikesInRing = Math.max(4, ring * 4);
            for (int i = 0; i < spikesInRing; i++) {
                double angle = (360.0 / spikesInRing) * i;
                double rad = Math.toRadians(angle);
                double px = center.x + ring * Math.cos(rad);
                double pz = center.z + ring * Math.sin(rad);

                // Terrain adaptation: find ground surface at this position
                double groundY = findGroundY(world, px, center.y, pz);

                // Spear pointing upward (pitch=0 is vertical up) with slight random tilt
                float spikeYaw = (float) angle;
                float spikePitch = (float)(Math.random() * 20.0 - 10.0); // mostly upward with slight tilt
                com.babelmoth.rotp_ata.entity.SpearSpikeEntity spike =
                        new com.babelmoth.rotp_ata.entity.SpearSpikeEntity(world, px, groundY, pz, spikeYaw, spikePitch, spikeLifetime);
                world.addFreshEntity(spike);
            }
        }

        user.swing(Hand.MAIN_HAND, true);
        user.playSound(SoundEvents.TRIDENT_HIT_GROUND, 1.5F, 0.8F);
        user.playSound(SoundEvents.GENERIC_EXPLODE, 0.5F, 1.5F);
    }

    /**
     * Find the ground surface Y at the given XZ position, searching up and down from referenceY.
     * Returns the Y of the top of the highest solid block within range.
     */
    private static double findGroundY(World world, double x, double referenceY, double z) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int refY = (int) Math.floor(referenceY);
        // Search up to 5 blocks above and below the reference
        for (int dy = 5; dy >= -5; dy--) {
            mutable.set((int) Math.floor(x), refY + dy, (int) Math.floor(z));
            BlockState state = world.getBlockState(mutable);
            BlockState above = world.getBlockState(mutable.above());
            // Ground = solid block with air/non-solid above
            if (state.getMaterial().isSolid() && !above.getMaterial().isSolid()) {
                return mutable.getY() + 1.0;
            }
        }
        // Fallback to reference Y
        return referenceY;
    }

    private static float getScaledDamage(IStandPower power) {
        IStandManifestation manifestation = power.getStandManifestation();
        if (manifestation instanceof StandEntity) {
            return (float) (BASE_DAMAGE * ((StandEntity) manifestation).getAttackDamage() / 8.0);
        }
        return BASE_DAMAGE;
    }
}
