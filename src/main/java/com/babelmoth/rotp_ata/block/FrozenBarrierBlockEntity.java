package com.babelmoth.rotp_ata.block;

import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.babelmoth.rotp_ata.init.InitBlocks;
import com.babelmoth.rotp_ata.capability.MothPoolProvider;
import com.babelmoth.rotp_ata.action.AshesToAshesFrozenBarrier;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.UUID;

public class FrozenBarrierBlockEntity extends TileEntity implements ITickableTileEntity {

    private static final int MAX_KINETIC_ENERGY = 100;
    private static final double MAX_DISTANCE = 25.0;

    private UUID ownerUUID;
    private int kineticEnergy = 0;
    private long placementTick;
    private int[] mothSlots = new int[AshesToAshesFrozenBarrier.MOTHS_PER_BARRIER]; // Pool slot indices

    public FrozenBarrierBlockEntity() {
        super(InitBlocks.FROZEN_BARRIER_TILE.get());
        for (int i = 0; i < mothSlots.length; i++) {
            mothSlots[i] = -1;
        }
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
            spawnMothsAtBarrier();
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
    
    /**
     * Set the pool slot indices used by this barrier.
     */
    public void setMothSlots(int[] slots) {
        this.mothSlots = slots.clone();
        setChanged();
    }
    
    /**
     * Get the pool slot indices used by this barrier.
     */
    public int[] getMothSlots() {
        return mothSlots.clone();
    }
    
    /**
     * 在屏障位置生成三只飞蛾并分配原槽位，飞蛾会飞回主人；不召回槽位（由飞蛾接管）。
     * 在移除屏障前调用。
     */
    public void spawnMothsAtBarrier() {
        World w = level;
        if (w == null || w.isClientSide || ownerUUID == null) return;

        LivingEntity owner = w.getServer() != null ? w.getServer().getPlayerList().getPlayer(ownerUUID) : null;
        if (owner == null) return;

        double cx = worldPosition.getX() + 0.5;
        double cy = worldPosition.getY() + 0.5;
        double cz = worldPosition.getZ() + 0.5;

        for (int i = 0; i < mothSlots.length; i++) {
            int slot = mothSlots[i];
            if (slot < 0) continue;

            FossilMothEntity moth = new FossilMothEntity(w, owner);
            moth.setMothPoolIndex(slot);
            double ox = (i - 1) * 0.35;
            double oz = (i % 2 == 0 ? 0.2 : -0.2);
            moth.setPos(cx + ox, cy, cz + oz);
            w.addFreshEntity(moth);
            mothSlots[i] = -1;
        }

        owner.getCapability(MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
            if (owner instanceof net.minecraft.entity.player.ServerPlayerEntity) {
                pool.sync((net.minecraft.entity.player.ServerPlayerEntity) owner);
            }
        });
        setChanged();
    }

    private void removeBarrier() {
        if (level != null && !level.isClientSide) {
            AshesToAshesFrozenBarrier.onBarrierRemoved(ownerUUID, worldPosition);
            level.removeBlock(worldPosition, false);
        }
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
                    spawnMothsAtBarrier();
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
        nbt.putIntArray("MothSlots", mothSlots);
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
        
        if (nbt.contains("MothSlots")) {
            int[] loaded = nbt.getIntArray("MothSlots");
            if (loaded.length == mothSlots.length) {
                mothSlots = loaded;
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
