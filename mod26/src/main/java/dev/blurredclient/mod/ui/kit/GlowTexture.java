package dev.blurredclient.mod.ui.kit;

import com.mojang.blaze3d.platform.NativeImage;
import dev.blurredclient.mod.BlurredMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * A soft round glow, drawn as one stretched, tinted texture.
 *
 * <p>The background's colour glows used to be dozens of stacked translucent
 * circles built from row fills — tens of thousands of fills a frame, which
 * cost real frame rate (from 1.21.6 every fill is its own render object).
 * A radial gradient is exactly what a texture is for: generate a 256x256
 * white falloff once, then draw it scaled and tinted. One quad per glow.
 *
 * <p>Drawing textures in the GUI changed twice across the supported versions
 * (plain call in 1.21.1, a render-layer function in 1.21.4, a render
 * pipeline from 1.21.8), and so did turning on smooth filtering (a sampler
 * object in 1.21.11). All of that is in this class.
 */
public final class GlowTexture {
    private GlowTexture() {}

    private static final int SIZE = 256;
    private static final Identifier ID = Identifier.fromNamespaceAndPath(BlurredMod.MOD_ID, "ui/glow");
    private static boolean registered;

    /** A texture that samples with linear filtering, so the stretched glow stays smooth. */
    private static final class Smooth extends DynamicTexture {
        Smooth(NativeImage image) {
            super(() -> "blurred glow", image);
            this.sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
                    .getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);
        }
    }

    private static void ensure() {
        if (registered) {
            return;
        }
        registered = true;
        NativeImage image = new NativeImage(SIZE, SIZE, true);
        float c = (SIZE - 1) / 2f;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float dx = (x - c) / c;
                float dy = (y - c) / c;
                float d2 = dx * dx + dy * dy;
                // Gaussian-ish falloff, forced to exactly zero at the edge so
                // the square texture never shows its border.
                float a = d2 >= 1f ? 0f : (float) Math.exp(-d2 * 3.2) * (1f - d2);
                int alpha = Math.round(Math.max(0f, Math.min(1f, a)) * 255f);
                // White, so the same value is right for ABGR and ARGB.
                int pixel = (alpha << 24) | 0x00FFFFFF;
                image.setPixel(x, y, pixel);
            }
        }
        DynamicTexture texture = new Smooth(image);
        Minecraft.getInstance().getTextureManager().register(ID, texture);
    }

    /** Draw a glow of {@code radius} units centred on {@code cx, cy}, tinted {@code argb}. */
    public static void draw(GuiGraphicsExtractor ctx, float cx, float cy, float radius, int argb) {
        ensure();
        int x = Math.round(cx - radius);
        int y = Math.round(cy - radius);
        int d = Math.max(1, Math.round(radius * 2));
        Tex.blit(ctx, ID, x, y, d, d, 0f, 0f, SIZE, SIZE, SIZE, SIZE, argb);
    }
}
