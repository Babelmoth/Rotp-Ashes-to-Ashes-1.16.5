package com.babelmoth.rotp_ata.block;

import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.babelmoth.rotp_ata.init.InitBlocks;
import com.babelmoth.rotp_ata.init.InitStands;
import com.babelmoth.rotp_ata.capability.MothPoolProvider;
import com.babelmoth.rotp_ata.action.AshesToAshesFrozenBarrier;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.UUID;

public class FrozenBarrierBlockEntity extends TileEntity implements ITickableTileEntity {

    // 模拟钻石硬度(5.0F)的每tick破坏进度，无视工具属性 (1.0 / 5.0 / 30.0)
    public static final float PROGRESS_PER_TICK = 1.0F / 5.0F / 30.0F;
    private static final double MAX_DISTANCE = 25.0;
    // 用于 destroyBlockProgress 的唯一ID
    private int destroyStageId = -1;

    private UUID ownerUUID;
    private float breakProgress = 0.0F; // 0.0 ~ 1.0
    private int lastSentStage = -1; // 上次发送的裂纹阶段
    private boolean mothsReleased = false; // 防止双重生成飞蛾
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
        this.destroyStageId = worldPosition.hashCode();
        setChanged();
    }

    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    /**
     * 增加破坏进度。进度持久化，停止破坏后不会重置。
     * 达到100%时生成三只满动能飞蛾并移除屏障。
     */
    public void addBreakProgress(float amount) {
        this.breakProgress = Math.min(1.0F, this.breakProgress + amount);
        syncAndUpdateStage();
        if (this.breakProgress >= 1.0F) {
            spawnMothsAtBarrier();
            removeBarrier();
        }
    }

    public float getBreakProgress() {
        return breakProgress;
    }

    public long getPlacementTick() {
        return placementTick;
    }
    
    /** Set the pool slot indices used by this barrier. */
    public void setMothSlots(int[] slots) {
        this.mothSlots = slots.clone();
        setChanged();
    }
    
    /** Get the pool slot indices used by this barrier. */
    public int[] getMothSlots() {
        return mothSlots.clone();
    }
    
    /**
     * 根据当前破坏进度生成飞蛾，动能平均分配到三只蛾子身上。
     * 如果主人未完全召唤替身，飞蛾直接消失（仅回收模槽位）。
     */
    public void spawnMothsAtBarrier() {
        if (mothsReleased) return;
        mothsReleased = true;
        World w = level;
        if (w == null || w.isClientSide || ownerUUID == null) return;

        LivingEntity owner = w.getServer() != null ? w.getServer().getPlayerList().getPlayer(ownerUUID) : null;
        if (owner == null) return;

        // 检查替身是否完全召唤（manifestation是StandEntity）
        boolean standFullySummoned = IStandPower.getStandPowerOptional(owner).map(power ->
                power.getType() == InitStands.STAND_ASHES_TO_ASHES.getStandType()
                        && power.isActive()
                        && power.getStandManifestation() instanceof StandEntity
        ).orElse(false);

        // 根据当前破坏进度计算每只飞蛾分配到的动能
        int maxPerMoth = com.babelmoth.rotp_ata.util.AshesToAshesConstants.MAX_ENERGY_BASE;
        int energyPerMoth = Math.round(breakProgress * maxPerMoth);

        double cx = worldPosition.getX() + 0.5;
        double cy = worldPosition.getY() + 0.5;
        double cz = worldPosition.getZ() + 0.5;

        for (int i = 0; i < mothSlots.length; i++) {
            int slot = mothSlots[i];
            if (slot < 0) continue;

            if (standFullySummoned) {
                // 替身已完全召唤：生成飞蛾，动能按破坏进度分配
                FossilMothEntity moth = new FossilMothEntity(w, owner);
                moth.setMothPoolIndex(slot);
                moth.setKineticEnergy(energyPerMoth);
                double ox = (i - 1) * 0.35;
                double oz = (i % 2 == 0 ? 0.2 : -0.2);
                moth.setPos(cx + ox, cy, cz + oz);
                w.addFreshEntity(moth);
            }
            // 未完全召唤：不生成飞蛾，仅回收模槽位
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
            // 清除裂纹覆盖层
            clearDestroyStage();
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

        // 实体站在屏障上时积蓄动能：每2tick积蓄1点（1/30 breakProgress）
        if (level.getGameTime() % 2 == 0) {
            net.minecraft.util.math.AxisAlignedBB aboveBox = new net.minecraft.util.math.AxisAlignedBB(
                    worldPosition.getX(), worldPosition.getY() + 1.0, worldPosition.getZ(),
                    worldPosition.getX() + 1.0, worldPosition.getY() + 1.5, worldPosition.getZ() + 1.0);
            java.util.List<LivingEntity> entities =
                    level.getEntitiesOfClass(LivingEntity.class, aboveBox,
                            e -> e.isAlive()
                                    && !(e instanceof FossilMothEntity)
                                    && !(e instanceof com.github.standobyte.jojo.entity.stand.StandEntity));
            if (!entities.isEmpty()) {
                addBreakProgress(entities.size() / 30.0F);
            }
        }

        // 定期重发裂纹覆盖层（防止超时消失）
        if (breakProgress > 0.0F && level.getGameTime() % 10 == 0) {
            sendDestroyStage();
        }
    }

    /**
     * 外部破坏时调用（替身、爆炸等通过Block.onRemove触发）。
     * 确保生成飞蛾并清理屏障状态。
     */
    public void onBarrierDestroyed() {
        if (level != null && !level.isClientSide) {
            spawnMothsAtBarrier();
            clearDestroyStage();
            AshesToAshesFrozenBarrier.onBarrierRemoved(ownerUUID, worldPosition);
        }
    }

    /**
     * 发送裂纹覆盖层到客户端，显示当前破坏进度。
     */
    private void sendDestroyStage() {
        if (level instanceof ServerWorld) {
            int stage = (int)(breakProgress * 10.0F) - 1; // -1 ~ 9
            if (stage != lastSentStage) {
                ((ServerWorld) level).destroyBlockProgress(getDestroyStageId(), worldPosition, stage);
                lastSentStage = stage;
            } else if (stage >= 0) {
                // 重发相同阶段以防止超时
                ((ServerWorld) level).destroyBlockProgress(getDestroyStageId(), worldPosition, stage);
            }
        }
    }

    private void clearDestroyStage() {
        if (level instanceof ServerWorld) {
            ((ServerWorld) level).destroyBlockProgress(getDestroyStageId(), worldPosition, -1);
            lastSentStage = -1;
        }
    }

    private int getDestroyStageId() {
        if (destroyStageId == -1) {
            destroyStageId = worldPosition.hashCode();
        }
        return destroyStageId;
    }

    private void syncAndUpdateStage() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            sendDestroyStage();
        }
    }

    @Override
    public CompoundNBT save(CompoundNBT nbt) {
        super.save(nbt);
        if (ownerUUID != null) {
            nbt.putUUID("Owner", ownerUUID);
        }
        nbt.putFloat("BreakProgress", breakProgress);
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
        breakProgress = nbt.getFloat("BreakProgress");
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
