package com.babelmoth.rotp_ata.client.ui.marker;

import java.util.List;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.init.InitStands;
import com.github.standobyte.jojo.client.ui.actionshud.ActionsOverlayGui;
import com.github.standobyte.jojo.client.ui.marker.MarkerRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

public class MothExfoliatingDetonationMarker extends MarkerRenderer {

    public MothExfoliatingDetonationMarker(Minecraft mc) {
        super(new ResourceLocation(AddonMain.MOD_ID, "textures/action/ashes_to_ashes_exfoliating_detonation.png"), mc);
        this.renderThroughBlocks = false;
    }

    @Override
    protected boolean shouldRender() {
        ActionsOverlayGui hud = ActionsOverlayGui.getInstance();
        return hud.showExtraActionHud(InitStands.ASHES_TO_ASHES_EXFOLIATING_DETONATION.get());
    }

    @Override
    protected void updatePositions(List<MarkerInstance> list, float partialTick) {
        // Exfoliating Detonation: Highlight Attached Moths WITH ENERGY
        // Logic identical to Kinetic Detonation, but renders with different icon because class texture is different.
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof com.babelmoth.rotp_ata.entity.FossilMothEntity) {
                com.babelmoth.rotp_ata.entity.FossilMothEntity moth = (com.babelmoth.rotp_ata.entity.FossilMothEntity) entity;
                boolean isOwner = false;
                try {
                    // Check owner UUID matching client player
                    java.util.UUID ownerUUID = moth.getOwnerUUID();
                    if (ownerUUID != null && ownerUUID.equals(mc.player.getUUID())) {
                        isOwner = true;
                    }
                } catch (Exception e) {}
                
                // Only mark moths with energy that are attached or have visited a position
                if (isOwner && moth.getKineticEnergy() > 0 &&
                   (moth.isAttachedToEntity() || moth.getAttachedPos() != null || moth.getEntityData().get(com.babelmoth.rotp_ata.entity.FossilMothEntity.ATTACHED_FACE) != -1)) {
                     list.add(new MarkerInstance(
                        moth.getPosition(partialTick).add(0, 0.5, 0),
                        true,
                        java.util.Optional.empty()
                    ));
                }
            }
        }
    }
}
