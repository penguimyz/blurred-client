package dev.blurredclient.mod.ui.kit;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

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
    public static void blit(DrawContext ctx, Identifier id, int x, int y, int w, int h,
                            float u, float v, int regionW, int regionH, int texW, int texH, int argb) {
        //? if >=1.21.8 {
        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h,
                regionW, regionH, texW, texH, argb);
        //?} elif >=1.21.4 {
        /*ctx.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, id, x, y, u, v, w, h,
                regionW, regionH, texW, texH, argb);
        *///?} else {
        /*com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(((argb >> 16) & 0xFF) / 255f,
                ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f);
        ctx.drawTexture(id, x, y, w, h, u, v, regionW, regionH, texW, texH);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        *///?}
    }

    /** Set one pixel, given as ARGB. (1.21.1 only has the ABGR setter.) */
    public static void setPixel(NativeImage image, int x, int y, int argb) {
        //? if >=1.21.4 {
        image.setColorArgb(x, y, argb);
        //?} else {
        /*int abgr = (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
        image.setColor(x, y, abgr);
        *///?}
    }
}
