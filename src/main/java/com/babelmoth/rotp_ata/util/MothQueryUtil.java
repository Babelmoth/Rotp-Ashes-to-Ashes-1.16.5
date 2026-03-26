package com.babelmoth.rotp_ata.util;

import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.power.impl.stand.IStandManifestation;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.github.standobyte.jojo.util.mc.MCUtil;
import com.github.standobyte.jojo.util.mod.JojoModUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.WeakHashMap;
import java.util.function.Predicate;

public class MothQueryUtil {

    private static final int CACHE_TTL_TICKS = AshesToAshesConstants.CACHE_TTL_TICKS;
    private static final WeakHashMap<LivingEntity, CachedMothList> attachedMothCache = new WeakHashMap<>();

    private static class CachedMothList {
        List<FossilMothEntity> moths;
        long lastUpdate;
    }

    public static final class ResolvedTarget {
        public static final ResolvedTarget EMPTY = new ResolvedTarget(null, null, null);

        private final BlockPos blockPos;
        private final Direction face;
        private final Entity entity;

        private ResolvedTarget(BlockPos blockPos, Direction face, Entity entity) {
            this.blockPos = blockPos;
            this.face = face;
            this.entity = entity;
        }

        public static ResolvedTarget block(BlockPos blockPos, Direction face) {
            return new ResolvedTarget(blockPos, face, null);
        }

        public static ResolvedTarget entity(Entity entity) {
            return new ResolvedTarget(null, null, entity);
        }

        public boolean hasBlock() {
            return blockPos != null;
        }

        public boolean hasEntity() {
            return entity != null;
        }

        public boolean isEmpty() {
            return !hasBlock() && !hasEntity();
        }

        public BlockPos getBlockPos() {
            return blockPos;
        }

        public Direction getFace() {
            return face;
        }

        public Entity getEntity() {
            return entity;
        }
    }

