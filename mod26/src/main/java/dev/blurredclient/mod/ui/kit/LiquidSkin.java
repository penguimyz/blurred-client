package dev.blurredclient.mod.ui.kit;

import dev.blurredclient.mod.Theme;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Liquid glass, after Apple's: thick, clear, rounded slabs of glass that
 * catch the light.
 *
 * <p>What sells it, in drawing order:
 * <ol>
 *   <li>a soft shadow, so the slab floats;</li>
 *   <li>a smoky clear body, a little lighter at the top, so text reads on
 *   any background but the world still shows through;</li>
 *   <li>a thick rim: two inner edge lines, the outer bright and the inner
 *   fainter, as light bends through the glass's edge;</li>
 *   <li>a specular highlight along the top edge, strongest at the left;</li>
 *   <li>a faint glow along the inside of the bottom edge, where light that
 *   came through leaves again.</li>
 * </ol>
 * Controls are capsules. Toggle knobs stretch like a droplet as they slide,
 * and every click sends water ripples out from the cursor (see
 * {@link Ripples}).
 */
public final class LiquidSkin implements Skin {
    public static final LiquidSkin INSTANCE = new LiquidSkin();

    private static final int BODY_TOP = 0x74262A33;
    private static final int BODY_BOTTOM = 0x8C15181F;
    private static final int RIM = 0x66FFFFFF;
    private static final int RIM_INNER = 0x1FFFFFFF;
    private static final int TRACK_OFF = 0x40FFFFFF;

    private LiquidSkin() {}

    private static int radius(Surface s) {
        return switch (s) {
            case WINDOW, RAIL -> 9;
            case CARD -> 7;
            case POPUP -> 7;
            case INSET -> 5;
        };
    }

    // ------------------------------------------------------------------
    // Panels
    // ------------------------------------------------------------------

    @Override
    public void panel(GuiGraphicsExtractor ctx, int x, int y, int w, int h, Surface surface) {
        panelInner(ctx, x, y, w, h, surface);
        SkinOutline.draw(ctx, x, y, w, h, Math.min(radius(surface), Math.min(w, h) / 2), surface);
    }

