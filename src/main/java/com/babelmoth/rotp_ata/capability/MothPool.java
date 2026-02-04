package com.babelmoth.rotp_ata.capability;

import net.minecraft.nbt.CompoundNBT;
import java.util.BitSet;

/**
 * Manages moth pool data (energy, slots, deployment status).
 */
public class MothPool implements IMothPool {
    private int[] kineticPool = new int[MAX_MOTHS];
    private int[] hamonPool = new int[MAX_MOTHS];
    private BitSet occupiedSlots = new BitSet(MAX_MOTHS);
    private BitSet deployedSlots = new BitSet(MAX_MOTHS);
    private int recoveryTimer = 0;

    @Override
    public int[] getKineticPool() {
        return kineticPool;
    }

    @Override
    public int[] getHamonPool() {
        return hamonPool;
    }

    @Override
    public void setKineticPool(int[] pool) {
        if (pool.length == MAX_MOTHS) this.kineticPool = pool;
    }

    @Override
    public void setHamonPool(int[] pool) {
        if (pool.length == MAX_MOTHS) this.hamonPool = pool;
    }

    @Override
    public int getTotalMoths() {
        return occupiedSlots.cardinality();
    }

    @Override
    public void setTotalMoths(int count) {
        // Not directly settable
    }

    @Override
    public int getMothKinetic(int index) {
        if (index >= 0 && index < MAX_MOTHS && occupiedSlots.get(index)) return kineticPool[index];
        return 0;
    }

    @Override
    public void setMothKinetic(int index, int amount) {
        if (index >= 0 && index < MAX_MOTHS && occupiedSlots.get(index)) {
            kineticPool[index] = Math.max(0, Math.min(amount, MOTH_MAX_KINETIC));
        }
    }

    @Override
    public int getMothHamon(int index) {
        if (index >= 0 && index < MAX_MOTHS && occupiedSlots.get(index)) return hamonPool[index];
        return 0;
    }

    @Override
    public void setMothHamon(int index, int amount) {
        if (index >= 0 && index < MAX_MOTHS && occupiedSlots.get(index)) {
            hamonPool[index] = Math.max(0, Math.min(amount, MOTH_MAX_KINETIC));
        }
    }

    @Override
    public int getTotalKineticEnergy() {
        int sum = 0;
        for (int i = occupiedSlots.nextSetBit(0); i >= 0; i = occupiedSlots.nextSetBit(i + 1)) {
            sum += kineticPool[i];
        }
        return sum;
    }

    @Override
    public int getAvailableKinetic() {
        int sum = 0;
        for (int i = occupiedSlots.nextSetBit(0); i >= 0; i = occupiedSlots.nextSetBit(i + 1)) {
            if (!deployedSlots.get(i)) sum += kineticPool[i];
        }
        return sum;
    }

    @Override
    public int getTotalHamonEnergy() {
        int sum = 0;
        for (int i = occupiedSlots.nextSetBit(0); i >= 0; i = occupiedSlots.nextSetBit(i + 1)) {
            sum += hamonPool[i];
        }
        return sum;
    }
    
    // --- Slot Management ---
    
    @Override
    public int allocateSlot() {
        return allocateSlotWithPriority(true); // Default behavior
    }

    @Override
    public int allocateSlotWithPriority(boolean emptyFirst) {
        int bestIndex = -1;
        
        if (emptyFirst) {
            for (int i = occupiedSlots.nextSetBit(0); i >= 0; i = occupiedSlots.nextSetBit(i + 1)) {
                if (!deployedSlots.get(i) && kineticPool[i] == 0) {
                    bestIndex = i;
                    break;
                }
            }
        }
        
        if (bestIndex == -1) {
            for (int i = occupiedSlots.nextSetBit(0); i >= 0; i = occupiedSlots.nextSetBit(i + 1)) {
                if (!deployedSlots.get(i)) {
                    bestIndex = i;
                    break;
                }
            }
        }
        
        if (bestIndex == -1) {
            bestIndex = occupiedSlots.nextClearBit(0);
            if (bestIndex < 0 || bestIndex >= MAX_MOTHS) return -1;
            
            occupiedSlots.set(bestIndex);
            kineticPool[bestIndex] = 0;
            hamonPool[bestIndex] = 0;
        }
        
        deployedSlots.set(bestIndex);
        return bestIndex;
    }
    
