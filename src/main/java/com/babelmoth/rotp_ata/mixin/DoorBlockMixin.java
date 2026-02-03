package com.babelmoth.rotp_ata.mixin;

import com.babelmoth.rotp_ata.event.AshesToAshesEventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
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

@Mixin(DoorBlock.class)
public class DoorBlockMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    public void onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit, CallbackInfoReturnable<ActionResultType> cir) {
        // Check both halves of the door
        int count = AshesToAshesEventHandler.getProtectingMothCount(world, pos);
        
        // Debug message
        if (!world.isClientSide && player != null) {
            player.displayClientMessage(new net.minecraft.util.text.StringTextComponent("[DEBUG] Door Use - Moth Count: " + count + " at " + pos), false);
        }
        
        if (count > 0) {
            cir.setReturnValue(ActionResultType.FAIL);
        }
    }
}
