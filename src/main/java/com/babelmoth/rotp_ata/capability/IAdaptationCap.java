package com.babelmoth.rotp_ata.capability;

import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.common.util.INBTSerializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface IAdaptationCap extends INBTSerializable<CompoundNBT> {

    Map<String, Collection<String>> getStrategyPools();
    void addStrategy(String damageKey, String strategyId);
    Collection<String> getStrategies(String damageKey);

    Map<String, CompoundNBT> getPendingEntries();
    void queuePending(String key, float severity, long tick);
    void removePending(String key);
    boolean hasPending(String key);
    Set<String> getAllPendingKeys();
    int getPendingCount(String key);
    float getPendingSeverity(String key);
    long getPendingLastSeenTick(String key);

    long getLastAdaptationTick(String damageKey);
    void setLastAdaptationTick(String damageKey, long tick);

    long getLastInjuredTick();
    void setLastInjuredTick(long tick);

    String getLastDamageSource(String damageKey);
    void setLastDamageSource(String damageKey, String sourceId);

    float getLastDamageAmount(String damageKey);
    void setLastDamageAmount(String damageKey, float amount);

    void clear();

    Set<String> getAllDamageKeys();

    String getCurrentTrialKey();
    void setCurrentTrialKey(String key);
    String getCurrentStrategyId();
    void setCurrentStrategyId(String strategyId);

    int getAdaptationCooldownTicks();
    void setAdaptationCooldownTicks(int ticks);
    void tickAdaptationCooldown();

    int getTimeStopStage();
    void setTimeStopStage(int stage);

    long getTimeStopTicks();
    void setTimeStopTicks(long ticks);
    void incrementTimeStopTicks();

    boolean isStaminaDepleted();
    void setStaminaDepleted(boolean depleted);
}
