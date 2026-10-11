package dev.blurredclient.mod.ui.kit;

import dev.blurredclient.mod.module.feature.AppearanceModule.Background;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import net.minecraft.client.gui.DrawContext;

/**
 * The menu backgrounds other than the default constellation. Each is drawn
 * from the clock alone (positions come from time and an index), so there's
 * no state to keep and they look the same at any frame rate. {@code parX,
 * parY} (-1..1) are where the cursor is, for a little parallax.
 */
final class Scenes {
    private Scenes() {}

    static void draw(DrawContext ctx, Background scene, int w, int h, float t, float parX, float parY, int accent) {
        // One batched draw for the whole scene: hundreds of separate strips would stall the menu.
        dev.blurredclient.mod.ui.GuiBatch.shape(ctx, () -> scene(ctx, scene, w, h, t, parX, parY, accent));
    }

    private static void scene(DrawContext ctx, Background scene, int w, int h, float t, float parX, float parY, int accent) {
        switch (scene) {
            case AURORA -> aurora(ctx, w, h, t, parX, parY);
            case STARFIELD -> starfield(ctx, w, h, t, parX, parY);
            case WAVES -> waves(ctx, w, h, t, parX, parY, accent);
            case SYNTHWAVE -> synthwave(ctx, w, h, t, parX, parY, accent);
            case BOKEH -> bokeh(ctx, w, h, t, parX, parY, accent);
            case CONSTELLATION -> { }
        }
    }

    private static float hash(int i, int salt) {
        double v = Math.sin(i * 12.9898 + salt * 78.233) * 43758.5453;
        return (float) (v - Math.floor(v));
    }

    private static void stars(DrawContext ctx, int w, int h, float t, int count, float parX, float parY, float maxY) {
        for (int i = 0; i < count; i++) {
            float x = hash(i, 1) * w + parX * 4 * hash(i, 9);
            float y = hash(i, 2) * h * maxY + parY * 3 * hash(i, 9);
            float tw = 0.5f + 0.5f * (float) Math.sin(t * (1 + hash(i, 3) * 2) + i);
            float r = 0.4f + hash(i, 4) * 0.6f;
            Draw.circle(ctx, x, y, r, Colors.fade(0xFFE8EEFF, 0.25f + 0.6f * tw));
        }
    }

    /** Curtains of green and violet light rippling over a night sky. */
    private static void aurora(DrawContext ctx, int w, int h, float t, float parX, float parY) {
        dev.blurredclient.mod.ui.GuiBatch.fill(ctx, 0, 0, w, h, 0xFF040814, 0xFF0A1424);
        stars(ctx, w, h, t, 90, parX, parY, 0.8f);
        int[] colours = {0x4434F0A0, 0x3A2ED8C8, 0x307A5CFF};
        for (int band = 0; band < 3; band++) {
            float baseY = h * (0.28f + band * 0.1f) + parY * 8 * (band + 1);
            for (int x = 0; x < w; x += 2) {
                float k = x / (float) w;
                double wave = Math.sin(k * 6 + t * 0.35 + band * 1.7) * 0.6 + Math.sin(k * 13 - t * 0.6 + band) * 0.25;
                float top = baseY + (float) wave * h * 0.07f + parX * 6;
                float len = h * (0.16f + 0.08f * (float) Math.sin(k * 9 + t * 0.5 + band * 2));
                float glow = 0.55f + 0.45f * (float) Math.sin(k * 20 + t * 1.3 + band * 3);
                dev.blurredclient.mod.ui.GuiBatch.fill(ctx, x, Math.round(top), x + 2, Math.round(top + len),
                        Colors.fade(colours[band], glow), colours[band] & 0x00FFFFFF);
            }
        }
        dev.blurredclient.mod.ui.GuiBatch.fill(ctx, 0, h * 3 / 4, w, h, 0x00000000, 0xC0020408);
    }

    /** Stars streaking out from the middle, as if flying through space. */
    private static void starfield(DrawContext ctx, int w, int h, float t, float parX, float parY) {
        dev.blurredclient.mod.ui.GuiBatch.fill(ctx, 0, 0, w, h, 0xFF02030A, 0xFF070A18);
        float cx = w / 2f - parX * 30;
        float cy = h / 2f - parY * 20;
        float reach = (float) Math.hypot(w, h) / 2f;
        for (int i = 0; i < 160; i++) {
            float angle = hash(i, 5) * (float) Math.PI * 2;
            float speed = 0.12f + hash(i, 6) * 0.3f;
            float life = (t * speed + hash(i, 7)) % 1f;
            float d = life * life * reach;
            float d0 = Math.max(0f, d - 4 - life * 26);
            float dx = (float) Math.cos(angle);
            float dy = (float) Math.sin(angle);
            int c = Colors.fade(hash(i, 8) > 0.85f ? 0xFFB8D8FF : 0xFFFFFFFF, Math.min(1f, life * 2.2f));
            Draw.line(ctx, cx + dx * d0, cy + dy * d0, cx + dx * d, cy + dy * d, 0.5f + life * 1.2f, c);
        }
        Draw.glow(ctx, cx, cy, Math.min(w, h) * 0.25f, 0x223060FF, 7);
    }

