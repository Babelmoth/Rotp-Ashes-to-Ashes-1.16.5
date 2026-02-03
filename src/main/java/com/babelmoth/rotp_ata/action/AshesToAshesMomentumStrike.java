package com.babelmoth.rotp_ata.action;

import com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity;
import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.init.ModSounds;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.github.standobyte.jojo.power.impl.stand.IStandManifestation;
import com.github.standobyte.jojo.util.mc.MCUtil;
import com.github.standobyte.jojo.util.mc.damage.KnockbackCollisionImpact;
import com.github.standobyte.jojo.util.mod.JojoModUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.util.math.vector.Vector3d;

import com.github.standobyte.jojo.action.ActionConditionResult;
import net.minecraft.util.text.TranslationTextComponent;
import com.github.standobyte.jojo.action.player.IPlayerAction;
import com.github.standobyte.jojo.action.player.ContinuousActionInstance;
import com.github.standobyte.jojo.capability.entity.PlayerUtilCap;
import com.github.standobyte.jojo.client.playeranim.anim.ModPlayerAnimations;

import java.util.List;

public class AshesToAshesMomentumStrike extends StandAction implements com.github.standobyte.jojo.action.player.IPlayerAction<AshesToAshesMomentumStrike.Instance, IStandPower> {
    // Mastery Constants
    private static final float MASTERY_THRESHOLD = 500.0f; // Points needed for max mastery
    private static final float POINTS_PER_HIT = 2.0f;
    
    // Balanced Values
    private static final float BASE_DAMAGE = 2.0f;
    private static final float MASTERED_EXTRA_DAMAGE = 3.0f; // Total 5.0 at max
    private static final int KINETIC_COST = 10;
    private static final float STAMINA_COST = 100f; // Explicit cost
    
    public AshesToAshesMomentumStrike(StandAction.Builder builder) {
        super(builder);
    }
    
    @Override
    public float getStaminaCost(IStandPower power) {
        return STAMINA_COST;
    }

    @Override
    protected ActionConditionResult checkSpecificConditions(LivingEntity user, IStandPower power, ActionTarget target) {
        if (power.getStamina() < getStaminaCost(power)) {
             return ActionConditionResult.createNegative(new TranslationTextComponent("jojo.message.action_condition.no_stamina"));
        }
        
        // ... (rest of check conditions)
        IStandManifestation manifestation = power.getStandManifestation();
        if (manifestation instanceof AshesToAshesStandEntity) {
            AshesToAshesStandEntity stand = (AshesToAshesStandEntity) manifestation;
            // Check total energy (Hamon + Kinetic) for availability
            if (stand.getGlobalTotalEnergy() < KINETIC_COST) {
                 return ActionConditionResult.createNegative(new TranslationTextComponent("jojo.ata.message.requires_momentum"));
            }
        } else {
             // If stand not summoned, check if we have any moths with total energy
             boolean hasEnergy = MCUtil.entitiesAround(
                 FossilMothEntity.class, user, 64.0, false,
                 moth -> moth.isAlive() && moth.getOwner() == user && moth.getTotalEnergy() >= 10 // Check total (Hamon + Kinetic)
             ).stream().findAny().isPresent();
             
             if (!hasEnergy) {
                 return ActionConditionResult.createNegative(new TranslationTextComponent("jojo.ata.message.requires_momentum"));
             }
        }
        
        return super.checkSpecificConditions(user, power, target);
    }

