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
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

/**
 * 动能依附：与普通依附类似，但会优先使用全局动能为放置/选中的飞蛾充能。
 * 作为依附的 Shift 变体，拥有独立图标和名称。
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
        // 需要实际目标：方块或实体
        if (target.getType() == ActionTarget.TargetType.BLOCK) {
            return ActionConditionResult.POSITIVE;
        }
        if (target.getType() == ActionTarget.TargetType.ENTITY) {
            // 禁止依附到化石蛾自己身上
            if (target.getEntity() instanceof FossilMothEntity) {
                return ActionConditionResult.NEGATIVE;
            }
            return ActionConditionResult.POSITIVE;
        }
        return ActionConditionResult.createNegative(new StringTextComponent("Need target"));
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (world.isClientSide) return;

        java.util.Optional<IMothPool> poolOpt = user.getCapability(MothPoolProvider.MOTH_POOL_CAPABILITY).resolve();
        if (!poolOpt.isPresent()) return;

        IMothPool pool = poolOpt.get();

        // 1. 只选用「未满动能」的飞蛾（有充能空间）；优先选动能多的
        java.util.List<FossilMothEntity> freeMoths = MothQueryUtil.getFreeMoths(user, 64.0);
        freeMoths.removeIf(m -> {
            int idx = m.getMothPoolIndex();
            int current = (idx >= 0 && pool.isSlotActive(idx)) ? pool.getMothKinetic(idx) : m.getKineticEnergy();
            return current >= m.getMaxEnergy(); // 已满则不可用于动能依附
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
            // 新蛾子：必须先分配槽位再充能，否则入世界后 allocate 可能拿到「未部署」槽位，
            // 该槽位未被 consume 仍含动能，syncToPool 用 taken 覆盖会凭空增加总动能
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
        // 有槽位时一律以池中该槽位为准，保证与 consumeExcluding 一致
        int currentEnergy = (excludeSlot >= 0 && pool.isSlotActive(excludeSlot))
            ? pool.getMothKinetic(excludeSlot)
            : activeMoth.getKineticEnergy();
        int roomLocal = Math.max(0, activeMoth.getMaxEnergy() - currentEnergy);
        // 只检测未被占用的动能（未部署/召回槽位）；已放出飞蛾的槽位视为被占用，不参与充能
        int available = pool.getAvailableKinetic();

        // 2. 充能前检查：未被占用的动能不足则提示并取消放置
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

        // 3. 依附
        if (target.getType() == ActionTarget.TargetType.BLOCK) {
            activeMoth.attachTo(target.getBlockPos(), target.getFace());
        } else if (target.getType() == ActionTarget.TargetType.ENTITY) {
            activeMoth.attachToEntity(target.getEntity());
        }

        // 4. 只从「其他槽位」消耗动能并充给当前飞蛾；充能后立即回写池，保证总量一致
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

