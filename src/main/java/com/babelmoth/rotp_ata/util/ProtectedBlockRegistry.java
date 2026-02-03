package com.babelmoth.rotp_ata.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for blocks protected by Fossil Moths.
 * Uses a simple Map for fast lookups without entity searches.
 */
public class ProtectedBlockRegistry {
    
    // Map: DimensionKey + BlockPos -> Moth Count
    // Using String key for dimension + pos to avoid complex keys
    private static final Map<String, Integer> protectedBlocks = new ConcurrentHashMap<>();
    
    private static String makeKey(World world, BlockPos pos) {
        if (world == null || pos == null) return null;
        return world.dimension().location().toString() + "_" + pos.asLong();
    }
    
    /**
     * Register a block as protected by a moth
     */
    public static void register(World world, BlockPos pos) {
        String key = makeKey(world, pos);
        if (key == null) return;
        protectedBlocks.merge(key, 1, Integer::sum);
    }
    
    /**
     * Unregister a moth from protecting a block
     */
    public static void unregister(World world, BlockPos pos) {
        String key = makeKey(world, pos);
        if (key == null) return;
        protectedBlocks.computeIfPresent(key, (k, v) -> v <= 1 ? null : v - 1);
    }
    
    /**
     * Get count of moths protecting a block
     */
    public static int getMothCount(World world, BlockPos pos) {
        String key = makeKey(world, pos);
        if (key == null) return 0;
        return protectedBlocks.getOrDefault(key, 0);
    }
    
    /**
     * Check if a block is protected
     */
    public static boolean isProtected(World world, BlockPos pos) {
        return getMothCount(world, pos) > 0;
    }
    
    /**
     * Clear all entries (for world unload or cleanup)
     */
    public static void clearAll() {
        protectedBlocks.clear();
    }
}
