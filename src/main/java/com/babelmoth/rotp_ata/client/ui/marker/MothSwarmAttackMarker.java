package com.babelmoth.rotp_ata.client.ui.marker;

import java.util.List;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.init.InitStands;
import com.github.standobyte.jojo.client.ui.actionshud.ActionsOverlayGui;
import com.github.standobyte.jojo.client.ui.marker.MarkerRenderer;
import com.github.standobyte.jojo.util.mod.JojoModUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;

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
        Entity viewEntity = player;

        RayTraceResult rtResult = JojoModUtil.rayTrace(viewEntity, RANGE,
            entity -> {
                if (entity.isSpectator() || entity == player || entity == viewEntity || entity instanceof com.babelmoth.rotp_ata.entity.FossilMothEntity) {
                    return false;
                }
                if (entity instanceof LivingEntity) {
                    return entity.isPickable() && ((LivingEntity) entity).isAlive();
                }
                if (entity instanceof ItemEntity) {
                    ItemEntity itemEntity = (ItemEntity) entity;
                    return !itemEntity.removed
                        && !itemEntity.getItem().isEmpty()
                        && !itemEntity.getPersistentData().getBoolean("ata_retrieved");
                }
                return false;
            });

        if (rtResult instanceof EntityRayTraceResult) {
            return ((EntityRayTraceResult) rtResult).getEntity();
        }
        return null;
    }
}