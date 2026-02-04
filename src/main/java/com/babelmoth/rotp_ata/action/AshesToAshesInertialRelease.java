package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity;
import com.babelmoth.rotp_ata.init.InitStands;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.init.ModSounds;
import com.github.standobyte.jojo.power.impl.stand.IStandManifestation;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.github.standobyte.jojo.util.mc.MCUtil;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.FallingBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.List;

import net.minecraft.particles.ParticleTypes;
import net.minecraft.particles.BlockParticleData;

public class AshesToAshesInertialRelease extends StandAction {
    private static final int KINETIC_COST = 50;
    private static final int REQUIRED_MASTERY = 500;
    private static final double SMASH_RADIUS = 5.0;
    private static final float SMASH_DAMAGE = 2.0f; // Reduced from 4.0f
    private static final float KNOCKBACK_STRENGTH = 2.5f; // Slight bump to base
    private static final float STAMINA_COST = 150f; // Stamina cost for this powerful ability

    public AshesToAshesInertialRelease(StandAction.Builder builder) {
        super(builder);
    }
    
    @Override
    public float getStaminaCost(IStandPower power) {
        return STAMINA_COST;
    }
    
    @Override
    public boolean isLegalInHud(IStandPower power) {
        return isUnlocked(power);
    }
    
    @Override
    public boolean isUnlocked(IStandPower power) {
        // Enforce visibility only when Mastered (AND technically unlocked)
        // This fixes the issue where it might appear visible due to dirty data (points=0 instead of -1)
        return super.isUnlocked(power) && 
               power.getLearningProgressPoints(InitStands.ASHES_TO_ASHES_MOMENTUM_STRIKE.get()) >= REQUIRED_MASTERY;
    }
    
    @Override
    protected ActionConditionResult checkSpecificConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        // 1. Check Total Energy (Hamon + Kinetic)
        IStandManifestation manifestation = power.getStandManifestation();
        if (manifestation instanceof AshesToAshesStandEntity) {
            AshesToAshesStandEntity stand = (AshesToAshesStandEntity) manifestation;
            if (stand.getGlobalTotalEnergy() < KINETIC_COST) {
                 return ActionConditionResult.createNegative(new TranslationTextComponent("jojo.ata.message.requires_momentum"));
            }
        }
        
        // 2. Check Momentum Strike Mastery
        float mastery = power.getLearningProgressPoints(InitStands.ASHES_TO_ASHES_MOMENTUM_STRIKE.get());
        if (mastery < REQUIRED_MASTERY) {
            return ActionConditionResult.createNegative(new TranslationTextComponent("jojo.ata.message.requires_mastery", "Momentum Strike"));
        }
        
        // 3. Target Check (Must aim at a block)
        if (target.getType() != ActionTarget.TargetType.BLOCK) {
            return ActionConditionResult.createNegative(new TranslationTextComponent("jojo.ata.message.action_condition.ground_only")); 
        }
        
