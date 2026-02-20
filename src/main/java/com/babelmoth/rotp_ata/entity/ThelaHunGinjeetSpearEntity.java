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

    private ItemStack spearItem = ItemStack.EMPTY;
    private int returningTicks = 0;
    private boolean burstMode = false; // burst spears disappear on hit, no sticking

    // 插入目标信息（服务端）
    private UUID stuckTargetUUID = null;
    private int stuckTargetId = -1;
    // 召回时忽略的实体ID（防止从目标身上召回后立即重新命中同一目标）
    private int recallIgnoreEntityId = -1;

    public ThelaHunGinjeetSpearEntity(EntityType<? extends ThelaHunGinjeetSpearEntity> type, World world) {
        super(type, world);
    }

    public ThelaHunGinjeetSpearEntity(World world, double x, double y, double z) {
        super(InitEntities.THELA_HUN_GINJEET_SPEAR_ENTITY.get(), x, y, z, world);
    }

    public ThelaHunGinjeetSpearEntity(World world, LivingEntity owner, ItemStack spearItem) {
        super(InitEntities.THELA_HUN_GINJEET_SPEAR_ENTITY.get(), owner, world);
        this.spearItem = spearItem == null ? ItemStack.EMPTY : spearItem.copy();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_RECALLED, false);
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

            // 记录原始目标，召回飞行中跳过该实体
            this.recallIgnoreEntityId = this.stuckTargetId;
            // 减少目标的 stuck count，清除荆棘数据，并将长矛移到目标当前位置
            if (!level.isClientSide && stuckTargetId >= 0) {
                Entity target = level.getEntity(stuckTargetId);
                if (target != null) {
                    // 将长矛移到目标当前位置（飞回动画从目标身上开始）
                    this.setPos(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ());
                    if (target instanceof LivingEntity) {
                        LivingEntity livingTarget = (LivingEntity) target;
                        decrementStuckCount(livingTarget);
                        // 清除荆棘数据
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

        // 飞回逻辑
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

            // 加速飞向玩家
            double speed = Math.min(dist, 0.5D + returningTicks * 0.05D);
            Vector3d motion = diff.normalize().scale(speed);
            setDeltaMovement(motion);

            this.yRotO = this.yRot;
            this.xRotO = this.xRot;
            this.yRot = (float) (Math.atan2(motion.x, motion.z) * (180.0D / Math.PI));
            this.xRot = (float) (Math.atan2(motion.y, Math.sqrt(motion.x * motion.x + motion.z * motion.z)) * (180.0D / Math.PI));

            // 手动移动（noPhysics=true 穿过方块）
            Vector3d oldPos = position();
            this.setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);

            // 手动检测飞回路径上的实体碰撞（noPhysics=true 跳过了 super.tick 的碰撞）
            if (!level.isClientSide) {
                net.minecraft.util.math.AxisAlignedBB sweepBox = getBoundingBox().expandTowards(motion).inflate(1.0);
                final int ignoreId = this.recallIgnoreEntityId;
                for (Entity candidate : level.getEntities(this, sweepBox,
                        e -> e instanceof LivingEntity && e.isAlive() && !isOwnerOrOwnersStand(e, owner) && !e.isSpectator() && e.getId() != ignoreId)) {
                    // 确认候选目标在飞行路径附近
                    double distSq = candidate.distanceToSqr(this);
                    if (distSq > 4.0) continue;

                    LivingEntity hitTarget = (LivingEntity) candidate;
                    // 解析追踪目标：替身实体→其主人
                    LivingEntity tickTrackTarget = resolveTrackingTarget(hitTarget);

                    // 停止飞回，插入目标
                    setRecalled(false);
                    setNoGravity(true);
                    noPhysics = false;

                    if (tickTrackTarget != null) {
                        incrementStuckCount(tickTrackTarget);
                        this.stuckTargetUUID = tickTrackTarget.getUUID();
                        this.stuckTargetId = tickTrackTarget.getId();
                    }

                    this.setInvisible(true);
                    setDeltaMovement(Vector3d.ZERO);
                    this.setPos(hitTarget.getX(), hitTarget.getY() + hitTarget.getBbHeight() * 0.5, hitTarget.getZ());
                    this.inGround = false;
                    this.pickup = PickupStatus.DISALLOWED;

                    float damage = getStandScaledDamage(owner);
                    // 附魔加成
                    damage += SpearEnchantHelper.getTotalBonusDamage(spearItem, hitTarget);
                    SpearEnchantHelper.applyFireAspect(spearItem, hitTarget);
                    hitTarget.hurt(createSpearDamageSource(owner), damage);
                    level.playSound(null, hitTarget.getX(), hitTarget.getY(), hitTarget.getZ(),
                            SoundEvents.TRIDENT_HIT, net.minecraft.util.SoundCategory.PLAYERS, 1.0F, 1.0F);

                    // 初始化荆棘系统（基于追踪目标，非替身实体）
                    if (tickTrackTarget != null) {
                        final float finalDmgRecall = damage;
                        tickTrackTarget.getCapability(com.babelmoth.rotp_ata.capability.SpearThornProvider.SPEAR_THORN_CAPABILITY).ifPresent(cap -> {
                            cap.setHasSpear(true);
                            cap.setDamageDealt(0);
                            cap.setDetachThreshold(cap.getThornCount() + finalDmgRecall);
                            syncThornData(tickTrackTarget, cap);
                        });
                    }

                    this.baseTick();
                    return;
                }
            }

            // 超时自动回收
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

        // 不可见（插在目标身上）时跟随目标移动
        if (isInvisible() && !level.isClientSide) {
            // 用 UUID 重新解析 stuckTargetId（退出重进后 entity ID 会变）
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
                    // 每 20 tick 刷新紫色发光标记（仅长矛主人可见）
                    Entity owner = getOwner();
                    if (owner != null && this.tickCount % 20 == 0) {
                        AshesToAshesPacketHandler.CHANNEL.send(
                            PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                            new com.babelmoth.rotp_ata.networking.SpearMarkSyncPacket(target.getId(), owner.getId(), 40));
                    }
                } else {
                    // 目标死亡或消失，减少 stuck count 并移除长矛
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
        // 爆裂模式：命中实体后造成伤害再消失
        if (burstMode) {
            Entity hitEntity = result.getEntity();
            if (hitEntity != null && !level.isClientSide) {
                float damage = 8.0F; // burst spears deal base damage
                hitEntity.hurt(createSpearDamageSource(null), damage);
                remove();
            }
            return;
        }

        // 飞回时命中实体：造成伤害并触发插入效果（与正常投掷相同）
        if (isRecalled()) {
            Entity hitEntity = result.getEntity();
            if (hitEntity == null) return;
            Entity owner = getOwner();
            // 收回时不攻击长矛主人及其替身
            if (isOwnerOrOwnersStand(hitEntity, owner)) return;

            // 解析追踪目标：替身实体→其主人
            LivingEntity recallTrackTarget = resolveTrackingTarget(hitEntity);

            if (!level.isClientSide) {
                // 停止飞回，插入目标
                setRecalled(false);
                setNoGravity(true);
                noPhysics = false;

                if (recallTrackTarget != null) {
                    incrementStuckCount(recallTrackTarget);
                    this.stuckTargetUUID = recallTrackTarget.getUUID();
                    this.stuckTargetId = recallTrackTarget.getId();
                }

                this.setInvisible(true);
                setDeltaMovement(Vector3d.ZERO);
                this.setPos(hitEntity.getX(), hitEntity.getY() + hitEntity.getBbHeight() * 0.5, hitEntity.getZ());
                this.inGround = false;
                this.pickup = PickupStatus.DISALLOWED;
            }

            float damage = getStandScaledDamage(owner);
            // 附魔加成
            if (hitEntity instanceof LivingEntity) {
                damage += SpearEnchantHelper.getTotalBonusDamage(spearItem, (LivingEntity) hitEntity);
                SpearEnchantHelper.applyFireAspect(spearItem, (LivingEntity) hitEntity);
            }
            hitEntity.hurt(createSpearDamageSource(owner), damage);
            playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);

            // 初始化荆棘系统（基于追踪目标，非替身实体）
            if (!level.isClientSide && recallTrackTarget != null) {
                final float finalDmg = damage;
                recallTrackTarget.getCapability(com.babelmoth.rotp_ata.capability.SpearThornProvider.SPEAR_THORN_CAPABILITY).ifPresent(cap -> {
                    cap.setHasSpear(true);
                    cap.setDamageDealt(0);
                    cap.setDetachThreshold(cap.getThornCount() + finalDmg);
                    syncThornData(recallTrackTarget, cap);
                });
            }
            return;
        }

        // 正常投掷命中
        Entity hitEntity = result.getEntity();
        if (hitEntity == null) return;
        Entity owner = getOwner();

        // 解析追踪目标：替身实体→其主人，避免替身血量同步导致的循环bug
        LivingEntity trackingTarget = resolveTrackingTarget(hitEntity);

        if (!level.isClientSide) {
            // 先处理插入逻辑（在 hurt 之前，防止目标死亡导致跳过）
            if (trackingTarget != null) {
                incrementStuckCount(trackingTarget);
                this.stuckTargetUUID = trackingTarget.getUUID();
                this.stuckTargetId = trackingTarget.getId();
            }

            // 长矛变不可见（视觉效果由 SpearStuckLayer 负责）
            this.setInvisible(true);
            setDeltaMovement(Vector3d.ZERO);
            this.setPos(hitEntity.getX(), hitEntity.getY() + hitEntity.getBbHeight() * 0.5, hitEntity.getZ());
            this.inGround = false;
            this.pickup = PickupStatus.DISALLOWED;
        }

        float damage = getStandScaledDamage(owner);
        // 附魔加成
        if (hitEntity instanceof LivingEntity) {
            damage += SpearEnchantHelper.getTotalBonusDamage(spearItem, (LivingEntity) hitEntity);
            SpearEnchantHelper.applyFireAspect(spearItem, (LivingEntity) hitEntity);
        }
        hitEntity.hurt(createSpearDamageSource(owner), damage);
        playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);

        // 引雷附魔：命中时召唤闪电
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

        // 初始化荆棘系统（基于追踪目标，非替身实体）
        if (!level.isClientSide && trackingTarget != null) {
            final float finalDmg = damage;
            trackingTarget.getCapability(com.babelmoth.rotp_ata.capability.SpearThornProvider.SPEAR_THORN_CAPABILITY).ifPresent(cap -> {
                cap.setHasSpear(true);
                cap.setDamageDealt(0);
                cap.setDetachThreshold(cap.getThornCount() + finalDmg);
                syncThornData(trackingTarget, cap);
            });
        }
    }

    @Override
    protected void onHitBlock(net.minecraft.util.math.BlockRayTraceResult result) {
        // 爆裂模式：命中方块后直接消失
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

    /**
     * 如果命中的是替身实体，返回其主人用于stuck/thorn追踪，避免替身血量同步导致的循环bug
     */
    private LivingEntity resolveTrackingTarget(Entity hitEntity) {
        if (hitEntity instanceof com.github.standobyte.jojo.entity.stand.StandEntity) {
            LivingEntity user = ((com.github.standobyte.jojo.entity.stand.StandEntity) hitEntity).getUser();
            if (user != null) return user;
        }
        return hitEntity instanceof LivingEntity ? (LivingEntity) hitEntity : null;
    }

    /**
     * 检查实体是否为长矛主人或其替身实体
     */
    private boolean isOwnerOrOwnersStand(Entity entity, Entity owner) {
        if (entity == null || owner == null) return false;
        if (entity == owner) return true;
        // 过滤主人的替身实体
        if (entity instanceof com.github.standobyte.jojo.entity.stand.StandEntity) {
            com.github.standobyte.jojo.entity.stand.StandEntity stand = (com.github.standobyte.jojo.entity.stand.StandEntity) entity;
            return stand.getUser() == owner;
        }
        return false;
    }

    /**
     * Get spear damage scaled by stand attack power. Base power 8.0 = 8.0 damage.
     */
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

    /**
     * 创建长矛伤害源（msgId 包含 "stand"，使其能伤害替身实体 = 替身触觉）
     */
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
        // Non-burst spears always glow (only visible to stand users via shouldRender)
        if (!burstMode) {
            return true;
        }
        return super.isGlowing();
    }

    @Override
    public int getTeamColor() {
        // Purple glow matching the spear mark color
        return 0x8B00FF;
    }

    @Override
    public void playerTouch(PlayerEntity player) {
        // 召回中或插入敌人时禁止vanilla拾取机制
        if (isRecalled() || stuckTargetId >= 0) {
            return;
        }
        // Only the owner can pick up this spear
        Entity owner = getOwner();
        if (owner != null && !player.equals(owner)) {
            return;
        }
        super.playerTouch(player);
    }

    @Override
    public void remove() {
        // 实体被移除时（非召回），减少目标的 stuck count
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
            // entity ID 在重新加载后会变，设为 -1 让 tick 中用 UUID 重新解析
            stuckTargetId = -1;
            setNoGravity(true);
            setInvisible(true);
        }
    }
}
