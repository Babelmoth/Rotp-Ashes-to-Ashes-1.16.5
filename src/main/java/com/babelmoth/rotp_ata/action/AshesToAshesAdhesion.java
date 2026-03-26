package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.capability.MothPoolProvider;
import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

public class AshesToAshesAdhesion extends StandAction {

    public AshesToAshesAdhesion(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public TargetRequirement getTargetRequirement() {
        return TargetRequirement.ANY;
    }

    @Override
    public ActionConditionResult checkConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        MothQueryUtil.ResolvedTarget resolvedTarget = resolveTarget(user);
        if (resolvedTarget.hasBlock()) {
            return ActionConditionResult.POSITIVE;
        }
        if (resolvedTarget.hasEntity()) {
            if (resolvedTarget.getEntity() instanceof FossilMothEntity) {
                return ActionConditionResult.NEGATIVE;
            }
            return ActionConditionResult.POSITIVE;
        }

        return ActionConditionResult.createNegative(new TranslationTextComponent("jojo.ata.message.need_target"));
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (!world.isClientSide) {
            MothQueryUtil.ResolvedTarget resolvedTarget = resolveTarget(user);
            if (resolvedTarget.isEmpty()) {
                return;
            }

            java.util.List<FossilMothEntity> freeMoths =
                MothQueryUtil.getViewpointFreeMoths(user, AshesToAshesConstants.QUERY_RADIUS_SWARM, true);

            freeMoths.sort(java.util.Comparator.comparingInt(FossilMothEntity::getKineticEnergy));

            FossilMothEntity activeMoth = null;
            boolean isNewMoth = false;

            if (!freeMoths.isEmpty()) {
                activeMoth = freeMoths.get(0);
            } else {
                activeMoth = user.getCapability(MothPoolProvider.MOTH_POOL_CAPABILITY)
                    .map(pool -> {
                        int slot = pool.allocateSlotWithPriority(true);
                        if (slot == -1) {
                            return null;
                        }
                        FossilMothEntity moth = new FossilMothEntity(world, user);
                        moth.setMothPoolIndex(slot);
                        return moth;
                    })
                    .orElse(null);
                isNewMoth = activeMoth != null;
            }

            if (activeMoth != null) {
                if (resolvedTarget.hasBlock()) {
                    activeMoth.attachTo(resolvedTarget.getBlockPos(), resolvedTarget.getFace());
                } else if (resolvedTarget.hasEntity()) {
                    activeMoth.attachToEntity(resolvedTarget.getEntity());
                }

                if (isNewMoth) {
                    world.addFreshEntity(activeMoth);
                }
            }
        }
    }

    private static MothQueryUtil.ResolvedTarget resolveTarget(LivingEntity user) {
        return MothQueryUtil.resolveBlockOrEntityTarget(user.level, user, AshesToAshesConstants.QUERY_RADIUS_SWARM, true,
                entity -> entity instanceof LivingEntity && entity.isAlive() && entity != user
                        && !(entity instanceof StandEntity) && !(entity instanceof FossilMothEntity));
    }
}