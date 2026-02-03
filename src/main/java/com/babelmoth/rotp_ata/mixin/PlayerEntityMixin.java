package com.babelmoth.rotp_ata.mixin;

import com.babelmoth.rotp_ata.event.AshesToAshesEventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {

    // Dig Speed / Harvest Speed
    // getDigSpeed is a Forge added method, so we might need remap=false or exact signature
    @Inject(method = "getDigSpeed(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)F", at = @At("RETURN"), cancellable = true, remap = false)
    public void onGetDigSpeed(BlockState state, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        // getDigSpeed takes state/pos, need to access world. Player has level.
        int count = AshesToAshesEventHandler.getProtectingMothCount(player.level, pos);
        
        if (count > 0) {
            float originalSpeed = cir.getReturnValue();
            if (count >= 5) {
                cir.setReturnValue(0.0f); // Unbreakable
            } else {
                float reduction = 0.2f * count;
                cir.setReturnValue(originalSpeed * (1.0f - reduction));
            }
        }
    }
}
