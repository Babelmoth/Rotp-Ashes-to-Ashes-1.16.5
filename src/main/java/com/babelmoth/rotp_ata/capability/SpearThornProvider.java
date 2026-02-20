package com.babelmoth.rotp_ata.capability;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SpearThornProvider implements ICapabilitySerializable<CompoundNBT> {
    @CapabilityInject(ISpearThorn.class)
    public static Capability<ISpearThorn> SPEAR_THORN_CAPABILITY = null;

    private final LazyOptional<ISpearThorn> instance = LazyOptional.of(SpearThorn::new);

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        return SPEAR_THORN_CAPABILITY.orEmpty(cap, instance);
    }

    @Override
    public CompoundNBT serializeNBT() {
        return instance.orElseThrow(() -> new IllegalArgumentException("LazyOptional must not be empty"))
                .serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundNBT nbt) {
        instance.orElseThrow(() -> new IllegalArgumentException("LazyOptional must not be empty"))
                .deserializeNBT(nbt);
    }
}