    public static List<FossilMothEntity> getFreeMoths(LivingEntity owner, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, owner, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner
                && !moth.isAttached() && !moth.isAttachedToEntity()
                && !moth.isRecalling() && !moth.isPiercingFiring() && !moth.isPiercingCharging()
                && !moth.isShieldPersistent());
    }

    public static List<FossilMothEntity> getAttachedMoths(LivingEntity target, double radius) {
        if (target == null || target.level == null) {
            return java.util.Collections.emptyList();
        }

        long currentTick = target.level.getGameTime();
        CachedMothList cached = attachedMothCache.get(target);

        if (cached == null || cached.lastUpdate + CACHE_TTL_TICKS < currentTick) {
            cached = new CachedMothList();
            cached.moths = MCUtil.entitiesAround(FossilMothEntity.class, target, radius, false,
                moth -> moth.isAlive() && moth.isAttachedToEntity()
                    && moth.getEntityData().get(FossilMothEntity.ATTACHED_ENTITY_ID) == target.getId());
            cached.lastUpdate = currentTick;
            attachedMothCache.put(target, cached);
        }

        return cached.moths;
    }

    public static List<FossilMothEntity> getOwnerMoths(LivingEntity owner, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, owner, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner);
    }

    public static List<FossilMothEntity> getMothsForSwarm(LivingEntity owner, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, owner, radius, false,
            moth -> moth.getOwner() == owner && !moth.isAttached() && !moth.isAttachedToEntity()
                && !moth.isShieldPersistent());
    }

    public static List<FossilMothEntity> getChargingMoths(LivingEntity owner, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, owner, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner && moth.isPiercingCharging());
    }

    public static List<FossilMothEntity> getMothsAtBlock(LivingEntity owner, BlockPos pos, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, owner, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner
                && moth.isAttached() && pos.equals(moth.getAttachedPos()));
    }

    public static List<FossilMothEntity> getMothsWithEnergy(LivingEntity owner, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, owner, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner
                && (moth.isAttachedToEntity() || moth.getAttachedPos() != null)
                && moth.getTotalEnergy() > 0
                && !moth.isShieldPersistent());
    }

    public static List<FossilMothEntity> getDetonationCandidateMoths(LivingEntity owner, Entity center, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, center, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner
                && !moth.isShieldPersistent()
                && !moth.isRecalling()
                && !moth.isPiercingCharging()
                && !moth.isPiercingFiring()
                && !moth.isSwarming());
    }

    private static boolean shouldUseRemoteView(StandEntity stand) {
        return stand != null && stand.isManuallyControlled() && !stand.isRemotePositionFixed();
    }

    private static Entity getViewpointCenter(LivingEntity user, boolean useRemoteView) {
        if (useRemoteView) {
            return IStandPower.getStandPowerOptional(user).map(power -> {
                IStandManifestation manifestation = power.getStandManifestation();
                if (manifestation instanceof StandEntity) {
                    StandEntity stand = (StandEntity) manifestation;
                    if (shouldUseRemoteView(stand)) {
                        return (Entity) stand;
                    }
                }
                return (Entity) user;
            }).orElse(user);
        }
        return user;
    }

    public static Entity getViewpointCenter(LivingEntity user) {
        return getViewpointCenter(user, false);
    }

    public static Entity getRemoteViewpointCenter(LivingEntity user) {
        return getViewpointCenter(user, true);
    }

    public static List<FossilMothEntity> getFreeMothsAround(LivingEntity owner, Entity center, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, center, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner
                && !moth.isAttached() && !moth.isAttachedToEntity()
                && !moth.isRecalling() && !moth.isPiercingFiring() && !moth.isPiercingCharging()
                && !moth.isShieldPersistent());
    }

    public static List<FossilMothEntity> getMothsForSwarmAround(LivingEntity owner, Entity center, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, center, radius, false,
            moth -> moth.getOwner() == owner && !moth.isAttached() && !moth.isAttachedToEntity()
                && !moth.isShieldPersistent());
    }

    public static Entity getOtherSideEntity(LivingEntity owner, Entity viewCenter) {
        IStandPower power = IStandPower.getStandPowerOptional(owner).resolve().orElse(null);
        if (power == null) {
            return null;
        }
        IStandManifestation manifestation = power.getStandManifestation();
        if (manifestation instanceof StandEntity && shouldUseRemoteView((StandEntity) manifestation)) {
            return viewCenter == owner ? (Entity) manifestation : owner;
        }
        return null;
    }

    public static List<FossilMothEntity> getViewpointFreeMoths(LivingEntity owner, double radius) {
        return getViewpointFreeMoths(owner, radius, false);
    }

    public static List<FossilMothEntity> getViewpointFreeMoths(LivingEntity owner, double radius, boolean useRemoteView) {
        final Entity viewCenter = getViewpointCenter(owner, useRemoteView);
        final Entity otherSide = getOtherSideEntity(owner, viewCenter);
        return MCUtil.entitiesAround(FossilMothEntity.class, viewCenter, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner
                && !moth.isAttached() && !moth.isAttachedToEntity()
                && !moth.isRecalling() && !moth.isPiercingFiring() && !moth.isPiercingCharging()
                && !moth.isShieldPersistent()
                && (otherSide == null || moth.distanceToSqr(viewCenter) <= moth.distanceToSqr(otherSide)));
    }

    public static List<FossilMothEntity> getViewpointSwarmMoths(LivingEntity owner, double radius) {
        return getViewpointSwarmMoths(owner, radius, false);
    }

    public static List<FossilMothEntity> getViewpointSwarmMoths(LivingEntity owner, double radius, boolean useRemoteView) {
        final Entity viewCenter = getViewpointCenter(owner, useRemoteView);
        final Entity otherSide = getOtherSideEntity(owner, viewCenter);
        return MCUtil.entitiesAround(FossilMothEntity.class, viewCenter, radius, false,
            moth -> moth.getOwner() == owner && !moth.isAttached() && !moth.isAttachedToEntity()
                && !moth.isShieldPersistent()
                && !moth.isSwarming()
                && (otherSide == null || moth.distanceToSqr(viewCenter) <= moth.distanceToSqr(otherSide)));
    }

    public static ResolvedTarget resolveBlockOrEntityTarget(World world, LivingEntity user, double range, boolean useRemoteView,
            Predicate<Entity> entityPredicate) {
        if (world == null || user == null) {
            return ResolvedTarget.EMPTY;
        }

        Entity viewEntity = getViewpointCenter(user, useRemoteView);
        Vector3d eyePos = viewEntity.getEyePosition(1.0F);
        Vector3d endPos = eyePos.add(viewEntity.getViewVector(1.0F).scale(range));
        BlockRayTraceResult blockHit = world.clip(new RayTraceContext(eyePos, endPos,
                RayTraceContext.BlockMode.OUTLINE, RayTraceContext.FluidMode.NONE, viewEntity));

        Entity entity = null;
        if (entityPredicate != null) {
            RayTraceResult entityHit = JojoModUtil.rayTrace(viewEntity, range, entityPredicate::test);
            if (entityHit instanceof EntityRayTraceResult) {
                entity = ((EntityRayTraceResult) entityHit).getEntity();
            }
        }

        double blockDistSqr = blockHit.getType() == RayTraceResult.Type.BLOCK
                ? eyePos.distanceToSqr(Vector3d.atCenterOf(blockHit.getBlockPos()))
                : Double.MAX_VALUE;
        double entityDistSqr = entity != null ? eyePos.distanceToSqr(entity.position()) : Double.MAX_VALUE;

        if (entity != null && entityDistSqr <= blockDistSqr) {
            return ResolvedTarget.entity(entity);
        }
        if (blockHit.getType() == RayTraceResult.Type.BLOCK) {
            return ResolvedTarget.block(blockHit.getBlockPos(), blockHit.getDirection());
        }
        return ResolvedTarget.EMPTY;
    }

    public static ResolvedTarget resolveBlockTarget(World world, LivingEntity user, double range, boolean useRemoteView) {
        if (world == null || user == null) {
            return ResolvedTarget.EMPTY;
        }

        Entity viewEntity = getViewpointCenter(user, useRemoteView);
        Vector3d eyePos = viewEntity.getEyePosition(1.0F);
        Vector3d endPos = eyePos.add(viewEntity.getViewVector(1.0F).scale(range));
        BlockRayTraceResult blockHit = world.clip(new RayTraceContext(eyePos, endPos,
                RayTraceContext.BlockMode.OUTLINE, RayTraceContext.FluidMode.NONE, viewEntity));
        if (blockHit.getType() == RayTraceResult.Type.BLOCK) {
            return ResolvedTarget.block(blockHit.getBlockPos(), blockHit.getDirection());
        }
        return ResolvedTarget.EMPTY;
    }

    public static List<FossilMothEntity> getShieldMoths(LivingEntity owner, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, owner, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner && moth.isShieldPersistent() && moth.isShieldMoth());
    }

    public static List<FossilMothEntity> getGuardianMoths(LivingEntity owner, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, owner, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner && moth.isShieldPersistent() && !moth.isShieldMoth());
    }
}