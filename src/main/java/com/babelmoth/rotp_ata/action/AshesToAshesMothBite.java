package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.github.standobyte.jojo.util.mc.damage.DamageUtil;
import com.github.standobyte.jojo.util.mc.damage.ModdedDamageSourceWrapper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 飞蛾撕咬：长按使用，无冷却。使用期间玩家减速（与 RotP 技能一致）。
 * 每个被依附实体上，随机一只可攻击的飞蛾进行攻击，该飞蛾攻击后进入 2 秒冷却，下一只再攻击；
 * 依附不同实体的飞蛾互不影响，可同时高效攻击多个实体。
 */
public class AshesToAshesMothBite extends StandAction {

    private static final double MOTH_SEARCH_RADIUS = 64.0;
    /** 单只飞蛾单次极低伤害值 */
    private static final float BITE_DAMAGE = 0.15f;

    public AshesToAshesMothBite(AbstractBuilder<?> builder) {
        super(builder);
    }

    @Override
    public float getHeldWalkSpeed() {
        return 0.5f; // Same as RotP in-action slowdown (e.g. melee barrage)
    }

    @Override
    public int getHoldDurationMax(IStandPower standPower) {
        return Integer.MAX_VALUE;
    }

    @Override
    public ActionConditionResult checkSpecificConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        return ActionConditionResult.POSITIVE; // 允许在远程操控时使用
    }

    @Override
    public void onHoldTick(World world, LivingEntity user, IStandPower power, int ticksHeld, ActionTarget target, boolean requirementsMet) {
        if (world.isClientSide || !requirementsMet) return;
        // 降低攻击频率：整体节奏从每 tick 一批改为每 3 tick 一批
        if (ticksHeld % 3 != 0) return;

        List<FossilMothEntity> ownerMoths = MothQueryUtil.getOwnerMoths(user, MOTH_SEARCH_RADIUS);
        // 按所依附的实体分组，不同实体互不影响
        Map<Integer, List<FossilMothEntity>> mothsByTargetId = ownerMoths.stream()
            .filter(FossilMothEntity::isAttachedToEntity)
            .collect(Collectors.groupingBy(m -> m.getEntityData().get(FossilMothEntity.ATTACHED_ENTITY_ID)));

        DamageSource baseSource = user instanceof net.minecraft.entity.player.PlayerEntity
            ? DamageSource.playerAttack((net.minecraft.entity.player.PlayerEntity) user)
            : DamageSource.mobAttack(user);
        DamageSource source = new ModdedDamageSourceWrapper(baseSource).setKnockbackReduction(0); // 无击退

        for (Map.Entry<Integer, List<FossilMothEntity>> entry : mothsByTargetId.entrySet()) {
            Entity attached = world.getEntity(entry.getKey());
            if (!(attached instanceof LivingEntity) || !attached.isAlive()) continue;
            LivingEntity host = (LivingEntity) attached;

            // 该实体上可攻击的飞蛾（未在 2 秒冷却中）
            List<FossilMothEntity> ready = new ArrayList<>();
            for (FossilMothEntity moth : entry.getValue()) {
                if (!moth.isMothBiteOnCooldown()) ready.add(moth);
            }
            if (ready.isEmpty()) continue;

            // 随机选一只进行攻击，攻击后该飞蛾进入 2 秒冷却
            FossilMothEntity chosen = ready.get(world.random.nextInt(ready.size()));
            DamageUtil.hurtThroughInvulTicks(host, source, BITE_DAMAGE);
            chosen.setMothBiteCooldown();
        }
    }
}
