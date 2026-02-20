package com.babelmoth.rotp_ata.capability;

import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.common.util.INBTSerializable;

public interface ISpearThorn extends INBTSerializable<CompoundNBT> {
    int getThornCount();
    void setThornCount(int count);
    void addThorns(int amount);

    float getDamageDealt();
    void setDamageDealt(float amount);
    void addDamageDealt(float amount);

    float getDetachThreshold();
    void setDetachThreshold(float threshold);

    boolean hasSpear();
    void setHasSpear(boolean hasSpear);

    float getLastHealth();
    void setLastHealth(float health);

    void reset();
}
