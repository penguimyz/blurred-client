package dev.blurredclient.mod.ui;

import dev.blurredclient.mod.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Shared drawing for the ocean menu skin.
 *
 * <p>Everything here is primitives — {@code fill} rectangles on a pixel grid —
 * so there are no textures to ship, nothing to bind, and the look scales with
 * the GUI scale instead of blurring. It is the same visual grammar as the
 * launcher: stepped water bands, dithered seams, hard edges, no soft glows.
 *
 * <p>These methods are deliberately static and stateless apart from an
 * animation clock, because they're called from mixins on vanilla widgets where
 * there is nowhere sensible to hang state.
 */
public final class OceanUi {
    private OceanUi() {}

    /** Pixel size for dither and bubble grids. Matches the launcher's 4px. */
    /**
     * The backdrop's pixel grid.
     *
     * <p>Halved from 4: at 4 the dither squares and light shafts were coarse
     * enough to read as the pattern itself rather than as texture, and the
     * bubbles came out as chunky blobs. Finer detail sits behind the content
     * instead of competing with it.
     */
    private static final int P = 2;

    /**
     * Depth bands for the menu background, surface first.
     *
     * <p>Hard stops rather than a gradient: a smooth fade is the wrong idiom
     * for pixel art, and stepped zones are how a water column actually reads.
     */
    private static final int[] BANDS = {
        0xFF17506B, 0xFF12455E, 0xFF0F3D55, 0xFF0C3549,
        0xFF0A2F43, 0xFF082838, 0xFF06202E, 0xFF041824,
    };

    /** Advances every frame; drives bubbles and caustics. */
    private static float clock;

    public static void tick(float delta) {
        clock += delta;
    }

    /**
     * The full menu backdrop: banded water, dither, light shafts and bubbles.
     *
     * <p>Drawn bottom-up in that order so each layer sits over the last, which
     * is what gives depth without any blending modes.
     */
    public static void background(GuiGraphicsExtractor ctx, int width, int height) {
        int bandHeight = Math.max(1, height / BANDS.length + 1);
        for (int i = 0; i < BANDS.length; i++) {
            int top = i * bandHeight;
            ctx.fill(0, top, width, Math.min(height, top + bandHeight), BANDS[i]);
        }

        dither(ctx, width, height);
        shafts(ctx, width, height);
        bubbles(ctx, width, height);
    }

    /**
     * Checker dither along the seams between the water bands.
     *
     * <p><b>Only the seams.</b> This used to tile the entire screen, which at
     * the current pixel grid is around 29,000 {@code fill} calls every frame —
     * and the backdrop draws behind vanilla's menus too, so that cost was paid
     * on the pause screen and the options screen as well. It was the reason the
     * menus went sluggish when the grid was made finer.
     *
     * <p>Confining it to the seams is also what the effect was always for: it
     * stands in for the stipple a pixel artist would use to blend one band into
     * the next. Tiling the flat middle of a band achieves nothing you can see.
     */
    private static void dither(GuiGraphicsExtractor ctx, int width, int height) {
        int tint = 0x0EBDF3FF;
        int bandHeight = Math.max(1, height / BANDS.length + 1);
        int pitch = P * 4;

        for (int i = 1; i < BANDS.length; i++) {
            int seam = i * bandHeight;
            for (int row = 0; row < 2; row++) {
                int y = seam - P + row * P;
                if (y < 0 || y + P > height) {
                    continue;
                }
                // Offset every other row so the two rows interlock.
                for (int x = (row & 1) * P * 2; x < width; x += pitch) {
                    ctx.fill(x, y, x + P, y + P, tint);
                }
            }
        }
    }

    /** Hard-edged light shafts angling down from an unseen surface. */
    private static void shafts(GuiGraphicsExtractor ctx, int width, int height) {
        int drift = (int) (Math.sin(clock * 0.012f) * 18);
        for (int i = -2; i < width / 90 + 3; i++) {
            int x = i * 90 + drift;
            // Slanted: each row steps sideways, which keeps the edge on the
            // pixel grid instead of antialiasing a diagonal.
            int slab = P * 3;
            for (int y = 0; y < height; y += slab) {
                int off = (y / slab) * 3;
                ctx.fill(x + off, y, x + off + 22, y + slab, 0x0FBDF3FF);
            }
        }
    }

