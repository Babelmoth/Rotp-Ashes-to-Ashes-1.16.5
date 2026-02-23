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

public class SpearEnchantHelper {

    private static final String ENCHANT_SAVE_KEY = "ThelaHunGinjeetEnchantments";
    private static final String DEATH_PROTECTION_KEY = "ThelaHunGinjeetDeathProtection";

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

    public static float getSharpnessBonusDamage(ItemStack spear) {
        int level = getSharpnessLevel(spear);
        if (level <= 0) return 0;
        return 1.0F + 0.5F * (level - 1);
    }

    public static float getSmiteBonusDamage(ItemStack spear, LivingEntity target) {
        int level = getSmiteLevel(spear);
        if (level <= 0 || target.getMobType() != CreatureAttribute.UNDEAD) return 0;
        return 2.5F * level;
    }

    public static float getBaneOfArthropodsBonusDamage(ItemStack spear, LivingEntity target) {
        int level = getBaneOfArthropodsLevel(spear);
        if (level <= 0 || target.getMobType() != CreatureAttribute.ARTHROPOD) return 0;
        return 2.5F * level;
    }

    public static float getTotalBonusDamage(ItemStack spear, LivingEntity target) {
        return getSharpnessBonusDamage(spear)
                + getSmiteBonusDamage(spear, target)
                + getBaneOfArthropodsBonusDamage(spear, target);
    }

    public static void applyFireAspect(ItemStack spear, LivingEntity target) {
        int level = getFireAspectLevel(spear);
        if (level > 0) {
            target.setSecondsOnFire(level * 4);
        }
    }

    public static float getKnockbackBonus(ItemStack spear) {
        int level = getKnockbackLevel(spear);
        return level * 0.5F;
    }

    public static void saveEnchantments(PlayerEntity player, ItemStack spear) {
        CompoundNBT data = player.getPersistentData();
        if (spear.isEnchanted()) {
            ListNBT enchantList = spear.getEnchantmentTags();
            data.put(ENCHANT_SAVE_KEY, enchantList.copy());

            grantEnchantAdvancement(player);
        } else {
            data.remove(ENCHANT_SAVE_KEY);
        }
    }

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

    public static void restoreEnchantments(PlayerEntity player, ItemStack spear) {
        CompoundNBT data = player.getPersistentData();
        if (data.contains(ENCHANT_SAVE_KEY)) {
            ListNBT enchantList = data.getList(ENCHANT_SAVE_KEY, 10);
            if (!enchantList.isEmpty()) {
                spear.getOrCreateTag().put("Enchantments", enchantList.copy());
            }
        }
    }

    public static void clearSavedEnchantments(PlayerEntity player) {
        player.getPersistentData().remove(ENCHANT_SAVE_KEY);
    }

    public static void setDeathProtection(PlayerEntity player) {
        player.getPersistentData().putBoolean(DEATH_PROTECTION_KEY, true);
    }

    public static void clearDeathProtection(PlayerEntity player) {
        player.getPersistentData().remove(DEATH_PROTECTION_KEY);
    }

    public static boolean hasDeathProtection(PlayerEntity player) {
        return player.getPersistentData().getBoolean(DEATH_PROTECTION_KEY);
    }

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
