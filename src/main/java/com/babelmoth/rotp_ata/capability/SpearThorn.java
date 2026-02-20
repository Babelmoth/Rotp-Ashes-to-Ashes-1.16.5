package com.babelmoth.rotp_ata.capability;

import net.minecraft.nbt.CompoundNBT;

public class SpearThorn implements ISpearThorn {
    public static final int MAX_THORNS = 100;

    private int thornCount = 0;
    private float damageDealt = 0;
    private float detachThreshold = 0;
    private boolean hasSpear = false;
    private float lastHealth = -1;

    @Override
    public int getThornCount() {
        return thornCount;
    }

    @Override
    public void setThornCount(int count) {
        this.thornCount = Math.max(0, Math.min(count, MAX_THORNS));
    }

    @Override
    public void addThorns(int amount) {
        setThornCount(thornCount + amount);
    }

    @Override
    public float getDamageDealt() {
        return damageDealt;
    }

    @Override
    public void setDamageDealt(float amount) {
        this.damageDealt = Math.max(0, amount);
    }

    @Override
    public void addDamageDealt(float amount) {
        setDamageDealt(damageDealt + amount);
    }

    @Override
    public float getDetachThreshold() {
        return detachThreshold;
    }

    @Override
    public void setDetachThreshold(float threshold) {
        this.detachThreshold = threshold;
    }

    @Override
    public boolean hasSpear() {
        return hasSpear;
    }

    @Override
    public void setHasSpear(boolean hasSpear) {
        this.hasSpear = hasSpear;
    }

    @Override
    public float getLastHealth() {
        return lastHealth;
    }

    @Override
    public void setLastHealth(float health) {
        this.lastHealth = health;
    }

    @Override
    public void reset() {
        this.thornCount = 0;
        this.damageDealt = 0;
        this.detachThreshold = 0;
        this.hasSpear = false;
        this.lastHealth = -1;
    }

    @Override
    public CompoundNBT serializeNBT() {
        CompoundNBT nbt = new CompoundNBT();
        nbt.putInt("ThornCount", thornCount);
        nbt.putFloat("DamageDealt", damageDealt);
        nbt.putFloat("DetachThreshold", detachThreshold);
        nbt.putBoolean("HasSpear", hasSpear);
        nbt.putFloat("LastHealth", lastHealth);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundNBT nbt) {
        thornCount = nbt.getInt("ThornCount");
        damageDealt = nbt.getFloat("DamageDealt");
        detachThreshold = nbt.getFloat("DetachThreshold");
        hasSpear = nbt.getBoolean("HasSpear");
        lastHealth = nbt.getFloat("LastHealth");
    }
}
