package com.babelmoth.rotp_ata.util;

import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.github.standobyte.jojo.power.impl.stand.IStandManifestation;
import com.github.standobyte.jojo.util.mc.MCUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.List;
import java.util.WeakHashMap;

/**
 * Moth entity query utilities.
 */
public class MothQueryUtil {
    
    private static final int CACHE_TTL_TICKS = AshesToAshesConstants.CACHE_TTL_TICKS;
    private static final WeakHashMap<LivingEntity, CachedMothList> attachedMothCache = new WeakHashMap<>();
    
    private static class CachedMothList {
        List<FossilMothEntity> moths;
        long lastUpdate;
    }
    
    /** Free moths around the owner (excludes attached, busy, and persistent). */
    public static List<FossilMothEntity> getFreeMoths(LivingEntity owner, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, owner, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner 
                && !moth.isAttached() && !moth.isAttachedToEntity()
                && !moth.isRecalling() && !moth.isPiercingFiring() && !moth.isPiercingCharging()
                && !moth.isShieldPersistent()); // Exclude guardian moths
    }
    
    /** Attached moths on target entity (cached). */
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
    
    /** All moths owned by the given entity. */
    public static List<FossilMothEntity> getOwnerMoths(LivingEntity owner, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, owner, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner);
    }
    
    /** Swarm-eligible moths (free, non-guardian). */
    public static List<FossilMothEntity> getMothsForSwarm(LivingEntity owner, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, owner, radius, false,
            moth -> moth.getOwner() == owner && !moth.isAttached() && !moth.isAttachedToEntity()
                && !moth.isShieldPersistent()); // Exclude guardian moths
    }
    
    /** Moths currently charging kinetic piercing. */
    public static List<FossilMothEntity> getChargingMoths(LivingEntity owner, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, owner, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner && moth.isPiercingCharging());
    }
    
    /** Moths attached to the block at pos. */
    public static List<FossilMothEntity> getMothsAtBlock(LivingEntity owner, net.minecraft.util.math.BlockPos pos, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, owner, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner 
                && moth.isAttached() && pos.equals(moth.getAttachedPos()));
    }
    
    /** Attached moths with energy, excluding guardians. */
    public static List<FossilMothEntity> getMothsWithEnergy(LivingEntity owner, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, owner, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner 
                && (moth.isAttachedToEntity() || moth.getAttachedPos() != null)
                && moth.getTotalEnergy() > 0
                && !moth.isShieldPersistent()); // Exclude guardian moths
    }
    
    /** Current viewpoint entity: stand if manually controlled, otherwise user. */
    public static Entity getViewpointCenter(LivingEntity user) {
        return IStandPower.getStandPowerOptional(user).map(power -> {
            IStandManifestation manifestation = power.getStandManifestation();
            if (manifestation instanceof StandEntity) {
                StandEntity stand = (StandEntity) manifestation;
                if (stand.isManuallyControlled()) return (Entity) stand;
            }
            return (Entity) user;
        }).orElse(user);
    }

    /** Free moths around the given center, owned by owner. */
    public static List<FossilMothEntity> getFreeMothsAround(LivingEntity owner, Entity center, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, center, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner 
                && !moth.isAttached() && !moth.isAttachedToEntity()
                && !moth.isRecalling() && !moth.isPiercingFiring() && !moth.isPiercingCharging()
                && !moth.isShieldPersistent());
    }

    /** Swarm-eligible moths around the given center, owned by owner. */
    public static List<FossilMothEntity> getMothsForSwarmAround(LivingEntity owner, Entity center, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, center, radius, false,
            moth -> moth.getOwner() == owner && !moth.isAttached() && !moth.isAttachedToEntity()
                && !moth.isShieldPersistent());
    }

    /** The entity on the opposite side of a manual-control split, or null if no split. */
    public static Entity getOtherSideEntity(LivingEntity owner, Entity viewCenter) {
        IStandPower power = IStandPower.getStandPowerOptional(owner).resolve().orElse(null);
        if (power == null) return null;
        IStandManifestation manifestation = power.getStandManifestation();
        if (manifestation instanceof StandEntity && ((StandEntity) manifestation).isManuallyControlled()) {
            return viewCenter == owner ? (Entity) manifestation : (Entity) owner;
        }
        return null;
    }

    /** Free moths local to the current viewpoint (proximity-split when stand is manually controlled). */
    public static List<FossilMothEntity> getViewpointFreeMoths(LivingEntity owner, double radius) {
        final Entity viewCenter = getViewpointCenter(owner);
        final Entity otherSide = getOtherSideEntity(owner, viewCenter);
        return MCUtil.entitiesAround(FossilMothEntity.class, viewCenter, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner
                && !moth.isAttached() && !moth.isAttachedToEntity()
                && !moth.isRecalling() && !moth.isPiercingFiring() && !moth.isPiercingCharging()
                && !moth.isShieldPersistent()
                && (otherSide == null || moth.distanceToSqr(viewCenter) <= moth.distanceToSqr(otherSide)));
    }

    /** Swarm moths local to the current viewpoint (proximity-split when stand is manually controlled). */
    public static List<FossilMothEntity> getViewpointSwarmMoths(LivingEntity owner, double radius) {
        final Entity viewCenter = getViewpointCenter(owner);
        final Entity otherSide = getOtherSideEntity(owner, viewCenter);
        return MCUtil.entitiesAround(FossilMothEntity.class, viewCenter, radius, false,
            moth -> moth.getOwner() == owner && !moth.isAttached() && !moth.isAttachedToEntity()
                && !moth.isShieldPersistent()
                && (otherSide == null || moth.distanceToSqr(viewCenter) <= moth.distanceToSqr(otherSide)));
    }

    /** Shield moths (persistent + isShieldMoth). */
    public static List<FossilMothEntity> getShieldMoths(LivingEntity owner, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, owner, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner && moth.isShieldPersistent() && moth.isShieldMoth());
    }
    
    /** Guardian moths (persistent, not shield). */
    public static List<FossilMothEntity> getGuardianMoths(LivingEntity owner, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, owner, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner && moth.isShieldPersistent() && !moth.isShieldMoth());
    }
}
