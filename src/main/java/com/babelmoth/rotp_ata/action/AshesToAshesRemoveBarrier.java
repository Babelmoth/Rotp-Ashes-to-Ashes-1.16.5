package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.block.FrozenBarrierBlock;
import com.babelmoth.rotp_ata.block.FrozenBarrierBlockEntity;
import com.babelmoth.rotp_ata.init.InitBlocks;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class AshesToAshesRemoveBarrier extends StandAction {

    public AshesToAshesRemoveBarrier(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public ActionConditionResult checkConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        if (target.getType() != ActionTarget.TargetType.BLOCK) {
            return conditionMessage("block_target");
        }
        return ActionConditionResult.POSITIVE;
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (world.isClientSide || !(user instanceof PlayerEntity)) return;

        BlockPos targetPos = target.getBlockPos();
        if (targetPos == null) return;
        
        // Check if target is our barrier
        TileEntity te = world.getBlockEntity(targetPos);
        if (te instanceof FrozenBarrierBlockEntity) {
            FrozenBarrierBlockEntity barrier = (FrozenBarrierBlockEntity) te;
            if (user.getUUID().equals(barrier.getOwnerUUID())) {
                barrier.spawnMothsAtBarrier();
                world.removeBlock(targetPos, false);
                AshesToAshesFrozenBarrier.onBarrierRemoved(user.getUUID(), targetPos);
            }
        }
    }
}