    private void panelInner(GuiGraphicsExtractor ctx, int x, int y, int w, int h, Surface surface) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int r = Math.min(radius(surface), Math.min(w, h) / 2);
        if (surface == Surface.INSET) {
            Draw.roundedGradient(ctx, x, y, w, h, r, 0x50000000, 0x38000000);
            Draw.roundedOutline(ctx, x, y, w, h, r, 0x24FFFFFF);
            return;
        }
        if (LiquidGlass.available()) {
            // The real thing: a slab that bends the world behind it.
            shadow(ctx, x, y, w, h, r, surface == Surface.CARD ? 1 : 3);
            LiquidGlass.draw(ctx, x, y, w, h, r, 0xFFFFFF);
            if (surface == Surface.POPUP) {
                // Popups sit over other panels; darken so their text reads.
                Draw.roundedRect(ctx, x, y, w, h, r, 0x70101318);
            }
            return;
        }
        shadow(ctx, x, y, w, h, r, surface == Surface.CARD ? 2 : 4);
        int top = surface == Surface.POPUP ? 0xE0262A33 : surface == Surface.CARD ? 0x40FFFFFF : BODY_TOP;
        int bottom = surface == Surface.POPUP ? 0xEA15181F : surface == Surface.CARD ? 0x18FFFFFF : BODY_BOTTOM;
        Draw.roundedGradient(ctx, x, y, w, h, r, top, bottom);
        glass(ctx, x, y, w, h, r);
    }

    /** Rim, inner rim, top highlight and bottom glow: the glass itself. */
    static void glass(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r) {
        // A light sheen over the top third, as on a curved surface.
        int sheen = Math.min(h / 3, 18);
        if (sheen > 2 && w > r * 2 + 2) {
            Draw.roundedGradient(ctx, x + 1, y + 1, w - 2, sheen, Math.max(0, r - 1), 0x1CFFFFFF, 0x00FFFFFF);
        }
        Draw.roundedOutline(ctx, x, y, w, h, r, RIM);
        if (w > 4 && h > 4) {
            Draw.roundedOutline(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), RIM_INNER);
        }
        // Specular highlight along the top edge, brightest at the left.
        int in = Draw.inset(0, h, r) + 1;
        int len = w - in * 2;
        for (int i = 0; i < len; i += 2) {
            float f = 1f - i / (float) len;
            int a = (int) (0xC0 * f * f);
            ctx.fill(x + in + i, y, x + in + i + 2, y + 1, (a << 24) | 0xFFFFFF);
        }
        int sideIn = Math.max(1, Math.min(h / 3, 14));
        ctx.fill(x, y + r, x + 1, y + r + sideIn, 0x50FFFFFF);
        // Light leaving through the bottom edge.
        int bin = Draw.inset(1, h, r) + 2;
        ctx.fill(x + bin, y + h - 2, x + w - bin, y + h - 1, 0x26FFFFFF);
    }

    /** Stacked faint rounded rects, widening and fading: a blur without a blur. */
    private static void shadow(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int depth) {
        for (int i = depth; i >= 1; i--) {
            int alpha = 0x0C + (depth - i) * 0x07;
            Draw.roundedRect(ctx, x - i, y - i + 3, w + i * 2, h + i * 2, r + i, alpha << 24);
        }
    }

    // ------------------------------------------------------------------
    // Controls
    // ------------------------------------------------------------------

    @Override
    public void button(GuiGraphicsExtractor ctx, Font font, String label, int x, int y, int w, int h,
                       float hover, boolean pressed, boolean enabled, boolean accent) {
        int r = h / 2;
        // Pressed glass squishes a little: one pixel in from each side.
        if (pressed) {
            x += 1;
            y += 1;
            w -= 2;
            h -= 2;
        }
        int a = accent();
        int top = accent ? Colors.fade(Colors.lerp(a, 0xFFFFFFFF, 0.15f), 0.85f + 0.15f * hover)
                : Colors.lerp(0x38FFFFFF, 0x55FFFFFF, hover);
        int bottom = accent ? Colors.fade(a, 0.75f + 0.2f * hover) : Colors.lerp(0x1EFFFFFF, 0x30FFFFFF, hover);
        if (!enabled) {
            top = Colors.fade(top, 0.4f);
            bottom = Colors.fade(bottom, 0.4f);
        }
        if (LiquidGlass.available() && enabled) {
            LiquidGlass.draw(ctx, x, y, w, h, r, accent ? accent() : 0xFFFFFF);
            if (hover > 0.01f) {
                Draw.roundedRect(ctx, x, y, w, h, r, Colors.fade(0x18FFFFFF, hover));
            }
        } else {
            Draw.roundedGradient(ctx, x, y, w, h, r, top, bottom);
            glass(ctx, x, y, w, h, r);
        }

        int color = !enabled ? Theme.TEXT_FAINT : 0xFFFFFFFF;
        String shown = Widgets.ellipsize(font, this, label, w - 10);
        int tx = x + (w - width(font, shown)) / 2;
        int ty = y + (h - font.lineHeight) / 2 + 1;
        drawText(ctx, font, shown, tx, ty, color);
    }

    @Override
    public void toggle(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float on, float hover, boolean enabled) {
        int r = h / 2;
        int track = Colors.lerp(TRACK_OFF, accent(), on);
        if (!enabled) {
            track = Colors.fade(track, 0.45f);
        }
        Draw.roundedRect(ctx, x, y, w, h, r, track);
        Draw.roundedOutline(ctx, x, y, w, h, r, Colors.lerp(0x40FFFFFF, 0x80FFFFFF, hover));

        // The knob stretches like a droplet mid-slide, and swells on hover.
        int d = h - 2;
        int stretch = Math.round((float) Math.sin(on * Math.PI) * d * 0.55f);
        int kw = d + stretch + (hover > 0.5f ? 1 : 0);
        int kx = x + 1 + Math.round(on * (w - 2 - d)) - stretch / 2;
        kx = Math.max(x + 1, Math.min(x + w - 1 - kw, kx));
        Draw.roundedRect(ctx, kx, y + 2, kw, d, d / 2, 0x38000000);
        Draw.roundedRect(ctx, kx, y + 1, kw, d, d / 2, 0xFFFFFFFF);
        ctx.fill(kx + d / 2, y + 2, kx + kw - d / 2, y + 3, 0x30000000);
    }

    @Override
    public void slider(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float fraction, float hover, boolean dragging) {
        int trackH = 4;
        int ty = y + (h - trackH) / 2;
        int usable = w - 12;
        Draw.roundedRect(ctx, x, ty, w, trackH, 2, TRACK_OFF);
        int filled = 6 + Math.round(fraction * usable);
        Draw.roundedRect(ctx, x, ty, filled, trackH, 2, accent());

        // A glass capsule knob, wider while held, like a lens over the track.
        int kh = 8;
        int kw = dragging ? 16 : 12 + (hover > 0.5f ? 1 : 0);
        int kx = x + 6 + Math.round(fraction * usable) - kw / 2;
        int ky = y + (h - kh) / 2;
        Draw.roundedRect(ctx, kx, ky + 1, kw, kh, kh / 2, 0x38000000);
        if (dragging) {
            Draw.roundedRect(ctx, kx, ky, kw, kh, kh / 2, 0x60FFFFFF);
            glass(ctx, kx, ky, kw, kh, kh / 2);
        } else {
            Draw.roundedRect(ctx, kx, ky, kw, kh, kh / 2, 0xFFFFFFFF);
        }
    }

    @Override
    public void field(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float hover, boolean focused) {
        int r = h / 2;
        Draw.roundedGradient(ctx, x, y, w, h, r, 0x48000000, 0x30000000);
        int edge = focused ? Colors.fade(accent(), 0.9f) : Colors.lerp(0x2CFFFFFF, 0x60FFFFFF, hover);
        Draw.roundedOutline(ctx, x, y, w, h, r, edge);
    }

    @Override
    public void highlight(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float amount) {
        if (amount <= 0.01f) {
            return;
        }
        Draw.roundedRect(ctx, x, y, w, h, h / 2, Colors.fade(0x26FFFFFF, amount));
    }

    @Override
    public void row(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float hover, float on) {
        int pr = (h - 2) / 2;
        if (LiquidGlass.available()) {
            // Enabled: a capsule of glass tinted with the accent. Hovered: a clear one.
            if (on > 0.01f) {
                LiquidGlass.draw(ctx, x + 2, y + 1, w - 4, h - 2, pr, Colors.lerp(0xFFFFFFFF, accent(), on));
            } else if (hover > 0.35f) {
                LiquidGlass.draw(ctx, x + 2, y + 1, w - 4, h - 2, pr, 0xFFFFFF);
            } else if (hover > 0.01f) {
                Draw.roundedRect(ctx, x + 2, y + 1, w - 4, h - 2, pr, Colors.fade(0x1AFFFFFF, hover));
            }
            return;
        }
        // An enabled module is a little capsule of tinted glass.
        if (on > 0.01f) {
            Draw.roundedRect(ctx, x + 2, y + 1, w - 4, h - 2, (h - 2) / 2, Colors.fade(accent(), 0.42f * on));
            Draw.roundedOutline(ctx, x + 2, y + 1, w - 4, h - 2, (h - 2) / 2, Colors.fade(0x55FFFFFF, on));
            ctx.fill(x + 2 + h / 2, y + 1, x + w - 2 - h / 2, y + 2, Colors.fade(0x70FFFFFF, on));
        }
        if (hover > 0.01f) {
            Draw.roundedRect(ctx, x + 2, y + 1, w - 4, h - 2, (h - 2) / 2, Colors.fade(0x1AFFFFFF, hover));
        }
    }

    @Override
    public int rowOnColor() {
        return 0xFFFFFFFF;
    }

    @Override
    public void scrollbar(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int thumbY, int thumbH, float hover) {
        Draw.roundedRect(ctx, x, thumbY, w, thumbH, w / 2, Colors.lerp(0x50FFFFFF, 0xA0FFFFFF, hover));
    }

    @Override
    public void scrim(GuiGraphicsExtractor ctx, int width, int height) {
        if (LiquidGlass.available()) {
            // The world stays clear behind real glass; just a faint vignette.
            ctx.fillGradient(0, 0, width, height, 0x10000000, 0x30000000);
            return;
        }
        ctx.fillGradient(0, 0, width, height, 0x22000000, 0x48000000);
    }

    @Override
    public boolean ripples() {
        return true;
    }

    @Override
    public int textDim() {
        return 0xFFEDEFF4;
    }

    @Override
    public int textFaint() {
        return 0xFFC4C9D4;
    }

    @Override
    public Component text(String s) {
        return dev.blurredclient.mod.ui.BlurredFont.ui(s);
    }

    @Override
    public void drawText(GuiGraphicsExtractor ctx, Font font, String s, int x, int y, int color) {
        // A soft shadow, so text reads over a bright world seen through clear glass.
        int a = (color >>> 24) & 0xFF;
        Draw.smoothText(ctx, font, s, x + 0.6f, y + 0.9f, ((a * 0xB4 / 0xFF) << 24), 1f);
        Draw.smoothText(ctx, font, s, x, y, color, 1f);
    }

    @Override
    public void drawBold(GuiGraphicsExtractor ctx, Font font, String s, int x, int y, int color) {
        drawText(ctx, font, s, x, y, color);
    }

    @Override
    public int width(Font font, String s) {
        return Draw.smoothWidth(font, s, 1f);
    }

    @Override
    public int boldWidth(Font font, String s) {
        return Draw.smoothWidth(font, s, 1f);
    }
}
