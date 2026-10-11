package dev.blurredclient.mod.ui.kit.widget;

import dev.blurredclient.mod.ui.kit.KitComponent;
import dev.blurredclient.mod.ui.kit.Container;

/**
 * Stacks children top to bottom at full width, each at its preferred height.
 * Invisible children take no space.
 */
public class Column extends Container {
    private final int gap;
    private final int padding;

    public Column(int gap, int padding) {
        this.gap = gap;
        this.padding = padding;
    }

    @Override
    protected void layout() {
        int inner = w - padding * 2;
        int cy = y + padding;
        for (KitComponent c : children()) {
            if (!c.visible) {
                continue;
            }
            int ch = c.preferredHeight(inner);
            c.setBounds(x + padding, cy, inner, ch);
            cy += ch + gap;
        }
    }

    @Override
    public int preferredHeight(int width) {
        int inner = width - padding * 2;
        int total = padding * 2;
        int shown = 0;
        for (KitComponent c : children()) {
            if (c.visible) {
                total += c.preferredHeight(inner);
                shown++;
            }
        }
        return total + Math.max(0, shown - 1) * gap;
    }
}
