package dev.blurredclient.mod.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayDeque;

/**
 * The drawing primitives every Blurred screen and HUD module is built from.
 *
 * <h2>Why shapes are drawn in screen pixels</h2>
 *
 * {@code DrawContext.fill} only takes whole GUI units. At GUI scale 4 a unit
 * is four screen pixels, and in a kit screen it's about 1.6 — either way, a
 * curve built from whole-unit rows is a staircase, and a unit that isn't a
 * whole number of pixels makes edges land between pixels and come out
 * uneven. That is what made the menus look pixelated.
 *
 * <p>So every shape here (rounded rects, circles, rings, outlines, plain
 * rects, lines) switches to <b>screen-pixel space</b> while it draws: it
 * scales the transform down by the current pixels-per-unit and works in real
 * pixels, with each edge pixel drawn at the alpha of how much of it the true
 * shape covers. The result is smooth at any GUI scale and any menu size.
 *
 * <p>Pixels-per-unit is tracked on a stack: the window's GUI scale at the
 * bottom, multiplied by every {@link #scaled} region on top (a kit screen's
 * own scale, a resized HUD module).
 *
 * <p>The transform type changed in 1.21.8 ({@code Matrix3x2fStack} replaced
 * the 3D {@code MatrixStack} for GUI drawing); those branches live here and
 * nowhere else.
 */
public final class Draw {
    private Draw() {}

    // ------------------------------------------------------------------
    // Pixels per unit
    // ------------------------------------------------------------------

    private static final ArrayDeque<Float> PX = new ArrayDeque<>();

