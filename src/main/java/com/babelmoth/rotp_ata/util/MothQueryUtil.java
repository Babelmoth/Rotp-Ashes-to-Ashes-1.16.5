package com.babelmoth.rotp_ata.util;

import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.github.standobyte.jojo.util.mc.MCUtil;
import net.minecraft.entity.LivingEntity;

import java.util.List;
import java.util.WeakHashMap;

/**
 * Utility class for querying FossilMothEntity instances with caching support.
 */
public class MothQueryUtil {
    
    private static final int CACHE_TTL_TICKS = AshesToAshesConstants.CACHE_TTL_TICKS;
    private static final WeakHashMap<LivingEntity, CachedMothList> attachedMothCache = new WeakHashMap<>();
    
    private static class CachedMothList {
        List<FossilMothEntity> moths;
        long lastUpdate;
    }
    
    /**
     * Gets free moths (not attached, not assigned to tasks, not persistent shield) owned by the owner.
     */
    public static List<FossilMothEntity> getFreeMoths(LivingEntity owner, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, owner, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner 
                && !moth.isAttached() && !moth.isAttachedToEntity()
                && !moth.isRecalling() && !moth.isPiercingFiring() && !moth.isPiercingCharging()
                && !moth.isShieldPersistent()); // Exclude guardian moths
    }
    
    /**
     * Gets moths attached to the target entity (cached).
     */
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
    
    /**
     * Gets all moths owned by the owner.
     */
    public static List<FossilMothEntity> getOwnerMoths(LivingEntity owner, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, owner, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner);
    }
    
    /**
     * Gets moths available for swarm attack (free moths, excluding guardian moths).
     */
    public static List<FossilMothEntity> getMothsForSwarm(LivingEntity owner, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, owner, radius, false,
            moth -> moth.getOwner() == owner && !moth.isAttached() && !moth.isAttachedToEntity()
                && !moth.isShieldPersistent()); // Exclude guardian moths
    }
    
    /**
     * Gets moths currently charging for kinetic piercing.
     */
    public static List<FossilMothEntity> getChargingMoths(LivingEntity owner, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, owner, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner && moth.isPiercingCharging());
    }
    
    /**
     * Gets moths attached to blocks at the given position.
     */
    public static List<FossilMothEntity> getMothsAtBlock(LivingEntity owner, net.minecraft.util.math.BlockPos pos, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, owner, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner 
                && moth.isAttached() && pos.equals(moth.getAttachedPos()));
    }
    
    /**
     * Gets moths with energy (kinetic or hamon) for detonation, excluding guardian moths.
     */
    public static List<FossilMothEntity> getMothsWithEnergy(LivingEntity owner, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, owner, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner 
                && (moth.isAttachedToEntity() || moth.getAttachedPos() != null)
                && moth.getTotalEnergy() > 0
                && !moth.isShieldPersistent()); // Exclude guardian moths
    }
    
    /**
     * Gets guardian moths (shield persistent) owned by the owner.
     */
    public static List<FossilMothEntity> getGuardianMoths(LivingEntity owner, double radius) {
        return MCUtil.entitiesAround(FossilMothEntity.class, owner, radius, false,
            moth -> moth.isAlive() && moth.getOwner() == owner && moth.isShieldPersistent());
    }
}
