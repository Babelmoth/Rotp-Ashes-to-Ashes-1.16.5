package com.babelmoth.rotp_ata.entity;

import com.babelmoth.rotp_ata.entity.ThelaHunGinjeetSpearEntity;
import com.babelmoth.rotp_ata.init.InitItems;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.entity.stand.StandEntityType;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import com.github.standobyte.jojo.init.ModEntityAttributes;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.itemtracking.SidedItemTrackerMap;
import com.github.standobyte.jojo.itemtracking.itemcap.TrackerItemStack;
import com.babelmoth.rotp_ata.util.SpearEnchantHelper;

import java.util.UUID;

public class ThelaHunGinjeetStandEntity extends StandEntity {

    private static final String SPEAR_TRACKER_KEY = "ThelaHunGinjeetSpearTracker";
    private static final UUID SHARPNESS_MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-111111111111");
    private static final UUID UNBREAKING_MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-222222222222");

    public ThelaHunGinjeetStandEntity(StandEntityType<ThelaHunGinjeetStandEntity> type, World world) {
        super(type, world);
    }

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide) {
            return;
        }

        LivingEntity user = getUser();
        if (user == null) {
            return;
        }

        if (user instanceof PlayerEntity) {
            ensureSingleSpearForUser((PlayerEntity) user);
            // 持续保存长矛附魔数据到玩家 persistent data
            saveSpearEnchantments((PlayerEntity) user);
            // 根据附魔更新替身属性
            applyEnchantmentStatModifiers((PlayerEntity) user);
            // 替身已恢复活跃，清除死亡保护标记
            SpearEnchantHelper.clearDeathProtection((PlayerEntity) user);
        }
    }

    private void ensureSingleSpearForUser(PlayerEntity user) {
        // 1. 检查是否有已投掷的长矛实体
        if (hasThrownSpear(user)) {
            removeAllSpearsFromInventory(user);
            return;
        }

        // 2. 检查背包中是否已有长矛
        int countInInv = countSpearsInInventory(user);
        if (countInInv == 1) {
            return;
        }
        if (countInInv > 1) {
            // 多余的移除，只保留一把
            int toRemove = countInInv - 1;
            for (int i = 0; i < user.inventory.getContainerSize() && toRemove > 0; i++) {
                ItemStack stack = user.inventory.getItem(i);
                if (!stack.isEmpty() && stack.getItem() == InitItems.THELA_HUN_GINJEET_SPEAR.get()) {
                    int removed = Math.min(toRemove, stack.getCount());
                    stack.shrink(removed);
                    toRemove -= removed;
                }
            }
            return;
        }

        // 3. 背包中没有长矛，通过 tracker UUID 检查长矛是否存在于世界其他位置（箱子、地面、附魔台等）
        UUID trackerUUID = getSpearTrackerUUID(user);
        if (trackerUUID != null) {
            SidedItemTrackerMap trackerMap = SidedItemTrackerMap.getSidedTrackers(user.level);
            TrackerItemStack tracker = trackerMap.getTracker(trackerUUID);
            if (tracker != null && tracker.isTracked()) {
                // 标记仍存在 → 长矛在某处（箱子/地面/其他容器），不生成新的
                return;
            }
        }

        // 4. 没有长矛 → 创建新的并标记 tracker，恢复附魔
        ItemStack spear = new ItemStack(InitItems.THELA_HUN_GINJEET_SPEAR.get());
        SpearEnchantHelper.restoreEnchantments(user, spear);
        if (user instanceof ServerPlayerEntity) {
            TrackerItemStack tracker = TrackerItemStack.setTracked(spear, (ServerPlayerEntity) user);
            setSpearTrackerUUID(user, tracker.getTrackerId());
        }
        user.inventory.add(spear);
    }

    private int countSpearsInInventory(PlayerEntity user) {
        int count = 0;
        for (int i = 0; i < user.inventory.getContainerSize(); i++) {
            ItemStack stack = user.inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == InitItems.THELA_HUN_GINJEET_SPEAR.get()) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private UUID getSpearTrackerUUID(PlayerEntity user) {
        CompoundNBT data = user.getPersistentData();
        if (data.hasUUID(SPEAR_TRACKER_KEY)) {
            return data.getUUID(SPEAR_TRACKER_KEY);
        }
        return null;
    }

    private void setSpearTrackerUUID(PlayerEntity user, UUID uuid) {
        user.getPersistentData().putUUID(SPEAR_TRACKER_KEY, uuid);
    }

    private void saveSpearEnchantments(PlayerEntity user) {
        ItemStack spear = SpearEnchantHelper.getSpearFromPlayer(user);
        if (!spear.isEmpty()) {
            SpearEnchantHelper.saveEnchantments(user, spear);
        }
    }

    private void applyEnchantmentStatModifiers(PlayerEntity user) {
        ItemStack spear = SpearEnchantHelper.getSpearFromPlayer(user);

        // 锋利 → 替身攻击力 (1.0 + 0.5 * (level - 1))
        ModifiableAttributeInstance attackAttr = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttr != null) {
            attackAttr.removeModifier(SHARPNESS_MODIFIER_UUID);
            float sharpnessBonus = SpearEnchantHelper.getSharpnessBonusDamage(spear);
            if (sharpnessBonus > 0) {
                attackAttr.addTransientModifier(new AttributeModifier(
                        SHARPNESS_MODIFIER_UUID, "Spear Sharpness",
                        sharpnessBonus, AttributeModifier.Operation.ADDITION));
            }
        }

        // 耐久 → 替身持久力 (每级 +2.0)
        ModifiableAttributeInstance durabilityAttr = this.getAttribute(ModEntityAttributes.STAND_DURABILITY.get());
        if (durabilityAttr != null) {
            durabilityAttr.removeModifier(UNBREAKING_MODIFIER_UUID);
            int unbreakingLevel = SpearEnchantHelper.getUnbreakingLevel(spear);
            if (unbreakingLevel > 0) {
                durabilityAttr.addTransientModifier(new AttributeModifier(
                        UNBREAKING_MODIFIER_UUID, "Spear Unbreaking",
                        unbreakingLevel * 2.0, AttributeModifier.Operation.ADDITION));
            }
        }
    }

    private boolean hasThrownSpear(PlayerEntity user) {
        AxisAlignedBB box = user.getBoundingBox().inflate(128.0);
        for (ThelaHunGinjeetSpearEntity spear : user.level.getEntitiesOfClass(ThelaHunGinjeetSpearEntity.class, box,
                e -> e.isAlive() && user.equals(e.getOwner()))) {
            ItemStack pickup = spear.getSpearItem();
            if (!pickup.isEmpty() && pickup.getItem() == InitItems.THELA_HUN_GINJEET_SPEAR.get()) {
                return true;
            }
        }
        return false;
    }

    private void removeAllSpearsFromInventory(PlayerEntity user) {
        for (int i = 0; i < user.inventory.getContainerSize(); i++) {
            ItemStack stack = user.inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == InitItems.THELA_HUN_GINJEET_SPEAR.get()) {
                user.inventory.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSrc) {
        return true;
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        this.addEffect(new EffectInstance(ModStatusEffects.FULL_INVISIBILITY.get(), Integer.MAX_VALUE, Integer.MAX_VALUE, false, false, false));
    }

    @Override
    public float getLeapStrength() {
        return 0.0F;
    }

    @Override
    protected double leapBaseStrength() {
        return 0.0;
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (level.isClientSide) {
            return;
        }
        LivingEntity user = getUser();
        if (user == null) {
            return;
        }

        if (user instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) user;
            removeAllSpearsFromInventory(player);
            // 清除世界内属于自己的所有长矛实体，并清除对应目标身上的stuck/thorn数据
            removeAllThrownSpears(player);
            // 清除 tracker 标记和被追踪的长矛（箱子/地面/其他玩家等）
            clearTrackedSpear(player);
        }
    }

    private void removeAllThrownSpears(PlayerEntity player) {
        AxisAlignedBB box = player.getBoundingBox().inflate(128.0);
        for (ThelaHunGinjeetSpearEntity spear : player.level.getEntitiesOfClass(ThelaHunGinjeetSpearEntity.class, box,
                e -> e.isAlive() && player.equals(e.getOwner()))) {
            // 如果长矛插在目标身上，清除目标的stuck和thorn数据
            int targetId = spear.getStuckTargetId();
            if (targetId >= 0) {
                net.minecraft.entity.Entity target = player.level.getEntity(targetId);
                if (target instanceof LivingEntity) {
                    LivingEntity livingTarget = (LivingEntity) target;
                    // 清除stuck数
                    livingTarget.getCapability(com.babelmoth.rotp_ata.capability.SpearStuckProvider.SPEAR_STUCK_CAPABILITY).ifPresent(cap -> {
                        cap.setSpearCount(0);
                        com.babelmoth.rotp_ata.networking.AshesToAshesPacketHandler.CHANNEL.send(
                                net.minecraftforge.fml.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> livingTarget),
                                new com.babelmoth.rotp_ata.networking.SpearStuckSyncPacket(livingTarget.getId(), 0));
                    });
                    // 清除thorn数据
                    livingTarget.getCapability(com.babelmoth.rotp_ata.capability.SpearThornProvider.SPEAR_THORN_CAPABILITY).ifPresent(cap -> {
                        cap.reset();
                        com.babelmoth.rotp_ata.networking.AshesToAshesPacketHandler.CHANNEL.send(
                                net.minecraftforge.fml.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> livingTarget),
                                new com.babelmoth.rotp_ata.networking.SpearThornSyncPacket(
                                        livingTarget.getId(), cap.getThornCount(), cap.getDamageDealt(),
                                        cap.getDetachThreshold(), cap.hasSpear()));
                    });
                }
            }
            spear.remove();
        }
    }

    private void clearTrackedSpear(PlayerEntity player) {
        UUID trackerUUID = getSpearTrackerUUID(player);
        if (trackerUUID != null) {
            SidedItemTrackerMap trackerMap = SidedItemTrackerMap.getSidedTrackers(player.level);
            TrackerItemStack tracker = trackerMap.getTracker(trackerUUID);
            if (tracker != null) {
                // 清除被追踪的物品本身（使其从箱子/地面/其他玩家背包中消失）
                ItemStack trackedItem = tracker.getItem();
                if (trackedItem != null && !trackedItem.isEmpty()) {
                    trackedItem.setCount(0);
                }
                trackerMap.removeTracker(trackerUUID);
            }
            // 清除玩家身上存储的 tracker UUID
            player.getPersistentData().remove(SPEAR_TRACKER_KEY + "Most");
            player.getPersistentData().remove(SPEAR_TRACKER_KEY + "Least");
        }
    }
}
