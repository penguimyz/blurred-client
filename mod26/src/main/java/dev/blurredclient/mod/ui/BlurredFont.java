package dev.blurredclient.mod.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * The mod's pixel font.
 *
 * <p>Minecraft's own font is already pixel art, so the obvious reaction is that
 * this is redundant. It isn't, because {@code minecraft:default} is not a fixed
 * thing:
 *
 * <ul>
 *   <li><b>Force Unicode Font</b> in the accessibility options swaps the whole
 *       default font for the Unicode sheet, which is a different, wider,
 *       noticeably worse-looking typeface.</li>
 *   <li><b>Resource packs</b> routinely replace the default font outright,
 *       including with smooth non-pixel typefaces.</li>
 * </ul>
 *
 * <p>Either turns every label the mod draws into something that doesn't match
 * the rest of the client. Pointing our own text at {@code blurred:pixel} — a
 * font that resolves the Latin range straight from vanilla's 8px bitmap atlas —
 * means the mod's chrome looks the same on every setup.
 *
 * <p>The font declaration keeps {@code minecraft:default} as a fallback
 * provider, so anything outside that atlas (Cyrillic, CJK, emoji in a chat
 * message) still renders normally instead of turning into missing-glyph boxes.
 *
 * <p>Use {@link #of} for anything the mod draws itself. Chat and player names
 * pass through it too — they're Latin in the overwhelming majority of cases,
 * and the fallback covers the rest.
 */
public final class BlurredFont {
    private BlurredFont() {}

    /** Matches {@code assets/blurred/font/pixel.json}. */
    public static final Identifier PIXEL = Identifier.fromNamespaceAndPath("blurred", "pixel");

    /**
     * The smooth UI face: Varela Round (SIL OFL, see {@code varela_round_ofl.txt}),
     * loaded through Minecraft's own TTF font provider.
     *
     * <p>A TTF font is rasterized once, at {@code size x oversample} pixels per
     * glyph, and the bitmap is then drawn at whatever size the text appears on
     * screen. If those two sizes don't match, the bitmap gets squeezed or
     * stretched and the text looks grainy — which is what happened when one
     * oversample (4) served menus drawn at ~1.6 screen pixels per unit. So
     * the font ships at ten oversample levels ({@code ui_0..9.json}) and each
     * piece of text uses the one closest to the screen pixels per unit where
     * it's being drawn.
     */
    private static final float[] OVERSAMPLE = {1f, 1.5f, 2f, 2.5f, 3f, 3.5f, 4f, 5f, 6f, 8f};
    private static final Style[] UI_STYLES = new Style[OVERSAMPLE.length];
    private static final Style[] UI_BOLD_STYLES = new Style[OVERSAMPLE.length];

    static {
        for (int i = 0; i < OVERSAMPLE.length; i++) {
            UI_STYLES[i] = withFont(Style.EMPTY, Identifier.fromNamespaceAndPath("blurred", "ui_" + i));
            UI_BOLD_STYLES[i] = withFont(Style.EMPTY, Identifier.fromNamespaceAndPath("blurred", "ui_bold_" + i));
        }
    }

    private static final Style STYLE = withFont(Style.EMPTY, PIXEL);

    /** Index of the oversample level closest to the current pixels per unit. */
    private static int level() {
        float k = Draw.pixelsPerUnit();
        int best = 0;
        for (int i = 1; i < OVERSAMPLE.length; i++) {
            if (Math.abs(OVERSAMPLE[i] - k) < Math.abs(OVERSAMPLE[best] - k)) {
                best = i;
            }
        }
        return best;
    }

    /**
     * The same face at an exact pixel size ({@code ui_px_5..64.json}, each
     * oversample 1). Drawn in screen-pixel space at a whole-pixel position,
     * one glyph pixel lands on exactly one screen pixel — no resampling at
     * all, which is what makes text crisp rather than slightly soft or
     * grainy. See {@link Draw#smoothText}.
     */
    public static final int MIN_PX = 5;
    public static final int MAX_PX = 64;
    private static final Style[] PX_STYLES = new Style[MAX_PX + 1];

    static {
        for (int n = MIN_PX; n <= MAX_PX; n++) {
            PX_STYLES[n] = withFont(Style.EMPTY, Identifier.fromNamespaceAndPath("blurred", "ui_px_" + n));
        }
    }

    /** {@code text} in the smooth face at exactly {@code px} pixels. */
    public static MutableComponent px(String text, int px) {
        int n = Math.max(MIN_PX, Math.min(MAX_PX, px));
        return Component.literal(text).setStyle(PX_STYLES[n]);
    }

    /** A literal string in the pixel font. */
    public static MutableComponent of(String text) {
        return Component.literal(text).setStyle(STYLE);
    }

    /** A literal string in the smooth UI font, sized for where it's about to be drawn. */
    public static MutableComponent ui(String text) {
        return Component.literal(text).setStyle(UI_STYLES[level()]);
    }

    /** A literal string in the smooth UI font, semibold. */
    public static MutableComponent uiBold(String text) {
        return Component.literal(text).setStyle(UI_BOLD_STYLES[level()]);
    }

    /** Put existing text into the pixel font, keeping its other styling. */
    public static Component apply(Component text) {
        return text.copy().withStyle(s -> withFont(s, PIXEL));
    }

    /**
     * Point a style at our font.
     *
     * <p>1.21.9 replaced {@code Style.withFont(Identifier)} with a
     * {@code StyleSpriteSource} — the same change that let a style reference a
     * player head as well as a font. The one-line difference is confined here
     * so nothing else in the mod has to branch on it.
     */
    private static Style withFont(Style base, Identifier font) {
        return base.withFont(new net.minecraft.network.chat.FontDescription.Resource(font));
    }
}
