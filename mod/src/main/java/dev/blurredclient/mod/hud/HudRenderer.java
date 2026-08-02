package dev.blurredclient.mod.hud;

import dev.blurredclient.mod.Theme;
import dev.blurredclient.mod.config.BlurredConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * The in-game overlay: a Blurred watermark plus a stack of stat readouts.
 *
 * <p>Everything is drawn with primitives rather than textures, so there is no
 * atlas to bind and the whole HUD scales cleanly with the GUI scale. Each row
 * is a small rounded panel in the launcher's palette, which is what ties the
 * game overlay to the launcher visually.
 *
 * <p>Rows are collected into a list first and drawn in one pass, so adding an
 * element never has to know where the previous one ended.
 */
public final class HudRenderer {
    private static final int PAD = 6;
    private static final int ROW_GAP = 3;
    private static final int MARGIN = 6;

    /** Rolling FPS smoothing so the number doesn't strobe every frame. */
    private static float smoothedFps = 60f;

    private HudRenderer() {}

    public static void render(DrawContext ctx, MinecraftClient client) {
        BlurredConfig cfg = BlurredConfig.get();
        if (!cfg.hudEnabled || client.player == null) {
            return;
        }
        // The HUD is chrome; hiding it with F1 should hide this too.
        if (client.options.hudHidden) {
            return;
        }

        // No client watermark. The HUD used to lead with a porthole mark and
        // "BLURRED"; it was branding taking up screen space in-game and was
        // removed on request. The stat rows below are the whole HUD now.
        int y = MARGIN;

        for (String row : buildRows(client, cfg)) {
            y = drawRow(ctx, client, MARGIN, y, row) + ROW_GAP;
        }
    }

    private static List<String> buildRows(MinecraftClient client, BlurredConfig cfg) {
        List<String> rows = new ArrayList<>(6);

        if (cfg.showFps) {
            // Exponential smoothing: fast enough to track a real drop, slow
            // enough that the digits stay readable.
            smoothedFps = MathHelper.lerp(0.08f, smoothedFps, client.getCurrentFps());
            rows.add(Math.round(smoothedFps) + " FPS");
        }

        if (cfg.showCoords && client.player != null) {
            rows.add(String.format(
                    "%.0f, %.0f, %.0f",
                    client.player.getX(), client.player.getY(), client.player.getZ()));
        }

        if (cfg.showDirection && client.player != null) {
            rows.add(facing(client.player.getYaw()));
        }

        if (cfg.showPing) {
            ServerInfo server = client.getCurrentServerEntry();
            if (server != null) {
                rows.add(server.ping + " ms");
            }
        }

        if (cfg.showCps) {
            rows.add(CpsTracker.leftCps() + " / " + CpsTracker.rightCps() + " CPS");
        }

        return rows;
    }

    /** Compass point from a yaw angle. */
    private static String facing(float yaw) {
        // Normalise to 0..360 then bucket into eight 45-degree sectors, offset
        // by half a sector so each label is centred on its true heading.
        float a = MathHelper.wrapDegrees(yaw) + 180f;
        int idx = Math.round(a / 45f) & 7;
        return switch (idx) {
            case 0 -> "N";
            case 1 -> "NE";
            case 2 -> "E";
            case 3 -> "SE";
            case 4 -> "S";
            case 5 -> "SW";
            case 6 -> "W";
            default -> "NW";
        };
    }

    private static int drawRow(DrawContext ctx, MinecraftClient client, int x, int y, String text) {
        int textWidth = client.textRenderer.getWidth(text);
        int h = client.textRenderer.fontHeight + PAD;
        int w = PAD + textWidth + PAD;

        panel(ctx, x, y, w, h);
        ctx.drawTextWithShadow(
                client.textRenderer,
                Text.literal(text),
                x + PAD,
                y + (h - client.textRenderer.fontHeight) / 2,
                Theme.TEXT);

        return y + h;
    }

    /**
     * A HUD panel: translucent abyssal fill, a lit top edge, and clipped
     * corners. Minecraft's {@code fill} only does rectangles, so the "rounded"
     * look comes from insetting the first and last row by a pixel — cheap, and
     * at HUD scale it reads as a radius.
     */
    public static void panel(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x + 1, y, x + w - 1, y + 1, Theme.PANEL);
        ctx.fill(x, y + 1, x + w, y + h - 1, Theme.PANEL);
        ctx.fill(x + 1, y + h - 1, x + w - 1, y + h, Theme.PANEL);

        // Lit top edge — the same wet-glass cue the launcher's cards have.
        ctx.fill(x + 2, y, x + w - 2, y + 1, Theme.PANEL_EDGE);
    }

    /**
     * The porthole mark, drawn as a filled ring.
     *
     * <p>Rasterised by scanline rather than by stroking a path: for each row of
     * the circle, work out the horizontal span of the outer disc and of the
     * inner hole, then fill the two segments left and right of the hole. That
     * gives a clean ring at any radius with one {@code fill} per segment, and
     * avoids needing a texture for what is a 10px icon.
     */
    public static void porthole(DrawContext ctx, int cx, int cy, int r) {
        int inner = Math.max(1, r - 2);
        for (int dy = -r; dy <= r; dy++) {
            int outerHalf = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
            if (outerHalf <= 0) {
                continue;
            }
            int y = cy + dy;

            if (Math.abs(dy) >= inner) {
                // Above/below the hole: one solid span.
                ctx.fill(cx - outerHalf, y, cx + outerHalf, y + 1, Theme.ACCENT);
            } else {
                int innerHalf = (int) Math.round(Math.sqrt(Math.max(0, inner * inner - dy * dy)));
                ctx.fill(cx - outerHalf, y, cx - innerHalf, y + 1, Theme.ACCENT);
                ctx.fill(cx + innerHalf, y, cx + outerHalf, y + 1, Theme.ACCENT);
            }
        }

        // Waterline across the glass.
        ctx.fill(cx - inner + 1, cy, cx + inner - 1, cy + 1, Theme.ACCENT_GLOW);
    }
}