    @Override
    public void recallMoth(int index) {
        if (index >= 0 && index < MAX_MOTHS) {
            deployedSlots.clear(index);
        }
    }
    
    @Override
    public void killMoth(int index) {
        if (index >= 0 && index < MAX_MOTHS) {
            deployedSlots.clear(index);
            occupiedSlots.clear(index);
            kineticPool[index] = 0;
            hamonPool[index] = 0;
        }
    }
    
    @Override
    public boolean isSlotActive(int index) {
        return index >= 0 && index < MAX_MOTHS && occupiedSlots.get(index);
    }

    @Override
    public void assertDeployed(int index) {
        if (index >= 0 && index < MAX_MOTHS && occupiedSlots.get(index)) {
            deployedSlots.set(index);
        }
    }

    @Override
    public void clearAllDeployed() {
        deployedSlots.clear();
    }
    
    // -----------------------

    @Override
    public boolean consumeMoths(int amount) {
        if (occupiedSlots.cardinality() < amount) return false;
        
        int consumed = 0;
        
        // Priority 1: Reserve & Empty
        for (int i = occupiedSlots.nextSetBit(0); i >= 0 && consumed < amount; i = occupiedSlots.nextSetBit(i + 1)) {
            if (!deployedSlots.get(i) && kineticPool[i] == 0) {
                killMoth(i);
                consumed++;
            }
        }
        if (consumed >= amount) return true;
        
        // Priority 2: Reserve & Charged
        for (int i = occupiedSlots.nextSetBit(0); i >= 0 && consumed < amount; i = occupiedSlots.nextSetBit(i + 1)) {
            if (!deployedSlots.get(i)) {
                killMoth(i);
                consumed++;
            }
        }
        if (consumed >= amount) return true;
        
        // Priority 3: Active & Empty
        for (int i = occupiedSlots.nextSetBit(0); i >= 0 && consumed < amount; i = occupiedSlots.nextSetBit(i + 1)) {
            if (deployedSlots.get(i) && kineticPool[i] == 0) {
                killMoth(i);
                consumed++;
            }
        }
        if (consumed >= amount) return true;
        
        // Priority 4: Active & Charged
        for (int i = occupiedSlots.nextSetBit(0); i >= 0 && consumed < amount; i = occupiedSlots.nextSetBit(i + 1)) {
            if (deployedSlots.get(i)) {
                killMoth(i);
                consumed++;
            }
        }
        
        return true;
    }

    @Override
    public int consumeKinetic(int amount) {
        int totalConsumed = 0;
        while (totalConsumed < amount) {
            int richestIndex = -1;
            int maxEnergy = 0;
            for (int i = occupiedSlots.nextSetBit(0); i >= 0; i = occupiedSlots.nextSetBit(i + 1)) {
                if (deployedSlots.get(i)) continue; // 已放出飞蛾的槽位视为被占用，不参与消耗
                if (kineticPool[i] > maxEnergy) {
                    maxEnergy = kineticPool[i];
                    richestIndex = i;
                }
            }
            if (richestIndex == -1) break;
            int toTake = Math.min(amount - totalConsumed, maxEnergy);
            kineticPool[richestIndex] -= toTake;
            totalConsumed += toTake;
        }
        return totalConsumed;
    }

