package dev.blurredclient.mod.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Two drawing primitives the overlay needs and {@code GuiGraphicsExtractor} doesn't have:
 * text at a size other than 8px, and a rectangle with actual corners.
 *
 * <h2>Scaled text</h2>
 *
 * Minecraft's font is one fixed size, so "make the HUD smaller" cannot be done
 * by asking for a smaller font — the only lever is the transform stack. Pushing
 * a scale around the draw call is what every client-side HUD does for this, and
 * it keeps the text sharp: the glyphs are still drawn from the same atlas, just
 * sampled at a lower rate.
 *
 * <p>The stack type changed in 1.21.8, when the GUI moved to a 2D transform
 * ({@code Matrix3x2fStack}) from the general 3D {@code MatrixStack}. That's the
 * only version-dependent line in here, which is the point of the class existing:
 * one branch, not one per call site.
 *
 * <h2>Rounded rectangles</h2>
 *
 * {@code fill} draws axis-aligned rectangles and nothing else, so a rounded box
 * is built row by row, each row inset by the horizontal distance from the
 * corner circle. Done properly — as a real chord length rather than a
 * hand-written ladder of one and two pixel insets — the same code gives a soft
 * 3px radius on a small panel and a true capsule on the HUD island, and every
 * shape in the client curves the same way.
 */
public final class Draw {
    private Draw() {}

    // ------------------------------------------------------------------
    // Component
    // ------------------------------------------------------------------

    /** Width {@code text} will occupy when drawn at {@code scale}. */
    public static int width(Font font, Component text, float scale) {
        return Math.round(font.width(text) * scale);
    }

    /** Height of a line of text at {@code scale}. */
    public static int height(Font font, float scale) {
        return Math.round(font.lineHeight * scale);
    }

    /**
     * Draw text with a shadow, scaled about its top-left corner.
     *
     * <p>The origin is translated as a whole number before the scale is applied,
     * so the glyph grid still starts on a pixel boundary — half a pixel of
     * offset here is the difference between crisp text and a smear.
     */
    public static void text(
            GuiGraphicsExtractor ctx, Font font, Component text, int x, int y, int color, float scale) {
        if (scale == 1f) {
            ctx.text(font, text, x, y, color);
            return;
        }

        var matrices = ctx.pose();
        matrices.pushMatrix();
        matrices.translate((float) x, (float) y);
        matrices.scale(scale, scale);
        ctx.text(font, text, 0, 0, color);
        matrices.popMatrix();
    }

    // ------------------------------------------------------------------
    // Shapes
    // ------------------------------------------------------------------

    /**
     * How far row {@code row} of an {@code h}-tall box is inset by a corner of
     * radius {@code r}.
     *
     * <p>Whole pixels, for callers that need to line a hard edge up with the
     * curve — the lit top edge of a panel, for instance. The curve itself is
     * drawn with coverage rather than insets; see {@link #roundedRect}.
     */
    public static int inset(int row, int h, int r) {
        r = Math.min(r, h / 2);
        if (r <= 0) {
            return 0;
        }

        int distance;
        if (row < r) {
            distance = r - 1 - row;
        } else if (row >= h - r) {
            distance = row - (h - r);
        } else {
            return 0;
        }

        double chord = Math.sqrt(Math.max(0d, (double) r * r - (distance + 0.5d) * (distance + 0.5d)));
        return (int) Math.round(r - chord);
    }

