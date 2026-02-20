package com.babelmoth.rotp_ata.client.ui.marker;

import java.util.List;
import java.util.Optional;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.capability.SpearStuckProvider;
import com.babelmoth.rotp_ata.init.InitStands;
import com.github.standobyte.jojo.client.ui.actionshud.ActionsOverlayGui;
import com.github.standobyte.jojo.client.ui.marker.MarkerRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ResourceLocation;

public class SpearRecallMarker extends MarkerRenderer {

    public SpearRecallMarker(Minecraft mc) {
        super(new ResourceLocation(AddonMain.MOD_ID, "textures/item/thela_hun_ginjeet_spear_item.png"), mc);
        this.renderThroughBlocks = true;
    }

    @Override
    protected boolean shouldRender() {
        ActionsOverlayGui hud = ActionsOverlayGui.getInstance();
        return hud.showExtraActionHud(InitStands.THELA_HUN_GINJEET_RECALL.get());
    }

    @Override
    protected void updatePositions(List<MarkerInstance> list, float partialTick) {
        if (mc.player == null || mc.level == null) return;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity) entity;

            int stuckCount = living.getCapability(SpearStuckProvider.SPEAR_STUCK_CAPABILITY)
                    .map(cap -> cap.getSpearCount()).orElse(0);
            if (stuckCount > 0) {
                list.add(new MarkerInstance(
                        living.getPosition(partialTick).add(0, living.getBbHeight() + 0.5, 0),
                        true,
                        Optional.empty()
                ));
            }
        }
    }
}
