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
import com.babelmoth.rotp_ata.entity.ThelaHunGinjeetStandEntity;
import com.babelmoth.rotp_ata.action.AshesToAshesAdhesion;
import com.babelmoth.rotp_ata.action.AshesToAshesKineticAdhesion;
import com.babelmoth.rotp_ata.action.AshesToAshesMothBite;
import com.babelmoth.rotp_ata.action.AshesToAshesMothRecall;
import com.babelmoth.rotp_ata.action.ThelaHunGinjeetRecall;
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

    // ======================================== Story Part ========================================

    // Original Stands Story Part Registration
    public static final com.github.standobyte.jojo.util.mod.StoryPart ORIGINAL_STANDS_PART = com.github.standobyte.jojo.util.mod.StoryPart.create(
            new net.minecraft.util.ResourceLocation(AddonMain.MOD_ID, "part_original_stands"),
            "jojo.story_part.original_stands",
            name -> name.withStyle(style -> style.withColor(net.minecraft.util.text.Color.parseColor("#8B7355"))));

    // ======================================== Ashes to Ashes ========================================

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
                    .resolveLevelToUnlock(2)
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
                    .resolveLevelToUnlock(3)
                    .cooldown(0, 0, 1.0f))); // Standalone skill, can be held to jet; limited by stamina

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
                    .resolveLevelToUnlock(3)
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
                    .resolveLevelToUnlock(4)
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

    public static final RegistryObject<com.babelmoth.rotp_ata.action.AshesToAshesSwarmConfig> ASHES_TO_ASHES_SWARM_CONFIG = ACTIONS.register("ashes_to_ashes_swarm_config", 
            () -> new com.babelmoth.rotp_ata.action.AshesToAshesSwarmConfig(new StandAction.Builder()
                    .resolveLevelToUnlock(0)
                    .cooldown(5, 0, 1.0f)));

    // Stand type: Ashes to Ashes. Stats: Power E-C, Speed C, Range A (100m), Durability C, Precision E
    public static final EntityStandRegistryObject<EntityStandType<StandStats>, StandEntityType<AshesToAshesStandEntity>> STAND_ASHES_TO_ASHES = 
            new EntityStandRegistryObject<>("ashes_to_ashes", 
                    STANDS, 
                    () -> new EntityStandType.Builder<StandStats>()
                    .color(0x8B7355) 
                    .storyPartName(InitStands.ORIGINAL_STANDS_PART.getName())
                    .leftClickHotbar(ASHES_TO_ASHES_MOTH_BITE.get(), ASHES_TO_ASHES_MOTH_SWARM_ATTACK.get(), ASHES_TO_ASHES_ADHESION.get(), ASHES_TO_ASHES_KINETIC_PIERCING.get(), ASHES_TO_ASHES_BULLET_WITH_BUTTERFLY_WINGS.get())
                    .rightClickHotbar(ASHES_TO_ASHES_SWARM_SHIELD.get(), ASHES_TO_ASHES_MOTH_RECALL.get(), ASHES_TO_ASHES_KINETIC_DETONATION.get(), ASHES_TO_ASHES_KINETIC_SENSING.get(), ASHES_TO_ASHES_FROZEN_BARRIER.get(), ASHES_TO_ASHES_SWARM_CONFIG.get())
                    .defaultStats(StandStats.class, new StandStats.Builder()
                            .tier(5) // E
                            .power(1.0) // E-C
                            .speed(10.0) // C
                            .range(50, 100) // A - 100m
                            .durability(12.0, 14.0) // C
                            .precision(8.0, 10) // E
                            .build())
                    .addSummonShout(InitSounds.ASHES_TO_ASHES_SUMMON_VOICELINE)
                    .addOst(InitSounds.ASHES_TO_ASHES_OST)
                    .build(),
                    
                    InitEntities.ENTITIES,
                    () -> new StandEntityType<AshesToAshesStandEntity>(AshesToAshesStandEntity::new, 0.065F, 0.195F)
                    .summonSound(InitSounds.ASHES_TO_ASHES_SUMMON_SOUND)
                    .unsummonSound(InitSounds.ASHES_TO_ASHES_UNSUMMON_SOUND))
            .withDefaultStandAttributes();

    // ======================================== Thela Hun Ginjeet ========================================

    public static final RegistryObject<com.babelmoth.rotp_ata.action.ThelaHunGinjeetSwiftThrust> THELA_HUN_GINJEET_SWIFT_THRUST = ACTIONS.register("thela_hun_ginjeet_swift_thrust",
            () -> new com.babelmoth.rotp_ata.action.ThelaHunGinjeetSwiftThrust(new StandAction.Builder()
                    .resolveLevelToUnlock(1)
                    .cooldown(40, 0, 1.0f)));

    public static final RegistryObject<com.babelmoth.rotp_ata.action.ThelaHunGinjeetSweep> THELA_HUN_GINJEET_SWEEP = ACTIONS.register("thela_hun_ginjeet_sweep",
            () -> new com.babelmoth.rotp_ata.action.ThelaHunGinjeetSweep(new StandAction.Builder()
                    .resolveLevelToUnlock(2)
                    .cooldown(60, 0, 1.0f)));

    public static final RegistryObject<com.babelmoth.rotp_ata.action.ThelaHunGinjeetBlock> THELA_HUN_GINJEET_BLOCK = ACTIONS.register("thela_hun_ginjeet_block",
            () -> new com.babelmoth.rotp_ata.action.ThelaHunGinjeetBlock(new StandAction.Builder()
                    .resolveLevelToUnlock(0)));

    public static final RegistryObject<ThelaHunGinjeetRecall> THELA_HUN_GINJEET_RECALL = ACTIONS.register("thela_hun_ginjeet_recall",
            () -> new ThelaHunGinjeetRecall(new StandAction.Builder()
                    .resolveLevelToUnlock(0)));

    // Thorn Strike: shift variant of thrust and sweep, max resolve to unlock
    public static final RegistryObject<com.babelmoth.rotp_ata.action.ThelaHunGinjeetThornStrike> THELA_HUN_GINJEET_THORN_STRIKE = ACTIONS.register("thela_hun_ginjeet_thorn_strike",
            () -> new com.babelmoth.rotp_ata.action.ThelaHunGinjeetThornStrike(new StandAction.Builder()
                    .resolveLevelToUnlock(4)
                    .cooldown(200, 0, 1.0f)));

    // Thorn Burst: detonate thorns on target entity, resolve 3
    public static final RegistryObject<com.babelmoth.rotp_ata.action.ThelaHunGinjeetThornBurst> THELA_HUN_GINJEET_THORN_BURST = ACTIONS.register("thela_hun_ginjeet_thorn_burst",
            () -> new com.babelmoth.rotp_ata.action.ThelaHunGinjeetThornBurst(new StandAction.Builder()
                    .resolveLevelToUnlock(3)
                    .cooldown(100, 0, 1.0f)));

    // Grab Pull: pull stuck entities towards player, resolve 2
    public static final RegistryObject<com.babelmoth.rotp_ata.action.ThelaHunGinjeetGrabPull> THELA_HUN_GINJEET_GRAB_PULL = ACTIONS.register("thela_hun_ginjeet_grab_pull",
            () -> new com.babelmoth.rotp_ata.action.ThelaHunGinjeetGrabPull(new StandAction.Builder()
                    .resolveLevelToUnlock(2)
                    .cooldown(40, 0, 1.0f)));

    // Grab Dash: dash self towards stuck entity (shift variant of grab pull), resolve 2
    public static final RegistryObject<com.babelmoth.rotp_ata.action.ThelaHunGinjeetGrabDash> THELA_HUN_GINJEET_GRAB_DASH = ACTIONS.register("thela_hun_ginjeet_grab_dash",
            () -> new com.babelmoth.rotp_ata.action.ThelaHunGinjeetGrabDash(new StandAction.Builder()
                    .resolveLevelToUnlock(2)
                    .cooldown(40, 0, 1.0f)));


    public static final EntityStandRegistryObject<EntityStandType<StandStats>, StandEntityType<ThelaHunGinjeetStandEntity>> STAND_THELA_HUN_GINJEET =
            new EntityStandRegistryObject<>("thela_hun_ginjeet",
                    STANDS,
                    () -> new EntityStandType.Builder<StandStats>()
                            .color(0x3f1c4a)
                            .storyPartName(InitStands.ORIGINAL_STANDS_PART.getName())
                            .disableManualControl()
                            .disableStandLeap()
                            .leftClickHotbar(
                                    THELA_HUN_GINJEET_SWIFT_THRUST.get(),
                                    THELA_HUN_GINJEET_SWEEP.get(),
                                    THELA_HUN_GINJEET_THORN_BURST.get())
                            .rightClickHotbar(
                                    THELA_HUN_GINJEET_RECALL.get(),
                                    THELA_HUN_GINJEET_BLOCK.get(),
                                    THELA_HUN_GINJEET_GRAB_PULL.get(),
                                    THELA_HUN_GINJEET_GRAB_DASH.get())
                            .defaultStats(StandStats.class, new StandStats.Builder()
                                    .tier(4)
                                    .power(5.0, 8.0)
                                    .speed(5.0)
                                    .range(3.0)
                                    .durability(5.0, 10.0)
                                    .precision(12.0)
                                    .randomWeight(1)
                                    .build())
                            .build(),

                    InitEntities.ENTITIES,
                    () -> new StandEntityType<ThelaHunGinjeetStandEntity>(ThelaHunGinjeetStandEntity::new, 0.0F, 0.0F)
                            .summonSound(InitSounds.ASHES_TO_ASHES_SUMMON_SOUND)
                            .unsummonSound(InitSounds.ASHES_TO_ASHES_UNSUMMON_SOUND))
            .withDefaultStandAttributes();

}
