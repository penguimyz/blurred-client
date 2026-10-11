package dev.blurredclient.mod;

/**
 * The launcher's ocean palette, restated as ARGB ints for the in-game HUD.
 *
 * <p>These must stay in sync with {@code src/styles/theme.css} — they are the
 * same design tokens, and the point of the mod is that the game overlay looks
 * like the launcher rather than like a generic mod HUD. The CSS is the source
 * of truth; when an accent changes there, change it here.
 *
 * <p>Format is 0xAARRGGBB, which is what {@code GuiGraphicsExtractor} expects.
 */
public final class Theme {
    private Theme() {}

    /** Bioluminescent cyan — the launcher's --accent. */
    public static final int ACCENT = 0xFF35E0D0;
    /** Same hue, heavily transparent, for glows and fills behind text. */
    public static final int ACCENT_GLOW = 0x4035E0D0;

    /** --text-primary / --text-secondary / --text-tertiary. */
    public static final int TEXT = 0xFFE0F8FF;
    public static final int TEXT_DIM = 0xFFA8CEDC;
    public static final int TEXT_FAINT = 0xFF6E93A2;

    /**
     * Panel fill and its lit top edge (the "wet glass" cue).
     *
     * <p>Deliberately a see-through <em>blue</em> rather than the near-black it
     * started as. At 0xD0 over #0A1A26 the panels read as opaque charcoal
     * boxes sitting on top of the game; the point of the theme is glass you can
     * see the world through. The alpha matches the launcher's own
     * {@code --glass-bg} (0.62), so a panel in game and a card in the launcher
     * have the same weight.
     */
    public static final int PANEL = 0x9E10384F;
    public static final int PANEL_RAISED = 0xB2164B66;
    public static final int PANEL_EDGE = 0x407DE2F0;

    public static final int SUCCESS = 0xFF45E6A8;
    public static final int WARNING = 0xFFFFC75A;
    public static final int DANGER = 0xFFFF6B81;

    /** Presence dot colours. */
    public static final int ONLINE = SUCCESS;
    public static final int OFFLINE = 0xFF4A6472;

    /**
     * Blend {@code color} toward transparent by {@code alpha} (0..1), keeping
     * the RGB. Used for fade-outs where re-deriving a constant would be noise.
     */
    public static int withAlpha(int color, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | (color & 0x00FFFFFF);
    }
}
