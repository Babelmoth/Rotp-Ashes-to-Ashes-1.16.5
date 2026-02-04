package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.block.FrozenBarrierBlockEntity;
import com.babelmoth.rotp_ata.init.InitBlocks;
import com.babelmoth.rotp_ata.capability.MothPoolProvider;
import com.babelmoth.rotp_ata.capability.IMothPool;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;

import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import java.util.*;

public class AshesToAshesFrozenBarrier extends StandAction {

    // Track barriers per player（无数量上限，每个屏障占用 MOTHS_PER_BARRIER 只飞蛾槽位）
    private static final Map<UUID, LinkedList<BlockPos>> PLAYER_BARRIERS = new HashMap<>();
    public static final int MOTHS_PER_BARRIER = 3;

    public AshesToAshesFrozenBarrier(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public ActionConditionResult checkConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        if (target.getType() != ActionTarget.TargetType.BLOCK) {
            return conditionMessage("block_target");
        }
        boolean hasSlots = user.getCapability(MothPoolProvider.MOTH_POOL_CAPABILITY)
            .map(pool -> pool.getTotalMoths() + MOTHS_PER_BARRIER <= IMothPool.MAX_MOTHS)
            .orElse(false);
        if (!hasSlots) {
            return ActionConditionResult.createNegative(new TranslationTextComponent("jojo.ata.message.requires_moths", MOTHS_PER_BARRIER));
        }
        return ActionConditionResult.POSITIVE;
    }

    /** 统计「放出来的飞蛾」时：世界上飞蛾数 + 每个屏障算 3 只（仅用于补位/收回判定） */
    public static int getEffectiveWorkingMothCount(LivingEntity owner) {
        if (owner == null) return 0;
        int summoned = MothQueryUtil.getOwnerMoths(owner, AshesToAshesConstants.QUERY_RADIUS_SWARM).size();
        int barriers = getPlayerBarriers(owner.getUUID()).size();
        return summoned + MOTHS_PER_BARRIER * barriers;
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (world.isClientSide || !(user instanceof PlayerEntity)) return;

        PlayerEntity player = (PlayerEntity) user;
        BlockPos targetPos = target.getBlockPos();
        if (targetPos == null) return;

        // 放置位置：与普通放方块一致，为点击面的邻格（点击顶面=上方，侧面=该侧，底面=下方）
        Direction face = target.getType() == ActionTarget.TargetType.BLOCK ? target.getFace() : Direction.UP;
        if (face == null) face = Direction.UP;
        BlockPos placePos = targetPos.relative(face);
        BlockState placeState = world.getBlockState(placePos);
        if (!placeState.isAir() && !placeState.getMaterial().isReplaceable()) {
            return;
        }

        // 分配 3 个槽位给屏障（不召回飞蛾，仅占池子槽位）
        final int[] allocatedSlots = new int[MOTHS_PER_BARRIER];
        Arrays.fill(allocatedSlots, -1);
        boolean success = user.getCapability(MothPoolProvider.MOTH_POOL_CAPABILITY).map(pool -> {
            for (int i = 0; i < MOTHS_PER_BARRIER; i++) {
                int slot = pool.allocateSlot();
                if (slot == -1) {
                    for (int j = 0; j < i; j++) {
                        pool.recallMoth(allocatedSlots[j]);
                    }
                    return false;
                }
                allocatedSlots[i] = slot;
                pool.assertDeployed(slot);
            }
            if (user instanceof net.minecraft.entity.player.ServerPlayerEntity) {
                pool.sync((net.minecraft.entity.player.ServerPlayerEntity) user);
            }
            return true;
        }).orElse(false);

        if (!success) return;

        LinkedList<BlockPos> barriers = PLAYER_BARRIERS.computeIfAbsent(player.getUUID(), k -> new LinkedList<>());

        world.setBlock(placePos, InitBlocks.FROZEN_BARRIER.get().defaultBlockState(), 3);
        TileEntity te = world.getBlockEntity(placePos);
        if (te instanceof FrozenBarrierBlockEntity) {
            FrozenBarrierBlockEntity barrier = (FrozenBarrierBlockEntity) te;
            barrier.setOwner(player);
            barrier.setMothSlots(allocatedSlots);
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
    
    // Get all barrier positions for a player
    public static List<BlockPos> getPlayerBarriers(UUID ownerUUID) {
        LinkedList<BlockPos> barriers = PLAYER_BARRIERS.get(ownerUUID);
        return barriers != null ? new ArrayList<>(barriers) : new ArrayList<>();
    }
}
