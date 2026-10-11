package dev.blurredclient.mod.ui.kit;

import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The animated menu background for the Modern and Glass styles.
 *
 * <p>Layers, back to front:
 * <ol>
 *   <li>a deep blue-black vertical gradient;</li>
 *   <li>three large, slow colour glows drifting on long Lissajous paths, which
 *       lean slightly toward the cursor (parallax);</li>
 *   <li>a soft light that follows the cursor;</li>
 *   <li>drifting particles that link up with faint lines when close, link to
 *       the cursor, and are gently pushed away from it;</li>
 *   <li>a vignette darkening the edges.</li>
 * </ol>
 * The cursor parts follow the Appearance module's "Reactive background"
 * switch; the whole thing freezes to a still frame when "Animated background"
 * is off.
 *
 * <p>Everything is fills, and all state is static, because vanilla screens
 * (via mixin) and kit screens share one backdrop — opening Options from the
 * title screen shouldn't restart the animation.
 */
public final class Backdrop {
    private Backdrop() {}

    private static final long START = System.nanoTime();
    private static final Random RANDOM = new Random(7);
    private static final int LINK_DIST = 58;
    private static final int CURSOR_LINK_DIST = 90;

    private static float[] px = new float[0];
    private static float[] py = new float[0];
    private static float[] vx = new float[0];
    private static float[] vy = new float[0];
    private static int fieldW;
    private static int fieldH;
    private static long lastNs = System.nanoTime();
    private static float cursorX = Float.NaN;
    private static float cursorY;

    /** Pixels per backdrop unit, matching kit screens at "Menu size" 1. */
    private static float unitScale() {
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        return (float) (2.0 / Math.max(1.0, gui));
    }

    /**
     * Draw over a whole vanilla screen, given in GUI pixels. Converts to
     * backdrop units so particles are the same size at every GUI scale.
     */
    public static void drawScreen(GuiGraphicsExtractor ctx, int guiW, int guiH, int mouseX, int mouseY) {
        float s = unitScale();
        Draw.scaled(ctx, 0, 0, s, () -> draw(ctx, Math.round(guiW / s), Math.round(guiH / s),
                Math.round(mouseX / s), Math.round(mouseY / s)));
    }

    /** Draw in the current drawing units (a kit screen's own scale). */
    public static void draw(GuiGraphicsExtractor ctx, int w, int h, int mouseX, int mouseY) {
        boolean animate = Modules.APPEARANCE.animatedBackground();
        boolean reactive = Modules.APPEARANCE.reactiveBackground();
        int accent = Skins.current().accent();

        long now = System.nanoTime();
        float dt = animate ? Math.min(0.1f, (now - lastNs) / 1_000_000_000f) : 0f;
        lastNs = now;
        float t = animate ? (now - START) / 1_000_000_000f : 12f;

        if (Float.isNaN(cursorX)) {
            cursorX = mouseX;
            cursorY = mouseY;
        }
        float ease = 1f - (float) Math.exp(-6 * dt);
        cursorX += (mouseX - cursorX) * ease;
        cursorY += (mouseY - cursorY) * ease;
        // How far the cursor is from centre, -1..1, for parallax.
        float parX = reactive ? (cursorX / Math.max(1, w) - 0.5f) * 2f : 0f;
        float parY = reactive ? (cursorY / Math.max(1, h) - 0.5f) * 2f : 0f;

        var scene = Modules.APPEARANCE.background();
        if (scene != dev.blurredclient.mod.module.feature.AppearanceModule.Background.CONSTELLATION) {
            Scenes.draw(ctx, scene, w, h, t, parX, parY, accent);
            if (reactive) {
                Draw.glow(ctx, cursorX, cursorY, 70, (0x26 << 24) | (accent & 0xFFFFFF), 7);
            }
            int band = h / 4;
            ctx.fillGradient(0, 0, w, band, 0x50000000, 0x00000000);
            ctx.fillGradient(0, h - band, w, h, 0x00000000, 0x60000000);
            return;
        }

        ctx.fillGradient(0, 0, w, h, 0xFF0C0F18, 0xFF05060A);

        float big = Math.min(w, h);
        Draw.glow(ctx, w * (0.22f + 0.07f * (float) Math.sin(t * 0.11)) + parX * 14,
                h * (0.30f + 0.10f * (float) Math.cos(t * 0.09)) + parY * 10,
                big * 0.62f, (0x46 << 24) | (accent & 0xFFFFFF), 9);
        Draw.glow(ctx, w * (0.78f + 0.06f * (float) Math.cos(t * 0.07)) + parX * 22,
                h * (0.70f + 0.08f * (float) Math.sin(t * 0.12)) + parY * 16,
                big * 0.55f, 0x380E6E8C, 9);
        Draw.glow(ctx, w * (0.55f + 0.10f * (float) Math.sin(t * 0.05 + 2)) + parX * 8,
                h * (0.12f + 0.05f * (float) Math.cos(t * 0.08 + 1)) + parY * 6,
                big * 0.42f, 0x2235E0D0, 8);

        if (reactive) {
            Draw.glow(ctx, cursorX, cursorY, 70, (0x30 << 24) | (accent & 0xFFFFFF), 7);
        }

        particles(ctx, w, h, dt, reactive, accent);

        // Vignette: top and bottom bands fading to transparent.
        int band = h / 4;
        ctx.fillGradient(0, 0, w, band, 0x60000000, 0x00000000);
        ctx.fillGradient(0, h - band, w, h, 0x00000000, 0x70000000);
    }

