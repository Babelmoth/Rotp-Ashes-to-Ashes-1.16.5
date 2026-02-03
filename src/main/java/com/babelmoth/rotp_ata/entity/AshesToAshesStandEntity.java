package com.babelmoth.rotp_ata.entity;

import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.entity.stand.StandEntityType;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;
import com.github.standobyte.jojo.init.ModStatusEffects;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.DamageSource;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;

/**
 * Stand entity for Ashes to Ashes.
 * Manages moth spawning, replenishment, and energy consumption.
 */
public class AshesToAshesStandEntity extends StandEntity {
    
    private boolean mothsSpawned = false;
    private static final int MOTH_COUNT = AshesToAshesConstants.DEFAULT_MOTH_COUNT;
    private int replenishTimer = 0;
    private boolean poolDataChanged = false;
    
    private final java.util.Deque<FossilMothEntity> deployedMoths = new java.util.ArrayDeque<>();
    private boolean wasUserLeaping = false;

    @Override
    public float getLeapStrength() {
        // RotP standard leap strength calculation: (float) Math.min(strength, 40) / 5F;
        // Setting to 6.0f (equivalent to 30 strength) for a strong but controlled jump.
        // This MUST return a value on the Client side for InputHandler to trigger.
        if (getGlobalTotalEnergy() >= 5 && getGlobalTotalMoths() >= 1) {
            return 6.0f;
        }
        return 0.0f;
    }

