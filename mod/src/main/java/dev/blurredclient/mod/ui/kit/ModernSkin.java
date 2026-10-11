package dev.blurredclient.mod.ui.kit;

import dev.blurredclient.mod.ui.BlurredFont;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * The default look: flat, dark and sharp, in the style of modern client UIs.
 *
 * <p>Near-black translucent panels with a small corner radius, one strong
 * blue accent for anything selected or on, white text in a smooth TTF face
 * (Varela Round, see {@link BlurredFont#ui}). No decoration in the panels
 * themselves — the contrast between dark glass and the accent does the work.
 */
public final class ModernSkin implements Skin {
    public static final ModernSkin INSTANCE = new ModernSkin();

    public static final int ACCENT = 0xFF26B4E6;
    private static final int ACCENT_HOVER = 0xFF55CDF2;

    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFB4B8C4;
    private static final int TEXT_FAINT = 0xFF7A7F8E;

    private static final int PANEL = 0xD8101014;
    private static final int RAISED = 0xFF1A1B21;
    private static final int RAISED_HOVER = 0xFF24262E;
    private static final int TRACK = 0xFF34363F;
    private static final int EDGE = 0x22FFFFFF;

    private ModernSkin() {}

    @Override
    public int accent() {
        return dev.blurredclient.mod.Theme.chroma(ACCENT);
    }

    @Override
    public int textPrimary() {
        return TEXT;
    }

    @Override
    public int textDim() {
        return TEXT_DIM;
    }

    @Override
    public int textFaint() {
        return TEXT_FAINT;
    }

    // ------------------------------------------------------------------

    @Override
    public void panel(DrawContext ctx, int x, int y, int w, int h, Surface surface) {
        panelInner(ctx, x, y, w, h, surface);
        SkinOutline.draw(ctx, x, y, w, h, (dev.blurredclient.mod.module.Modules.APPEARANCE.rounded() ? Math.min(4, Math.min(w, h) / 2) : 0), surface);
    }

    private void panelInner(DrawContext ctx, int x, int y, int w, int h, Surface surface) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int r = Math.min(surface == Surface.INSET ? 2 : 3, Math.min(w, h) / 2);
        switch (surface) {
            case WINDOW, RAIL, POPUP -> {
                // Flat: one fill and a hairline border. No drop shadow and no
                // sheen — both read as fussy shading on a flat dark UI.
                Draw.roundedRect(ctx, x, y, w, h, r, surface == Surface.POPUP ? 0xF2121319 : PANEL);
                Draw.roundedOutline(ctx, x, y, w, h, r, 0x18FFFFFF);
            }
            // Cards sit inside a panel: just a faint lift, no shadow.
            case CARD -> Draw.roundedRect(ctx, x, y, w, h, r, 0x0CFFFFFF);
            case INSET -> {
                Draw.roundedRect(ctx, x, y, w, h, r, 0xE00B0C10);
                Draw.roundedOutline(ctx, x, y, w, h, r, EDGE);
            }
        }
    }

    @Override
    public void button(DrawContext ctx, TextRenderer font, String label, int x, int y, int w, int h,
                       float hover, boolean pressed, boolean enabled, boolean accent) {
        int r = Math.min(3, h / 2);
        int fill = accent
                ? Colors.lerp(accent(), dev.blurredclient.mod.Theme.chroma(ACCENT_HOVER), hover)
                : Colors.lerp(RAISED, RAISED_HOVER, hover);
        if (pressed) {
            fill = Colors.lerp(fill, 0xFF000000, 0.2f);
        }
        if (!enabled) {
            fill = Colors.fade(fill, 0.5f);
        }
        Draw.roundedRect(ctx, x, y, w, h, r, fill);
        if (hover > 0.01f && !accent) {
            Draw.roundedOutline(ctx, x, y, w, h, r, Colors.fade(accent(), hover * 0.7f));
        }
        String shown = Widgets.ellipsize(font, this, label, w - 8);
        int tx = x + (w - boldWidth(font, shown)) / 2;
        int ty = y + (h - font.fontHeight) / 2 + 1;
        drawBold(ctx, font, shown, tx, ty, enabled ? TEXT : TEXT_FAINT);
    }

    @Override
    public void toggle(DrawContext ctx, int x, int y, int w, int h, float on, float hover, boolean enabled) {
        int r = h / 2;
        int track = Colors.lerp(Colors.lerp(TRACK, 0xFF40434E, hover), accent(), on);
        if (!enabled) {
            track = Colors.fade(track, 0.5f);
        }
        Draw.roundedRect(ctx, x, y, w, h, r, track);
        int d = h - 4;
        int kx = x + 2 + Math.round(on * (w - 4 - d));
        Draw.roundedRect(ctx, kx, y + 2, d, d, d / 2, 0xFFFFFFFF);
    }

    @Override
    public void slider(DrawContext ctx, int x, int y, int w, int h, float fraction, float hover, boolean dragging) {
        int trackH = 3;
        int ty = y + (h - trackH) / 2;
        int usable = w - 8;
        Draw.roundedRect(ctx, x, ty, w, trackH, 1, TRACK);
        Draw.roundedRect(ctx, x, ty, 4 + Math.round(fraction * usable), trackH, 1, accent());
        int knob = dragging || hover > 0.5f ? 8 : 6;
        int kx = x + 4 + Math.round(fraction * usable) - knob / 2;
        Draw.roundedRect(ctx, kx, y + (h - knob) / 2, knob, knob, knob / 2, 0xFFFFFFFF);
    }

    @Override
    public void field(DrawContext ctx, int x, int y, int w, int h, float hover, boolean focused) {
        int r = Math.min(2, h / 2);
        Draw.roundedRect(ctx, x, y, w, h, r, Colors.lerp(0xE00B0C10, 0xF0111218, hover));
        Draw.roundedOutline(ctx, x, y, w, h, r, focused ? accent() : Colors.lerp(EDGE, 0x40FFFFFF, hover));
    }

    @Override
    public void highlight(DrawContext ctx, int x, int y, int w, int h, float amount) {
        if (amount <= 0.01f) {
            return;
        }
        Draw.roundedRect(ctx, x, y, w, h, Math.min(2, h / 2), Colors.fade((0x38 << 24) | (accent() & 0xFFFFFF), amount));
    }

    @Override
    public void scrollbar(DrawContext ctx, int x, int y, int w, int h, int thumbY, int thumbH, float hover) {
        int bx = x + w - 3;
        Draw.roundedRect(ctx, bx, thumbY, 3, thumbH, 1, Colors.lerp(0x50FFFFFF, accent(), hover));
    }

    @Override
    public void scrim(DrawContext ctx, int width, int height) {
        Draw.rect(ctx, 0, 0, width, height, 0x50000000);
    }

    @Override
    public Text text(String s) {
        return BlurredFont.ui(s);
    }

    @Override
    public Text bold(String s) {
        return BlurredFont.uiBold(s);
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
