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

public class SpearSpikeEntity extends Entity implements IEntityAdditionalSpawnData {
    private static final DataParameter<Float> DATA_SPIKE_YAW = EntityDataManager.defineId(SpearSpikeEntity.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> DATA_SPIKE_PITCH = EntityDataManager.defineId(SpearSpikeEntity.class, DataSerializers.FLOAT);

    private int lifetime = 40;
    private int age = 0;
    private int emergeTicks = 5;
    private double targetY;
    private double startY;
    private static final double EMERGE_DEPTH = 1.5;
    private boolean projectileMode = false;

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

    public void setProjectileMode(boolean mode) {
        this.projectileMode = mode;
        if (mode) {
            this.noPhysics = false;
        }
    }

    public boolean isProjectileMode() {
        return projectileMode;
    }

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

            Vector3d pos = position();
            Vector3d motion = getDeltaMovement();
            Vector3d nextPos = pos.add(motion);

            if (!level.isClientSide) {
                BlockRayTraceResult blockHit = level.clip(new RayTraceContext(
                        pos, nextPos, RayTraceContext.BlockMode.COLLIDER,
                        RayTraceContext.FluidMode.NONE, this));
                if (blockHit.getType() == RayTraceResult.Type.BLOCK) {
                    remove();
                    return;
                }
            }

            this.setPos(nextPos.x, nextPos.y, nextPos.z);

            this.setDeltaMovement(motion.add(0, -0.04, 0));

            if (motion.lengthSqr() > 0.001) {
                float newYaw = (float) Math.toDegrees(Math.atan2(motion.z, motion.x));
                float horizSpeed = (float) Math.sqrt(motion.x * motion.x + motion.z * motion.z);
                float newPitch = (float) Math.toDegrees(Math.atan2(motion.y, horizSpeed));
                this.setSpikeYaw(-newYaw + 90.0F);
                this.setSpikePitch(-newPitch);
            }
        } else {

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
