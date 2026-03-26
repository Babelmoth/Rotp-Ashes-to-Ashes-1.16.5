package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AshesToAshesKineticDetonation extends StandAction {
    private static final double DETONATION_GROUP_LINK_RADIUS = 2.5D;
    private static final float OWNER_DAMAGE_FACTOR = 0.5F;

    public AshesToAshesKineticDetonation(AbstractBuilder<?> builder) {
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
                .filter(moth -> moth.getKineticEnergy() > 0)
                .min(Comparator.comparingDouble(moth -> moth.distanceToSqr(viewCenter)))
                .orElse(null);
        if (seed == null) {
            return;
        }

        List<FossilMothEntity> group = collectDetonationGroup(seed, candidates);
        explodeGroup(world, user, group);
    }

    public static List<FossilMothEntity> collectDetonationGroup(FossilMothEntity seed, List<FossilMothEntity> candidates) {
        List<FossilMothEntity> group = new ArrayList<>();
        ArrayDeque<FossilMothEntity> queue = new ArrayDeque<>();
        Set<FossilMothEntity> visited = new HashSet<>();

        queue.add(seed);
        visited.add(seed);

        double maxDistSqr = DETONATION_GROUP_LINK_RADIUS * DETONATION_GROUP_LINK_RADIUS;
        while (!queue.isEmpty()) {
            FossilMothEntity current = queue.poll();
            group.add(current);
            for (FossilMothEntity candidate : candidates) {
                if (visited.contains(candidate) || !candidate.isAlive()) {
                    continue;
                }
                if (current.distanceToSqr(candidate) <= maxDistSqr) {
                    visited.add(candidate);
                    queue.add(candidate);
                }
            }
        }

        return group;
    }

    private static void explodeGroup(World world, LivingEntity user, List<FossilMothEntity> group) {
        if (group == null || group.isEmpty()) {
            return;
        }

        List<FossilMothEntity> validGroup = new ArrayList<>();
        int totalKinetic = 0;
        boolean useHamon = false;
        double cx = 0.0D;
        double cy = 0.0D;
        double cz = 0.0D;

        for (FossilMothEntity moth : group) {
            if (moth == null || !moth.isAlive() || moth.level != world || moth.getOwner() != user) {
                continue;
            }
            validGroup.add(moth);
            totalKinetic += Math.max(0, moth.getKineticEnergy());
            if (moth.getHamonEnergy() > 0) {
                useHamon = true;
            }
            cx += moth.getX();
            cy += moth.getY();
            cz += moth.getZ();
        }

        if (validGroup.isEmpty() || totalKinetic <= 0) {
            return;
        }

        int groupSize = validGroup.size();
        cx /= groupSize;
        cy /= groupSize;
        cz /= groupSize;

        float chargeRatio = Math.min(1.0F, (float) totalKinetic / (float) (groupSize * validGroup.get(0).getMaxEnergy()));
        float countFactor = 0.35F + 0.65F * Math.min(1.0F, (groupSize - 1) / 4.0F);
        float radius = Math.min(4.5F, (0.6F + 0.22F * groupSize + 1.8F * chargeRatio) * countFactor);
        float damage = (0.8F + totalKinetic * 0.32F) * countFactor;

        playDetonationEffects(world, cx, cy, cz, radius, groupSize, totalKinetic, useHamon);

        Vector3d center = new Vector3d(cx, cy, cz);
        DamageSource detonationDamage = new EntityDamageSource("ashes_to_ashes_kinetic_detonation", user) {
            @Override
            public Vector3d getSourcePosition() {
                return center;
            }
        }.setExplosion();
        List<LivingEntity> targets = world.getEntitiesOfClass(LivingEntity.class, new AxisAlignedBB(cx, cy, cz, cx, cy, cz).inflate(radius + 1.0D));
        for (LivingEntity target : targets) {
            if (target instanceof FossilMothEntity) {
                continue;
            }
            double distSqr = target.distanceToSqr(center);
            if (distSqr > radius * radius) {
                continue;
            }
            float distRatio = 1.0F - (float) Math.sqrt(distSqr) / radius;
            if (distRatio <= 0.0F) {
                continue;
            }
            float ownerFactor = target == user ? OWNER_DAMAGE_FACTOR : 1.0F;
            float actualDamage = damage * distRatio * ownerFactor;
            boolean hurt = target.hurt(detonationDamage, actualDamage);
            if (hurt && useHamon) {
                com.github.standobyte.jojo.util.mc.damage.DamageUtil.dealHamonDamage(target, 0.5F * countFactor * ownerFactor, seedEntity(validGroup), user);
            }
        }

        for (FossilMothEntity moth : validGroup) {
            moth.setKineticEnergy(0);
            moth.setHamonEnergy(0);
            moth.setDissipateOnRemove(true);
            moth.remove();
        }
    }

    private static void playDetonationEffects(World world, double cx, double cy, double cz, float radius, int groupSize, int totalKinetic, boolean useHamon) {
        world.playSound(null, cx, cy, cz, SoundEvents.GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.7F + radius * 0.12F, 0.85F + world.random.nextFloat() * 0.2F);

        if (world instanceof ServerWorld) {
            ServerWorld serverWorld = (ServerWorld) world;
            serverWorld.sendParticles(ParticleTypes.EXPLOSION, cx, cy, cz, 2 + Math.max(1, groupSize / 2), radius * 0.15D, radius * 0.1D, radius * 0.15D, 0.02D);
            int particleCount = 10 + groupSize * 6 + Math.min(40, totalKinetic);
            for (int i = 0; i < particleCount; i++) {
                double px = cx + (world.random.nextDouble() - 0.5D) * radius * 2.0D;
                double py = cy + (world.random.nextDouble() - 0.5D) * radius * 2.0D;
                double pz = cz + (world.random.nextDouble() - 0.5D) * radius * 2.0D;
                double vx = (world.random.nextDouble() - 0.5D) * 0.35D;
                double vy = world.random.nextDouble() * 0.2D;
                double vz = (world.random.nextDouble() - 0.5D) * 0.35D;
                if (useHamon) {
                    serverWorld.sendParticles(com.github.standobyte.jojo.init.ModParticles.HAMON_SPARK.get(), px, py, pz, 1, vx, vy, vz, 0.1D);
                }
                else {
                    serverWorld.sendParticles(com.babelmoth.rotp_ata.init.InitParticles.FOSSIL_ASH.get(), px, py, pz, 1, vx, vy, vz, 0.1D);
                }
            }
        }
    }

    private static FossilMothEntity seedEntity(List<FossilMothEntity> group) {
        return group.isEmpty() ? null : group.get(0);
    }
}