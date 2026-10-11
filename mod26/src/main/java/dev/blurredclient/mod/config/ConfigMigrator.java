package dev.blurredclient.mod.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.module.ModuleState;
import dev.blurredclient.mod.module.ModuleIds;

import java.util.Map;

/**
 * Turns a v1 {@code blurred.json} (a flat bag of booleans) into v2.
 *
 * <p>v1 had one master HUD switch plus a switch per readout; v2 has a module
 * per readout. A readout that was on but hidden by the master switch comes
 * across as off, so the HUD looks exactly as it did before the upgrade.
 *
 * <p>Works on raw JSON rather than through the module classes, so it can run
 * (and be tested) without the game, and so a future change to a module
 * can't quietly change what an old file migrates to.
 */
public final class ConfigMigrator {
    private ConfigMigrator() {}

    /** True if {@code root} is a v1 file (v1 never wrote a version field). */
    public static boolean isV1(JsonObject root) {
        return !root.has("version");
    }

    public static ConfigV2 fromV1(JsonObject v1) {
        ConfigV2 cfg = new ConfigV2();
        Map<String, ModuleState> profile = cfg.profile(ConfigV2.DEFAULT_PROFILE);

        boolean hud = bool(v1, "hudEnabled", true);

        // Info bar: carries the old position and island/stack choice.
        ModuleState bar = state(true);
        bar.settings.put("style", new JsonPrimitive(bool(v1, "hudIsland", true) ? "ISLAND" : "STACK"));
        bar.placement = barPlacement(string(v1, "hudPosition", "TOP_LEFT"));
        profile.put(ModuleIds.INFO_BAR, bar);

        // Readouts. All stay inside the bar, as they always were in v1.
        readout(profile, ModuleIds.FPS, hud && bool(v1, "showFps", true));
        readout(profile, ModuleIds.PING, hud && bool(v1, "showPing", true));
        readout(profile, ModuleIds.COORDS, hud && bool(v1, "showCoords", false));
        readout(profile, ModuleIds.DIRECTION, hud && bool(v1, "showDirection", false));
        readout(profile, ModuleIds.CPS, hud && bool(v1, "showCps", false));
        readout(profile, ModuleIds.CLOCK, hud && bool(v1, "showClock", false));

        profile.put(ModuleIds.NUMERIC_PING, state(bool(v1, "numericPing", true)));
        profile.put(ModuleIds.CLIENT_BADGE, state(bool(v1, "showClientBadge", true)));

        ModuleState menus = state(bool(v1, "styleMenus", true));
        menus.settings.put("defer_to_essential", new JsonPrimitive(bool(v1, "deferToEssential", true)));
        cfg.global.put(ModuleIds.MENU_THEME, menus);

        ModuleState crew = state(true);
        crew.settings.put("chat_toasts", new JsonPrimitive(bool(v1, "chatToasts", true)));
        crew.settings.put("share_server", new JsonPrimitive(bool(v1, "shareServer", true)));
        crew.settings.put("announce_lan", new JsonPrimitive(bool(v1, "announceLanWorlds", true)));
        cfg.global.put(ModuleIds.CREW, crew);

        cfg.global.put(ModuleIds.CAPES, state(bool(v1, "showCapes", true)));

        return cfg.sanitise();
    }

    /** v1's three top-edge positions, with the 4px margin v1 drew at. */
    static HudPlacement barPlacement(String v1Position) {
        return switch (v1Position) {
            case "TOP_CENTER" -> new HudPlacement(HudPlacement.Anchor.TOP_CENTER, 0, 4);
            case "TOP_RIGHT" -> new HudPlacement(HudPlacement.Anchor.TOP_RIGHT, -4, 4);
            default -> new HudPlacement(HudPlacement.Anchor.TOP_LEFT, 4, 4);
        };
    }

    private static void readout(Map<String, ModuleState> profile, String id, boolean on) {
        ModuleState s = state(on);
        s.settings.put("in_bar", new JsonPrimitive(true));
        profile.put(id, s);
    }

    private static ModuleState state(boolean enabled) {
        ModuleState s = new ModuleState();
        s.enabled = enabled;
        return s;
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : fallback;
    }

    private static String string(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : fallback;
    }
}
