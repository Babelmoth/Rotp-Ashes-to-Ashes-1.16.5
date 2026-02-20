package com.babelmoth.rotp_ata.action;

import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.power.impl.stand.IStandManifestation;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.babelmoth.rotp_ata.init.InitItems;

import com.babelmoth.rotp_ata.util.SpearEnchantHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * Spear Sweep: AOE sweep attack that damages and knocks back enemies in front.
 * Similar to Silver Chariot's heavy sweep attack.
 */
public class ThelaHunGinjeetSweep extends StandAction {
    private static final float BASE_SWEEP_DAMAGE = 5.0F;
    private static final double SWEEP_RANGE = 4.0;
    private static final double SWEEP_ANGLE = 120.0; // frontal 120-degree cone
    private static final float KNOCKBACK_STRENGTH = 0.6F;
    private static final float STAMINA_COST = 150.0F;

    public ThelaHunGinjeetSweep(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public TargetRequirement getTargetRequirement() {
        return TargetRequirement.NONE;
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

        Vector3d userPos = user.position();
        ItemStack spear = user.getMainHandItem();
        int sweepingLevel = SpearEnchantHelper.getSweepingLevel(spear);
        double actualRange = SWEEP_RANGE + sweepingLevel * 0.5;

        AxisAlignedBB sweepBox = user.getBoundingBox().inflate(actualRange);
        List<Entity> entities = world.getEntities(user, sweepBox, e -> e instanceof LivingEntity && e.isAlive());

        // Player's look direction (horizontal only)
        float yRotRad = user.yRot * ((float) Math.PI / 180.0F);
        Vector3d lookDir = new Vector3d(-MathHelper.sin(yRotRad), 0, MathHelper.cos(yRotRad));
        // cos(60) = 0.5 for 120-degree cone
        double minDot = Math.cos(Math.toRadians(SWEEP_ANGLE / 2.0));

        int hitCount = 0;
        for (Entity entity : entities) {
            Vector3d toEntity = entity.position().subtract(userPos);
            double dist = Math.sqrt(toEntity.x * toEntity.x + toEntity.z * toEntity.z);
            if (dist > actualRange || dist < 0.1) continue;

            // Check if within frontal cone using dot product
            Vector3d toEntityNorm = new Vector3d(toEntity.x / dist, 0, toEntity.z / dist);
            double dot = lookDir.x * toEntityNorm.x + lookDir.z * toEntityNorm.z;
            if (dot < minDot) continue;

            // DamageSource with "stand" in msgId to enable stand touch
            DamageSource source = new DamageSource("stand.spear_sweep") {
                @Override
                public Entity getEntity() {
                    return user;
                }
            };
            float damage = getScaledDamage(power);
            // 横扫之刃加成: 每级增加 (1 + level) / (1 + 4) 比例的额外伤害
            if (sweepingLevel > 0) {
                damage += damage * ((1.0F + sweepingLevel) / 5.0F);
            }
            // 附魔加成
            if (entity instanceof LivingEntity) {
                damage += SpearEnchantHelper.getTotalBonusDamage(spear, (LivingEntity) entity);
                SpearEnchantHelper.applyFireAspect(spear, (LivingEntity) entity);
            }
            entity.hurt(source, damage);

            // Knockback: push away from player + 附魔击退加成
            double knockScale = KNOCKBACK_STRENGTH + SpearEnchantHelper.getKnockbackBonus(spear);
            Vector3d knockDir = toEntity.normalize();
            entity.setDeltaMovement(entity.getDeltaMovement().add(
                    knockDir.x * knockScale,
                    0.3,
                    knockDir.z * knockScale));
            entity.hurtMarked = true;
            hitCount++;
        }

        // Swing hand animation
        user.swing(Hand.MAIN_HAND, true);

        // Sweep sound effects
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.TRIDENT_RIPTIDE_3, net.minecraft.util.SoundCategory.PLAYERS, 0.6F, 1.4F);
        if (hitCount > 0) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, net.minecraft.util.SoundCategory.PLAYERS, 1.0F, 1.0F);
        } else {
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.PLAYER_ATTACK_WEAK, net.minecraft.util.SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
        // Trigger vanilla sweep particles
        if (user instanceof PlayerEntity) {
            ((PlayerEntity) user).sweepAttack();
        }
    }

    /**
     * Scale damage based on stand attack power. Base power 8.0 = base damage.
     */
    private static float getScaledDamage(IStandPower power) {
        IStandManifestation manifestation = power.getStandManifestation();
        if (manifestation instanceof StandEntity) {
            return (float) (BASE_SWEEP_DAMAGE * ((StandEntity) manifestation).getAttackDamage() / 8.0);
        }
        return BASE_SWEEP_DAMAGE;
    }
}
