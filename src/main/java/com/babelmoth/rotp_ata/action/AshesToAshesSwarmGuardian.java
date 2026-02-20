package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.github.standobyte.jojo.power.impl.stand.IStandManifestation;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileHelper;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import java.util.List;
import java.util.ArrayList;

public class AshesToAshesSwarmGuardian extends StandAction {

    private static final int GUARDIAN_MOTH_COUNT = 10;

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
            Vector3d eyePos = viewCenter instanceof LivingEntity
                    ? ((LivingEntity) viewCenter).getEyePosition(1.0F) : viewCenter.position();
            Vector3d lookVec = viewCenter instanceof LivingEntity
                    ? ((LivingEntity) viewCenter).getViewVector(1.0F) : viewCenter.getLookAngle();
            double range = AshesToAshesConstants.QUERY_RADIUS_GUARDIAN;
            Vector3d maxVec = eyePos.add(lookVec.scale(range));
            AxisAlignedBB aabb = viewCenter.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(1.0);

            EntityRayTraceResult result = ProjectileHelper.getEntityHitResult(
                    viewCenter, eyePos, maxVec, aabb,
                    entity -> entity instanceof LivingEntity && entity.isAlive()
                            && entity != user && !(entity instanceof FossilMothEntity)
                            && !(entity instanceof StandEntity),
                    range * range);
            Entity targetEntity = result != null ? result.getEntity() : null;

            List<FossilMothEntity> currentGuardians = MothQueryUtil.getGuardianMoths(user, AshesToAshesConstants.QUERY_RADIUS_GUARDIAN);

            if (targetEntity == null || currentGuardians.size() >= GUARDIAN_MOTH_COUNT) {
                for (FossilMothEntity moth : currentGuardians) {
                    moth.setShieldTarget(null);
                }
                return;
            }

            List<FossilMothEntity> availableMoths = MothQueryUtil.getFreeMothsAround(user, viewCenter, range);
            int needMore = GUARDIAN_MOTH_COUNT - currentGuardians.size();

            if (availableMoths.size() < needMore) {
                int toSpawn = needMore - availableMoths.size();
                Entity spawnCenter = viewCenter;
                user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
                    for (int i = 0; i < toSpawn; i++) {
                        int slot = pool.allocateSlotWithPriority(true);
                        if (slot != -1) {
                            FossilMothEntity moth = new FossilMothEntity(world, user);
                            moth.setMothPoolIndex(slot);
                            moth.setPos(spawnCenter.getX(), spawnCenter.getY() + 1, spawnCenter.getZ());
                            world.addFreshEntity(moth);
                            availableMoths.add(moth);
                        }
                    }
                    if (user instanceof net.minecraft.entity.player.ServerPlayerEntity) {
                        pool.sync((net.minecraft.entity.player.ServerPlayerEntity) user);
                    }
                });
            }

            int recruited = 0;
            for (FossilMothEntity moth : availableMoths) {
                if (currentGuardians.size() + recruited >= GUARDIAN_MOTH_COUNT) break;
                moth.setShieldTarget(targetEntity, true);
                moth.setIsShieldMoth(false);
                moth.refreshShield();
                moth.detach();
                recruited++;
            }

            for (FossilMothEntity moth : currentGuardians) {
                moth.setShieldTarget(targetEntity, true);
                moth.refreshShield();
            }
        }
    }
}
