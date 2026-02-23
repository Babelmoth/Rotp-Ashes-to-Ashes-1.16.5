package com.babelmoth.rotp_ata.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProtectedBlockRegistry {

    private static final Map<String, Integer> protectedBlocks = new ConcurrentHashMap<>();

    private static String makeKey(World world, BlockPos pos) {
        if (world == null || pos == null) return null;
        return world.dimension().location().toString() + "_" + pos.asLong();
    }

    public static void register(World world, BlockPos pos) {
        String key = makeKey(world, pos);
        if (key == null) return;
        protectedBlocks.merge(key, 1, Integer::sum);
    }

    public static void unregister(World world, BlockPos pos) {
        String key = makeKey(world, pos);
        if (key == null) return;
        protectedBlocks.computeIfPresent(key, (k, v) -> v <= 1 ? null : v - 1);
    }

    public static int getMothCount(World world, BlockPos pos) {
        String key = makeKey(world, pos);
        if (key == null) return 0;
        return protectedBlocks.getOrDefault(key, 0);
    }

    public static boolean isProtected(World world, BlockPos pos) {
        return getMothCount(world, pos) > 0;
    }

    public static void clearAll() {
        protectedBlocks.clear();
    }
}
