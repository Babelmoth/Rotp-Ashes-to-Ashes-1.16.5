package com.babelmoth.rotp_ata.capability;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraftforge.common.util.Constants;

import java.util.*;

public class AdaptationCap implements IAdaptationCap {
    private final Map<String, AdaptationEntry> memory = new HashMap<>();
    private final Map<String, PendingAdaptationEntry> pending = new HashMap<>();
    private long lastInjuredTick = 0;

    private String currentTrialKey = null;
    private String currentStrategyId = null;
    private int adaptationCooldownTicks = 0;

    private int timeStopStage = 0;
    private long timeStopTicks = 0;
    private boolean staminaDepleted = false;

    private static class AdaptationEntry {
        Set<String> strategyIds = new LinkedHashSet<>();
        long lastTick = 0;
        String lastDamageSource = "";
        float lastDamageAmount = 0;
    }

    private static class PendingAdaptationEntry {
        int count = 0;
        float severity = 0;
        long lastSeenTick = 0;
    }

    @Override
    public Map<String, Collection<String>> getStrategyPools() {
        Map<String, Collection<String>> pools = new HashMap<>();
        memory.forEach((k, v) -> pools.put(k, Collections.unmodifiableSet(v.strategyIds)));
        return pools;
    }

    @Override
    public void addStrategy(String damageKey, String strategyId) {
        memory.computeIfAbsent(damageKey, k -> new AdaptationEntry()).strategyIds.add(strategyId);
    }

    @Override
    public Collection<String> getStrategies(String damageKey) {
        AdaptationEntry entry = memory.get(damageKey);
        return entry != null ? entry.strategyIds : Collections.emptyList();
    }

    @Override
    public Map<String, CompoundNBT> getPendingEntries() {
        Map<String, CompoundNBT> entries = new HashMap<>();
        pending.forEach((key, value) -> {
            CompoundNBT nbt = new CompoundNBT();
            nbt.putInt("count", value.count);
            nbt.putFloat("severity", value.severity);
            nbt.putLong("last_seen", value.lastSeenTick);
            entries.put(key, nbt);
        });
        return entries;
    }

    @Override
    public void queuePending(String key, float severity, long tick) {
        PendingAdaptationEntry entry = pending.computeIfAbsent(key, k -> new PendingAdaptationEntry());
        entry.count++;
        entry.severity += Math.max(0, severity);
        entry.lastSeenTick = tick;
    }

    @Override
    public void removePending(String key) {
        pending.remove(key);
    }

    @Override
    public boolean hasPending(String key) {
        return pending.containsKey(key);
    }

    @Override
    public Set<String> getAllPendingKeys() {
        return pending.keySet();
    }

    @Override
    public int getPendingCount(String key) {
        PendingAdaptationEntry entry = pending.get(key);
        return entry != null ? entry.count : 0;
    }

    @Override
    public float getPendingSeverity(String key) {
        PendingAdaptationEntry entry = pending.get(key);
        return entry != null ? entry.severity : 0;
    }

    @Override
    public long getPendingLastSeenTick(String key) {
        PendingAdaptationEntry entry = pending.get(key);
        return entry != null ? entry.lastSeenTick : 0;
    }

    @Override
    public long getLastAdaptationTick(String damageKey) {
        AdaptationEntry entry = memory.get(damageKey);
        return entry != null ? entry.lastTick : 0;
    }

    @Override
    public void setLastAdaptationTick(String damageKey, long tick) {
        memory.computeIfAbsent(damageKey, k -> new AdaptationEntry()).lastTick = tick;
    }

    @Override
    public long getLastInjuredTick() { return lastInjuredTick; }

    @Override
    public void setLastInjuredTick(long tick) { this.lastInjuredTick = tick; }

    @Override
    public String getLastDamageSource(String damageKey) {
        AdaptationEntry entry = memory.get(damageKey);
        return entry != null ? entry.lastDamageSource : "";
    }

    @Override
    public void setLastDamageSource(String damageKey, String sourceId) {
        memory.computeIfAbsent(damageKey, k -> new AdaptationEntry()).lastDamageSource = sourceId;
    }

    @Override
    public float getLastDamageAmount(String damageKey) {
        AdaptationEntry entry = memory.get(damageKey);
        return entry != null ? entry.lastDamageAmount : 0;
    }

    @Override
    public void setLastDamageAmount(String damageKey, float amount) {
        memory.computeIfAbsent(damageKey, k -> new AdaptationEntry()).lastDamageAmount = amount;
    }

    @Override
    public void clear() {
        memory.clear();
        pending.clear();
        lastInjuredTick = 0;
        currentTrialKey = null;
        currentStrategyId = null;
        adaptationCooldownTicks = 0;
        timeStopStage = 0;
        timeStopTicks = 0;
        staminaDepleted = false;
    }

