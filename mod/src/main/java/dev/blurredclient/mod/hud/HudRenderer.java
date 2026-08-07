package dev.blurredclient.mod.hud;

import dev.blurredclient.mod.Theme;
import dev.blurredclient.mod.config.BlurredConfig;
import dev.blurredclient.mod.ui.BlurredFont;
import dev.blurredclient.mod.ui.Draw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The in-game overlay: a compact readout of FPS, ping and whatever else you've
 * asked for.
 *
 * <h2>The island</h2>
 *
 * Readouts used to be a vertical stack of separate panels down the top-left
 * corner — one box per number, which at three or four readouts took a
 * meaningful bite out of the screen and looked like a debug dump. They're now a
 * single pill with the values divided inside it, sat against the top edge in
 * whichever corner you want. One object instead of five, and it reads as part
 * of the window chrome rather than as something covering the game.
 *
 * <p>Everything is drawn with primitives rather than textures, so there is no
 * atlas to bind and the whole HUD scales cleanly with the GUI scale.
 *
 * <h2>Size</h2>
 *
 * The readouts are drawn below full font size and the padding is tight around
 * them. A HUD is glanced at, not read, and at 8px with 7px of padding the pill
 * was competing with the hotbar for attention — which is the wrong way round.
 * {@link Draw#text} handles the scaling, so the numbers stay on the pixel grid.
 */
public final class HudRenderer {
    /**
     * Horizontal padding, kept at roughly the corner radius: with a capsule end
     * the fill curves away from the text, so padding measured from the widest
     * row is not the clearance the first character actually gets.
     */
    private static final int PAD_X = 6;
    private static final int PAD_Y = 3;
    private static final int GAP = 5;
    private static final int MARGIN = 4;
    private static final int ROW_GAP = 2;

    /**
     * Readout text size, as a fraction of the game font. Small enough to read
     * as chrome, large enough that a three-digit FPS count is still legible at
     * GUI scale 2.
     */
    private static final float SCALE = 0.8f;

    /** Rolling FPS smoothing so the number doesn't strobe every frame. */
    private static float smoothedFps = 60f;
    /** Same for ping, which arrives in discrete jumps from the server. */
    private static float smoothedPing = -1f;

    /**
     * Wall-clock time, 12-hour with an am/pm marker — the way a clock in the
     * corner of a screen is read everywhere outside a timetable. 24-hour was
     * simply the terser thing to format, not the thing anyone wanted.
     */
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm a");

    private HudRenderer() {}

    /** One readout: its text and the colour to draw it in. */
    private record Item(String text, int color) {}

    public static void render(DrawContext ctx, MinecraftClient client) {
        BlurredConfig cfg = BlurredConfig.get();
        if (!cfg.hudEnabled || client.player == null) {
            return;
        }
        // The HUD is chrome; hiding it with F1 should hide this too.
        if (client.options.hudHidden) {
            return;
        }

        List<Item> items = buildItems(client, cfg);
        if (items.isEmpty()) {
            return;
        }

        if (cfg.hudIsland) {
            drawIsland(ctx, client, cfg, items);
        } else {
            drawStack(ctx, client, cfg, items);
        }
    }

    private static List<Item> buildItems(MinecraftClient client, BlurredConfig cfg) {
        List<Item> items = new ArrayList<>(6);

        if (cfg.showFps) {
            // Exponential smoothing: fast enough to track a real drop, slow
            // enough that the digits stay readable.
            smoothedFps = MathHelper.lerp(0.08f, smoothedFps, client.getCurrentFps());
            int fps = Math.round(smoothedFps);
            items.add(new Item(fps + " FPS", fpsColor(fps)));
        }

        if (cfg.showPing) {
            int latency = measurePing(client);
            if (latency >= 0) {
                smoothedPing = smoothedPing < 0 ? latency : MathHelper.lerp(0.25f, smoothedPing, latency);
                int ms = Math.round(smoothedPing);
                items.add(new Item(ms + " ms", pingColor(ms)));
            } else {
                // Reset, so reconnecting doesn't average against a stale value
                // from the previous server.
                smoothedPing = -1f;
            }
        }

        if (cfg.showCoords && client.player != null) {
            items.add(new Item(
                    String.format(
                            "%.0f %.0f %.0f",
                            client.player.getX(), client.player.getY(), client.player.getZ()),
                    Theme.TEXT));
        }

        if (cfg.showDirection && client.player != null) {
            items.add(new Item(facing(client.player.getYaw()), Theme.TEXT));
        }

        if (cfg.showCps) {
            items.add(new Item(CpsTracker.leftCps() + "/" + CpsTracker.rightCps() + " CPS", Theme.TEXT));
        }

        if (cfg.showClock) {
            items.add(new Item(LocalTime.now().format(CLOCK), Theme.TEXT_DIM));
        }

        return items;
    }

    /**
     * Our own latency, as the server measures it.
     *
     * <p>This used to read {@code client.getCurrentServerEntry().ping}, which is
     * wrong in a way that's easy to miss: {@code ServerInfo.ping} is filled in
     * by the <em>multiplayer list</em> when it pings servers to draw their
     * signal bars, and it is never updated again once you connect. So the HUD
     * was showing the round-trip of a status ping from whenever you last looked
     * at the server list — frozen for the whole session, and simply absent if
     * you joined by direct connect.
     *
     * <p>The player list entry for our own UUID carries the latency the server
     * measures from its keep-alive round trip, refreshed continuously while
     * playing. That's the real number.
     *
     * @return latency in milliseconds, or -1 when there isn't one (singleplayer,
     *         or before the first measurement lands)
     */
    private static int measurePing(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) {
            return -1;
        }
        PlayerListEntry self = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        if (self == null) {
            return -1;
        }
        int latency = self.getLatency();
        // A single-player integrated server reports 0, which is true but not
        // worth a readout.
        return latency <= 0 ? -1 : latency;
    }

    private static int fpsColor(int fps) {
        if (fps >= 100) return Theme.SUCCESS;
        if (fps >= 55) return Theme.TEXT;
        if (fps >= 30) return Theme.WARNING;
        return Theme.DANGER;
    }

    private static int pingColor(int ms) {
        if (ms < 60) return Theme.SUCCESS;
        if (ms < 120) return Theme.TEXT;
        if (ms < 250) return Theme.WARNING;
        return Theme.DANGER;
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

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    /** Left edge for a block of the given width, honouring the chosen corner. */
    private static int originX(MinecraftClient client, BlurredConfig cfg, int width) {
        int screen = client.getWindow().getScaledWidth();
        return switch (cfg.hudPosition) {
            case TOP_LEFT -> MARGIN;
            case TOP_CENTER -> (screen - width) / 2;
            case TOP_RIGHT -> screen - width - MARGIN;
        };
    }

    private static void drawIsland(
            DrawContext ctx, MinecraftClient client, BlurredConfig cfg, List<Item> items) {
        var font = client.textRenderer;

        int content = 0;
        for (int i = 0; i < items.size(); i++) {
            content += Draw.width(font, BlurredFont.of(items.get(i).text()), SCALE);
            if (i > 0) {
                content += GAP * 2;
            }
        }

        int lineH = Draw.height(font, SCALE);
        int w = PAD_X * 2 + content;
        int h = lineH + PAD_Y * 2;
        int x = originX(client, cfg, w);
        int y = MARGIN;

        island(ctx, x, y, w, h);

        int cursor = x + PAD_X;
        int textY = y + (h - lineH) / 2;
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                // Divider sits in the middle of the gap, inset vertically so it
                // reads as a separator rather than as a wall.
                int dx = cursor + GAP;
                ctx.fill(dx, y + 3, dx + 1, y + h - 3, 0x3A7DE2F0);
                cursor += GAP * 2;
            }
            Item item = items.get(i);
            Text text = BlurredFont.of(item.text());
            Draw.text(ctx, font, text, cursor, textY, item.color(), SCALE);
            cursor += Draw.width(font, text, SCALE);
        }
    }

    /**
     * The pill itself: a true capsule, with the corner radius taken all the way
     * to half the height.
     *
     * <p>It used to fake the curve with a fixed ladder of one and two pixel
     * insets, which at this size reads as a rectangle with its corners bitten
     * off rather than as a rounded shape. {@link Draw#roundedRect} derives each
     * row's inset from the actual circle, so the ends are properly round and
     * stay round if the height changes.
     */
    private static void island(DrawContext ctx, int x, int y, int w, int h) {
        int r = h / 2;

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
        ctx.fill(x + 1, y + h / 2 - 1, x + 2, y + h / 2 + 1, Theme.ACCENT_GLOW);
        ctx.fill(x + w - 2, y + h / 2 - 1, x + w - 1, y + h / 2 + 1, Theme.ACCENT_GLOW);
    }

    /** One panel per readout, stacked downward. The pre-island layout. */
    private static void drawStack(
            DrawContext ctx, MinecraftClient client, BlurredConfig cfg, List<Item> items) {
        var font = client.textRenderer;
        int y = MARGIN;
        int lineH = Draw.height(font, SCALE);

        for (Item item : items) {
            Text text = BlurredFont.of(item.text());
            int w = PAD_X * 2 + Draw.width(font, text, SCALE);
            int h = lineH + PAD_Y * 2;
            int x = originX(client, cfg, w);

            panel(ctx, x, y, w, h);
            Draw.text(ctx, font, text, x + PAD_X, y + (h - lineH) / 2, item.color(), SCALE);
            y += h + ROW_GAP;
        }
    }

    /**
     * A HUD panel: translucent abyssal fill, a lit top edge, and rounded
     * corners. A smaller radius than the island's — this is a box that happens
     * to be soft at the corners, not a capsule.
     */
    public static void panel(DrawContext ctx, int x, int y, int w, int h) {
        int r = Math.min(4, h / 2);
        Draw.roundedRect(ctx, x, y, w, h, r, Theme.PANEL);

        // Lit top edge — the same wet-glass cue the launcher's cards have.
        int cap = Draw.inset(0, h, r) + 1;
        ctx.fill(x + cap, y, x + w - cap, y + 1, Theme.PANEL_EDGE);
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
