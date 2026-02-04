package com.babelmoth.rotp_ata.init;

import com.github.standobyte.jojo.action.Action;
import com.github.standobyte.jojo.entity.stand.StandEntityType;
import com.github.standobyte.jojo.init.power.stand.EntityStandRegistryObject;
import com.github.standobyte.jojo.init.power.stand.ModStandsInit;
import com.github.standobyte.jojo.power.impl.stand.stats.StandStats;
import com.github.standobyte.jojo.power.impl.stand.type.EntityStandType;
import com.github.standobyte.jojo.power.impl.stand.type.StandType;
import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity;
import com.babelmoth.rotp_ata.action.AshesToAshesAdhesion;
import com.babelmoth.rotp_ata.action.AshesToAshesKineticAdhesion;
import com.babelmoth.rotp_ata.action.AshesToAshesMothBite;
import com.babelmoth.rotp_ata.action.AshesToAshesMothRecall;
import com.github.standobyte.jojo.action.stand.StandAction;

import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public class InitStands {
    @SuppressWarnings("unchecked")
    public static final DeferredRegister<Action<?>> ACTIONS = DeferredRegister.create(
            (Class<Action<?>>) ((Class<?>) Action.class), AddonMain.MOD_ID);
    @SuppressWarnings("unchecked")
    public static final DeferredRegister<StandType<?>> STANDS = DeferredRegister.create(
            (Class<StandType<?>>) ((Class<?>) StandType.class), AddonMain.MOD_ID);
    
    // ======================================== Ashes to Ashes (灰烬归灰烬) ========================================
    
    public static final RegistryObject<com.babelmoth.rotp_ata.action.AshesToAshesSwarmRecall> ASHES_TO_ASHES_SWARM_RECALL = ACTIONS.register("ashes_to_ashes_swarm_recall", 
            () -> new com.babelmoth.rotp_ata.action.AshesToAshesSwarmRecall(new StandAction.Builder()
                    .resolveLevelToUnlock(0)
                    .cooldown(5, 0, 1.0f)));

    public static final RegistryObject<com.babelmoth.rotp_ata.action.AshesToAshesSwarmGuardian> ASHES_TO_ASHES_SWARM_GUARDIAN = ACTIONS.register("ashes_to_ashes_swarm_guardian", 
            () -> new com.babelmoth.rotp_ata.action.AshesToAshesSwarmGuardian(new StandAction.Builder()
                    .resolveLevelToUnlock(1)
                    .cooldown(0, 0, 1.0f)));

    public static final RegistryObject<AshesToAshesKineticAdhesion> ASHES_TO_ASHES_KINETIC_ADHESION = ACTIONS.register("ashes_to_ashes_kinetic_adhesion", 
            () -> new AshesToAshesKineticAdhesion(new StandAction.Builder()
                    .resolveLevelToUnlock(0)
                    .cooldown(0, 0, 1.0f)));

    public static final RegistryObject<AshesToAshesAdhesion> ASHES_TO_ASHES_ADHESION = ACTIONS.register("ashes_to_ashes_adhesion", 
            () -> new AshesToAshesAdhesion(
                    addShift(new StandAction.Builder()
                    .resolveLevelToUnlock(0)
                    .cooldown(0, 0, 1.0f), ASHES_TO_ASHES_KINETIC_ADHESION::get)));

    public static final RegistryObject<AshesToAshesMothRecall> ASHES_TO_ASHES_MOTH_RECALL = ACTIONS.register("ashes_to_ashes_moth_recall", 
            () -> new AshesToAshesMothRecall(
                    addShift(new StandAction.Builder()
                    .resolveLevelToUnlock(0)
                    .cooldown(5, 0, 1.0f), ASHES_TO_ASHES_SWARM_RECALL::get)));

    public static final RegistryObject<AshesToAshesMothBite> ASHES_TO_ASHES_MOTH_BITE = ACTIONS.register("ashes_to_ashes_moth_bite",
            () -> new AshesToAshesMothBite(new StandAction.Builder()
                    .resolveLevelToUnlock(0)
                    .cooldown(0, 0, 1.0f)));

    public static final RegistryObject<com.babelmoth.rotp_ata.action.AshesToAshesMothJet> ASHES_TO_ASHES_MOTH_JET = ACTIONS.register("ashes_to_ashes_moth_jet", 
            () -> new com.babelmoth.rotp_ata.action.AshesToAshesMothJet(new StandAction.Builder()
                    .resolveLevelToUnlock(1)
                    .cooldown(0, 0, 1.0f))); // 独立技能，可持续喷射，由精神力限制

    public static final RegistryObject<com.babelmoth.rotp_ata.action.AshesToAshesMothSwarmAttack> ASHES_TO_ASHES_MOTH_SWARM_ATTACK = ACTIONS.register("ashes_to_ashes_moth_swarm_attack", 
            () -> new com.babelmoth.rotp_ata.action.AshesToAshesMothSwarmAttack(
                    addShift(new StandAction.Builder()
                    .resolveLevelToUnlock(1)
                    .cooldown(0, 0, 1.0f), ASHES_TO_ASHES_MOTH_JET::get)));
                    
    public static final RegistryObject<com.babelmoth.rotp_ata.action.AshesToAshesSwarmShield> ASHES_TO_ASHES_SWARM_SHIELD = ACTIONS.register("ashes_to_ashes_swarm_shield", 
            () -> new com.babelmoth.rotp_ata.action.AshesToAshesSwarmShield(
                    addShift(new StandAction.Builder()
                    .resolveLevelToUnlock(0)
                    .cooldown(10, 0, 1.0f), ASHES_TO_ASHES_SWARM_GUARDIAN::get)));

    public static final RegistryObject<com.babelmoth.rotp_ata.action.AshesToAshesKineticPiercing> ASHES_TO_ASHES_KINETIC_PIERCING = ACTIONS.register("ashes_to_ashes_kinetic_piercing", 
            () -> new com.babelmoth.rotp_ata.action.AshesToAshesKineticPiercing(new StandAction.Builder()
                    .resolveLevelToUnlock(2)
// 10 seconds cooldown
                    )); 
                    
    public static final RegistryObject<com.babelmoth.rotp_ata.action.AshesToAshesBulletWithButterflyWings> ASHES_TO_ASHES_BULLET_WITH_BUTTERFLY_WINGS = ACTIONS.register("ashes_to_ashes_bullet_with_butterfly_wings", 
            () -> new com.babelmoth.rotp_ata.action.AshesToAshesBulletWithButterflyWings(new StandAction.Builder()
                    .resolveLevelToUnlock(2)
                    .cooldown(0, 0, 0f))); // No cooldown by default, limited by resources

    public static final RegistryObject<com.babelmoth.rotp_ata.action.AshesToAshesExfoliatingDetonation> ASHES_TO_ASHES_EXFOLIATING_DETONATION = ACTIONS.register("ashes_to_ashes_exfoliating_detonation", 
            () -> new com.babelmoth.rotp_ata.action.AshesToAshesExfoliatingDetonation(new StandAction.Builder()
                    .resolveLevelToUnlock(3)
                    .cooldown(20, 0, 1.0f)));
    
    public static final RegistryObject<com.babelmoth.rotp_ata.action.AshesToAshesKineticDetonation> ASHES_TO_ASHES_KINETIC_DETONATION = ACTIONS.register("ashes_to_ashes_kinetic_detonation", 
            () -> new com.babelmoth.rotp_ata.action.AshesToAshesKineticDetonation(
                    addShift(new StandAction.Builder()
                    .resolveLevelToUnlock(3)
                    .cooldown(20, 0, 1.0f), ASHES_TO_ASHES_EXFOLIATING_DETONATION::get)));

    private static StandAction.Builder addShift(StandAction.Builder builder, java.util.function.Supplier<? extends Action<?>> shift) {
        try {
            java.lang.reflect.Method m = null;
            Class<?> clazz = builder.getClass();
            while (clazz != null && m == null) {
                try {
                    m = clazz.getDeclaredMethod("addShiftVariation", java.util.function.Supplier.class);
                } catch (NoSuchMethodException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            if (m != null) {
                m.setAccessible(true);
                m.invoke(builder, shift);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return builder;
    }

    public static final RegistryObject<com.babelmoth.rotp_ata.action.AshesToAshesKineticSensing> ASHES_TO_ASHES_KINETIC_SENSING = ACTIONS.register("ashes_to_ashes_kinetic_sensing", 
            () -> new com.babelmoth.rotp_ata.action.AshesToAshesKineticSensing(new StandAction.Builder()
                    .resolveLevelToUnlock(1)
                    .cooldown(0, 0, 1.0f)));

    public static final RegistryObject<com.babelmoth.rotp_ata.action.AshesToAshesRemoveBarrier> ASHES_TO_ASHES_REMOVE_BARRIER = ACTIONS.register("ashes_to_ashes_remove_barrier", 
            () -> new com.babelmoth.rotp_ata.action.AshesToAshesRemoveBarrier(new StandAction.Builder()
                    .resolveLevelToUnlock(3)
                    .cooldown(2, 0, 1.0f)));

    public static final RegistryObject<com.babelmoth.rotp_ata.action.AshesToAshesFrozenBarrier> ASHES_TO_ASHES_FROZEN_BARRIER = ACTIONS.register("ashes_to_ashes_frozen_barrier", 
            () -> new com.babelmoth.rotp_ata.action.AshesToAshesFrozenBarrier(
                    addShift(new StandAction.Builder()
                    .resolveLevelToUnlock(3)
                    .cooldown(10, 0, 1.0f), ASHES_TO_ASHES_REMOVE_BARRIER::get)));

    // Stand type instance - 灰烬归灰烬 (Ashes to Ashes)
    // Stats: 破坏力 E-C, 速度 C, 射程 A (800m), 持久力 B, 精密度 A
    public static final EntityStandRegistryObject<EntityStandType<StandStats>, StandEntityType<AshesToAshesStandEntity>> STAND_ASHES_TO_ASHES = 
            new EntityStandRegistryObject<>("ashes_to_ashes", 
                    STANDS, 
                    () -> new EntityStandType.Builder<StandStats>()
                    .color(0x8B7355) // 灰褐色 (Ash brown)
                    .leftClickHotbar(ASHES_TO_ASHES_MOTH_BITE.get(), ASHES_TO_ASHES_MOTH_SWARM_ATTACK.get(), ASHES_TO_ASHES_ADHESION.get(), ASHES_TO_ASHES_KINETIC_PIERCING.get(), ASHES_TO_ASHES_BULLET_WITH_BUTTERFLY_WINGS.get())
                    .rightClickHotbar(ASHES_TO_ASHES_SWARM_SHIELD.get(), ASHES_TO_ASHES_MOTH_RECALL.get(), ASHES_TO_ASHES_KINETIC_DETONATION.get(), ASHES_TO_ASHES_KINETIC_SENSING.get(), ASHES_TO_ASHES_FROZEN_BARRIER.get())
                    .defaultStats(StandStats.class, new StandStats.Builder()
                            .tier(5) // 成长性 A
                            .power(1.0) // E-C
                            .speed(10.0) // C
                            .range(50, 100) // A - 800m
                            .durability(10.0, 16.0) // B
                            .precision(12.0, 16) // A
                            .build())
                    .addSummonShout(InitSounds.ASHES_TO_ASHES_SUMMON_VOICELINE)
                    .addOst(InitSounds.ASHES_TO_ASHES_OST)
                    // .disableManualControl() // Re-enabled for user request
                    .build(),
                    
                    InitEntities.ENTITIES,
                    () -> new StandEntityType<AshesToAshesStandEntity>(AshesToAshesStandEntity::new, 0.065F, 0.195F)
                    .summonSound(InitSounds.ASHES_TO_ASHES_SUMMON_SOUND)
                    .unsummonSound(InitSounds.ASHES_TO_ASHES_UNSUMMON_SOUND))
            .withDefaultStandAttributes();
    
}
