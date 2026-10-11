package dev.blurredclient.mod.ui;

import dev.blurredclient.mod.BlurredMod;
import dev.blurredclient.mod.ui.kit.Tex;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Anti-aliased text, rasterized by Java's own font engine.
 *
 * <h2>Why not Minecraft's TTF fonts</h2>
 *
 * Minecraft can load TTF fonts, but its text shader discards pixels below a
 * small alpha. The soft edge pixels that make anti-aliased text look smooth
 * are exactly those, so they get cut — thin strokes break up and letters look
 * like they have pixels missing, at any size.
 *
 * <p>Here each glyph is drawn once with Java2D (grayscale anti-aliasing,
 * fractional metrics — desktop-quality text) into a white-on-transparent
 * atlas texture per pixel size, then drawn as a textured quad through the
 * normal GUI image pipeline, which blends every alpha level. Text is always
 * drawn at its real on-screen pixel size, at whole-pixel positions, so each
 * glyph pixel lands on one screen pixel.
 *
 * <p>Glyphs are added to the atlas the first time they're seen and the
 * texture is re-uploaded before the next draw. Characters the UI font lacks
 * fall back to Java's sans-serif.
 */
public final class SmoothFont {
    private SmoothFont() {}

    private static final String TTF = "/assets/blurred/font/varela_round.ttf";
    /** Glyph coverage is lifted slightly so a light face holds up at small sizes. */
    private static final double GAMMA = 0.8;

    private static Font base;
    private static boolean baseFailed;
    private static final Map<Integer, Atlas> ATLASES = new HashMap<>();

    private static Font base() {
        if (base == null && !baseFailed) {
            try (InputStream in = SmoothFont.class.getResourceAsStream(TTF)) {
                base = Font.createFont(Font.TRUETYPE_FONT, in);
            } catch (Exception e) {
                baseFailed = true;
                BlurredMod.LOGGER.warn("Could not load UI font, using Java's sans-serif", e);
            }
        }
        return base != null ? base : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    }

    private static Atlas atlas(int px) {
        int n = Math.max(5, Math.min(96, px));
        return ATLASES.computeIfAbsent(n, Atlas::new);
    }

