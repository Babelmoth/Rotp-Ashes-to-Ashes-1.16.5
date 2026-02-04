package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.babelmoth.rotp_ata.block.FrozenBarrierBlockEntity;
import com.babelmoth.rotp_ata.init.InitBlocks;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.github.standobyte.jojo.util.mc.MCUtil;

import net.minecraft.entity.LivingEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.List;

public class AshesToAshesSwarmRecall extends StandAction {

    public AshesToAshesSwarmRecall(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (!world.isClientSide) {
            // Recall all attached moths (entity-attached and block-attached)
            List<FossilMothEntity> activeMoths = MCUtil.entitiesAround(
                FossilMothEntity.class, user, 256, false, 
                moth -> moth.isAlive() && moth.getOwner() == user && (moth.isAttached() || moth.isAttachedToEntity()));
            
            for (FossilMothEntity moth : activeMoths) {
                moth.recall();
            }
            
            // Also remove all barriers (releasing their slots)
            List<BlockPos> barriers = AshesToAshesFrozenBarrier.getPlayerBarriers(user.getUUID());
            for (BlockPos pos : barriers) {
                if (world.getBlockState(pos).getBlock() == InitBlocks.FROZEN_BARRIER.get()) {
                    TileEntity te = world.getBlockEntity(pos);
                    if (te instanceof FrozenBarrierBlockEntity) {
                        FrozenBarrierBlockEntity barrier = (FrozenBarrierBlockEntity) te;
                        barrier.spawnMothsAtBarrier();
                    }
                    world.removeBlock(pos, false);
                    AshesToAshesFrozenBarrier.onBarrierRemoved(user.getUUID(), pos);
                }
            }
        }
    }
}