    @Override
    public int consumeKineticExcludingSlot(int amount, int excludeSlot) {
        if (excludeSlot < 0 || excludeSlot >= MAX_MOTHS) return consumeKinetic(amount);
        int totalConsumed = 0;
        while (totalConsumed < amount) {
            int richestIndex = -1;
            int maxEnergy = 0;
            for (int i = occupiedSlots.nextSetBit(0); i >= 0; i = occupiedSlots.nextSetBit(i + 1)) {
                if (deployedSlots.get(i) || i == excludeSlot) continue; // 被占用槽位与排除槽位均不消耗
                if (kineticPool[i] > maxEnergy) {
                    maxEnergy = kineticPool[i];
                    richestIndex = i;
                }
            }
            if (richestIndex == -1) break;
            int toTake = Math.min(amount - totalConsumed, kineticPool[richestIndex]);
            kineticPool[richestIndex] -= toTake;
            totalConsumed += toTake;
        }
        return totalConsumed;
    }

    @Override
    public int consumeHamon(int amount) {
        int totalConsumed = 0;
        while (totalConsumed < amount) {
            int richestIndex = -1;
            int maxEnergy = 0;
            
            for (int i = occupiedSlots.nextSetBit(0); i >= 0; i = occupiedSlots.nextSetBit(i + 1)) {
                if (hamonPool[i] > maxEnergy) {
                    maxEnergy = hamonPool[i];
                    richestIndex = i;
                }
            }
            
            if (richestIndex == -1) break;
            
            int toTake = Math.min(amount - totalConsumed, maxEnergy);
            hamonPool[richestIndex] -= toTake;
            totalConsumed += toTake;
        }
        return totalConsumed;
    }
    
    @Override
    public void tickRecovery() {
        if (occupiedSlots.cardinality() < MAX_MOTHS) {
            recoveryTimer++;
            if (recoveryTimer >= 100) {
                recoveryTimer = 0;
                int slot = occupiedSlots.nextClearBit(0);
                if (slot >= 0 && slot < MAX_MOTHS) {
                    occupiedSlots.set(slot);
                    kineticPool[slot] = 0;
                    hamonPool[slot] = 0;
                }
            }
        }
    }

    @Override
    public void sync(net.minecraft.entity.player.ServerPlayerEntity player) {
        if (!player.level.isClientSide) {
            com.babelmoth.rotp_ata.networking.AshesToAshesPacketHandler.sendToClient(
                new com.babelmoth.rotp_ata.networking.MothPoolSyncPacket(this.serializeNBT()), 
                player
            );
        }
    }

    @Override
    public CompoundNBT serializeNBT() {
        CompoundNBT nbt = new CompoundNBT();
        nbt.putIntArray("KineticPool", kineticPool);
        nbt.putIntArray("HamonPool", hamonPool);
        nbt.putByteArray("OccupiedSlots", occupiedSlots.toByteArray());
        nbt.putByteArray("DeployedSlots", deployedSlots.toByteArray());
        nbt.putInt("RecoveryTimer", recoveryTimer);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundNBT nbt) {
        this.kineticPool = nbt.getIntArray("KineticPool");
        this.hamonPool = nbt.getIntArray("HamonPool");

        byte[] occBytes = nbt.getByteArray("OccupiedSlots");
        if (occBytes != null && occBytes.length > 0) {
            this.occupiedSlots = BitSet.valueOf(occBytes);
        } else {
            this.occupiedSlots = new BitSet(MAX_MOTHS);
        }
        
        byte[] depBytes = nbt.getByteArray("DeployedSlots");
        if (depBytes != null && depBytes.length > 0) {
            this.deployedSlots = BitSet.valueOf(depBytes);
        } else {
            this.deployedSlots = new BitSet(MAX_MOTHS);
        }

        this.recoveryTimer = nbt.getInt("RecoveryTimer");
        
        if (this.kineticPool.length != MAX_MOTHS) this.kineticPool = new int[MAX_MOTHS];
        if (this.hamonPool.length != MAX_MOTHS) this.hamonPool = new int[MAX_MOTHS];
    }
}
