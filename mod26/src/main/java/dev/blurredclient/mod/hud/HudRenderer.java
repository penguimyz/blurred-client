package dev.blurredclient.mod.hud;

import dev.blurredclient.mod.Theme;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.module.hud.HudModule;
import dev.blurredclient.mod.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws every HUD module in its place.
 *
 * <p>Modules say how big they are and draw themselves at 0,0; this class
 * resolves each one's {@link HudPlacement} against the current screen and
 * applies its scale. That split is what lets the layout editor reuse the exact
 * same placement maths to show and drag modules.
 */
public final class HudRenderer {
    /** Bumped once per HUD frame so modules can cache per-frame work. */
    private static long frame;

    private HudRenderer() {}

    public static long frame() {
        return frame;
    }

    public static void render(GuiGraphicsExtractor ctx, Minecraft client) {
        frame++;
        if (client.player == null) {
            return;
        }
        // The HUD is chrome; hiding it with F1 should hide this too.
        if (client.gui.hud.isHidden()) {
            return;
        }
        // The layout editor draws its own copy, with placeholder content.
        if (client.gui.screen() instanceof dev.blurredclient.mod.screen.HudEditorScreen) {
            return;
        }

        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();

        for (HudModule module : Modules.hud()) {
            if (!module.isActive() || !module.drawsItself()) {
                continue;
            }
            HudModule.Size size = module.measure(client, false);
            if (size == null) {
                continue;
            }
            HudPlacement p = module.placement();
            int w = Math.round(size.width() * p.scale);
            int h = Math.round(size.height() * p.scale);
            int[] custom = module.screenPosition(client, screenW, screenH, w, h);
            int x = custom != null ? custom[0] : p.left(screenW, w);
            int y = custom != null ? custom[1] : p.top(screenH, h);

            Draw.scaled(ctx, x, y, p.scale, () -> module.render(ctx, client, false));
        }
    }

    /**
     * The porthole mark, drawn as a filled ring. Used as the Blurred badge in
     * the tab list.
     *
     * <p>Rasterised by scanline rather than by stroking a path: for each row of
     * the circle, work out the horizontal span of the outer disc and of the
     * inner hole, then fill the two segments left and right of the hole. That
     * gives a clean ring at any radius with one {@code fill} per segment, and
     * avoids needing a texture for what is a 10px icon.
     */
    public static void porthole(GuiGraphicsExtractor ctx, int cx, int cy, int r) {
        int inner = Math.max(1, r - 2);
        for (int dy = -r; dy <= r; dy++) {
            int outerHalf = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
            if (outerHalf <= 0) {
                continue;
            }
            int y = cy + dy;

            if (Math.abs(dy) >= inner) {
                // Above/below the hole: one solid span.
                ctx.fill(cx - outerHalf, y, cx + outerHalf, y + 1, Theme.accent());
            } else {
                int innerHalf = (int) Math.round(Math.sqrt(Math.max(0, inner * inner - dy * dy)));
                ctx.fill(cx - outerHalf, y, cx - innerHalf, y + 1, Theme.accent());
                ctx.fill(cx + innerHalf, y, cx + outerHalf, y + 1, Theme.accent());
            }
        }

        // Waterline across the glass.
        ctx.fill(cx - inner + 1, cy, cx + inner - 1, cy + 1, Theme.accentGlow());
    }
}
