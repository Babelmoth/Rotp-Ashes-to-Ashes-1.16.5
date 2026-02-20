package com.babelmoth.rotp_ata.action;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.power.impl.stand.IStandManifestation;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.github.standobyte.jojo.util.general.LazySupplier;
import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.init.InitItems;

import com.babelmoth.rotp_ata.util.SpearEnchantHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Swift Thrust: Player dashes forward and damages enemies in the path.
 * Similar to Silver Chariot's dash attack, but performed by the player.
 */
public class ThelaHunGinjeetSwiftThrust extends StandAction {
    private static final float BASE_THRUST_DAMAGE = 5.0F;
    private static final double THRUST_DISTANCE = 10.0;
    private static final double HIT_RADIUS = 1.5;
    private static final int THRUST_STEPS = 6;
    private static final float STAMINA_COST = 200.0F;

    private final LazySupplier<ResourceLocation> riptideTexture =
            new LazySupplier<>(() -> new ResourceLocation(AddonMain.MOD_ID, "textures/action/thela_hun_ginjeet_swift_thrust_riptide.png"));

    public ThelaHunGinjeetSwiftThrust(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public ResourceLocation getIconTexturePath(@Nullable IStandPower power) {
        if (power != null && power.getUser() != null) {
            ItemStack spear = power.getUser().getMainHandItem();
            if (!spear.isEmpty() && spear.getItem() == InitItems.THELA_HUN_GINJEET_SPEAR.get()
                    && SpearEnchantHelper.getRiptideLevel(spear) > 0) {
                return riptideTexture.get();
            }
        }
        return super.getIconTexturePath(power);
    }

    @Override
    public String getTranslationKey(IStandPower power, ActionTarget target) {
        if (power != null && power.getUser() != null) {
            ItemStack spear = power.getUser().getMainHandItem();
            if (!spear.isEmpty() && spear.getItem() == InitItems.THELA_HUN_GINJEET_SPEAR.get()
                    && SpearEnchantHelper.getRiptideLevel(spear) > 0) {
                return "action.rotp_ata.thela_hun_ginjeet_riptide_thrust";
            }
        }
        return super.getTranslationKey(power, target);
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

        ItemStack spear = user.getMainHandItem();
        int riptideLevel = SpearEnchantHelper.getRiptideLevel(spear);

        if (riptideLevel > 0) {
            performRiptideThrust(world, user, power, spear, riptideLevel);
        } else {
            performNormalThrust(world, user, power, spear);
        }
    }

    private void performNormalThrust(World world, LivingEntity user, IStandPower power, ItemStack spear) {
        Vector3d lookVec = user.getLookAngle();
        Vector3d startPos = user.position().add(0, user.getBbHeight() * 0.5, 0);

        Set<Integer> hitEntities = new HashSet<>();

        // Step-check enemies along the path
        for (int step = 1; step <= THRUST_STEPS; step++) {
            double progress = (double) step / THRUST_STEPS;
            Vector3d checkPos = startPos.add(lookVec.scale(THRUST_DISTANCE * progress));

            AxisAlignedBB hitBox = new AxisAlignedBB(
                    checkPos.x - HIT_RADIUS, checkPos.y - 0.5, checkPos.z - HIT_RADIUS,
                    checkPos.x + HIT_RADIUS, checkPos.y + user.getBbHeight() + 0.5, checkPos.z + HIT_RADIUS);

            List<Entity> entities = world.getEntities(user, hitBox, e -> e instanceof LivingEntity && e.isAlive() && !hitEntities.contains(e.getId()));
            for (Entity entity : entities) {
                hitEntities.add(entity.getId());
                DamageSource source = new DamageSource("stand.spear_thrust") {
                    @Override
                    public Entity getEntity() {
                        return user;
                    }
                };
                float damage = getScaledDamage(power);
                if (entity instanceof LivingEntity) {
                    damage += SpearEnchantHelper.getTotalBonusDamage(spear, (LivingEntity) entity);
                    SpearEnchantHelper.applyFireAspect(spear, (LivingEntity) entity);
                }
                entity.hurt(source, damage);
                double knockbackScale = 0.8 + SpearEnchantHelper.getKnockbackBonus(spear);
                Vector3d knockback = lookVec.scale(knockbackScale);
                entity.setDeltaMovement(entity.getDeltaMovement().add(knockback.x, knockback.y * 0.5 + 0.1, knockback.z));
                entity.hurtMarked = true;
            }
        }

        Vector3d motion = lookVec.scale(THRUST_DISTANCE * 0.15);
        user.setDeltaMovement(motion.x, motion.y, motion.z);
        user.hurtMarked = true;

        user.swing(Hand.MAIN_HAND, true);
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.TRIDENT_THROW, net.minecraft.util.SoundCategory.PLAYERS, 1.0F, 1.2F);
        if (!hitEntities.isEmpty()) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.PLAYER_ATTACK_STRONG, net.minecraft.util.SoundCategory.PLAYERS, 1.0F, 0.9F);
        }
    }

    /**
     * 激流旋转突刺：360°AOE旋转攻击 + 向前冲刺，伤害随激流等级提升
     */
    private void performRiptideThrust(World world, LivingEntity user, IStandPower power, ItemStack spear, int riptideLevel) {
        Vector3d lookVec = user.getLookAngle();
        Vector3d startPos = user.position().add(0, user.getBbHeight() * 0.5, 0);

        // 激流伤害加成：每级 +2.0
        float riptideDamageBonus = riptideLevel * 2.0F;

        Set<Integer> hitEntities = new HashSet<>();

        // 旋转突刺：沿路径 + 360°范围检测
        double riptideDistance = THRUST_DISTANCE * (1.0 + riptideLevel * 0.2);
        double riptideRadius = HIT_RADIUS * 2.0;

        for (int step = 1; step <= THRUST_STEPS; step++) {
            double progress = (double) step / THRUST_STEPS;
            Vector3d checkPos = startPos.add(lookVec.scale(riptideDistance * progress));

            AxisAlignedBB hitBox = new AxisAlignedBB(
                    checkPos.x - riptideRadius, checkPos.y - 1.0, checkPos.z - riptideRadius,
                    checkPos.x + riptideRadius, checkPos.y + user.getBbHeight() + 1.0, checkPos.z + riptideRadius);

            List<Entity> entities = world.getEntities(user, hitBox, e -> e instanceof LivingEntity && e.isAlive() && !hitEntities.contains(e.getId()));
            for (Entity entity : entities) {
                hitEntities.add(entity.getId());
                DamageSource source = new DamageSource("stand.spear_riptide") {
                    @Override
                    public Entity getEntity() {
                        return user;
                    }
                };
                float damage = getScaledDamage(power) + riptideDamageBonus;
                if (entity instanceof LivingEntity) {
                    damage += SpearEnchantHelper.getTotalBonusDamage(spear, (LivingEntity) entity);
                    SpearEnchantHelper.applyFireAspect(spear, (LivingEntity) entity);
                }
                entity.hurt(source, damage);
                // 旋转击退：从玩家位置向外推
                Vector3d toEntity = entity.position().subtract(user.position()).normalize();
                double knockbackScale = 1.0 + SpearEnchantHelper.getKnockbackBonus(spear);
                entity.setDeltaMovement(entity.getDeltaMovement().add(
                        toEntity.x * knockbackScale, 0.3, toEntity.z * knockbackScale));
                entity.hurtMarked = true;
            }
        }

        // 激流冲刺移动（比普通突刺更远）
        Vector3d motion = lookVec.scale(riptideDistance * 0.18);
        user.setDeltaMovement(motion.x, motion.y + 0.2, motion.z);
        user.hurtMarked = true;

        // 触发原版激流旋转动画 + 漩涡粒子特效
        user.startAutoSpinAttack(20);
        user.swing(Hand.MAIN_HAND, true);
        // 激流音效（根据等级选择不同音效）
        net.minecraft.util.SoundEvent riptideSound = riptideLevel >= 3 ? SoundEvents.TRIDENT_RIPTIDE_3
                : riptideLevel == 2 ? SoundEvents.TRIDENT_RIPTIDE_2 : SoundEvents.TRIDENT_RIPTIDE_1;
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                riptideSound, net.minecraft.util.SoundCategory.PLAYERS, 1.0F, 1.0F);
        if (!hitEntities.isEmpty()) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, net.minecraft.util.SoundCategory.PLAYERS, 1.0F, 0.8F);
        }
    }

    /**
     * Scale damage based on stand attack power. Base power 8.0 = base damage.
     */
    private static float getScaledDamage(IStandPower power) {
        IStandManifestation manifestation = power.getStandManifestation();
        if (manifestation instanceof StandEntity) {
            return (float) (BASE_THRUST_DAMAGE * ((StandEntity) manifestation).getAttackDamage() / 8.0);
        }
        return BASE_THRUST_DAMAGE;
    }
}
