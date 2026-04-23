package com.babelmoth.rotp_ata.capability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.CompoundNBT;

import java.util.UUID;

public class DharmaWheelHostCap implements IDharmaWheelHostCap {
    private UUID ownerUuid;
    private int ownerEntityId = -1;
    private boolean hasWheel;
    private CompoundNBT storedAdaptationData = new CompoundNBT();
    private long lastSyncTick;
    private LivingEntity cachedHost;

    @Override
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    @Override
    public void setOwnerUuid(UUID uuid) {
        this.ownerUuid = uuid;
    }

    @Override
    public int getOwnerEntityId() {
        return ownerEntityId;
    }

    @Override
    public void setOwnerEntityId(int entityId) {
        this.ownerEntityId = entityId;
    }

    @Override
    public boolean hasWheel() {
        return hasWheel;
    }

    @Override
    public void setHasWheel(boolean hasWheel) {
        this.hasWheel = hasWheel;
    }

    @Override
    public CompoundNBT getStoredAdaptationData() {
        return storedAdaptationData == null ? new CompoundNBT() : storedAdaptationData.copy();
    }

    @Override
    public void setStoredAdaptationData(CompoundNBT nbt) {
        this.storedAdaptationData = nbt == null ? new CompoundNBT() : nbt.copy();
    }

    @Override
    public long getLastSyncTick() {
        return lastSyncTick;
    }

    @Override
    public void setLastSyncTick(long tick) {
        this.lastSyncTick = tick;
    }

    @Override
    public LivingEntity getCachedHost() {
        return cachedHost;
    }

    @Override
    public void setCachedHost(LivingEntity entity) {
        this.cachedHost = entity;
    }

    @Override
    public CompoundNBT serializeNBT() {
        CompoundNBT nbt = new CompoundNBT();
        if (ownerUuid != null) {
            nbt.putUUID("owner_uuid", ownerUuid);
        }
        nbt.putInt("owner_id", ownerEntityId);
        nbt.putBoolean("has_wheel", hasWheel);
        nbt.put("adaptation", getStoredAdaptationData());
        nbt.putLong("last_sync", lastSyncTick);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundNBT nbt) {
        ownerUuid = nbt.hasUUID("owner_uuid") ? nbt.getUUID("owner_uuid") : null;
        ownerEntityId = nbt.getInt("owner_id");
        hasWheel = nbt.getBoolean("has_wheel");
        storedAdaptationData = nbt.contains("adaptation") ? nbt.getCompound("adaptation").copy() : new CompoundNBT();
        lastSyncTick = nbt.getLong("last_sync");
    }
}
