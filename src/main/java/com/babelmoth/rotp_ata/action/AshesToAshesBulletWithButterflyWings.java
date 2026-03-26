package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.capability.MothPoolProvider;
import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.babelmoth.rotp_ata.event.AshesToAshesEventHandler;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.power.impl.stand.IStandManifestation;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.github.standobyte.jojo.init.ModStatusEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.world.World;

public class AshesToAshesBulletWithButterflyWings extends StandAction {

    public AshesToAshesBulletWithButterflyWings(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public float getHeldWalkSpeed() {
        return 0.5f;
    }

    @Override
    public int getHoldDurationMax(IStandPower standPower) {
        return Integer.MAX_VALUE;
    }

    @Override
    public ActionConditionResult checkSpecificConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        IStandManifestation manifestation = power.getStandManifestation();
        if (manifestation instanceof StandEntity) {
            StandEntity stand = (StandEntity) manifestation;
            if (stand.isManuallyControlled()) {
                return ActionConditionResult.NEGATIVE;
            }
        }
        return ActionConditionResult.POSITIVE;
    }

    @Override
    public void onHoldTick(World world, LivingEntity user, IStandPower power, int ticksHeld, ActionTarget target, boolean requirementsMet) {
        if (!world.isClientSide && requirementsMet) {
            int fireInterval = user.hasEffect(ModStatusEffects.RESOLVE.get()) ? 3 : 5;
            if (ticksHeld % fireInterval == 0) {
                int momentumCost = 5;
                float staminaCost = 50.0f;

                if (power.getStamina() >= staminaCost) {
                    user.getCapability(MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
                        int slot = pool.allocateSlotWithPriority(true);
                        if (slot == -1) {
                            return;
                        }

                        int hamonUsed = consumeMothEnergy(user, momentumCost, power);
                        if (hamonUsed < 0) {
                            pool.recallMoth(slot);
                            return;
                        }

                        power.consumeStamina(staminaCost);

                        FossilMothEntity bulletMoth = new FossilMothEntity(world, user);
                        bulletMoth.setMothPoolIndex(slot);

                        if (hamonUsed > 0) {
                            bulletMoth.setHamonEnergy(1);
                        }

                        double angle = world.random.nextDouble() * Math.PI * 2;
                        double dist = 1.0 + world.random.nextDouble() * 1.5;
                        double px = user.getX() + Math.cos(angle) * dist;
                        double pz = user.getZ() + Math.sin(angle) * dist;
                        double py = user.getEyeY() + (world.random.nextDouble() - 0.5) * 1.5;

                        bulletMoth.setPos(px, py, pz);
                        bulletMoth.yRot = user.yRot;
                        bulletMoth.xRot = user.xRot;
                        bulletMoth.setIsBullet(true);

                        net.minecraft.util.math.vector.Vector3d lookDir = user.getViewVector(1.0f);
                        lookDir = lookDir.add(
                            (world.random.nextDouble() - 0.5) * 0.1,
                            (world.random.nextDouble() - 0.5) * 0.1,
                            (world.random.nextDouble() - 0.5) * 0.1).normalize();

                        float speed = user.hasEffect(ModStatusEffects.RESOLVE.get()) ? 5.0f : 3.5f;
                        bulletMoth.piercingFire(lookDir, speed);

                        world.addFreshEntity(bulletMoth);
                        if (user instanceof ServerPlayerEntity) {
                            pool.sync((ServerPlayerEntity) user);
                        }

                        world.playSound(null, px, py, pz,
                            SoundEvents.FIRECHARGE_USE, SoundCategory.PLAYERS, 0.5f, 1.5f + world.random.nextFloat() * 0.5f);
                    });
                }
            }
        }
    }

    private int consumeMothEnergy(LivingEntity user, int amount, IStandPower power) {
        if (amount <= 0) return 0;

        com.github.standobyte.jojo.power.impl.stand.IStandManifestation manifestation = power.getStandManifestation();
        if (manifestation instanceof com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity) {
            com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity stand = (com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity) manifestation;
            if (stand.getGlobalTotalEnergy() < amount) return -1;
            int hamonBefore = stand.getGlobalHamonEnergy();
            stand.consumeEnergyPrioritizeHamon(amount);
            int hamonAfter = stand.getGlobalHamonEnergy();
            if (user instanceof net.minecraft.entity.player.ServerPlayerEntity) {
                user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(p -> p.sync((net.minecraft.entity.player.ServerPlayerEntity) user));
            }
            return hamonBefore - hamonAfter;
        }

        return user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY)
            .map(pool -> {
                int totalHamon = pool.getTotalHamonEnergy();
                int availableKinetic = pool.getAvailableKinetic();
                if (totalHamon + availableKinetic < amount) return -1;
                int hamonToUse = Math.min(totalHamon, amount);
                int remaining = amount - hamonToUse;
                int kineticToUse = Math.min(availableKinetic, remaining);
                if (hamonToUse > 0) pool.consumeHamon(hamonToUse);
                if (kineticToUse > 0) pool.consumeKinetic(kineticToUse);
                if (user instanceof net.minecraft.entity.player.ServerPlayerEntity) {
                    pool.sync((net.minecraft.entity.player.ServerPlayerEntity) user);
                }
                return hamonToUse;
            })
            .orElse(-1);
    }
}