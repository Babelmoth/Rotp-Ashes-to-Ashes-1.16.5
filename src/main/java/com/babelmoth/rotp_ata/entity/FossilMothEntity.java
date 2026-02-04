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
// import com.github.standobyte.jojo.init.ModEffects; // Invalid
import com.github.standobyte.jojo.init.ModStatusEffects; // Corrected
// import com.github.standobyte.jojo.power.nonstand.INonStandPower; // Invalid pkg
// import com.github.standobyte.jojo.power.nonstand.type.NonStandPowerType; // Invalid pkg
// import com.github.standobyte.jojo.init.power.hamon.ModHamon; // Invalid pkg



/**
 * Fossil moth entity for Ashes to Ashes stand.
 * Handles attachment, energy management, and combat behaviors.
 */
public class FossilMothEntity extends TameableEntity implements IFlyingAnimal, IAnimatable {

    private final AnimationFactory factory = new AnimationFactory(this);
    
    // 记录驻留位置
    private double stayX, stayY, stayZ;
    private boolean isStaying = false;
    private int desyncDelay;
    // replaced with DataParameter
    
    // 依附状态: -1 = 飞行, 0-5 = Direction.getIndex()
    private static final DataParameter<Boolean> IS_RECALLING = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Boolean> IS_SHIELD_PERSISTENT = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.BOOLEAN);

    // 依附状态: -1 = 飞行, 0-5 = Direction.getIndex()
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
    
    // Kinetic Piercing & Bullet Params
    public static final DataParameter<Boolean> IS_PIERCING_CHARGING = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.BOOLEAN);
    public static final DataParameter<Boolean> IS_PIERCING_FIRING = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.BOOLEAN);
    public static final DataParameter<Float> PIERCING_DAMAGE = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.FLOAT);
    public static final DataParameter<Float> PIERCING_SPEED = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.FLOAT);
    public static final DataParameter<Boolean> IS_BULLET = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.BOOLEAN);
    public static final DataParameter<Boolean> KINETIC_SENSING_ENABLED = EntityDataManager.defineId(FossilMothEntity.class, DataSerializers.BOOLEAN);
    private long lastShieldTick = 0;
    
    public void refreshShield() {
         this.lastShieldTick = level.getGameTime();
    }
    
    public boolean isRecalling() {
        return this.entityData.get(IS_RECALLING);
    }
    
    // --- Moth Pool Integration ---
    private int mothPoolIndex = -1;
    private boolean kineticEnergyChanged = false;
    private boolean hamonEnergyChanged = false;
    
    public void setMothPoolIndex(int index) {
        this.mothPoolIndex = index;
    }
    
    public int getMothPoolIndex() {
        return this.mothPoolIndex;
    }
    
    /**
     * Syncs local DataParameters TO the Pool.
     * Called when energy changes.
     */
    public void updatePool() {
        if (level.isClientSide || mothPoolIndex == -1) return;
        LivingEntity owner = getOwner();
        if (owner == null) return;
        
        owner.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
            pool.setMothKinetic(mothPoolIndex, getKineticEnergy());
            pool.setMothHamon(mothPoolIndex, getHamonEnergy());
        });
    }
    
    /**
     * Syncs local DataParameters FROM the Pool.
     * Called on tick to ensure consistency.
     */
    public void syncFromPool() {
        if (level.isClientSide || mothPoolIndex == -1) return;
        LivingEntity owner = getOwner();
        if (owner == null) return;
        
        owner.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
            if (!pool.isSlotActive(mothPoolIndex)) {
                this.remove(); // Pool killed us
                return;
            }
            
            this.entityData.set(KINETIC_ENERGY, pool.getMothKinetic(mothPoolIndex));
            this.entityData.set(HAMON_ENERGY, pool.getMothHamon(mothPoolIndex));
        });
    }

    /**
     * Syncs local data TO the Pool.
     * Ensured that world-gained energy is persisted.
     */
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
    // -----------------------------
    
    // ... logic ...
    
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
        
        // Apply kinetic sensing state to new moths if enabled
        boolean sensingEnabled = com.babelmoth.rotp_ata.action.AshesToAshesKineticSensing.isSensingEnabled(user);
        
        for (int i = 0; i < toSpawn; i++) {
            FossilMothEntity moth = new FossilMothEntity(level, user);
            
            // Apply kinetic sensing state if enabled
            if (sensingEnabled) {
                moth.setKineticSensingEnabled(true);
            }
            
            // 在用户周围生成
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
    }

    public FossilMothEntity(EntityType<? extends FossilMothEntity> type, World world) {
        super(type, world);
        this.moveControl = new FlyingMovementController(this, 70, true);
        this.setNoGravity(true);
        this.setPathfindingMalus(PathNodeType.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(PathNodeType.WATER, -1.0F);
        // 随机启动延迟（0-20 tick）实现动画去同步
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
    }
    
    // Piercing/Bullet Accessors
    
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
    
    // Public getter for entityData to allow access from other packages
    public EntityDataManager getEntityData() {
        return this.entityData;
    }
    
    // Trigger Method
    // Trigger Method
    public void piercingCharge(LivingEntity shooter) {
        this.entityData.set(IS_PIERCING_CHARGING, true);
        this.entityData.set(IS_PIERCING_FIRING, false);
        this.setNoAi(true);
        this.setNoGravity(true);
        this.detach(); // Detach if attached
        
        // Teleport to front of shooter
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
        
        // Calculate damage based on kinetic energy
        // Bullet mode overrides this in tick() but setting it here provides a fallback base
        float baseDmg = 5.0f;
        float energyDmg = (float)getKineticEnergy() * 1.5f; // Max 10 energy -> 15 dmg (Total 20)
        this.entityData.set(PIERCING_DAMAGE, baseDmg + energyDmg);
        
        this.setDeltaMovement(direction.normalize().scale(speed));
        this.setNoAi(true);
    }
    
    private int piercingLifeTime = 0;
    
    public void swarmTo(Entity target) {
        this.entityData.set(SWARM_TARGET_ID, target.getId());
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
        // TODO: Access resolve level from owner to switch between 100 and 200
        return MAX_ENERGY_BASE;
    }
    
    // Combined energy for display and attribute scaling
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
        int energy = getTotalEnergy(); // Combined kinetic + hamon energy
        int maxEnergy = getMaxEnergy();
        float ratio = (float) energy / maxEnergy;
        
        // HP: 5 -> 50, Armor: 2 -> 20, Toughness: 0 -> 5
        double newHealth = 5.0 + 45.0 * ratio;
        double newArmor = 2.0 + 18.0 * ratio;
        double newToughness = 5.0 * ratio;
        double newSpeed = 0.4 - 0.2 * ratio; // Ground speed
        double newFlySpeed = 0.8 - 0.4 * ratio; // Flying speed also decreases
        
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(newHealth);
        this.getAttribute(Attributes.ARMOR).setBaseValue(newArmor);
        this.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(newToughness);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(newSpeed);
        this.getAttribute(Attributes.FLYING_SPEED).setBaseValue(newFlySpeed);
        
        // Heal relative to ratio if needed, or just set? For now just set max.
        // If current health > new max, clamp it.
        // If growing, maybe heal?
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
                .add(Attributes.MOVEMENT_SPEED, 0.4D)   // 适当降低速度
                .add(Attributes.FLYING_SPEED, 0.8D)     // 适当降低飞行速度
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
    
    // 禁用掉落伤害和粒子效果
    @Override
    public boolean causeFallDamage(float distance, float multiplier) {
        return false;
    }
    
    @Override
    protected void checkFallDamage(double y, boolean onGround, net.minecraft.block.BlockState state, BlockPos pos) {
        // 不产生掉落粒子效果
    }
    
    /**
     * 获取当前alpha透明度(用于渲染)
     * 前15 tick 从0渐变到1实现淡入效果
     */
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
        
        // 检查是否为波纹伤害 (需检查 Ripples of the Past 的波纹伤害类型)
        // 假设 Ripple damage 有特定标识，或者我们简单判断是否为 Magic + User是波纹使者?
        // 暂时假设所有非物理伤害为特殊伤害，或者检查 source.getMsgId().contains("ripple")?
        boolean isHamon = source.getMsgId().contains("ripple") || source.getMsgId().contains("hamon") || source.getMsgId().contains("overdrive");
        
        if (isHamon) {
            setHamonEnergy(getHamonEnergy() + (int)amount);
            return false;
        }
        
        // 物理伤害吸收逻辑
        // 排除: 摔落(已免疫), 虚空, 指令kill, 魔法(药水), 饥饿, 窒息
        if (!source.isBypassArmor() && !source.isMagic() && source != DamageSource.OUT_OF_WORLD) {
            int currentEnergy = getKineticEnergy();
            int max = getMaxEnergy();
            
            if (currentEnergy < max) {
                // 吸收动能
                int absorbed = Math.min((int)amount, max - currentEnergy);
                setKineticEnergy(currentEnergy + absorbed);
                return false; // 完全无效化
            } else {
                // 动能已满，伤害转移给本体
                LivingEntity owner = getOwner();
                if (owner != null) {
                    // 计算比例: 50血对应2血 -> 25:1
                    // 假设总血量50 (max), 本体每只蛾子对应2点.
                    float ratio = 2.0f / 50.0f; // 0.04
                    float damageToOwner = amount * ratio;
                    if (damageToOwner > 0) {
                        owner.hurt(source, damageToOwner);
                    }
                }
                // 自己也受伤
                return super.hurt(source, amount);
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
        // Save data handled by MothPool when remove() is called
        this.entityData.set(IS_RECALLING, true);
        this.detach();
    }
    
    private void spawnDespawnParticles() {
        // 消散特效 - 生成灰尘粒子向下掉落 (vy < -0.01 触发扩散模式)
        if (level.isClientSide) {
            for (int i = 0; i < 10; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double dist = 0.08 + random.nextDouble() * 0.1;
                double vx = Math.cos(angle) * dist;
                double vz = Math.sin(angle) * dist;
                double vy = -0.02 - random.nextDouble() * 0.03; // 负值触发下落模式
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
        // --- Moth Pool Sync ---
        if (!level.isClientSide) {
             LivingEntity owner = getOwner();
             if (owner != null) {
                 owner.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
                     if (mothPoolIndex == -1) {
                         // Allocate slot if not already assigned
                         int idx = pool.allocateSlot();
                         if (idx != -1) {
                             this.mothPoolIndex = idx;
                         } else {
                             // Pool full
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
        // ----------------------
        
        // Handle Piercing Firing FIRST - completely override normal tick
        if (isPiercingFiring()) {
            this.piercingLifeTime++;
            if (this.piercingLifeTime > 100 || this.getDeltaMovement().lengthSqr() < 0.01) {
                spawnDespawnParticles();
                this.remove();
                return;
            }
            
            // --- High Speed Hit Detection (Raycast) ---
            net.minecraft.util.math.vector.Vector3d start = this.position();
            net.minecraft.util.math.vector.Vector3d motion = this.getDeltaMovement();
            net.minecraft.util.math.vector.Vector3d end = start.add(motion);
            
            // 1. Raytrace Blocks
            net.minecraft.util.math.RayTraceResult hitResult = this.level.clip(new net.minecraft.util.math.RayTraceContext(
                start, end, 
                net.minecraft.util.math.RayTraceContext.BlockMode.COLLIDER, 
                net.minecraft.util.math.RayTraceContext.FluidMode.NONE, 
                this));
            
            if (hitResult.getType() != net.minecraft.util.math.RayTraceResult.Type.MISS) {
                end = hitResult.getLocation(); // Stop at block hit
            }
            
            // 2. Iterate Entities along path
            // Expand box to cover the entire movement path + inflation
            net.minecraft.util.math.AxisAlignedBB collisionBox = this.getBoundingBox().expandTowards(motion).inflate(1.0);
            List<Entity> hits = this.level.getEntities(this, collisionBox, e -> e instanceof LivingEntity && e != getOwner() && !e.isSpectator());
            
            // Calculate Damage (using total energy)
            boolean useHamon = getHamonEnergy() > 0;
            float chargeRatio = (float)getTotalEnergy() / (float)getMaxEnergy();
            float baseDmg = 4.0f;
            float maxDmg = 18.0f;
            float damage = baseDmg + (maxDmg - baseDmg) * chargeRatio;
            
            // Scaled Effects
            float knockbackStrength = 0.5f + 2.0f * chargeRatio; 
            float penetrationThreshold = 0.6f + 2.4f * chargeRatio; 
            float hardnessResistance = 0.1f - 0.05f * chargeRatio; 
            float wallExplosionSize = 0.5f + 1.0f * chargeRatio;

            // BULLET MODE OVERRIDES
            if (isBullet()) {
                damage = 6.0f; // Fixed damage for bullet? Or just some value.
                knockbackStrength = 1.5f;
                wallExplosionSize = 0.5f; // Small explosion
                penetrationThreshold = 0.0f; // No penetration
            }
            
            for (Entity e : hits) {
                // Precise check: does vector intersect entity box?
                // Or just lenient check since we filtered by path box
                if (e.getBoundingBox().intersects(this.getBoundingBox().expandTowards(motion).inflate(0.5))) {
                     LivingEntity target = (LivingEntity)e;
                     
                     // --- Guard-Breaking Logic ---
                     // Disable player shields
                     if (target instanceof net.minecraft.entity.player.PlayerEntity) {
                         net.minecraft.entity.player.PlayerEntity player = (net.minecraft.entity.player.PlayerEntity)target;
                         if (player.isBlocking()) {
                             com.github.standobyte.jojo.util.mc.damage.DamageUtil.disableShield(player, 1.0F);
                         }
                     }
                     // Break stand blocking
                     if (target instanceof com.github.standobyte.jojo.entity.stand.StandEntity) {
                         com.github.standobyte.jojo.entity.stand.StandEntity standTarget = (com.github.standobyte.jojo.entity.stand.StandEntity)target;
                         standTarget.breakStandBlocking(com.github.standobyte.jojo.entity.stand.StandStatFormulas.getBlockingBreakTicks(standTarget.getDurability()));
                     }
                     
                     // Deal Damage (always physical damage first)
                     boolean dmgSuccess = target.hurt(DamageSource.mobAttack(getOwner() instanceof LivingEntity ? (LivingEntity)getOwner() : this), damage);
                     
                     // Add small bonus Hamon damage if moth has Hamon energy
                     if (dmgSuccess && useHamon) {
                         com.github.standobyte.jojo.util.mc.damage.DamageUtil.dealHamonDamage(target, 0.5F, this, getOwner());
                     }
                     
                     if (dmgSuccess) {
                         // --- Heavy Attack Knockback API ---
                         net.minecraft.util.math.vector.Vector3d knockbackDir = motion.normalize();
                         
                         // Apply directional knockback
                         com.github.standobyte.jojo.util.mc.damage.DamageUtil.knockback(target, knockbackStrength, 
                             (float) -Math.atan2(knockbackDir.x, knockbackDir.z) * (180F / (float)Math.PI));
                         
                         // Capture variables for lambda
                         final float finalKnockback = knockbackStrength;
                         final float finalExplosion = wallExplosionSize;
                         final float finalDamage = damage;
                         final LivingEntity finalAttacker = getOwner() instanceof LivingEntity ? (LivingEntity)getOwner() : this;
                         
                         // Wall collision damage/explosion also scales
                         com.github.standobyte.jojo.util.mc.damage.KnockbackCollisionImpact.getHandler(target).ifPresent(cap -> cap
                             .onPunchSetKnockbackImpact(knockbackDir.scale(finalKnockback), finalAttacker)
                             .withImpactExplosion(finalExplosion, null, finalDamage * 0.3f)
                         );
                     }
                }
            }
            
            // 3. Handle Hit Result (Block Collision) - With Penetration Logic
            if (hitResult.getType() == net.minecraft.util.math.RayTraceResult.Type.BLOCK) {
                 net.minecraft.util.math.BlockRayTraceResult blockHit = (net.minecraft.util.math.BlockRayTraceResult)hitResult;
                 BlockPos targetPos = blockHit.getBlockPos();
                 net.minecraft.block.BlockState state = level.getBlockState(targetPos);
                 
                 if (!state.isAir(level, targetPos) && state.getMaterial() != net.minecraft.block.material.Material.BARRIER) {
                     float hardness = state.getDestroySpeed(level, targetPos);
                     
                     if (!isBullet() && hardness >= 0 && hardness < penetrationThreshold) {
                         // --- PENETRATE soft blocks (Only if NOT Bullet) ---
                         level.destroyBlock(targetPos, true);
                         
                         // Reduce speed based on hardness and charge
                         float speedReduction = hardness * hardnessResistance;
                         float currentSpeed = (float)motion.length();
                         float newSpeed = Math.max(currentSpeed - speedReduction, 0.2f); 
                         
                         if (newSpeed < 0.3f) {
                             spawnDespawnParticles();
                             this.remove();
                             return;
                         }
                         
                         this.setDeltaMovement(motion.normalize().scale(newSpeed));
                         
                         // Continue logic...
                     } else {
                         // --- STOP on hard blocks OR Bullet Mode ---
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
                                     explosionMode // BREAK for Kinetic Piercing, NONE for Bullet
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
            
            // Move entity to new position
            this.setPos(end.x, end.y, end.z);
            
            // Skip super.tick()
            return;
        }
        
        // Handle Piercing Charging - position is set by Action, just render particles
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
            // Don't return here - let super.tick() run for proper entity sync
        }

        super.tick();
        
        // Apply kinetic sensing state from owner if enabled (for newly spawned moths)
        if (!level.isClientSide) {
            LivingEntity owner = getOwner();
            if (owner != null) {
                boolean shouldBeEnabled = com.babelmoth.rotp_ata.action.AshesToAshesKineticSensing.isSensingEnabled(owner);
                if (shouldBeEnabled != isKineticSensingEnabled()) {
                    setKineticSensingEnabled(shouldBeEnabled);
                }
            }
        }
        
        // Kinetic Sensing passive effect
        tickKineticSensing();
        
        // 生成特效 - 前15 tick显示灰尘聚集效果，蛾子在前8 tick隐形
        if (level.isClientSide && this.tickCount < 15) {
            // 灰尘从周围聚集到中心 - 使用自定义FOSSIL_ASH粒子
            for (int i = 0; i < 3; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double dist = 0.5 + random.nextDouble() * 0.5;
                double px = getX() + Math.cos(angle) * dist;
                double pz = getZ() + Math.sin(angle) * dist;
                double py = getY() + random.nextDouble() * 0.3;
                // 粒子向中心移动
                double vx = (getX() - px) * 0.12;
                double vz = (getZ() - pz) * 0.12;
                double vy = (getY() + 0.15 - py) * 0.12;
                level.addParticle(com.babelmoth.rotp_ata.init.InitParticles.FOSSIL_ASH_GATHER.get(), px, py, pz, vx, vy, vz);
            }
        }
        
        // 淡入效果由 FossilMothRenderer.render() 中的 alpha 参数处理
        
        // Client-side Hamon particles and sound - emit CONTINUOUSLY if moth has Hamon energy
        // This runs BEFORE any early returns so it works in all states
        if (level.isClientSide && getHamonEnergy() > 0) {
            net.minecraft.util.math.vector.Vector3d soundPos = this.getBoundingBox().getCenter();
            com.github.standobyte.jojo.client.sound.HamonSparksLoopSound.playSparkSound(this, soundPos, 1.0F, true);
            
            if (this.tickCount % 5 == 0) {
                com.github.standobyte.jojo.client.particle.custom.CustomParticlesHelper.createHamonSparkParticles(
                    this, this.getRandomX(0.3), this.getY(0.5), this.getRandomZ(0.3), 1);
            }
        }
        
        // 召回逻辑
        if (this.entityData.get(IS_RECALLING)) {
            if (!this.level.isClientSide) {
                LivingEntity owner = getOwner();
                if (owner != null) {
                    this.navigation.moveTo(owner, 1.2);
                    if (this.distanceToSqr(owner) < 2.0) {
                        // Data was already saved in recall() - just remove
                        this.remove();
                    }
                } else {
                    this.remove();
                }
            }
            return;
        }    
        
        // 携带物品返回逻辑：飞蛾携带物品飞回主人身边
        int carriedItemId = this.entityData.get(CARRIED_ITEM_ID);
        if (carriedItemId != -1) {
            Entity carriedItem = level.getEntity(carriedItemId);
            LivingEntity owner = getOwner();
            
            if (carriedItem instanceof ItemEntity && owner != null) {
                ItemEntity itemEntity = (ItemEntity) carriedItem;
                // 让物品实体跟随飞蛾
                if (!itemEntity.removed && !itemEntity.getItem().isEmpty()) {
                    // 物品实体跟随飞蛾移动
                    net.minecraft.util.math.vector.Vector3d mothPos = this.position();
                    net.minecraft.util.math.vector.Vector3d itemPos = itemEntity.position();
                    net.minecraft.util.math.vector.Vector3d offset = new net.minecraft.util.math.vector.Vector3d(0, -0.3, 0); // 物品在飞蛾下方
                    net.minecraft.util.math.vector.Vector3d targetPos = mothPos.add(offset);
                    
                    // 平滑移动物品实体到飞蛾位置
                    double dx = targetPos.x - itemPos.x;
                    double dy = targetPos.y - itemPos.y;
                    double dz = targetPos.z - itemPos.z;
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    
                    if (distance > 0.1) {
                        double speed = 0.3;
                        itemEntity.setDeltaMovement(
                            dx * speed / distance,
                            dy * speed / distance + 0.1, // 轻微向上力
                            dz * speed / distance
                        );
                        itemEntity.setNoPickUpDelay();
                        itemEntity.setOwner(this.getUUID()); // 锁定给飞蛾
                    } else {
                        // 物品已到达飞蛾位置，保持跟随
                        itemEntity.setDeltaMovement(0, 0, 0);
                        itemEntity.setPos(targetPos.x, targetPos.y, targetPos.z);
                    }
                    
                    // 飞蛾飞回主人身边
                    this.navigation.moveTo(owner, 1.2);
                    
                    // 当飞蛾接近主人时，将物品交给主人
                    if (this.distanceToSqr(owner) < 3.0) {
                        giveItemToOwner(itemEntity);
                        this.entityData.set(CARRIED_ITEM_ID, -1);
                    }
                } else {
                    // 物品已消失，清除携带状态
                    this.entityData.set(CARRIED_ITEM_ID, -1);
                }
            } else {
                // 物品实体不存在，清除携带状态
                this.entityData.set(CARRIED_ITEM_ID, -1);
            }
            return;
        }
        
        // Swarming Logic: 生物 → 依附；物品实体 → 捡回主人
        int swarmTargetId = this.entityData.get(SWARM_TARGET_ID);
        if (swarmTargetId != -1) {
             Entity target = level.getEntity(swarmTargetId);
             // 对于 ItemEntity，使用更可靠的检查：!removed 且物品不为空
             boolean targetValid = target != null && !target.removed;
             if (target instanceof ItemEntity) {
                 ItemEntity itemEntity = (ItemEntity) target;
                 targetValid = targetValid && !itemEntity.getItem().isEmpty() 
                     && !itemEntity.getPersistentData().getBoolean(ITEM_RETRIEVED_TAG);
             } else if (target instanceof LivingEntity) {
                 targetValid = targetValid && target.isAlive();
             }
             
             if (targetValid) {
                 this.navigation.moveTo(target, 1.5); // Fast speed
                 if (this.distanceToSqr(target) < 2.0) {
                     this.entityData.set(SWARM_TARGET_ID, -1);
                     if (target instanceof ItemEntity) {
                         // 物品实体：飞蛾携带物品飞回，不依附
                         startCarryingItem((ItemEntity) target);
                     } else {
                         attachToEntity(target);
                     }
                 }
             } else {
                 // Target lost or dead/removed
                 this.entityData.set(SWARM_TARGET_ID, -1);
             }
             return;
        }

        // 护盾/保护模式逻辑
        Entity shieldTarget = getShieldTarget();
        
        // Heartbeat check: If shield target hasn't been refreshed in 5 ticks, clear it.
        // This ensures if the action stops without triggering stoppedHolding, the shield drops.
        // 只在非持久化模式下检查心跳
        if (shieldTarget != null && !isShieldPersistent()) {
            if (level.getGameTime() - lastShieldTick > 5) {
                setShieldTarget(null);
                shieldTarget = null;
            }
        }

        if (shieldTarget != null && shieldTarget.isAlive()) {
             // 简化的护盾逻辑 - 类似依附，使用固定偏移
             // 每只蛾子基于自己的ID计算一个固定的角度偏移
             float angleOffset = (this.getId() % 10) * 36.0f; // 0-360度分布
             double rad = Math.toRadians(angleOffset + (level.getGameTime() * 2) % 360); // 缓慢旋转
             
             double radius = 0.8; // 更近的环绕距离
             double heightOffset = 0.8 + ((this.getId() % 3) * 0.3); // 不同高度层
             
             double tx = shieldTarget.getX() + Math.cos(rad) * radius;
             double tz = shieldTarget.getZ() + Math.sin(rad) * radius;
             double ty = shieldTarget.getY() + heightOffset;
             
             // 直接设置位置而非导航（更精确）
             this.setPos(tx, ty, tz);
             this.setDeltaMovement(0, 0, 0);
             
             // 朝向目标
             double dx = shieldTarget.getX() - tx;
             double dz = shieldTarget.getZ() - tz;
             this.yRot = (float)(Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
             this.yBodyRot = this.yRot;
             this.yHeadRot = this.yRot;
             
             return; // 护盾模式下覆盖普通跟随
        }
        
        if (isAttached()) {
            this.setDeltaMovement(0, 0, 0);
            
            // 强制锁定朝向防止AI漂移
            if (attachedPos != null) {
                // 再次读取依附面以确认朝向
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
                // 检查所依附的方块是否还存在
                if (attachedPos != null && level.getBlockState(attachedPos).isAir()) {
                    detach();
                }
                
                // Hamon absorption: detect HamonBlockChargeEntity at attached position
                if (attachedPos != null) {
                    net.minecraft.util.math.AxisAlignedBB aabb = new net.minecraft.util.math.AxisAlignedBB(attachedPos).inflate(0.5);
                    java.util.List<com.github.standobyte.jojo.entity.HamonBlockChargeEntity> charges = 
                        level.getEntitiesOfClass(com.github.standobyte.jojo.entity.HamonBlockChargeEntity.class, aabb);
                    
                    for (com.github.standobyte.jojo.entity.HamonBlockChargeEntity charge : charges) {
                        // Absorb the Hamon charge - use TOTAL energy for cap
                        int totalEnergy = getTotalEnergy();
                        int maxEnergy = getMaxEnergy();
                        int space = maxEnergy - totalEnergy;
                        
                        if (space > 0) {
                            int absorbAmount = Math.min(space, 10);
                            setHamonEnergy(getHamonEnergy() + absorbAmount);
                            charge.remove();
                        }
                    }
                    
                    // Sendo Overdrive is now handled via damage event in AshesToAshesEventHandler
                }
            }
            
            return; // 依附时不执行飞行逻辑
        }
        
        // 飞行时不再掉落灰烬粒子 (用户要求移除)
        
        // 实体依附状态逻辑
        if (isAttachedToEntity()) {
            this.setDeltaMovement(0, 0, 0);
            Entity target = level.getEntity(this.entityData.get(ATTACHED_ENTITY_ID));
            
            if (target != null && target.isAlive()) {
                // 计算跟随位置 (基于目标朝向旋转偏移量)
                float targetYaw = target instanceof LivingEntity ? ((LivingEntity)target).yBodyRot : target.yRot;
                float offX = this.entityData.get(ATTACHED_OFFSET_X);
                float offY = this.entityData.get(ATTACHED_OFFSET_Y);
                float offZ = this.entityData.get(ATTACHED_OFFSET_Z);
                
                // 旋转偏移向量 (绕Y轴旋转 targetYaw)
                double rad = Math.toRadians(-targetYaw);
                double dx = offX * Math.cos(rad) - offZ * Math.sin(rad);
                double dz = offX * Math.sin(rad) + offZ * Math.cos(rad);
                
                this.setPos(target.getX() + dx, target.getY() + offY, target.getZ() + dz);
                
                // 强制同步朝向，加上一个固定的相对旋转
                this.yRot = targetYaw + this.entityData.get(ATTACHED_ROTATION); 
                this.yBodyRot = this.yRot;
                this.yHeadRot = this.yRot;
                this.xRot = 0; // 暂时水平?
                
                this.clearFire(); 
                this.setRemainingFireTicks(0);
                
                // Vehicle Boost Logic (Boat / Minecart) - Centralized Coordinator
                // Run on BOTH SIDES for responsive boat movement (Client Auth)
                if (target instanceof net.minecraft.entity.item.BoatEntity || 
                    target instanceof net.minecraft.entity.item.minecart.AbstractMinecartEntity) {
                    
                    CompoundNBT data = target.getPersistentData();
                    long lastBoost = data.getLong("RotP_AtA_LastBoostTick");
                    
                    // Only run once per tick per vehicle
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
                                 // Apply boost every 5 ticks
                                 if (tickCount % 5 == 0) { 
                                      float boostPerMoth = 0.15f; 
                                      float totalBoost = Math.min(1.0f, boostPerMoth * boosters.size());
                                      
                                      // Apply boost ADDITIVELY (Push)
                                      net.minecraft.util.math.vector.Vector3d current = target.getDeltaMovement();
                                      net.minecraft.util.math.vector.Vector3d boostVec;
                                      
                                      if (current.lengthSqr() < 0.001) {
                                          boostVec = target.getLookAngle().scale(totalBoost * 0.5); 
                                      } else {
                                          boostVec = current.normalize().scale(totalBoost * 0.5); 
                                      }
                                      
                                      target.setDeltaMovement(current.add(boostVec));
                                      
                                      // Consume energy (Server Only)
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
                
                // Visual Feedback: Kinetic Energy Particles (Enhanced)
                // Disable particles when attached to avoid clutter (User Request)
                if (level.isClientSide && !isAttachedToEntity()) {
                    int energy = getKineticEnergy();
                    if (energy > 0) {
                        float ratio = (energy / (float)getMaxEnergy());
                        // More particles as energy increases
                        float chance = 0.1f + ratio * 0.4f; // 10% to 50% per tick
                        if (random.nextFloat() < chance) {
                             // Amber/gold colored particles
                             level.addParticle(ParticleTypes.FLAME, 
                                 getX() + (random.nextDouble() - 0.5) * 0.3, 
                                 getY() + random.nextDouble() * 0.2 + 0.1, 
                                 getZ() + (random.nextDouble() - 0.5) * 0.3, 
                                 0, 0.02, 0);
                        }
                        // Extra sparkle at high energy
                        if (ratio > 0.5f && random.nextFloat() < ratio * 0.3f) {
                             level.addParticle(ParticleTypes.ENCHANT, 
                                 getX() + (random.nextDouble() - 0.5) * 0.4, 
                                 getY() + random.nextDouble() * 0.3, 
                                 getZ() + (random.nextDouble() - 0.5) * 0.4, 
                                 0, 0.05, 0);
                        }
                    }
                }
                

                
                // Apply Effects logic every 10 ticks
                if (!level.isClientSide && this.tickCount % AshesToAshesConstants.SYNC_INTERVAL_TICKS == 0 && target instanceof LivingEntity) {
                    LivingEntity livingTarget = (LivingEntity) target;
                    LivingEntity owner = getOwner();
                    
                    if (owner != null && target == owner) {
                        // Self-Adhesion: Buff Owner with stamina integration
                        IStandPower.getStandPowerOptional(owner).ifPresent(power -> {
                            float stamina = power.getStamina();
                            float maxStamina = power.getMaxStamina();
                            float staminaRatio = maxStamina > 0 ? stamina / maxStamina : 0;
                            
                            // Consume stamina while self-adhered (0.5 per 10 ticks = 1 per second)
                            if (stamina > 0) {
                                power.consumeStamina(0.5f, true);
                            }
                            
                            // Auto-detach if stamina is depleted
                            if (staminaRatio < 0.1f) {
                                detach();
                                return;
                            }
                            
                            // Only apply buffs if stamina > 50%
                            if (staminaRatio >= 0.5f) {
                                int energy = getKineticEnergy();
                                if (energy > 0) {
                                    // Consume energy (slowly)
                                    if (random.nextInt(10) == 0) {
                                        setKineticEnergy(energy - 1);
                                    }
                                    // Apply Buffs (short duration to refresh)
                                    // Note: Damage boost removed to not affect Momentum Strike
                                    livingTarget.addEffect(new EffectInstance(Effects.MOVEMENT_SPEED, 30, 0, true, false, false));
                                    livingTarget.addEffect(new EffectInstance(Effects.DIG_SPEED, 30, 0, true, false, false));
                                }
                            }
                            // Stamina 10%-50%: No buffs but still attached, still consuming stamina
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
                             // Disable items? (Mining Fatigue high level prevents mining)
                             // Weakness?
                             livingTarget.addEffect(new EffectInstance(Effects.WEAKNESS, 30, 1, true, false));
                             // "Disable item use" is hard without event.
                        }
                        
                        // Absorb Hamon
                        // Absorb Hamon (Disabled until correct API found)
                        /*
                        INonStandPower.getNonStandPowerOptional(livingTarget).ifPresent(power -> {
                             if (power.getType() == ModHamon.HAMON.get() && power.getEnergy() > 0) { // Check specific type?
                                 power.consumeEnergy(1.0f); // Drain
                                 setHamonEnergy(getHamonEnergy() + 1);
                             }
                        });
                        */
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
            
            // 检查 Stand 是否激活
            IStandPower.getStandPowerOptional(owner).ifPresent(power -> {
                if (power.getType() != InitStands.STAND_ASHES_TO_ASHES.getStandType() || !power.isActive()) {
                    this.remove();
                    return;
                }
                
                // 远程操控跟随逻辑
                if (power.getStandManifestation() instanceof StandEntity) {
                    StandEntity standEntity = (StandEntity) power.getStandManifestation();
                    
                    if (standEntity.isRemotePositionFixed()) {
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
                        
                    } else if (standEntity.isManuallyControlled()) {
                        isStaying = false;
                        double navX = standEntity.getX() + (random.nextDouble() - 0.5) * 2;
                        double navY = standEntity.getY() + 1 + random.nextDouble() * 1.5;
                        double navZ = standEntity.getZ() + (random.nextDouble() - 0.5) * 2;
                        this.navigation.moveTo(navX, navY, navZ, 1.2);
                        
                    } else {
                        // 跟随本体模式
                        isStaying = false;
                        double navX = owner.getX() + (random.nextDouble() - 0.5) * 2.5;
                        double navY = owner.getY() + 1 + random.nextDouble() * 1.5;
                        double navZ = owner.getZ() + (random.nextDouble() - 0.5) * 2.5;
                        this.navigation.moveTo(navX, navY, navZ, 0.8);
                    }
                    
                    if (!isStaying) {
                        float maxRange = (float) standEntity.getMaxRange() * 2;
                        // Distance Culling: If too far, return to pool (Reserve)
                        // Using stricter range (64 blocks as per plan) or dynamic based on stand range
                        double cullRange = Math.max(64.0, maxRange + 32.0);
                        
                        if (this.distanceTo(owner) > cullRange) {
                            this.remove(); // Triggers pool.recallMoth -> Data saved in reserve
                        }
                    }
                } else {
                    // 替身未召唤时的逻辑
                    // 如果处于未依附状态，飞回玩家身边并消失 (模拟收回)
                   if (!isAttached() && !isAttachedToEntity()) {
                        double navX = owner.getX();
                        double navY = owner.getY() + 1;
                        double navZ = owner.getZ();
                        this.navigation.moveTo(navX, navY, navZ, 1.0);
                        
                        // 靠近玩家时消失
                        if (this.distanceToSqr(owner) < 4.0) {
                            this.remove(); // Triggers pool.recallMoth
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
        // 免疫掉落伤害 (已移除，以便护盾逻辑能正常处理摔落)
        /*if (source == DamageSource.FALL) {
            return true;
        }*/
        // 免疫窒息伤害
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

    public boolean isVisibleForAll() {
        return false;
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
    
    // ========== GeckoLib IAnimatable ==========
    
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
        // Unregister old position if re-attaching
        if (this.attachedPos != null && this.level instanceof net.minecraft.world.World) {
            com.babelmoth.rotp_ata.util.ProtectedBlockRegistry.unregister((net.minecraft.world.World) this.level, this.attachedPos);
        }
        
        this.attachedPos = pos;
        this.entityData.set(ATTACHED_FACE, (byte) face.get3DDataValue());
        
        // Register new position
        if (this.level instanceof net.minecraft.world.World) {
            com.babelmoth.rotp_ata.util.ProtectedBlockRegistry.register((net.minecraft.world.World) this.level, pos);
        }
        
        // 生成随机偏移 (范围 +/- 0.3, 避免超出方块)
        float offX = (this.random.nextFloat() - 0.5f) * 0.6f;
        float offY = (this.random.nextFloat() - 0.5f) * 0.6f;
        float rot = this.random.nextFloat() * 360.0f;
        
        this.entityData.set(ATTACHED_OFFSET_X, offX);
        this.entityData.set(ATTACHED_OFFSET_Y, offY);
        this.entityData.set(ATTACHED_ROTATION, rot);
        
        // 调整位置贴合方块表面
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
            // 北/南面: X轴和Y轴偏移
            x = pos.getX() + 0.5 + offX;
            y = pos.getY() + 0.5 + offY;
            z = pos.getZ() + 0.5 + face.getStepZ() * 0.55;
        } else {
            // 东/西面: Z轴和Y轴偏移
            x = pos.getX() + 0.5 + face.getStepX() * 0.55;
            y = pos.getY() + 0.5 + offY;
            z = pos.getZ() + 0.5 + offX; // 使用 offX 作为水平偏移 (Z)
        }
        
        this.setPos(x, y, z);
        
        // 调整朝向 (根据 face)
        
        // 调整朝向 (根据 face)
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
        
        // 停止AI和物理运算
        this.setNoAi(true);
    }
    
    public void detach() {
        // Unregister from protected blocks
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
        
        // 计算随机偏移 (在 bounding box 表面)
        //简化模型：圆柱体表面随机点
        float radius = target.getBbWidth() * 0.5f + 0.1f; // 略微浮出
        float height = target.getBbHeight();
        
        float angle = this.random.nextFloat() * 360.0f;
        double rad = Math.toRadians(angle);
        
        float offX = (float) (Math.sin(rad) * radius);
        float offZ = (float) (Math.cos(rad) * radius);
        float offY = this.random.nextFloat() * height;
        
        this.entityData.set(ATTACHED_OFFSET_X, offX);
        this.entityData.set(ATTACHED_OFFSET_Y, offY);
        this.entityData.set(ATTACHED_OFFSET_Z, offZ); // 新增 Z 偏移
        
        // 随机朝向 (相对目标的角度)
        this.entityData.set(ATTACHED_ROTATION, this.random.nextFloat() * 360.0f);

        // 如果附着的是主人，则自动开启护盾保护逻辑
        LivingEntity owner = getOwner();
        if (owner != null && target.is(owner)) {
            this.entityData.set(SHIELD_TARGET_ID, target.getId());
        }
        
        this.setNoAi(true);
    }

    /** 物品实体 NBT 标记：已被飞蛾领取，避免多只飞蛾重复捡起 */
    private static final String ITEM_RETRIEVED_TAG = "ata_retrieved";

    /**
     * 飞蛾开始携带物品：标记物品已被领取，让飞蛾携带物品飞回主人身边
     */
    private void startCarryingItem(ItemEntity itemEntity) {
        if (itemEntity == null || itemEntity.getItem().isEmpty()) return;
        if (itemEntity.getPersistentData().getBoolean(ITEM_RETRIEVED_TAG)) return; // 已被其他飞蛾领取
        LivingEntity owner = getOwner();
        if (owner == null) return;

        // 标记物品已被领取，防止其他飞蛾重复拾取
        itemEntity.getPersistentData().putBoolean(ITEM_RETRIEVED_TAG, true);
        
        // 设置飞蛾携带物品
        this.entityData.set(CARRIED_ITEM_ID, itemEntity.getId());
        
        // 锁定物品实体给飞蛾，防止被其他实体拾取
        itemEntity.setOwner(this.getUUID());
        itemEntity.setNoPickUpDelay();
        
        // 设置物品实体无重力，让它跟随飞蛾
        itemEntity.setNoGravity(true);
    }
    
    /**
     * 将携带的物品交给主人
     */
    private void giveItemToOwner(ItemEntity itemEntity) {
        if (itemEntity == null || itemEntity.getItem().isEmpty()) return;
        LivingEntity owner = getOwner();
        if (owner == null) return;

        ItemStack toGive = itemEntity.getItem().copy();
        itemEntity.remove(); // 移除物品实体

        if (owner instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) owner;
            if (!player.inventory.add(toGive)) {
                // 背包满或无法放入：掉落在主人脚下
                ItemEntity drop = new ItemEntity(level, owner.getX(), owner.getY() + 0.5, owner.getZ(), toGive);
                drop.setNoPickUpDelay();
                level.addFreshEntity(drop);
            }
        } else {
            // 非玩家主人：掉落在主人脚下
            ItemEntity drop = new ItemEntity(level, owner.getX(), owner.getY() + 0.5, owner.getZ(), toGive);
            drop.setNoPickUpDelay();
            level.addFreshEntity(drop);
        }
    }

    @Override
    public void remove(boolean keepData) {
        // 清理携带的物品：如果飞蛾被移除时还携带着物品，将物品掉落在当前位置
        if (!level.isClientSide) {
            int carriedItemId = this.entityData.get(CARRIED_ITEM_ID);
            if (carriedItemId != -1) {
                Entity carriedItem = level.getEntity(carriedItemId);
                if (carriedItem instanceof ItemEntity) {
                    ItemEntity itemEntity = (ItemEntity) carriedItem;
                    if (!itemEntity.removed && !itemEntity.getItem().isEmpty()) {
                        // 恢复重力，让物品自然掉落
                        itemEntity.setNoGravity(false);
                        itemEntity.setOwner((java.util.UUID) null); // 清除锁定
                        // 物品会自然掉落，不需要额外处理
                    }
                }
            }
        }
        
        // --- Moth Pool Cleanup ---
        if (!level.isClientSide && mothPoolIndex != -1) {
            LivingEntity owner = getOwner();
            if (owner != null) {
                 owner.getCapability(com.babelmoth.rotp_ata.capability.MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
                     if (this.isDeadOrDying()) {
                         pool.killMoth(mothPoolIndex);
                     } else if (!keepData) {
                         // Recall / Despawn (keep data in reserve)
                         pool.recallMoth(mothPoolIndex);
                     }
                 });
            }
        }
        // -------------------------

        // Unregister from protected blocks when moth is removed
        if (this.attachedPos != null && this.level instanceof net.minecraft.world.World) {
            com.babelmoth.rotp_ata.util.ProtectedBlockRegistry.unregister((net.minecraft.world.World) this.level, this.attachedPos);
        }
        
        if (this.level.isClientSide) {
            // 如果是正在召回状态下消失，播放消散粒子
            if (this.entityData.get(IS_RECALLING)) {
                spawnDespawnParticles();
            }
        }
        super.remove(keepData);
    }


    // --- Kinetic Detonation Logic ---
    
    public void detonateKinetic() {
        if (this.level.isClientSide) return;
        
        // Check if this moth has Hamon energy for Hamon damage
        boolean useHamon = getHamonEnergy() > 0;
        
        // Use total energy for effect calculation
        float chargeRatio = (float)getTotalEnergy() / (float)getMaxEnergy();
        // Refined: Radius 0.5-2.0, Damage 1-8 (Nerfed for Swarm Balance)
        float radius = 0.5f + 1.5f * chargeRatio;
        float damage = 1.0f + 7.0f * chargeRatio;
        float knockbackStrength = 0.5f + 1.5f * chargeRatio;
        
        // 1. Visual Explosion (No Block Damage)
        this.level.explode(this, this.getX(), this.getY(), this.getZ(), radius, Explosion.Mode.NONE);
        
        // Spawn Fossil Ash Particles (or Hamon sparks if using Hamon)
        if (this.level instanceof net.minecraft.world.server.ServerWorld) {
            net.minecraft.world.server.ServerWorld serverWorld = (net.minecraft.world.server.ServerWorld) this.level;
            int particleCount = 20 + (int)(30 * chargeRatio);
            for (int i = 0; i < particleCount; i++) {
                double px = this.getX() + (this.random.nextDouble() - 0.5) * radius * 2;
                double py = this.getY() + (this.random.nextDouble() - 0.5) * radius * 2;
                double pz = this.getZ() + (this.random.nextDouble() - 0.5) * radius * 2;
                double vx = (this.random.nextDouble() - 0.5) * 0.5;
                double vy = this.random.nextDouble() * 0.3;
                double vz = (this.random.nextDouble() - 0.5) * 0.5;
                if (useHamon) {
                    // Hamon spark particles
                    serverWorld.sendParticles(com.github.standobyte.jojo.init.ModParticles.HAMON_SPARK.get(), px, py, pz, 1, vx, vy, vz, 0.1);
                } else {
                    serverWorld.sendParticles(com.babelmoth.rotp_ata.init.InitParticles.FOSSIL_ASH.get(), px, py, pz, 1, vx, vy, vz, 0.1);
                }
            }
        }
        
        // 2. Heavy Knockback & Damage Application
        List<LivingEntity> targets = this.level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(radius + 1.0));
        LivingEntity owner = getOwner();
        
        for (LivingEntity target : targets) {
            if (target == owner || target == this) continue;
            
            double distSqr = target.distanceToSqr(this);
            if (distSqr > radius * radius) continue;
            
            float distRatio = 1.0f - (float)Math.sqrt(distSqr) / radius;
            if (distRatio < 0) distRatio = 0;
            
            float actualDamage = damage * distRatio;
            
            // Apply damage (always explosion damage first)
            boolean hurt = target.hurt(DamageSource.explosion(this instanceof LivingEntity ? (LivingEntity)this : owner), actualDamage);
            
            // Add small bonus Hamon damage if moth had Hamon energy
            if (hurt && useHamon) {
                com.github.standobyte.jojo.util.mc.damage.DamageUtil.dealHamonDamage(target, 0.5F, this, owner);
            }
            
            if (hurt) {
                net.minecraft.util.math.vector.Vector3d knockbackDir = target.position().subtract(this.position()).normalize();
                
                com.github.standobyte.jojo.util.mc.damage.DamageUtil.knockback(target, knockbackStrength, 
                        (float) -Math.atan2(knockbackDir.x, knockbackDir.z) * (180F / (float)Math.PI));
                        
                com.github.standobyte.jojo.util.mc.damage.KnockbackCollisionImpact.getHandler(target).ifPresent(cap -> cap
                     .onPunchSetKnockbackImpact(knockbackDir.scale(knockbackStrength), owner instanceof LivingEntity ? (LivingEntity)owner : null)
                     .withImpactExplosion(1.2f, null, actualDamage * 0.5f)
                 );
            }
        }
        
        this.remove();
    }
    
    public void detonateExfoliating() {
        if (this.level.isClientSide) return;
        
        ExfoliatingAshCloudEntity cloud = new ExfoliatingAshCloudEntity(this.level, this.getX(), this.getY(), this.getZ());
        cloud.setOwner(getOwner());
        
        // Use total energy for effect calculation
        float chargeRatio = (float)getTotalEnergy() / (float)getMaxEnergy();
        // Refined: Duration 10s-30s
        int duration = 200 + (int)(400 * chargeRatio);
        cloud.setDuration(duration);
        
        // Radius: 3-10 blocks
        float radius = 3.0f + 7.0f * chargeRatio;
        cloud.setRadius(radius);
        
        // Mark cloud as Hamon-infused if moth had Hamon energy
        if (getHamonEnergy() > 0) {
            cloud.setHamonInfused(true);
        }
        
        this.level.addFreshEntity(cloud);
        this.remove();
    }

    // --- Kinetic Sensing Logic ---
    
    private static final int AMBER_COLOR = 0xe7801a;
    private static final double SENSING_RANGE = 16.0;
    
    public void tickKineticSensing() {
        // ROTP pattern: glow color is set on CLIENT only
        if (!this.level.isClientSide) return;
        if (!isKineticSensingEnabled()) return;
        
        // Only apply glow if owner is the client player
        net.minecraft.entity.LivingEntity owner = getOwner();
        if (owner == null || owner != com.github.standobyte.jojo.client.ClientUtil.getClientPlayer()) {
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
        // Check if entity is moving (velocity > threshold)
        net.minecraft.util.math.vector.Vector3d vel = entity.getDeltaMovement();
        double speedSqr = vel.x * vel.x + vel.z * vel.z; // Ignore Y for ground movement
        if (speedSqr > 0.001) return true;
        
        // Check swing time (attacking/interacting) or upward movement (jumping)
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
                    // Sync final state before removal
                    syncToPool();
                    
                    if (!this.isAlive()) {
                        // Combat loss
                        pool.killMoth(mothPoolIndex);
                    } else {
                        // Natural removal (Recall, Distance Culling, Unsummon)
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
