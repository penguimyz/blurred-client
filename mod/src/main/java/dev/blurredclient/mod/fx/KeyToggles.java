package dev.blurredclient.mod.fx;

import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.setting.KeybindSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

import java.util.HashSet;
import java.util.Set;

/** Per-module toggle keys: a press (not a hold) flips the module, outside menus. */
public final class KeyToggles {
    private KeyToggles() {}

    private static final Set<Integer> DOWN = new HashSet<>();

    public static void tick(MinecraftClient client) {
        if (client.currentScreen != null || client.player == null) {
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

    private static boolean pressed(MinecraftClient client, int code) {
        //? if >=1.21.10 {
        return InputUtil.isKeyPressed(client.getWindow(), code);
        //?} else {
        /*return InputUtil.isKeyPressed(client.getWindow().getHandle(), code);
        *///?}
    }
}
