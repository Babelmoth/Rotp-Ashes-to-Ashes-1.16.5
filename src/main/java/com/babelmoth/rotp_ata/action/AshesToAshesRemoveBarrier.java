package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.block.FrozenBarrierBlockEntity;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

public class AshesToAshesRemoveBarrier extends StandAction {

    public AshesToAshesRemoveBarrier(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public TargetRequirement getTargetRequirement() {
        return TargetRequirement.ANY;
    }

    @Override
    public ActionConditionResult checkConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        MothQueryUtil.ResolvedTarget resolvedTarget = resolveTarget(user);
        if (!resolvedTarget.hasBlock()) {
            return ActionConditionResult.createNegative(new TranslationTextComponent("jojo.ata.message.need_target"));
        }
        return ActionConditionResult.POSITIVE;
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (world.isClientSide || !(user instanceof PlayerEntity)) return;

        MothQueryUtil.ResolvedTarget resolvedTarget = resolveTarget(user);
        if (!resolvedTarget.hasBlock()) return;

        BlockPos targetPos = resolvedTarget.getBlockPos();
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

    private static MothQueryUtil.ResolvedTarget resolveTarget(LivingEntity user) {
        return MothQueryUtil.resolveBlockTarget(user.level, user, AshesToAshesConstants.QUERY_RADIUS_GUARDIAN, true);
    }
}