    /** Slow bubbles rising up the menu, positions derived from the clock. */
    private static void bubbles(GuiGraphicsExtractor ctx, int width, int height) {
        for (int i = 0; i < 14; i++) {
            // Deterministic per-index pseudo-random, so bubbles keep their
            // identity frame to frame without storing any state.
            float seed = i * 37.13f;
            int x = (int) ((Math.sin(seed) * 0.5 + 0.5) * width);
            float speed = 0.25f + (i % 5) * 0.06f;
            int y = (int) (height - ((clock * speed + i * 53) % (height + 40)));
            // Radii in screen pixels now, not multiples of the backdrop grid,
            // so they stay small as P changes.
            double r = 1.5d + (i % 3) * 0.9d;

            x += (int) (Math.sin(clock * 0.03f + i) * 6);
            Draw.ring(ctx, x, y, (float) r, (float) (r - 1d), 0x55BDF3FF);
        }
    }

    /**
     * An ocean-styled button, replacing the vanilla texture.
     *
     * <p><b>Square.</b> This used to chamfer the corners by insetting the first
     * and last pixel row, which is the standard pixel-art trick for a rounded
     * edge. At button sizes it did not read as rounded — it read as four
     * missing corners, and worse, it read as *inconsistently* missing corners,
     * because the chamfer landed differently depending on the widget's height.
     * A hard rectangle is what the launcher's own cards do and it's what this
     * does now.
     *
     * <p>Carries the launcher's press behaviour: a hard offset shadow at rest.
     * Vanilla has no "pressed" state exposed to the renderer, so hover stands
     * in for it — hovering lifts the button instead.
     */
    public static void button(
            GuiGraphicsExtractor ctx, Font font, Component label,
            int x, int y, int w, int h, boolean hovered, boolean enabled) {
        button(ctx, font, label, x, y, w, h, hovered, enabled, false);
    }