        return super.checkSpecificConditions(user, power, target);
    }

    @Override
    public void onClick(World world, LivingEntity user, IStandPower power) {
        if (!world.isClientSide()) {
            // Force summon stand if not active
            if (!power.isActive() && power.getType() instanceof com.github.standobyte.jojo.power.impl.stand.type.EntityStandType) {
                ((com.github.standobyte.jojo.power.impl.stand.type.EntityStandType<?>) power.getType())
                    .summon(user, power, entity -> {}, true, false);
            }
        } else {
            if (user instanceof PlayerEntity) {
                user.swing(net.minecraft.util.Hand.MAIN_HAND, true);
            }
        }
        super.onClick(world, user, power);
    }
    
    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (world.isClientSide()) return;
        ServerWorld serverWorld = (ServerWorld) world;

        Vector3d center = Vector3d.atCenterOf(target.getBlockPos());
        
        // 1. Sound and Visuals
        world.playSound(null, center.x, center.y, center.z, ModSounds.HEAVY_PUNCH.get(), user.getSoundSource(), 1.0F, 0.5F);
        
        // Note: Cooldown is now handled automatically by the Builder config in InitStands.java

        // 3. Ground Smasher Logic (Refined from user's KubeJS script)
        BlockPos centerPos = target.getBlockPos();
        int radius = (int) SMASH_RADIUS;
        
        // Iterate circularly
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x*x + z*z <= radius*radius) {
                     // Check exclusion: Skip the block directly under the player (x=0, z=0)
                     if (x == 0 && z == 0) continue;

                     BlockPos targetPos = centerPos.offset(x, 0, z); // Target the block itself, not below
                     
                     BlockState state = world.getBlockState(targetPos);
                     float hardness = state.getDestroySpeed(world, targetPos);
                     
                     // Filter: Hardness <= 0.8 and valid block
                     if (!state.isAir(world, targetPos) && hardness >= 0 && hardness <= 0.8f) {
                         // Formula from script: motion = 0.12 + abs(x)*0.014 + abs(z)*0.014
                         // Note: Script applies ONLY vertical motion to blocks.
                         double motionY = 0.12 + Math.abs(x) * 0.014 + Math.abs(z) * 0.014;
                         
                         FallingBlockEntity fallingBlock = new FallingBlockEntity(world, targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, state);
                         fallingBlock.time = 1; 
                         fallingBlock.setDeltaMovement(0, motionY, 0); // Pure vertical motion
                         fallingBlock.dropItem = false;
                         
                         world.setBlock(targetPos, net.minecraft.block.Blocks.AIR.defaultBlockState(), 3);
                         world.addFreshEntity(fallingBlock);
                         
                         // Add Particles (Smoke and Block Crumbs)
                         serverWorld.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, 
                             targetPos.getX() + 0.5, targetPos.getY() + 1.2, targetPos.getZ() + 0.5, 
                             2, 0.1, 0.1, 0.1, 0.03);
                         serverWorld.sendParticles(new BlockParticleData(ParticleTypes.BLOCK, state),
                             targetPos.getX() + 0.5, targetPos.getY() + 1.2, targetPos.getZ() + 0.5,
                             4, 0.2, 0.2, 0.2, 0.1);
                     }
                }
            }
        }

        // 4. Consume Resources First (to track Hamon usage)
        boolean usedHamon = false;
        IStandManifestation manifestation = power.getStandManifestation();
        if (manifestation instanceof AshesToAshesStandEntity) {
            AshesToAshesStandEntity stand = (AshesToAshesStandEntity) manifestation;
            AshesToAshesStandEntity.EnergyConsumeResult result = stand.consumeEnergyPrioritizeHamon(KINETIC_COST);
            usedHamon = result.usedHamon();
        }

        // 5. Entity Knockback and Damage (Outward Wave)
        List<LivingEntity> targets = MCUtil.entitiesAround(LivingEntity.class, user, SMASH_RADIUS + 1, false, 
            e -> e != user && !e.isAlliedTo(user) && e != power.getStandManifestation());
            
        for (LivingEntity e : targets) {
            Vector3d dir = e.position().subtract(center);
            double dirX = dir.x;
            double dirZ = dir.z;
            
            // Apply damage (always normal damage first)
            e.hurt(net.minecraft.util.DamageSource.mobAttack(user), SMASH_DAMAGE);
            
            // Add small bonus Hamon damage if Hamon was used
            if (usedHamon) {
                com.github.standobyte.jojo.util.mc.damage.DamageUtil.dealHamonDamage(e, 0.5F, user, user);
            }
            
            // Apply knockback motion
            e.setDeltaMovement(e.getDeltaMovement().add(
                0.5 * (dirX + 0.00001), 
                0.6, 
                0.5 * (dirZ + 0.00001)
            ));
            e.hurtMarked = true;
        }
    }
}