    public int getGlobalKineticEnergy() {
        LivingEntity user = getUser();
        if (user == null) return 0;
        return user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY)
            .map(com.babelmoth.rotp_ata.capability.IMothPool::getTotalKineticEnergy)
            .orElse(0);
    }
    
    public int getGlobalHamonEnergy() {
        LivingEntity user = getUser();
        if (user == null) return 0;
        return user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY)
            .map(com.babelmoth.rotp_ata.capability.IMothPool::getTotalHamonEnergy)
            .orElse(0);
    }
    
    public int getGlobalTotalEnergy() {
        return getGlobalKineticEnergy() + getGlobalHamonEnergy();
    }

    public int getGlobalTotalMoths() {
        LivingEntity user = getUser();
        if (user == null) return 0;
        return user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY)
            .map(com.babelmoth.rotp_ata.capability.IMothPool::getTotalMoths)
            .orElse(0);
    }

    public AshesToAshesStandEntity(StandEntityType<AshesToAshesStandEntity> type, World world) {
        super(type, world);
    }
    
    @Override
    public void tick() {
        super.tick();
        
        // 在服务端首次tick时生成化石蛾
        if (!level.isClientSide) {
            LivingEntity user = getUser();
            if (user != null) {
                user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(com.babelmoth.rotp_ata.capability.IMothPool::tickRecovery);
                
                // Authentic Leap Consumption logic:
                // Only consume when the jump starts (rising edge of isDoingLeap flag)
                if (user instanceof com.github.standobyte.jojo.util.mod.IPlayerLeap) {
                    com.github.standobyte.jojo.util.mod.IPlayerLeap leapData = (com.github.standobyte.jojo.util.mod.IPlayerLeap) user;
                    boolean isLeaping = leapData.isDoingLeap();
                    if (isLeaping && !wasUserLeaping) {
                        if (getGlobalTotalEnergy() >= 5 && consumeMoths(1)) {
                            consumeEnergyPrioritizeHamon(5);
                            poolDataChanged = true;
                        }
                    }
                    wasUserLeaping = isLeaping;
                }
            }
            
            if (!mothsSpawned) {
                spawnFossilMoths();
                mothsSpawned = true;
            }

            // 实时补位逻辑: 每秒检查一次
            replenishTimer++;
            if (replenishTimer >= 20) {
                replenishTimer = 0;
                replenishMoths();
            }

            if (poolDataChanged || this.tickCount % AshesToAshesConstants.SYNC_INTERVAL_TICKS == 0) {
                user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
                    if (user instanceof net.minecraft.entity.player.ServerPlayerEntity) {
                        pool.sync((net.minecraft.entity.player.ServerPlayerEntity)user);
                    }
                });
                poolDataChanged = false;
            }
            
            tickShieldLogic();
        }
    }

    private void replenishMoths() {
        LivingEntity user = getUser();
        if (user == null || !this.isAlive()) return;

        int currentSwarmSize = MothQueryUtil.getFreeMoths(user, AshesToAshesConstants.QUERY_RADIUS_SWARM).size();

        if (currentSwarmSize < MOTH_COUNT) {
            user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
                int toSpawn = Math.min(2, MOTH_COUNT - currentSwarmSize);
                if (toSpawn <= 0 || toSpawn > MOTH_COUNT) return;
                
                for (int i = 0; i < toSpawn; i++) {
                    int slot = pool.allocateSlotWithPriority(true);
                    
                    if (slot == -1) {
                        pool.clearAllDeployed();
                        slot = pool.allocateSlotWithPriority(true);
                    }
                    
                    if (slot != -1 && level != null) {
                        FossilMothEntity moth = new FossilMothEntity(level, user);
                        moth.setMothPoolIndex(slot);
                        moth.setPos(this.getX(), this.getY() + 1.0, this.getZ());
                        level.addFreshEntity(moth);
                    }
                }
                poolDataChanged = true;
            });
        }
    }
    
    private void spawnFossilMoths() {
        LivingEntity user = getUser();
        if (user == null) return;
        
        FossilMothEntity.spawnMothsForUser(level, user, MOTH_COUNT);
    }
    
    // --- Shield Logic ---
    private boolean isShieldActive = false;
    private int shieldConsumptionTimer = 0;
    
    public void toggleShield() {
       this.isShieldActive = !this.isShieldActive;
       // Feedback message?
    }
    
    public boolean isShieldActive() {
        return this.isShieldActive;
    }
    
    private void tickShieldLogic() {
        if (!isShieldActive) return;
        
        LivingEntity user = getUser();
        if (user == null) {
            isShieldActive = false;
            return;
        }
        
        // 1. Maintain Shield Target on Moths
        // Only target moths that are NOT attached (orbiting)
        java.util.List<FossilMothEntity> activeMoths = MothQueryUtil.getMothsForSwarm(user, AshesToAshesConstants.QUERY_RADIUS_CHARGING);
            
        for (FossilMothEntity moth : activeMoths) {
            if (moth.getShieldTarget() != user) {
                moth.setShieldTarget(user, false);
            }
            moth.refreshShield();
        }
        
        // 2. Consume Resources
        // Stamina drain handled by Event? Or here?
        // EventHandler handles *Self-Adhesion* stamina.
        // Shield orbiters might not be covered if not attached.
        // Let's drain generic stamina here.
        if (user instanceof net.minecraft.entity.player.PlayerEntity && !((net.minecraft.entity.player.PlayerEntity)user).isCreative()) {
             com.github.standobyte.jojo.power.impl.stand.IStandPower.getStandPowerOptional((net.minecraft.entity.player.PlayerEntity)user).ifPresent(power -> {
                 power.consumeStamina(0.5F); // Moderate active drain per tick
                 if (power.getStamina() <= 0) toggleShield();
             });
        }
        
        // 3. Periodic Moth Consumption (Fuel)
        // Every 5 seconds (100 ticks)
        shieldConsumptionTimer++;
        if (shieldConsumptionTimer >= 100) {
            shieldConsumptionTimer = 0;
            // Consume 1 moth from pool (Reserve preferred)
            if (!consumeMoths(1)) {
                // Fail
                toggleShield(); // Turn off if run out of fuel
            } else {
                poolDataChanged = true;
            }
        }
    }

    
    // ... deploy/recall methods ...

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        
        // 替身收回时(Unsummoned), 必须移除所有化石蛾
        // 否则它们会变成幽灵实体留存在世界上
        if (!level.isClientSide) {
            LivingEntity user = getUser();
            if (user != null) {
            List<FossilMothEntity> activeMoths = MothQueryUtil.getOwnerMoths(user, AshesToAshesConstants.QUERY_RADIUS_GUARDIAN);
                
                for (FossilMothEntity moth : activeMoths) {
                    moth.recall(); // Saves data and removes entity
                }
            }
        }
    }
    
    // ... deploy/recall methods ...
    
    /**
     * Result of energy consumption showing Hamon vs Kinetic breakdown.
     * hamonUsed > 0 indicates Hamon damage should be applied.
     */
    public static class EnergyConsumeResult {
        public final int hamonUsed;
        public final int kineticUsed;
        public final boolean success;
        
        public EnergyConsumeResult(int hamonUsed, int kineticUsed, boolean success) {
            this.hamonUsed = hamonUsed;
            this.kineticUsed = kineticUsed;
            this.success = success;
        }
        
        public boolean usedHamon() {
            return hamonUsed > 0;
        }
        
        public int totalUsed() {
            return hamonUsed + kineticUsed;
        }
    }
    
    /**
     * Consume energy with Hamon priority.
     * Returns result showing breakdown.
     */
    public EnergyConsumeResult consumeEnergyPrioritizeHamon(int amount) {
        LivingEntity user = getUser();
        if (user == null) return new EnergyConsumeResult(0, 0, false);
        
        return user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).map(pool -> {
            int totalHamon = pool.getTotalHamonEnergy();
            int totalKinetic = pool.getTotalKineticEnergy();
            
            if (totalHamon + totalKinetic < amount) {
                return new EnergyConsumeResult(0, 0, false);
            }
            
            // Prioritize Hamon
            int hamonToUse = Math.min(totalHamon, amount);
            int remaining = amount - hamonToUse;
            int kineticToUse = Math.min(totalKinetic, remaining);
            
            if (hamonToUse > 0) pool.consumeHamon(hamonToUse);
            if (kineticToUse > 0) pool.consumeKinetic(kineticToUse);
            
            return new EnergyConsumeResult(hamonToUse, kineticToUse, true);
        }).orElse(new EnergyConsumeResult(0, 0, false));
    }
    
    /**
     * Consume Hamon energy from moths (distributed).
     */
    private void consumeGlobalHamonEnergy(int amount) {
        LivingEntity user = getUser();
        if (user == null) return;
        
        List<FossilMothEntity> activeMoths = MothQueryUtil.getOwnerMoths(user, AshesToAshesConstants.QUERY_RADIUS_GUARDIAN);
        activeMoths.removeIf(m -> m.getHamonEnergy() <= 0);
        
        if (activeMoths.isEmpty()) return;
        
        int needed = amount;
        while (needed > 0 && !activeMoths.isEmpty()) {
            int split = Math.max(1, needed / activeMoths.size());
            java.util.Iterator<FossilMothEntity> it = activeMoths.iterator();
            while (it.hasNext()) {
                FossilMothEntity m = it.next();
                int take = Math.min(m.getHamonEnergy(), split);
                m.setHamonEnergy(m.getHamonEnergy() - take);
                needed -= take;
                if (m.getHamonEnergy() == 0) {
                    it.remove();
                }
                if (needed == 0) break;
            }
        }
    }
    
    public boolean consumeMoths(int count) {
        LivingEntity user = getUser();
        if (user == null) return false;
        
        return user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY)
            .map(pool -> pool.consumeMoths(count))
            .orElse(false);
    }
    
    public boolean consumeGlobalEnergy(int amount) {
        LivingEntity user = getUser();
        if (user == null) return false;
        
        return user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).map(pool -> {
            if (pool.getTotalKineticEnergy() < amount) return false;
            pool.consumeKinetic(amount);
            return true;
        }).orElse(false);
    }

    @Override
    protected double leapBaseStrength() {
        // Great Jump is now handled by Active Consumption in AshesToAshesEventHandler.onLivingJump
        return super.leapBaseStrength();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        this.addEffect(new EffectInstance(ModStatusEffects.FULL_INVISIBILITY.get(), Integer.MAX_VALUE, 0, false, false, false));
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSrc) {
        return true;
    }
}
