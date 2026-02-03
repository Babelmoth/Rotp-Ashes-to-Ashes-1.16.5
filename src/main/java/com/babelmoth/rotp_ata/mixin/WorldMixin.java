package com.babelmoth.rotp_ata.mixin;

import com.babelmoth.rotp_ata.event.AshesToAshesEventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class WorldMixin {

    // Prevent block from detecting neighbor signals
    @Inject(method = "hasNeighborSignal", at = @At("HEAD"), cancellable = true)
    public void onHasNeighborSignal(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        World level = (World) (Object) this;
        // Optimization: Use getProtectingMothCount directly
        if (AshesToAshesEventHandler.getProtectingMothCount(level, pos) > 0) {
            cir.setReturnValue(false);
        }
    }
}
