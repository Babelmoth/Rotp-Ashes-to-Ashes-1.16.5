package com.babelmoth.rotp_ata.init;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.babelmoth.rotp_ata.entity.SpearSpikeEntity;
import com.babelmoth.rotp_ata.entity.ThelaHunGinjeetSpearEntity;

import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class InitEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(
            ForgeRegistries.ENTITIES, AddonMain.MOD_ID);
    
    // Fossil moth entity - Ashes to Ashes swarm unit
    public static final RegistryObject<EntityType<FossilMothEntity>> FOSSIL_MOTH = ENTITIES.register("fossil_moth",
            () -> EntityType.Builder.<FossilMothEntity>of(FossilMothEntity::new, EntityClassification.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(10)
                    .build(AddonMain.MOD_ID + ":fossil_moth"));
    
    // Exfoliating detonation smoke cloud entity
    public static final RegistryObject<EntityType<com.babelmoth.rotp_ata.entity.ExfoliatingAshCloudEntity>> EXFOLIATING_ASH_CLOUD = ENTITIES.register("exfoliating_ash_cloud",
            () -> EntityType.Builder.<com.babelmoth.rotp_ata.entity.ExfoliatingAshCloudEntity>of(com.babelmoth.rotp_ata.entity.ExfoliatingAshCloudEntity::new, EntityClassification.MISC)
                    .sized(6.0F, 0.5F) // Start large like campfire smoke spread
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build(AddonMain.MOD_ID + ":exfoliating_ash_cloud"));
    
    // Decorative spear spike entity (thorn strike / raid strike visual)
    public static final RegistryObject<EntityType<SpearSpikeEntity>> SPEAR_SPIKE_ENTITY = ENTITIES.register("spear_spike",
            () -> EntityType.Builder.<SpearSpikeEntity>of(SpearSpikeEntity::new, EntityClassification.MISC)
                    .sized(0.3F, 0.3F)
                    .clientTrackingRange(64)
                    .updateInterval(20)
                    .noSummon()
                    .build(AddonMain.MOD_ID + ":spear_spike"));

    // Thela Hun Ginjeet spear projectile
    public static final RegistryObject<EntityType<ThelaHunGinjeetSpearEntity>> THELA_HUN_GINJEET_SPEAR_ENTITY = ENTITIES.register("thela_hun_ginjeet_spear",
            () -> EntityType.Builder.<ThelaHunGinjeetSpearEntity>of(ThelaHunGinjeetSpearEntity::new, EntityClassification.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build(AddonMain.MOD_ID + ":thela_hun_ginjeet_spear"));

}
