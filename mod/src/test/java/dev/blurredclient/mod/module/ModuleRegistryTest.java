package dev.blurredclient.mod.module;

import com.google.gson.JsonPrimitive;
import dev.blurredclient.mod.config.ConfigV2;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.SliderSetting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleRegistryTest {
    static final class Zoom extends Module {
        final SliderSetting level = add(new SliderSetting("level", "Zoom level", "", 4, 1, 10, 1, "x"));

        Zoom() {
            super("zoom", "Zoom", "Hold a key to zoom in.", Category.VISUAL, false);
        }
    }

    static final class Crew extends Module {
        final BoolSetting share = add(new BoolSetting("share", "Share server", "", true));

        Crew() {
            super("crew", "Crew", "", Category.CREW, true);
        }

        @Override
        public boolean toggleable() {
            return false;
        }
    }

    ModuleRegistry registry;
    Zoom zoom;
    Crew crew;

    @BeforeEach
    void setUp() {
        registry = new ModuleRegistry();
        zoom = registry.register(new Zoom());
        crew = registry.register(new Crew());
        registry.load(new ConfigV2());
    }

    @Test
    void rejectsDuplicateIds() {
        assertThrows(IllegalStateException.class, () -> registry.register(new Zoom()));
    }

    @Test
    void loadAppliesDefaultsWhenStateMissing() {
        assertFalse(zoom.isEnabled());
        assertEquals(4.0, zoom.level.get());
        assertTrue(crew.isEnabled());
    }

    @Test
    void profilesKeepSeparateStateButShareGlobalModules() {
        zoom.setEnabled(true);
        zoom.level.set(8.0);
        crew.share.set(false);

        registry.switchProfile("Building");
        // New profile starts from defaults for per-profile modules...
        assertFalse(zoom.isEnabled());
        assertEquals(4.0, zoom.level.get());
        // ...but the crew module is global and keeps its value.
        assertFalse(crew.share.isOn());

        registry.switchProfile(ConfigV2.DEFAULT_PROFILE);
        assertTrue(zoom.isEnabled());
        assertEquals(8.0, zoom.level.get());
    }

    @Test
    void duplicateCopiesCurrentState() {
        zoom.setEnabled(true);
        assertTrue(registry.duplicateProfile("PvP"));
        assertEquals("PvP", registry.activeProfile());
        assertTrue(zoom.isEnabled());
        assertFalse(registry.duplicateProfile("PvP"));
    }

    @Test
    void renameAndDeleteProfiles() {
        registry.duplicateProfile("PvP");
        assertTrue(registry.renameProfile("PvP", "Combat"));
        assertEquals("Combat", registry.activeProfile());
        assertEquals(java.util.List.of(ConfigV2.DEFAULT_PROFILE, "Combat"), registry.profileNames());

        assertTrue(registry.deleteProfile("Combat"));
        assertEquals(ConfigV2.DEFAULT_PROFILE, registry.activeProfile());
        assertFalse(registry.deleteProfile(ConfigV2.DEFAULT_PROFILE), "last profile can't be deleted");
    }

    @Test
    void untoggleableModulesStayOnEvenIfFileSaysOff() {
        ModuleState off = new ModuleState();
        off.enabled = false;
        crew.readState(off);
        assertTrue(crew.isEnabled());
    }

    @Test
    void captureKeepsStateForUnknownModules() {
        ModuleState future = new ModuleState();
        future.enabled = true;
        future.settings.put("x", new JsonPrimitive(1));
        ConfigV2 cfg = new ConfigV2();
        cfg.profile(ConfigV2.DEFAULT_PROFILE).put("from_a_newer_version", future);
        registry.load(cfg);

        ConfigV2 out = registry.capture();
        assertTrue(out.profiles.get(ConfigV2.DEFAULT_PROFILE).containsKey("from_a_newer_version"));
        assertTrue(out.global.containsKey("crew"));
        assertTrue(out.profiles.get(ConfigV2.DEFAULT_PROFILE).containsKey("zoom"));
    }

    @Test
    void searchMatchesNameDescriptionAndSettingLabels() {
        assertEquals(1, registry.search("ZOOM").size());
        assertEquals(1, registry.search("hold a key").size());
        assertEquals(1, registry.search("share server").size());
        assertEquals(2, registry.search("  ").size());
    }
}
