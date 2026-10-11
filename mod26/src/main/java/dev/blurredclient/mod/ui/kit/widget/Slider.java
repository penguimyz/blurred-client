package dev.blurredclient.mod.ui.kit.widget;

import dev.blurredclient.mod.setting.SliderSetting;
import dev.blurredclient.mod.ui.kit.KitComponent;
import dev.blurredclient.mod.ui.kit.KitScreen;
import dev.blurredclient.mod.ui.kit.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

/**
 * A slider bound to a {@link SliderSetting}, with its value printed beside it.
 *
 * <p>Drag anywhere on the track to set; scroll or use the arrow keys to step,
 * and hold Shift to step ten at a time. Every change goes through the
 * setting, so snapping and clamping are the setting's job, not the widget's.
 */
public class Slider extends KitComponent {
    /** Space reserved on the right for the value readout. */
    private static final int VALUE_W = 36;

    private final SliderSetting setting;
    private final Runnable onChange;

    public Slider(SliderSetting setting, Runnable onChange) {
        this.setting = setting;
        this.onChange = onChange;
    }

    private int trackW() {
        return Math.max(20, w - VALUE_W - 4);
    }

    @Override
    protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
        float hov = hover.follow(ui.isHovered(this));
        ui.skin.slider(ctx, x, y, trackW(), h, (float) setting.fraction(), hov, ui.isPressed(this));
        String text = format(setting.get(), setting.step()) + setting.suffix();
        int tx = x + w - ui.skin.width(ui.font, text);
        ui.skin.drawText(ctx, ui.font, text, tx, y + (h - ui.font.lineHeight) / 2 + 1, ui.skin.textDim());
    }

    /** Whole numbers for whole steps, otherwise as many decimals as the step has. */
    static String format(double value, double step) {
        if (step >= 1 && value == Math.rint(value)) {
            return Long.toString(Math.round(value));
        }
        int decimals = Math.max(0, Math.min(3, (int) Math.ceil(-Math.log10(step))));
        return String.format("%." + decimals + "f", value);
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public boolean onMouseDown(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        setFromMouse(mx);
        return true;
    }

    @Override
    public void onDrag(double mx, double my, int button) {
        setFromMouse(mx);
    }

    private void setFromMouse(double mx) {
        double before = setting.get();
        setting.setFraction((mx - x - 5) / (trackW() - 10));
        if (setting.get() != before) {
            onChange.run();
        }
    }

    private void nudge(int steps) {
        double before = setting.get();
        int mult = KitScreen.shiftDown() ? 10 : 1;
        setting.set(setting.get() + steps * mult * setting.step());
        if (setting.get() != before) {
            onChange.run();
        }
    }

    @Override
    public boolean onScroll(double mx, double my, double amount) {
        nudge(amount > 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean onKey(int key, int scancode, int modifiers) {
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_UP) {
            nudge(1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_DOWN) {
            nudge(-1);
            return true;
        }
        return false;
    }
}
