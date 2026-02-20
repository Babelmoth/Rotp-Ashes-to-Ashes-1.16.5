package com.babelmoth.rotp_ata.client.render.item;

import com.mojang.blaze3d.matrix.MatrixStack;

import com.babelmoth.rotp_ata.init.InitItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.tileentity.ItemStackTileEntityRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.ListNBT;
import com.github.standobyte.jojo.client.ClientUtil;

public class ThelaHunGinjeetSpearISTER extends ItemStackTileEntityRenderer {

    @Override
    public void renderByItem(ItemStack stack, ItemCameraTransforms.TransformType transformType, MatrixStack matrixStack,
            IRenderTypeBuffer buffer, int packedLight, int packedOverlay) {
        // 非替身使者不可见（GUI图标除外，由物品模型属性控制）
        if (!ClientUtil.canSeeStands() && transformType != ItemCameraTransforms.TransformType.GUI) return;
        ItemRenderer renderer = Minecraft.getInstance().getItemRenderer();

        // MC 的 ItemRenderer.render() 在调用 ISTER 前已做 translate(-0.5, -0.5, -0.5)
        // 需要先反向补偿，再让 renderStatic 内部重新走完整的 display 变换流程
        matrixStack.pushPose();
        matrixStack.translate(0.5F, 0.5F, 0.5F);

        if (transformType == ItemCameraTransforms.TransformType.GUI) {
            ItemStack sprite = new ItemStack(InitItems.THELA_HUN_GINJEET_SPEAR_SPRITE.get());
            // 复制附魔标签以显示附魔光效
            copyEnchantTags(stack, sprite);
            // Force full brightness for GUI to prevent dark inventory icon
            renderer.renderStatic(sprite, transformType, 15728880, OverlayTexture.NO_OVERLAY, matrixStack, buffer);
        } else {
            boolean isThrowing = Minecraft.getInstance().player != null
                    && Minecraft.getInstance().player.isUsingItem()
                    && Minecraft.getInstance().player.getUseItem() == stack;

            if (isThrowing) {
                // 蓄力时使用 throwing 辅助物品（其 item model 有矛头翻转 display）
                ItemStack throwingStack = new ItemStack(InitItems.THELA_HUN_GINJEET_SPEAR_THROWING.get());
                copyEnchantTags(stack, throwingStack);
                renderer.renderStatic(throwingStack, transformType, packedLight, packedOverlay, matrixStack, buffer);
            } else {
                ItemStack modelStack = new ItemStack(InitItems.THELA_HUN_GINJEET_SPEAR_RENDER.get());
                copyEnchantTags(stack, modelStack);
                renderer.renderStatic(modelStack, transformType, packedLight, packedOverlay, matrixStack, buffer);
            }
        }

        matrixStack.popPose();
    }

    /**
     * 将原始长矛的附魔标签复制到代理渲染物品，以显示附魔光效
     */
    private static void copyEnchantTags(ItemStack source, ItemStack target) {
        if (source.isEnchanted()) {
            ListNBT enchantList = source.getEnchantmentTags();
            if (enchantList != null && !enchantList.isEmpty()) {
                target.getOrCreateTag().put("Enchantments", enchantList.copy());
            }
        }
    }
}
