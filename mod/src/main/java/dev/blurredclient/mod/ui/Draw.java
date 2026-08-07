package dev.blurredclient.mod.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Two drawing primitives the overlay needs and {@code DrawContext} doesn't have:
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
    // Text
    // ------------------------------------------------------------------

    /** Width {@code text} will occupy when drawn at {@code scale}. */
    public static int width(TextRenderer font, Text text, float scale) {
        return Math.round(font.getWidth(text) * scale);
    }

    /** Height of a line of text at {@code scale}. */
    public static int height(TextRenderer font, float scale) {
        return Math.round(font.fontHeight * scale);
    }

    /**
     * Draw text with a shadow, scaled about its top-left corner.
     *
     * <p>The origin is translated as a whole number before the scale is applied,
     * so the glyph grid still starts on a pixel boundary — half a pixel of
     * offset here is the difference between crisp text and a smear.
     */
    public static void text(
            DrawContext ctx, TextRenderer font, Text text, int x, int y, int color, float scale) {
        if (scale == 1f) {
            ctx.drawTextWithShadow(font, text, x, y, color);
            return;
        }

        //? if >=1.21.8 {
        var matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate((float) x, (float) y);
        matrices.scale(scale, scale);
        ctx.drawTextWithShadow(font, text, 0, 0, color);
        matrices.popMatrix();
        //?} else {
        /*var matrices = ctx.getMatrices();
        matrices.push();
        matrices.translate((float) x, (float) y, 0f);
        matrices.scale(scale, scale, 1f);
        ctx.drawTextWithShadow(font, text, 0, 0, color);
        matrices.pop();
        *///?}
    }

    // ------------------------------------------------------------------
    // Shapes
    // ------------------------------------------------------------------

    /**
     * How far row {@code row} of an {@code h}-tall box is inset by a corner of
     * radius {@code r}.
     *
     * <p>Rows in the straight middle section return 0. Rows inside a corner
     * return the radius minus the half-chord of the corner circle at that row,
     * measured through the centre of the pixel — which is what stops the
     * topmost row from being drawn full width and giving the box a flat, clipped
     * look instead of a curve.
     *
     * <p>Public so callers can align an edge highlight or a border to the same
     * curve as the fill.
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

    /** A filled rectangle with rounded corners of radius {@code r}. */
    public static void roundedRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        for (int row = 0; row < h; row++) {
            int in = inset(row, h, r);
            if (in * 2 >= w) {
                continue;
            }
            ctx.fill(x + in, y + row, x + w - in, y + row + 1, color);
        }
    }

    /**
     * A one-pixel outline following the same curve as {@link #roundedRect}.
     *
     * <p>Drawn per row from the fill's insets rather than as four sides plus
     * corner pixels, so the outline can never drift off the shape it's tracing.
     */
    public static void roundedOutline(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        for (int row = 0; row < h; row++) {
            int in = inset(row, h, r);
            if (in * 2 >= w) {
                continue;
            }
            int left = x + in;
            int right = x + w - in;

            // A row whose neighbours are inset further is part of the curve, so
            // the whole span between them belongs to the edge.
            int above = row == 0 ? in + r : inset(row - 1, h, r);
            int below = row == h - 1 ? in + r : inset(row + 1, h, r);
            int step = Math.max(above, below) - in;

            if (step > 0) {
                ctx.fill(left, y + row, Math.min(right, left + step), y + row + 1, color);
                ctx.fill(Math.max(left, right - step), y + row, right, y + row + 1, color);
            }
            ctx.fill(left, y + row, left + 1, y + row + 1, color);
            ctx.fill(right - 1, y + row, right, y + row + 1, color);
        }
    }
}