    private static void particles(GuiGraphicsExtractor ctx, int w, int h, float dt, boolean reactive, int accent) {
        ensureField(w, h);
        int n = px.length;

        for (int i = 0; i < n; i++) {
            if (reactive) {
                // Gentle push away from the cursor.
                float dx = px[i] - cursorX;
                float dy = py[i] - cursorY;
                float d2 = dx * dx + dy * dy;
                if (d2 < 60 * 60 && d2 > 0.01f) {
                    float d = (float) Math.sqrt(d2);
                    float push = (1f - d / 60f) * 40f * dt;
                    px[i] += dx / d * push;
                    py[i] += dy / d * push;
                }
            }
            px[i] += vx[i] * dt;
            py[i] += vy[i] * dt;
            // Wrap around the edges.
            if (px[i] < -5) px[i] += w + 10;
            if (px[i] > w + 5) px[i] -= w + 10;
            if (py[i] < -5) py[i] += h + 10;
            if (py[i] > h + 5) py[i] -= h + 10;
        }

        int lineColor = 0xFFFFFF;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                float dx = px[j] - px[i];
                float dy = py[j] - py[i];
                if (Math.abs(dx) > LINK_DIST || Math.abs(dy) > LINK_DIST) {
                    continue;
                }
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < LINK_DIST) {
                    int a = (int) (0x2A * (1f - d / LINK_DIST));
                    line(ctx, px[i], py[i], px[j], py[j], (a << 24) | lineColor);
                }
            }
            if (reactive) {
                float dx = cursorX - px[i];
                float dy = cursorY - py[i];
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < CURSOR_LINK_DIST) {
                    int a = (int) (0x70 * (1f - d / CURSOR_LINK_DIST));
                    line(ctx, px[i], py[i], cursorX, cursorY, (a << 24) | (accent & 0xFFFFFF));
                }
            }
        }

        for (int i = 0; i < n; i++) {
            float size = 1f + (i % 3) * 0.4f;
            Draw.circle(ctx, px[i], py[i], size, i % 5 == 0 ? Colors.fade(accent, 0.9f) : 0xB0E6ECFF);
        }
    }

    /** A thin anti-aliased line between two particles (or a particle and the cursor). */
    private static void line(GuiGraphicsExtractor ctx, float x1, float y1, float x2, float y2, int color) {
        Draw.line(ctx, x1, y1, x2, y2, 0.6f, color);
    }

    /** (Re)seed particles when the area changes, keeping density constant. */
    private static void ensureField(int w, int h) {
        if (w == fieldW && h == fieldH && px.length > 0) {
            return;
        }
        int n = Math.max(24, Math.min(90, (w * h) / 2600));
        float[] nx = new float[n];
        float[] ny = new float[n];
        float[] nvx = new float[n];
        float[] nvy = new float[n];
        for (int i = 0; i < n; i++) {
            nx[i] = RANDOM.nextFloat() * w;
            ny[i] = RANDOM.nextFloat() * h;
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            float speed = 4f + RANDOM.nextFloat() * 8f;
            nvx[i] = (float) Math.cos(angle) * speed;
            nvy[i] = (float) Math.sin(angle) * speed;
        }
        px = nx;
        py = ny;
        vx = nvx;
        vy = nvy;
        fieldW = w;
        fieldH = h;
    }
}
