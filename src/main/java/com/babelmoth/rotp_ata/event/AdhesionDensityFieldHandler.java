package com.babelmoth.rotp_ata.event;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.util.mc.MCUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID)
public class AdhesionDensityFieldHandler {
    private static final UUID ADHESION_FIELD_SPEED_UUID = UUID.fromString("5fa3408e-2b23-4c14-a7aa-b255cc2c9d11");
    private static final UUID ADHESION_FIELD_DAMAGE_UUID = UUID.fromString("34c73771-83c4-4d60-b812-01d2fe7494e2");
    private static final UUID ADHESION_FIELD_ATTACK_SPEED_UUID = UUID.fromString("f74f66aa-a8c9-4ef6-95c1-72d1305128ec");
    private static final String ADHESION_JUMP_PENALTY_TAG = "AtaAdhesionJumpPenalty";
    private static final String PROJECTILE_IMMOBILIZED_TAG = "ata_moth_immobilized";
    private static final String PROJECTILE_FREEZE_TICKS_TAG = "AtaMothFreezeTicks";
    private static final String PROJECTILE_DROP_TAG = "ata_moth_projectile_drop";
    private static final double ADHESION_FIELD_RADIUS = 4.0D;
    private static final double FLYING_FIELD_RADIUS = 2.5D;
    private static final int ENERGY_ABSORB_INTERVAL = 10;
    private static final int PROJECTILE_STOP_THRESHOLD = 4;
    private static final int PROJECTILE_FREEZE_TICKS = 16;
    private static final int RESISTANCE_STOP_THRESHOLD = 5;
    private static final int SURROUNDING_SHIELD_THRESHOLD = 6;
    private static final double SURROUNDING_SHIELD_RADIUS = 2.1D;
    private static final double PROJECTILE_SCAN_RADIUS = 5.0D;
    private static final double PROJECTILE_IMMOBILIZE_DISTANCE = 2.1D;
    private static final double PROJECTILE_NORMALIZE_SPEED_THRESHOLD = 2.0D;
    private static final double PROJECTILE_NORMALIZED_SPEED = 3.0D;
    private static final double EXPLOSION_ABSORB_RADIUS = 6.0D;
    private static final float EXPLOSION_DAMAGE_REDUCTION_PER_MOTH = 0.12F;
    private static final float EXPLOSION_BLOCK_REDUCTION_PER_MOTH = 0.14F;
    private static final float EXPLOSION_MIN_DAMAGE_FACTOR = 0.08F;
    private static final float EXPLOSION_MIN_BLOCK_FACTOR = 0.03F;
    private static final ThreadLocal<Boolean> IS_PROCESSING_SURROUNDING_SHIELD = ThreadLocal.withInitial(() -> false);

    @SubscribeEvent
    public static void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (entity.level.isClientSide || entity instanceof FossilMothEntity) {
            return;
        }

        List<FossilMothEntity> attachedMoths = getAttachedParticipatingMoths(entity);
        List<FossilMothEntity> flyingMoths = getFlyingParticipatingMoths(entity);
        int attachedCount = attachedMoths.size();
        int flyingCount = flyingMoths.size();

        if (attachedCount <= 0 && flyingCount <= 0) {
            clearFieldDebuffs(entity);
            return;
        }

        if (entity instanceof StandEntity) {
            applyStandDebuffs(entity, attachedCount, flyingCount);
        }
        else {
            applyLivingDebuffs(entity, attachedCount, flyingCount);
        }

        int totalFieldStrength = attachedCount + flyingCount;
        if (totalFieldStrength >= RESISTANCE_STOP_THRESHOLD) {
            applyApproachResistance(entity, attachedMoths, flyingMoths, totalFieldStrength);
        }

        if (attachedCount <= 0) {
            return;
        }

        if (entity.tickCount % ENERGY_ABSORB_INTERVAL != 0) {
            return;
        }

        double speedSqr = entity.getDeltaMovement().lengthSqr();
        if (speedSqr <= 0.0001D) {
            return;
        }

        distributeKineticToMoths(attachedMoths, 2);
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntityLiving();
        if (victim.level.isClientSide || !(victim instanceof PlayerEntity)) {
            return;
        }
        if (IS_PROCESSING_SURROUNDING_SHIELD.get()) {
            return;
        }

