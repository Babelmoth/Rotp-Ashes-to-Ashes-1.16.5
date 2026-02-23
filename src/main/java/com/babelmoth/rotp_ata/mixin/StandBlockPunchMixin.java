package com.babelmoth.rotp_ata.mixin;

import com.babelmoth.rotp_ata.block.FrozenBarrierBlock;
import com.babelmoth.rotp_ata.block.FrozenBarrierBlockEntity;
import com.github.standobyte.jojo.action.stand.punch.StandBlockPunch;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.entity.stand.StandEntityTask;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StandBlockPunch.class, remap = false)
public class StandBlockPunchMixin {

    @Shadow(remap = false) @Final public StandEntity stand;
    @Shadow(remap = false) @Final public BlockPos blockPos;

    /**
     * 替身体术命中方块时，若目标为FrozenBarrierBlock则按攻击伤害累计动能
     */
    @Inject(method = "doHit", at = @At("HEAD"), remap = false)
    private void onDoHit(StandEntityTask task, CallbackInfoReturnable<Boolean> cir) {
        World world = stand.level;
        if (world == null || world.isClientSide) return;

        if (world.getBlockState(blockPos).getBlock() instanceof FrozenBarrierBlock) {
            TileEntity te = world.getBlockEntity(blockPos);
            if (te instanceof FrozenBarrierBlockEntity) {
                float damage = (float) stand.getAttackDamage();
                float progress = Math.max(1, Math.round(damage)) / 30.0F;
                ((FrozenBarrierBlockEntity) te).addBreakProgress(progress);
            }
        }
    }
}
