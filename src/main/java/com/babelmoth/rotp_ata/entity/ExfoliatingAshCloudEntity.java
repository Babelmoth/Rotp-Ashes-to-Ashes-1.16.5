package com.babelmoth.rotp_ata.entity;

import com.babelmoth.rotp_ata.init.InitEntities;
import com.babelmoth.rotp_ata.init.InitParticles;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.IPacket;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;

import java.util.List;
import java.util.OptionalInt;

public class ExfoliatingAshCloudEntity extends AreaEffectCloudEntity {

    private static final int AMBER_COLOR = 0xe7801a;
    private static final int HAMON_COLOR = 0xffdd00;
    private static java.util.function.Predicate<LivingEntity> localPlayerCheck;
    private boolean hamonInfused = false;

    public ExfoliatingAshCloudEntity(EntityType<? extends AreaEffectCloudEntity> entityType, World world) {
        super(entityType, world);
    }

    public ExfoliatingAshCloudEntity(World world, double x, double y, double z) {
        this(InitEntities.EXFOLIATING_ASH_CLOUD.get(), world);
        this.setPos(x, y, z);
    }

    public void setHamonInfused(boolean value) {
        this.hamonInfused = value;
    }

    public boolean isHamonInfused() {
        return this.hamonInfused;
    }

    public static void setLocalPlayerCheck(java.util.function.Predicate<LivingEntity> check) {
        localPlayerCheck = check;
    }

    @Override
    public void tick() {
        if (this.level.isClientSide && this.tickCount % 6 == 0) {
            float radius = this.getRadius();
            int particleCount = Math.max(6, (int) Math.ceil(radius * radius * 0.45F));
            for (int i = 0; i < particleCount; i++) {
                double theta = this.random.nextDouble() * Math.PI * 2;
                double phi = Math.acos(2 * this.random.nextDouble() - 1);
                double r = radius * Math.cbrt(this.random.nextDouble());
                double px = this.getX() + r * Math.sin(phi) * Math.cos(theta);
                double py = this.getY() + r * Math.cos(phi);
                double pz = this.getZ() + r * Math.sin(phi) * Math.sin(theta);
                double vx = (this.random.nextDouble() - 0.5D) * 0.02D * Math.max(1.0F, radius * 0.3F);
                double vy = this.random.nextDouble() * 0.03D + radius * 0.003D;
                double vz = (this.random.nextDouble() - 0.5D) * 0.02D * Math.max(1.0F, radius * 0.3F);

                if (hamonInfused) {
                    this.level.addParticle(com.github.standobyte.jojo.init.ModParticles.HAMON_SPARK.get(), px, py, pz, vx, vy, vz);
                }
                else {
                    this.level.addParticle(InitParticles.ASH_SMOKE_LARGE.get(), px, py, pz, vx, vy, vz);
                }
            }
        }

        if (this.level.isClientSide && this.tickCount % 5 == 0) {
            LivingEntity owner = this.getOwner() instanceof LivingEntity ? (LivingEntity) this.getOwner() : null;
            if (owner != null && localPlayerCheck != null && localPlayerCheck.test(owner)) {
                float radius = this.getRadius();
                List<LivingEntity> targets = this.level.getEntitiesOfClass(LivingEntity.class,
                        this.getBoundingBox().inflate(radius, radius, radius));

                for (LivingEntity target : targets) {
                    if (target.is(owner) || target instanceof FossilMothEntity) continue;

                    double dx = target.getX() - this.getX();
                    double dy = target.getY() - this.getY();
                    double dz = target.getZ() - this.getZ();
                    double distSqr = dx * dx + dy * dy + dz * dz;

                    if (distSqr <= radius * radius) {
                        int glowColor = hamonInfused ? HAMON_COLOR : AMBER_COLOR;
                        target.getCapability(com.github.standobyte.jojo.capability.entity.EntityUtilCapProvider.CAPABILITY).ifPresent(cap -> {
                            cap.setClGlowingColor(OptionalInt.of(glowColor), 40);
                        });
                    }
                }
            }
        }

        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();

        int duration = this.getDuration();
        if (duration > 0) {
            this.setDuration(duration - 1);
        }
        if (duration <= 0) {
            this.remove();
            return;
        }

        if (!this.level.isClientSide && this.tickCount % 5 == 0) {
            float radius = this.getRadius();
            List<LivingEntity> targets = this.level.getEntitiesOfClass(LivingEntity.class,
                    this.getBoundingBox().inflate(radius, radius, radius));

            LivingEntity owner = this.getOwner() instanceof LivingEntity ? (LivingEntity) this.getOwner() : null;

            for (LivingEntity target : targets) {
                if (target.is(owner) || target instanceof FossilMothEntity) continue;

                double dx = target.getX() - this.getX();
                double dy = target.getY() - this.getY();
                double dz = target.getZ() - this.getZ();
                double distSqr = dx * dx + dy * dy + dz * dz;

                if (distSqr <= radius * radius) {
                    applyDebuffs(target, radius);
                }
            }
        }
    }

    private void applyDebuffs(LivingEntity target, float radius) {
        int duration = 55 + Math.min(90, (int) (radius * 7.5F));
        int amplifier = radius >= 10.0F ? 2 : radius >= 5.0F ? 1 : 0;
        int weaknessAmp = radius >= 8.0F ? 1 : 0;

        target.addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN, duration, amplifier));
        target.addEffect(new EffectInstance(Effects.WEAKNESS, duration, weaknessAmp));
        target.addEffect(new EffectInstance(Effects.DIG_SLOWDOWN, duration, amplifier));

        if (hamonInfused) {
            target.addEffect(new EffectInstance(com.github.standobyte.jojo.init.ModStatusEffects.HAMON_SPREAD.get(), duration, amplifier));
        }

        int airDrain = 3 + amplifier * 2;
        int currentAir = target.getAirSupply();
        if (currentAir > 0) {
            target.setAirSupply(Math.max(0, currentAir - airDrain));
        }
        else if (target.hurtTime == 0) {
            target.hurt(net.minecraft.util.DamageSource.DROWN, 1.0F + amplifier * 0.5F);
        }
    }

    @Override
    public IPacket<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}