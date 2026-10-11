package dev.blurredclient.mod.fx;

import com.mojang.blaze3d.platform.InputConstants;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.setting.KeybindSetting;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;

/** Per-module toggle keys: a press (not a hold) flips the module, outside menus. */
public final class KeyToggles {
    private KeyToggles() {}

    private static final Set<Integer> DOWN = new HashSet<>();

    public static void tick(Minecraft client) {
        if (client.gui.screen() != null || client.player == null) {
            DOWN.clear();
            return;
        }
        for (Module m : Modules.REGISTRY.all()) {
            KeybindSetting key = m.toggleKey();
            if (key == null || !key.isBound()) {
                continue;
            }
            int code = key.keyCode();
            boolean pressed = pressed(client, code);
            if (pressed && DOWN.add(code)) {
                m.toggle();
                dev.blurredclient.mod.module.hud.NotificationsModule.moduleToggled(m.name(), m.isEnabled());
            } else if (!pressed) {
                DOWN.remove(code);
            }
        }
    }

    private static boolean pressed(Minecraft client, int code) {
        return InputConstants.isKeyDown(client.getWindow(), code);
    }
}
