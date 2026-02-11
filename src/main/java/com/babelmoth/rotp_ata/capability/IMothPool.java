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
    /** Kinetic energy available for skills: sums only non-deployed (reserve) slots. Deployed slots (moths out) are treated as occupied and excluded. */
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
    /** Consume N moths from the pool (Reserve > Active, Empty > Charged). */
    boolean consumeMoths(int amount);
    /** Consume kinetic energy from non-deployed (reserve) slots only; returns the actual amount consumed. Deployed slots are never drained. */
    int consumeKinetic(int amount);
    /** Consume kinetic from non-deployed slots while excluding excludeSlot; excludeSlot < 0 behaves like consumeKinetic(amount). */
    int consumeKineticExcludingSlot(int amount, int excludeSlot);
    int consumeHamon(int amount);
    
    void tickRecovery(); // Passive recovery over time

    // Networking Sync
    void sync(net.minecraft.entity.player.ServerPlayerEntity player);
}
