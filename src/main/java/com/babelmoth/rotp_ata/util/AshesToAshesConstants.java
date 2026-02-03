package com.babelmoth.rotp_ata.util;

/**
 * Constants for Ashes to Ashes mod.
 */
public class AshesToAshesConstants {
    
    // Moth pool constants
    public static final int MAX_MOTHS = 500;
    public static final int DEFAULT_MOTH_COUNT = 20;
    public static final int MOTH_MAX_KINETIC = 10;
    public static final int MOTH_MAX_HAMON = 10;
    
    // Query radius constants
    public static final double QUERY_RADIUS_SWARM = 64.0;
    public static final double QUERY_RADIUS_ATTACHMENT = 2.0;
    public static final double QUERY_RADIUS_SHIELD = 5.0;
    public static final double QUERY_RADIUS_CHARGING = 32.0;
    public static final double QUERY_RADIUS_GUARDIAN = 256.0;
    
    // Sync constants
    public static final int SYNC_INTERVAL_TICKS = 10;
    public static final int CACHE_TTL_TICKS = 5;
    
    // Energy constants
    public static final int MAX_ENERGY_BASE = 10;
    public static final int MAX_ENERGY_RESOLVE = 20;
    
    // Other constants
    public static final int OXYGEN_DEPLETION_THRESHOLD = 8;
    public static final double ADHESION_DEBUFF_RADIUS = 2.0;
    
    private AshesToAshesConstants() {
        // Utility class
    }
}
