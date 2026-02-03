package com.babelmoth.rotp_ata.mixin;

import com.babelmoth.rotp_ata.util.ProtectedBlockRegistry;
import net.minecraft.block.AbstractPressurePlateBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractPressurePlateBlock.class)
public class PressurePlateBlockMixin {

    // Intercept getSignalStrength - this is called to calculate the signal output
    // If protected by moths, return 0 (no signal = plate not activated)
    @Inject(method = "getSignalStrength", at = @At("HEAD"), cancellable = true)
    private void onGetSignalStrength(World world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (ProtectedBlockRegistry.isProtected(world, pos)) {
            cir.setReturnValue(0); // No signal = plate ignores entities
        }
    }
}