    // ... onClick ...

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (!world.isClientSide()) {
            // Store the original target for use in the Instance
            this.storedTarget = target;
            setPlayerAction(user, power); // Triggers Continuous Action
        }
    }
    
    // Temporary storage for target between perform() and createContinuousActionInstance()
    private ActionTarget storedTarget = ActionTarget.EMPTY;
    
    @Override
    public Instance createContinuousActionInstance(LivingEntity user, com.github.standobyte.jojo.capability.entity.PlayerUtilCap userCap, IStandPower power) {
        if (user.level.isClientSide() && user instanceof PlayerEntity) {
            com.github.standobyte.jojo.client.playeranim.anim.ModPlayerAnimations.pillarManPunch.setAnimEnabled((PlayerEntity) user, true);
        }
        ActionTarget targetToUse = this.storedTarget;
        this.storedTarget = ActionTarget.EMPTY; // Clear after use
        return new Instance(user, userCap, power, this, targetToUse); 
    }

    // Logic moved to Instance
    public void performStrike(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        // 1. Consume Energy First (to know if Hamon was used)
        boolean usedHamon = false;
        IStandManifestation manifestation = power.getStandManifestation();
        if (manifestation instanceof AshesToAshesStandEntity) {
            AshesToAshesStandEntity stand = (AshesToAshesStandEntity) manifestation;
            AshesToAshesStandEntity.EnergyConsumeResult result = stand.consumeEnergyPrioritizeHamon(KINETIC_COST);
            usedHamon = result.usedHamon();
        }
        
        // 2. Calculate Damage based on Mastery (Linear interpolation)
        float learningProgress = power.getLearningProgressPoints(this);
        boolean isMastered = learningProgress >= MASTERY_THRESHOLD;
        float progressRatio = Math.min(learningProgress / MASTERY_THRESHOLD, 1.0f);
        
        float bonusDamage = BASE_DAMAGE + (MASTERED_EXTRA_DAMAGE * progressRatio);
        float totalDamage = (float) user.getAttributeValue(Attributes.ATTACK_DAMAGE) + bonusDamage;

        // 3. Perform Strike
        boolean hitSuccessful = false;

        if (target.getType() == ActionTarget.TargetType.ENTITY) {
             Entity entity = target.getEntity();
             if (entity instanceof LivingEntity) {
                 hitSuccessful = punchPerformEntity(world, user, (LivingEntity)entity, totalDamage, usedHamon);
             }
        } else if (target.getType() == ActionTarget.TargetType.BLOCK) {
             hitSuccessful = punchPerformBlock(world, user, target, totalDamage);
        }

        // Fallback: If no entity hit (and not aiming at a block), try a sweep in front of the user
        if (!hitSuccessful && target.getType() != ActionTarget.TargetType.BLOCK) {
            List<LivingEntity> nearby = MCUtil.entitiesAround(LivingEntity.class, user, 4.0, false, 
                e -> MCUtil.canHarm(user, e) && e != user);
            
            LivingEntity closest = null;
            double closestDistSqr = 100.0;
            Vector3d lookVec = user.getLookAngle();
            Vector3d eyePos = user.getEyePosition(1.0f);
            
            for (LivingEntity e : nearby) {
                 Vector3d toEntity = e.getBoundingBox().getCenter().subtract(eyePos).normalize();
                 double dot = lookVec.dot(toEntity);
                 // Check if in front (cone) and within reach
                 if (dot > 0.7) { 
                     double d = user.distanceToSqr(e);
                     if (d < 25.0 && d < closestDistSqr) { // 5 blocks reach
                         closestDistSqr = d;
                         closest = e;
                     }
                 }
            }
            
            if (closest != null) {
                hitSuccessful = punchPerformEntity(world, user, closest, totalDamage, usedHamon);
            }
        }

        // 4. Add Mastery (Only on success)
        if (hitSuccessful && !isMastered) {
            power.addLearningProgressPoints(this, POINTS_PER_HIT);
        }
    }
    
    private boolean punchPerformEntity(World world, LivingEntity user, LivingEntity targetEntity, float damage, boolean useHamon) {
         PlayerEntity pUser = user instanceof PlayerEntity ? (PlayerEntity) user : null;
         
         // Always deal normal physical damage first
         boolean hurt = targetEntity.hurt(pUser != null ? net.minecraft.util.DamageSource.playerAttack(pUser) : net.minecraft.util.DamageSource.mobAttack(user), damage);
         
         // If Hamon was used, add small bonus Hamon damage (1.0F - based on RotP patterns)
         if (hurt && useHamon) {
             com.github.standobyte.jojo.util.mc.damage.DamageUtil.dealHamonDamage(targetEntity, 1.0F, user, user);
         }
         
         if (hurt) {
             world.playSound(null, targetEntity.getX(), targetEntity.getEyeY(), targetEntity.getZ(), ModSounds.PILLAR_MAN_PUNCH.get(), targetEntity.getSoundSource(), 1.2F, 0.8F);
             
             targetEntity.knockback(2.0F, user.getX() - targetEntity.getX(), user.getZ() - targetEntity.getZ());
             KnockbackCollisionImpact.getHandler(targetEntity).ifPresent(cap -> cap
                     .onPunchSetKnockbackImpact(targetEntity.getDeltaMovement(), user)
             );
             return true;
         }
         return false;
    }

    private boolean punchPerformBlock(World world, LivingEntity user, ActionTarget target, float damage) {
        if (JojoModUtil.breakingBlocksEnabled(world)) {
            blockDestroy(world, user, target, 0, 0, 0, damage);
            
            // Area destruction logic preserved for high damage (optional, maybe keep tied to mastery later?)
            if (damage >= 20.0f) {
                // Area destruction optimization
                for(int i=-1; i<=1; i++) for(int j=-1; j<=1; j++) for(int k=-1; k<=1; k++) 
                    if(i!=0 || j!=0 || k!=0) blockDestroy(world, user, target, i, j, k, damage);
            }
        }
        world.playSound(null, user.getX(), user.getY(), user.getZ(), ModSounds.HEAVY_PUNCH.get(), user.getSoundSource(), 1.5F, 1.2F);
        return true; 
    }

    private void blockDestroy(World world, LivingEntity user, ActionTarget target, double x, double y, double z, float damage) {
        BlockPos pos = target.getBlockPos().offset(x, y, z);
        if (!world.isClientSide() && JojoModUtil.canEntityDestroy((ServerWorld) world, pos, world.getBlockState(pos), user)) {
            BlockState blockState = world.getBlockState(pos);
            if (!blockState.isAir()) {
                float blockHardness = blockState.getDestroySpeed(world, pos);
                // boolean dropItem = true; // This variable was unused, removed it.
                
                if (blockHardness >= 0 && blockHardness <= 2.5f * Math.sqrt(damage)) {
                    MCUtil.destroyBlock(world, pos, true, user);
                } else {
                    SoundType soundType = blockState.getSoundType(world, pos, user);
                    world.playSound(null, pos, soundType.getHitSound(), SoundCategory.BLOCKS, (soundType.getVolume() + 1.0F) / 8.0F, soundType.getPitch() * 0.5F);
                }
            }
        }
    }
    
    @Override
    public float getMaxTrainingPoints(IStandPower power) {
        return MASTERY_THRESHOLD;
    }
    
    @Override
    public void onMaxTraining(IStandPower power) {
        power.unlockAction((StandAction) getShiftVariationIfPresent());
    }
    
    public static class Instance extends com.github.standobyte.jojo.action.player.ContinuousActionInstance<AshesToAshesMomentumStrike, IStandPower> {
        private final ActionTarget storedTarget;
        
        public Instance(LivingEntity user, com.github.standobyte.jojo.capability.entity.PlayerUtilCap userCap, IStandPower power, AshesToAshesMomentumStrike action, ActionTarget target) {
            super(user, userCap, power, action);
            this.storedTarget = target != null ? target : ActionTarget.EMPTY;
        }

        @Override
        public void playerTick() {
            // Anim Logic
            if (getTick() == 1) { // Start
                 if (user.level.isClientSide()) {
                    user.swing(net.minecraft.util.Hand.MAIN_HAND, true);
                    user.level.playSound(user instanceof PlayerEntity ? (PlayerEntity)user : null, user.getX(), user.getEyeY(), user.getZ(), 
                        ModSounds.PILLAR_MAN_SWING.get(), user.getSoundSource(), 1.0F, 1.25F);
                 }
            }
            // Strike at tick 4 using the stored target
            if (getTick() == 4) {
                if (!user.level.isClientSide()) {
                    action.performStrike(user.level, user, playerPower, storedTarget);
                }
            }
            // End
            if (getTick() >= 8) {
                stopAction();
            }
        }
        
        @Override
        public void onStop() {
            super.onStop();
            if (user.level.isClientSide() && user instanceof PlayerEntity) {
                com.github.standobyte.jojo.client.playeranim.anim.ModPlayerAnimations.pillarManPunch.setAnimEnabled((PlayerEntity) user, false);
            }
        }
        
        @Override
        public boolean updateTarget() { return true; }
        @Override
        public float getWalkSpeed() { return 0.5f; }
    }
}