    /** Screen pixels per drawing unit at this point in the frame. */
    public static float pixelsPerUnit() {
        if (!PX.isEmpty()) {
            return PX.peek();
        }
        return (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
    }

    // ------------------------------------------------------------------
    // Transforms (the only version-dependent code)
    // ------------------------------------------------------------------

    public static void push(DrawContext ctx) {
        //? if >=1.21.8 {
        ctx.getMatrices().pushMatrix();
        //?} else {
        /*ctx.getMatrices().push();
        *///?}
    }

    public static void pop(DrawContext ctx) {
        //? if >=1.21.8 {
        ctx.getMatrices().popMatrix();
        //?} else {
        /*ctx.getMatrices().pop();
        *///?}
    }

    public static void translate(DrawContext ctx, float x, float y) {
        //? if >=1.21.8 {
        ctx.getMatrices().translate(x, y);
        //?} else {
        /*ctx.getMatrices().translate(x, y, 0f);
        *///?}
    }

    private static void scale(DrawContext ctx, float s) {
        //? if >=1.21.8 {
        ctx.getMatrices().scale(s, s);
        //?} else {
        /*ctx.getMatrices().scale(s, s, 1f);
        *///?}
    }

    private static void rotate(DrawContext ctx, float radians) {
        //? if >=1.21.8 {
        ctx.getMatrices().rotate(radians);
        //?} else {
        /*ctx.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotation(radians));
        *///?}
    }

    /**
     * Run {@code body} with the origin moved to {@code x, y} and everything
     * scaled by {@code scale} — a kit screen's own scale, a resized HUD module.
     * The body draws as if at 0,0 and full size.
     */
    public static void scaled(DrawContext ctx, int x, int y, float scale, Runnable body) {
        push(ctx);
        translate(ctx, x, y);
        scale(ctx, scale);
        PX.push(pixelsPerUnit() * scale);
        try {
            body.run();
        } finally {
            PX.pop();
            pop(ctx);
        }
    }

    /** Callback for {@link #inPixels}: receives pixels-per-unit. */
    private interface PixelBody {
        void draw(float k);
    }

    /**
     * Draw in screen pixels: the body gets {@code k} (pixels per current unit)
     * and multiplies its unit coordinates by it.
     */
    private static void inPixels(DrawContext ctx, PixelBody body) {
        float k = pixelsPerUnit();
        if (Math.abs(k - 1f) < 0.001f) {
            body.draw(1f);
            return;
        }
        push(ctx);
        scale(ctx, 1f / k);
        PX.push(1f);
        try {
            body.draw(k);
        } finally {
            PX.pop();
            pop(ctx);
        }
    }

    // ------------------------------------------------------------------
    // Text
    // ------------------------------------------------------------------

    /** Width {@code text} will occupy when drawn at {@code scale}. */
    public static int width(TextRenderer font, Text text, float scale) {
        if (smoothStyle()) {
            return smoothWidth(font, text.getString(), scale);
        }
        return Math.round(font.getWidth(text) * scale);
    }

    /** True for the Modern and Glass styles, whose text goes through {@link #smoothText}. */
    private static boolean smoothStyle() {
        return dev.blurredclient.mod.ui.kit.Skins.current() != dev.blurredclient.mod.ui.kit.PixelSkin.INSTANCE;
    }

    /** Height of a line of text at {@code scale}. */
    public static int height(TextRenderer font, float scale) {
        return Math.round(font.fontHeight * scale);
    }

    /**
     * Draw text scaled about its top-left corner. The pixel-art style keeps
     * Minecraft's hard drop shadow; the smooth styles draw without one,
     * because a one-unit offset shadow under antialiased text is itself a
     * pixel-art effect — at GUI scale 4 it's a 4px block behind every letter.
     */
    public static void text(DrawContext ctx, TextRenderer font, Text text, int x, int y, int color, float scale) {
        if (smoothStyle()) {
            smoothText(ctx, font, text.getString(), x, y, color, scale);
            return;
        }
        boolean shadow = true;
        if (scale == 1f) {
            ctx.drawText(font, text, x, y, color, shadow);
            return;
        }
        push(ctx);
        translate(ctx, x, y);
        scale(ctx, scale);
        PX.push(pixelsPerUnit() * scale);
        try {
            ctx.drawText(font, text, 0, 0, color, shadow);
        } finally {
            PX.pop();
            pop(ctx);
        }
    }

    // ------------------------------------------------------------------
    // Smooth text, pixel-exact
    // ------------------------------------------------------------------

    /** Em size of the smooth face, in drawing units (matches the old 8-unit TTF size). */
    private static final float EM = 8f;

    /** The pixel size the smooth face should be at, for text at {@code scale}. */
    private static int pixelSize(float scale) {
        return Math.round(EM * pixelsPerUnit() * scale);
    }

    /**
     * Width, in current units, of {@code s} drawn by {@link #smoothText} at
     * {@code scale}. Measured at the pixel size that will actually be drawn,
     * so layout matches what appears.
     */
    public static int smoothWidth(TextRenderer font, String s, float scale) {
        float k = pixelsPerUnit();
        return Math.round(SmoothFont.width(s, pixelSize(scale)) / k);
    }

    /**
     * Draw {@code s} in the UI face with real anti-aliasing (see
     * {@link SmoothFont}): switches to screen-pixel space and draws at the
     * true on-screen pixel size, baseline on a whole pixel. {@code x, y} is
     * the top of a 9-unit text line, as for vanilla text, so it's a drop-in
     * for {@code drawText}; the text is centred vertically in that line.
     */
    public static void smoothText(DrawContext ctx, TextRenderer font, String s, float x, float y, int color, float scale) {
        if (s.isEmpty() || Colors.alpha(color) == 0) {
            return;
        }
        int n = pixelSize(scale);
        inPixels(ctx, k -> {
            float lineTop = y * k;
            float lineH = 9f * k * scale;
            float asc = SmoothFont.ascent(n);
            float desc = SmoothFont.descent(n);
            int baseline = Math.round(lineTop + (lineH - (asc + desc)) / 2f + asc);
            SmoothFont.draw(ctx, s, Math.round(x * k), baseline, n, color);
        });
    }

    // ------------------------------------------------------------------
    // Shapes (all anti-aliased, all drawn in screen pixels)
    // ------------------------------------------------------------------

    /**
     * How far row {@code row} of an {@code h}-tall box is inset by a corner of
     * radius {@code r}, in whole units. Kept for the pixel-art style, which
     * wants exactly this staircase.
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

    /** A plain rectangle, like {@code ctx.fill}, but with sub-pixel-accurate edges. */
    public static void rect(DrawContext ctx, float x1, float y1, float x2, float y2, int color) {
        roundedRectF(ctx, Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1), 0f, color);
    }

