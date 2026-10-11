package dev.blurredclient.mod.ui.kit;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * How the UI kit's controls look.
 *
 * <p>Widgets own behaviour (what a click does, where the knob is); a skin owns
 * appearance. Switching between glass and pixel art swaps the skin and every
 * screen follows, without any widget or screen knowing which one is active.
 *
 * <p>Animated states arrive as floats (0 = idle, 1 = fully hovered/on), so a
 * skin can fade between them smoothly (glass) or step them (pixel art).
 */
public interface Skin {
    /** What a panel is for, which decides its weight. */
    enum Surface {
        /** The main window of a screen. Gets the full tank treatment. */
        WINDOW,
        /** A card or group inside a window. */
        CARD,
        /** The left navigation rail. */
        RAIL,
        /** Dropdown lists, colour picker, tooltips: floats above everything. */
        POPUP,
        /** The recessed background of a text field or dropdown. */
        INSET
    }

    void panel(GuiGraphicsExtractor ctx, int x, int y, int w, int h, Surface surface);

    /**
     * @param hover   0..1 hover amount
     * @param pressed held down right now
     * @param accent  the primary action on a screen
     */
    void button(GuiGraphicsExtractor ctx, Font font, String label, int x, int y, int w, int h,
                float hover, boolean pressed, boolean enabled, boolean accent);

    /** @param on 0..1, animated between off and on */
    void toggle(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float on, float hover, boolean enabled);

    void slider(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float fraction, float hover, boolean dragging);

    /** Background for a text field or a closed dropdown. */
    void field(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float hover, boolean focused);

    /** A highlighted row in a list (dropdown option, nav item). */
    void highlight(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float amount);

    void scrollbar(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int thumbY, int thumbH, float hover);

    /** Full-screen dim behind a menu, so it reads over a bright world. */
    void scrim(GuiGraphicsExtractor ctx, int width, int height);

    /**
     * Behind a module's row in the click GUI: {@code hover} and {@code on}
     * (enabled) are 0..1. By default just a faint wash on hover; the name's
     * colour shows whether it's on.
     */
    default void row(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float hover, float on) {
        if (hover > 0.01f) {
            dev.blurredclient.mod.ui.Draw.rect(ctx, x, y, x + w, y + h, dev.blurredclient.mod.ui.Colors.fade(0x14FFFFFF, hover));
        }
    }

    /** The name colour of an enabled module's row. */
    default int rowOnColor() {
        return accent();
    }

    /** Whether clicks send out water ripples ({@link Ripples}). */
    default boolean ripples() {
        return false;
    }

    /** Highlight colour: selected items, filled tracks, the caret. */
    default int accent() {
        return dev.blurredclient.mod.Theme.accent();
    }

    default int textPrimary() {
        return dev.blurredclient.mod.Theme.TEXT;
    }

    default int textDim() {
        return dev.blurredclient.mod.Theme.TEXT_DIM;
    }

    default int textFaint() {
        return dev.blurredclient.mod.Theme.TEXT_FAINT;
    }

    /** Text in this skin's font. */
    Component text(String s);

    /** Heavier text for headings and panel titles. Same face unless the skin has a bold one. */
    default Component bold(String s) {
        return text(s);
    }

    default void drawBold(GuiGraphicsExtractor ctx, Font font, String s, int x, int y, int color) {
        drawText(ctx, font, s, x, y, color);
    }

    default int boldWidth(Font font, String s) {
        return font.width(bold(s));
    }

    /** Draw text in this skin's font. Glass draws without a shadow; pixel art with. */
    void drawText(GuiGraphicsExtractor ctx, Font font, String s, int x, int y, int color);

    default int width(Font font, String s) {
        return font.width(text(s));
    }
}