    @Override
    public int getAdaptationCooldownTicks() { return adaptationCooldownTicks; }

    @Override
    public void setAdaptationCooldownTicks(int ticks) { this.adaptationCooldownTicks = Math.max(0, ticks); }

    @Override
    public void tickAdaptationCooldown() {
        if (adaptationCooldownTicks > 0) {
            adaptationCooldownTicks--;
        }
    }

    @Override
    public int getTimeStopStage() { return timeStopStage; }
    @Override
    public void setTimeStopStage(int stage) { this.timeStopStage = stage; }
    @Override
    public long getTimeStopTicks() { return timeStopTicks; }
    @Override
    public void setTimeStopTicks(long ticks) { this.timeStopTicks = ticks; }
    @Override
    public void incrementTimeStopTicks() { this.timeStopTicks++; }

    @Override
    public boolean isStaminaDepleted() { return staminaDepleted; }
    @Override
    public void setStaminaDepleted(boolean depleted) { this.staminaDepleted = depleted; }

    @Override
    public Set<String> getAllDamageKeys() { return memory.keySet(); }

    @Override
    public String getCurrentTrialKey() { return currentTrialKey; }
    @Override
    public void setCurrentTrialKey(String key) { this.currentTrialKey = key; }
    @Override
    public String getCurrentStrategyId() { return currentStrategyId; }
    @Override
    public void setCurrentStrategyId(String strategyId) { this.currentStrategyId = strategyId; }

    @Override
    public CompoundNBT serializeNBT() {
        CompoundNBT nbt = new CompoundNBT();
        nbt.putLong("injured_tick", lastInjuredTick);
        nbt.putInt("adaptation_cooldown", adaptationCooldownTicks);
        nbt.putInt("ts_stage", timeStopStage);
        nbt.putLong("ts_ticks", timeStopTicks);
        nbt.putBoolean("stamina_depleted", staminaDepleted);

        CompoundNBT memoryNbt = new CompoundNBT();
        memory.forEach((key, entry) -> {
            CompoundNBT entryNbt = new CompoundNBT();
            ListNBT strategyList = new ListNBT();
            for (String id : entry.strategyIds) {
                if (id != null) strategyList.add(StringNBT.valueOf(id));
            }
            entryNbt.put("pool", strategyList);
            entryNbt.putLong("tick", entry.lastTick);
            entryNbt.putString("src", entry.lastDamageSource);
            entryNbt.putFloat("amt", entry.lastDamageAmount);
            memoryNbt.put(key, entryNbt);
        });
        nbt.put("memory", memoryNbt);

        CompoundNBT pendingNbt = new CompoundNBT();
        pending.forEach((key, entry) -> {
            CompoundNBT entryNbt = new CompoundNBT();
            entryNbt.putInt("count", entry.count);
            entryNbt.putFloat("severity", entry.severity);
            entryNbt.putLong("last_seen", entry.lastSeenTick);
            pendingNbt.put(key, entryNbt);
        });
        nbt.put("pending", pendingNbt);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundNBT nbt) {
        memory.clear();
        pending.clear();
        lastInjuredTick = nbt.getLong("injured_tick");
        adaptationCooldownTicks = nbt.getInt("adaptation_cooldown");
        timeStopStage = nbt.getInt("ts_stage");
        timeStopTicks = nbt.getLong("ts_ticks");
        staminaDepleted = nbt.getBoolean("stamina_depleted");

        CompoundNBT memoryNbt = nbt.getCompound("memory");
        for (String key : memoryNbt.getAllKeys()) {
            CompoundNBT entryNbt = memoryNbt.getCompound(key);
            AdaptationEntry entry = new AdaptationEntry();
            if (entryNbt.contains("pool")) {
                ListNBT pool = entryNbt.getList("pool", Constants.NBT.TAG_STRING);
                for (int i = 0; i < pool.size(); i++) {
                    entry.strategyIds.add(pool.getString(i));
                }
            }
            entry.lastTick = entryNbt.getLong("tick");
            entry.lastDamageSource = entryNbt.getString("src");
            entry.lastDamageAmount = entryNbt.getFloat("amt");
            memory.put(key, entry);
        }

        CompoundNBT pendingNbt = nbt.getCompound("pending");
        for (String key : pendingNbt.getAllKeys()) {
            CompoundNBT entryNbt = pendingNbt.getCompound(key);
            PendingAdaptationEntry entry = new PendingAdaptationEntry();
            entry.count = entryNbt.getInt("count");
            entry.severity = entryNbt.getFloat("severity");
            entry.lastSeenTick = entryNbt.getLong("last_seen");
            pending.put(key, entry);
        }
    }
}
