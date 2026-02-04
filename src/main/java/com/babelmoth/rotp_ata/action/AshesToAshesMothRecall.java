package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.block.FrozenBarrierBlockEntity;
import com.babelmoth.rotp_ata.init.InitBlocks;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;

import net.minecraft.entity.LivingEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.ArrayList;
import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;

public class AshesToAshesMothRecall extends StandAction {

    public AshesToAshesMothRecall(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (!world.isClientSide) {
            // Get attached moths (entity-attached)
            List<FossilMothEntity> attachedMoths = MothQueryUtil.getAttachedMoths(user, AshesToAshesConstants.QUERY_RADIUS_GUARDIAN);
            
            // Get block-attached moths
            List<FossilMothEntity> blockMoths = MothQueryUtil.getOwnerMoths(user, AshesToAshesConstants.QUERY_RADIUS_GUARDIAN);
            blockMoths.removeIf(m -> !m.isAttached());
            
            ArrayList<FossilMothEntity> activeMoths = new ArrayList<>(attachedMoths);
            activeMoths.addAll(blockMoths);
            
            // Find the furthest attached moth
            FossilMothEntity furthestMoth = null;
            double maxDist = 0;
            for (FossilMothEntity moth : activeMoths) {
                double dist = moth.distanceToSqr(user);
                if (dist > maxDist) {
                    maxDist = dist;
                    furthestMoth = moth;
                }
            }
            
            // Also check barriers - find the furthest barrier
            List<BlockPos> barriers = AshesToAshesFrozenBarrier.getPlayerBarriers(user.getUUID());
            BlockPos furthestBarrier = null;
            for (BlockPos pos : barriers) {
                if (world.getBlockState(pos).getBlock() == InitBlocks.FROZEN_BARRIER.get()) {
                    double dist = user.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (dist > maxDist) {
                        maxDist = dist;
                        furthestBarrier = pos;
                        furthestMoth = null; // Barrier is further
                    }
                }
            }
            
            // Recall the furthest one
            if (furthestMoth != null) {
                furthestMoth.recall();
            } else if (furthestBarrier != null) {
                TileEntity te = world.getBlockEntity(furthestBarrier);
                if (te instanceof FrozenBarrierBlockEntity) {
                    FrozenBarrierBlockEntity barrier = (FrozenBarrierBlockEntity) te;
                    barrier.spawnMothsAtBarrier();
                }
                world.removeBlock(furthestBarrier, false);
                AshesToAshesFrozenBarrier.onBarrierRemoved(user.getUUID(), furthestBarrier);
            }
        }
    }
}
