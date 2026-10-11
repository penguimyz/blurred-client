package dev.blurredclient.mod.ui.kit;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Drawing part of a texture in the GUI, tinted — the one call whose shape
 * changed in every era of the supported versions: a plain call with the
 * shader colour as tint on 1.21.1, a render-layer function on 1.21.4, a
 * render pipeline from 1.21.8 (all checked with javap). Callers never branch.
 */
public final class Tex {
    private Tex() {}

    /**
     * Draw the {@code regionW x regionH} region at {@code u, v} of a
     * {@code texW x texH} texture into {@code w x h} at {@code x, y}, tinted
     * by {@code argb}.
     */
    public static void blit(GuiGraphicsExtractor ctx, Identifier id, int x, int y, int w, int h,
                            float u, float v, int regionW, int regionH, int texW, int texH, int argb) {
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h,
                regionW, regionH, texW, texH, argb);
    }

    /** Set one pixel, given as ARGB. (1.21.1 only has the ABGR setter.) */
    public static void setPixel(NativeImage image, int x, int y, int argb) {
        image.setPixel(x, y, argb);
    }
}
