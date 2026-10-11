package dev.blurredclient.mod.hud;

import dev.blurredclient.mod.Theme;
import dev.blurredclient.mod.ui.kit.PixelSkin;
import dev.blurredclient.mod.ui.kit.Skins;
import net.minecraft.text.Text;
import dev.blurredclient.mod.ui.Draw;
import net.minecraft.client.gui.DrawContext;

/**
 * Shared measurements and shapes for HUD modules, so every module's panel has
 * the same padding, text size and edge treatment.
 *
 * <p>Moved out of the old single {@code HudRenderer} unchanged: the numbers
 * and the drawing are exactly what v1 used, which is what keeps the HUD
 * looking the same after the module-system rewrite.
 */
public final class HudStyle {
    private HudStyle() {}

    /** Pixel art keeps the original ocean HUD; the other styles get the modern one. */
    public static boolean pixel() {
        return Skins.current() == PixelSkin.INSTANCE;
    }

    public static int accent() {
        return Skins.current().accent();
    }

    /** HUD text in the active style's font. */
    public static Text text(String s) {
        return Skins.current().text(s);
    }

    public static Text bold(String s) {
        return Skins.current().bold(s);
    }

    /**
     * Horizontal padding, kept at roughly the corner radius: with a capsule end
     * the fill curves away from the text, so padding measured from the widest
     * row is not the clearance the first character actually gets.
     */
    public static final int PAD_X = 6;
    public static final int PAD_Y = 3;
    /** Half the space between two readouts in the island; the divider sits in the middle. */
    public static final int GAP = 5;
    /** Vertical space between rows in the stacked layout. */
    public static final int ROW_GAP = 2;

    /**
     * Readout text size, as a fraction of the game font. Small enough to read
     * as chrome, large enough that a three-digit FPS count is still legible at
     * GUI scale 2.
     */
    public static final float TEXT_SCALE = 0.8f;

    /**
     * A HUD panel: translucent abyssal fill, a lit top edge, and rounded
     * corners. A smaller radius than the island's — this is a box that happens
     * to be soft at the corners, not a capsule.
     */
    public static void panel(DrawContext ctx, int x, int y, int w, int h) {
        if (!pixel()) {
            Draw.roundedRect(ctx, x, y, w, h, Math.min(2, h / 2), 0xA80E0F14);
            return;
        }
        int r = Math.min(4, h / 2);
        Draw.roundedRect(ctx, x, y, w, h, r, Theme.PANEL);

        // Lit top edge — the same wet-glass cue the launcher's cards have.
        int cap = Draw.inset(0, h, r) + 1;
        ctx.fill(x + cap, y, x + w - cap, y + 1, Theme.PANEL_EDGE);
    }

    /**
     * The pill: a true capsule, with the corner radius taken all the way to
     * half the height. {@link Draw#roundedRect} derives each row's inset from
     * the actual circle, so the ends stay round at any height.
     */
    public static void island(DrawContext ctx, int x, int y, int w, int h) {
        int r = h / 2;
        if (!pixel()) {
            // Modern styles: a gently rounded bar, not a capsule.
            int mr = Math.min(3, h / 2);
            Draw.roundedRect(ctx, x, y, w, h, mr, 0xA80E0F14);
            Draw.roundedOutline(ctx, x, y, w, h, mr, 0x1CFFFFFF);
            return;
        }

        // Shadow, offset down. Grounds the pill against a bright sky.
        Draw.roundedRect(ctx, x, y + 2, w, h, r, 0x40000B12);
        Draw.roundedRect(ctx, x, y, w, h, r, Theme.PANEL);
        // Faint rim, so the shape has a defined edge over bright terrain.
        Draw.roundedOutline(ctx, x, y, w, h, r, 0x2A7DE2F0);

        // Lit top edge — the wet-glass cue the launcher's cards have. Inset to
        // match the curve, so it stops where the corner starts.
        int cap = Draw.inset(0, h, r) + 1;
        ctx.fill(x + cap, y, x + w - cap, y + 1, Theme.PANEL_EDGE);

        // Accent pip at each end, at the widest row so it sits inside the fill.
        ctx.fill(x + 1, y + h / 2 - 1, x + 2, y + h / 2 + 1, Theme.accentGlow());
        ctx.fill(x + w - 2, y + h / 2 - 1, x + w - 1, y + h / 2 + 1, Theme.accentGlow());
    }
}
