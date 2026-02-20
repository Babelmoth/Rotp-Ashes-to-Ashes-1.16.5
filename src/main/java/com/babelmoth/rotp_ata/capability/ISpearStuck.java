package com.babelmoth.rotp_ata.capability;

import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.common.util.INBTSerializable;

public interface ISpearStuck extends INBTSerializable<CompoundNBT> {
    int getSpearCount();
    void setSpearCount(int count);
    void increment();
    void decrement();
}
