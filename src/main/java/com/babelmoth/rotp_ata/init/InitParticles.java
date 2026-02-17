package com.babelmoth.rotp_ata.init;

import com.babelmoth.rotp_ata.AddonMain;

import net.minecraft.particles.BasicParticleType;
import net.minecraft.particles.ParticleType;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class InitParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(
        ForgeRegistries.PARTICLE_TYPES, AddonMain.MOD_ID);
    
    // Custom ash particle - no gravity, moves in velocity direction
    public static final RegistryObject<BasicParticleType> FOSSIL_ASH = PARTICLES.register(
        "fossil_ash", () -> new BasicParticleType(false));
            
    // Gathering effect particle
    public static final RegistryObject<BasicParticleType> FOSSIL_ASH_GATHER = PARTICLES.register(
        "fossil_ash_gather", () -> new BasicParticleType(false));
            
    // Large smoke particle (kinetic detonation - Exfoliating mode)
    public static final RegistryObject<BasicParticleType> ASH_SMOKE_LARGE = PARTICLES.register(
        "ash_smoke_large", () -> new BasicParticleType(false));

}
