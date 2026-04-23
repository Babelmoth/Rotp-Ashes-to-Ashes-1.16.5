package com.babelmoth.rotp_ata.adaptation;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.capability.AdaptationCapProvider;
import com.babelmoth.rotp_ata.capability.DharmaWheelHostCapProvider;
import com.babelmoth.rotp_ata.capability.IAdaptationCap;
import com.babelmoth.rotp_ata.capability.IDharmaWheelHostCap;
import com.babelmoth.rotp_ata.init.InitStands;
import com.babelmoth.rotp_ata.networking.DharmaChakraPacketManager;
import com.babelmoth.rotp_ata.networking.S2CAdaptationRotationPacket;
import com.babelmoth.rotp_ata.networking.S2CAdaptationSyncPacket;
import com.github.standobyte.jojo.capability.world.TimeStopHandler;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AdaptationManager {
    private static final int ADAPTATION_COOLDOWN = 20 * 20;
    private static final int TS_ADAPTATION_INTERVAL = 100;
    private static final int MAX_TIME_STOP_STAGE = 4;
    private static final float DEATH_ADAPTATION_THRESHOLD = 6.0f;
    private static final ResourceLocation TIME_STOP_ADVANCEMENT = new ResourceLocation("jojo", "jojo/time_ability");
    private static final String TIME_STOP_PENDING_KEY = "special:time_stop";
    private static final String WATER_ENVIRONMENT_KEY = "environment:water";
    private static final String PAIN_PENDING_KEY = "special:pain";

    public static void handleDefensiveAdaptation(PlayerEntity player, LivingHurtEvent event) {
        if (player.level.isClientSide()) return;

        if (IStandPower.getStandPowerOptional(player).map(stand -> stand.getType() != InitStands.STAND_DHARMA_CHAKRA.getStandType() || !stand.isActive()).orElse(true)) {
            return;
        }

        player.getCapability(AdaptationCapProvider.CAPABILITY).ifPresent(cap -> {
            String damageKey = generateDamageKey(event);
            Collection<String> pool = cap.getStrategies(damageKey);
            cap.queuePending(damageKey, event.getAmount(), player.level.getGameTime());
            cap.queuePending(PAIN_PENDING_KEY, event.getAmount(), player.level.getGameTime());
            cap.setLastInjuredTick(player.level.getGameTime());
            cap.setLastDamageSource(damageKey, event.getSource().getMsgId());
            cap.setLastDamageAmount(damageKey, event.getAmount());

            if (hasFinalImmunityForEvent(cap, event, damageKey)) {
                event.setAmount(0);
                event.setCanceled(true);
                return;
            }

            applyPool(event, pool);

            float painIgnoreThreshold = getPainIgnoreThreshold(player);
            if (event.getAmount() <= painIgnoreThreshold) {
                event.getEntityLiving().hurtTime = 0;
                event.getEntityLiving().hurtDuration = 0;
                event.getEntityLiving().invulnerableTime = Math.min(event.getEntityLiving().invulnerableTime, 2);
            }

            if (event.getAmount() <= 0.05f) {
                event.setAmount(0);
                event.setCanceled(true);
            }
        });
    }

    public static void handleNegativeEffectAdaptation(PlayerEntity player, EffectInstance effectInstance) {
        if (player.level.isClientSide()) return;
        if (effectInstance == null || effectInstance.getEffect() == null || effectInstance.getEffect().isBeneficial()) return;

        if (IStandPower.getStandPowerOptional(player).map(stand -> stand.getType() != InitStands.STAND_DHARMA_CHAKRA.getStandType() || !stand.isActive()).orElse(true)) {
            return;
        }

        player.getCapability(AdaptationCapProvider.CAPABILITY).ifPresent(cap -> {
            String effectKey = effectKey(effectInstance.getEffect());
            cap.queuePending(effectKey, effectInstance.getAmplifier() + 1.0f, player.level.getGameTime());
            cap.setLastInjuredTick(player.level.getGameTime());
        });
    }

    public static boolean shouldNegateNegativeEffect(PlayerEntity player, EffectInstance effectInstance) {
        if (effectInstance == null || effectInstance.getEffect() == null || effectInstance.getEffect().isBeneficial()) {
            return false;
        }

        return player.getCapability(AdaptationCapProvider.CAPABILITY)
                .map(cap -> hasStrategy(cap, effectKey(effectInstance.getEffect()), "debuff_immunity"))
                .orElse(false);
    }

    public static void handleDeathAdaptation(PlayerEntity player, LivingDeathEvent event) {
        if (player.level.isClientSide()) return;

        IStandPower standPower = IStandPower.getPlayerStandPower(player);
        if (standPower.getType() != InitStands.STAND_DHARMA_CHAKRA.getStandType() || !standPower.isActive()) {
            return;
        }

        player.getCapability(AdaptationCapProvider.CAPABILITY).ifPresent(cap -> {
            float lastDamage = 0;
            for (String key : cap.getAllDamageKeys()) {
                lastDamage = Math.max(lastDamage, cap.getLastDamageAmount(key));
            }

            if (lastDamage > 0 && lastDamage <= DEATH_ADAPTATION_THRESHOLD) {
                player.deathTime = 0;
                player.hurtTime = 0;

                com.github.standobyte.jojo.power.impl.stand.IStandManifestation manifestation = standPower.getStandManifestation();
                if (manifestation instanceof net.minecraft.entity.LivingEntity) {
                    net.minecraft.entity.LivingEntity standEntity = (net.minecraft.entity.LivingEntity) manifestation;
                    if (standEntity.removed) {
                        standEntity.removed = false;
                        standEntity.revive();
                    }
                    standEntity.setHealth(standEntity.getMaxHealth());
                    standEntity.deathTime = 0;
                    standEntity.hurtTime = 0;
                }

                com.github.standobyte.jojo.util.mc.MCUtil.onLivingResurrect(player);
                evolve(player);
                syncCapability(player, cap);
            }
        });
    }

    public static void onPlayerTick(PlayerEntity player, boolean forceTimeStop) {
        if (player.level.isClientSide()) return;

        long gameTime = player.level.getGameTime();
        boolean isTick20 = gameTime % 20 == 0;

        boolean isActive = IStandPower.getStandPowerOptional(player).map(stand -> stand.getType() == InitStands.STAND_DHARMA_CHAKRA.getStandType() && stand.isActive()).orElse(false);
        boolean isDharma = IStandPower.getStandPowerOptional(player).map(stand -> stand.getType() == InitStands.STAND_DHARMA_CHAKRA.getStandType()).orElse(false);
        if (!isDharma) return;

        if (!isActive) {
            player.getCapability(AdaptationCapProvider.CAPABILITY).ifPresent(cap -> {
                if (!cap.getAllDamageKeys().isEmpty() || cap.getTimeStopStage() > 0 || cap.getTimeStopTicks() > 0) {
                    cap.clear();
                    syncCapability(player, cap);
                }
            });
            return;
        }

        player.getCapability(AdaptationCapProvider.CAPABILITY).ifPresent(cap -> {
            long currentTime = player.level.getGameTime();
            cap.tickAdaptationCooldown();

            boolean isTimeStopped = forceTimeStop || TimeStopHandler.isTimeStopped(player.level, player.blockPosition());
            int stage = cap.getTimeStopStage();

            if (isTimeStopped) {
                if (stage >= 1) {
                    EffectInstance tsEffect = new EffectInstance(com.github.standobyte.jojo.init.ModStatusEffects.TIME_STOP.get(), 40, 0, false, false, false);
                    player.addEffect(tsEffect);
                }

                if (player instanceof ServerPlayerEntity) {
                    player.level.getCapability(com.github.standobyte.jojo.capability.world.WorldUtilCapProvider.CAPABILITY)
                        .ifPresent(worldCap -> worldCap.getTimeStopHandler().sendPlayerState((ServerPlayerEntity) player));
                }

                cap.incrementTimeStopTicks();
                long tsTicks = cap.getTimeStopTicks();

                if (stage < MAX_TIME_STOP_STAGE && tsTicks >= (long) (stage + 1) * TS_ADAPTATION_INTERVAL && !cap.hasPending(TIME_STOP_PENDING_KEY)) {
                    cap.queuePending(TIME_STOP_PENDING_KEY, 10.0f + (stage + 1) * 5.0f, currentTime);
                    syncCapability(player, cap);
                }

                if (stage == 2) {
                    player.addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN, 20, 4, false, false, false));
                }

                if (stage == 3) {
                    player.addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN, 20, 1, false, false, false));
                }
            }

            if (isTick20) {
                maintainNegativeEffectAdaptation(player, cap);
                maintainEnvironmentalAdaptation(player, cap);
            }

            if (cap.getAdaptationCooldownTicks() <= 0 && !cap.getAllPendingKeys().isEmpty()) {
                tryResolveAllPendingAdaptations(player, cap, currentTime);
            }
        });
    }

    private static void tryResolveAllPendingAdaptations(PlayerEntity player, IAdaptationCap cap, long currentTick) {
        List<String> pendingKeys = cap.getAllPendingKeys().stream()
                .sorted((a, b) -> Double.compare(computePendingPriority(cap, b), computePendingPriority(cap, a)))
                .collect(Collectors.toList());
        if (pendingKeys.isEmpty()) {
            return;
        }

        boolean gained = false;
        for (String pendingKey : pendingKeys) {
            if (TIME_STOP_PENDING_KEY.equals(pendingKey)) {
                if (resolveTimeStopAdaptation(player, cap, currentTick) != null) {
                    gained = true;
                }
                continue;
            }

            Collection<String> learned = cap.getStrategies(pendingKey);
            String nextStrategyId = AdaptationStrategies.getNextStrategyId(pendingKey, learned);
            if (nextStrategyId == null) {
                cap.removePending(pendingKey);
                continue;
            }

            cap.addStrategy(pendingKey, nextStrategyId);
            cap.setCurrentTrialKey(pendingKey);
            cap.setCurrentStrategyId(nextStrategyId);
            cap.setLastAdaptationTick(pendingKey, currentTick);
            cap.removePending(pendingKey);
            gained = true;
        }

        if (gained) {
            cap.setAdaptationCooldownTicks(ADAPTATION_COOLDOWN);
            evolve(player);
            purgeNegativeEffects(player);
            syncCapability(player, cap);
        }
    }

    private static String resolveTimeStopAdaptation(PlayerEntity player, IAdaptationCap cap, long currentTick) {
        int currentStage = cap.getTimeStopStage();
        if (currentStage >= MAX_TIME_STOP_STAGE) {
            cap.removePending(TIME_STOP_PENDING_KEY);
            return null;
        }

        int newStage = currentStage + 1;
        cap.setTimeStopStage(newStage);
        cap.setCurrentTrialKey(TIME_STOP_PENDING_KEY);
        cap.setCurrentStrategyId("time_stop_stage_" + newStage);
        cap.setLastAdaptationTick(TIME_STOP_PENDING_KEY, currentTick);
        cap.removePending(TIME_STOP_PENDING_KEY);

        if (newStage == MAX_TIME_STOP_STAGE && player instanceof ServerPlayerEntity) {
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
            Advancement advancement = serverPlayer.server.getAdvancements().getAdvancement(TIME_STOP_ADVANCEMENT);
            if (advancement != null) {
                AdvancementProgress progress = serverPlayer.getAdvancements().getOrStartProgress(advancement);
                if (!progress.isDone()) {
                    for (String criterion : progress.getRemainingCriteria()) {
                        serverPlayer.getAdvancements().award(advancement, criterion);
                        break;
                    }
                }
            }
        }

        return "time_stop_stage_" + newStage;
    }

    private static void applyPool(LivingHurtEvent event, Collection<String> pool) {
        for (String id : pool) {
            IAdaptationStrategy strategy = AdaptationStrategies.get(id);
            if (strategy != null) {
                strategy.apply(event);
            }
        }
    }

    private static void maintainNegativeEffectAdaptation(PlayerEntity player, IAdaptationCap cap) {
        Set<Effect> activeEffects = new java.util.HashSet<>();
        List<Effect> effectsToRemove = new ArrayList<>();
        for (EffectInstance instance : player.getActiveEffects()) {
            if (instance.getEffect().isBeneficial()) {
                continue;
            }

            String key = effectKey(instance.getEffect());
            activeEffects.add(instance.getEffect());
            cap.queuePending(key, instance.getAmplifier() + 1.0f, player.level.getGameTime());

            if (hasStrategy(cap, key, "debuff_immunity") || hasStrategy(cap, key, "debuff_purge")) {
                effectsToRemove.add(instance.getEffect());
            }
        }

        for (Effect effect : effectsToRemove) {
            player.removeEffect(effect);
        }

        if (!activeEffects.isEmpty() && cap.getAdaptationCooldownTicks() <= 0) {
            syncCapability(player, cap);
        }
    }

    private static void maintainEnvironmentalAdaptation(PlayerEntity player, IAdaptationCap cap) {
        if (player.isInWaterOrBubble()) {
            float severity = 1.0F;
            if (player.getAirSupply() < player.getMaxAirSupply() * 0.4F) {
                severity += 1.5F;
            }
            cap.queuePending(WATER_ENVIRONMENT_KEY, severity, player.level.getGameTime());
            cap.setLastInjuredTick(player.level.getGameTime());
        }
    }

    public static void purgeNegativeEffects(PlayerEntity player) {
        List<Effect> effectsToRemove = new ArrayList<>();
        for (EffectInstance instance : player.getActiveEffects()) {
            if (instance.getEffect().isBeneficial()) {
                continue;
            }
            effectsToRemove.add(instance.getEffect());
        }
        for (Effect effect : effectsToRemove) {
            player.removeEffect(effect);
        }
    }

    public static boolean hasStrategy(PlayerEntity player, String key, String strategyId) {
        return player.getCapability(AdaptationCapProvider.CAPABILITY)
                .map(cap -> cap.getStrategies(key).contains(strategyId))
                .orElse(false);
    }

    public static boolean hasAnyStrategy(PlayerEntity player, String key, String... strategyIds) {
        return player.getCapability(AdaptationCapProvider.CAPABILITY)
                .map(cap -> {
                    Collection<String> strategies = cap.getStrategies(key);
                    for (String id : strategyIds) {
                        if (strategies.contains(id)) {
                            return true;
                        }
                    }
                    return false;
                })
                .orElse(false);
    }

    public static int getPainAdaptationLevel(PlayerEntity player) {
        return player.getCapability(AdaptationCapProvider.CAPABILITY)
                .map(cap -> {
                    Collection<String> learned = cap.getStrategies(PAIN_PENDING_KEY);
                    if (learned.contains("pain_numbing_v")) return 5;
                    if (learned.contains("pain_numbing_iv")) return 4;
                    if (learned.contains("pain_numbing_iii")) return 3;
                    if (learned.contains("pain_numbing_ii")) return 2;
                    if (learned.contains("pain_numbing_i")) return 1;
                    return 0;
                })
                .orElse(0);
    }

    public static float getPainIgnoreThreshold(PlayerEntity player) {
        int level = getPainAdaptationLevel(player);
        switch (level) {
            case 1:
                return 0.75F;
            case 2:
                return 1.75F;
            case 3:
                return 3.0F;
            case 4:
                return 4.5F;
            case 5:
                return 6.5F;
            default:
                return 0.0F;
        }
    }

    public static boolean hasBorrowedWheel(LivingEntity living) {
        return living.getCapability(DharmaWheelHostCapProvider.CAPABILITY)
                .map(IDharmaWheelHostCap::hasWheel)
                .orElse(false);
    }

    public static boolean hasTransferredWheel(PlayerEntity player) {
        return player.getCapability(AdaptationCapProvider.CAPABILITY)
                .map(IAdaptationCap::isStaminaDepleted)
                .orElse(false);
    }

    public static boolean giveWheelToTarget(PlayerEntity owner, LivingEntity target) {
        if (target == null || target == owner || !target.isAlive()) {
            return false;
        }
        if (hasBorrowedWheel(target)) {
            return false;
        }

        CompoundNBT adaptationData = owner.getCapability(AdaptationCapProvider.CAPABILITY)
                .map(IAdaptationCap::serializeNBT)
                .orElse(new CompoundNBT());

        boolean stored = target.getCapability(DharmaWheelHostCapProvider.CAPABILITY).map(hostCap -> {
            hostCap.setOwnerUuid(owner.getUUID());
            hostCap.setOwnerEntityId(owner.getId());
            hostCap.setHasWheel(true);
            hostCap.setStoredAdaptationData(adaptationData);
            hostCap.setLastSyncTick(owner.level.getGameTime());
            hostCap.setCachedHost(target);
            return true;
        }).orElse(false);

        if (!stored) {
            return false;
        }

        owner.getCapability(AdaptationCapProvider.CAPABILITY).ifPresent(cap -> {
            cap.setStaminaDepleted(true);
            syncCapability(owner, cap);
        });
        return true;
    }

    public static boolean recallWheel(PlayerEntity owner) {
        List<LivingEntity> candidates = owner.level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(128.0D),
                living -> living != owner && living.isAlive() && living.getCapability(DharmaWheelHostCapProvider.CAPABILITY)
                        .map(cap -> cap.hasWheel() && owner.getUUID().equals(cap.getOwnerUuid()))
                        .orElse(false));
        boolean recalled = false;
        for (LivingEntity living : candidates) {
            recalled |= recallWheelFromHost(owner, living);
        }
        if (!recalled) {
            owner.getCapability(AdaptationCapProvider.CAPABILITY).ifPresent(cap -> {
                cap.setStaminaDepleted(false);
                syncCapability(owner, cap);
            });
        }
        return true;
    }

    public static boolean recallWheelFromHost(PlayerEntity owner, LivingEntity host) {
        return host.getCapability(DharmaWheelHostCapProvider.CAPABILITY).map(hostCap -> {
            if (!hostCap.hasWheel() || !owner.getUUID().equals(hostCap.getOwnerUuid())) {
                return false;
            }

            CompoundNBT nbt = hostCap.getStoredAdaptationData();
            owner.getCapability(AdaptationCapProvider.CAPABILITY).ifPresent(ownerCap -> {
                if (!nbt.isEmpty()) {
                    ownerCap.deserializeNBT(nbt);
                }
                ownerCap.setStaminaDepleted(false);
                syncCapability(owner, ownerCap);
            });

            hostCap.setHasWheel(false);
            hostCap.setOwnerUuid(null);
            hostCap.setOwnerEntityId(-1);
            hostCap.setStoredAdaptationData(new CompoundNBT());
            hostCap.setCachedHost(null);
            return true;
        }).orElse(false);
    }

    public static void syncBorrowedWheelAdaptation(LivingEntity host) {
        if (host == null || host.level.isClientSide()) {
            return;
        }
        host.getCapability(DharmaWheelHostCapProvider.CAPABILITY).ifPresent(hostCap -> {
            if (!hostCap.hasWheel()) {
                return;
            }
            if (host.level.getGameTime() - hostCap.getLastSyncTick() < 10) {
                return;
            }
            hostCap.setLastSyncTick(host.level.getGameTime());
            host.getCapability(AdaptationCapProvider.CAPABILITY).ifPresent(adaptationCap -> hostCap.setStoredAdaptationData(adaptationCap.serializeNBT()));
        });
    }

    private static double computePendingPriority(IAdaptationCap cap, String key) {
        double score = 0;
        score += cap.getPendingCount(key) * 4.0;
        score += cap.getPendingSeverity(key) * 2.5;
        score += Math.min(200, cap.getPendingLastSeenTick(key)) * 0.001;

        if (TIME_STOP_PENDING_KEY.equals(key)) {
            score += 120;
        }

        if (PAIN_PENDING_KEY.equals(key)) {
            score += 40;
        }

        if (AdaptationStrategies.isNegativeEffectKey(key)) {
            score += 55;
            if (key.contains("blindness") || key.contains("weakness") || key.contains("movement_slowdown") || key.contains("dig_slowdown") || key.contains("bleeding") || key.contains("wither")) {
                score += 35;
            }
        }

        if (key.contains("magic") || key.contains("wither") || key.contains("dragonBreath")) {
            score += 45;
        }
        if (key.contains("explosion")) {
            score += 35;
        }
        if (key.contains("projectile")) {
            score += 25;
        }
        if (key.contains("fire") || key.contains("lava")) {
            score += 20;
        }
        if (key.contains("outOfWorld") || key.contains("genericKill")) {
            score += 80;
        }

        return score;
    }

    private static boolean hasStrategy(IAdaptationCap cap, String key, String strategyId) {
        return cap.getStrategies(key).contains(strategyId);
    }

    private static boolean hasFinalImmunityForEvent(IAdaptationCap cap, LivingHurtEvent event, String damageKey) {
        if (event.getSource().isProjectile()) {
            return hasFinalAcrossKeys(cap, "projectile_immunity", key -> key.contains(":projectile") || key.contains("arrow") || key.contains("trident"));
        }
        if (event.getSource().isExplosion()) {
            return hasFinalAcrossKeys(cap, "blast_immunity", key -> key.contains(":explosion") || key.contains("explosion"));
        }
        if (event.getSource().isFire() || isFireKey(damageKey)) {
            return hasFinalAcrossKeys(cap, "fire_immunity", AdaptationManager::isFireKey);
        }
        if (event.getSource().isMagic() || isMagicKey(damageKey)) {
            return hasFinalAcrossKeys(cap, "magic_immunity", AdaptationManager::isMagicKey);
        }
        if ("fall".equals(event.getSource().getMsgId())) {
            return hasFinalAcrossKeys(cap, "fall_immunity", key -> "fall".equals(key));
        }
        if ("drown".equals(event.getSource().getMsgId())) {
            return hasFinalAcrossKeys(cap, "abyssal_sovereign", key -> key.contains("drown") || key.contains("water"));
        }

        return hasFinalAcrossKeys(cap, "physical_immunity", key -> !isFireKey(key) && !isMagicKey(key)
                && !key.contains(":projectile") && !key.contains(":explosion") && !"fall".equals(key));
    }

    private static boolean hasFinalAcrossKeys(IAdaptationCap cap, String finalStrategyId, java.util.function.Predicate<String> keyFilter) {
        for (String key : cap.getAllDamageKeys()) {
            if (keyFilter.test(key) && cap.getStrategies(key).contains(finalStrategyId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFireKey(String key) {
        return key.contains("fire") || key.contains("lava") || key.contains("hot_floor") || key.contains("in_fire") || key.contains("on_fire");
    }

    private static boolean isMagicKey(String key) {
        return key.contains("magic") || key.contains("indirectMagic") || key.contains("wither") || key.contains("dragonBreath");
    }

    private static String effectKey(Effect effect) {
        ResourceLocation registryName = effect.getRegistryName();
        return "effect:" + (registryName != null ? registryName.toString() : effect.getDescriptionId());
    }

    private static void evolve(PlayerEntity player) {
        if (player.level.isClientSide()) return;
        if (player instanceof ServerPlayerEntity) {
            DharmaChakraPacketManager.sendToClientsTrackingAndSelf(new S2CAdaptationRotationPacket(player.getUUID()), player);
        }
    }

    public static void syncCapability(PlayerEntity player, IAdaptationCap cap) {
        if (player instanceof ServerPlayerEntity) {
            DharmaChakraPacketManager.sendToClientsTrackingAndSelf(new S2CAdaptationSyncPacket(player.getId(), cap.serializeNBT()), player);
        }
    }

    public static int getBodyReinforcementTier(LivingEntity entity) {
        if (!(entity instanceof PlayerEntity)) {
            return 0;
        }

        PlayerEntity player = (PlayerEntity) entity;
        boolean isDharmaActive = IStandPower.getStandPowerOptional(player)
                .map(stand -> stand.getType() == InitStands.STAND_DHARMA_CHAKRA.getStandType() && stand.isActive())
                .orElse(false);
        if (!isDharmaActive) {
            return 0;
        }

        return player.getCapability(AdaptationCapProvider.CAPABILITY)
                .map(cap -> {
                    int adaptedTypeCount = 0;
                    for (String key : cap.getAllDamageKeys()) {
                        if (!cap.getStrategies(key).isEmpty() && !TIME_STOP_PENDING_KEY.equals(key)) {
                            adaptedTypeCount++;
                        }
                    }

                    int tier = 1 + adaptedTypeCount / 2;
                    if (cap.getTimeStopStage() >= 2) {
                        tier += 1;
                    }
                    return Math.max(1, Math.min(7, tier));
                })
                .orElse(1);
    }

    public static float getBodyDamageBonus(LivingEntity entity) {
        int tier = getBodyReinforcementTier(entity);
        if (tier <= 0) {
            return 0.0F;
        }
        return 1.0F + (tier - 1) * 1.25F;
    }

    private static String generateDamageKey(LivingHurtEvent event) {
        String msgId = event.getSource().getMsgId();
        if (event.getSource().isProjectile()) return msgId + ":projectile";
        if (event.getSource().isExplosion()) return msgId + ":explosion";
        return msgId;
    }
}
