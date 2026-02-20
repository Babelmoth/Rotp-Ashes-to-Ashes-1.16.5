package com.babelmoth.rotp_ata.item;

import com.babelmoth.rotp_ata.entity.ThelaHunGinjeetSpearEntity;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.power.impl.stand.IStandManifestation;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.UseAction;
import net.minecraft.stats.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeMod;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentType;
import net.minecraft.enchantment.Enchantments;

import javax.annotation.Nullable;
import java.util.List;

public class ThelaHunGinjeetSpearItem extends Item {
    private static final int USE_DURATION = 72000;
    private final Multimap<Attribute, AttributeModifier> defaultModifiers;

    public ThelaHunGinjeetSpearItem(Properties properties) {
        super(properties);
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        // 基础攻击伤害 1 + 7 = 8 (matches stand power 8.0)
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", 7.0D, AttributeModifier.Operation.ADDITION));
        // 攻速: 基础 4 - 3.0 = 1.0
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", -3.0D, AttributeModifier.Operation.ADDITION));
        // 攻击范围加成 +1
        if (ForgeMod.REACH_DISTANCE.isPresent()) {
            builder.put(ForgeMod.REACH_DISTANCE.get(), new AttributeModifier(java.util.UUID.fromString("7a1b2c3d-4e5f-6789-abcd-ef0123456789"), "Weapon modifier", 1.0D, AttributeModifier.Operation.ADDITION));
        }
        this.defaultModifiers = builder.build();
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlotType slot, ItemStack stack) {
        if (slot != EquipmentSlotType.MAINHAND) {
            return super.getAttributeModifiers(slot, stack);
        }
        // Check if the stack has stand-scaled damage stored in NBT
        if (stack.hasTag() && stack.getTag().contains("StandScaledDamage")) {
            double scaledDamage = stack.getTag().getDouble("StandScaledDamage");
            ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
            // scaledDamage is the final damage value; subtract 1 for base hand damage
            builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", scaledDamage - 1.0D, AttributeModifier.Operation.ADDITION));
            builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", -3.0D, AttributeModifier.Operation.ADDITION));
            if (ForgeMod.REACH_DISTANCE.isPresent()) {
                builder.put(ForgeMod.REACH_DISTANCE.get(), new AttributeModifier(java.util.UUID.fromString("7a1b2c3d-4e5f-6789-abcd-ef0123456789"), "Weapon modifier", 1.0D, AttributeModifier.Operation.ADDITION));
            }
            return builder.build();
        }
        return this.defaultModifiers;
    }

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (!itemstack.isEmpty()) {
            player.startUsingItem(hand);
            return ActionResult.consume(itemstack);
        }
        return ActionResult.pass(itemstack);
    }

    @Override
    public UseAction getUseAnimation(ItemStack stack) {
        return UseAction.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_DURATION;
    }

    @Override
    public void releaseUsing(ItemStack stack, World world, LivingEntity livingEntity, int timeLeft) {
        if (!(livingEntity instanceof PlayerEntity)) {
            return;
        }
        PlayerEntity player = (PlayerEntity) livingEntity;
        int usedTicks = this.getUseDuration(stack) - timeLeft;
        // 三叉戟式蓄力：必须蓄力至少0.5秒（10 ticks）才能投掷
        if (usedTicks < 10) {
            return;
        }
        if (!world.isClientSide) {
            ThelaHunGinjeetSpearEntity spearEntity = new ThelaHunGinjeetSpearEntity(world, player, stack.copy());
            spearEntity.shootFromRotation(player, player.xRot, player.yRot, 0.0F, 2.5F, 1.0F);
            if (player.abilities.instabuild) {
                spearEntity.pickup = AbstractArrowEntity.PickupStatus.CREATIVE_ONLY;
            }
            world.addFreshEntity(spearEntity);
            world.playSound(null, spearEntity, SoundEvents.TRIDENT_THROW, SoundCategory.PLAYERS, 1.0F, 1.0F);
            if (!player.abilities.instabuild) {
                stack.shrink(1);
            }
            player.awardStat(Stats.ITEM_USED.get(this));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        super.appendHoverText(stack, world, tooltip, flag);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        // 与钻石制装备相同
        return 10;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        // 剑类附魔池
        if (enchantment.category == EnchantmentType.WEAPON) {
            return true;
        }
        // 额外允许激流和引雷
        if (enchantment == Enchantments.RIPTIDE || enchantment == Enchantments.CHANNELING) {
            return true;
        }
        // 允许通用附魔（经验修补、消失诅咒等）
        if (enchantment.category == EnchantmentType.BREAKABLE) {
            return true;
        }
        return false;
    }
}
