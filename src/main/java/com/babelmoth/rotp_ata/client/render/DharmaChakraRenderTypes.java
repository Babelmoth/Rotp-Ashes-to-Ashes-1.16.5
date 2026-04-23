package com.babelmoth.rotp_ata.client.render;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.RenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;

public class DharmaChakraRenderTypes extends RenderType {

    private DharmaChakraRenderTypes(String name, net.minecraft.client.renderer.vertex.VertexFormat format, int mode, int bufferSize,
            boolean affectCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectCrumbling, sortOnUpload, setupState, clearState);
    }

    public static RenderType dharmaChakraMain(ResourceLocation texture) {
        RenderType.State state = RenderType.State.builder()
                .setTextureState(new RenderState.TextureState(texture, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setDiffuseLightingState(NO_DIFFUSE_LIGHTING)
                .setAlphaState(DEFAULT_ALPHA)
                .setCullState(CULL)
                .setLightmapState(NO_LIGHTMAP)
                .setOverlayState(NO_OVERLAY)
                .createCompositeState(true);
        return create("dharma_chakra_main", DefaultVertexFormats.NEW_ENTITY, 7, 256, true, true, state);
    }

    private static final RenderState.CullState CULL_FRONT = new RenderState.CullState(true) {
        @Override
        public void setupRenderState() {
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glCullFace(GL11.GL_FRONT);
        }

        @Override
        public void clearRenderState() {
            GL11.glCullFace(GL11.GL_BACK);
        }
    };

    public static RenderType dharmaChakraOutline(ResourceLocation texture) {
        RenderType.State state = RenderType.State.builder()
                .setTextureState(new RenderState.TextureState(texture, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setDiffuseLightingState(NO_DIFFUSE_LIGHTING)
                .setAlphaState(DEFAULT_ALPHA)
                .setCullState(CULL_FRONT)
                .setLightmapState(NO_LIGHTMAP)
                .setOverlayState(NO_OVERLAY)
                .createCompositeState(true);
        return create("dharma_chakra_outline", DefaultVertexFormats.NEW_ENTITY, 7, 256, true, true, state);
    }
}
