package dev.blurredclient.mod.ui.kit;

import net.minecraft.client.gui.Font;

/**
 * Per-frame drawing context handed down the component tree: the font, the
 * active skin, and the pointer/focus state each component needs to pick its
 * look.
 */
public final class Ui {
    public final KitScreen screen;
    public final Font font;
    public final Skin skin;
    public final int mouseX;
    public final int mouseY;

    Ui(KitScreen screen, Font font, Skin skin, int mouseX, int mouseY) {
        this.screen = screen;
        this.font = font;
        this.skin = skin;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    /** True if the pointer is over {@code c} or something inside it. */
    public boolean isHovered(KitComponent c) {
        for (KitComponent h = screen.hovered(); h != null; h = h.parent()) {
            if (h == c) {
                return true;
            }
        }
        return false;
    }

    public boolean isFocused(KitComponent c) {
        return screen.focused() == c;
    }

    /** True while {@code c} holds a mouse button it accepted. */
    public boolean isPressed(KitComponent c) {
        return screen.captured() == c;
    }
}
