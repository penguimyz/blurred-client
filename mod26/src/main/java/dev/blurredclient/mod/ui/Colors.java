package dev.blurredclient.mod.ui;

/**
 * ARGB colour maths for the UI kit: blending for animated states, and HSV for
 * the colour picker.
 *
 * <p>No AWT: {@code java.awt.Color} would work, but pulling AWT into a game
 * client for three functions is a poor trade, and it initialises a toolkit on
 * some platforms.
 */
public final class Colors {
    private Colors() {}

    public static int alpha(int argb) {
        return argb >>> 24;
    }

    /** Same RGB, alpha multiplied by {@code factor} (0..1). */
    public static int fade(int argb, float factor) {
        int a = Math.round(alpha(argb) * Math.max(0f, Math.min(1f, factor)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** Per-channel blend from {@code a} to {@code b}, alpha included. */
    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = (a >>> shift) & 0xFF;
            int cb = (b >>> shift) & 0xFF;
            out |= (Math.round(ca + (cb - ca) * t) & 0xFF) << shift;
        }
        return out;
    }

    /**
     * HSV to opaque RGB.
     *
     * @param h hue 0..1 (wraps)
     * @param s saturation 0..1
     * @param v value 0..1
     */
    public static int hsv(float h, float s, float v) {
        h = (h % 1f + 1f) % 1f * 6f;
        int sector = (int) Math.floor(h);
        float f = h - sector;
        float p = v * (1f - s);
        float q = v * (1f - s * f);
        float t = v * (1f - s * (1f - f));
        float r, g, b;
        switch (sector) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    /** RGB (alpha ignored) to {h, s, v}, each 0..1. */
    public static float[] toHsv(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0f) {
            h = 0f;
        } else if (max == r) {
            h = ((g - b) / d) / 6f;
        } else if (max == g) {
            h = ((b - r) / d + 2f) / 6f;
        } else {
            h = ((r - g) / d + 4f) / 6f;
        }
        h = (h % 1f + 1f) % 1f;
        return new float[] {h, max == 0f ? 0f : d / max, max};
    }

    private static int channel(float f) {
        return Math.max(0, Math.min(255, Math.round(f * 255f)));
    }
}