    /**
     * @param rounded round the corners off. Only true for buttons on the mod's
     *                own screens — see {@code PressableWidgetMixin}. Vanilla's
     *                menus are square and a lone rounded button among them
     *                looks like a rendering fault rather than a style.
     */
    public static void button(
            GuiGraphicsExtractor ctx, Font font, Component label,
            int x, int y, int w, int h, boolean hovered, boolean enabled, boolean rounded) {

        // Two-tone body: lighter upper half, darker lower. A single flat fill
        // reads as a coloured rectangle; splitting it is what makes the control
        // look like an object catching light from above.
        int top = !enabled ? 0xB0102631 : hovered ? 0xF02A6E82 : 0xE0154356;
        int bottom = !enabled ? 0xB00A1B24 : hovered ? 0xF01B5163 : 0xE00D2E3D;
        int edge = !enabled ? 0x50557C8A : hovered ? Theme.accent() : 0x9A5FA9BC;

        int r = rounded ? Math.min(BUTTON_RADIUS, Math.min(w, h) / 2) : 0;

        // Hard drop shadow, offset down-right. Never blurred.
        Draw.roundedRect(ctx, x + 2, y + 2, w, h, r, 0x70000B12);

        // Body: two halves, lighter over darker.
        int mid = y + h / 2;
        if (r == 0) {
            ctx.fill(x, y, x + w, mid, top);
            ctx.fill(x, mid, x + w, y + h, bottom);
        } else {
            // Full rounded shape in the darker tone, then the lit upper half
            // rounded only at the top — so the seam stays straight and the
            // outline stays a single continuous curve.
            Draw.roundedRect(ctx, x, y, w, h, r, bottom);
            Draw.roundedRect(ctx, x, y, w, mid - y + r, r, top);
            ctx.fill(x + r, mid - 1, x + w - r, mid, top);
        }

        // A band of caustic dither across the upper half, so the face has
        // texture rather than being two flat blocks of colour.
        if (enabled) {
            int sheen = hovered ? 0x1EDFFBFF : 0x12BDF3FF;
            for (int dy = y + 2; dy < mid; dy += P) {
                for (int dx = x + 2 + ((dy - y) / P % 2) * P; dx < x + w - 2; dx += P * 2) {
                    ctx.fill(dx, dy, Math.min(dx + P, x + w - 2), Math.min(dy + 1, mid), sheen);
                }
            }
        }

        // Bevel: light inside the top-left, dark inside the bottom-right. The
        // classic two-pixel trick that gives a flat sprite volume.
        ctx.fill(x + 1, y + 1, x + w - 1, y + 2, 0x40CFF6FF);
        ctx.fill(x + 1, y + 1, x + 2, y + h - 1, 0x2ACFF6FF);
        ctx.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0x50041018);
        ctx.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, 0x40041018);

        // Border. Rounded follows the curve; square keeps all four sides full
        // length, with no bitten corners.
        if (r > 0) {
            Draw.roundedOutline(ctx, x, y, w, h, r, edge);
        } else {
        ctx.fill(x, y, x + w, y + 1, edge);
        ctx.fill(x, y + h - 1, x + w, y + h, edge);
        ctx.fill(x, y, x + 1, y + h, edge);
        ctx.fill(x + w - 1, y, x + w, y + h, edge);
        }

        // Hover: accent bars sliding in from both ends, like a selected slot.
        // Skipped on narrow buttons, where they'd meet in the middle.
        if (hovered && enabled && w >= 40) {
            ctx.fill(x + 3, y + h / 2 - 3, x + 5, y + h / 2 + 3, Theme.accent());
            ctx.fill(x + w - 5, y + h / 2 - 3, x + w - 3, y + h / 2 + 3, Theme.accent());
        }

        int textColor = !enabled ? Theme.TEXT_FAINT : hovered ? 0xFFEFFEFF : Theme.TEXT;

        // Trim the label to the space between the hover bars so a long string
        // can never spill outside its own button.
        int room = w - (w >= 40 ? 16 : 6);
        Component shown = BlurredFont.apply(label);
        if (font.width(shown) > room) {
            shown = BlurredFont.of(
                    font.plainSubstrByWidth(label.getString(), Math.max(0, room - 6)) + "…");
        }

        int tx = x + (w - font.width(shown)) / 2;
        int ty = y + (h - font.lineHeight) / 2 + 1;
        ctx.text(font, shown, tx, ty, textColor);
    }

    /**
     * A framed panel for grouping things on a mod screen.
     *
     * <p>Heavier than the HUD's {@link dev.blurredclient.mod.hud.HudRenderer#panel}:
     * that one is a translucent slab meant to sit over live gameplay, whereas
     * this is furniture on a full screen and can afford an inner edge and a
     * corner detail. Having both is the difference between a screen that reads
     * as composed and one that reads as text floating on a background.
     */
    public static void panel(GuiGraphicsExtractor ctx, int x, int y, int w, int h, boolean raised) {
        int r = Math.min(PANEL_RADIUS, Math.min(w, h) / 2);

        Draw.roundedRect(ctx, x + 2, y + 2, w, h, r, 0x50000B12);
        Draw.roundedRect(ctx, x, y, w, h, r, raised ? Theme.PANEL_RAISED : Theme.PANEL);

        // A rim all the way round, following the curve. The old version drew
        // four straight edges, which on a rounded panel leaves the corners
        // bare and the whole thing looks unfinished — the corner ticks that
        // used to be here were covering for exactly that.
        Draw.roundedOutline(ctx, x, y, w, h, r, Theme.PANEL_EDGE);

        // Lit top edge, inset to where the curve ends so it doesn't poke out
        // past the corner.
        int cap = r + 1;
        if (w > cap * 2) {
            ctx.fill(x + cap, y, x + w - cap, y + 1, Theme.PANEL_EDGE);
        }
    }

    /** Corner radius for panels. Big enough to read as round at GUI scale 2. */
    private static final int PANEL_RADIUS = 5;

    /** Corner radius for the mod's own buttons. */
    private static final int BUTTON_RADIUS = 4;

    /**
     * A section heading: a label with a rule running out to the right.
     *
     * <p>Returns the y below the heading, so callers can stack sections without
     * tracking magic offsets.
     */
    public static int heading(GuiGraphicsExtractor ctx, Font font, String text, int x, int y, int w) {
        ctx.text(font, BlurredFont.of(text), x, y, Theme.TEXT_FAINT);
        int after = x + font.width(BlurredFont.of(text)) + 6;
        int lineY = y + font.lineHeight / 2;
        if (after < x + w) {
            ctx.fill(after, lineY, x + w, lineY + 1, 0x337DE2F0);
        }
        return y + font.lineHeight + 4;
    }

    /*
      The submarine, as a sprite grid — one character per pixel, so it reads as
      a picture in the source and can be edited without a drawing tool. Facing
      right, propeller at the rear.

        # hull      = hull shading (underside)
        T tower     | periscope
        O porthole glass (lit)
        P propeller
    */
    private static final String[] SUB = {
        "...................||.......",
        "...................||.......",
        "..............TTTTTTTT......",
        ".............TT......TT.....",
        "......################......",
        "...##########################",
        "P.############################",
        "PP###..OO....OO....OO....#####",
        "PP###..OO....OO....OO....#####",
        "P.############################",
        "...##########################",
        "......================......",
        "..........==========........",
    };

    private static int subColor(char c) {
        return switch (c) {
            case '#' -> 0xFF3C7284;
            case '=' -> 0xFF24505E;
            case 'T' -> 0xFF44819A;
            case '|' -> 0xFF1B3F4C;
            case 'O' -> 0xFF7EF0E0;
            case 'P' -> 0xFF2E5F70;
            default -> 0;
        };
    }

    /**
     * The submarine hanging in the water, gently bobbing.
     *
     * <p>Bob is rounded to whole sprite pixels rather than applied smoothly —
     * a pixel sprite drifting on sub-pixel offsets shimmers, which is exactly
     * the artefact the whole pixel-art treatment is trying to avoid.
     */
    public static void submarine(GuiGraphicsExtractor ctx, int centerX, int centerY, int scale) {
        int w = 0;
        for (String row : SUB) {
            w = Math.max(w, row.length());
        }

        int bob = Math.round((float) Math.sin(clock * 0.02f) * 1.5f) * scale;
        int tilt = Math.round((float) Math.sin(clock * 0.014f) * 1f) * scale;

        int ox = centerX - (w * scale) / 2;
        int oy = centerY - (SUB.length * scale) / 2 + bob;

        for (int y = 0; y < SUB.length; y++) {
            String row = SUB[y];
            // Tilt by shifting rows: the nose rides slightly higher than the
            // tail as it rocks, without needing rotation.
            int rowShift = (y < SUB.length / 2) ? tilt : -tilt;
            for (int x = 0; x < row.length(); x++) {
                int color = subColor(row.charAt(x));
                if (color == 0) {
                    continue;
                }
                ctx.fill(
                        ox + x * scale + rowShift, oy + y * scale,
                        ox + x * scale + scale + rowShift, oy + y * scale + scale,
                        color);
            }
        }

        // Bubble trail off the propeller, drifting up and back.
        for (int i = 0; i < 6; i++) {
            float t = (clock * 0.6f + i * 40) % 240;
            int bx = ox - (int) (t * 0.35f) * scale / 2;
            int by = oy + (SUB.length / 2) * scale - (int) (t * 0.5f);
            double r = 1.2d + (i % 2) * 0.8d;
            Draw.ring(ctx, bx, by, (float) r, (float) (r - 1d), 0x44BDF3FF);
        }
    }

    /**
     * The Blurred mark: a porthole ring with a waterline, plus the wordmark.
     * Returns the width drawn, so callers can centre a row around it.
     */
    public static int logo(GuiGraphicsExtractor ctx, Font font, int x, int y, int size) {
        int r = size / 2;
        int cx = x + r;
        int cy = y + r;

        // Ring, scanline-rasterised at 1px so it stays crisp at menu scale.
        int inner = Math.max(1, r - 2);
        for (int dy = -r; dy <= r; dy++) {
            int outer = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
            if (outer <= 0) {
                continue;
            }
            int yy = cy + dy;
            if (Math.abs(dy) >= inner) {
                ctx.fill(cx - outer, yy, cx + outer, yy + 1, Theme.accent());
            } else {
                int in = (int) Math.round(Math.sqrt(Math.max(0, inner * inner - dy * dy)));
                ctx.fill(cx - outer, yy, cx - in, yy + 1, Theme.accent());
                ctx.fill(cx + in, yy, cx + outer, yy + 1, Theme.accent());
            }
        }
        // Waterline across the glass.
        ctx.fill(cx - inner + 1, cy, cx + inner - 1, cy + 1, Theme.accentGlow());

        Component word = Component.literal("BLURRED");
        ctx.text(font, word, x + size + 5, cy - font.lineHeight / 2, Theme.accent());
        return size + 5 + font.width(word);
    }

    /** Width the {@link #logo} row will occupy, for centring before drawing. */
    public static int logoWidth(Font font, int size) {
        return size + 5 + font.width(Component.literal("BLURRED"));
    }
}
