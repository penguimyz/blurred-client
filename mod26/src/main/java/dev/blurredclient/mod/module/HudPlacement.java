package dev.blurredclient.mod.module;

/**
 * Where a HUD module sits: an anchor point on the screen, an offset from it,
 * and a scale.
 *
 * <h2>Why anchors</h2>
 *
 * Storing a plain x/y breaks the moment the window or GUI scale changes —
 * something parked against the right edge at 1080p ends up mid-screen at 720p.
 * An anchor says which part of the screen the module belongs to, and the
 * module's own matching corner (or edge midpoint) is pinned there. A module
 * anchored {@link Anchor#TOP_RIGHT} keeps its right edge the same distance
 * from the screen's right edge at every resolution.
 *
 * <p>The offset is in scaled GUI pixels, added after anchoring, so it means
 * the same thing for every anchor: +x is right, +y is down.
 *
 * <p>Plain data with no Minecraft imports, so the maths is unit tested.
 */
public final class HudPlacement {
    public enum Anchor {
        TOP_LEFT(0f, 0f), TOP_CENTER(0.5f, 0f), TOP_RIGHT(1f, 0f),
        MIDDLE_LEFT(0f, 0.5f), CENTER(0.5f, 0.5f), MIDDLE_RIGHT(1f, 0.5f),
        BOTTOM_LEFT(0f, 1f), BOTTOM_CENTER(0.5f, 1f), BOTTOM_RIGHT(1f, 1f);

        /** Fraction across / down the screen this anchor sits at. */
        public final float fx;
        public final float fy;

        Anchor(float fx, float fy) {
            this.fx = fx;
            this.fy = fy;
        }

        /** The anchor for a column (0..2) and row (0..2) of a 3x3 grid. */
        public static Anchor of(int column, int row) {
            return values()[row * 3 + column];
        }
    }

    public Anchor anchor = Anchor.TOP_LEFT;
    public int x;
    public int y;
    public float scale = 1f;

    public HudPlacement() {}

    public HudPlacement(Anchor anchor, int x, int y) {
        this.anchor = anchor;
        this.x = x;
        this.y = y;
    }

    public HudPlacement copy() {
        HudPlacement c = new HudPlacement(anchor, x, y);
        c.scale = scale;
        return c;
    }

    /**
     * Left edge on screen for a module {@code width} wide (already scaled),
     * clamped so it can never be dragged or resized fully off screen.
     */
    public int left(int screenWidth, int width) {
        int raw = Math.round(anchor.fx * (screenWidth - width)) + x;
        return clamp(raw, screenWidth, width);
    }

    /** Top edge on screen for a module {@code height} tall (already scaled). */
    public int top(int screenHeight, int height) {
        int raw = Math.round(anchor.fy * (screenHeight - height)) + y;
        return clamp(raw, screenHeight, height);
    }

    private static int clamp(int pos, int screen, int size) {
        return Math.max(0, Math.min(Math.max(0, screen - size), pos));
    }

    /**
     * The placement that puts a module of the given size at {@code left, top},
     * re-anchored to whichever third of the screen its centre is in.
     *
     * <p>Used when a drag ends: dropping something near the right edge should
     * anchor it right, so it stays there when the resolution changes, without
     * the player having to choose an anchor by hand.
     */
    public static HudPlacement nearest(int left, int top, int width, int height,
                                       int screenWidth, int screenHeight, float scale) {
        int column = third(left + width / 2f, screenWidth);
        int row = third(top + height / 2f, screenHeight);
        Anchor anchor = Anchor.of(column, row);

        HudPlacement p = new HudPlacement(anchor, 0, 0);
        p.scale = scale;
        p.x = left - Math.round(anchor.fx * (screenWidth - width));
        p.y = top - Math.round(anchor.fy * (screenHeight - height));
        return p;
    }

    private static int third(float position, int size) {
        if (size <= 0) {
            return 0;
        }
        float f = position / size;
        return f < 1f / 3f ? 0 : f < 2f / 3f ? 1 : 2;
    }
}