        List<FossilMothEntity> surroundingMoths = getSurroundingOwnerMoths((PlayerEntity) victim);
        if (surroundingMoths.size() < SURROUNDING_SHIELD_THRESHOLD) {
            return;
        }

        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        if (attacker == victim) {
            return;
        }
        if (attacker instanceof StandEntity && ((StandEntity) attacker).getUser() == victim) {
            return;
        }
        if (attacker instanceof FossilMothEntity && ((FossilMothEntity) attacker).getOwner() == victim) {
            return;
        }

        try {
            IS_PROCESSING_SURROUNDING_SHIELD.set(true);
            int totalDamage = (int) Math.ceil(event.getAmount());
            int mothCount = surroundingMoths.size();
            int perMoth = totalDamage / mothCount;
            int remainder = totalDamage % mothCount;

            for (int i = 0; i < surroundingMoths.size(); i++) {
                FossilMothEntity moth = surroundingMoths.get(i);
                int shareAmount = perMoth + (i == 0 ? remainder : 0);
                int energy = moth.getKineticEnergy();
                if (energy > 0) {
                    int absorb = Math.min(shareAmount, energy);
                    moth.setKineticEnergy(energy - absorb);
                    shareAmount -= absorb;
                }
                if (shareAmount > 0) {
                    moth.hurt(source, shareAmount);
                }
            }

            event.setCanceled(true);
        }
        finally {
            IS_PROCESSING_SURROUNDING_SHIELD.set(false);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntityLiving();
        if (victim.level.isClientSide || !event.getSource().isExplosion()) {
            return;
        }

        Vector3d sourcePos = event.getSource().getSourcePosition();
        if (sourcePos == null) {
            Entity direct = event.getSource().getDirectEntity();
            if (direct != null) {
                sourcePos = direct.position();
            }
            else {
                Entity source = event.getSource().getEntity();
                sourcePos = source != null ? source.position() : victim.position();
            }
        }

        LivingEntity excludedOwner = getIgnoredExplosionOwner(event.getSource());
        List<FossilMothEntity> moths = getExplosionAbsorbingMoths(victim.level, sourcePos, excludedOwner);
        if (moths.isEmpty()) {
            return;
        }

        float factor = getExplosionDamageFactor(moths.size());
        event.setAmount(event.getAmount() * factor);
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getWorld().isClientSide) {
            return;
        }

        Vector3d center = new Vector3d(event.getExplosion().getPosition().x, event.getExplosion().getPosition().y, event.getExplosion().getPosition().z);
        List<FossilMothEntity> moths = getExplosionAbsorbingMoths(event.getWorld(), center, null);
        if (moths.isEmpty()) {
            return;
        }

        float blockFactor = getExplosionBlockFactor(moths.size());
        List<BlockPos> affectedBlocks = event.getAffectedBlocks();
        int originalBlockCount = affectedBlocks.size();
        if (!affectedBlocks.isEmpty()) {
            affectedBlocks.sort(Comparator.comparingDouble(pos -> pos.distSqr(center.x, center.y, center.z, true)));
            int keepCount = Math.max(0, Math.min(affectedBlocks.size(), (int) Math.ceil(affectedBlocks.size() * blockFactor)));
            if (keepCount < affectedBlocks.size()) {
                affectedBlocks.subList(keepCount, affectedBlocks.size()).clear();
            }
        }

