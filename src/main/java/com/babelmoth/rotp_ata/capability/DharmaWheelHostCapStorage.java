package com.babelmoth.rotp_ata.capability;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nullable;

public class DharmaWheelHostCapStorage implements Capability.IStorage<IDharmaWheelHostCap> {
    @Nullable
    @Override
    public INBT writeNBT(Capability<IDharmaWheelHostCap> capability, IDharmaWheelHostCap instance, Direction side) {
        return instance.serializeNBT();
    }

    @Override
    public void readNBT(Capability<IDharmaWheelHostCap> capability, IDharmaWheelHostCap instance, Direction side, INBT nbt) {
        if (nbt instanceof CompoundNBT) {
            instance.deserializeNBT((CompoundNBT) nbt);
        }
    }
}
