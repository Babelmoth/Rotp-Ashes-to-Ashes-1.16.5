package com.babelmoth.rotp_ata.entity;

import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.github.standobyte.jojo.power.impl.stand.StandUtil;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;
import com.github.standobyte.jojo.util.mod.JojoModUtil;
import com.github.standobyte.jojo.util.mc.MCUtil;
import com.babelmoth.rotp_ata.init.InitStands;

import java.util.List;
import java.util.stream.Collectors;
import com.babelmoth.rotp_ata.init.InitEntities;
import com.babelmoth.rotp_ata.entity.ExfoliatingAshCloudEntity;
import net.minecraft.world.Explosion;

import net.minecraft.entity.AgeableEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.controller.FlyingMovementController;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.passive.IFlyingAnimal;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.pathfinding.FlyingPathNavigator;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

import javax.annotation.Nullable;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.Direction;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import com.github.standobyte.jojo.init.ModStatusEffects;

public class FossilMothEntity extends TameableEntity implements IFlyingAnimal, IAnimatable {

    private final AnimationFactory factory = new AnimationFactory(this);

    private double stayX, stayY, stayZ;
    private boolean isStaying = false;
    private int desyncDelay;

    public interface ClientEffectsHandler {
        void playHamonChargeEffects(FossilMothEntity moth);
    }

    private static java.util.function.Predicate<LivingEntity> localPlayerCheck;
    private static ClientEffectsHandler clientEffectsHandler;


