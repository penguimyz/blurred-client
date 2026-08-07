package dev.blurredclient.mod.ui;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

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
    public static final Identifier PIXEL = Identifier.of("blurred", "pixel");

    private static final Style STYLE = withPixelFont(Style.EMPTY);

    /** A literal string in the pixel font. */
    public static MutableText of(String text) {
        return Text.literal(text).setStyle(STYLE);
    }

    /** Put existing text into the pixel font, keeping its other styling. */
    public static Text apply(Text text) {
        return text.copy().styled(BlurredFont::withPixelFont);
    }

    /**
     * Point a style at our font.
     *
     * <p>1.21.9 replaced {@code Style.withFont(Identifier)} with a
     * {@code StyleSpriteSource} — the same change that let a style reference a
     * player head as well as a font. The one-line difference is confined here
     * so nothing else in the mod has to branch on it.
     */
    private static Style withPixelFont(Style base) {
        //? if >=1.21.10 {
        return base.withFont(new net.minecraft.text.StyleSpriteSource.Font(PIXEL));
        //?} else {
        /*return base.withFont(PIXEL);
         *///?}
    }
}
