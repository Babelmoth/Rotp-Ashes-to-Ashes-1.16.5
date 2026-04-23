package com.babelmoth.rotp_ata.mixin;

import com.babelmoth.rotp_ata.adaptation.AdaptationManager;
import com.babelmoth.rotp_ata.capability.AdaptationCapProvider;
import com.babelmoth.rotp_ata.init.InitStands;
import com.github.standobyte.jojo.capability.world.TimeStopHandler;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TimeStopHandler.class)
public class TimeStopHandlerMixin {

    @Inject(method = "canPlayerMoveInStoppedTime", at = @At("RETURN"), cancellable = true, remap = false)
    private static void onCanPlayerMoveInStoppedTime(PlayerEntity player, boolean checkEffect, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;

        player.getCapability(AdaptationCapProvider.CAPABILITY).ifPresent(cap -> {
            if (cap.getTimeStopStage() >= 2) {
                cir.setReturnValue(true);
            }
        });
    }

    @Inject(method = "hasTimeStopAbility", at = @At("RETURN"), cancellable = true, remap = false)
    private static void onHasTimeStopAbility(net.minecraft.entity.LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;

        entity.getCapability(AdaptationCapProvider.CAPABILITY).ifPresent(cap -> {
            if (cap.getTimeStopStage() >= 1) {
                cir.setReturnValue(true);
            }
        });
    }

    @Inject(method = "tickInStoppedTime", at = @At("HEAD"), remap = false)
    private void onTickInStoppedTime(Entity entity, CallbackInfo ci) {
        if (entity instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) entity;
            IStandPower.getStandPowerOptional(player).ifPresent(power -> {
                if (power.getType() == InitStands.STAND_DHARMA_CHAKRA.getStandType()) {
                    AdaptationManager.onPlayerTick(player, true);
                }
            });
        }
    }
}
