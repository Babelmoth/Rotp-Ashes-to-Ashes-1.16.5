package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.adaptation.AdaptationManager;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;

public class DharmaChakraTransfer extends StandAction {
    private static final float STAMINA_COST = 35.0F;

    public DharmaChakraTransfer(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public TargetRequirement getTargetRequirement() {
        return TargetRequirement.ANY;
    }

    @Override
    public ActionConditionResult checkConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        if (!(user instanceof PlayerEntity)) {
            return ActionConditionResult.NEGATIVE;
        }
        if (power.getStamina() < STAMINA_COST) {
            return ActionConditionResult.NEGATIVE;
        }
        if (target.getType() == ActionTarget.TargetType.ENTITY && target.getEntity() instanceof LivingEntity && target.getEntity() != user) {
            LivingEntity living = (LivingEntity) target.getEntity();
            if (AdaptationManager.hasBorrowedWheel(living)) {
                return ActionConditionResult.createNegative(new StringTextComponent("目标已持有法轮"));
            }
            return ActionConditionResult.POSITIVE;
        }
        if (AdaptationManager.hasTransferredWheel((PlayerEntity) user)) {
            return ActionConditionResult.POSITIVE;
        }
        return ActionConditionResult.createNegative(new StringTextComponent("需要生物目标或已外借的法轮"));
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (world.isClientSide || !(user instanceof PlayerEntity)) {
            return;
        }

        PlayerEntity player = (PlayerEntity) user;
        boolean success = false;
        if (target.getType() == ActionTarget.TargetType.ENTITY && target.getEntity() instanceof LivingEntity && target.getEntity() != user) {
            success = AdaptationManager.giveWheelToTarget(player, (LivingEntity) target.getEntity());
        }
        else {
            success = AdaptationManager.recallWheel(player);
        }

        if (success) {
            power.consumeStamina(STAMINA_COST);
        }
    }
}
