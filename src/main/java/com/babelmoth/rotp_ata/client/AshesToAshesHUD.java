package com.babelmoth.rotp_ata.client;

import com.babelmoth.rotp_ata.AddonMain;
import com.babelmoth.rotp_ata.capability.IMothPool;
import com.babelmoth.rotp_ata.capability.MothPoolProvider;
import com.babelmoth.rotp_ata.entity.FossilMothEntity;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.babelmoth.rotp_ata.util.MothQueryUtil;
import com.babelmoth.rotp_ata.util.AshesToAshesConstants;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AshesToAshesHUD {
    private static final Minecraft mc = Minecraft.getInstance();

    @SubscribeEvent
    public static void onRenderHUD(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.CROSSHAIRS) return;
        
        PlayerEntity player = mc.player;
        if (player == null) return;
        
        IStandPower.getStandPowerOptional(player).ifPresent(power -> {
            if (power.hasPower() && power.getType().getRegistryName().getNamespace().equals(AddonMain.MOD_ID) 
                && power.getType().getRegistryName().getPath().equals("ashes_to_ashes")) {
                
                player.getCapability(MothPoolProvider.MOTH_POOL_CAPABILITY).ifPresent(pool -> {
                    int total = pool.getTotalMoths();
                    int active = MothQueryUtil.getOwnerMoths(player, 128.0).size();
                    int consumed = AshesToAshesConstants.MAX_MOTHS - total;
                    int kinetic = pool.getTotalKineticEnergy();

                    MatrixStack stack = event.getMatrixStack();
                    int width = mc.getWindow().getGuiScaledWidth();
                    int height = mc.getWindow().getGuiScaledHeight();
                    
                    int x = width / 2 + 12;
                    int y = height / 2 - 4;
                    
                    // 1. Quantity Status (Grey)
                    String countText = String.format("%d / %d / %d", total, active, consumed);
                    mc.font.drawShadow(stack, countText, x, y, 0xAAAAAA);
                    
                    // 2. Kinetic Total (Amber: 0xe7801a)
                    String kineticText = String.format("%d KE", kinetic);
                    mc.font.drawShadow(stack, kineticText, x, y + 10, 0xe7801a);
                });
            }
        });
    }
}