        int absorbedKinetic = Math.max(1, (int) Math.ceil((event.getAffectedEntities().size() * 1.5D + originalBlockCount * 0.12D) * (1.0F - blockFactor)));
        distributeKineticToMoths(moths, absorbedKinetic);
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isClientSide) {
            return;
        }

        ServerWorld serverWorld = (ServerWorld) event.world;
        for (Entity entity : serverWorld.getAllEntities()) {
            if (!(entity instanceof ProjectileEntity)) {
                continue;
            }
            ProjectileEntity projectile = (ProjectileEntity) entity;
            CompoundNBT data = entity.getPersistentData();
            int freezeTicks = data.getInt(PROJECTILE_FREEZE_TICKS_TAG);
            if (freezeTicks > 0) {
                entity.setNoGravity(true);
                data.putInt(PROJECTILE_FREEZE_TICKS_TAG, freezeTicks - 1);
                if (freezeTicks - 1 <= 0) {
                    entity.setNoGravity(false);
                    entity.removeTag(PROJECTILE_IMMOBILIZED_TAG);
                    entity.addTag(PROJECTILE_DROP_TAG);
                    data.remove(PROJECTILE_FREEZE_TICKS_TAG);
                }
            }
            else if (entity.getTags().contains(PROJECTILE_DROP_TAG)) {
                entity.setNoGravity(false);
                if (entity.isOnGround()) {
                    entity.removeTag(PROJECTILE_DROP_TAG);
                }
            }
            else {
                processProjectileAgainstMothField(projectile);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (entity.level.isClientSide || entity instanceof FossilMothEntity) {
            return;
        }

        double jumpPenalty = entity.getPersistentData().getDouble(ADHESION_JUMP_PENALTY_TAG);
        if (jumpPenalty <= 0) {
            return;
        }

        entity.setDeltaMovement(
                entity.getDeltaMovement().x,
                Math.max(0.08D, entity.getDeltaMovement().y * (1.0D - jumpPenalty)),
                entity.getDeltaMovement().z
        );
    }

    private static void applyLivingDebuffs(LivingEntity entity, int attachedCount, int flyingCount) {
        double attachedSpeedDebuff = attachedCount > 0 ? Math.max(-1.0D, -0.20D * attachedCount) : 0.0D;
        double attachedDamageDebuff = attachedCount > 0 ? -1.0D * attachedCount : 0.0D;
        double attachedAttackSpeedDebuff = attachedCount > 0 ? -0.10D * attachedCount : 0.0D;

        double flyingSpeedDebuff = flyingCount > 0 ? Math.max(-0.45D, -0.08D * flyingCount) : 0.0D;
        double totalSpeedDebuff = Math.max(-1.0D, attachedSpeedDebuff + flyingSpeedDebuff);
        double jumpPenalty = Math.min(0.85D, attachedCount * 0.06D + flyingCount * 0.03D);

        updateModifier(entity, Attributes.MOVEMENT_SPEED, ADHESION_FIELD_SPEED_UUID,
                "Adhesion Field Speed Debuff", totalSpeedDebuff, AttributeModifier.Operation.MULTIPLY_TOTAL);
        updateModifier(entity, Attributes.ATTACK_DAMAGE, ADHESION_FIELD_DAMAGE_UUID,
                "Adhesion Field Damage Debuff", attachedDamageDebuff, AttributeModifier.Operation.ADDITION);
        updateModifier(entity, Attributes.ATTACK_SPEED, ADHESION_FIELD_ATTACK_SPEED_UUID,
                "Adhesion Field Attack Speed Debuff", attachedAttackSpeedDebuff, AttributeModifier.Operation.MULTIPLY_TOTAL);
        setJumpPenalty(entity, jumpPenalty);
    }

    private static void applyStandDebuffs(LivingEntity entity, int attachedCount, int flyingCount) {
        double standSpeedDebuff = attachedCount > 0 ? Math.max(-0.85D, -0.12D * attachedCount) : 0.0D;
        double standFlyingSpeedDebuff = flyingCount > 0 ? Math.max(-0.30D, -0.05D * flyingCount) : 0.0D;
        double totalSpeedDebuff = Math.max(-0.90D, standSpeedDebuff + standFlyingSpeedDebuff);
        double standDamageDebuff = attachedCount > 0 ? Math.max(-0.75D, -0.08D * attachedCount) : 0.0D;
        double jumpPenalty = Math.min(0.70D, attachedCount * 0.04D + flyingCount * 0.02D);

        updateModifier(entity, Attributes.MOVEMENT_SPEED, ADHESION_FIELD_SPEED_UUID,
                "Adhesion Stand Speed Debuff", totalSpeedDebuff, AttributeModifier.Operation.MULTIPLY_TOTAL);
        updateModifier(entity, Attributes.ATTACK_DAMAGE, ADHESION_FIELD_DAMAGE_UUID,
                "Adhesion Stand Damage Debuff", standDamageDebuff, AttributeModifier.Operation.MULTIPLY_TOTAL);
        updateModifier(entity, Attributes.ATTACK_SPEED, ADHESION_FIELD_ATTACK_SPEED_UUID, "", 0, null);
        setJumpPenalty(entity, jumpPenalty);
    }

    private static List<FossilMothEntity> getAttachedParticipatingMoths(LivingEntity entity) {
        return entity.level.getEntitiesOfClass(FossilMothEntity.class,
                entity.getBoundingBox().inflate(ADHESION_FIELD_RADIUS),
                moth -> isAttachedParticipatingMoth(moth, entity));
    }

    private static List<FossilMothEntity> getFlyingParticipatingMoths(LivingEntity entity) {
        return entity.level.getEntitiesOfClass(FossilMothEntity.class,
                entity.getBoundingBox().inflate(FLYING_FIELD_RADIUS),
                moth -> isFlyingParticipatingMoth(moth, entity));
    }

    private static boolean isAttachedParticipatingMoth(FossilMothEntity moth, LivingEntity target) {
        if (!canParticipateInField(moth) || !moth.isAttached() || moth.getAttachedPos() == null) {
            return false;
        }
        return isValidEnemyMoth(moth, target) && moth.distanceToSqr(target) <= ADHESION_FIELD_RADIUS * ADHESION_FIELD_RADIUS;
    }

    private static boolean isFlyingParticipatingMoth(FossilMothEntity moth, LivingEntity target) {
        if (!canParticipateInField(moth) || moth.isAttached() || moth.isAttachedToEntity() || moth.isRecalling()) {
            return false;
        }
        if ((moth.isShieldPersistent() && !moth.isShieldMoth()) || moth.isPiercingCharging() || moth.isPiercingFiring() || moth.isSwarming()) {
            return false;
        }
        return isValidEnemyMoth(moth, target) && moth.distanceToSqr(target) <= FLYING_FIELD_RADIUS * FLYING_FIELD_RADIUS;
    }

    private static boolean canParticipateInField(FossilMothEntity moth) {
        return moth.isAlive() && moth.getKineticEnergy() < moth.getMaxEnergy();
    }

    private static boolean isValidEnemyMoth(FossilMothEntity moth, LivingEntity target) {
        LivingEntity owner = moth.getOwner();
        if (owner == null || owner.is(target)) {
            return false;
        }
        if (target instanceof StandEntity) {
            LivingEntity user = ((StandEntity) target).getUser();
            if (user != null && owner.is(user)) {
                return false;
            }
        }
        return true;
    }

    private static List<FossilMothEntity> getSurroundingOwnerMoths(PlayerEntity player) {
        return MCUtil.entitiesAround(FossilMothEntity.class, player, SURROUNDING_SHIELD_RADIUS, false,
                moth -> canParticipateInField(moth) && moth.getOwner() == player
                        && !moth.isAttached() && !moth.isAttachedToEntity()
                        && !moth.isRecalling() && !moth.isPiercingCharging() && !moth.isPiercingFiring()
                        && !moth.isShieldPersistent());
    }

    private static List<FossilMothEntity> getExplosionAbsorbingMoths(World world, Vector3d center, LivingEntity excludedOwner) {
        AxisAlignedBB box = new AxisAlignedBB(center.x, center.y, center.z, center.x, center.y, center.z).inflate(EXPLOSION_ABSORB_RADIUS);
        return world.getEntitiesOfClass(FossilMothEntity.class, box,
                moth -> canParticipateInField(moth)
                        && moth.distanceToSqr(center) <= EXPLOSION_ABSORB_RADIUS * EXPLOSION_ABSORB_RADIUS
                        && (excludedOwner == null || moth.getOwner() != excludedOwner));
    }

    private static LivingEntity getIgnoredExplosionOwner(DamageSource source) {
        if (source != null && source.isExplosion() && "ashes_to_ashes_kinetic_detonation".equals(source.getMsgId()) && source.getEntity() instanceof LivingEntity) {
            return (LivingEntity) source.getEntity();
        }
        return null;
    }

    private static float getExplosionDamageFactor(int mothCount) {
        return Math.max(EXPLOSION_MIN_DAMAGE_FACTOR, 1.0F - mothCount * EXPLOSION_DAMAGE_REDUCTION_PER_MOTH);
    }

    private static float getExplosionBlockFactor(int mothCount) {
        return Math.max(EXPLOSION_MIN_BLOCK_FACTOR, 1.0F - mothCount * EXPLOSION_BLOCK_REDUCTION_PER_MOTH);
    }

    private static void processProjectileAgainstMothField(ProjectileEntity projectile) {
        if (!projectile.isAlive() || projectile.getTags().contains(PROJECTILE_IMMOBILIZED_TAG)) {
            return;
        }

        CompoundNBT data = projectile.getPersistentData();
        if (data.getInt(PROJECTILE_FREEZE_TICKS_TAG) > 0) {
            return;
        }

        AxisAlignedBB searchBox = projectile.getBoundingBox().inflate(PROJECTILE_SCAN_RADIUS);
        List<FossilMothEntity> attachedMoths = projectile.level.getEntitiesOfClass(FossilMothEntity.class, searchBox,
                moth -> canParticipateInField(moth) && moth.isAttached() && moth.getAttachedPos() != null
                        && projectile.distanceToSqr(moth) <= PROJECTILE_SCAN_RADIUS * PROJECTILE_SCAN_RADIUS);
        List<FossilMothEntity> flyingMoths = projectile.level.getEntitiesOfClass(FossilMothEntity.class, searchBox,
                moth -> canParticipateInField(moth) && !moth.isAttached() && !moth.isAttachedToEntity() && !moth.isRecalling()
                        && (!moth.isShieldPersistent() || moth.isShieldMoth()) && !moth.isPiercingCharging() && !moth.isPiercingFiring()
                        && !moth.isSwarming()
                        && projectile.distanceToSqr(moth) <= PROJECTILE_SCAN_RADIUS * PROJECTILE_SCAN_RADIUS);

        int fieldStrength = attachedMoths.size() + flyingMoths.size();
        if (fieldStrength < PROJECTILE_STOP_THRESHOLD) {
            return;
        }

        Vector3d fieldCenter = getFieldCenter(attachedMoths, flyingMoths);
        if (fieldCenter == null) {
            return;
        }

        double distance = calculateProjectileDistance(fieldCenter, projectile);
        double speed = calculateProjectileSpeed(projectile);

        if (speed > PROJECTILE_NORMALIZE_SPEED_THRESHOLD) {
            normalizeProjectileMotion(projectile, speed);
        }

        if (distance <= PROJECTILE_IMMOBILIZE_DISTANCE) {
            immobilizeProjectile(projectile, fieldStrength, attachedMoths, flyingMoths, speed);
        }
    }

    private static double calculateProjectileDistance(Vector3d center, ProjectileEntity projectile) {
        double dx = projectile.getX() - center.x;
        double dy = projectile.getY() - center.y;
        double dz = projectile.getZ() - center.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double calculateProjectileSpeed(ProjectileEntity projectile) {
        Vector3d motion = projectile.getDeltaMovement();
        return Math.sqrt(motion.x * motion.x + motion.y * motion.y + motion.z * motion.z);
    }

    private static void normalizeProjectileMotion(ProjectileEntity projectile, double speed) {
        Vector3d motion = projectile.getDeltaMovement();
        projectile.setNoGravity(false);
        projectile.setDeltaMovement(
                (motion.x / speed) * PROJECTILE_NORMALIZED_SPEED,
                (motion.y / speed) * PROJECTILE_NORMALIZED_SPEED,
                (motion.z / speed) * PROJECTILE_NORMALIZED_SPEED
        );
    }

    private static void immobilizeProjectile(ProjectileEntity projectile, int fieldStrength,
                                             List<FossilMothEntity> attachedMoths,
                                             List<FossilMothEntity> flyingMoths,
                                             double speed) {
        Vector3d motion = projectile.getDeltaMovement();
        projectile.addTag(PROJECTILE_IMMOBILIZED_TAG);
        projectile.setDeltaMovement(motion.x / 500.0D, motion.y / 500.0D, motion.z / 500.0D);
        projectile.setNoGravity(true);
        projectile.getPersistentData().putInt(PROJECTILE_FREEZE_TICKS_TAG, PROJECTILE_FREEZE_TICKS + Math.min(fieldStrength, 10));

        List<FossilMothEntity> participants = new ArrayList<>(attachedMoths);
        participants.addAll(flyingMoths);
        distributeKineticToMoths(participants, Math.max(1, (int) Math.ceil(speed)));
    }

    private static void distributeKineticToMoths(List<FossilMothEntity> moths, int totalKinetic) {
        if (moths.isEmpty() || totalKinetic <= 0) {
            return;
        }

        List<FossilMothEntity> withRoom = new ArrayList<>();
        for (FossilMothEntity moth : moths) {
            if (moth.getKineticEnergy() < moth.getMaxEnergy()) {
                withRoom.add(moth);
            }
        }

        for (int i = 0; i < totalKinetic && !withRoom.isEmpty(); i++) {
            withRoom.sort(Comparator.comparingInt(FossilMothEntity::getKineticEnergy));
            FossilMothEntity moth = withRoom.get(0);
            int next = Math.min(moth.getKineticEnergy() + 1, moth.getMaxEnergy());
            moth.setKineticEnergy(next);
            if (next >= moth.getMaxEnergy()) {
                withRoom.remove(0);
                moth.recall();
            }
        }
    }

    private static void applyApproachResistance(LivingEntity entity, List<FossilMothEntity> attachedMoths,
                                                List<FossilMothEntity> flyingMoths, int fieldStrength) {
        Vector3d fieldCenter = getFieldCenter(attachedMoths, flyingMoths);
        if (fieldCenter == null) {
            return;
        }

        Vector3d motion = entity.getDeltaMovement();
        Vector3d horizontalMotion = new Vector3d(motion.x, 0, motion.z);
        if (horizontalMotion.lengthSqr() <= 0.0001D) {
            return;
        }

        Vector3d toCenter = fieldCenter.subtract(entity.position());
        Vector3d horizontalToCenter = new Vector3d(toCenter.x, 0, toCenter.z);
        if (horizontalToCenter.lengthSqr() <= 0.0001D) {
            return;
        }

        Vector3d approachDir = horizontalMotion.normalize();
        Vector3d centerDir = horizontalToCenter.normalize();
        double approachFactor = approachDir.dot(centerDir);
        if (approachFactor <= 0.15D) {
            return;
        }

        double resistance = Math.min(0.92D, 0.12D * fieldStrength * approachFactor);
        Vector3d slowed = horizontalMotion.scale(Math.max(0.0D, 1.0D - resistance));
        if (slowed.lengthSqr() < 0.0025D) {
            slowed = Vector3d.ZERO;
        }
        entity.setDeltaMovement(slowed.x, motion.y, slowed.z);
    }

    private static Vector3d getFieldCenter(List<FossilMothEntity> attachedMoths, List<FossilMothEntity> flyingMoths) {
        double x = 0;
        double y = 0;
        double z = 0;
        int count = 0;
        for (FossilMothEntity moth : attachedMoths) {
            x += moth.getX();
            y += moth.getY();
            z += moth.getZ();
            count++;
        }
        for (FossilMothEntity moth : flyingMoths) {
            x += moth.getX();
            y += moth.getY();
            z += moth.getZ();
            count++;
        }
        return count > 0 ? new Vector3d(x / count, y / count, z / count) : null;
    }

    private static void setJumpPenalty(LivingEntity entity, double jumpPenalty) {
        CompoundNBT persistentData = entity.getPersistentData();
        if (jumpPenalty > 0) {
            persistentData.putDouble(ADHESION_JUMP_PENALTY_TAG, jumpPenalty);
        }
        else if (persistentData.contains(ADHESION_JUMP_PENALTY_TAG)) {
            persistentData.remove(ADHESION_JUMP_PENALTY_TAG);
        }
    }

    private static void clearFieldDebuffs(LivingEntity entity) {
        updateModifier(entity, Attributes.MOVEMENT_SPEED, ADHESION_FIELD_SPEED_UUID, "", 0, null);
        updateModifier(entity, Attributes.ATTACK_DAMAGE, ADHESION_FIELD_DAMAGE_UUID, "", 0, null);
        updateModifier(entity, Attributes.ATTACK_SPEED, ADHESION_FIELD_ATTACK_SPEED_UUID, "", 0, null);
        CompoundNBT persistentData = entity.getPersistentData();
        if (persistentData.contains(ADHESION_JUMP_PENALTY_TAG)) {
            persistentData.remove(ADHESION_JUMP_PENALTY_TAG);
        }
    }

    private static void updateModifier(LivingEntity entity,
                                       net.minecraft.entity.ai.attributes.Attribute attribute,
                                       UUID uuid,
                                       String name,
                                       double amount,
                                       AttributeModifier.Operation operation) {
        ModifiableAttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }

        AttributeModifier modifier = instance.getModifier(uuid);
        if (amount != 0) {
            if (modifier != null) {
                if (Math.abs(modifier.getAmount() - amount) > 0.001D || modifier.getOperation() != operation) {
                    instance.removeModifier(uuid);
                    instance.addTransientModifier(new AttributeModifier(uuid, name, amount, operation));
                }
            }
            else {
                instance.addTransientModifier(new AttributeModifier(uuid, name, amount, operation));
            }
        }
        else if (modifier != null) {
            instance.removeModifier(uuid);
        }
    }
}