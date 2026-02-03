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
    
    // 自定义灰尘粒子 - 不受重力影响，向速度方向移动
    public static final RegistryObject<BasicParticleType> FOSSIL_ASH = PARTICLES.register(
        "fossil_ash", () -> new BasicParticleType(false));
            
    // 聚集特效粒子
    public static final RegistryObject<BasicParticleType> FOSSIL_ASH_GATHER = PARTICLES.register(
        "fossil_ash_gather", () -> new BasicParticleType(false));
            
    // 大号烟雾粒子 (动能爆破-Exfoliating Mode)
    public static final RegistryObject<BasicParticleType> ASH_SMOKE_LARGE = PARTICLES.register(
        "ash_smoke_large", () -> new BasicParticleType(false));
}
