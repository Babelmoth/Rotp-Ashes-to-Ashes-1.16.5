package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import com.github.standobyte.jojo.util.mod.JojoModUtil;
import java.util.List;

public class AshesToAshesSwarmGuardian extends StandAction {

    public AshesToAshesSwarmGuardian(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public String getTranslationKey(IStandPower power, ActionTarget target) {
        return "action.rotp_ata.ashes_to_ashes_swarm_shield_target";
    }

    @Override
    public ActionConditionResult checkConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        return ActionConditionResult.POSITIVE;
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (!world.isClientSide) {
            if (!power.isActive() && power.getType() instanceof com.github.standobyte.jojo.power.impl.stand.type.EntityStandType) {
                ((com.github.standobyte.jojo.power.impl.stand.type.EntityStandType<?>) power.getType())
                    .summon(user, power, entity -> {}, true, true);
            }

            Entity viewCenter = MothQueryUtil.getViewpointCenter(user);
            double range = AshesToAshesConstants.QUERY_RADIUS_GUARDIAN;

            RayTraceResult rtResult = JojoModUtil.rayTrace(viewCenter, range,
                    entity -> entity instanceof LivingEntity && entity.isAlive()
                            && entity != user && !(entity instanceof FossilMothEntity)
                            && !(entity instanceof StandEntity));
            Entity targetEntity = rtResult instanceof EntityRayTraceResult ? ((EntityRayTraceResult) rtResult).getEntity() : null;

            List<FossilMothEntity> currentGuardians = MothQueryUtil.getGuardianMoths(user, AshesToAshesConstants.QUERY_RADIUS_GUARDIAN);
            int targetGuardianCount = user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY)
                    .map(com.babelmoth.rotp_ata.capability.IMothPool::getShieldMothCount)
                    .orElse(10);

            if (targetEntity == null) {
                for (FossilMothEntity moth : currentGuardians) {
                    moth.setShieldTarget(null);
                }
                return;
            }

            for (FossilMothEntity moth : currentGuardians) {
                moth.setShieldTarget(targetEntity, true);
                moth.refreshShield();
            }

            if (currentGuardians.size() >= targetGuardianCount) {
                return;
            }

            List<FossilMothEntity> availableMoths = MothQueryUtil.getFreeMothsAround(user, viewCenter, range);
            int needMore = targetGuardianCount - currentGuardians.size();
            int recruited = 0;
            for (FossilMothEntity moth : availableMoths) {
                if (recruited >= needMore) break;
                moth.setShieldTarget(targetEntity, true);
                moth.setIsShieldMoth(false);
                moth.refreshShield();
                moth.detach();
                recruited++;
            }
        }
    }
}
