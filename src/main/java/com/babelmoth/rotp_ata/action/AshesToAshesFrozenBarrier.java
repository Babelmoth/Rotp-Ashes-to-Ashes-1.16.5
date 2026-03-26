package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.block.FrozenBarrierBlockEntity;
import com.babelmoth.rotp_ata.capability.MothPoolProvider;
import com.babelmoth.rotp_ata.init.InitBlocks;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AshesToAshesFrozenBarrier extends StandAction {

    private static final Map<UUID, LinkedList<BlockPos>> PLAYER_BARRIERS = new HashMap<>();
    public static final int MOTHS_PER_BARRIER = 3;

    public AshesToAshesFrozenBarrier(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public TargetRequirement getTargetRequirement() {
        return TargetRequirement.ANY;
    }

    @Override
    public ActionConditionResult checkConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        boolean hasSlots = user.getCapability(MothPoolProvider.MOTH_POOL_CAPABILITY)
            .map(pool -> pool.getAvailableSlotCount() >= MOTHS_PER_BARRIER)
            .orElse(false);
        if (!hasSlots) {
            return ActionConditionResult.createNegative(new TranslationTextComponent("jojo.ata.message.requires_moths", MOTHS_PER_BARRIER));
        }
        if (resolvePlacementTarget(user).isEmpty()) {
            return ActionConditionResult.createNegative(new TranslationTextComponent("jojo.ata.message.need_target"));
        }
        return ActionConditionResult.POSITIVE;
    }

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
        MothQueryUtil.ResolvedTarget resolvedTarget = resolvePlacementTarget(user);
        if (!resolvedTarget.hasBlock()) return;

        BlockPos targetPos = resolvedTarget.getBlockPos();
        Direction face = resolvedTarget.getFace() != null ? resolvedTarget.getFace() : Direction.UP;
        BlockPos placePos = targetPos.relative(face);
        BlockState placeState = world.getBlockState(placePos);
        if (!placeState.isAir() && !placeState.getMaterial().isReplaceable()) {
            return;
        }

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

    private static MothQueryUtil.ResolvedTarget resolvePlacementTarget(LivingEntity user) {
        return MothQueryUtil.resolveBlockTarget(user.level, user, AshesToAshesConstants.QUERY_RADIUS_GUARDIAN, true);
    }

    public static void onBarrierRemoved(UUID ownerUUID, BlockPos pos) {
        LinkedList<BlockPos> barriers = PLAYER_BARRIERS.get(ownerUUID);
        if (barriers != null) {
            barriers.remove(pos);
        }
    }

    public static List<BlockPos> getPlayerBarriers(UUID ownerUUID) {
        LinkedList<BlockPos> barriers = PLAYER_BARRIERS.get(ownerUUID);
        return barriers != null ? new ArrayList<>(barriers) : new ArrayList<>();
    }
}