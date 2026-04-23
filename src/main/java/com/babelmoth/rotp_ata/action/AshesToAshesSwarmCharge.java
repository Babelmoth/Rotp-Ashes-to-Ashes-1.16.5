package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.capability.IMothPool;
import com.babelmoth.rotp_ata.capability.MothPoolProvider;
import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class AshesToAshesSwarmCharge extends AshesToAshesMothCharge {
    private static final double GROUP_RADIUS = 6.0D;

    public AshesToAshesSwarmCharge(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public ActionConditionResult checkConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        Optional<FossilMothEntity> moth = findTargetMoth(user, target);
        if (!moth.isPresent()) {
            return ActionConditionResult.createNegative(new net.minecraft.util.text.TranslationTextComponent("jojo.ata.message.need_target"));
        }
        return ActionConditionResult.POSITIVE;
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (world.isClientSide) {
            return;
        }

        Optional<FossilMothEntity> centerMoth = findTargetMoth(user, target);
        if (!centerMoth.isPresent()) {
            return;
        }

        user.getCapability(MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> chargeSwarm(user, pool, centerMoth.get()));
    }

    private void chargeSwarm(LivingEntity user, IMothPool pool, FossilMothEntity centerMoth) {
        List<FossilMothEntity> targets = centerMoth.level.getEntitiesOfClass(
                FossilMothEntity.class,
                centerMoth.getBoundingBox().inflate(GROUP_RADIUS),
                moth -> moth.isAlive() && moth.getOwner() == user);
        targets.sort(Comparator.comparingInt(FossilMothEntity::getKineticEnergy).thenComparingDouble(moth -> moth.distanceToSqr(centerMoth)));

        List<FossilMothEntity> chargeable = new ArrayList<>();
        int totalRoom = 0;
        for (FossilMothEntity moth : targets) {
            int room = Math.max(0, moth.getMaxEnergy() - moth.getKineticEnergy());
            if (room > 0) {
                chargeable.add(moth);
                totalRoom += room;
            }
        }

        if (chargeable.isEmpty()) {
            return;
        }

        int transferBudget = Math.min(CHARGE_AMOUNT, totalRoom);
        if (pool.getAvailableKinetic() < transferBudget) {
            notifyInsufficient(user);
            return;
        }

        int taken = pool.consumeKinetic(transferBudget);
        if (taken <= 0) {
            notifyInsufficient(user);
            return;
        }

        int index = 0;
        while (taken > 0 && !chargeable.isEmpty()) {
            if (index >= chargeable.size()) {
                index = 0;
            }
            FossilMothEntity moth = chargeable.get(index);
            if (moth.getKineticEnergy() >= moth.getMaxEnergy()) {
                chargeable.remove(index);
                continue;
            }
            int newEnergy = Math.min(moth.getMaxEnergy(), moth.getKineticEnergy() + 1);
            moth.setKineticEnergy(newEnergy);
            int slot = moth.getMothPoolIndex();
            if (slot >= 0 && pool.isSlotActive(slot)) {
                pool.setMothKinetic(slot, newEnergy);
            }
            taken--;
            index++;
        }

        if (user instanceof ServerPlayerEntity) {
            pool.sync((ServerPlayerEntity) user);
        }
    }
}
