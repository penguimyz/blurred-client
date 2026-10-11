package dev.blurredclient.mod.ui.kit.widget;

import dev.blurredclient.mod.ui.kit.KitComponent;
import dev.blurredclient.mod.ui.kit.Skin;
import dev.blurredclient.mod.ui.kit.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A skinned surface (window, card, rail) that other components sit on. Holds
 * one optional content child that fills it, inset by a padding.
 */
public class Panel extends KitComponent {
    private final Skin.Surface surface;
    private final int padding;

    public Panel(Skin.Surface surface, int padding) {
        this.surface = surface;
        this.padding = padding;
    }

    @Override
    protected void layout() {
        for (KitComponent c : children()) {
            c.setBounds(x + padding, y + padding, w - padding * 2, h - padding * 2);
        }
    }

    @Override
    public int preferredHeight(int width) {
        int inner = 0;
        for (KitComponent c : children()) {
            inner = Math.max(inner, c.preferredHeight(width - padding * 2));
        }
        return inner + padding * 2;
    }

    @Override
    protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
        ui.skin.panel(ctx, x, y, w, h, surface);
    }
}
