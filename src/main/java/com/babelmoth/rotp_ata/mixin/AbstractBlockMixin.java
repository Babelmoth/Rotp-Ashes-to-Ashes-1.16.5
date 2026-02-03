package com.babelmoth.rotp_ata.mixin;

import com.babelmoth.rotp_ata.event.AshesToAshesEventHandler;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.class)
public class AbstractBlockMixin {

    // Block Interaction (Right Click)
    // Intercepts usage before block logic runs (Door toggle, Button press, etc.)
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    public void onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit, CallbackInfoReturnable<ActionResultType> cir) {
        if (AshesToAshesEventHandler.getProtectingMothCount(world, pos) > 0) {
            // Strictly fail interaction
            cir.setReturnValue(ActionResultType.FAIL);
        }
    }
}
