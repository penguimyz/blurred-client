package dev.blurredclient.mod.ui.kit.widget;

import dev.blurredclient.mod.ui.kit.KitComponent;
import dev.blurredclient.mod.ui.kit.Ui;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A labelled push button. Fires on release over the button, as vanilla does. */
public class Button extends KitComponent {
    private final Supplier<String> label;
    private final Runnable onClick;
    private boolean accent;
    private boolean enabled = true;

    public Button(String label, Runnable onClick) {
        this(() -> label, onClick);
    }

    public Button(Supplier<String> label, Runnable onClick) {
        this.label = label;
        this.onClick = onClick;
    }

    /** Mark as the primary action on its screen. */
    public Button accent() {
        this.accent = true;
        return this;
    }

    public Button enabled(boolean next) {
        this.enabled = next;
        return this;
    }

    @Override
    public int preferredHeight(int width) {
        return 18;
    }

    @Override
    protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
        float hov = hover.follow(enabled && ui.isHovered(this));
        ui.skin.button(ctx, ui.font, label.get(), x, y, w, h, hov, ui.isPressed(this), enabled, accent);
    }

    @Override
    public boolean onMouseDown(double mx, double my, int button) {
        return enabled && button == 0;
    }

    @Override
    public void onMouseUp(double mx, double my, int button) {
        if (enabled && contains(mx, my)) {
            Sounds.click();
            onClick.run();
        }
    }
}