    /**
     * Scale a colour's alpha by a coverage fraction.
     *
     * <p>This is the whole trick behind the smooth edges below. Minecraft's
     * {@code fill} blends by alpha, so a pixel that a shape only half covers can
     * be drawn at half alpha and the edge stops being a staircase.
     */
    private static int shade(int color, double coverage) {
        int a = (int) Math.round(((color >>> 24) & 0xFF) * Math.max(0d, Math.min(1d, coverage)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /** One pixel, at partial coverage. */
    private static void pixel(GuiGraphicsExtractor ctx, int x, int y, int color, double coverage) {
        if (coverage <= 0.004d) {
            return;
        }
        ctx.fill(x, y, x + 1, y + 1, coverage >= 0.996d ? color : shade(color, coverage));
    }

    /**
     * The quarter-disc in one corner, drawn pixel by pixel with coverage.
     *
     * @param cx,cy centre of the corner circle, in continuous coordinates
     * @param px,py top-left of the r*r pixel block to fill
     */
    private static void cornerArc(
            GuiGraphicsExtractor ctx, double cx, double cy, int px, int py, int r, int color) {
        for (int oy = 0; oy < r; oy++) {
            for (int ox = 0; ox < r; ox++) {
                double dx = px + ox + 0.5d - cx;
                double dy = py + oy + 0.5d - cy;
                // Coverage as the signed distance from the edge, clamped to a
                // one-pixel band. Cheap, and at these radii indistinguishable
                // from proper area sampling.
                pixel(ctx, px + ox, py + oy, color, r - Math.sqrt(dx * dx + dy * dy) + 0.5d);
            }
        }
    }

    /**
     * A filled rectangle with genuinely round corners.
     *
     * <p>The straight parts are three solid fills; only the four corner blocks
     * are touched per pixel, so the cost is {@code 4 * r^2} regardless of how
     * big the panel is.
     *
     * <p>This used to inset each row by a whole number of pixels, which is the
     * obvious way to do it with nothing but {@code fill} — and it looks like a
     * rectangle with its corners bitten off in steps rather than a curve. At
     * the radii the UI actually uses, three or four pixels, the staircase is
     * most of the corner.
     */
    public static void roundedRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }

        ctx.fill(x, y + r, x + w, y + h - r, color);
        ctx.fill(x + r, y, x + w - r, y + r, color);
        ctx.fill(x + r, y + h - r, x + w - r, y + h, color);

        cornerArc(ctx, x + r, y + r, x, y, r, color);
        cornerArc(ctx, x + w - r, y + r, x + w - r, y, r, color);
        cornerArc(ctx, x + r, y + h - r, x, y + h - r, r, color);
        cornerArc(ctx, x + w - r, y + h - r, x + w - r, y + h - r, r, color);
    }

    /** A one-pixel outline following the same curve as {@link #roundedRect}. */
    public static void roundedOutline(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));

        // Straight runs between the corners.
        ctx.fill(x + r, y, x + w - r, y + 1, color);
        ctx.fill(x + r, y + h - 1, x + w - r, y + h, color);
        ctx.fill(x, y + r, x + 1, y + h - r, color);
        ctx.fill(x + w - 1, y + r, x + w, y + h - r, color);
        if (r == 0) {
            return;
        }

        cornerRing(ctx, x + r, y + r, x, y, r, color);
        cornerRing(ctx, x + w - r, y + r, x + w - r, y, r, color);
        cornerRing(ctx, x + r, y + h - r, x, y + h - r, r, color);
        cornerRing(ctx, x + w - r, y + h - r, x + w - r, y + h - r, r, color);
    }

    /** The arc of a corner outline, one pixel thick, anti-aliased. */
    private static void cornerRing(
            GuiGraphicsExtractor ctx, double cx, double cy, int px, int py, int r, int color) {
        for (int oy = 0; oy < r; oy++) {
            for (int ox = 0; ox < r; ox++) {
                double dx = px + ox + 0.5d - cx;
                double dy = py + oy + 0.5d - cy;
                double d = Math.sqrt(dx * dx + dy * dy);
                pixel(ctx, px + ox, py + oy, color, 1d - Math.abs(d - (r - 0.5d)));
            }
        }
    }

    /**
     * A filled circle.
     *
     * <p>Same coverage approach as the corners, which is what makes a 5px dot
     * read as round instead of as a plus sign.
     */
    public static void circle(GuiGraphicsExtractor ctx, int cx, int cy, double r, int color) {
        int min = (int) Math.floor(cx - r);
        int max = (int) Math.ceil(cx + r);
        int top = (int) Math.floor(cy - r);
        int bottom = (int) Math.ceil(cy + r);

        for (int py = top; py <= bottom; py++) {
            for (int px = min; px <= max; px++) {
                double dx = px + 0.5d - cx;
                double dy = py + 0.5d - cy;
                pixel(ctx, px, py, color, r - Math.sqrt(dx * dx + dy * dy) + 0.5d);
            }
        }
    }

    /** A circular ring of the given thickness, anti-aliased on both edges. */
    public static void ring(GuiGraphicsExtractor ctx, int cx, int cy, double r, double thickness, int color) {
        int min = (int) Math.floor(cx - r) - 1;
        int max = (int) Math.ceil(cx + r) + 1;
        int top = (int) Math.floor(cy - r) - 1;
        int bottom = (int) Math.ceil(cy + r) + 1;
        double inner = Math.max(0d, r - thickness);

        for (int py = top; py <= bottom; py++) {
            for (int px = min; px <= max; px++) {
                double dx = px + 0.5d - cx;
                double dy = py + 0.5d - cy;
                double d = Math.sqrt(dx * dx + dy * dy);
                // Inside the outer edge and outside the inner one, each edge
                // softened over a single pixel.
                double coverage = Math.min(r - d + 0.5d, d - inner + 0.5d);
                pixel(ctx, px, py, color, coverage);
            }
        }
    }
}
