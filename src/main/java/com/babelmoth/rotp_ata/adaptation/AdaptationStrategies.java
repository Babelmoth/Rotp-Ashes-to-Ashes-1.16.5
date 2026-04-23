package com.babelmoth.rotp_ata.adaptation;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import java.util.*;

public class AdaptationStrategies {
    private static final Map<String, IAdaptationStrategy> REGISTRY = new HashMap<>();
    private static final Map<String, List<String>> PATHS = new HashMap<>();
    private static final List<String> PHYSICAL_PATH = Arrays.asList("physical_hardening", "kinetic_diffusion", "structural_absorption", "inertial_nullification", "physical_immunity");
    private static final List<String> PROJECTILE_PATH = Arrays.asList("trajectory_reading", "projectile_parry", "ballistic_shed", "projectile_refraction", "projectile_immunity");
    private static final List<String> FIRE_PATH = Arrays.asList("heat_endurance", "fire_absorption", "flame_skin", "pyre_nullification", "fire_immunity");
    private static final List<String> MAGIC_PATH = Arrays.asList("arcane_buffer", "magic_dampening", "mana_siphon", "spell_nullification", "magic_immunity");
    private static final List<String> BLAST_PATH = Arrays.asList("shock_absorption", "blast_buffering", "seismic_bastion", "detonation_nullification", "blast_immunity");
    private static final List<String> FALL_PATH = Arrays.asList("impact_bracing", "fall_cushion", "fall_dampening", "gravity_anchor", "fall_immunity");
    private static final List<String> WATER_PATH = Arrays.asList("water_breathing", "water_swiftness", "water_predator", "water_mastery", "abyssal_sovereign");
    private static final List<String> PAIN_PATH = Arrays.asList("pain_numbing_i", "pain_numbing_ii", "pain_numbing_iii", "pain_numbing_iv", "pain_numbing_v");
    private static final List<String> NEGATIVE_EFFECT_PATH = Arrays.asList("debuff_resistance", "debuff_purge", "debuff_immunity");