    /** A filled rectangle with rounded corners of radius {@code r}. */
    public static void roundedRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        roundedRectF(ctx, x, y, w, h, r, color);
    }

    /**
     * Whether rounded shapes keep their radius. False when the Corners
     * setting is Square: every "rounded" rect and outline is then drawn with
     * square corners, so one switch squares off the whole UI. Circles are
     * exempt — a particle or a status dot is meant to be round.
     */
    private static boolean roundCorners() {
        return dev.blurredclient.mod.module.Modules.APPEARANCE.rounded();
    }

    /** A rounded rectangle at sub-unit position and size. */
    public static void roundedRectF(DrawContext ctx, float x, float y, float w, float h, float r, int color) {
        if (w <= 0 || h <= 0 || Colors.alpha(color) == 0) {
            return;
        }
        float radius = roundCorners() ? r : 0f;
        inPixels(ctx, k -> pxRoundedRect(ctx, x * k, y * k, w * k, h * k, radius * k, color));
    }

    /** Pixel-space rounded rect. Each row: a solid span plus partially covered end pixels. */
    private static void pxRoundedRect(DrawContext ctx, float x, float y, float w, float h, float r, int color) {
        GuiBatch.shape(ctx, () -> pxRoundedRectRows(ctx, x, y, w, h, r, color));
    }

    private static void pxRoundedRectRows(DrawContext ctx, float x, float y, float w, float h, float r, int color) {
        r = Math.max(0f, Math.min(r, Math.min(w, h) / 2f));
        int top = (int) Math.floor(y);
        int bottom = (int) Math.ceil(y + h);
        // The straight middle section in one fill, rows with full coverage only.
        int midTop = (int) Math.ceil(y + r);
        int midBottom = (int) Math.floor(y + h - r);
        if (midBottom > midTop) {
            span(ctx, midTop, midBottom, x, x + w, color, 1f);
        }
        for (int yy = top; yy < bottom; yy++) {
            if (yy >= midTop && yy < midBottom) {
                continue;
            }
            float v = Math.min(yy + 1, y + h) - Math.max(yy, y);
            if (v <= 0) {
                continue;
            }
            float ry = yy + 0.5f;
            float dy = 0f;
            if (ry < y + r) {
                dy = y + r - ry;
            } else if (ry > y + h - r) {
                dy = ry - (y + h - r);
            }
            float inset = dy > 0f ? r - (float) Math.sqrt(Math.max(0f, r * r - dy * dy)) : 0f;
            span(ctx, yy, yy + 1, x + inset, x + w - inset, color, Math.min(1f, v));
        }
    }

    /**
     * Fill rows {@code y1..y2} from {@code left} to {@code right} (sub-pixel),
     * with the partly covered end columns at partial alpha.
     */
    private static void span(DrawContext ctx, int y1, int y2, float left, float right, int color, float vertical) {
        if (right <= left) {
            return;
        }
        int l = (int) Math.ceil(left);
        int r = (int) Math.floor(right);
        if (r < l) {
            int c = Colors.fade(color, (right - left) * vertical);
            GuiBatch.fill(ctx, r, y1, r + 1, y2, c, c);
            return;
        }
        if (r > l) {
            int c = vertical >= 0.999f ? color : Colors.fade(color, vertical);
            GuiBatch.fill(ctx, l, y1, r, y2, c, c);
        }
        float lc = (l - left) * vertical;
        float rc = (right - r) * vertical;
        if (lc > 0.01f) {
            int c = Colors.fade(color, lc);
            GuiBatch.fill(ctx, l - 1, y1, l, y2, c, c);
        }
        if (rc > 0.01f) {
            int c = Colors.fade(color, rc);
            GuiBatch.fill(ctx, r, y1, r + 1, y2, c, c);
        }
    }

    /**
     * A rounded rectangle whose colour runs from {@code top} to {@code bottom}.
     * Banded in pixel rows, each its own colour, so the gradient is smooth.
     */
    public static void roundedGradient(DrawContext ctx, int x, int y, int w, int h, int r, int top, int bottom) {
        if (w <= 0 || h <= 0) {
            return;
        }
        inPixels(ctx, k -> GuiBatch.shape(ctx, () -> {
            float px = x * k;
            float py = y * k;
            float pw = w * k;
            float ph = h * k;
            float pr = Math.min(r * k, Math.min(pw, ph) / 2f);
            int rows = (int) Math.ceil(ph);
            for (int i = 0; i < rows; i++) {
                float ry = py + i + 0.5f;
                float dy = 0f;
                if (ry < py + pr) {
                    dy = py + pr - ry;
                } else if (ry > py + ph - pr) {
                    dy = ry - (py + ph - pr);
                }
                float inset = dy > 0f ? pr - (float) Math.sqrt(Math.max(0f, pr * pr - dy * dy)) : 0f;
                int c = Colors.lerp(top, bottom, rows <= 1 ? 0f : i / (float) (rows - 1));
                int yy = (int) Math.floor(py) + i;
                float v = Math.min(yy + 1, py + ph) - Math.max(yy, py);
                span(ctx, yy, yy + 1, px + inset, px + pw - inset, c, Math.max(0f, Math.min(1f, v)));
            }
        }));
    }

    /**
     * A one-pixel (screen pixel) outline following the rounded rect's curve:
     * the ring between the shape and the same shape inset by one pixel.
     */
    public static void roundedOutline(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0 || Colors.alpha(color) == 0) {
            return;
        }
        float radius = roundCorners() ? r : 0f;
        inPixels(ctx, k -> {
            float t = Math.max(1f, Math.round(k * 0.6f));
            pxRoundedRing(ctx, x * k, y * k, w * k, h * k, radius * k, t, color);
        });
    }

    private static void pxRoundedRing(DrawContext ctx, float x, float y, float w, float h, float r, float t, int color) {
        GuiBatch.shape(ctx, () -> pxRoundedRingRows(ctx, x, y, w, h, r, t, color));
    }

    private static void pxRoundedRingRows(DrawContext ctx, float x, float y, float w, float h, float r, float t, int color) {
        r = Math.max(0f, Math.min(r, Math.min(w, h) / 2f));
        float ri = Math.max(0f, r - t);
        int top = (int) Math.floor(y);
        int bottom = (int) Math.ceil(y + h);
        // Rows clear of both corners are identical: the two sides, each one tall span.
        int midTop = (int) Math.ceil(y + Math.max(r, t) + 1);
        int midBottom = (int) Math.floor(y + h - Math.max(r, t) - 1);
        if (midBottom > midTop) {
            span(ctx, midTop, midBottom, x, x + t, color, 1f);
            span(ctx, midTop, midBottom, x + w - t, x + w, color, 1f);
        }
        for (int yy = top; yy < bottom; yy++) {
            if (yy >= midTop && yy < midBottom) {
                continue;
            }
            float v = Math.min(yy + 1, y + h) - Math.max(yy, y);
            if (v <= 0) {
                continue;
            }
            float ry = yy + 0.5f;
            float outer = insetAt(ry, y, h, r);
            boolean innerRow = ry > y + t && ry < y + h - t;
            if (!innerRow) {
                span(ctx, yy, yy + 1, x + outer, x + w - outer, color, Math.min(1f, v));
                continue;
            }
            float inner = t + insetAt(ry, y + t, h - 2 * t, ri);
            span(ctx, yy, yy + 1, x + outer, x + inner, color, 1f);
            span(ctx, yy, yy + 1, x + w - inner, x + w - outer, color, 1f);
        }
    }

    private static float insetAt(float ry, float y, float h, float r) {
        float dy = 0f;
        if (ry < y + r) {
            dy = y + r - ry;
        } else if (ry > y + h - r) {
            dy = ry - (y + h - r);
        }
        return dy > 0f ? r - (float) Math.sqrt(Math.max(0f, r * r - Math.min(dy, r) * Math.min(dy, r))) : 0f;
    }

    /**
     * A rounded rectangle that keeps its radius whatever the Corners setting
     * says — for icon strokes, which are drawn shapes, not UI panels.
     */
    public static void roundedRectAlways(DrawContext ctx, float x, float y, float w, float h, float r, int color) {
        if (w <= 0 || h <= 0 || Colors.alpha(color) == 0) {
            return;
        }
        inPixels(ctx, k -> pxRoundedRect(ctx, x * k, y * k, w * k, h * k, r * k, color));
    }

    /** A filled, anti-aliased circle centred on {@code cx, cy}. */
    public static void circle(DrawContext ctx, float cx, float cy, float r, int color) {
        if (Colors.alpha(color) == 0) {
            return;
        }
        inPixels(ctx, k -> pxRoundedRect(ctx, (cx - r) * k, (cy - r) * k, r * 2 * k, r * 2 * k, r * k, color));
    }

    /** An anti-aliased ring (circle outline) of the given stroke thickness, in units. */
    public static void ring(DrawContext ctx, float cx, float cy, float r, float thickness, int color) {
        inPixels(ctx, k -> {
            float pr = r * k;
            pxRoundedRing(ctx, (cx - r) * k, (cy - r) * k, pr * 2, pr * 2, pr, Math.max(1f, thickness * k), color);
        });
    }

    /**
     * Part of a ring, from {@code from} to {@code to} (fractions of a turn,
     * clockwise from 12 o'clock), as short line segments.
     */
    public static void arc(DrawContext ctx, float cx, float cy, float r, float thickness, float from, float to, int color) {
        if (to - from <= 0.001f) {
            return;
        }
        int steps = Math.max(2, Math.round((to - from) * 28));
        float prevX = 0;
        float prevY = 0;
        for (int i = 0; i <= steps; i++) {
            double a = (from + (to - from) * i / steps) * Math.PI * 2 - Math.PI / 2;
            float x = cx + (float) Math.cos(a) * (r - thickness / 2);
            float y = cy + (float) Math.sin(a) * (r - thickness / 2);
            if (i > 0) {
                line(ctx, prevX, prevY, x, y, thickness, color);
            }
            prevX = x;
            prevY = y;
        }
    }

    /**
     * A straight line {@code thickness} units wide, drawn as one rotated quad
     * — smooth at any angle, and one fill instead of a dotted trail.
     */
    public static void line(DrawContext ctx, float x1, float y1, float x2, float y2, float thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f) {
            return;
        }
        inPixels(ctx, k -> {
            float t = Math.max(1f, thickness * k);
            push(ctx);
            translate(ctx, x1 * k, y1 * k);
            rotate(ctx, (float) Math.atan2(dy, dx));
            translate(ctx, 0f, -t / 2f);
            ctx.fill(0, 0, Math.round(len * k), Math.max(1, Math.round(t)), color);
            pop(ctx);
        });
    }

    /**
     * A soft radial glow centred on {@code cx, cy}: one stretched, tinted
     * texture ({@link dev.blurredclient.mod.ui.kit.GlowTexture}), so a
     * screen-sized glow costs a single draw. {@code rings} is ignored; it's
     * kept so callers didn't have to change.
     */
    public static void glow(DrawContext ctx, float cx, float cy, float radius, int color, int rings) {
        dev.blurredclient.mod.ui.kit.GlowTexture.draw(ctx, cx, cy, radius, color);
    }

    // ------------------------------------------------------------------
    // Clipping
    // ------------------------------------------------------------------

    /** Scale the active kit screen draws at; see {@link #scissor}. */
    private static final ArrayDeque<Float> UI_SCALES = new ArrayDeque<>();

    public static void pushUiScale(float s) {
        UI_SCALES.push(s);
    }

    public static void popUiScale() {
        UI_SCALES.poll();
    }

    /**
     * Clip drawing to a rectangle given in the current drawing units.
     *
     * <p>From 1.21.4 {@code enableScissor} applies the current transform
     * itself (checked with javap); 1.21.1's doesn't, so there the rectangle
     * is scaled by the kit screen's scale by hand.
     */
    public static void scissor(DrawContext ctx, int x1, int y1, int x2, int y2) {
        //? if >=1.21.4 {
        ctx.enableScissor(x1, y1, x2, y2);
        //?} else {
        /*float s = UI_SCALES.isEmpty() ? 1f : UI_SCALES.peek();
        ctx.enableScissor((int) Math.floor(x1 * s), (int) Math.floor(y1 * s),
                (int) Math.ceil(x2 * s), (int) Math.ceil(y2 * s));
        *///?}
    }

    public static void unscissor(DrawContext ctx) {
        ctx.disableScissor();
    }
}
