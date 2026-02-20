package com.babelmoth.rotp_ata.capability;

import net.minecraft.nbt.CompoundNBT;

public class SpearStuck implements ISpearStuck {
    private int spearCount = 0;

    @Override
    public int getSpearCount() {
        return spearCount;
    }

    @Override
    public void setSpearCount(int count) {
        this.spearCount = Math.max(0, count);
    }

    @Override
    public void increment() {
        setSpearCount(spearCount + 1);
    }

    @Override
    public void decrement() {
        setSpearCount(spearCount - 1);
    }

    @Override
    public CompoundNBT serializeNBT() {
        CompoundNBT nbt = new CompoundNBT();
        nbt.putInt("SpearCount", spearCount);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundNBT nbt) {
        spearCount = nbt.getInt("SpearCount");
    }
}