    private static final DataParameter<Boolean> IS_RECALLING = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Boolean> IS_SHIELD_PERSISTENT = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.BOOLEAN);

    public static final DataParameter<Byte> ATTACHED_FACE = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.BYTE);
    public static final DataParameter<Float> ATTACHED_OFFSET_X = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.FLOAT);
    public static final DataParameter<Float> ATTACHED_OFFSET_Y = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.FLOAT);
    public static final DataParameter<Float> ATTACHED_OFFSET_Z = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.FLOAT);
    public static final DataParameter<Float> ATTACHED_ROTATION = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.FLOAT);
    public static final DataParameter<Integer> ATTACHED_ENTITY_ID = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.INT);
    public static final DataParameter<Integer> KINETIC_ENERGY = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.INT);
    public static final DataParameter<Integer> HAMON_ENERGY = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.INT);
    public static final DataParameter<Integer> SHIELD_TARGET_ID = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.INT);
    public static final DataParameter<Integer> SWARM_TARGET_ID = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.INT);
    public static final DataParameter<Integer> CARRIED_ITEM_ID = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.INT);

    public static final DataParameter<Boolean> IS_PIERCING_CHARGING = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.BOOLEAN);
    public static final DataParameter<Boolean> IS_PIERCING_FIRING = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.BOOLEAN);
    public static final DataParameter<Float> PIERCING_DAMAGE = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.FLOAT);
    public static final DataParameter<Float> PIERCING_SPEED = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.FLOAT);
    public static final DataParameter<Boolean> IS_BULLET = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.BOOLEAN);
    public static final DataParameter<Boolean> KINETIC_SENSING_ENABLED = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.BOOLEAN);
    public static final DataParameter<Boolean> IS_SHIELD_MOTH = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.BOOLEAN);
    private long lastShieldTick = 0;

    private long mothBiteCooldownUntilTick = 0L;

    public static void setLocalPlayerCheck(java.util.function.Predicate<LivingEntity> check) {
        localPlayerCheck = check;
    }

    public static void setClientEffectsHandler(ClientEffectsHandler handler) {
        clientEffectsHandler = handler;
    }

    public void refreshShield() {
         this.lastShieldTick = level.getGameTime();
    }

    public boolean isMothBiteOnCooldown() {
        return level != null && !level.isClientSide && level.getGameTime() < mothBiteCooldownUntilTick;
    }

    public void setMothBiteCooldown() {
        if (level != null && !level.isClientSide) {
            mothBiteCooldownUntilTick = level.getGameTime() + 40;
        }
    }

    public boolean isRecalling() {
        return this.entityData.get(IS_RECALLING);
    }

    private int mothPoolIndex = -1;
    private boolean kineticEnergyChanged = false;
    private boolean hamonEnergyChanged = false;

    public void setMothPoolIndex(int index) {
        this.mothPoolIndex = index;
    }

    public int getMothPoolIndex() {
        return this.mothPoolIndex;
    }

    public void updatePool() {
        if (level.isClientSide || mothPoolIndex == -1) return;
        LivingEntity owner = getOwner();
        if (owner == null) return;

        owner.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
            pool.setMothKinetic(mothPoolIndex, getKineticEnergy());
            pool.setMothHamon(mothPoolIndex, getHamonEnergy());
        });
    }

    public void syncFromPool() {
        if (level.isClientSide || mothPoolIndex == -1) return;
        LivingEntity owner = getOwner();
        if (owner == null) return;

        owner.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
            if (!pool.isSlotActive(mothPoolIndex)) {
                this.remove();
                return;
            }

            this.entityData.set(KINETIC_ENERGY, pool.getMothKinetic(mothPoolIndex));
            this.entityData.set(HAMON_ENERGY, pool.getMothHamon(mothPoolIndex));
        });
    }

    public void syncToPool() {
        if (level.isClientSide || mothPoolIndex == -1) return;
        LivingEntity owner = getOwner();
        if (owner == null) return;

        owner.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
            if (pool.isSlotActive(mothPoolIndex)) {
                pool.setMothKinetic(mothPoolIndex, getKineticEnergy());
                pool.setMothHamon(mothPoolIndex, getHamonEnergy());
            }
        });
    }

    private static final int MAX_ENERGY_BASE = AshesToAshesConstants.MAX_ENERGY_BASE;
    private static final int MAX_ENERGY_RESOLVE = AshesToAshesConstants.MAX_ENERGY_RESOLVE;

    private BlockPos attachedPos;

    public BlockPos getAttachedPos() {
        return this.attachedPos;
    }

    public static void spawnMothsForUser(World level, LivingEntity user, int targetCount) {
        if (level.isClientSide || user == null) return;

        int existingCount = MothQueryUtil.getOwnerMoths(user, 128.0).size();
        int toSpawn = targetCount - existingCount;
        if (toSpawn <= 0) return;

        user.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
            for (int i = 0; i < toSpawn; i++) {
                int slot = pool.allocateSlotWithPriority(true);
                if (slot == -1) {
                    break;
                }

                FossilMothEntity moth = new FossilMothEntity(level, user);
                moth.setMothPoolIndex(slot);

                double offsetX = (level.random.nextDouble() - 0.5) * 1.5;
                double offsetY = 1 + level.random.nextDouble() * 1.0;
                double offsetZ = (level.random.nextDouble() - 0.5) * 1.5;

                moth.setPos(
                    user.getX() + offsetX,
                    user.getY() + offsetY,
                    user.getZ() + offsetZ
                );

                level.addFreshEntity(moth);
            }
        });
    }

    public FossilMothEntity(EntityType<? extends FossilMothEntity> type, World world) {
        super(type, world);
        this.moveControl = new FlyingMovementController(this, 70, true);
        this.setNoGravity(true);
        this.setPathfindingMalus(PathNodeType.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(PathNodeType.WATER, -1.0F);

        this.desyncDelay = this.random.nextInt(20);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ATTACHED_FACE, (byte) -1);
        this.entityData.define(ATTACHED_OFFSET_X, 0.0f);
        this.entityData.define(ATTACHED_OFFSET_Y, 0.0f);
        this.entityData.define(ATTACHED_OFFSET_Z, 0.0f);
        this.entityData.define(ATTACHED_ROTATION, 0.0f);
        this.entityData.define(ATTACHED_ENTITY_ID, -1);
        this.entityData.define(KINETIC_ENERGY, 0);
        this.entityData.define(HAMON_ENERGY, 0);
        this.entityData.define(SHIELD_TARGET_ID, -1);
        this.entityData.define(IS_RECALLING, false);
        this.entityData.define(IS_SHIELD_PERSISTENT, false);
        this.entityData.define(SWARM_TARGET_ID, -1);
        this.entityData.define(CARRIED_ITEM_ID, -1);
        this.entityData.define(IS_PIERCING_CHARGING, false);
        this.entityData.define(IS_PIERCING_FIRING, false);
        this.entityData.define(PIERCING_DAMAGE, 0.0f);
        this.entityData.define(PIERCING_SPEED, 0.0f);
        this.entityData.define(IS_BULLET, false);
        this.entityData.define(KINETIC_SENSING_ENABLED, false);
        this.entityData.define(IS_SHIELD_MOTH, false);
    }

    public void setPiercingCharging(boolean charging) {
        this.entityData.set(IS_PIERCING_CHARGING, charging);
    }

    public boolean isPiercingCharging() {
        return this.entityData.get(IS_PIERCING_CHARGING);
    }

    public void setPiercingFiring(boolean firing) {
        this.entityData.set(IS_PIERCING_FIRING, firing);
    }

    public boolean isPiercingFiring() {
        return this.entityData.get(IS_PIERCING_FIRING);
    }

    public void setIsBullet(boolean isBullet) {
        this.entityData.set(IS_BULLET, isBullet);
    }

    public boolean isKineticSensingEnabled() {
        return this.entityData.get(KINETIC_SENSING_ENABLED);
    }

    public void setKineticSensingEnabled(boolean enabled) {
        this.entityData.set(KINETIC_SENSING_ENABLED, enabled);
    }

    public boolean isBullet() {
        return this.entityData.get(IS_BULLET);
    }

    public EntityDataManager getEntityData() {
        return this.entityData;
    }

    public void piercingCharge(LivingEntity shooter) {
        this.entityData.set(IS_PIERCING_CHARGING, true);
        this.entityData.set(IS_PIERCING_FIRING, false);
        this.setNoAi(true);
        this.setNoGravity(true);
        this.detach();

        net.minecraft.util.math.vector.Vector3d view = shooter.getViewVector(1.0f);
        double x = shooter.getX() + view.x * 1.5;
        double y = shooter.getEyeY() + view.y * 1.5;
        double z = shooter.getZ() + view.z * 1.5;
        this.setPos(x, y, z);
        this.setDeltaMovement(0, 0, 0);
        this.yRot = shooter.yRot;
        this.xRot = shooter.xRot;
    }

    public void piercingFire(net.minecraft.util.math.vector.Vector3d direction, float speed) {
        this.entityData.set(IS_PIERCING_CHARGING, false);
        this.entityData.set(IS_PIERCING_FIRING, true);
        this.entityData.set(PIERCING_SPEED, speed);
        this.piercingLifeTime = 0;

        float baseDmg = 5.0f;
        float energyDmg = (float)getKineticEnergy() * 1.5f;
        this.entityData.set(PIERCING_DAMAGE, baseDmg + energyDmg);

        this.setDeltaMovement(direction.normalize().scale(speed));
        this.setNoAi(true);
    }

    private int piercingLifeTime = 0;

    private boolean isJetProjectile = false;

    private boolean dissipateOnRemove = false;

    public boolean isJetProjectile() {
        return isJetProjectile;
    }

    public void setJetProjectile(boolean jetProjectile) {
        this.isJetProjectile = jetProjectile;
    }

    public void setDissipateOnRemove(boolean dissipateOnRemove) {
        this.dissipateOnRemove = dissipateOnRemove;
    }

    public void jetFire(net.minecraft.util.math.vector.Vector3d direction, float speed) {
        this.entityData.set(IS_PIERCING_CHARGING, false);
        this.entityData.set(IS_PIERCING_FIRING, true);
        this.entityData.set(PIERCING_SPEED, speed);
        this.piercingLifeTime = 0;
        this.isJetProjectile = true;
        this.setDeltaMovement(direction.normalize().scale(speed));
        this.setNoAi(true);
        this.setNoGravity(true);
    }

    public void swarmTo(Entity target) {
        this.entityData.set(SWARM_TARGET_ID, target.getId());
    }

    public boolean isSwarming() {
        return this.entityData.get(SWARM_TARGET_ID) != -1;
    }

    public void setShieldTarget(Entity target) {
        setShieldTarget(target, false);
    }

    public void setShieldTarget(Entity target, boolean persistent) {
        this.entityData.set(SHIELD_TARGET_ID, target == null ? -1 : target.getId());
        this.entityData.set(IS_SHIELD_PERSISTENT, persistent);
        if (target != null) {
            refreshShield();
        }
    }

    public Entity getShieldTarget() {
        int id = this.entityData.get(SHIELD_TARGET_ID);
        return id == -1 ? null : level.getEntity(id);
    }

    public boolean isShieldPersistent() {
        return this.entityData.get(IS_SHIELD_PERSISTENT);
    }

    public boolean isShieldMoth() {
        return this.entityData.get(IS_SHIELD_MOTH);
    }

    public void setIsShieldMoth(boolean value) {
        this.entityData.set(IS_SHIELD_MOTH, value);
    }

    public int getHamonEnergy() {
        return this.entityData.get(HAMON_ENERGY);
    }

    public void setHamonEnergy(int energy) {
        int oldEnergy = this.entityData.get(HAMON_ENERGY);
        int newEnergy = Math.max(0, Math.min(energy, getMaxEnergy()));
        this.entityData.set(HAMON_ENERGY, newEnergy);
        if (oldEnergy != newEnergy) {
            hamonEnergyChanged = true;
        }
    }

    public int getKineticEnergy() {
        return this.entityData.get(KINETIC_ENERGY);
    }

    public void setKineticEnergy(int energy) {
        int oldEnergy = this.entityData.get(KINETIC_ENERGY);
        int newEnergy = Math.max(0, Math.min(energy, getMaxEnergy()));
        this.entityData.set(KINETIC_ENERGY, newEnergy);
        if (oldEnergy != newEnergy) {
            kineticEnergyChanged = true;
        }
    }

    public int getMaxEnergy() {
        return MAX_ENERGY_BASE;
    }

    public int getTotalEnergy() {
        return getKineticEnergy() + getHamonEnergy();
    }

    @Override
    public void onSyncedDataUpdated(DataParameter<?> key) {
        super.onSyncedDataUpdated(key);
        if (KINETIC_ENERGY.equals(key) || HAMON_ENERGY.equals(key)) {
            refreshDimensions();
            updateAttributesBasedOnEnergy();
        }
    }

    private void updateAttributesBasedOnEnergy() {
        int energy = getTotalEnergy();
        int maxEnergy = getMaxEnergy();
        float ratio = (float) energy / maxEnergy;

        int hamonEnergy = getHamonEnergy();
        float hamonRatio = (float) hamonEnergy / maxEnergy;
        LivingEntity owner = getOwner();
        double ownerMaxHealth = owner != null ? owner.getMaxHealth() : 20.0D;
        double newHealth = Math.max(1.0D, ownerMaxHealth / 50.0D);

        double newArmor = 2.0 + 18.0 * ratio;
        double newToughness = 5.0 * ratio;
        double newSpeed = 0.4 - 0.2 * ratio;
        double newFlySpeed = 0.8 - 0.4 * ratio;

        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(newHealth);
        this.getAttribute(Attributes.ARMOR).setBaseValue(newArmor);
        this.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(newToughness);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(newSpeed);
        this.getAttribute(Attributes.FLYING_SPEED).setBaseValue(newFlySpeed);

        if (this.getHealth() > newHealth) {
            this.setHealth((float)newHealth);
        }
    }

    public FossilMothEntity(World world, LivingEntity owner) {
        this(com.babelmoth.rotp_ata.init.InitEntities.FOSSIL_MOTH.get(), world);
        this.setOwnerUUID(owner.getUUID());
    }

    public static AttributeModifierMap.MutableAttribute createAttributes() {
        return TameableEntity.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 5.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.4D)
                .add(Attributes.FLYING_SPEED, 0.8D)
                .add(Attributes.ARMOR, 2.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new com.babelmoth.rotp_ata.entity.ai.NoTeleportFollowOwnerGoal(this, 1.2D, 3.0F, 1.5F, true));
        this.goalSelector.addGoal(2, new LookAtGoal(this, MobEntity.class, 8.0F));
        this.goalSelector.addGoal(3, new LookRandomlyGoal(this));
    }

    @Override
    protected PathNavigator createNavigation(World world) {
        FlyingPathNavigator flyingNavigator = new FlyingPathNavigator(this, world);
        flyingNavigator.setCanOpenDoors(false);
        flyingNavigator.setCanFloat(true);
        flyingNavigator.setCanPassDoors(true);
        return flyingNavigator;
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, IWorldReader world) {
        return world.getBlockState(pos).isAir() ? 10.0F : 5.0F;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        LivingEntity owner = getOwner();
        if (owner != null && entity == owner) {
            return false;
        }
        if (entity instanceof FossilMothEntity) {
            return false;
        }
        return super.canCollideWith(entity);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, net.minecraft.block.BlockState state, BlockPos pos) {

    }

    public float getAlpha(float partialTick) {
        float ticks = this.tickCount + partialTick;
        if (ticks < 15) {
            return ticks / 15.0f;
        }
        return 1.0f;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        }

        boolean isHamon = source.getMsgId().contains("ripple") || source.getMsgId().contains("hamon") || source.getMsgId().contains("overdrive");

        if (isHamon) {
            setHamonEnergy(getHamonEnergy() + (int)amount);
            return false;
        }

        boolean isFall = source == DamageSource.FALL;
        boolean isExplosion = source.isExplosion();
        boolean isOwnerSafeKineticDetonation = isExplosion && "ashes_to_ashes_kinetic_detonation".equals(source.getMsgId()) && source.getEntity() == getOwner();
        if (!isOwnerSafeKineticDetonation && (isFall || isExplosion || (!source.isBypassArmor() && !source.isMagic() && source != DamageSource.OUT_OF_WORLD))) {
            int currentEnergy = getKineticEnergy();
            int max = getMaxEnergy();

            if (currentEnergy < max) {

                int absorbed = Math.min((int)amount, max - currentEnergy);
                setKineticEnergy(currentEnergy + absorbed);
                return false;
            }
        }

        LivingEntity owner = getOwner();
        if (owner != null) {

            float ratio = 1.0f / 50.0f;
            float damageToOwner = amount * ratio;
            if (damageToOwner > 0) {
                owner.hurt(source, damageToOwner);
            }
        }

        return super.hurt(source, amount);
    }

    @Override
    public int getMaxFallDistance() {
        return 0;
    }

    @Override
    public net.minecraft.entity.EntitySize getDimensions(net.minecraft.entity.Pose pose) {
        return net.minecraft.entity.EntitySize.fixed(0.25F, 0.25F);
    }

    public void recall() {

        this.entityData.set(IS_RECALLING, true);
        this.detach();
    }

    private void spawnDespawnParticles() {

        if (level.isClientSide) {
            for (int i = 0; i < 10; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double dist = 0.08 + random.nextDouble() * 0.1;
                double vx = Math.cos(angle) * dist;
                double vz = Math.sin(angle) * dist;
                double vy = -0.02 - random.nextDouble() * 0.03;
                level.addParticle(com.babelmoth.rotp_ata.init.InitParticles.FOSSIL_ASH.get(),
                    getX() + (random.nextDouble() - 0.5) * 0.2,
                    getY() + 0.15 + random.nextDouble() * 0.2,
                    getZ() + (random.nextDouble() - 0.5) * 0.2,
                    vx, vy, vz);
            }
        }
    }

    @Override
    public void tick() {

        if (!level.isClientSide) {
             LivingEntity owner = getOwner();
             if (owner != null) {
                 owner.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
                     if (mothPoolIndex == -1) {

                         int idx = pool.allocateSlot();
                         if (idx != -1) {
                             this.mothPoolIndex = idx;
                         } else {

                             this.remove();
                             return;
                         }
                     }

                    pool.assertDeployed(mothPoolIndex);

                    boolean shouldSync = kineticEnergyChanged || hamonEnergyChanged || (this.tickCount % AshesToAshesConstants.SYNC_INTERVAL_TICKS == 0);
                    if (shouldSync) {
                        syncToPool();
                        syncFromPool();
                        kineticEnergyChanged = false;
                        hamonEnergyChanged = false;
                    }
                 });
             }
        }

        if (isPiercingFiring()) {
            this.piercingLifeTime++;
            int maxLife = isJetProjectile ? 80 : 100;
            if (this.piercingLifeTime > maxLife || this.getDeltaMovement().lengthSqr() < 0.01) {
                if (isJetProjectile) setDissipateOnRemove(true);
                spawnDespawnParticles();
                this.remove();
                return;
            }

            net.minecraft.util.math.vector.Vector3d start = this.position();
            net.minecraft.util.math.vector.Vector3d motion = this.getDeltaMovement();
            net.minecraft.util.math.vector.Vector3d end = start.add(motion);

            net.minecraft.util.math.RayTraceResult hitResult = this.level.clip(new net.minecraft.util.math.RayTraceContext(
                start, end,
                net.minecraft.util.math.RayTraceContext.BlockMode.COLLIDER,
                net.minecraft.util.math.RayTraceContext.FluidMode.NONE,
                this));

            if (hitResult.getType() != net.minecraft.util.math.RayTraceResult.Type.MISS) {
                end = hitResult.getLocation();
            }

            net.minecraft.util.math.AxisAlignedBB collisionBox = this.getBoundingBox().expandTowards(motion).inflate(1.0);
            List<Entity> hits = this.level.getEntities(this, collisionBox, e -> {
                if (!(e instanceof LivingEntity) || e == getOwner() || e.isSpectator()) return false;
                if (isJetProjectile) {

                    if (e instanceof FossilMothEntity) return false;
                    if (((LivingEntity)e).isInvulnerableTo(DamageSource.GENERIC)) return false;
                }
                return true;
            });

            boolean useHamon = getHamonEnergy() > 0;
            float chargeRatio = (float)getTotalEnergy() / (float)getMaxEnergy();
            float baseDmg = 4.0f;
            float maxDmg = 18.0f;
            float damage = baseDmg + (maxDmg - baseDmg) * chargeRatio;

            float knockbackStrength = 0.5f + 2.0f * chargeRatio;
            float penetrationThreshold = 0.6f + 2.4f * chargeRatio;
            float hardnessResistance = 0.1f - 0.05f * chargeRatio;
            float wallExplosionSize = 0.5f + 1.0f * chargeRatio;

            if (isBullet()) {
                damage = 6.0f;
                knockbackStrength = 1.5f;
                wallExplosionSize = 0.5f;
                penetrationThreshold = 0.0f;
            }

            for (Entity e : hits) {
                if (e.getBoundingBox().intersects(this.getBoundingBox().expandTowards(motion).inflate(0.5))) {
                     LivingEntity target = (LivingEntity)e;

                     if (isJetProjectile) {
                         this.entityData.set(IS_PIERCING_FIRING, false);
                         this.isJetProjectile = false;
                         this.setPos(target.getX(), target.getY(), target.getZ());
                         this.attachToEntity(target);
                         this.setNoAi(true);
                         return;
                     }

                     if (target instanceof net.minecraft.entity.player.PlayerEntity) {
                         net.minecraft.entity.player.PlayerEntity player = (net.minecraft.entity.player.PlayerEntity)target;
                         if (player.isBlocking()) {
                             com.github.standobyte.jojo.util.mc.damage.DamageUtil.disableShield(player, 1.0F);
                         }
                     }

                     if (target instanceof com.github.standobyte.jojo.entity.stand.StandEntity) {
                         com.github.standobyte.jojo.entity.stand.StandEntity standTarget = (com.github.standobyte.jojo.entity.stand.StandEntity)target;
                         standTarget.breakStandBlocking(com.github.standobyte.jojo.entity.stand.StandStatFormulas.getBlockingBreakTicks(standTarget.getDurability()));
                     }

                     boolean dmgSuccess = target.hurt(DamageSource.mobAttack(getOwner() instanceof LivingEntity ? (LivingEntity)getOwner() : this), damage);

                     if (dmgSuccess && useHamon) {
                         com.github.standobyte.jojo.util.mc.damage.DamageUtil.dealHamonDamage(target, 0.5F, this, getOwner());
                     }

                     if (dmgSuccess) {

                         net.minecraft.util.math.vector.Vector3d knockbackDir = motion.normalize();

                         com.github.standobyte.jojo.util.mc.damage.DamageUtil.knockback(target, knockbackStrength,
                             (float) -Math.atan2(knockbackDir.x, knockbackDir.z) * (180F / (float)Math.PI));

                         final float finalKnockback = knockbackStrength;
                         final float finalExplosion = wallExplosionSize;
                         final float finalDamage = damage;
                         final LivingEntity finalAttacker = getOwner() instanceof LivingEntity ? (LivingEntity)getOwner() : this;

                         com.github.standobyte.jojo.util.mc.damage.KnockbackCollisionImpact.getHandler(target).ifPresent(cap -> cap
                             .onPunchSetKnockbackImpact(knockbackDir.scale(finalKnockback), finalAttacker)
                             .withImpactExplosion(finalExplosion, null, finalDamage * 0.3f)
                         );
                     }
                }
            }

            if (hitResult.getType() == net.minecraft.util.math.RayTraceResult.Type.BLOCK) {
                 if (isJetProjectile) {
                     setDissipateOnRemove(true);
                     spawnDespawnParticles();
                     this.remove();
                     return;
                 }
                 net.minecraft.util.math.BlockRayTraceResult blockHit = (net.minecraft.util.math.BlockRayTraceResult)hitResult;
                 BlockPos targetPos = blockHit.getBlockPos();
                 net.minecraft.block.BlockState state = level.getBlockState(targetPos);

                 if (!state.isAir(level, targetPos) && state.getMaterial() != net.minecraft.block.material.Material.BARRIER) {
                     float hardness = state.getDestroySpeed(level, targetPos);

                     if (!isBullet() && hardness >= 0 && hardness < penetrationThreshold) {

                         level.destroyBlock(targetPos, true);

                         float speedReduction = hardness * hardnessResistance;
                         float currentSpeed = (float)motion.length();
                         float newSpeed = Math.max(currentSpeed - speedReduction, 0.2f);

                         if (newSpeed < 0.3f) {
                             spawnDespawnParticles();
                             this.remove();
                             return;
                         }

                         this.setDeltaMovement(motion.normalize().scale(newSpeed));

                     } else {

                         LivingEntity owner = getOwner() instanceof LivingEntity ? (LivingEntity)getOwner() : null;
                         if (owner != null && !level.isClientSide) {
                             float radius = isBullet() ? 0.5f : (1.0f + 2.0f * (float)getKineticEnergy() / (float)getMaxEnergy());
                             net.minecraft.world.Explosion.Mode explosionMode = isBullet() ? net.minecraft.world.Explosion.Mode.NONE : net.minecraft.world.Explosion.Mode.BREAK;

                             com.github.standobyte.jojo.action.stand.StandEntityHeavyAttack.HeavyPunchBlockInstance.HeavyPunchExplosion explosion =
                                 new com.github.standobyte.jojo.action.stand.StandEntityHeavyAttack.HeavyPunchBlockInstance.HeavyPunchExplosion(
                                     level, this,
                                     new com.github.standobyte.jojo.action.ActionTarget(targetPos, blockHit.getDirection()),
                                     motion.normalize(),
                                     DamageSource.mobAttack(this).setExplosion(),
                                     null,
                                     targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5,
                                     radius,
                                     false,
                                     explosionMode
                                 );

                             explosion
                                 .aoeDamage(damage * 0.5f)
                                 .createBlockShards(damage, 10);

                             com.github.standobyte.jojo.util.mc.damage.explosion.CustomExplosion.explode(explosion);
                         }

                         spawnDespawnParticles();
                         this.remove();
                         return;
                     }
                 }
            }

            this.setPos(end.x, end.y, end.z);

            return;
        }

        if (isPiercingCharging()) {
            this.setDeltaMovement(0, 0, 0);
            if (level.isClientSide) {
                for (int i = 0; i < 5; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double dist = 1.0 + random.nextDouble() * 1.0;
                    double px = getX() + Math.cos(angle) * dist;
                    double pz = getZ() + Math.sin(angle) * dist;
                    double py = getY() + (random.nextDouble() - 0.5) * 2.0;
                    double vx = (getX() - px) * 0.1;
                    double vy = (getY() - py) * 0.1;
                    double vz = (getZ() - pz) * 0.1;
                    level.addParticle(com.babelmoth.rotp_ata.init.InitParticles.FOSSIL_ASH_GATHER.get(), px, py, pz, vx, vy, vz);
                }
            }

        }

        super.tick();

        if (!level.isClientSide) {
            LivingEntity owner = getOwner();
            if (owner != null) {
                boolean ownerSensingEnabled = com.babelmoth.rotp_ata.action.AshesToAshesKineticSensing.isSensingEnabled(owner);
                if (ownerSensingEnabled != isKineticSensingEnabled()) {
                    setKineticSensingEnabled(ownerSensingEnabled);
                }
            }
        }

        tickKineticSensing();

        if (level.isClientSide && this.tickCount < 15) {

            for (int i = 0; i < 3; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double dist = 0.5 + random.nextDouble() * 0.5;
                double px = getX() + Math.cos(angle) * dist;
                double pz = getZ() + Math.sin(angle) * dist;
                double py = getY() + random.nextDouble() * 0.3;

                double vx = (getX() - px) * 0.12;
                double vz = (getZ() - pz) * 0.12;
                double vy = (getY() + 0.15 - py) * 0.12;
                level.addParticle(com.babelmoth.rotp_ata.init.InitParticles.FOSSIL_ASH_GATHER.get(), px, py, pz, vx, vy, vz);
            }
        }

        if (level.isClientSide && getHamonEnergy() > 0 && clientEffectsHandler != null) {
            clientEffectsHandler.playHamonChargeEffects(this);
        }

        if (this.entityData.get(IS_RECALLING)) {
            if (!this.level.isClientSide) {
                LivingEntity owner = getOwner();
                if (owner != null) {
                    this.navigation.moveTo(owner, 1.2);
                    if (this.distanceToSqr(owner) < 2.0) {

                        this.remove();
                    }
                } else {
                    this.remove();
                }
            }
            return;
        }

        int carriedItemId = this.entityData.get(CARRIED_ITEM_ID);
        if (carriedItemId != -1) {
            Entity carriedItem = level.getEntity(carriedItemId);
            LivingEntity owner = getOwner();

            if (carriedItem instanceof ItemEntity && owner != null) {
                ItemEntity itemEntity = (ItemEntity) carriedItem;

                if (!itemEntity.removed && !itemEntity.getItem().isEmpty()) {

                    net.minecraft.util.math.vector.Vector3d mothPos = this.position();
                    net.minecraft.util.math.vector.Vector3d itemPos = itemEntity.position();
                    net.minecraft.util.math.vector.Vector3d offset = new net.minecraft.util.math.vector.Vector3d(0, -0.3, 0);
                    net.minecraft.util.math.vector.Vector3d targetPos = mothPos.add(offset);

                    double dx = targetPos.x - itemPos.x;
                    double dy = targetPos.y - itemPos.y;
                    double dz = targetPos.z - itemPos.z;
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                    if (distance > 0.1) {
                        double speed = 0.3;
                        itemEntity.setDeltaMovement(
                            dx * speed / distance,
                            dy * speed / distance + 0.1,
                            dz * speed / distance
                        );
                        itemEntity.setNoPickUpDelay();
                        itemEntity.setOwner(this.getUUID());
                    } else {

                        itemEntity.setDeltaMovement(0, 0, 0);
                        itemEntity.setPos(targetPos.x, targetPos.y, targetPos.z);
                    }

                    this.navigation.moveTo(owner, 1.2);

                    if (this.distanceToSqr(owner) < 3.0) {
                        giveItemToOwner(itemEntity);
                        this.entityData.set(CARRIED_ITEM_ID, -1);
                    }
                } else {

                    this.entityData.set(CARRIED_ITEM_ID, -1);
                }
            } else {

                this.entityData.set(CARRIED_ITEM_ID, -1);
            }
            return;
        }

        int swarmTargetId = this.entityData.get(SWARM_TARGET_ID);
        if (swarmTargetId != -1) {
             Entity target = level.getEntity(swarmTargetId);

             boolean targetValid = target != null && !target.removed;
             if (target instanceof ItemEntity) {
                 ItemEntity itemEntity = (ItemEntity) target;
                 targetValid = targetValid && !itemEntity.getItem().isEmpty()
                     && !itemEntity.getPersistentData().getBoolean(ITEM_RETRIEVED_TAG);
             } else if (target instanceof LivingEntity) {
                 targetValid = targetValid && target.isAlive();
             }

             if (targetValid) {
                 this.navigation.moveTo(target, 1.5);
                 if (this.distanceToSqr(target) < 2.0) {
                     this.entityData.set(SWARM_TARGET_ID, -1);
                     if (target instanceof ItemEntity) {

                         startCarryingItem((ItemEntity) target);
                     } else {
                         attachToEntity(target);
                     }
                 }
             } else {

                 this.entityData.set(SWARM_TARGET_ID, -1);
             }
             return;
        }

        Entity shieldTarget = getShieldTarget();

        if (shieldTarget != null && !isShieldPersistent()) {
            if (level.getGameTime() - lastShieldTick > 5) {
                setShieldTarget(null);
                shieldTarget = null;
            }
        }

        if (shieldTarget != null && shieldTarget.isAlive()) {

             float angleOffset = (this.getId() % 10) * 36.0f;
             double rad = Math.toRadians(angleOffset + (level.getGameTime() * 2) % 360);

             double radius = 0.8;
             double heightOffset = 0.8 + ((this.getId() % 3) * 0.3);

             double tx = shieldTarget.getX() + Math.cos(rad) * radius;
             double tz = shieldTarget.getZ() + Math.sin(rad) * radius;
             double ty = shieldTarget.getY() + heightOffset;

             this.setPos(tx, ty, tz);
             this.setDeltaMovement(0, 0, 0);

             double dx = shieldTarget.getX() - tx;
             double dz = shieldTarget.getZ() - tz;
             this.yRot = (float)(Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
             this.yBodyRot = this.yRot;
             this.yHeadRot = this.yRot;

             return;
        }

            if (isAttached()) {
            this.setDeltaMovement(0, 0, 0);

            if (attachedPos != null) {

                Direction face = getAttachedFace();
                if (face != null) {
                    float lockedYaw = 0;
                    float lockedPitch = 0;
                     switch (face) {
                        case UP: lockedPitch = -90; break;
                        case DOWN: lockedPitch = 90; break;
                        case NORTH: lockedYaw = 180; break;
                        case SOUTH: lockedYaw = 0; break;
                        case WEST: lockedYaw = 90; break;
                        case EAST: lockedYaw = -90; break;
                    }
                    this.yRot = lockedYaw;
                    this.yBodyRot = lockedYaw;
                    this.yHeadRot = lockedYaw;
                    this.yRotO = lockedYaw;
                    this.xRot = lockedPitch;
                    this.xRotO = lockedPitch;
                }
            }

            if (!level.isClientSide) {

                if (attachedPos != null && level.getBlockState(attachedPos).isAir()) {
                    detach();
                }

                if (attachedPos != null) {
                    net.minecraft.util.math.AxisAlignedBB aabb = new net.minecraft.util.math.AxisAlignedBB(attachedPos).inflate(0.5);
                    java.util.List<com.github.standobyte.jojo.entity.HamonBlockChargeEntity> charges =
                        level.getEntitiesOfClass(com.github.standobyte.jojo.entity.HamonBlockChargeEntity.class, aabb);

                    for (com.github.standobyte.jojo.entity.HamonBlockChargeEntity charge : charges) {

                        int totalEnergy = getTotalEnergy();
                        int maxEnergy = getMaxEnergy();
                        int space = maxEnergy - totalEnergy;

                        if (space > 0) {
                            int absorbAmount = Math.min(space, 10);
                            setHamonEnergy(getHamonEnergy() + absorbAmount);
                            charge.remove();
                        }
                    }

                }
            }

            return;
        }

        if (isAttachedToEntity()) {
            this.setDeltaMovement(0, 0, 0);
            Entity target = level.getEntity(this.entityData.get(ATTACHED_ENTITY_ID));

            if (target != null && target.isAlive()) {

                float targetYaw = target instanceof LivingEntity ? ((LivingEntity)target).yBodyRot : target.yRot;
                float offX = this.entityData.get(ATTACHED_OFFSET_X);
                float offY = this.entityData.get(ATTACHED_OFFSET_Y);
                float offZ = this.entityData.get(ATTACHED_OFFSET_Z);

                double rad = Math.toRadians(-targetYaw);
                double dx = offX * Math.cos(rad) - offZ * Math.sin(rad);
                double dz = offX * Math.sin(rad) + offZ * Math.cos(rad);

                this.setPos(target.getX() + dx, target.getY() + offY, target.getZ() + dz);

                this.yRot = targetYaw + this.entityData.get(ATTACHED_ROTATION);
                this.yBodyRot = this.yRot;
                this.yHeadRot = this.yRot;
                this.xRot = 0;

                this.clearFire();
                this.setRemainingFireTicks(0);

                if (target instanceof net.minecraft.entity.item.BoatEntity ||
                    target instanceof net.minecraft.entity.item.minecart.AbstractMinecartEntity) {

                    CompoundNBT data = target.getPersistentData();
                    long lastBoost = data.getLong("RotP_AtA_LastBoostTick");

                    if (lastBoost != level.getGameTime()) {
                         data.putLong("RotP_AtA_LastBoostTick", level.getGameTime());

                         double speedSqr = target.getDeltaMovement().lengthSqr();
                         if (speedSqr > 0.005) {
                             List<FossilMothEntity> boosters = com.github.standobyte.jojo.util.mc.MCUtil.entitiesAround(
                                FossilMothEntity.class, target, 2.0, false,
                                moth -> moth.isAttachedToEntity() &&
                                        moth.entityData.get(ATTACHED_ENTITY_ID) == target.getId() &&
                                        moth.getKineticEnergy() > 0
                             );

                             if (!boosters.isEmpty()) {

                                 if (tickCount % 5 == 0) {
                                      float boostPerMoth = 0.15f;
                                      float totalBoost = Math.min(1.0f, boostPerMoth * boosters.size());

                                      net.minecraft.util.math.vector.Vector3d current = target.getDeltaMovement();
                                      net.minecraft.util.math.vector.Vector3d boostVec;

                                      if (current.lengthSqr() < 0.001) {
                                          boostVec = target.getLookAngle().scale(totalBoost * 0.5);
                                      } else {
                                          boostVec = current.normalize().scale(totalBoost * 0.5);
                                      }

                                      target.setDeltaMovement(current.add(boostVec));

                                      if (!level.isClientSide) {
                                          for (FossilMothEntity m : boosters) {
                                              m.setKineticEnergy(m.getKineticEnergy() - 1);
                                          }
                                      }
                                 }
                             }
                         }
                    }
                }

                if (level.isClientSide && !isAttachedToEntity()) {
                    int energy = getKineticEnergy();
                    if (energy > 0) {
                        float ratio = (energy / (float)getMaxEnergy());

                        float chance = 0.1f + ratio * 0.4f;
                        if (random.nextFloat() < chance) {

                             level.addParticle(ParticleTypes.FLAME,
                                 getX() + (random.nextDouble() - 0.5) * 0.3,
                                 getY() + random.nextDouble() * 0.2 + 0.1,
                                 getZ() + (random.nextDouble() - 0.5) * 0.3,
                                 0, 0.02, 0);
                        }

                        if (ratio > 0.5f && random.nextFloat() < ratio * 0.3f) {
                             level.addParticle(ParticleTypes.ENCHANT,
                                 getX() + (random.nextDouble() - 0.5) * 0.4,
                                 getY() + random.nextDouble() * 0.3,
                                 getZ() + (random.nextDouble() - 0.5) * 0.4,
                                 0, 0.05, 0);
                        }
                    }
                }

                if (!level.isClientSide && this.tickCount % AshesToAshesConstants.SYNC_INTERVAL_TICKS == 0 && target instanceof LivingEntity) {
                    LivingEntity livingTarget = (LivingEntity) target;
                    LivingEntity owner = getOwner();

                    if (owner != null && target == owner) {

                        IStandPower.getStandPowerOptional(owner).ifPresent(power -> {
                            float stamina = power.getStamina();
                            float maxStamina = power.getMaxStamina();
                            float staminaRatio = maxStamina > 0 ? stamina / maxStamina : 0;

                            if (stamina > 0) {
                                power.consumeStamina(0.5f, true);
                            }

                            if (staminaRatio < 0.1f) {
                                detach();
                                return;
                            }

                            if (staminaRatio >= 0.5f) {
                                int energy = getKineticEnergy();
                                if (energy > 0) {

                                    if (random.nextInt(10) == 0) {
                                        setKineticEnergy(energy - 1);
                                    }

                                    livingTarget.addEffect(new EffectInstance(Effects.MOVEMENT_SPEED, 30, 0, true, false, false));
                                    livingTarget.addEffect(new EffectInstance(Effects.DIG_SPEED, 30, 0, true, false, false));
                                }
                            }

                        });
                    } else {
                        int mothCount = 1;
                        List<FossilMothEntity> others = MothQueryUtil.getAttachedMoths(livingTarget, AshesToAshesConstants.QUERY_RADIUS_ATTACHMENT);
                        others.removeIf(m -> m == this);
                        mothCount += others.size();

                        if (mothCount >= 3) {
                            int amplifier = mothCount >= 10 ? 1 : 0;
                            livingTarget.addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN, 30, amplifier, true, false));
                            livingTarget.addEffect(new EffectInstance(Effects.DIG_SLOWDOWN, 30, amplifier, true, false));
                        }
                         if (mothCount >= 10 && livingTarget instanceof PlayerEntity) {
                             livingTarget.addEffect(new EffectInstance(Effects.WEAKNESS, 30, 1, true, false));
                        }
                    }
                }

            } else {
                if (!level.isClientSide) detach();
            }
            return;
        }

        if (!level.isClientSide) {
            LivingEntity owner = getOwner();
            if (owner == null || !owner.isAlive()) {
                this.remove();
                return;
            }

            IStandPower.getStandPowerOptional(owner).ifPresent(power -> {
                if (power.getType() != InitStands.STAND_ASHES_TO_ASHES.getStandType() || !power.isActive()) {
                    this.remove();
                    return;
                }

                if (power.getStandManifestation() instanceof StandEntity) {
                    StandEntity standEntity = (StandEntity) power.getStandManifestation();

                    boolean shouldFollowStand = false;
                    if (standEntity.isManuallyControlled() || standEntity.isRemotePositionFixed()) {
                        boolean remoteFollowEnabled = owner.getCapability(
                                com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY)
                                .map(com.babelmoth.rotp_ata.capability.IMothPool::isRemoteFollow).orElse(false);
                        if (remoteFollowEnabled && mothPoolIndex >= 0) {
                            int ratio = owner.getCapability(
                                    com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY)
                                    .map(com.babelmoth.rotp_ata.capability.IMothPool::getRemoteFollowRatio).orElse(50);
                            int orbitCount = owner.getCapability(
                                    com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY)
                                    .map(com.babelmoth.rotp_ata.capability.IMothPool::getOrbitMothCount).orElse(20);
                            int followCount = Math.max(1, orbitCount * ratio / 100);
                            shouldFollowStand = (mothPoolIndex % orbitCount) < followCount;
                        }
                    }

                    if (shouldFollowStand && standEntity.isRemotePositionFixed()) {
                        if (!isStaying) {
                            stayX = standEntity.getX() + (random.nextDouble() - 0.5) * 2;
                            stayY = standEntity.getY() + 1 + random.nextDouble() * 1.5;
                            stayZ = standEntity.getZ() + (random.nextDouble() - 0.5) * 2;
                            isStaying = true;
                        }
                        double navX = stayX + (random.nextDouble() - 0.5) * 1.5;
                        double navY = stayY + (random.nextDouble() - 0.5) * 0.8;
                        double navZ = stayZ + (random.nextDouble() - 0.5) * 1.5;
                        this.navigation.moveTo(navX, navY, navZ, 0.5);

                    } else if (shouldFollowStand && standEntity.isManuallyControlled()) {
                        isStaying = false;
                        double navX = standEntity.getX() + (random.nextDouble() - 0.5) * 2;
                        double navY = standEntity.getY() + 1 + random.nextDouble() * 1.5;
                        double navZ = standEntity.getZ() + (random.nextDouble() - 0.5) * 2;
                        this.navigation.moveTo(navX, navY, navZ, 1.2);

                    } else {
                        isStaying = false;
                        double navX = owner.getX() + (random.nextDouble() - 0.5) * 2.5;
                        double navY = owner.getY() + 1 + random.nextDouble() * 1.5;
                        double navZ = owner.getZ() + (random.nextDouble() - 0.5) * 2.5;
                        this.navigation.moveTo(navX, navY, navZ, 0.8);
                    }

                    if (!isStaying) {
                        float maxRange = (float) standEntity.getMaxRange();

                        if (this.distanceTo(owner) > maxRange) {
                            this.remove();
                        }
                    }
                } else {

                   if (!isAttached() && !isAttachedToEntity()) {
                        double navX = owner.getX();
                        double navY = owner.getY() + 1;
                        double navZ = owner.getZ();
                        this.navigation.moveTo(navX, navY, navZ, 1.0);

                        if (this.distanceToSqr(owner) < 4.0) {
                            this.remove();
                        }
                   }
                }
            });
        }
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source == DamageSource.OUT_OF_WORLD) {
            return false;
        }

        if (source == DamageSource.IN_WALL) {
            return true;
        }
        LivingEntity owner = getOwner();
        if (owner != null && owner.is(source.getEntity())) {
            return true;
        }
        if (owner instanceof PlayerEntity && ((PlayerEntity) owner).abilities.invulnerable) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    public void playHurtSoundEffect(DamageSource source) {
        this.playHurtSound(source);
    }

    private boolean forceVisibleForAll = false;

    public void setForceVisibleForAll(boolean value) {
        this.forceVisibleForAll = value;
    }

    public boolean isVisibleForAll() {
        return forceVisibleForAll;
    }

    @Override
    public boolean isInvisible() {
        return !isVisibleForAll() || underInvisibilityEffect();
    }

    public boolean underInvisibilityEffect() {
        return super.isInvisible();
    }

    @Override
    public boolean isInvisibleTo(PlayerEntity player) {
        return !isVisibleForAll() && !StandUtil.clStandEntityVisibleTo(player)
                || !JojoModUtil.seesInvisibleAsSpectator(player) && underInvisibilityEffect();
    }

    @Nullable
    @Override
    public AgeableEntity getBreedOffspring(ServerWorld world, AgeableEntity mate) {
        return null;
    }

    @Override
    protected int getExperienceReward(PlayerEntity player) {
        return 0;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {
        if (this.desyncDelay > 0) {
            this.desyncDelay--;
            return PlayState.STOP;
        }

        if (isAttached()) {
            return PlayState.STOP;
        }

        event.getController().setAnimation(new AnimationBuilder().addAnimation("idle", true));
        return PlayState.CONTINUE;
    }

    @Override
    public void registerControllers(AnimationData data) {
        AnimationController<FossilMothEntity> controller = new AnimationController<>(this, "controller", 0, this::predicate);
        data.addAnimationController(controller);
    }

    @Override
    public AnimationFactory getFactory() {
        return this.factory;
    }

    public boolean isAttached() {
        return this.entityData.get(ATTACHED_FACE) != -1;
    }

    public Direction getAttachedFace() {
        byte index = this.entityData.get(ATTACHED_FACE);
        return index == -1 ? null : Direction.from3DDataValue(index);
    }

    public void attachTo(BlockPos pos, Direction face) {

        if (this.attachedPos != null && this.level instanceof net.minecraft.world.World) {
            com.babelmoth.rotp_ata.util.ProtectedBlockRegistry.unregister((net.minecraft.world.World) this.level, this.attachedPos);
        }

        this.attachedPos = pos;
        this.entityData.set(ATTACHED_FACE, (byte) face.get3DDataValue());

        if (this.level instanceof net.minecraft.world.World) {
            com.babelmoth.rotp_ata.util.ProtectedBlockRegistry.register((net.minecraft.world.World) this.level, pos);
        }

        float offX = (this.random.nextFloat() - 0.5f) * 0.6f;
        float offY = (this.random.nextFloat() - 0.5f) * 0.6f;
        float rot = this.random.nextFloat() * 360.0f;

        this.entityData.set(ATTACHED_OFFSET_X, offX);
        this.entityData.set(ATTACHED_OFFSET_Y, offY);
        this.entityData.set(ATTACHED_ROTATION, rot);

        double x, y, z;

        if (face == Direction.UP) {
            x = pos.getX() + 0.5 + offX;
            y = pos.getY() + 1.01;
            z = pos.getZ() + 0.5 + offY;
        } else if (face == Direction.DOWN) {
            x = pos.getX() + 0.5 + offX;
            y = pos.getY() - 0.01;
            z = pos.getZ() + 0.5 + offY;
        } else if (face == Direction.NORTH || face == Direction.SOUTH) {

            x = pos.getX() + 0.5 + offX;
            y = pos.getY() + 0.5 + offY;
            z = pos.getZ() + 0.5 + face.getStepZ() * 0.55;
        } else {

            x = pos.getX() + 0.5 + face.getStepX() * 0.55;
            y = pos.getY() + 0.5 + offY;
            z = pos.getZ() + 0.5 + offX;
        }

        this.setPos(x, y, z);

        float yaw = 0;
        float pitch = 0;
        switch (face) {
            case UP: pitch = -90; break;
            case DOWN: pitch = 90; break;
            case NORTH: yaw = 180; break;
            case SOUTH: yaw = 0; break;
            case WEST: yaw = 90; break;
            case EAST: yaw = -90; break;
        }
        this.yRot = yaw;
        this.xRot = pitch;

        this.setNoAi(true);
    }

    public void detach() {

        if (this.attachedPos != null && this.level instanceof net.minecraft.world.World) {
            com.babelmoth.rotp_ata.util.ProtectedBlockRegistry.unregister((net.minecraft.world.World) this.level, this.attachedPos);
        }

        this.entityData.set(ATTACHED_FACE, (byte) -1);
        this.entityData.set(ATTACHED_ENTITY_ID, -1);
        this.attachedPos = null;
        this.setNoGravity(true);
        this.setNoAi(false);
    }

    public boolean isAttachedToEntity() {
        return this.entityData.get(ATTACHED_ENTITY_ID) != -1;
    }

    public void attachToEntity(Entity target) {
        this.entityData.set(ATTACHED_ENTITY_ID, target.getId());

        float radius = target.getBbWidth() * 0.5f + 0.1f;
        float height = target.getBbHeight();

        float angle = this.random.nextFloat() * 360.0f;
        double rad = Math.toRadians(angle);

        float offX = (float) (Math.sin(rad) * radius);
        float offZ = (float) (Math.cos(rad) * radius);
        float offY = this.random.nextFloat() * height;

        this.entityData.set(ATTACHED_OFFSET_X, offX);
        this.entityData.set(ATTACHED_OFFSET_Y, offY);
        this.entityData.set(ATTACHED_OFFSET_Z, offZ);

        this.entityData.set(ATTACHED_ROTATION, this.random.nextFloat() * 360.0f);

        LivingEntity owner = getOwner();
        if (owner != null && target.is(owner)) {
            this.entityData.set(SHIELD_TARGET_ID, target.getId());
        }

        this.setNoAi(true);
    }

    private static final String ITEM_RETRIEVED_TAG = "ata_retrieved";

    private void startCarryingItem(ItemEntity itemEntity) {
        if (itemEntity == null || itemEntity.getItem().isEmpty()) return;
        if (itemEntity.getPersistentData().getBoolean(ITEM_RETRIEVED_TAG)) return;
        LivingEntity owner = getOwner();
        if (owner == null) return;

        itemEntity.getPersistentData().putBoolean(ITEM_RETRIEVED_TAG, true);

        this.entityData.set(CARRIED_ITEM_ID, itemEntity.getId());

        itemEntity.setOwner(this.getUUID());
        itemEntity.setNoPickUpDelay();

        itemEntity.setNoGravity(true);
    }

    private void giveItemToOwner(ItemEntity itemEntity) {
        if (itemEntity == null || itemEntity.getItem().isEmpty()) return;
        LivingEntity owner = getOwner();
        if (owner == null) return;

        ItemStack toGive = itemEntity.getItem().copy();
        itemEntity.remove();

        if (owner instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) owner;
            if (!player.inventory.add(toGive)) {

                ItemEntity drop = new ItemEntity(level, owner.getX(), owner.getY() + 0.5, owner.getZ(), toGive);
                drop.setNoPickUpDelay();
                level.addFreshEntity(drop);
            }
        } else {

            ItemEntity drop = new ItemEntity(level, owner.getX(), owner.getY() + 0.5, owner.getZ(), toGive);
            drop.setNoPickUpDelay();
            level.addFreshEntity(drop);
        }
    }

    @Override
    public void remove(boolean keepData) {

        if (!level.isClientSide) {
            int carriedItemId = this.entityData.get(CARRIED_ITEM_ID);
            if (carriedItemId != -1) {
                Entity carriedItem = level.getEntity(carriedItemId);
                if (carriedItem instanceof ItemEntity) {
                    ItemEntity itemEntity = (ItemEntity) carriedItem;
                    if (!itemEntity.removed && !itemEntity.getItem().isEmpty()) {
                        itemEntity.setNoGravity(false);
                        itemEntity.setOwner((java.util.UUID) null);
                    }
                }
            }
        }

        if (!level.isClientSide && mothPoolIndex != -1) {
            LivingEntity owner = getOwner();
            if (owner != null) {
                 owner.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
                     if (this.isDeadOrDying()) {
                         pool.killMoth(mothPoolIndex);
                     } else if (!keepData) {

                         pool.recallMoth(mothPoolIndex);
                     }
                 });
            }
        }

        if (this.attachedPos != null && this.level instanceof net.minecraft.world.World) {
            com.babelmoth.rotp_ata.util.ProtectedBlockRegistry.unregister((net.minecraft.world.World) this.level, this.attachedPos);
        }

        if (this.level.isClientSide) {
            if (this.entityData.get(IS_RECALLING)) {

                spawnDespawnParticles();
            }
        }
        super.remove(keepData);
    }

    public void detonateKinetic() {
        detonateKineticGroup(java.util.Collections.singletonList(this));
    }

    public void detonateKineticGroup(List<FossilMothEntity> group) {
        if (this.level.isClientSide || group == null || group.isEmpty()) return;

        List<FossilMothEntity> validGroup = new java.util.ArrayList<>();
        int totalKinetic = 0;
        boolean useHamon = false;
        double cx = 0.0D;
        double cy = 0.0D;
        double cz = 0.0D;

        for (FossilMothEntity moth : group) {
            if (moth == null || !moth.isAlive() || moth.level != this.level || moth.getOwner() != this.getOwner()) {
                continue;
            }
            validGroup.add(moth);
            totalKinetic += Math.max(0, moth.getKineticEnergy());
            if (moth.getHamonEnergy() > 0) {
                useHamon = true;
            }
            cx += moth.getX();
            cy += moth.getY();
            cz += moth.getZ();
        }

        if (validGroup.isEmpty() || totalKinetic <= 0) {
            return;
        }

        cx /= validGroup.size();
        cy /= validGroup.size();
        cz /= validGroup.size();

        float chargeRatio = Math.min(1.0F, (float) totalKinetic / (float) (validGroup.size() * getMaxEnergy()));
        float radius = Math.min(4.0F, 0.8F + 0.35F * validGroup.size() + 2.2F * chargeRatio);
        float damage = 1.0F + totalKinetic * 0.45F;

        this.level.explode(this, cx, cy, cz, radius, Explosion.Mode.NONE);

        if (this.level instanceof net.minecraft.world.server.ServerWorld) {
            net.minecraft.world.server.ServerWorld serverWorld = (net.minecraft.world.server.ServerWorld) this.level;
            int particleCount = 20 + validGroup.size() * 8 + Math.min(60, totalKinetic * 2);
            for (int i = 0; i < particleCount; i++) {
                double px = cx + (this.random.nextDouble() - 0.5) * radius * 2.0D;
                double py = cy + (this.random.nextDouble() - 0.5) * radius * 2.0D;
                double pz = cz + (this.random.nextDouble() - 0.5) * radius * 2.0D;
                double vx = (this.random.nextDouble() - 0.5) * 0.5D;
                double vy = this.random.nextDouble() * 0.3D;
                double vz = (this.random.nextDouble() - 0.5) * 0.5D;
                if (useHamon) {
                    serverWorld.sendParticles(com.github.standobyte.jojo.init.ModParticles.HAMON_SPARK.get(), px, py, pz, 1, vx, vy, vz, 0.1D);
                } else {
                    serverWorld.sendParticles(com.babelmoth.rotp_ata.init.InitParticles.FOSSIL_ASH.get(), px, py, pz, 1, vx, vy, vz, 0.1D);
                }
            }
        }

        List<LivingEntity> targets = this.level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.util.math.AxisAlignedBB(cx, cy, cz, cx, cy, cz).inflate(radius + 1.0D));
        LivingEntity owner = getOwner();
        net.minecraft.util.math.vector.Vector3d center = new net.minecraft.util.math.vector.Vector3d(cx, cy, cz);

        for (LivingEntity target : targets) {
            if (target == owner) continue;
            if (validGroup.contains(target)) continue;

            double distSqr = target.distanceToSqr(center);
            if (distSqr > radius * radius) continue;

            float distRatio = 1.0f - (float) Math.sqrt(distSqr) / radius;
            if (distRatio < 0) distRatio = 0;

            float actualDamage = damage * distRatio;
            boolean hurt = target.hurt(DamageSource.explosion(this instanceof LivingEntity ? (LivingEntity) this : owner), actualDamage);
            if (hurt && useHamon) {
                com.github.standobyte.jojo.util.mc.damage.DamageUtil.dealHamonDamage(target, 0.5F, this, owner);
            }
        }

        for (FossilMothEntity moth : validGroup) {
            moth.setKineticEnergy(0);
            moth.setHamonEnergy(0);
            moth.setDissipateOnRemove(true);
            moth.remove();
        }
    }

    private boolean exfoliatingDetonated = false;

    public void detonateExfoliating() {
        if (this.level.isClientSide || exfoliatingDetonated) return;
        exfoliatingDetonated = true;

        double groupRadius = 8.0;
        java.util.List<FossilMothEntity> nearbyMoths = this.level.getEntitiesOfClass(
                FossilMothEntity.class,
                this.getBoundingBox().inflate(groupRadius),
                m -> m != this && m.getOwner() == this.getOwner()
                        && !m.exfoliatingDetonated && m.isAlive());

        int totalEnergy = getTotalEnergy();
        int maxEnergy = getMaxEnergy();
        boolean hasHamon = getHamonEnergy() > 0;
        double cx = this.getX(), cy = this.getY(), cz = this.getZ();
        int mothCount = 1;

        for (FossilMothEntity moth : nearbyMoths) {
            moth.exfoliatingDetonated = true;
            totalEnergy += moth.getTotalEnergy();
            if (moth.getHamonEnergy() > 0) hasHamon = true;
            cx += moth.getX();
            cy += moth.getY();
            cz += moth.getZ();
            mothCount++;
        }

        cx /= mothCount;
        cy /= mothCount;
        cz /= mothCount;

        float avgChargeRatio = (float) totalEnergy / (float) (mothCount * maxEnergy);

        float radius = 1.5f + (mothCount - 1) * 1.5f * avgChargeRatio;
        radius = Math.min(radius, 12.0f);

        int duration = 100 + (int) ((mothCount - 1) * 100 * avgChargeRatio);
        duration = Math.min(duration, 600);

        ExfoliatingAshCloudEntity cloud = new ExfoliatingAshCloudEntity(this.level, cx, cy, cz);
        cloud.setOwner(getOwner());
        cloud.setDuration(duration);
        cloud.setRadius(radius);
        if (hasHamon) cloud.setHamonInfused(true);
        this.level.addFreshEntity(cloud);

        for (FossilMothEntity moth : nearbyMoths) {
            moth.remove();
        }
        this.remove();
    }

    private static final int AMBER_COLOR = 0xe7801a;
    private static final double SENSING_RANGE = 16.0;

    public void tickKineticSensing() {

        if (!this.level.isClientSide) return;
        if (!isKineticSensingEnabled()) return;

        net.minecraft.entity.LivingEntity owner = getOwner();
        if (owner == null || localPlayerCheck == null || !localPlayerCheck.test(owner)) {
            return;
        }

        java.util.List<net.minecraft.entity.LivingEntity> nearbyEntities = this.level.getEntitiesOfClass(
            net.minecraft.entity.LivingEntity.class,
            this.getBoundingBox().inflate(SENSING_RANGE),
            e -> e != this && e != owner && !(e instanceof FossilMothEntity) && isMovingOrInteracting(e)
        );

        for (net.minecraft.entity.LivingEntity target : nearbyEntities) {
            target.getCapability(com.github.standobyte.jojo.capability.entity.EntityUtilCapProvider.CAPABILITY).ifPresent(cap -> {
                cap.setClGlowingColor(java.util.OptionalInt.of(AMBER_COLOR), 40);
            });
        }
    }

    private boolean isMovingOrInteracting(net.minecraft.entity.LivingEntity entity) {

        net.minecraft.util.math.vector.Vector3d vel = entity.getDeltaMovement();
        double speedSqr = vel.x * vel.x + vel.z * vel.z;
        if (speedSqr > 0.001) return true;

        if (entity.swinging || vel.y > 0.1) return true;

        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundNBT compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("MothPoolIndex", this.mothPoolIndex);
    }

    @Override
    public void readAdditionalSaveData(CompoundNBT compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("MothPoolIndex")) {
            this.mothPoolIndex = compound.getInt("MothPoolIndex");
        }
    }

    @Override
    public void remove() {
        if (!level.isClientSide && mothPoolIndex != -1) {
            LivingEntity owner = getOwner();
            if (owner != null) {
                owner.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
                    syncToPool();
                    if (dissipateOnRemove || !this.isAlive()) {
                        pool.killMoth(mothPoolIndex);
                    } else {
                        pool.recallMoth(mothPoolIndex);
                    }
                    if (owner instanceof net.minecraft.entity.player.ServerPlayerEntity) {
                        pool.sync((net.minecraft.entity.player.ServerPlayerEntity)owner);
                    }
                });
            }
        }
        super.remove();
    }
}
