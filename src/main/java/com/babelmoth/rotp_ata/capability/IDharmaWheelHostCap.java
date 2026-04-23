package com.babelmoth.rotp_ata.capability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.UUID;

public interface IDharmaWheelHostCap extends INBTSerializable<CompoundNBT> {
    UUID getOwnerUuid();
    void setOwnerUuid(UUID uuid);

    int getOwnerEntityId();
    void setOwnerEntityId(int entityId);

    boolean hasWheel();
    void setHasWheel(boolean hasWheel);

    CompoundNBT getStoredAdaptationData();
    void setStoredAdaptationData(CompoundNBT nbt);

    long getLastSyncTick();
    void setLastSyncTick(long tick);

    LivingEntity getCachedHost();
    void setCachedHost(LivingEntity entity);
}
