package com.babelmoth.rotp_ata.action;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.github.standobyte.jojo.power.impl.stand.IStandManifestation;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;

public class AshesToAshesMothSwarmAttack extends StandAction {

    /** 先召唤后需延迟执行的扑击（等替身 tick 补充飞蛾后再执行） */
    private static final Map<UUID, PendingSwarm> PENDING_SWARM = new ConcurrentHashMap<>();
    private static final int DELAY_TICKS = 3;

    public static final class PendingSwarm {
        final net.minecraft.world.server.ServerWorld world;
        final long runAtTick;
        PendingSwarm(net.minecraft.world.server.ServerWorld world, long runAtTick) {
            this.world = world;
            this.runAtTick = runAtTick;
        }
    }

    /** 由 EventHandler 在 ServerTickEvent 中调用，执行到期的延迟扑击 */
    public static void tickPendingSwarmAttacks(net.minecraft.world.server.ServerWorld world) {
        long now = world.getGameTime();
        PENDING_SWARM.entrySet().removeIf(entry -> {
            PendingSwarm p = entry.getValue();
            if (p.world != world || now < p.runAtTick) return false;
            net.minecraft.entity.player.PlayerEntity player = world.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null && player.isAlive()) {
                com.github.standobyte.jojo.power.impl.stand.IStandPower power = com.github.standobyte.jojo.power.impl.stand.IStandPower.getStandPowerOptional(player).orElse(null);
                if (power != null && power.isActive()) {
                    runSwarmAttackStatic(p.world, player, power);
                }
            }
            return true;
        });
    }

    public AshesToAshesMothSwarmAttack(AbstractBuilder<?> builder) {
        super(builder);
    }
    
    @Override
    public TargetRequirement getTargetRequirement() {
        return TargetRequirement.NONE;
    }

    @Override
    public ActionConditionResult checkConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        // 与原技能一致：有身边飞蛾可用时允许；若替身未释放也允许（先完全释放，下一 tick 再用身边飞蛾）
        List<FossilMothEntity> moths = MothQueryUtil.getMothsForSwarm(user, AshesToAshesConstants.QUERY_RADIUS_SWARM);
        if (!moths.isEmpty()) {
            return ActionConditionResult.POSITIVE;
        }
        if (!power.isActive()) {
            return ActionConditionResult.POSITIVE; // 未释放时允许，perform 里会先召唤再下一 tick 用身边飞蛾
        }
        return ActionConditionResult.createNegative(new StringTextComponent("No moths available"));
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (!world.isClientSide && world instanceof net.minecraft.world.server.ServerWorld) {
            net.minecraft.world.server.ServerWorld serverWorld = (net.minecraft.world.server.ServerWorld) world;
            boolean wasInactive = !power.isActive();

            // 若未完全释放替身：先执行与 M 键等价的“完全释放替身”（addToWorld=true 才会真正加入世界并播放召唤），再在下一 tick 根据已释放的替身执行扑击
            if (wasInactive && power.getType() instanceof com.github.standobyte.jojo.power.impl.stand.type.EntityStandType) {
                ((com.github.standobyte.jojo.power.impl.stand.type.EntityStandType<?>) power.getType())
                    .summon(user, power, entity -> {}, true, true);
                // 延迟若干 tick 再执行扑击，让替身先 tick 并补充身边飞蛾，再驱使飞蛾（与先释放再使用技能一致）
                PENDING_SWARM.put(user.getUUID(), new PendingSwarm(serverWorld, serverWorld.getGameTime() + DELAY_TICKS));
                return;
            }

            runSwarmAttack(serverWorld, user, power);
        }
    }

    /** 替身已释放时的扑击逻辑（与原技能一致：只调用身边飞蛾，不单独生成） */
    private void runSwarmAttack(net.minecraft.world.server.ServerWorld world, LivingEntity user, IStandPower power) {
        runSwarmAttackStatic(world, user, power);
    }

    private static void runSwarmAttackStatic(net.minecraft.world.server.ServerWorld world, LivingEntity user, IStandPower power) {
        double range = AshesToAshesConstants.QUERY_RADIUS_SWARM;
        List<FossilMothEntity> moths = MothQueryUtil.getMothsForSwarm(user, range);
        if (moths.isEmpty()) {
            return; // 身边没有飞蛾则不扑击，和原技能一致
        }

        Entity viewEntity = user;
        net.minecraft.util.math.vector.Vector3d eyePos = user.getEyePosition(1.0F);
        net.minecraft.util.math.vector.Vector3d lookVec = user.getViewVector(1.0F);

        IStandManifestation stand = power.getStandManifestation();
        if (stand instanceof StandEntity) {
            StandEntity standEntity = (StandEntity) stand;
            if (standEntity.isManuallyControlled()) {
                viewEntity = standEntity;
                eyePos = standEntity.getEyePosition(1.0F);
                lookVec = standEntity.getViewVector(1.0F);
            }
        }

        net.minecraft.util.math.vector.Vector3d maxVec = eyePos.add(lookVec.x * range, lookVec.y * range, lookVec.z * range);
        net.minecraft.util.math.AxisAlignedBB aabb = viewEntity.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(1.0D, 1.0D, 1.0D);
        final Entity finalViewEntity = viewEntity;

        net.minecraft.util.math.EntityRayTraceResult result = net.minecraft.entity.projectile.ProjectileHelper.getEntityHitResult(
            viewEntity, eyePos, maxVec, aabb,
            entity -> {
                if (entity.isSpectator() || entity == user || entity == finalViewEntity || entity instanceof FossilMothEntity)
                    return false;
                if (entity instanceof LivingEntity)
                    return entity.isPickable() && ((LivingEntity) entity).isAlive();
                if (entity instanceof ItemEntity) {
                    ItemEntity ie = (ItemEntity) entity;
                    return !ie.removed && !ie.getItem().isEmpty() && !ie.getPersistentData().getBoolean("ata_retrieved");
                }
                return false;
            },
            range * range
        );

        Entity targetEntity = result != null ? result.getEntity() : null;
        if (targetEntity != null && !moths.isEmpty()) {
            for (FossilMothEntity moth : moths) {
                moth.swarmTo(targetEntity);
            }
        }
    }
}
