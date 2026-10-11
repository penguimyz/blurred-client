package dev.blurredclient.mod.ui.kit.widget;

import dev.blurredclient.mod.ui.kit.Anim;
import dev.blurredclient.mod.ui.kit.KitComponent;
import dev.blurredclient.mod.ui.kit.Ui;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * An on/off switch with a sliding knob.
 *
 * <p>Reads its state from a supplier every frame instead of keeping a copy, so
 * it can never disagree with the setting it controls — even if something else
 * (a profile switch, the launcher) changes that setting while it's on screen.
 */
public class Toggle extends KitComponent {
    public static final int WIDTH = 22;
    public static final int HEIGHT = 12;

    private final BooleanSupplier value;
    private final Runnable flip;
    private final Anim on;
    private boolean enabled = true;

    public Toggle(BooleanSupplier value, Runnable flip) {
        this.value = value;
        this.flip = flip;
        this.on = new Anim(value.getAsBoolean() ? 1f : 0f, 14f);
    }

    public Toggle enabled(boolean next) {
        this.enabled = next;
        return this;
    }

    @Override
    protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
        float hov = hover.follow(enabled && ui.isHovered(this));
        // Centre a fixed-size switch in whatever box the layout gave us.
        int tx = x + (w - WIDTH) / 2;
        int ty = y + (h - HEIGHT) / 2;
        ui.skin.toggle(ctx, tx, ty, WIDTH, HEIGHT, on.follow(value.getAsBoolean()), hov, enabled);
    }

    @Override
    public boolean onMouseDown(double mx, double my, int button) {
        return enabled && button == 0;
    }

    @Override
    public void onMouseUp(double mx, double my, int button) {
        if (enabled && contains(mx, my)) {
            Sounds.click();
            flip.run();
        }
    }
}
