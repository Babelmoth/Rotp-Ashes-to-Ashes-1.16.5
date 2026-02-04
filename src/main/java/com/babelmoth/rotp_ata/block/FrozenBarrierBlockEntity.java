package com.babelmoth.rotp_ata.block;

import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.babelmoth.rotp_ata.init.InitBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FrozenBarrierBlockEntity extends TileEntity implements ITickableTileEntity {

    private static final int MAX_KINETIC_ENERGY = 100;
    private static final double MAX_DISTANCE = 25.0;
    private static final int MOTHS_PER_BARRIER = 3;

    private UUID ownerUUID;
    private int kineticEnergy = 0;
    private long placementTick;
    private List<Integer> mothIds = new ArrayList<>(); // Store moth entity IDs

    public FrozenBarrierBlockEntity() {
        super(InitBlocks.FROZEN_BARRIER_TILE.get());
    }

    public void setOwner(PlayerEntity player) {
        this.ownerUUID = player.getUUID();
        this.placementTick = level != null ? level.getGameTime() : 0;
        setChanged();
    }

    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void addKineticEnergy(int amount) {
        this.kineticEnergy += amount;
        if (this.kineticEnergy >= MAX_KINETIC_ENERGY) {
            removeBarrier();
        }
        setChanged();
    }

    public int getKineticEnergy() {
        return kineticEnergy;
    }

    public long getPlacementTick() {
        return placementTick;
    }

    public List<Integer> getMothIds() {
        return mothIds;
    }
    
    public void setMothIds(List<Integer> ids) {
        this.mothIds = ids != null ? new ArrayList<>(ids) : new ArrayList<>();
        setChanged();
    }
    
    private void removeBarrier() {
        if (level != null && !level.isClientSide) {
            // Release moths when barrier is removed
            releaseMoths();
            level.removeBlock(worldPosition, false);
        }
    }
    
    public void releaseMoths() {
        if (level == null || level.isClientSide) return;
        
        for (Integer mothId : new ArrayList<>(mothIds)) {
            net.minecraft.entity.Entity entity = level.getEntity(mothId);
            if (entity instanceof FossilMothEntity) {
                FossilMothEntity moth = (FossilMothEntity) entity;
                // Detach from barrier
                moth.detach();
                // Make moth fly back to owner
                if (moth.getOwner() != null) {
                    moth.recall();
                }
            }
        }
        mothIds.clear();
        setChanged();
    }

    @Override
    public void tick() {
        if (level == null || level.isClientSide) return;

        // Check distance to owner
        if (ownerUUID != null) {
            PlayerEntity owner = level.getServer().getPlayerList().getPlayer(ownerUUID);
            if (owner != null) {
                double distance = owner.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
                if (distance > MAX_DISTANCE * MAX_DISTANCE) {
                    removeBarrier();
                    return;
                }
            }
        }
    }

    @Override
    public CompoundNBT save(CompoundNBT nbt) {
        super.save(nbt);
        if (ownerUUID != null) {
            nbt.putUUID("Owner", ownerUUID);
        }
        nbt.putInt("KineticEnergy", kineticEnergy);
        nbt.putLong("PlacementTick", placementTick);
        int[] mothIdsArray = mothIds.stream().mapToInt(Integer::intValue).toArray();
        nbt.putIntArray("MothIds", mothIdsArray);
        return nbt;
    }

    @Override
    public void load(BlockState state, CompoundNBT nbt) {
        super.load(state, nbt);
        if (nbt.hasUUID("Owner")) {
            ownerUUID = nbt.getUUID("Owner");
        }
        kineticEnergy = nbt.getInt("KineticEnergy");
        placementTick = nbt.getLong("PlacementTick");
        mothIds.clear();
        if (nbt.contains("MothIds", 11)) { // 11 = IntArrayNBT type
            int[] mothIdsArray = nbt.getIntArray("MothIds");
            for (int id : mothIdsArray) {
                mothIds.add(id);
            }
        }
    }

    @Nullable
    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        return new SUpdateTileEntityPacket(worldPosition, 1, getUpdateTag());
    }

    @Override
    public CompoundNBT getUpdateTag() {
        return save(new CompoundNBT());
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
        load(getBlockState(), pkt.getTag());
    }
}
