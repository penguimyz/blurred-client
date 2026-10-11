package dev.blurredclient.mod.ui.kit;

import dev.blurredclient.mod.Theme;
import dev.blurredclient.mod.ui.BlurredFont;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.OceanUi;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The pixel-art ocean look: square edges, hard shadows, the pixel font, and
 * animation in visible steps rather than smooth fades.
 *
 * <p>Built on {@link OceanUi}, which is what the vanilla-menu restyle already
 * uses, so Blurred's own screens and Minecraft's restyled ones match.
 */
public final class PixelSkin implements Skin {
    public static final PixelSkin INSTANCE = new PixelSkin();

    private static final int EDGE = 0x9A5FA9BC;
    private static final int WELL = 0xE0061620;

    private PixelSkin() {}

    /** Quantise an animation to a few steps — pixel art moves in jumps. */
    private static float step(float f) {
        return Math.round(f * 3f) / 3f;
    }

    @Override
    public void panel(GuiGraphicsExtractor ctx, int x, int y, int w, int h, Surface surface) {
        panelInner(ctx, x, y, w, h, surface);
        SkinOutline.draw(ctx, x, y, w, h, 0, surface);
    }

    private void panelInner(GuiGraphicsExtractor ctx, int x, int y, int w, int h, Surface surface) {
        switch (surface) {
            case WINDOW, RAIL -> OceanUi.panel(ctx, x, y, w, h, false);
            case CARD, POPUP -> OceanUi.panel(ctx, x, y, w, h, true);
            case INSET -> well(ctx, x, y, w, h, 0x507DE2F0);
        }
    }

    /** A carved-in box: dark fill, shadow along the inside top, thin border. */
    private static void well(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int border) {
        ctx.fill(x, y, x + w, y + h, WELL);
        ctx.fill(x + 1, y + 1, x + w - 1, y + 2, 0x60000000);
        ctx.fill(x, y, x + w, y + 1, border);
        ctx.fill(x, y + h - 1, x + w, y + h, border);
        ctx.fill(x, y, x + 1, y + h, border);
        ctx.fill(x + w - 1, y, x + w, y + h, border);
    }

    @Override
    public void button(GuiGraphicsExtractor ctx, Font font, String label, int x, int y, int w, int h,
                       float hover, boolean pressed, boolean enabled, boolean accent) {
        int dy = pressed ? 1 : 0;
        OceanUi.button(ctx, font, BlurredFont.of(label), x, y + dy, w, h, hover > 0.5f, enabled);
        if (accent && enabled) {
            ctx.fill(x, y + dy, x + w, y + dy + 1, Theme.accent());
            ctx.fill(x, y + dy + h - 1, x + w, y + dy + h, Theme.accent());
        }
    }

    @Override
    public void toggle(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float on, float hover, boolean enabled) {
        float s = step(on);
        well(ctx, x, y, w, h, hover > 0.5f ? Theme.accent() : EDGE);
        if (s > 0f) {
            ctx.fill(x + 2, y + 2, x + 2 + Math.round(s * (w - 4)), y + h - 2, Colors.fade(0xE01B6E70, enabled ? 1f : 0.5f));
        }
        int k = h - 4;
        int kx = x + 2 + Math.round(s * (w - 4 - k));
        ctx.fill(kx + 1, y + 3, kx + k + 1, y + h - 1, 0x70000B12);
        ctx.fill(kx, y + 2, kx + k, y + h - 2, s >= 1f ? Theme.accent() : 0xFF557C8A);
        ctx.fill(kx, y + 2, kx + k, y + 3, 0x60FFFFFF);
    }

    @Override
    public void slider(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float fraction, float hover, boolean dragging) {
        int trackH = 4;
        int ty = y + (h - trackH) / 2;
        well(ctx, x, ty, w, trackH, EDGE);
        int knobW = 4;
        int kx = x + Math.round(fraction * (w - knobW));
        ctx.fill(x + 1, ty + 1, kx, ty + trackH - 1, Theme.accent());
        int kc = dragging || hover > 0.5f ? 0xFFEFFEFF : Theme.TEXT;
        ctx.fill(kx + 1, y + 2, kx + knobW + 1, y + h, 0x70000B12);
        ctx.fill(kx, y + 1, kx + knobW, y + h - 1, kc);
    }

    @Override
    public void field(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float hover, boolean focused) {
        well(ctx, x, y, w, h, focused ? Theme.accent() : hover > 0.5f ? 0x9A7DE2F0 : 0x507DE2F0);
    }

    @Override
    public void highlight(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float amount) {
        float s = step(amount);
        if (s <= 0f) {
            return;
        }
        ctx.fill(x, y, x + w, y + h, Colors.fade(0x5035E0D0, s));
        ctx.fill(x, y, x + 2, y + h, Colors.fade(Theme.accent(), s));
    }

    @Override
    public void scrollbar(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int thumbY, int thumbH, float hover) {
        ctx.fill(x, y, x + w, y + h, 0x40092230);
        ctx.fill(x, thumbY, x + w, thumbY + thumbH, hover > 0.5f ? Theme.accent() : Theme.accentGlow());
    }

    @Override
    public void scrim(GuiGraphicsExtractor ctx, int width, int height) {
        ctx.fill(0, 0, width, height, 0x90020C12);
    }

    @Override
    public Component text(String s) {
        return BlurredFont.of(s);
    }

    @Override
    public void drawText(GuiGraphicsExtractor ctx, Font font, String s, int x, int y, int color) {
        ctx.text(font, text(s), x, y, color);
    }
}
