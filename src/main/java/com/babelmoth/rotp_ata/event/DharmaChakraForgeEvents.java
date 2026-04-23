package com.babelmoth.rotp_ata.event;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.adaptation.AdaptationManager;
import com.babelmoth.rotp_ata.capability.AdaptationCapProvider;
import com.babelmoth.rotp_ata.capability.DharmaWheelHostCapProvider;
import com.babelmoth.rotp_ata.init.InitStands;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DharmaChakraForgeEvents {
    private static final ResourceLocation ADAPTATION_CAP = new ResourceLocation(AddonMain.MOD_ID, "adaptation_memory");
    private static final ResourceLocation DHARMA_WHEEL_HOST_CAP = new ResourceLocation(AddonMain.MOD_ID, "dharma_wheel_host");
    private static final java.util.UUID BODY_REINFORCEMENT_DAMAGE_UUID = java.util.UUID.fromString("c3f05f27-b6d8-4cb7-995a-bcfefd24a2f2");
    private static final double BORROWED_WHEEL_SYNC_RANGE = 64.0D;

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof PlayerEntity) {
            event.addCapability(ADAPTATION_CAP, new AdaptationCapProvider());
        }
        if (event.getObject() instanceof LivingEntity && !(event.getObject() instanceof PlayerEntity)) {
            event.addCapability(DHARMA_WHEEL_HOST_CAP, new DharmaWheelHostCapProvider());
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) event.getEntity();
            AdaptationManager.handleDefensiveAdaptation(player, event);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPotionAdded(PotionEvent.PotionApplicableEvent event) {
        if (!(event.getEntityLiving() instanceof PlayerEntity)) {
            return;
        }

        PlayerEntity player = (PlayerEntity) event.getEntityLiving();
        EffectInstance effectInstance = event.getPotionEffect();
        if (effectInstance == null || effectInstance.getEffect() == null || effectInstance.getEffect().isBeneficial()) {
            return;
        }

        AdaptationManager.handleNegativeEffectAdaptation(player, effectInstance);
        if (AdaptationManager.shouldNegateNegativeEffect(player, effectInstance)) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) event.getEntity();

            com.github.standobyte.jojo.power.impl.stand.IStandPower.getStandPowerOptional(player).ifPresent(power -> {
                if (power.getType() == InitStands.STAND_DHARMA_CHAKRA.getStandType() && power.isActive() && !AdaptationManager.hasTransferredWheel(player)) {
                    event.setCanceled(true);
                    if (player.getHealth() <= 0) {
                        player.setHealth(0.5f);
                    }
                }
            });

            AdaptationManager.handleDeathAdaptation(player, event);
            return;
        }

        if (event.getEntity() instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) event.getEntity();
            living.getCapability(DharmaWheelHostCapProvider.CAPABILITY).ifPresent(hostCap -> {
                if (!hostCap.hasWheel() || hostCap.getOwnerUuid() == null || living.level.isClientSide()) {
                    return;
                }
                PlayerEntity owner = living.level.getPlayerByUUID(hostCap.getOwnerUuid());
                if (owner != null) {
                    AdaptationManager.recallWheelFromHost(owner, living);
                }
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        PlayerEntity player = event.getPlayer();
        player.getCapability(AdaptationCapProvider.CAPABILITY).ifPresent(cap -> {
            AdaptationManager.syncCapability(player, cap);
        });
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        PlayerEntity player = event.getPlayer();
        player.getCapability(AdaptationCapProvider.CAPABILITY).ifPresent(cap -> {
            AdaptationManager.syncCapability(player, cap);
        });
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.Clone event) {
        PlayerEntity oldPlayer = event.getOriginal();
        PlayerEntity newPlayer = event.getPlayer();
        if (!event.isWasDeath()) {
            oldPlayer.getCapability(AdaptationCapProvider.CAPABILITY).ifPresent(oldCap -> {
                newPlayer.getCapability(AdaptationCapProvider.CAPABILITY).ifPresent(newCap -> {
                    newCap.deserializeNBT(oldCap.serializeNBT());
                    AdaptationManager.syncCapability(newPlayer, newCap);
                });
            });
        }
        else {
            newPlayer.getCapability(AdaptationCapProvider.CAPABILITY).ifPresent(cap -> {
                AdaptationManager.syncCapability(newPlayer, cap);
            });
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onWorldTick(net.minecraftforge.event.TickEvent.WorldTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (event.world.isClientSide()) return;

        long gameTime = event.world.getGameTime();
        Set<Integer> syncedHosts = gameTime % 20 == 0 ? new HashSet<>() : null;

        for (PlayerEntity player : event.world.players()) {
            AdaptationManager.onPlayerTick(player, false);
            handleDharmaChakraTick(player);

            if (syncedHosts != null) {
                AxisAlignedBB searchBox = player.getBoundingBox().inflate(BORROWED_WHEEL_SYNC_RANGE);
                for (LivingEntity living : event.world.getEntitiesOfClass(LivingEntity.class, searchBox, living -> !(living instanceof PlayerEntity))) {
                    if (syncedHosts.add(living.getId())) {
                        AdaptationManager.syncBorrowedWheelAdaptation(living);
                    }
                }
            }
        }
    }

    private static void handleDharmaChakraTick(PlayerEntity player) {
        if (player.level.isClientSide) return;

        ModifiableAttributeInstance attackDamageAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamageAttr != null) {
            AttributeModifier existing = attackDamageAttr.getModifier(BODY_REINFORCEMENT_DAMAGE_UUID);
            float bonusDamage = AdaptationManager.getBodyDamageBonus(player);
            if (bonusDamage > 0) {
                if (existing == null || Math.abs(existing.getAmount() - bonusDamage) > 0.001) {
                    if (existing != null) {
                        attackDamageAttr.removeModifier(BODY_REINFORCEMENT_DAMAGE_UUID);
                    }
                    attackDamageAttr.addTransientModifier(new AttributeModifier(BODY_REINFORCEMENT_DAMAGE_UUID, "DharmaBodyReinforcementDamage", bonusDamage, AttributeModifier.Operation.ADDITION));
                }
            }
            else if (existing != null) {
                attackDamageAttr.removeModifier(BODY_REINFORCEMENT_DAMAGE_UUID);
            }
        }

        com.github.standobyte.jojo.power.impl.stand.IStandPower.getStandPowerOptional(player).ifPresent(power -> {
            if (power.getType() != InitStands.STAND_DHARMA_CHAKRA.getStandType()) {
                return;
            }

            player.getCapability(AdaptationCapProvider.CAPABILITY).ifPresent(cap -> {
                if (cap.isStaminaDepleted() && power.getStamina() >= power.getMaxStamina()) {
                    cap.setStaminaDepleted(false);
                }

                if (power.isActive() && cap.isStaminaDepleted()) {
                    if (power.getType() instanceof com.github.standobyte.jojo.power.impl.stand.type.EntityStandType) {
                        ((com.github.standobyte.jojo.power.impl.stand.type.EntityStandType<?>) power.getType()).forceUnsummon(player, power);
                    }
                    player.displayClientMessage(
                            new net.minecraft.util.text.TranslationTextComponent("jojo.message.dharma_chakra_needs_full_stamina"),
                            true);
                    return;
                }
            });

            if (power.isActive()) {
                AdaptationManager.purgeNegativeEffects(player);

                if (AdaptationManager.hasAnyStrategy(player, "environment:water", "water_breathing", "water_swiftness", "water_predator", "water_mastery", "abyssal_sovereign")) {
                    if (player.isInWaterOrBubble()) {
                        player.setAirSupply(player.getMaxAirSupply());
                    }
                }
                if (AdaptationManager.hasAnyStrategy(player, "environment:water", "water_swiftness", "water_predator", "water_mastery", "abyssal_sovereign") && player.isInWaterOrBubble()) {
                    player.addEffect(new net.minecraft.potion.EffectInstance(net.minecraft.potion.Effects.DOLPHINS_GRACE, 30, 0, false, false, false));
                }
                if (AdaptationManager.hasAnyStrategy(player, "environment:water", "water_mastery", "abyssal_sovereign") && player.isInWaterOrBubble()) {
                    player.addEffect(new net.minecraft.potion.EffectInstance(net.minecraft.potion.Effects.WATER_BREATHING, 40, 0, false, false, false));
                }
                if (AdaptationManager.hasAnyStrategy(player, "environment:water", "water_predator", "water_mastery", "abyssal_sovereign") && player.isInWaterOrBubble()) {
                    player.addEffect(new net.minecraft.potion.EffectInstance(net.minecraft.potion.Effects.CONDUIT_POWER, 40, 0, false, false, false));
                }
                if (AdaptationManager.hasStrategy(player, "environment:water", "abyssal_sovereign") && player.isInWaterOrBubble()) {
                    player.addEffect(new net.minecraft.potion.EffectInstance(net.minecraft.potion.Effects.DAMAGE_RESISTANCE, 30, 0, false, false, false));
                }

                if (AdaptationManager.hasAnyStrategy(player, "inFire", "fire_absorption", "fire_immunity")
                        || AdaptationManager.hasAnyStrategy(player, "onFire", "fire_absorption", "fire_immunity")
                        || AdaptationManager.hasAnyStrategy(player, "lava", "fire_absorption", "fire_immunity")) {
                    player.clearFire();
                    player.setRemainingFireTicks(0);
                }

                if (power.getStamina() <= 0) {
                    if (power.getType() instanceof com.github.standobyte.jojo.power.impl.stand.type.EntityStandType) {
                        ((com.github.standobyte.jojo.power.impl.stand.type.EntityStandType<?>) power.getType()).forceUnsummon(player, power);
                        player.getCapability(AdaptationCapProvider.CAPABILITY).ifPresent(cap -> {
                            cap.setStaminaDepleted(true);
                        });
                    }
                }

                float healthRatio = player.getHealth() / player.getMaxHealth();
                if (healthRatio < 0.20f) {
                    int amplifier = healthRatio < 0.10f ? 2 : (healthRatio < 0.15f ? 1 : 0);

                    player.addEffect(new net.minecraft.potion.EffectInstance(
                            net.minecraft.potion.Effects.MOVEMENT_SLOWDOWN, 40, amplifier, false, false, true));
                    player.addEffect(new net.minecraft.potion.EffectInstance(
                            net.minecraft.potion.Effects.WEAKNESS, 40, amplifier, false, false, true));
                    player.addEffect(new net.minecraft.potion.EffectInstance(
                            net.minecraft.potion.Effects.BLINDNESS, 40, 0, false, false, true));
                    player.addEffect(new net.minecraft.potion.EffectInstance(
                            net.minecraft.potion.Effects.DIG_SLOWDOWN, 40, amplifier, false, false, true));
                }
            }
        });
    }
}
