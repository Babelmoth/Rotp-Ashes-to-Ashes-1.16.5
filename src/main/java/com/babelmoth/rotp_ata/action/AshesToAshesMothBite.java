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
 * Moth bite: hold to use, no cooldown. Player is slowed while holding (same as RotP skills).
 * Per attached entity, one random attack-ready moth deals damage then enters 2s cooldown; moths on different entities are independent.
 */
public class AshesToAshesMothBite extends StandAction {

    private static final double MOTH_SEARCH_RADIUS = 64.0;
    /** Very low damage per moth per hit */
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
        return ActionConditionResult.POSITIVE; // Allowed during remote control
    }

    @Override
    public void onHoldTick(World world, LivingEntity user, IStandPower power, int ticksHeld, ActionTarget target, boolean requirementsMet) {
        if (world.isClientSide || !requirementsMet) return;
        // Throttle: one batch every 3 ticks instead of every tick
        if (ticksHeld % 3 != 0) return;

        List<FossilMothEntity> ownerMoths = MothQueryUtil.getOwnerMoths(user, MOTH_SEARCH_RADIUS);
        // Group by attached entity so each entity is handled independently
        Map<Integer, List<FossilMothEntity>> mothsByTargetId = ownerMoths.stream()
            .filter(FossilMothEntity::isAttachedToEntity)
            .collect(Collectors.groupingBy(m -> m.getEntityData().get(FossilMothEntity.ATTACHED_ENTITY_ID)));

        DamageSource baseSource = user instanceof net.minecraft.entity.player.PlayerEntity
            ? DamageSource.playerAttack((net.minecraft.entity.player.PlayerEntity) user)
            : DamageSource.mobAttack(user);
        DamageSource source = new ModdedDamageSourceWrapper(baseSource).setKnockbackReduction(0); // No knockback

        for (Map.Entry<Integer, List<FossilMothEntity>> entry : mothsByTargetId.entrySet()) {
            Entity attached = world.getEntity(entry.getKey());
            if (!(attached instanceof LivingEntity) || !attached.isAlive()) continue;
            LivingEntity host = (LivingEntity) attached;

            // Moths on this entity that can attack (not on 2s cooldown)
            List<FossilMothEntity> ready = new ArrayList<>();
            for (FossilMothEntity moth : entry.getValue()) {
                if (!moth.isMothBiteOnCooldown()) ready.add(moth);
            }
            if (ready.isEmpty()) continue;

            // Pick one at random to attack; that moth enters 2s cooldown
            FossilMothEntity chosen = ready.get(world.random.nextInt(ready.size()));
            DamageUtil.hurtThroughInvulTicks(host, source, BITE_DAMAGE);
            chosen.setMothBiteCooldown();
        }
    }
}
