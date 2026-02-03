package com.babelmoth.rotp_ata.entity;

import com.babelmoth.rotp_ata.init.InitParticles;
import com.babelmoth.rotp_ata.init.InitEntities;

import net.minecraft.entity.Entity;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.network.IPacket;
import net.minecraftforge.fml.network.NetworkHooks;

import java.util.List;
import java.util.OptionalInt;

public class ExfoliatingAshCloudEntity extends AreaEffectCloudEntity {

    private static final int AMBER_COLOR = 0xe7801a;
    private static final int HAMON_COLOR = 0xffdd00;
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

    @Override
    public void tick() {
        // Custom particle spawn on CLIENT - spawn very few large particles at random positions
        if (this.level.isClientSide && this.tickCount % 10 == 0) {
            float radius = this.getRadius();
            int particleCount = 1 + this.random.nextInt(2);
            for (int i = 0; i < particleCount; i++) {
                double theta = this.random.nextDouble() * Math.PI * 2;
                double phi = Math.acos(2 * this.random.nextDouble() - 1);
                double r = radius * Math.cbrt(this.random.nextDouble());
                double px = this.getX() + r * Math.sin(phi) * Math.cos(theta);
                double py = this.getY() + r * Math.cos(phi);
                double pz = this.getZ() + r * Math.sin(phi) * Math.sin(theta);
                
                if (hamonInfused) {
                    // Hamon spark particles for Hamon-infused clouds
                    this.level.addParticle(com.github.standobyte.jojo.init.ModParticles.HAMON_SPARK.get(), px, py, pz, 0, 0, 0);
                } else {
                    this.level.addParticle(InitParticles.ASH_SMOKE_LARGE.get(), px, py, pz, 0, 0, 0);
                }
            }
        }
        
        // CLIENT-SIDE glow effect (ROTP pattern)
        if (this.level.isClientSide && this.tickCount % 5 == 0) {
            LivingEntity owner = this.getOwner() instanceof LivingEntity ? (LivingEntity)this.getOwner() : null;
            if (owner != null && owner == com.github.standobyte.jojo.client.ClientUtil.getClientPlayer()) {
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
        
        // Duration countdown
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

        // Server-side effect logic
        if (!this.level.isClientSide && this.tickCount % 5 == 0) {
            float radius = this.getRadius();
            List<LivingEntity> targets = this.level.getEntitiesOfClass(LivingEntity.class, 
                    this.getBoundingBox().inflate(radius, radius, radius));
            
            LivingEntity owner = this.getOwner() instanceof LivingEntity ? (LivingEntity)this.getOwner() : null;

            for (LivingEntity target : targets) {
                if (target.is(owner) || target instanceof FossilMothEntity) continue;
                
                double dx = target.getX() - this.getX();
                double dy = target.getY() - this.getY();
                double dz = target.getZ() - this.getZ();
                double distSqr = dx * dx + dy * dy + dz * dz;
                
                if (distSqr <= radius * radius) {
                    applyDebuffs(target);
                }
            }
        }
    }

    private void applyDebuffs(LivingEntity target) {
        int duration = 40; 
        
        target.addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN, duration, 1));
        target.addEffect(new EffectInstance(Effects.WEAKNESS, duration, 0));
        target.addEffect(new EffectInstance(Effects.DIG_SLOWDOWN, duration, 1));
        
        // Hamon-infused clouds apply HAMON_SPREAD effect (continuous Hamon burn)
        if (hamonInfused) {
            target.addEffect(new EffectInstance(com.github.standobyte.jojo.init.ModStatusEffects.HAMON_SPREAD.get(), duration, 0));
        }
        
        int currentAir = target.getAirSupply();
        if (currentAir > 0) {
            target.setAirSupply(Math.max(0, currentAir - 5));
        }
        else {
            // Only deal drowning damage if target is not already being hurt (prevents stacking)
            if (target.hurtTime == 0) {
                target.hurt(net.minecraft.util.DamageSource.DROWN, 1.0F);
            }
        }
    }
    
    @Override
    public IPacket<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}


