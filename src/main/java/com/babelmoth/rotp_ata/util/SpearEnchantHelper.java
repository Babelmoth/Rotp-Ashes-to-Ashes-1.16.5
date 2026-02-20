package com.babelmoth.rotp_ata.util;

import com.babelmoth.rotp_ata.init.InitItems;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.CreatureAttribute;
import net.minecraft.entity.LivingEntity;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.ResourceLocation;

/**
 * Utility class for reading spear enchantments and applying their effects.
 */
public class SpearEnchantHelper {

    private static final String ENCHANT_SAVE_KEY = "ThelaHunGinjeetEnchantments";
    private static final String DEATH_PROTECTION_KEY = "ThelaHunGinjeetDeathProtection";

    // ==================== 读取附魔等级 ====================

    public static int getSharpnessLevel(ItemStack spear) {
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, spear);
    }

    public static int getSmiteLevel(ItemStack spear) {
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SMITE, spear);
    }

    public static int getBaneOfArthropodsLevel(ItemStack spear) {
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BANE_OF_ARTHROPODS, spear);
    }

    public static int getFireAspectLevel(ItemStack spear) {
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FIRE_ASPECT, spear);
    }

    public static int getKnockbackLevel(ItemStack spear) {
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.KNOCKBACK, spear);
    }

    public static int getLootingLevel(ItemStack spear) {
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MOB_LOOTING, spear);
    }

    public static int getUnbreakingLevel(ItemStack spear) {
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, spear);
    }

    public static int getRiptideLevel(ItemStack spear) {
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.RIPTIDE, spear);
    }

    public static int getChannelingLevel(ItemStack spear) {
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.CHANNELING, spear);
    }

    public static int getSweepingLevel(ItemStack spear) {
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SWEEPING_EDGE, spear);
    }

    public static int getImpalingLevel(ItemStack spear) {
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.IMPALING, spear);
    }

    // ==================== 伤害计算 ====================

    /**
     * 锋利附魔的额外伤害: 1.0 + 0.5 * (level - 1)
     */
    public static float getSharpnessBonusDamage(ItemStack spear) {
        int level = getSharpnessLevel(spear);
        if (level <= 0) return 0;
        return 1.0F + 0.5F * (level - 1);
    }

    /**
     * 亡灵杀手对亡灵目标的额外伤害: 2.5 * level
     */
    public static float getSmiteBonusDamage(ItemStack spear, LivingEntity target) {
        int level = getSmiteLevel(spear);
        if (level <= 0 || target.getMobType() != CreatureAttribute.UNDEAD) return 0;
        return 2.5F * level;
    }

    /**
     * 节肢杀手对节肢目标的额外伤害: 2.5 * level
     */
    public static float getBaneOfArthropodsBonusDamage(ItemStack spear, LivingEntity target) {
        int level = getBaneOfArthropodsLevel(spear);
        if (level <= 0 || target.getMobType() != CreatureAttribute.ARTHROPOD) return 0;
        return 2.5F * level;
    }

    /**
     * 获取对目标的总附魔额外伤害（锋利 + 亡灵/节肢杀手）
     */
    public static float getTotalBonusDamage(ItemStack spear, LivingEntity target) {
        return getSharpnessBonusDamage(spear)
                + getSmiteBonusDamage(spear, target)
                + getBaneOfArthropodsBonusDamage(spear, target);
    }

    // ==================== 效果应用 ====================

    /**
     * 对目标应用火焰附加效果
     */
    public static void applyFireAspect(ItemStack spear, LivingEntity target) {
        int level = getFireAspectLevel(spear);
        if (level > 0) {
            target.setSecondsOnFire(level * 4);
        }
    }

    /**
     * 获取击退附魔的额外击退强度
     */
    public static float getKnockbackBonus(ItemStack spear) {
        int level = getKnockbackLevel(spear);
        return level * 0.5F;
    }

    // ==================== 附魔保存/恢复 ====================

    /**
     * 将长矛的附魔数据保存到玩家 persistent data
     */
    public static void saveEnchantments(PlayerEntity player, ItemStack spear) {
        CompoundNBT data = player.getPersistentData();
        if (spear.isEnchanted()) {
            ListNBT enchantList = spear.getEnchantmentTags();
            data.put(ENCHANT_SAVE_KEY, enchantList.copy());
            // 授予隐藏成就 "This is a dangerous place.."
            grantEnchantAdvancement(player);
        } else {
            data.remove(ENCHANT_SAVE_KEY);
        }
    }

    /**
     * 授予附魔长矛的隐藏成就
     */
    private static void grantEnchantAdvancement(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        Advancement advancement = serverPlayer.getServer().getAdvancements()
                .getAdvancement(new ResourceLocation("rotp_ata", "thela_hun_ginjeet_enchant"));
        if (advancement == null) return;
        AdvancementProgress progress = serverPlayer.getAdvancements().getOrStartProgress(advancement);
        if (!progress.isDone()) {
            for (String criterion : progress.getRemainingCriteria()) {
                serverPlayer.getAdvancements().award(advancement, criterion);
            }
        }
    }

    /**
     * 从玩家 persistent data 恢复附魔到长矛
     */
    public static void restoreEnchantments(PlayerEntity player, ItemStack spear) {
        CompoundNBT data = player.getPersistentData();
        if (data.contains(ENCHANT_SAVE_KEY)) {
            ListNBT enchantList = data.getList(ENCHANT_SAVE_KEY, 10); // 10 = CompoundNBT
            if (!enchantList.isEmpty()) {
                spear.getOrCreateTag().put("Enchantments", enchantList.copy());
            }
        }
    }

    /**
     * 清除保存的附魔数据
     */
    public static void clearSavedEnchantments(PlayerEntity player) {
        player.getPersistentData().remove(ENCHANT_SAVE_KEY);
    }

    /**
     * 设置死亡保护标记，防止重生过渡期内附魔被误清除
     */
    public static void setDeathProtection(PlayerEntity player) {
        player.getPersistentData().putBoolean(DEATH_PROTECTION_KEY, true);
    }

    /**
     * 清除死亡保护标记（替身恢复后调用）
     */
    public static void clearDeathProtection(PlayerEntity player) {
        player.getPersistentData().remove(DEATH_PROTECTION_KEY);
    }

    /**
     * 检查是否处于死亡保护期
     */
    public static boolean hasDeathProtection(PlayerEntity player) {
        return player.getPersistentData().getBoolean(DEATH_PROTECTION_KEY);
    }

    // ==================== 工具方法 ====================

    /**
     * 从玩家背包获取长矛（主手优先）
     */
    public static ItemStack getSpearFromPlayer(PlayerEntity player) {
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty() && mainHand.getItem() == InitItems.THELA_HUN_GINJEET_SPEAR.get()) {
            return mainHand;
        }
        for (int i = 0; i < player.inventory.getContainerSize(); i++) {
            ItemStack stack = player.inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == InitItems.THELA_HUN_GINJEET_SPEAR.get()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
