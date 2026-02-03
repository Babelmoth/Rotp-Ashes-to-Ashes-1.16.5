package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.block.FrozenBarrierBlockEntity;
import com.babelmoth.rotp_ata.init.InitBlocks;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;

import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

import java.util.*;

public class AshesToAshesFrozenBarrier extends StandAction {

    // Track barriers per player (max 5)
    private static final Map<UUID, LinkedList<BlockPos>> PLAYER_BARRIERS = new HashMap<>();
    private static final int MAX_BARRIERS = 5;

    public AshesToAshesFrozenBarrier(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public ActionConditionResult checkSpecificConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        if (target.getType() != ActionTarget.TargetType.BLOCK) {
            return conditionMessage("block_target");
        }
        return ActionConditionResult.POSITIVE;
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (world.isClientSide || !(user instanceof PlayerEntity)) return;

        PlayerEntity player = (PlayerEntity) user;
        BlockPos targetPos = target.getBlockPos();
        if (targetPos == null) return;

        // Place above the target block
        BlockPos placePos = targetPos.above();
        if (!world.getBlockState(placePos).isAir()) {
            return; // Can't place if not air
        }

        // Check and remove oldest if over limit
        LinkedList<BlockPos> barriers = PLAYER_BARRIERS.computeIfAbsent(player.getUUID(), k -> new LinkedList<>());
        while (barriers.size() >= MAX_BARRIERS) {
            BlockPos oldest = barriers.removeFirst();
            if (world.getBlockState(oldest).getBlock() == InitBlocks.FROZEN_BARRIER.get()) {
                world.removeBlock(oldest, false);
            }
        }

        // Place new barrier
        BlockState barrierState = InitBlocks.FROZEN_BARRIER.get().defaultBlockState();
        world.setBlock(placePos, barrierState, 3);

        // Set owner
        TileEntity te = world.getBlockEntity(placePos);
        if (te instanceof FrozenBarrierBlockEntity) {
            ((FrozenBarrierBlockEntity) te).setOwner(player);
        }

        barriers.add(placePos);
    }

    // Called when barrier is removed to update tracking
    public static void onBarrierRemoved(UUID ownerUUID, BlockPos pos) {
        LinkedList<BlockPos> barriers = PLAYER_BARRIERS.get(ownerUUID);
        if (barriers != null) {
            barriers.remove(pos);
        }
    }
}
