package dev.blurredclient.mod.ui.kit;

import dev.blurredclient.mod.Theme;
import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Smooth glass, styled as the wall of a fish tank.
 *
 * <p>What sells it, in drawing order:
 * <ol>
 *   <li>a soft shadow, so the panel floats;</li>
 *   <li>a water tint that deepens toward the bottom, like looking into a tank;</li>
 *   <li>slow caustic light drifting across the water;</li>
 *   <li>a few tiny bubbles rising to the surface;</li>
 *   <li>a bright waterline just under the top edge;</li>
 *   <li>a thin glass rim with a glint where light catches the top-left corner.</li>
 * </ol>
 * Caustics and bubbles follow the Appearance module's "Bubbles and light
 * ripples" switch. Everything is fills with alpha: no textures, no shaders,
 * so it behaves identically on every version.
 */
public final class GlassSkin implements Skin {
    public static final GlassSkin INSTANCE = new GlassSkin();

    private static final long START = System.nanoTime();

    private static final int RIM = 0x55A8E6F0;
    private static final int RIM_HOVER = 0x90BFF6FF;
    private static final int WATERLINE = 0x70CFF8FF;
    private static final int GLINT = 0xA0FFFFFF;
    private static final int TRACK_OFF = 0x70304A56;

    private GlassSkin() {}

    private static float seconds() {
        return (System.nanoTime() - START) / 1_000_000_000f;
    }

    private static int radius(Surface s) {
        return switch (s) {
            case WINDOW, RAIL -> 8;
            case CARD -> 6;
            case POPUP -> 5;
            case INSET -> 4;
        };
    }

    // ------------------------------------------------------------------
    // Panels
    // ------------------------------------------------------------------

    @Override
    public void panel(DrawContext ctx, int x, int y, int w, int h, Surface surface) {
        panelInner(ctx, x, y, w, h, surface);
        SkinOutline.draw(ctx, x, y, w, h, Math.min(radius(surface), Math.min(w, h) / 2), surface);
    }

    private void panelInner(DrawContext ctx, int x, int y, int w, int h, Surface surface) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int r = Math.min(radius(surface), Math.min(w, h) / 2);

        if (surface != Surface.INSET) {
            softShadow(ctx, x, y, w, h, r, surface == Surface.CARD ? 2 : 4);
        }

        int[] tint = switch (surface) {
            case WINDOW -> new int[] {0xC0103848, 0xE0051822};
            case RAIL -> new int[] {0xC80C3040, 0xE8041620};
            case CARD -> new int[] {0x60185060, 0x900A2832};
            case POPUP -> new int[] {0xF0123E4E, 0xF8072430};
            case INSET -> new int[] {0x90031218, 0xA8061C26};
        };
        Draw.roundedGradient(ctx, x, y, w, h, r, tint[0], tint[1]);

        boolean big = surface == Surface.WINDOW || surface == Surface.RAIL;
        if (Modules.APPEARANCE.tankEffects() && (big || (surface == Surface.CARD && h > 60))) {
            caustics(ctx, x, y, w, h, r);
            if (big && h > 80) {
                bubbles(ctx, x, y, w, h, r);
            }
        }

