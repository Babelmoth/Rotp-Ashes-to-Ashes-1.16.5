package com.babelmoth.rotp_ata.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.network.NetworkHooks;

/**
 * Decorative spear spike entity used for visual effects.
 * Spawns below ground and emerges upward over several ticks.
 * Auto-despawns after a set lifetime.
 */
public class SpearSpikeEntity extends Entity implements IEntityAdditionalSpawnData {
    private static final DataParameter<Float> DATA_SPIKE_YAW = EntityDataManager.defineId(SpearSpikeEntity.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> DATA_SPIKE_PITCH = EntityDataManager.defineId(SpearSpikeEntity.class, DataSerializers.FLOAT);

    private int lifetime = 40; // 2 seconds default
    private int age = 0;
    private int emergeTicks = 5; // ticks to fully emerge from ground
    private double targetY; // final Y position (ground surface)
    private double startY;  // starting Y position (below ground)
    private static final double EMERGE_DEPTH = 1.5; // how far below ground to start
    private boolean projectileMode = false; // when true, flies with velocity and despawns on block hit

    public SpearSpikeEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noPhysics = true;
    }

    public SpearSpikeEntity(World world, double x, double y, double z, float yaw, float pitch, int lifetime) {
        this(com.babelmoth.rotp_ata.init.InitEntities.SPEAR_SPIKE_ENTITY.get(), world);
        this.targetY = y;
        this.startY = y - EMERGE_DEPTH;
        this.setPos(x, this.startY, z);
        this.setSpikeYaw(yaw);
        this.setSpikePitch(pitch);
        this.lifetime = lifetime;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_SPIKE_YAW, 0.0F);
        this.entityData.define(DATA_SPIKE_PITCH, 0.0F);
    }

    public void setSpikeYaw(float yaw) {
        this.entityData.set(DATA_SPIKE_YAW, yaw);
    }

    public float getSpikeYaw() {
        return this.entityData.get(DATA_SPIKE_YAW);
    }

    public void setSpikePitch(float pitch) {
        this.entityData.set(DATA_SPIKE_PITCH, pitch);
    }

    public float getSpikePitch() {
        return this.entityData.get(DATA_SPIKE_PITCH);
    }

    /**
     * Enable projectile mode: the spike will fly with its velocity, apply gravity,
     * and despawn when hitting a block.
     */
    public void setProjectileMode(boolean mode) {
        this.projectileMode = mode;
        if (mode) {
            this.noPhysics = false;
        }
    }

    public boolean isProjectileMode() {
        return projectileMode;
    }

    /**
     * Returns emergence progress from 0.0 (fully underground) to 1.0 (fully emerged).
     */
    public float getEmergenceProgress(float partialTicks) {
        if (emergeTicks <= 0) return 1.0F;
        float progress = (age + partialTicks) / (float) emergeTicks;
        return Math.min(1.0F, Math.max(0.0F, progress));
    }

    @Override
    public void tick() {
        super.tick();
        age++;

        if (projectileMode) {
            // Projectile flight: apply velocity, gravity, and block collision
            Vector3d pos = position();
            Vector3d motion = getDeltaMovement();
            Vector3d nextPos = pos.add(motion);

            // Ray trace for block collision
            if (!level.isClientSide) {
                BlockRayTraceResult blockHit = level.clip(new RayTraceContext(
                        pos, nextPos, RayTraceContext.BlockMode.COLLIDER,
                        RayTraceContext.FluidMode.NONE, this));
                if (blockHit.getType() == RayTraceResult.Type.BLOCK) {
                    remove();
                    return;
                }
            }

            // Move
            this.setPos(nextPos.x, nextPos.y, nextPos.z);
            // Apply gravity
            this.setDeltaMovement(motion.add(0, -0.04, 0));

            // Update visual rotation to match flight direction
            if (motion.lengthSqr() > 0.001) {
                float newYaw = (float) Math.toDegrees(Math.atan2(motion.z, motion.x));
                float horizSpeed = (float) Math.sqrt(motion.x * motion.x + motion.z * motion.z);
                float newPitch = (float) Math.toDegrees(Math.atan2(motion.y, horizSpeed));
                this.setSpikeYaw(-newYaw + 90.0F);
                this.setSpikePitch(-newPitch);
            }
        } else {
            // Emergence animation: move upward from startY to targetY
            if (age <= emergeTicks) {
                float progress = (float) age / (float) emergeTicks;
                progress = Math.min(1.0F, progress);
                double currentY = startY + (targetY - startY) * progress;
                this.setPos(this.getX(), currentY, this.getZ());
            }
        }

        if (!level.isClientSide && age >= lifetime) {
            remove();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundNBT nbt) {
        this.age = nbt.getInt("Age");
        this.lifetime = nbt.getInt("Lifetime");
        this.targetY = nbt.getDouble("TargetY");
        this.startY = nbt.getDouble("StartY");
        this.emergeTicks = nbt.getInt("EmergeTicks");
    }

    @Override
    protected void addAdditionalSaveData(CompoundNBT nbt) {
        nbt.putInt("Age", this.age);
        nbt.putInt("Lifetime", this.lifetime);
        nbt.putDouble("TargetY", this.targetY);
        nbt.putDouble("StartY", this.startY);
        nbt.putInt("EmergeTicks", this.emergeTicks);
    }

    @Override
    public IPacket<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(PacketBuffer buffer) {
        buffer.writeFloat(getSpikeYaw());
        buffer.writeFloat(getSpikePitch());
        buffer.writeInt(lifetime);
        buffer.writeDouble(targetY);
        buffer.writeDouble(startY);
        buffer.writeInt(emergeTicks);
    }

    @Override
    public void readSpawnData(PacketBuffer buffer) {
        setSpikeYaw(buffer.readFloat());
        setSpikePitch(buffer.readFloat());
        this.lifetime = buffer.readInt();
        this.targetY = buffer.readDouble();
        this.startY = buffer.readDouble();
        this.emergeTicks = buffer.readInt();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
