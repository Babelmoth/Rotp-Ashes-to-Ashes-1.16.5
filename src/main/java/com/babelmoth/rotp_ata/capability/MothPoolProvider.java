package com.babelmoth.rotp_ata.capability;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class MothPoolProvider implements ICapabilitySerializable<CompoundNBT> {
    @CapabilityInject(IMothPool.class)
    public static Capability<IMothPool> MOTH_POOL_CAPABILITY = null;

    private final LazyOptional<IMothPool> instance = LazyOptional.of(MothPool::new);

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        return MOTH_POOL_CAPABILITY.orEmpty(cap, instance);
    }

    @Override
    public CompoundNBT serializeNBT() {
        return (CompoundNBT) instance.orElseThrow(() -> new IllegalArgumentException("LazyOptional must not be empty"))
                .serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundNBT nbt) {
        instance.orElseThrow(() -> new IllegalArgumentException("LazyOptional must not be empty"))
                .deserializeNBT(nbt);
    }
}
