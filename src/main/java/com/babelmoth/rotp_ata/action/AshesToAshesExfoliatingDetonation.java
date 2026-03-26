package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.entity.ExfoliatingAshCloudEntity;
import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AshesToAshesExfoliatingDetonation extends StandAction {

    public AshesToAshesExfoliatingDetonation(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (world.isClientSide) {
            return;
        }

        Entity viewCenter = MothQueryUtil.getRemoteViewpointCenter(user);
        List<FossilMothEntity> candidates = MothQueryUtil.getDetonationCandidateMoths(user, viewCenter, AshesToAshesConstants.QUERY_RADIUS_SWARM);
        if (candidates.isEmpty()) {
            return;
        }

        FossilMothEntity seed = candidates.stream()
                .filter(moth -> moth.getTotalEnergy() > 0)
                .min(Comparator.comparingDouble(moth -> moth.distanceToSqr(viewCenter)))
                .orElse(null);
        if (seed == null) {
            return;
        }

        List<FossilMothEntity> group = AshesToAshesKineticDetonation.collectDetonationGroup(seed, candidates);
        detonateGroup(world, user, group);
    }

    private static void detonateGroup(World world, LivingEntity user, List<FossilMothEntity> group) {
        if (group == null || group.isEmpty()) {
            return;
        }

        List<FossilMothEntity> validGroup = new ArrayList<>();
        int totalEnergy = 0;
        boolean hasHamon = false;
        double cx = 0.0D;
        double cy = 0.0D;
        double cz = 0.0D;

        for (FossilMothEntity moth : group) {
            if (moth == null || !moth.isAlive() || moth.level != world || moth.getOwner() != user) {
                continue;
            }
            int energy = moth.getTotalEnergy();
            if (energy <= 0) {
                continue;
            }
            validGroup.add(moth);
            totalEnergy += energy;
            if (moth.getHamonEnergy() > 0) {
                hasHamon = true;
            }
            cx += moth.getX();
            cy += moth.getY();
            cz += moth.getZ();
        }

        if (validGroup.isEmpty() || totalEnergy <= 0) {
            return;
        }

        int groupSize = validGroup.size();
        cx /= groupSize;
        cy /= groupSize;
        cz /= groupSize;

        float chargeRatio = Math.min(1.0F, (float) totalEnergy / (float) (groupSize * validGroup.get(0).getMaxEnergy()));
        float cloudRadius = Math.min(13.5F, 2.2F + groupSize * 0.7F + chargeRatio * 4.25F);
        int duration = Math.min(700, 180 + groupSize * 42 + Math.round(totalEnergy * 4.5F));

        ExfoliatingAshCloudEntity cloud = new ExfoliatingAshCloudEntity(world, cx, cy, cz);
        cloud.setOwner(user);
        cloud.setDuration(duration);
        cloud.setRadius(cloudRadius);
        if (hasHamon) {
            cloud.setHamonInfused(true);
        }
        world.addFreshEntity(cloud);

        for (FossilMothEntity moth : validGroup) {
            moth.setKineticEnergy(0);
            moth.setHamonEnergy(0);
            moth.setDissipateOnRemove(true);
            moth.remove();
        }
    }
}