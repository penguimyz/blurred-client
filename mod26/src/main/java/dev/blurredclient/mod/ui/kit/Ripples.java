package dev.blurredclient.mod.ui.kit;

import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Water ripples from clicks: a quick bright splash where you clicked, then
 * two rings spreading out and fading, kept inside the thing you clicked.
 * For skins that want them (Liquid glass). Coordinates are the kit's UI
 * units.
 */
public final class Ripples {
    private Ripples() {}

    private static final float LIFE = 0.9f;

    private record Ripple(float x, float y, int clipX1, int clipY1, int clipX2, int clipY2, float size, long born) {}

    private static final List<Ripple> RIPPLES = new ArrayList<>();

    /** A ripple at {@code x, y}, kept inside the given box (or anywhere, if it's empty). */
    public static void spawn(float x, float y, int bx, int by, int bw, int bh) {
        if (RIPPLES.size() > 12) {
            RIPPLES.remove(0);
        }
        float size = bw > 0 && bh > 0 ? Math.max(22f, Math.min(64f, Math.min(bw, bh) * 0.9f)) : 40f;
        RIPPLES.add(new Ripple(x, y, bx, by, bx + bw, by + bh, size, System.nanoTime()));
    }

    public static void render(GuiGraphicsExtractor ctx) {
        if (RIPPLES.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        RIPPLES.removeIf(r -> (now - r.born) / 1e9f > LIFE);
        if (LiquidGlass.available()) {
            // The glass itself ripples (see latest); nothing to draw on top.
            return;
        }
        for (Ripple r : RIPPLES) {
            float t = (now - r.born) / 1e9f / LIFE;
            boolean clip = r.clipX2 > r.clipX1 && r.clipY2 > r.clipY1;
            if (clip) {
                Draw.scissor(ctx, r.clipX1, r.clipY1, r.clipX2, r.clipY2);
            }
            try {
                draw(ctx, r, t);
            } finally {
                if (clip) {
                    Draw.unscissor(ctx);
                }
            }
        }
    }

    /**
     * The newest splash landing on a glass slab at {@code x, y, w, h}: its
     * centre relative to the slab and how far through it is (0..1), or null.
     */
    public static float[] latest(int x, int y, int w, int h) {
        long now = System.nanoTime();
        for (int i = RIPPLES.size() - 1; i >= 0; i--) {
            Ripple r = RIPPLES.get(i);
            float t = (now - r.born) / 1e9f / LIFE;
            if (t < 0f || t >= 1f) {
                continue;
            }
            if (r.x >= x && r.x <= x + w && r.y >= y && r.y <= y + h) {
                return new float[] {r.x - x, r.y - y, t};
            }
        }
        return null;
    }

    private static void draw(GuiGraphicsExtractor ctx, Ripple r, float t) {
        float e = 1f - (1f - t) * (1f - t) * (1f - t);
        // The splash: a soft bright disc that swells and fades quickly.
        if (t < 0.4f) {
            float k = 1f - t / 0.4f;
            Draw.circle(ctx, r.x, r.y, 3f + r.size * 0.35f * (1 - k), Colors.fade(0x55FFFFFF, k));
        }
        // Two rings, the second a beat behind, thinning as they spread.
        for (int i = 0; i < 2; i++) {
            float ti = Math.max(0f, t - i * 0.18f) / (1f - i * 0.18f);
            if (ti <= 0f) {
                continue;
            }
            float ei = 1f - (1f - ti) * (1f - ti) * (1f - ti);
            float radius = 2f + r.size * ei;
            float thick = 2.4f * (1f - ti) + 0.7f;
            float alpha = (1f - ti) * (i == 0 ? 0.9f : 0.55f);
            Draw.ring(ctx, r.x, r.y, radius, thick, Colors.fade(0xFFFFFFFF, alpha));
        }
        // A faint tinted fill inside the first ring, like disturbed water.
        Draw.ring(ctx, r.x, r.y, r.size * e * 0.6f, r.size * e * 0.25f, Colors.fade(0x22FFFFFF, 1f - t));
    }
}
