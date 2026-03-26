package com.babelmoth.rotp_ata.entity;

import com.babelmoth.rotp_ata.capability.SpearStuckProvider;
import com.babelmoth.rotp_ata.init.InitItems;
import com.babelmoth.rotp_ata.init.InitEntities;
import com.babelmoth.rotp_ata.util.SpearEnchantHelper;
import com.babelmoth.rotp_ata.networking.AshesToAshesPacketHandler;
import com.babelmoth.rotp_ata.networking.SpearStuckSyncPacket;
import com.github.standobyte.jojo.action.non_stand.HamonOrganismInfusion;
import com.github.standobyte.jojo.entity.damaging.projectile.BlockShardEntity;
import com.github.standobyte.jojo.init.ModEntityTypes;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.particles.BlockParticleData;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.tags.BlockTags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.fml.network.PacketDistributor;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ThelaHunGinjeetSpearEntity extends AbstractArrowEntity implements IEntityAdditionalSpawnData {
    private static final DataParameter<Boolean> DATA_RECALLED = EntityDataManager.defineId(ThelaHunGinjeetSpearEntity.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Integer> DATA_OWNER_ID = EntityDataManager.defineId(ThelaHunGinjeetSpearEntity.class, DataSerializers.INT);
    private static final DataParameter<Boolean> DATA_PENDING_LIVING_BLOCK = EntityDataManager.defineId(ThelaHunGinjeetSpearEntity.class, DataSerializers.BOOLEAN);

    private ItemStack spearItem = ItemStack.EMPTY;
    private int returningTicks = 0;
    private boolean burstMode = false;

    private UUID stuckTargetUUID = null;
    private int stuckTargetId = -1;

    private int recallIgnoreEntityId = -1;

    private int recallGraceTicks = 0;
    private static final double BURST_MAX_TRAVEL_DISTANCE = 28.0;
    private static final int BURST_MAX_LIFETIME_TICKS = 80;
    private Vector3d burstOrigin = null;
    private int burstTicks = 0;
    
    private BlockPos livingBlockHitPos = null;
    private int livingBlockExplosionTicks = 0;

    public ThelaHunGinjeetSpearEntity(EntityType<? extends ThelaHunGinjeetSpearEntity> type, World world) {
        super(type, world);
    }

    public ThelaHunGinjeetSpearEntity(World world, double x, double y, double z) {
        super(InitEntities.THELA_HUN_GINJEET_SPEAR_ENTITY.get(), x, y, z, world);
    }

    public ThelaHunGinjeetSpearEntity(World world, LivingEntity owner, ItemStack spearItem) {
        super(InitEntities.THELA_HUN_GINJEET_SPEAR_ENTITY.get(), owner, world);
        this.spearItem = spearItem == null ? ItemStack.EMPTY : spearItem.copy();
        if (owner != null) {
            this.entityData.set(DATA_OWNER_ID, owner.getId());
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_RECALLED, false);
        this.entityData.define(DATA_OWNER_ID, -1);
        this.entityData.define(DATA_PENDING_LIVING_BLOCK, false);
    }

    public ItemStack getSpearItem() {
        if (spearItem.isEmpty()) {
            spearItem = new ItemStack(InitItems.THELA_HUN_GINJEET_SPEAR.get());
        }
        return spearItem;
    }

    @Override
    protected ItemStack getPickupItem() {
        return getSpearItem().copy();
    }

    public void setRecalled(boolean recalled) {
        if (burstMode) return;
        
        this.entityData.set(DATA_RECALLED, recalled);
        if (recalled) {
            this.returningTicks = 0;
            this.setNoGravity(true);
            this.noPhysics = true;
            this.inGround = false;
            this.setInvisible(false);
            this.pickup = PickupStatus.DISALLOWED;

            this.livingBlockHitPos = null;
            this.livingBlockExplosionTicks = 0;
            this.entityData.set(DATA_PENDING_LIVING_BLOCK, false);

            this.recallIgnoreEntityId = this.stuckTargetId;
            this.recallGraceTicks = 0;

            if (!level.isClientSide && stuckTargetId >= 0) {
                Entity target = level.getEntity(stuckTargetId);
                if (target != null) {

                    this.setPos(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ());
                    if (target instanceof LivingEntity) {
                        LivingEntity livingTarget = (LivingEntity) target;
                        decrementStuckCount(livingTarget);

                        livingTarget.getCapability(com.babelmoth.rotp_ata.capability.SpearThornProvider.SPEAR_THORN_CAPABILITY).ifPresent(cap -> {
                            cap.reset();
                            syncThornData(livingTarget, cap);
                        });
                    }
                }
            }
            this.stuckTargetUUID = null;
            this.stuckTargetId = -1;
        }
    }

    public boolean isRecalled() {
        return this.entityData.get(DATA_RECALLED);
    }

    public int getStuckTargetId() {
        return this.stuckTargetId;
    }

    public void setBurstMode(boolean burst) {
        this.burstMode = burst;
        if (burst) {
            this.burstOrigin = this.position();
            this.burstTicks = 0;
        }
    }

    public boolean isBurstMode() {
        return this.burstMode;
    }

    public boolean hasPendingLivingBlockExplosion() {
        if (level.isClientSide) {
            return !burstMode && this.entityData.get(DATA_PENDING_LIVING_BLOCK);
        }
        return !burstMode && livingBlockHitPos != null;
    }

    public boolean isOwnedBy(Entity entity) {
        if (entity == null) return false;
        if (level.isClientSide) {
            int ownerId = this.entityData.get(DATA_OWNER_ID);
            return ownerId >= 0 && ownerId == entity.getId();
        }
        Entity owner = getOwner();
        return entity.equals(owner);
    }

    public boolean triggerLivingBlockExplosion() {
        if (level.isClientSide || burstMode || livingBlockHitPos == null) {
            return false;
        }
        explodeFromLivingBlock();
        livingBlockHitPos = null;
        livingBlockExplosionTicks = 0;
        this.entityData.set(DATA_PENDING_LIVING_BLOCK, false);
        return true;
    }

    @Override
    public void tick() {
        if (burstMode) {
            if (burstOrigin == null) {
                burstOrigin = this.position();
            }
            burstTicks++;
            if (!level.isClientSide) {
                double maxDistSq = BURST_MAX_TRAVEL_DISTANCE * BURST_MAX_TRAVEL_DISTANCE;
                if (this.position().distanceToSqr(burstOrigin) >= maxDistSq || burstTicks >= BURST_MAX_LIFETIME_TICKS) {
                    remove();
                    return;
                }
            }
        }

        if (livingBlockHitPos != null && !level.isClientSide && !burstMode) {
            livingBlockExplosionTicks--;
            if (livingBlockExplosionTicks <= 0) {
                livingBlockHitPos = null;
                this.entityData.set(DATA_PENDING_LIVING_BLOCK, false);
            }
            if (!isRecalled()) {
                this.baseTick();
                return;
            }
        }
        
        boolean recalled = isRecalled();

        if (recalled) {
            Entity owner = getOwner();
            if (owner == null || !owner.isAlive()) {
                setRecalled(false);
                setNoGravity(false);
                noPhysics = false;
                setDeltaMovement(Vector3d.ZERO);
                return;
            }

            returningTicks++;

            Vector3d ownerPos = owner.getEyePosition(1.0F);
            Vector3d diff = ownerPos.subtract(position());
            double dist = diff.length();

            if (dist < 1.5D) {
                if (!level.isClientSide && owner instanceof PlayerEntity) {
                    PlayerEntity player = (PlayerEntity) owner;
                    ItemStack stack = getSpearItem().copy();
                    if (player.inventory.add(stack) || player.abilities.instabuild) {
                        playSound(SoundEvents.TRIDENT_RETURN, 1.0F, 1.0F);
                        remove();
                        return;
                    }
                }
            }

            double speed = Math.min(dist, 0.5D + returningTicks * 0.05D);
            Vector3d motion = diff.normalize().scale(speed);
            setDeltaMovement(motion);

            this.yRotO = this.yRot;
            this.xRotO = this.xRot;
            this.yRot = (float) (Math.atan2(motion.x, motion.z) * (180.0D / Math.PI));
            this.xRot = (float) (Math.atan2(motion.y, Math.sqrt(motion.x * motion.x + motion.z * motion.z)) * (180.0D / Math.PI));

            Vector3d oldPos = position();
            this.setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);

            if (recallGraceTicks > 0) {
                recallGraceTicks--;
            }

            if (!level.isClientSide && recallGraceTicks <= 0) {
                net.minecraft.util.math.AxisAlignedBB sweepBox = getBoundingBox().expandTowards(motion).inflate(1.0);
                final int ignoreId = this.recallIgnoreEntityId;
                for (Entity candidate : level.getEntities(this, sweepBox,
                        e -> e instanceof LivingEntity && e.isAlive() && !isOwnerOrOwnersStand(e, owner) && !e.isSpectator() && e.getId() != ignoreId)) {

                    double distSq = candidate.distanceToSqr(this);
                    if (distSq > 4.0) continue;

                    LivingEntity hitTarget = (LivingEntity) candidate;

                    LivingEntity tickThornTarget = resolveTrackingTarget(hitTarget);

                    float damage = getStandScaledDamage(owner);

                    damage += SpearEnchantHelper.getTotalBonusDamage(spearItem, hitTarget);
                    SpearEnchantHelper.applyFireAspect(spearItem, hitTarget);

                    float healthBefore = hitTarget.getHealth();
                    hitTarget.hurt(createSpearDamageSource(owner), damage);
                    float actualDmg = Math.max(0, healthBefore - hitTarget.getHealth());
                    level.playSound(null, hitTarget.getX(), hitTarget.getY(), hitTarget.getZ(),
                            SoundEvents.TRIDENT_HIT, net.minecraft.util.SoundCategory.PLAYERS, 1.0F, 1.0F);

                    if (actualDmg >= damage * 0.2F) {

                        setRecalled(false);
                        setNoGravity(true);
                        noPhysics = false;

                        incrementStuckCount(hitTarget);
                        this.stuckTargetUUID = hitTarget.getUUID();
                        this.stuckTargetId = hitTarget.getId();

                        this.setInvisible(true);
                        setDeltaMovement(Vector3d.ZERO);
                        this.setPos(hitTarget.getX(), hitTarget.getY() + hitTarget.getBbHeight() * 0.5, hitTarget.getZ());
                        this.inGround = false;
                        this.pickup = PickupStatus.DISALLOWED;

                        if (tickThornTarget != null) {
                            final float finalDmgRecall = damage;
                            tickThornTarget.getCapability(com.babelmoth.rotp_ata.capability.SpearThornProvider.SPEAR_THORN_CAPABILITY).ifPresent(cap -> {
                                cap.setHasSpear(true);
                                cap.setDamageDealt(0);
                                cap.setDetachThreshold(cap.getThornCount() + finalDmgRecall);
                                syncThornData(tickThornTarget, cap);
                            });
                        }

                        this.baseTick();
                        return;
                    }

                    break;
                }
            }

            if (returningTicks > 200) {
                if (!level.isClientSide && owner instanceof PlayerEntity) {
                    PlayerEntity player = (PlayerEntity) owner;
                    ItemStack stack = getSpearItem().copy();
                    if (!player.inventory.add(stack)) {
                        player.drop(stack, false);
                    }
                }
                remove();
                return;
            }

            this.baseTick();
            return;
        }

        if (isInvisible() && !level.isClientSide) {

            if (stuckTargetId < 0 && stuckTargetUUID != null && level instanceof ServerWorld) {
                Entity found = ((ServerWorld) level).getEntity(stuckTargetUUID);
                if (found != null) {
                    stuckTargetId = found.getId();
                }
            }

            if (stuckTargetId >= 0) {
                Entity target = level.getEntity(stuckTargetId);
                if (target != null && target.isAlive()) {
                    this.setPos(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ());
                    setDeltaMovement(Vector3d.ZERO);

                    Entity owner = getOwner();
                    if (owner != null && this.tickCount % 20 == 0) {
                        AshesToAshesPacketHandler.CHANNEL.send(
                            PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                            new com.babelmoth.rotp_ata.networking.SpearMarkSyncPacket(target.getId(), owner.getId(), 40));
                    }
                } else {

                    if (target instanceof LivingEntity) {
                        decrementStuckCount((LivingEntity) target);
                    }
                    this.stuckTargetUUID = null;
                    this.stuckTargetId = -1;
                    this.setInvisible(false);
                    this.setNoGravity(false);
                }
            }
            this.baseTick();
            return;
        }

        super.tick();
    }

    @Override
    protected void onHitEntity(EntityRayTraceResult result) {

        if (burstMode) {
            Entity hitEntity = result.getEntity();
            if (hitEntity == null) return;
            Entity owner = getOwner();
            
            if (isOwnerOrOwnersStand(hitEntity, owner)) return;
            
            LivingEntity thornTarget = resolveTrackingTarget(hitEntity);
            LivingEntity stickTarget = hitEntity instanceof LivingEntity ? (LivingEntity) hitEntity : thornTarget;
            
            float damage = 8.0F;
            if (hitEntity instanceof LivingEntity) {
                damage += SpearEnchantHelper.getTotalBonusDamage(spearItem, (LivingEntity) hitEntity);
                SpearEnchantHelper.applyFireAspect(spearItem, (LivingEntity) hitEntity);
            }
            
            float healthBefore = hitEntity instanceof LivingEntity ? ((LivingEntity) hitEntity).getHealth() : 0;
            hitEntity.hurt(createSpearDamageSource(owner), damage);
            float actualDmg = hitEntity instanceof LivingEntity 
                    ? Math.max(0, healthBefore - ((LivingEntity) hitEntity).getHealth()) : damage;
            
            boolean shouldStick = actualDmg >= damage * 0.2F;
            if (shouldStick && !level.isClientSide && stickTarget != null) {
                setNoGravity(true);
                noPhysics = false;
                
                incrementStuckCount(stickTarget);
                this.stuckTargetUUID = stickTarget.getUUID();
                this.stuckTargetId = stickTarget.getId();
                
                this.setInvisible(true);
                setDeltaMovement(Vector3d.ZERO);
                this.setPos(hitEntity.getX(), hitEntity.getY() + hitEntity.getBbHeight() * 0.5, hitEntity.getZ());
                this.inGround = false;
                this.pickup = PickupStatus.DISALLOWED;
                
                if (thornTarget != null) {
                    final float finalDmg = damage;
                    thornTarget.getCapability(com.babelmoth.rotp_ata.capability.SpearThornProvider.SPEAR_THORN_CAPABILITY).ifPresent(cap -> {
                        cap.setHasSpear(true);
                        cap.setDamageDealt(0);
                        cap.setDetachThreshold(cap.getThornCount() + finalDmg);
                        syncThornData(thornTarget, cap);
                    });
                }
            } else if (!level.isClientSide) {
                remove();
            }
            return;
        }

        if (isRecalled()) {

            if (recallGraceTicks > 0) return;
            Entity hitEntity = result.getEntity();
            if (hitEntity == null) return;
            Entity owner = getOwner();

            if (isOwnerOrOwnersStand(hitEntity, owner)) return;

            LivingEntity recallThornTarget = resolveTrackingTarget(hitEntity);

            LivingEntity recallStickTarget = hitEntity instanceof LivingEntity ? (LivingEntity) hitEntity : recallThornTarget;

            float damage = getStandScaledDamage(owner);

            if (hitEntity instanceof LivingEntity) {
                damage += SpearEnchantHelper.getTotalBonusDamage(spearItem, (LivingEntity) hitEntity);
                SpearEnchantHelper.applyFireAspect(spearItem, (LivingEntity) hitEntity);
            }

            float healthBefore = hitEntity instanceof LivingEntity ? ((LivingEntity) hitEntity).getHealth() : 0;
            hitEntity.hurt(createSpearDamageSource(owner), damage);
            float actualDmg = hitEntity instanceof LivingEntity
                    ? Math.max(0, healthBefore - ((LivingEntity) hitEntity).getHealth()) : damage;
            playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);

            boolean shouldStick = actualDmg >= damage * 0.2F;
            if (shouldStick && !level.isClientSide) {

                setRecalled(false);
                setNoGravity(true);
                noPhysics = false;

                if (recallStickTarget != null) {
                    incrementStuckCount(recallStickTarget);
                    this.stuckTargetUUID = recallStickTarget.getUUID();
                    this.stuckTargetId = recallStickTarget.getId();
                }

                this.setInvisible(true);
                setDeltaMovement(Vector3d.ZERO);
                this.setPos(hitEntity.getX(), hitEntity.getY() + hitEntity.getBbHeight() * 0.5, hitEntity.getZ());
                this.inGround = false;
                this.pickup = PickupStatus.DISALLOWED;

                if (recallThornTarget != null) {
                    final float finalDmg = damage;
                    recallThornTarget.getCapability(com.babelmoth.rotp_ata.capability.SpearThornProvider.SPEAR_THORN_CAPABILITY).ifPresent(cap -> {
                        cap.setHasSpear(true);
                        cap.setDamageDealt(0);
                        cap.setDetachThreshold(cap.getThornCount() + finalDmg);
                        syncThornData(recallThornTarget, cap);
                    });
                }
            }
            return;
        }

        Entity hitEntity = result.getEntity();
        if (hitEntity == null) return;
        Entity owner = getOwner();

        LivingEntity thornTarget = resolveTrackingTarget(hitEntity);

        LivingEntity stickTarget = hitEntity instanceof LivingEntity ? (LivingEntity) hitEntity : thornTarget;

        float damage = getStandScaledDamage(owner);

        if (hitEntity instanceof LivingEntity) {
            damage += SpearEnchantHelper.getTotalBonusDamage(spearItem, (LivingEntity) hitEntity);
            SpearEnchantHelper.applyFireAspect(spearItem, (LivingEntity) hitEntity);
        }

        float healthBefore = hitEntity instanceof LivingEntity ? ((LivingEntity) hitEntity).getHealth() : 0;
        hitEntity.hurt(createSpearDamageSource(owner), damage);
        float actualDmg = hitEntity instanceof LivingEntity
                ? Math.max(0, healthBefore - ((LivingEntity) hitEntity).getHealth()) : damage;
        playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);

        if (!level.isClientSide && SpearEnchantHelper.getChannelingLevel(spearItem) > 0
                && level instanceof ServerWorld && hitEntity instanceof LivingEntity) {
            if (level.canSeeSky(hitEntity.blockPosition())) {
                net.minecraft.entity.effect.LightningBoltEntity lightning = net.minecraft.entity.EntityType.LIGHTNING_BOLT.create(level);
                if (lightning != null) {
                    lightning.moveTo(hitEntity.getX(), hitEntity.getY(), hitEntity.getZ());
                    lightning.setCause(owner instanceof ServerPlayerEntity ? (ServerPlayerEntity) owner : null);
                    level.addFreshEntity(lightning);
                }
            }
        }

        boolean shouldStick = actualDmg >= damage * 0.2F;
        if (shouldStick && !level.isClientSide) {
            if (stickTarget != null) {
                incrementStuckCount(stickTarget);
                this.stuckTargetUUID = stickTarget.getUUID();
                this.stuckTargetId = stickTarget.getId();
            }

            this.setInvisible(true);
            setDeltaMovement(Vector3d.ZERO);
            this.setPos(hitEntity.getX(), hitEntity.getY() + hitEntity.getBbHeight() * 0.5, hitEntity.getZ());
            this.inGround = false;
            this.pickup = PickupStatus.DISALLOWED;

            if (thornTarget != null) {
                final float finalDmg = damage;
                thornTarget.getCapability(com.babelmoth.rotp_ata.capability.SpearThornProvider.SPEAR_THORN_CAPABILITY).ifPresent(cap -> {
                    cap.setHasSpear(true);
                    cap.setDamageDealt(0);
                    cap.setDetachThreshold(cap.getThornCount() + finalDmg);
                    syncThornData(thornTarget, cap);
                });
            }
        }
    }

    @Override
    protected void onHitBlock(net.minecraft.util.math.BlockRayTraceResult result) {

        if (burstMode) {
            if (!level.isClientSide) {
                remove();
            }
            return;
        }
        
        super.onHitBlock(result);
        
        if (!level.isClientSide) {
            BlockPos blockPos = result.getBlockPos();
            BlockState blockState = level.getBlockState(blockPos);
            
            if (HamonOrganismInfusion.isBlockLiving(blockState)) {
                this.livingBlockHitPos = blockPos;
                this.livingBlockExplosionTicks = 200;
                this.entityData.set(DATA_PENDING_LIVING_BLOCK, true);
                
                level.playSound(null, blockPos, SoundEvents.WOOD_BREAK, SoundCategory.BLOCKS, 1.0F, 0.8F);
            }
        }
    }

    private void incrementStuckCount(LivingEntity target) {
        target.getCapability(SpearStuckProvider.SPEAR_STUCK_CAPABILITY).ifPresent(cap -> {
            cap.increment();
            syncStuckCount(target, cap.getSpearCount());
        });
    }

    private void decrementStuckCount(LivingEntity target) {
        target.getCapability(SpearStuckProvider.SPEAR_STUCK_CAPABILITY).ifPresent(cap -> {
            cap.decrement();
            syncStuckCount(target, cap.getSpearCount());
        });
    }

    private void syncStuckCount(LivingEntity target, int count) {
        if (!level.isClientSide) {
            AshesToAshesPacketHandler.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                    new SpearStuckSyncPacket(target.getId(), count));
        }
    }

    private void syncThornData(LivingEntity target, com.babelmoth.rotp_ata.capability.ISpearThorn cap) {
        if (!level.isClientSide) {
            AshesToAshesPacketHandler.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                    new com.babelmoth.rotp_ata.networking.SpearThornSyncPacket(
                            target.getId(), cap.getThornCount(), cap.getDamageDealt(),
                            cap.getDetachThreshold(), cap.hasSpear()));
        }
    }
    
    private void explodeFromLivingBlock() {
        if (livingBlockHitPos == null || level.isClientSide) return;
        
        ServerWorld serverWorld = (ServerWorld) level;
        Vector3d explosionPos = Vector3d.atCenterOf(livingBlockHitPos);
        BlockState blockState = level.getBlockState(livingBlockHitPos);
        Block block = blockState.getBlock();
        
        serverWorld.sendParticles(ParticleTypes.EXPLOSION, 
                explosionPos.x, explosionPos.y, explosionPos.z, 
                3, 0.5, 0.5, 0.5, 0.0);
        
        level.playSound(null, livingBlockHitPos, SoundEvents.GENERIC_EXPLODE, SoundCategory.BLOCKS, 1.0F, 1.0F);
        
        Entity owner = getOwner();
        List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(LivingEntity.class, 
                new AxisAlignedBB(livingBlockHitPos).inflate(8.0), 
                entity -> entity.isAlive() && entity != owner && !isOwnerOrOwnersStand(entity, owner))
                .stream()
                .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(explosionPos)))
                .collect(Collectors.toList());
        
        int spearsToShoot = 5;
        boolean isLog = BlockTags.LOGS.contains(block);
        boolean isUnstrippedLog = isLog && getStrippedLog(block) != null;
        boolean isStrippedLog = isLog && getStrippedLog(block) == null;
        boolean isBamboo = block == Blocks.BAMBOO || block == Blocks.BAMBOO_SAPLING;
        
        if (isLog || isBamboo) {
            spearsToShoot = 7;
            
            for (int i = 0; i < 5 + level.random.nextInt(6); i++) {
                double angle = level.random.nextDouble() * Math.PI * 2.0;
                double pitch = (level.random.nextDouble() - 0.25) * 0.8;
                Vector3d shardDir = new Vector3d(
                        Math.cos(angle) * Math.cos(pitch),
                        Math.sin(pitch),
                        Math.sin(angle) * Math.cos(pitch)
                ).normalize();
                double launchSpeed = 0.45 + level.random.nextDouble() * 0.35;
                
                BlockShardEntity shard = new BlockShardEntity(owner instanceof LivingEntity ? (LivingEntity) owner : null, 
                        level, blockState, livingBlockHitPos);
                Vector3d spawnPos = explosionPos.add(shardDir.scale(0.65));
                shard.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
                shard.setDeltaMovement(shardDir.scale(launchSpeed));
                shard.noPhysics = true;
                level.addFreshEntity(shard);
            }
        }
        
        if (block == Blocks.GRASS_BLOCK) {
            for (int i = 0; i < 20; i++) {
                serverWorld.sendParticles(new BlockParticleData(ParticleTypes.BLOCK, Blocks.GRASS_BLOCK.defaultBlockState()),
                        explosionPos.x, explosionPos.y, explosionPos.z,
                        1, 0.8, 0.5, 0.8, 0.2);
                serverWorld.sendParticles(new BlockParticleData(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                        explosionPos.x, explosionPos.y, explosionPos.z,
                        1, 0.8, 0.5, 0.8, 0.2);
            }
            
            for (LivingEntity entity : nearbyEntities) {
                if (entity.distanceToSqr(explosionPos) <= 16.0) {
                    entity.addEffect(new EffectInstance(Effects.BLINDNESS, 60, 0));
                }
            }
        }
        
        boolean isCactus = block == Blocks.CACTUS;
        boolean isMelon = block == Blocks.MELON;
        boolean isPumpkin = block == Blocks.PUMPKIN || block == Blocks.CARVED_PUMPKIN;
        
        if (isCactus || isMelon || isPumpkin) {
            float pierceDamage = isCactus ? 4.0F : 2.0F;
            for (LivingEntity entity : nearbyEntities) {
                if (entity.distanceToSqr(explosionPos) <= 25.0) {
                    entity.hurt(DamageSource.CACTUS, pierceDamage);
                }
            }
        }
        
        for (int i = 0; i < spearsToShoot; i++) {
            double angle = (Math.PI * 2.0 / spearsToShoot) * i + level.random.nextDouble() * 0.5;
            double pitch = (level.random.nextDouble() - 0.5) * 0.5;
            Vector3d direction = new Vector3d(
                Math.cos(angle) * Math.cos(pitch),
                Math.sin(pitch),
                Math.sin(angle) * Math.cos(pitch)
            ).normalize();
            
            ThelaHunGinjeetSpearEntity burstSpear = new ThelaHunGinjeetSpearEntity(level, explosionPos.x, explosionPos.y, explosionPos.z);
            burstSpear.setOwner(owner);
            burstSpear.setBurstMode(true);
            burstSpear.pickup = PickupStatus.DISALLOWED;
            burstSpear.noPhysics = true;
            burstSpear.shoot(direction.x, direction.y, direction.z, (float)(1.5 + level.random.nextDouble()), 5.0F);
            level.addFreshEntity(burstSpear);
        }
        
        if (block == Blocks.GRASS_BLOCK) {
            level.setBlock(livingBlockHitPos, Blocks.DIRT.defaultBlockState(), 3);
        } else if (isUnstrippedLog) {
            Block strippedLog = getStrippedLog(block);
            if (strippedLog != null) {
                level.setBlock(livingBlockHitPos, strippedLog.defaultBlockState(), 3);
            }
        } else if (isBamboo || isStrippedLog || block == Blocks.LILY_PAD || blockState.getMaterial() == Material.LEAVES) {
            level.destroyBlock(livingBlockHitPos, false);
        }
        
        if (!burstMode) {
            setRecalled(true);
        } else {
            remove();
        }
    }
    
    private Block getStrippedLog(Block log) {
        if (log == Blocks.OAK_LOG) return Blocks.STRIPPED_OAK_LOG;
        if (log == Blocks.SPRUCE_LOG) return Blocks.STRIPPED_SPRUCE_LOG;
        if (log == Blocks.BIRCH_LOG) return Blocks.STRIPPED_BIRCH_LOG;
        if (log == Blocks.JUNGLE_LOG) return Blocks.STRIPPED_JUNGLE_LOG;
        if (log == Blocks.ACACIA_LOG) return Blocks.STRIPPED_ACACIA_LOG;
        if (log == Blocks.DARK_OAK_LOG) return Blocks.STRIPPED_DARK_OAK_LOG;
        if (log == Blocks.CRIMSON_STEM) return Blocks.STRIPPED_CRIMSON_STEM;
        if (log == Blocks.WARPED_STEM) return Blocks.STRIPPED_WARPED_STEM;
        if (log == Blocks.OAK_WOOD) return Blocks.STRIPPED_OAK_WOOD;
        if (log == Blocks.SPRUCE_WOOD) return Blocks.STRIPPED_SPRUCE_WOOD;
        if (log == Blocks.BIRCH_WOOD) return Blocks.STRIPPED_BIRCH_WOOD;
        if (log == Blocks.JUNGLE_WOOD) return Blocks.STRIPPED_JUNGLE_WOOD;
        if (log == Blocks.ACACIA_WOOD) return Blocks.STRIPPED_ACACIA_WOOD;
        if (log == Blocks.DARK_OAK_WOOD) return Blocks.STRIPPED_DARK_OAK_WOOD;
        if (log == Blocks.CRIMSON_HYPHAE) return Blocks.STRIPPED_CRIMSON_HYPHAE;
        if (log == Blocks.WARPED_HYPHAE) return Blocks.STRIPPED_WARPED_HYPHAE;
        return null;
    }

    private LivingEntity resolveTrackingTarget(Entity hitEntity) {
        if (hitEntity instanceof com.github.standobyte.jojo.entity.stand.StandEntity) {
            LivingEntity user = ((com.github.standobyte.jojo.entity.stand.StandEntity) hitEntity).getUser();
            if (user != null) return user;
        }
        return hitEntity instanceof LivingEntity ? (LivingEntity) hitEntity : null;
    }

    private boolean isOwnerOrOwnersStand(Entity entity, Entity owner) {
        if (entity == null || owner == null) return false;
        if (entity == owner) return true;

        if (entity instanceof com.github.standobyte.jojo.entity.stand.StandEntity) {
            com.github.standobyte.jojo.entity.stand.StandEntity stand = (com.github.standobyte.jojo.entity.stand.StandEntity) entity;
            return stand.getUser() == owner;
        }
        return false;
    }

    private float getStandScaledDamage(Entity owner) {
        if (owner instanceof LivingEntity) {
            return com.github.standobyte.jojo.power.impl.stand.IStandPower.getStandPowerOptional((LivingEntity) owner)
                    .map(power -> {
                        float dmg = 8.0F;
                        com.github.standobyte.jojo.power.impl.stand.IStandManifestation m = power.getStandManifestation();
                        if (m instanceof com.github.standobyte.jojo.entity.stand.StandEntity) {
                            dmg = (float) ((com.github.standobyte.jojo.entity.stand.StandEntity) m).getAttackDamage();
                        }
                        if (power.getResolveLevel() > 0) {
                            dmg += 2.0F;
                        }
                        return dmg;
                    }).orElse(8.0F);
        }
        return 8.0F;
    }

    private DamageSource createSpearDamageSource(Entity owner) {
        Entity attacker = owner == null ? this : owner;
        return new DamageSource("stand.spear") {
            @Override
            public Entity getEntity() {
                return attacker;
            }
            @Override
            public Entity getDirectEntity() {
                return ThelaHunGinjeetSpearEntity.this;
            }
        };
    }

    @Override
    protected net.minecraft.util.SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!super.canHitEntity(entity)) return false;
        Entity owner = getOwner();
        if (owner != null && isOwnerOrOwnersStand(entity, owner)) return false;
        return true;
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
    }

    private static java.util.function.IntPredicate localPlayerOwnerCheck;
    public static void setLocalPlayerOwnerCheck(java.util.function.IntPredicate check) { localPlayerOwnerCheck = check; }

    @Override
    public boolean isGlowing() {

        if (!burstMode) {
            if (level.isClientSide && localPlayerOwnerCheck != null) {
                int ownerId = this.entityData.get(DATA_OWNER_ID);
                return ownerId >= 0 && localPlayerOwnerCheck.test(ownerId);
            }
            return true;
        }
        return super.isGlowing();
    }

    @Override
    public int getTeamColor() {

        return 0x8B00FF;
    }

    @Override
    public void playerTouch(PlayerEntity player) {

        if (isRecalled() || stuckTargetId >= 0) {
            return;
        }

        Entity owner = getOwner();
        if (owner != null && !player.equals(owner)) {
            return;
        }
        super.playerTouch(player);
    }

    @Override
    public void remove() {

        if (!level.isClientSide && isInvisible() && stuckTargetId >= 0) {
            Entity target = level.getEntity(stuckTargetId);
            if (target instanceof LivingEntity) {
                decrementStuckCount((LivingEntity) target);
            }
        }
        super.remove();
    }

    @Override
    public IPacket<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(PacketBuffer buffer) {
        buffer.writeItem(spearItem);
    }

    @Override
    public void readSpawnData(PacketBuffer additionalData) {
        spearItem = additionalData.readItem();
    }

    @Override
    public void addAdditionalSaveData(CompoundNBT nbt) {
        super.addAdditionalSaveData(nbt);
        if (!spearItem.isEmpty()) {
            nbt.put("SpearItem", spearItem.save(new CompoundNBT()));
        }
        nbt.putBoolean("Recalled", isRecalled());
        nbt.putBoolean("BurstMode", burstMode);
        nbt.putInt("BurstTicks", burstTicks);
        if (burstOrigin != null) {
            nbt.putDouble("BurstOriginX", burstOrigin.x);
            nbt.putDouble("BurstOriginY", burstOrigin.y);
            nbt.putDouble("BurstOriginZ", burstOrigin.z);
        }
        if (stuckTargetUUID != null) {
            nbt.putUUID("StuckTargetUUID", stuckTargetUUID);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundNBT nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("SpearItem")) {
            spearItem = ItemStack.of(nbt.getCompound("SpearItem"));
        }
        burstMode = nbt.getBoolean("BurstMode");
        burstTicks = nbt.getInt("BurstTicks");
        if (nbt.contains("BurstOriginX") && nbt.contains("BurstOriginY") && nbt.contains("BurstOriginZ")) {
            burstOrigin = new Vector3d(nbt.getDouble("BurstOriginX"), nbt.getDouble("BurstOriginY"), nbt.getDouble("BurstOriginZ"));
        }
        if (nbt.getBoolean("Recalled")) {
            setRecalled(true);
        }
        if (nbt.hasUUID("StuckTargetUUID")) {
            stuckTargetUUID = nbt.getUUID("StuckTargetUUID");

            stuckTargetId = -1;
            setNoGravity(true);
            setInvisible(true);
        }

        Entity owner = getOwner();
        if (owner != null) {
            this.entityData.set(DATA_OWNER_ID, owner.getId());
        }
    }
}
