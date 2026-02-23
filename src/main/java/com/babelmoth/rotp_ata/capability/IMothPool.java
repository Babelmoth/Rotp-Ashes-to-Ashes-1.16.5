package com.babelmoth.rotp_ata.capability;

import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.common.util.INBTSerializable;

public interface IMothPool extends INBTSerializable<CompoundNBT> {
    int MAX_MOTHS = 500;
    int MOTH_MAX_KINETIC = 10;

    int[] getKineticPool();
    int[] getHamonPool();
    void setKineticPool(int[] pool);
    void setHamonPool(int[] pool);

    int getTotalMoths();
    void setTotalMoths(int count);

    int getMothKinetic(int index);
    void setMothKinetic(int index, int amount);
    int getMothHamon(int index);
    void setMothHamon(int index, int amount);

    int getTotalKineticEnergy();

    int getAvailableKinetic();
    int getTotalHamonEnergy();

    int allocateSlot();
    int allocateSlotWithPriority(boolean emptyFirst);
    void recallMoth(int index);
    void killMoth(int index);
    boolean isSlotActive(int index);
    void assertDeployed(int index);
    void clearAllDeployed();

    boolean consumeMoths(int amount);

    int consumeKinetic(int amount);

    int consumeKineticExcludingSlot(int amount, int excludeSlot);
    int consumeHamon(int amount);

    void tickRecovery();

    int getDeployedCount();

    int getOrbitMothCount();
    void setOrbitMothCount(int count);
    int getShieldMothCount();
    void setShieldMothCount(int count);
    int getSwarmAttackCount();
    void setSwarmAttackCount(int count);
    boolean isBarrierPassthrough();
    void setBarrierPassthrough(boolean passthrough);
    boolean isAutoChargeShield();
    void setAutoChargeShield(boolean autoCharge);
    boolean isRemoteFollow();
    void setRemoteFollow(boolean remoteFollow);
    int getRemoteFollowRatio();
    void setRemoteFollowRatio(int ratio);

    void sync(net.minecraft.entity.player.ServerPlayerEntity player);
}
