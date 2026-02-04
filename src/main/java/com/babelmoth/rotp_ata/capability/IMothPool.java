package com.babelmoth.rotp_ata.capability;

import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * Interface for moth pool capability.
 * Manages moth slots, energy storage, and lifecycle.
 */
public interface IMothPool extends INBTSerializable<CompoundNBT> {
    int MAX_MOTHS = 500;
    int MOTH_MAX_KINETIC = 10;
    
    // Core Data
    int[] getKineticPool();
    int[] getHamonPool();
    void setKineticPool(int[] pool);
    void setHamonPool(int[] pool);
    
    // Basic Management
    int getTotalMoths();
    void setTotalMoths(int count);
    
    // Energy Management
    int getMothKinetic(int index);
    void setMothKinetic(int index, int amount);
    int getMothHamon(int index);
    void setMothHamon(int index, int amount);
    
    // Aggregation
    int getTotalKineticEnergy();
    /** 未被占用的动能：仅统计未部署（召回）槽位的动能；已放出飞蛾的槽位视为被占用，技能只能使用本返回值。 */
    int getAvailableKinetic();
    int getTotalHamonEnergy();
    
    // Life Cycle & Slot Management
    int allocateSlot(); // Returns index or -1 if full
    int allocateSlotWithPriority(boolean emptyFirst); // Specialized for replenishment/adhesion
    void recallMoth(int index); // Entity removed, data kept (Reserve)
    void killMoth(int index);   // Entity killed, data cleared
    boolean isSlotActive(int index);
    void assertDeployed(int index);
    void clearAllDeployed();
    
    // Mass Operations
    boolean consumeMoths(int amount); // Consumes N moths (Reserve > Active, Empty > Charged)
    /** 从「未被占用」的槽位（未部署/召回）消耗动能，返回实际消耗量。已放出飞蛾的槽位不会被消耗。 */
    int consumeKinetic(int amount);
    /** 从未被占用的槽位消耗动能，且排除 excludeSlot；excludeSlot < 0 时等价于 consumeKinetic(amount)。 */
    int consumeKineticExcludingSlot(int amount, int excludeSlot);
    int consumeHamon(int amount);
    
    void tickRecovery(); // Passive recovery over time

    // Networking Sync
    void sync(net.minecraft.entity.player.ServerPlayerEntity player);
}
