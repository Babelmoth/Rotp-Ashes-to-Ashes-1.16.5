package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.capability.IMothPool;
import com.babelmoth.rotp_ata.capability.MothPoolProvider;
import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.github.standobyte.jojo.util.mod.JojoModUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import java.util.Optional;

public class AshesToAshesMothCharge extends StandAction {
    protected static final int CHARGE_AMOUNT = Math.max(8, IMothPool.MOTH_MAX_KINETIC / 4);
    protected static final double TARGET_RANGE = AshesToAshesConstants.QUERY_RADIUS_SWARM;

    public AshesToAshesMothCharge(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public TargetRequirement getTargetRequirement() {
        return TargetRequirement.ANY;
    }

    @Override
    public ActionConditionResult checkConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        Optional<FossilMothEntity> moth = findTargetMoth(user, target);
        if (!moth.isPresent()) {
            return ActionConditionResult.createNegative(new TranslationTextComponent("jojo.ata.message.need_target"));
        }
        if (moth.get().getKineticEnergy() >= moth.get().getMaxEnergy()) {
            return ActionConditionResult.createNegative(new TranslationTextComponent("jojo.ata.message.kinetic_insufficient"));
        }
        return ActionConditionResult.POSITIVE;
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (world.isClientSide) {
            return;
        }

        Optional<FossilMothEntity> targetMoth = findTargetMoth(user, target);
        if (!targetMoth.isPresent()) {
            return;
        }

        user.getCapability(MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> chargeSingleMoth(user, pool, targetMoth.get()));
    }

    protected void chargeSingleMoth(LivingEntity user, IMothPool pool, FossilMothEntity moth) {
        int room = Math.max(0, moth.getMaxEnergy() - moth.getKineticEnergy());
        if (room <= 0) {
            return;
        }

        int transfer = Math.min(CHARGE_AMOUNT, room);
        if (pool.getAvailableKinetic() < transfer) {
            notifyInsufficient(user);
            return;
        }

        int slot = moth.getMothPoolIndex();
        int taken = pool.consumeKineticExcludingSlot(transfer, slot);
        if (taken <= 0) {
            notifyInsufficient(user);
            return;
        }

        int newEnergy = Math.min(moth.getMaxEnergy(), moth.getKineticEnergy() + taken);
        moth.setKineticEnergy(newEnergy);
        if (slot >= 0 && pool.isSlotActive(slot)) {
            pool.setMothKinetic(slot, newEnergy);
        }
        if (user instanceof ServerPlayerEntity) {
            pool.sync((ServerPlayerEntity) user);
        }
    }

    protected static Optional<FossilMothEntity> findTargetMoth(LivingEntity user, ActionTarget target) {
        if (target.getType() == ActionTarget.TargetType.ENTITY && target.getEntity() instanceof FossilMothEntity) {
            FossilMothEntity moth = (FossilMothEntity) target.getEntity();
            if (moth.isAlive() && moth.getOwner() == user) {
                return Optional.of(moth);
            }
        }

        Entity viewCenter = MothQueryUtil.getViewpointCenter(user);
        RayTraceResult rtResult = JojoModUtil.rayTrace(viewCenter, TARGET_RANGE,
                entity -> entity instanceof FossilMothEntity
                        && entity.isAlive()
                        && ((FossilMothEntity) entity).getOwner() == user);
        if (rtResult instanceof EntityRayTraceResult) {
            Entity entity = ((EntityRayTraceResult) rtResult).getEntity();
            if (entity instanceof FossilMothEntity) {
                return Optional.of((FossilMothEntity) entity);
            }
        }
        return Optional.empty();
    }

    protected static void notifyInsufficient(LivingEntity user) {
        if (user instanceof ServerPlayerEntity) {
            ((ServerPlayerEntity) user).displayClientMessage(
                    new TranslationTextComponent("jojo.ata.message.kinetic_insufficient"), true);
        }
    }
}
