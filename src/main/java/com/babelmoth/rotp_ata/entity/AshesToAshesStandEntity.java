package com.babelmoth.rotp_ata.entity;

import com.babelmoth.rotp_ata.action.AshesToAshesSwarmShield;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.entity.stand.StandEntityType;
import com.github.standobyte.jojo.init.ModStatusEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class AshesToAshesStandEntity extends StandEntity {

    private static final UUID SHIELD_SLOW_UUID = UUID.fromString("d7b79db8-58b9-46f0-9b9b-0dd6d43bcfe1");

    private boolean mothsSpawned = false;
    private static final int MOTH_COUNT = AshesToAshesConstants.DEFAULT_MOTH_COUNT;
    private static final int MOTH_COUNT_RESOLVE = 30;

    private int getTargetMothCount() {
        LivingEntity user = getUser();
        if (user == null) return MOTH_COUNT;
        int base = user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY)
                .map(com.babelmoth.rotp_ata.capability.IMothPool::getOrbitMothCount)
                .orElse(MOTH_COUNT);
        if (user.hasEffect(ModStatusEffects.RESOLVE.get())) {
            return base + 10;
        }
        return base;
    }

    private int replenishTimer = 0;
    private boolean poolDataChanged = false;
    private final java.util.Deque<FossilMothEntity> deployedMoths = new java.util.ArrayDeque<>();
    private static final float LEAP_STRENGTH = 2.5F;
    private static final double LEAP_BASE_STRENGTH = 2.5D;
    private boolean wasUserLeaping = false;

    @Override
    public float getLeapStrength() {
        if (canLeapWithMoths()) {
            return LEAP_STRENGTH;
        }
        return 0.0F;
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

        if (!level.isClientSide) {
            LivingEntity user = getUser();
            if (user != null) {
                user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(com.babelmoth.rotp_ata.capability.IMothPool::tickRecovery);

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

            replenishTimer++;
            if (replenishTimer >= 20) {
                replenishTimer = 0;
                replenishMoths();
                recallExcessMoths();
            }

            if (user != null) {
                boolean shouldSync = poolDataChanged
                        || this.tickCount <= 1
                        || this.tickCount % AshesToAshesConstants.SYNC_INTERVAL_TICKS == 0;
                if (shouldSync) {
                    user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
                        if (user instanceof net.minecraft.entity.player.ServerPlayerEntity) {
                            pool.sync((net.minecraft.entity.player.ServerPlayerEntity) user);
                        }
                    });
                    poolDataChanged = false;
                }
            }

            tickShieldLogic();
            tickGuardianLogic();
        }
    }

    private void recallExcessMoths() {
        LivingEntity user = getUser();
        if (user == null) return;
        List<FossilMothEntity> freeMoths = MothQueryUtil.getFreeMoths(user, AshesToAshesConstants.QUERY_RADIUS_SWARM);
        int targetCount = getTargetMothCount();
        if (freeMoths.size() <= targetCount) return;
        int toRecall = freeMoths.size() - targetCount;
        freeMoths.sort(Comparator.comparingDouble(m -> m.distanceToSqr(user)));
        for (int i = freeMoths.size() - 1; i >= freeMoths.size() - toRecall; i--) {
            FossilMothEntity moth = freeMoths.get(i);
            if (moth.isAlive()) moth.recall();
        }
    }

    private void replenishMoths() {
        LivingEntity user = getUser();
        if (user == null || !this.isAlive()) return;

        int currentSwarmSize = MothQueryUtil.getFreeMoths(user, AshesToAshesConstants.QUERY_RADIUS_SWARM).size();
        int targetCount = getTargetMothCount();
        if (currentSwarmSize < targetCount) {
            user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
                int toSpawn = Math.min(2, targetCount - currentSwarmSize);
                if (toSpawn <= 0 || toSpawn > targetCount) return;

                for (int i = 0; i < toSpawn; i++) {
                    int slot = pool.allocateSlotWithPriority(true);
                    if (slot == -1) {
                        break;
                    }

                    if (level != null) {
                        FossilMothEntity moth = new FossilMothEntity(level, user);
                        moth.setMothPoolIndex(slot);
                        moth.setPos(user.getX(), user.getY() + 1.0, user.getZ());
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
        FossilMothEntity.spawnMothsForUser(level, user, getTargetMothCount());
    }

    private boolean isShieldActive = false;
    private int shieldConsumptionTimer = 0;

    public void toggleShield() {
        this.isShieldActive = !this.isShieldActive;
    }

    public void setShieldActive(boolean active) {
        this.isShieldActive = active;
    }

    public boolean isShieldActive() {
        return this.isShieldActive;
    }

    private int getShieldMothCountConfig() {
        LivingEntity user = getUser();
        if (user == null) return 10;
        return user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY)
                .map(com.babelmoth.rotp_ata.capability.IMothPool::getShieldMothCount)
                .orElse(10);
    }

    private int getGuardianMothCountConfig() {
        return getShieldMothCountConfig();
    }

    private void tickShieldLogic() {
        LivingEntity user = getUser();
        if (user == null) {
            isShieldActive = false;
            return;
        }
        if (!AshesToAshesSwarmShield.isShieldEnabled(user)) {
            isShieldActive = false;
            removeShieldSlowModifier(user);
            return;
        }
        if (!isShieldActive) {
            removeShieldSlowModifier(user);
            return;
        }

        List<FossilMothEntity> shieldMoths = MothQueryUtil.getShieldMoths(user, AshesToAshesConstants.QUERY_RADIUS_GUARDIAN);
        if (shieldMoths.isEmpty()) {
            removeShieldSlowModifier(user);
            return;
        }

        for (FossilMothEntity moth : shieldMoths) {
            moth.refreshShield();
        }

        applyShieldSlowModifier(user);

        if (user instanceof net.minecraft.entity.player.PlayerEntity && !((net.minecraft.entity.player.PlayerEntity) user).isCreative()) {
            com.github.standobyte.jojo.power.impl.stand.IStandPower.getStandPowerOptional((net.minecraft.entity.player.PlayerEntity) user).ifPresent(power -> {
                power.consumeStamina(0.045F * shieldMoths.size());
                if (power.getStamina() <= 0) {
                    AshesToAshesSwarmShield.turnOffShieldForUser(level, user);
                    removeShieldSlowModifier(user);
                    isShieldActive = false;
                }
            });
        }
    }

    private void tickGuardianLogic() {
        LivingEntity user = getUser();
        if (user == null) return;

        List<FossilMothEntity> guardianMoths = MothQueryUtil.getGuardianMoths(user, AshesToAshesConstants.QUERY_RADIUS_GUARDIAN);
        int guardianCount = guardianMoths.size();
        if (guardianCount == 0) return;

        Entity guardianTarget = null;
        for (FossilMothEntity moth : guardianMoths) {
            Entity t = moth.getShieldTarget();
            if (t != null && t.isAlive()) {
                guardianTarget = t;
                break;
            }
        }

        if (guardianTarget == null) {
            clearGuardianTargets(guardianMoths);
            return;
        }

        for (FossilMothEntity moth : guardianMoths) {
            moth.refreshShield();
        }

        if (user instanceof net.minecraft.entity.player.PlayerEntity && !((net.minecraft.entity.player.PlayerEntity) user).isCreative()) {
            com.github.standobyte.jojo.power.impl.stand.IStandPower.getStandPowerOptional((net.minecraft.entity.player.PlayerEntity) user).ifPresent(power -> {
                power.consumeStamina(0.012F * guardianCount);
                if (power.getStamina() <= 0) {
                    clearGuardianTargets(guardianMoths);
                }
            });
        }
    }

    private void clearGuardianTargets(List<FossilMothEntity> guardianMoths) {
        for (FossilMothEntity moth : guardianMoths) {
            moth.setShieldTarget(null);
        }
    }

    private void applyShieldSlowModifier(LivingEntity user) {
        ModifiableAttributeInstance speed = user.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SHIELD_SLOW_UUID) == null) {
            speed.addTransientModifier(new AttributeModifier(SHIELD_SLOW_UUID, "Swarm shield slow", -0.45D, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private void removeShieldSlowModifier(LivingEntity user) {
        ModifiableAttributeInstance speed = user.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SHIELD_SLOW_UUID);
        }
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();

        if (!level.isClientSide) {
            LivingEntity user = getUser();
            if (user != null) {
                removeShieldSlowModifier(user);
                AshesToAshesSwarmShield.turnOffShieldForUser(level, user);
                List<FossilMothEntity> activeMoths = MothQueryUtil.getOwnerMoths(user, AshesToAshesConstants.QUERY_RADIUS_GUARDIAN);
                for (FossilMothEntity moth : activeMoths) {
                    moth.recall();
                }
            }
        }
    }

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

    public EnergyConsumeResult consumeEnergyPrioritizeHamon(int amount) {
        LivingEntity user = getUser();
        if (user == null) return new EnergyConsumeResult(0, 0, false);

        return user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).map(pool -> {
            int totalHamon = pool.getTotalHamonEnergy();
            int availableKinetic = pool.getAvailableKinetic();

            if (totalHamon + availableKinetic < amount) {
                return new EnergyConsumeResult(0, 0, false);
            }

            int hamonToUse = Math.min(totalHamon, amount);
            int remaining = amount - hamonToUse;
            int kineticToUse = Math.min(availableKinetic, remaining);

            if (hamonToUse > 0) pool.consumeHamon(hamonToUse);
            if (kineticToUse > 0) pool.consumeKinetic(kineticToUse);

            return new EnergyConsumeResult(hamonToUse, kineticToUse, true);
        }).orElse(new EnergyConsumeResult(0, 0, false));
    }

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
            if (pool.getAvailableKinetic() < amount) return false;
            pool.consumeKinetic(amount);
            return true;
        }).orElse(false);
    }

    private boolean canLeapWithMoths() {
        return getGlobalTotalEnergy() >= 5 && getGlobalTotalMoths() >= 1;
    }

    @Override
    protected double leapBaseStrength() {
        return LEAP_BASE_STRENGTH;
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
