package dev.blurredclient.mod.config;

import dev.blurredclient.mod.module.ModuleState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shape of {@code blurred.json} from v2 on.
 *
 * <pre>
 * {
 *   "version": 2,
 *   "activeProfile": "Default",
 *   "allowedRestricted": ["freelook"],
 *   "global":   { "menu_theme": { "enabled": true, "settings": {...} } },
 *   "profiles": { "Default": { "fps": { "enabled": true, "settings": {...},
 *                                      "placement": {...} } } }
 * }
 * </pre>
 *
 * <p>The launcher reads and writes this same file (its Blurred tab), so field
 * names are part of a contract with {@code src/types/blurredConfig.ts}.
 * Rename nothing without changing both.
 */
public final class ConfigV2 {
    public static final int VERSION = 2;
    public static final String DEFAULT_PROFILE = "Default";

    public int version = VERSION;
    public String activeProfile = DEFAULT_PROFILE;

    /**
     * Restricted modules (freelook, fullbright) the player opted into from the
     * launcher. Anything not listed doesn't exist in game — it isn't just
     * switched off. Written by the launcher; the mod never adds to it.
     */
    public List<String> allowedRestricted = new ArrayList<>();

    /** State for modules shared by every profile. */
    public Map<String, ModuleState> global = new LinkedHashMap<>();

    /** Profile name to that profile's module states. */
    public Map<String, Map<String, ModuleState>> profiles = new LinkedHashMap<>();

    /** Click GUI panel positions, keyed by category name. Shared by all profiles. */
    public Map<String, PanelState> clickGui = new LinkedHashMap<>();

    /** Where one click GUI panel sits, and whether it's folded up. */
    public static final class PanelState {
        public int x;
        public int y;
        public boolean collapsed;

        public PanelState() {}

        public PanelState(int x, int y, boolean collapsed) {
            this.x = x;
            this.y = y;
            this.collapsed = collapsed;
        }
    }

    /** The states for {@code name}, creating an empty profile if needed. */
    public Map<String, ModuleState> profile(String name) {
        return profiles.computeIfAbsent(name, n -> new LinkedHashMap<>());
    }

    /**
     * Repair anything a hand edit or an older launcher could have left null,
     * so the rest of the mod can assume a complete object.
     */
    public ConfigV2 sanitise() {
        if (allowedRestricted == null) {
            allowedRestricted = new ArrayList<>();
        }
        if (global == null) {
            global = new LinkedHashMap<>();
        }
        if (profiles == null) {
            profiles = new LinkedHashMap<>();
        }
        profiles.values().removeIf(p -> p == null);
        if (clickGui == null) {
            clickGui = new LinkedHashMap<>();
        }
        clickGui.values().removeIf(p -> p == null);
        if (activeProfile == null || activeProfile.isBlank()) {
            activeProfile = DEFAULT_PROFILE;
        }
        profile(activeProfile);
        version = VERSION;
        return this;
    }
}
