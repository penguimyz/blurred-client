package dev.blurredclient.mod.ui.kit.widget;

import dev.blurredclient.mod.setting.KeybindSetting;
import dev.blurredclient.mod.ui.kit.Component;
import dev.blurredclient.mod.ui.kit.KitScreen;
import dev.blurredclient.mod.ui.kit.Ui;
import dev.blurredclient.mod.ui.kit.Widgets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Click, then press a key to bind it. Escape unbinds; clicking elsewhere
 * cancels.
 */
public class KeybindButton extends Component {
    private final KeybindSetting setting;
    private final Runnable onChange;
    private boolean listening;

    public KeybindButton(KeybindSetting setting, Runnable onChange) {
        this.setting = setting;
        this.onChange = onChange;
    }

    /** The key's name as the player's keyboard layout prints it. */
    public static String keyName(int code) {
        if (code == KeybindSetting.NONE) {
            return "None";
        }
        return InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString();
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public void onFocusChanged(boolean focused) {
        if (!focused) {
            listening = false;
        }
    }

    @Override
    protected void draw(DrawContext ctx, Ui ui) {
        float hov = hover.follow(ui.isHovered(this));
        ui.skin.field(ctx, x, y, w, h, hov, listening);
        String label = listening ? "Press a key…" : keyName(setting.keyCode());
        label = Widgets.ellipsize(ui.font, ui.skin, label, w - 8);
        int tx = x + (w - ui.skin.width(ui.font, label)) / 2;
        ui.skin.drawText(ctx, ui.font, label, tx, y + (h - ui.font.fontHeight) / 2 + 1,
                listening ? ui.skin.accent() : setting.isBound() ? ui.skin.textPrimary() : ui.skin.textFaint());
    }

    @Override
    public boolean onMouseDown(double mx, double my, int button) {
        return button == 0;
    }

    @Override
    public void onMouseUp(double mx, double my, int button) {
        if (contains(mx, my)) {
            Sounds.click();
            listening = !listening;
        }
    }

    @Override
    public boolean onKey(int key, int scancode, int modifiers) {
        if (!listening) {
            return false;
        }
        setting.set(key == GLFW.GLFW_KEY_ESCAPE ? KeybindSetting.NONE : key);
        listening = false;
        onChange.run();
        KitScreen screen = KitScreen.active();
        if (screen != null) {
            screen.focus(null);
        }
        return true;
    }
}