        if (surface != Surface.INSET) {
            waterline(ctx, x, y, w, h, r);
        }
        Draw.roundedOutline(ctx, x, y, w, h, r, surface == Surface.INSET ? 0x30A8E6F0 : RIM);
        if (surface != Surface.INSET) {
            glint(ctx, x, y, w, h, r);
        }
    }

    /** Stacked faint rounded rects, widening and fading: a blur without a blur. */
    private static void softShadow(DrawContext ctx, int x, int y, int w, int h, int r, int depth) {
        for (int i = depth; i >= 1; i--) {
            int alpha = 0x10 + (depth - i) * 0x08;
            Draw.roundedRect(ctx, x - i, y - i + 2, w + i * 2, h + i * 2, r + i, alpha << 24);
        }
    }

    /** The surface of the water: a bright line with a faint band beneath it. */
    private static void waterline(DrawContext ctx, int x, int y, int w, int h, int r) {
        if (h < 10) {
            return;
        }
        int row = 3;
        int in = Draw.inset(row, h, r) + 2;
        ctx.fill(x + in, y + row, x + w - in, y + row + 1, WATERLINE);
        ctx.fill(x + in, y + row + 1, x + w - in, y + row + 2, 0x20CFF8FF);
    }

    /** Light catching the glass at the top-left corner. */
    private static void glint(DrawContext ctx, int x, int y, int w, int h, int r) {
        int along = Math.min(28, w / 4);
        ctx.fill(x + r, y, x + r + along, y + 1, GLINT);
        int down = Math.min(18, h / 4);
        ctx.fill(x, y + r, x + 1, y + r + down, 0x60FFFFFF);
        // A short second highlight just inside, as on a thick pane.
        ctx.fill(x + r + 2, y + 1, x + r + 2 + along / 3, y + 2, 0x40FFFFFF);
    }

    /**
     * Caustics: broken streaks of light that drift sideways and swell and
     * shrink, the way sunlight ripples on the floor of a tank.
     */
    private static void caustics(DrawContext ctx, int x, int y, int w, int h, int r) {
        float t = seconds();
        int left = x + r + 2;
        int span = w - r * 2 - 24;
        if (span <= 0) {
            return;
        }
        int band = 0;
        for (int yy = y + 10; yy < y + h - 8; yy += 11, band++) {
            float phase = t * 0.6f + band * 1.7f;
            int segments = Math.max(1, w / 46);
            for (int k = 0; k < segments; k++) {
                int offset = (int) (k * 46 + Math.sin(phase + k) * 14 + t * 7 + band * 19);
                int sx = left + Math.floorMod(offset, span);
                int segW = 8 + (int) (7 * (1 + Math.sin(phase * 1.3 + k * 2.1)));
                int alpha = 0x0A + (int) (0x0C * (0.5 + 0.5 * Math.sin(phase * 0.8 + k)));
                ctx.fill(sx, yy, sx + segW, yy + 1, (alpha << 24) | 0xBFF6FF);
            }
        }
    }

    /**
     * Bubbles rising from the floor of the panel to the waterline, wobbling a
     * little and fading as they reach the surface. Positions come from the
     * clock and the bubble's index, so there's no state to keep.
     */
    private static void bubbles(DrawContext ctx, int x, int y, int w, int h, int r) {
        float t = seconds();
        int count = Math.max(3, w / 70);
        int floor = y + h - 6;
        int surface = y + 8;
        int rise = floor - surface;
        for (int i = 0; i < count; i++) {
            double noise = Math.sin(i * 12.9898) * 43758.5453;
            float fx = (float) (noise - Math.floor(noise));
            int bx = x + r + 4 + (int) (fx * Math.max(1, w - r * 2 - 10));
            float speed = 5f + (i % 4) * 3f;
            float travelled = (t * speed + i * 37f) % rise;
            int by = floor - (int) travelled;
            bx += (int) Math.round(Math.sin(t * 1.5 + i) * 2);

            float fade = Math.min(1f, (by - surface) / 14f);
            int size = 2 + (i % 2);
            bubble(ctx, bx, by, size, fade);
        }
    }

    private static void bubble(DrawContext ctx, int x, int y, int size, float fade) {
        int body = Colors.fade(0x55CFF8FF, fade);
        int shine = Colors.fade(0xB0FFFFFF, fade);
        if (size <= 2) {
            ctx.fill(x, y, x + 2, y + 2, body);
            ctx.fill(x, y, x + 1, y + 1, shine);
            return;
        }
        // A hollow 3x3 ring with a bright pixel top-left.
        ctx.fill(x + 1, y, x + 2, y + 1, body);
        ctx.fill(x, y + 1, x + 1, y + 2, body);
        ctx.fill(x + 2, y + 1, x + 3, y + 2, body);
        ctx.fill(x + 1, y + 2, x + 2, y + 3, body);
        ctx.fill(x, y, x + 1, y + 1, shine);
    }

    // ------------------------------------------------------------------
    // Controls
    // ------------------------------------------------------------------

    @Override
    public void button(DrawContext ctx, TextRenderer font, String label, int x, int y, int w, int h,
                       float hover, boolean pressed, boolean enabled, boolean accent) {
        int r = Math.min(5, h / 2);
        int base = accent
                ? Colors.lerp(0xC026B3A6, 0xF035E0D0, hover)
                : Colors.lerp(0x50184858, 0x8826687A, hover);
        if (pressed) {
            // Sink toward the deep-water colour, so it reads as pushed in.
            base = Colors.lerp(base, 0xF0061820, 0.18f);
        }
        if (!enabled) {
            base = Colors.fade(base, 0.45f);
        }
        Draw.roundedRect(ctx, x, y, w, h, r, base);
        int cap = Draw.inset(1, h, r) + 1;
        ctx.fill(x + cap, y + 1, x + w - cap, y + 2, enabled ? 0x40FFFFFF : 0x18FFFFFF);
        Draw.roundedOutline(ctx, x, y, w, h, r, enabled ? Colors.lerp(0x40A8E6F0, RIM_HOVER, hover) : 0x20A8E6F0);

        int color = !enabled ? Theme.TEXT_FAINT : accent ? 0xFF04202A : Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, 0.5f + hover / 2);
        String shown = Widgets.ellipsize(font, this, label, w - 10);
        int tx = x + (w - width(font, shown)) / 2;
        int ty = y + (h - font.fontHeight) / 2 + 1 + (pressed ? 1 : 0);
        drawText(ctx, font, shown, tx, ty, color);
    }

    @Override
    public void toggle(DrawContext ctx, int x, int y, int w, int h, float on, float hover, boolean enabled) {
        int r = h / 2;
        int track = Colors.lerp(TRACK_OFF, 0xE035E0D0, on);
        if (!enabled) {
            track = Colors.fade(track, 0.45f);
        }
        Draw.roundedRect(ctx, x, y, w, h, r, track);
        Draw.roundedOutline(ctx, x, y, w, h, r, Colors.lerp(0x40A8E6F0, RIM_HOVER, hover));

        int d = h - 4;
        int kx = x + 2 + Math.round(on * (w - 4 - d));
        Draw.roundedRect(ctx, kx, y + 3, d, d, d / 2, 0x40000000);
        Draw.roundedRect(ctx, kx, y + 2, d, d, d / 2, Colors.lerp(0xFFB8D4DC, 0xFFFFFFFF, Math.max(on, hover)));
    }

    @Override
    public void slider(DrawContext ctx, int x, int y, int w, int h, float fraction, float hover, boolean dragging) {
        int knob = 8 + (dragging || hover > 0.5f ? 2 : 0);
        int trackH = 4;
        int ty = y + (h - trackH) / 2;
        int usable = w - 10;
        Draw.roundedRect(ctx, x, ty, w, trackH, 2, TRACK_OFF);
        int filled = 5 + Math.round(fraction * usable);
        Draw.roundedRect(ctx, x, ty, filled, trackH, 2, 0xE035E0D0);

        int kx = x + 5 + Math.round(fraction * usable) - knob / 2;
        int ky = y + (h - knob) / 2;
        Draw.roundedRect(ctx, kx, ky + 1, knob, knob, knob / 2, 0x40000000);
        Draw.roundedRect(ctx, kx, ky, knob, knob, knob / 2, 0xFFF4FDFF);
        Draw.roundedOutline(ctx, kx, ky, knob, knob, knob / 2, Theme.accent());
    }

    @Override
    public void field(DrawContext ctx, int x, int y, int w, int h, float hover, boolean focused) {
        int r = Math.min(4, h / 2);
        Draw.roundedGradient(ctx, x, y, w, h, r, 0x90031218, 0xA8061C26);
        int edge = focused ? 0xC035E0D0 : Colors.lerp(0x30A8E6F0, 0x70A8E6F0, hover);
        Draw.roundedOutline(ctx, x, y, w, h, r, edge);
    }

    @Override
    public void highlight(DrawContext ctx, int x, int y, int w, int h, float amount) {
        if (amount <= 0.01f) {
            return;
        }
        Draw.roundedRect(ctx, x, y, w, h, Math.min(5, h / 2), Colors.fade(0x4835E0D0, amount));
    }

    @Override
    public void scrollbar(DrawContext ctx, int x, int y, int w, int h, int thumbY, int thumbH, float hover) {
        Draw.roundedRect(ctx, x, y, w, h, w / 2, 0x14FFFFFF);
        Draw.roundedRect(ctx, x, thumbY, w, thumbH, w / 2, Colors.lerp(0x50A8E6F0, 0xA0BFF6FF, hover));
    }

    @Override
    public void scrim(DrawContext ctx, int width, int height) {
        ctx.fillGradient(0, 0, width, height, 0x60021A24, 0x98010A10);
    }

    @Override
    public Text text(String s) {
        return dev.blurredclient.mod.ui.BlurredFont.ui(s);
    }

    @Override
    public void drawText(DrawContext ctx, TextRenderer font, String s, int x, int y, int color) {
        Draw.smoothText(ctx, font, s, x, y, color, 1f);
    }

    @Override
    public void drawBold(DrawContext ctx, TextRenderer font, String s, int x, int y, int color) {
        Draw.smoothText(ctx, font, s, x, y, color, 1f);
    }

    @Override
    public int width(TextRenderer font, String s) {
        return Draw.smoothWidth(font, s, 1f);
    }

    @Override
    public int boldWidth(TextRenderer font, String s) {
        return Draw.smoothWidth(font, s, 1f);
    }
}