    static {
        register(new IAdaptationStrategy() {
            @Override public String getId() { return "physical_hardening"; }
            @Override public void apply(LivingHurtEvent event) {
                if (!event.getSource().isBypassArmor()) event.setAmount(event.getAmount() * 0.8f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "kinetic_diffusion"; }
            @Override public void apply(LivingHurtEvent event) {
                if (!event.getSource().isBypassArmor()) event.setAmount(event.getAmount() * 0.65f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "structural_absorption"; }
            @Override public void apply(LivingHurtEvent event) {
                if (!event.getSource().isBypassArmor()) event.setAmount(event.getAmount() * 0.5f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "inertial_nullification"; }
            @Override public void apply(LivingHurtEvent event) {
                if (!event.getSource().isBypassArmor()) event.setAmount(event.getAmount() * 0.3f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "physical_immunity"; }
            @Override public void apply(LivingHurtEvent event) {
                event.setAmount(0);
                event.setCanceled(true);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "heat_endurance"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().isFire()) event.setAmount(event.getAmount() * 0.75f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "fire_absorption"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().isFire()) event.setAmount(event.getAmount() * 0.45f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "flame_skin"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().isFire()) event.setAmount(event.getAmount() * 0.25f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "pyre_nullification"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().isFire()) event.setAmount(event.getAmount() * 0.08f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "fire_immunity"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().isFire()) {
                    event.setAmount(0);
                    event.setCanceled(true);
                    event.getEntityLiving().clearFire();
                }
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "arcane_buffer"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().isMagic()) event.setAmount(event.getAmount() * 0.75f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "magic_dampening"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().isMagic()) event.setAmount(event.getAmount() * 0.45f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "mana_siphon"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().isMagic()) event.setAmount(event.getAmount() * 0.25f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "spell_nullification"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().isMagic()) event.setAmount(event.getAmount() * 0.08f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "magic_immunity"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().isMagic()) {
                    event.setAmount(0);
                    event.setCanceled(true);
                }
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "shock_absorption"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().isExplosion()) event.setAmount(event.getAmount() * 0.75f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "blast_buffering"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().isExplosion()) event.setAmount(event.getAmount() * 0.5f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "seismic_bastion"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().isExplosion()) event.setAmount(event.getAmount() * 0.3f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "detonation_nullification"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().isExplosion()) event.setAmount(event.getAmount() * 0.1f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "blast_immunity"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().isExplosion()) {
                    event.setAmount(0);
                    event.setCanceled(true);
                }
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "impact_bracing"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().getMsgId().equals("fall")) event.setAmount(event.getAmount() * 0.7f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "fall_cushion"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().getMsgId().equals("fall")) event.setAmount(event.getAmount() * 0.45f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "fall_dampening"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().getMsgId().equals("fall")) event.setAmount(event.getAmount() * 0.2f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "gravity_anchor"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().getMsgId().equals("fall")) event.setAmount(event.getAmount() * 0.06f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "fall_immunity"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().getMsgId().equals("fall")) {
                    event.setAmount(0);
                    event.setCanceled(true);
                }
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "trajectory_reading"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().isProjectile()) event.setAmount(event.getAmount() * 0.75f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "projectile_parry"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().isProjectile()) event.setAmount(event.getAmount() * 0.55f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "ballistic_shed"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().isProjectile()) event.setAmount(event.getAmount() * 0.35f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "projectile_refraction"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().isProjectile()) event.setAmount(event.getAmount() * 0.15f);
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "projectile_immunity"; }
            @Override public void apply(LivingHurtEvent event) {
                if (event.getSource().isProjectile()) {
                    event.setAmount(0);
                    event.setCanceled(true);

                    Entity direct = event.getSource().getDirectEntity();
                    LivingEntity defender = event.getEntityLiving();
                    if (direct != null && !direct.level.isClientSide) {
                        Vector3d reflected = direct.getDeltaMovement().scale(-1.2D);
                        if (reflected.lengthSqr() < 1.0E-4D) {
                            Vector3d look = defender.getLookAngle();
                            reflected = look.scale(1.1D);
                        }
                        direct.setDeltaMovement(reflected.x, Math.max(0.05D, reflected.y), reflected.z);
                        direct.hurtMarked = true;

                        if (direct instanceof ProjectileEntity) {
                            ((ProjectileEntity) direct).setOwner(defender);
                        }
                    }
                }
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "water_breathing"; }
            @Override public void apply(LivingHurtEvent event) {
                if ("drown".equals(event.getSource().getMsgId())) {
                    event.setAmount(event.getAmount() * 0.75F);
                }
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "water_swiftness"; }
            @Override public void apply(LivingHurtEvent event) {
                if ("drown".equals(event.getSource().getMsgId())) {
                    event.setAmount(event.getAmount() * 0.45F);
                }
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "water_predator"; }
            @Override public void apply(LivingHurtEvent event) {
                if ("drown".equals(event.getSource().getMsgId())) {
                    event.setAmount(event.getAmount() * 0.2F);
                }
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "water_mastery"; }
            @Override public void apply(LivingHurtEvent event) {
                if ("drown".equals(event.getSource().getMsgId())) {
                    event.setAmount(event.getAmount() * 0.05F);
                }
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "abyssal_sovereign"; }
            @Override public void apply(LivingHurtEvent event) {
                if ("drown".equals(event.getSource().getMsgId())) {
                    event.setAmount(0);
                    event.setCanceled(true);
                }
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "pain_numbing_i"; }
            @Override public void apply(LivingHurtEvent event) {}
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "pain_numbing_ii"; }
            @Override public void apply(LivingHurtEvent event) {}
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "pain_numbing_iii"; }
            @Override public void apply(LivingHurtEvent event) {}
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "pain_numbing_iv"; }
            @Override public void apply(LivingHurtEvent event) {}
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "pain_numbing_v"; }
            @Override public void apply(LivingHurtEvent event) {}
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "debuff_resistance"; }
            @Override public void apply(LivingHurtEvent event) {
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "debuff_purge"; }
            @Override public void apply(LivingHurtEvent event) {
            }
        });

        register(new IAdaptationStrategy() {
            @Override public String getId() { return "debuff_immunity"; }
            @Override public void apply(LivingHurtEvent event) {
            }
        });

        PATHS.put("fall", FALL_PATH);
        PATHS.put("default_physical", PHYSICAL_PATH);
        PATHS.put("projectile", PROJECTILE_PATH);
        PATHS.put("fire", FIRE_PATH);
        PATHS.put("magic", MAGIC_PATH);
        PATHS.put("explosion", BLAST_PATH);
        PATHS.put("water", WATER_PATH);
        PATHS.put("environment:water", WATER_PATH);
        PATHS.put("drown", WATER_PATH);
        PATHS.put("special:pain", PAIN_PATH);
        PATHS.put("negative_effect", NEGATIVE_EFFECT_PATH);
    }

    public static void register(IAdaptationStrategy strategy) {
        REGISTRY.put(strategy.getId(), strategy);
    }

    public static IAdaptationStrategy get(String id) {
        return REGISTRY.get(id);
    }

    public static String getNextStrategyId(String key, Collection<String> learned) {
        List<String> path = getPathForKey(key);
        for (String id : path) {
            if (!learned.contains(id)) {
                return id;
            }
        }
        return null;
    }

    public static boolean hasCompleteAdaptation(String key, Collection<String> learned) {
        List<String> path = getPathForKey(key);
        return !path.isEmpty() && learned.contains(path.get(path.size() - 1));
    }

    public static boolean isNegativeEffectKey(String key) {
        return key.startsWith("effect:");
    }

    private static List<String> getPathForKey(String key) {
        if (isNegativeEffectKey(key)) {
            return NEGATIVE_EFFECT_PATH;
        }
        if (key.contains(":projectile") || key.contains("arrow") || key.contains("trident")) {
            return PROJECTILE_PATH;
        }
        if (key.contains(":explosion") || key.contains("explosion")) {
            return BLAST_PATH;
        }
        if (key.contains("fire") || key.contains("lava") || key.contains("hot_floor") || key.contains("in_fire") || key.contains("on_fire")) {
            return FIRE_PATH;
        }
        if (key.contains("magic") || key.contains("indirectMagic") || key.contains("wither") || key.contains("dragonBreath")) {
            return MAGIC_PATH;
        }
        if (key.contains("water") || key.contains("drown")) {
            return WATER_PATH;
        }
        if (key.equals("fall")) {
            return FALL_PATH;
        }
        return PHYSICAL_PATH;
    }
}
