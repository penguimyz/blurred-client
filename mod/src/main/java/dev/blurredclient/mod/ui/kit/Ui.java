package dev.blurredclient.mod.ui.kit;

import net.minecraft.client.font.TextRenderer;

/**
 * Per-frame drawing context handed down the component tree: the font, the
 * active skin, and the pointer/focus state each component needs to pick its
 * look.
 */
public final class Ui {
    public final KitScreen screen;
    public final TextRenderer font;
    public final Skin skin;
    public final int mouseX;
    public final int mouseY;

    Ui(KitScreen screen, TextRenderer font, Skin skin, int mouseX, int mouseY) {
        this.screen = screen;
        this.font = font;
        this.skin = skin;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    /** True if the pointer is over {@code c} or something inside it. */
    public boolean isHovered(Component c) {
        for (Component h = screen.hovered(); h != null; h = h.parent()) {
            if (h == c) {
                return true;
            }
        }
        return false;
    }

    public boolean isFocused(Component c) {
        return screen.focused() == c;
    }

    /** True while {@code c} holds a mouse button it accepted. */
    public boolean isPressed(Component c) {
        return screen.captured() == c;
    }
}
