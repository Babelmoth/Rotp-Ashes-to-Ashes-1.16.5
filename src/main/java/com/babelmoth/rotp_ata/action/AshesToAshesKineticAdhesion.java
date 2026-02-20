package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.capability.IMothPool;
import com.babelmoth.rotp_ata.capability.MothPoolProvider;
import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

/**
 * Kinetic adhesion: like normal adhesion but charges the placed/selected moth from the pool's available kinetic.
 * Shift variant of adhesion with its own icon and name.
 */
public class AshesToAshesKineticAdhesion extends StandAction {

    public AshesToAshesKineticAdhesion(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public TargetRequirement getTargetRequirement() {
        return TargetRequirement.ANY;
    }

    @Override
    public ActionConditionResult checkConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        // Require a block or entity target
        if (target.getType() == ActionTarget.TargetType.BLOCK) {
            return ActionConditionResult.POSITIVE;
        }
        if (target.getType() == ActionTarget.TargetType.ENTITY) {
            // Do not attach to fossil moths
            if (target.getEntity() instanceof FossilMothEntity) {
                return ActionConditionResult.NEGATIVE;
            }
            return ActionConditionResult.POSITIVE;
        }
        return ActionConditionResult.createNegative(new TranslationTextComponent("jojo.ata.message.need_target"));
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (world.isClientSide) return;

        java.util.Optional<IMothPool> poolOpt = user.getCapability(MothPoolProvider.MOTH_POOL_CAPABILITY).resolve();
        if (!poolOpt.isPresent()) return;

        IMothPool pool = poolOpt.get();

        // 1. Use only moths with room for more kinetic; prefer those with more energy
        java.util.List<FossilMothEntity> freeMoths = MothQueryUtil.getFreeMoths(user, 64.0);
        freeMoths.removeIf(m -> {
            int idx = m.getMothPoolIndex();
            int current = (idx >= 0 && pool.isSlotActive(idx)) ? pool.getMothKinetic(idx) : m.getKineticEnergy();
            return current >= m.getMaxEnergy(); // Full moths are not used for kinetic adhesion
        });
        freeMoths.sort(java.util.Comparator.<FossilMothEntity>comparingInt(m -> {
            int idx = m.getMothPoolIndex();
            return (idx >= 0 && pool.isSlotActive(idx)) ? pool.getMothKinetic(idx) : m.getKineticEnergy();
        }).reversed());

        FossilMothEntity activeMoth = null;
        boolean isNewMoth = false;

        if (!freeMoths.isEmpty()) {
            activeMoth = freeMoths.get(0);
        } else {
            // New moth: allocate slot before charging so we don't reuse an undrained slot and create extra kinetic on sync
            if (pool.getTotalMoths() < IMothPool.MAX_MOTHS) {
                int slot = pool.allocateSlotWithPriority(true);
                if (slot < 0) return;
                activeMoth = new FossilMothEntity(world, user);
                activeMoth.setMothPoolIndex(slot);
                isNewMoth = true;
            }
        }

        if (activeMoth == null) {
            return;
        }

        int excludeSlot = activeMoth.getMothPoolIndex();
        // When slot is assigned, use pool value as source of truth for consistency with consumeExcluding
        int currentEnergy = (excludeSlot >= 0 && pool.isSlotActive(excludeSlot))
            ? pool.getMothKinetic(excludeSlot)
            : activeMoth.getKineticEnergy();
        int roomLocal = Math.max(0, activeMoth.getMaxEnergy() - currentEnergy);
        // Only count available (non-deployed) kinetic; deployed moths are occupied and not used for charging
        int available = pool.getAvailableKinetic();

        // 2. Pre-charge check: insufficient available kinetic -> message and cancel placement
        if (roomLocal > 0 && available < roomLocal) {
            if (user instanceof ServerPlayerEntity) {
                ((ServerPlayerEntity) user).displayClientMessage(
                    new TranslationTextComponent("jojo.ata.message.kinetic_insufficient"), true);
            }
            if (isNewMoth) {
                pool.recallMoth(excludeSlot);
            }
            return;
        }

        // 3. Attach
        if (target.getType() == ActionTarget.TargetType.BLOCK) {
            activeMoth.attachTo(target.getBlockPos(), target.getFace());
        } else if (target.getType() == ActionTarget.TargetType.ENTITY) {
            activeMoth.attachToEntity(target.getEntity());
        }

        // 4. Consume kinetic from other slots and charge this moth; write back to pool immediately
        int takeLimit = Math.min(roomLocal, available);
        if (takeLimit > 0 && excludeSlot >= 0) {
            int taken = pool.consumeKineticExcludingSlot(takeLimit, excludeSlot);
            if (taken > 0) {
                int newEnergy = currentEnergy + taken;
                activeMoth.setKineticEnergy(newEnergy);
                pool.setMothKinetic(excludeSlot, newEnergy);
                if (user instanceof ServerPlayerEntity) {
                    pool.sync((ServerPlayerEntity) user);
                }
            }
        }

        if (isNewMoth) {
            world.addFreshEntity(activeMoth);
        }
    }
}

