package com.babelmoth.rotp_ata.client.ui.marker;

import java.util.List;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.init.InitStands;
import com.github.standobyte.jojo.client.ui.actionshud.ActionsOverlayGui;
import com.github.standobyte.jojo.client.ui.marker.MarkerRenderer;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.github.standobyte.jojo.power.impl.stand.IStandManifestation;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.projectile.ProjectileHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;

public class MothSwarmAttackMarker extends MarkerRenderer {
    private static final double RANGE = 64.0D;

    public MothSwarmAttackMarker(Minecraft mc) {
        super(new ResourceLocation(AddonMain.MOD_ID, "textures/action/ashes_to_ashes_moth_swarm_attack.png"), mc);
        this.renderThroughBlocks = false;
    }

    @Override
    protected boolean shouldRender() {
        ActionsOverlayGui hud = ActionsOverlayGui.getInstance();
        return hud.showExtraActionHud(InitStands.ASHES_TO_ASHES_MOTH_SWARM_ATTACK.get());
    }

    @Override
    protected void updatePositions(List<MarkerInstance> list, float partialTick) {
        // Show marker on the entity the player/stand is looking at
        Entity target = getLookedAtEntity(mc.player, partialTick);
        if (target != null) {
            list.add(new MarkerInstance(
                target.getPosition(partialTick).add(0, target.getBbHeight() * 1.1, 0),
                true,
                java.util.Optional.empty()
            ));
        }
    }
    
    private Entity getLookedAtEntity(LivingEntity player, float partialTick) {
        Vector3d eyePos;
        Vector3d lookVec;
        Entity viewEntity = player;
        
        // Check if Stand is in remote control mode
        java.util.Optional<IStandPower> powerOpt = IStandPower.getStandPowerOptional(player).resolve();
        if (powerOpt.isPresent()) {
            IStandPower power = powerOpt.get();
            IStandManifestation stand = power.getStandManifestation();
            if (stand instanceof StandEntity) {
                StandEntity standEntity = (StandEntity) stand;
                if (standEntity.isManuallyControlled()) {
                    // Use Stand's perspective
                    viewEntity = standEntity;
                    eyePos = standEntity.getEyePosition(partialTick);
                    lookVec = standEntity.getViewVector(partialTick);
                } else {
                    eyePos = player.getEyePosition(partialTick);
                    lookVec = player.getViewVector(partialTick);
                }
            } else {
                eyePos = player.getEyePosition(partialTick);
                lookVec = player.getViewVector(partialTick);
            }
        } else {
            eyePos = player.getEyePosition(partialTick);
            lookVec = player.getViewVector(partialTick);
        }
        
        Vector3d maxVec = eyePos.add(lookVec.x * RANGE, lookVec.y * RANGE, lookVec.z * RANGE);
        AxisAlignedBB aabb = viewEntity.getBoundingBox().expandTowards(lookVec.scale(RANGE)).inflate(1.0D, 1.0D, 1.0D);
        
        final Entity finalViewEntity = viewEntity;
        final LivingEntity finalPlayer = player;
        EntityRayTraceResult result = ProjectileHelper.getEntityHitResult(
            viewEntity, 
            eyePos, 
            maxVec, 
            aabb, 
            entity -> {
                // 排除观察者和自己
                if (entity.isSpectator() || entity == finalPlayer || entity == finalViewEntity || entity instanceof com.babelmoth.rotp_ata.entity.FossilMothEntity) {
                    return false;
                }
                // 检查是否为生物实体
                if (entity instanceof LivingEntity) {
                    return entity.isPickable() && ((LivingEntity) entity).isAlive();
                }
                // 检查是否为物品实体
                if (entity instanceof ItemEntity) {
                    ItemEntity itemEntity = (ItemEntity) entity;
                    // 确保物品实体有效：未移除、物品不为空、未被其他飞蛾领取
                    return !itemEntity.removed 
                        && !itemEntity.getItem().isEmpty()
                        && !itemEntity.getPersistentData().getBoolean("ata_retrieved");
                }
                return false;
            }, 
            RANGE * RANGE
        );
        
        if (result != null) {
            return result.getEntity();
        }
        return null;
    }
}