    /** Layered glowing waves in the accent colour. */
    private static void waves(DrawContext ctx, int w, int h, float t, float parX, float parY, int accent) {
        dev.blurredclient.mod.ui.GuiBatch.fill(ctx, 0, 0, w, h, 0xFF070A14, 0xFF0B1020);
        for (int layer = 0; layer < 5; layer++) {
            float base = h * (0.42f + layer * 0.11f) + parY * 6 * (layer + 1);
            float amp = h * (0.05f - layer * 0.006f);
            float speed = 0.35f + layer * 0.12f;
            int c = Colors.lerp(accent, 0xFF2440A0, layer / 4f);
            float prevY = Float.NaN;
            for (int x = 0; x <= w; x += 3) {
                float k = x / (float) w;
                float y = base + amp * (float) (Math.sin(k * 7 + t * speed + layer) + 0.5 * Math.sin(k * 15 - t * speed * 1.4))
                        + parX * 4 * layer;
                dev.blurredclient.mod.ui.GuiBatch.fill(ctx, x, Math.round(y), x + 3, h, Colors.fade(c, 0.22f), Colors.fade(c, 0.05f));
                if (!Float.isNaN(prevY)) {
                    Draw.line(ctx, x - 3, prevY, x, y, 1f, Colors.fade(c, 0.75f - layer * 0.1f));
                }
                prevY = y;
            }
        }
    }

    /** A neon sun setting behind a scrolling grid. */
    private static void synthwave(DrawContext ctx, int w, int h, float t, float parX, float parY, int accent) {
        int horizon = Math.round(h * 0.58f + parY * 6);
        dev.blurredclient.mod.ui.GuiBatch.fill(ctx, 0, 0, w, horizon, 0xFF120626, 0xFF4A0F4E);
        stars(ctx, w, horizon, t, 60, parX, parY, 1f);
        // The sun: banded, fading pink to orange.
        float sx = w / 2f + parX * 12;
        float r = Math.min(w, h) * 0.22f;
        Draw.glow(ctx, sx, horizon - r * 0.4f, r * 1.8f, 0x40FF3C8C, 8);
        for (int y = Math.round(horizon - r * 1.4f); y < horizon; y++) {
            float dy = (horizon - r * 0.4f) - y;
            if (Math.abs(dy) > r) {
                continue;
            }
            float k = (y - (horizon - r * 1.4f)) / (r * 1.4f);
            // Stripes widening towards the bottom.
            if (k > 0.45f && ((int) ((y + t * 6) / (2 + k * 5)) % 2 == 0)) {
                continue;
            }
            float half = (float) Math.sqrt(r * r - dy * dy);
            int sun = Colors.lerp(0xFFFFD45C, 0xFFFF3C8C, k);
            dev.blurredclient.mod.ui.GuiBatch.fill(ctx, Math.round(sx - half), y, Math.round(sx + half), y + 1, sun, sun);
        }
        // Ground and grid.
        dev.blurredclient.mod.ui.GuiBatch.fill(ctx, 0, horizon, w, h, 0xFF14041E, 0xFF06020A);
        int grid = Colors.fade(Colors.lerp(accent, 0xFFFF3CC8, 0.6f), 0.7f);
        float scroll = (t * 0.6f) % 1f;
        for (int i = 0; i < 14; i++) {
            float z = (i + scroll) / 14f;
            float y = horizon + (h - horizon) * z * z;
            int line = Colors.fade(grid, 0.25f + 0.75f * z);
            dev.blurredclient.mod.ui.GuiBatch.fill(ctx, 0, Math.round(y), w, Math.round(y) + 1, line, line);
        }
        for (int i = -12; i <= 12; i++) {
            float x0 = sx + i * w * 0.02f;
            float x1 = sx + i * w * 0.2f;
            Draw.line(ctx, x0, horizon, x1, h, 0.8f, Colors.fade(grid, 0.6f));
        }
        dev.blurredclient.mod.ui.GuiBatch.fill(ctx, 0, horizon, w, horizon + 1, 0xFFFF8AD8, 0xFFFF8AD8);
    }

    /** Big soft out-of-focus lights drifting slowly. */
    private static void bokeh(DrawContext ctx, int w, int h, float t, float parX, float parY, int accent) {
        dev.blurredclient.mod.ui.GuiBatch.fill(ctx, 0, 0, w, h, 0xFF0A0B14, 0xFF140C1C);
        int[] palette = {accent, 0xFFFF7AB6, 0xFF7AB8FF, 0xFFFFC46B, 0xFF9B7BFF};
        for (int i = 0; i < 26; i++) {
            float depth = 0.3f + hash(i, 11) * 0.7f;
            float x = (hash(i, 12) * w + t * (4 + 10 * depth) * (hash(i, 13) - 0.5f) * 2 + w * 4) % (w + 80) - 40
                    + parX * 20 * depth;
            float y = hash(i, 14) * h + (float) Math.sin(t * 0.3 + i) * 12 + parY * 14 * depth;
            float r = 10 + depth * 34;
            int c = palette[i % palette.length];
            Draw.circle(ctx, x, y, r, Colors.fade(c, 0.07f + 0.06f * depth));
            Draw.circle(ctx, x, y, r * 0.8f, Colors.fade(c, 0.06f + 0.05f * depth));
            Draw.ring(ctx, x, y, r, 1f, Colors.fade(c, 0.12f));
        }
    }
}
