package com.babelmoth.rotp_ata.entity;

import com.babelmoth.rotp_ata.capability.SpearStuckProvider;
import com.babelmoth.rotp_ata.init.InitItems;
import com.babelmoth.rotp_ata.init.InitEntities;
import com.babelmoth.rotp_ata.util.SpearEnchantHelper;
import com.babelmoth.rotp_ata.networking.AshesToAshesPacketHandler;
import com.babelmoth.rotp_ata.networking.SpearStuckSyncPacket;

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
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.fml.network.PacketDistributor;

import java.util.UUID;

public class ThelaHunGinjeetSpearEntity extends AbstractArrowEntity implements IEntityAdditionalSpawnData {
    private static final DataParameter<Boolean> DATA_RECALLED = EntityDataManager.defineId(ThelaHunGinjeetSpearEntity.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Integer> DATA_OWNER_ID = EntityDataManager.defineId(ThelaHunGinjeetSpearEntity.class, DataSerializers.INT);

    private ItemStack spearItem = ItemStack.EMPTY;
    private int returningTicks = 0;
    private boolean burstMode = false;

    private UUID stuckTargetUUID = null;
    private int stuckTargetId = -1;

    private int recallIgnoreEntityId = -1;

    private int recallGraceTicks = 0;

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
        this.entityData.set(DATA_RECALLED, recalled);
        if (recalled) {
            this.returningTicks = 0;
            this.setNoGravity(true);
            this.noPhysics = true;
            this.inGround = false;
            this.setInvisible(false);
            this.pickup = PickupStatus.DISALLOWED;

            this.recallIgnoreEntityId = this.stuckTargetId;
            this.recallGraceTicks = 30;

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
    }

    public boolean isBurstMode() {
        return this.burstMode;
    }

    @Override
    public void tick() {
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
            if (hitEntity != null && !level.isClientSide) {
                float damage = 8.0F;
                hitEntity.hurt(createSpearDamageSource(null), damage);
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
                        com.github.standobyte.jojo.power.impl.stand.IStandManifestation m = power.getStandManifestation();
                        if (m instanceof com.github.standobyte.jojo.entity.stand.StandEntity) {
                            return (float) ((com.github.standobyte.jojo.entity.stand.StandEntity) m).getAttackDamage();
                        }
                        return 8.0F;
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

    @Override
    public boolean isGlowing() {

        if (!burstMode) {
            if (level.isClientSide) {
                return isLocalPlayerOwner();
            }
            return true;
        }
        return super.isGlowing();
    }

    private boolean isLocalPlayerOwner() {
        net.minecraft.entity.player.PlayerEntity localPlayer = net.minecraft.client.Minecraft.getInstance().player;
        if (localPlayer == null) return false;
        int ownerId = this.entityData.get(DATA_OWNER_ID);
        return ownerId >= 0 && ownerId == localPlayer.getId();
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