    /** Width in pixels of {@code s} at {@code px} pixels. */
    public static float width(String s, int px) {
        Atlas a = atlas(px);
        float w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            w += a.glyph(cp).advance;
        }
        return w;
    }

    /** Ascent and descent in pixels, for vertical placement. */
    public static float ascent(int px) {
        return atlas(px).ascent;
    }

    public static float descent(int px) {
        return atlas(px).descent;
    }

    /**
     * Draw {@code s} at {@code px} pixels with its baseline at {@code baseline},
     * starting at {@code x}. Coordinates are screen pixels — call inside the
     * pixel-space transform.
     */
    public static void draw(DrawContext ctx, String s, int x, int baseline, int px, int argb) {
        Atlas a = atlas(px);
        // Rasterize everything first, so one upload covers the whole string.
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            a.glyph(cp);
        }
        a.flush();
        float pen = x;
        // One GUI element for the whole string, not one per glyph (see GuiBatch).
        //? if >=1.21.8 {
        Object batch = GuiBatch.beginText(ctx);
        //?}
        float side = a.side;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            Glyph g = a.glyph(cp);
            if (g.w > 0) {
                int gx = Math.round(pen) + g.ox;
                int gy = baseline + g.oy;
                //? if >=1.21.8 {
                if (!GuiBatch.glyph(gx, gy, gx + g.w, gy + g.h, g.u / side, g.v / side,
                        (g.u + g.w) / side, (g.v + g.h) / side, argb)) {
                    Tex.blit(ctx, a.id, gx, gy, g.w, g.h, g.u, g.v, g.w, g.h, a.side, a.side, argb);
                }
                //?} else {
                /*Tex.blit(ctx, a.id, gx, gy, g.w, g.h, g.u, g.v, g.w, g.h, a.side, a.side, argb);
                *///?}
            }
            pen += g.advance;
        }
        //? if >=1.21.8 {
        GuiBatch.endText(batch, a.texture, a.id);
        //?}
    }

    /** One rasterized glyph: its atlas region, its offset from the pen at the baseline, its advance. */
    private record Glyph(int u, int v, int w, int h, int ox, int oy, float advance) {}

    /** All glyphs at one pixel size, packed into one texture in rows. */
    private static final class Atlas {
        private final int side;
        private final Font font;
        private final Font fallback;
        private final FontRenderContext frc = new FontRenderContext(null, true, true);
        private final NativeImage image;
        private final NativeImageBackedTexture texture;
        private final Identifier id;
        private final Map<Integer, Glyph> glyphs = new HashMap<>();
        private final float ascent;
        private final float descent;
        private int penX = 1;
        private int penY = 1;
        private int rowH;
        private boolean dirty;

        Atlas(int px) {
            this.side = Math.max(256, Math.min(1024, Integer.highestOneBit(px * 12 - 1) << 1));
            this.font = base().deriveFont((float) px);
            this.fallback = new Font(Font.SANS_SERIF, Font.PLAIN, px);
            LineMetrics lm = font.getLineMetrics("Hgj", frc);
            this.ascent = lm.getAscent();
            this.descent = lm.getDescent();
            this.image = new NativeImage(side, side, true);
            this.id = Identifier.of(BlurredMod.MOD_ID, "ui/text_" + px);
            //? if >=1.21.8 {
            this.texture = new NativeImageBackedTexture(() -> "blurred text " + px, image);
            //?} else {
            /*this.texture = new NativeImageBackedTexture(image);
            *///?}
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        }

        Glyph glyph(int cp) {
            Glyph g = glyphs.get(cp);
            if (g == null) {
                g = rasterize(cp);
                glyphs.put(cp, g);
            }
            return g;
        }

        void flush() {
            if (dirty) {
                texture.upload();
                dirty = false;
            }
        }

        private Glyph rasterize(int cp) {
            Font f = font.canDisplay(cp) ? font : fallback;
            GlyphVector gv = f.createGlyphVector(frc, new String(Character.toChars(cp)));
            float advance = (float) gv.getGlyphMetrics(0).getAdvanceX();
            Rectangle b = gv.getPixelBounds(frc, 0, 0);
            if (b.width <= 0 || b.height <= 0) {
                return new Glyph(0, 0, 0, 0, 0, 0, advance);
            }
            int w = b.width + 2;
            int h = b.height + 2;
            if (penX + w + 1 > side) {
                penX = 1;
                penY += rowH + 1;
                rowH = 0;
            }
            if (penY + h + 1 > side) {
                // Full: start over. Rare (it takes hundreds of distinct glyphs at
                // one size), and everything re-rasterizes on demand.
                clear();
            }

            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setColor(Color.WHITE);
            g2.drawGlyphVector(gv, 1 - b.x, 1 - b.y);
            g2.dispose();

            for (int yy = 0; yy < h; yy++) {
                for (int xx = 0; xx < w; xx++) {
                    int a = img.getRGB(xx, yy) >>> 24;
                    if (a > 0) {
                        a = (int) Math.round(Math.pow(a / 255.0, GAMMA) * 255.0);
                    }
                    Tex.setPixel(image, penX + xx, penY + yy, (a << 24) | 0x00FFFFFF);
                }
            }
            Glyph g = new Glyph(penX, penY, w, h, b.x - 1, b.y - 1, advance);
            penX += w + 1;
            rowH = Math.max(rowH, h);
            dirty = true;
            return g;
        }

        private void clear() {
            for (int yy = 0; yy < side; yy++) {
                for (int xx = 0; xx < side; xx++) {
                    Tex.setPixel(image, xx, yy, 0);
                }
            }
            glyphs.clear();
            penX = 1;
            penY = 1;
            rowH = 0;
        }
    }
}
