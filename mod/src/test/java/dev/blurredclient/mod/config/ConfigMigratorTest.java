package dev.blurredclient.mod.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.module.ModuleIds;
import dev.blurredclient.mod.module.ModuleState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigratorTest {
    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void detectsV1() {
        assertTrue(ConfigMigrator.isV1(json("{\"showFps\":true}")));
        assertFalse(ConfigMigrator.isV1(json("{\"version\":2}")));
    }

    @Test
    void emptyV1GivesV1Defaults() {
        ConfigV2 cfg = ConfigMigrator.fromV1(json("{}"));
        Map<String, ModuleState> p = cfg.profiles.get(ConfigV2.DEFAULT_PROFILE);
        assertTrue(p.get(ModuleIds.FPS).enabled);
        assertTrue(p.get(ModuleIds.PING).enabled);
        assertFalse(p.get(ModuleIds.COORDS).enabled);
        assertEquals("ISLAND", p.get(ModuleIds.INFO_BAR).settings.get("style").getAsString());
        assertEquals(HudPlacement.Anchor.TOP_LEFT, p.get(ModuleIds.INFO_BAR).placement.anchor);
        assertTrue(cfg.global.get(ModuleIds.MENU_THEME).enabled);
        assertTrue(cfg.global.get(ModuleIds.CAPES).enabled);
    }

    @Test
    void masterHudSwitchOffTurnsReadoutsOff() {
        ConfigV2 cfg = ConfigMigrator.fromV1(json("{\"hudEnabled\":false,\"showFps\":true,\"showClock\":true}"));
        Map<String, ModuleState> p = cfg.profiles.get(ConfigV2.DEFAULT_PROFILE);
        assertFalse(p.get(ModuleIds.FPS).enabled);
        assertFalse(p.get(ModuleIds.CLOCK).enabled);
    }

    @Test
    void carriesEveryV1Option() {
        ConfigV2 cfg = ConfigMigrator.fromV1(json("""
                {"hudEnabled":true,"showFps":false,"showPing":true,"showCoords":true,
                 "showDirection":true,"showCps":true,"showClock":true,
                 "hudPosition":"TOP_RIGHT","hudIsland":false,
                 "chatToasts":false,"shareServer":false,"showCapes":false,
                 "styleMenus":false,"numericPing":false,"showClientBadge":false,
                 "deferToEssential":false,"announceLanWorlds":false}
                """));
        Map<String, ModuleState> p = cfg.profiles.get(ConfigV2.DEFAULT_PROFILE);
        assertFalse(p.get(ModuleIds.FPS).enabled);
        assertTrue(p.get(ModuleIds.COORDS).enabled);
        assertTrue(p.get(ModuleIds.CPS).enabled);
        assertEquals("STACK", p.get(ModuleIds.INFO_BAR).settings.get("style").getAsString());
        HudPlacement bar = p.get(ModuleIds.INFO_BAR).placement;
        assertEquals(HudPlacement.Anchor.TOP_RIGHT, bar.anchor);
        assertEquals(-4, bar.x);
        assertFalse(p.get(ModuleIds.NUMERIC_PING).enabled);
        assertFalse(p.get(ModuleIds.CLIENT_BADGE).enabled);

        assertFalse(cfg.global.get(ModuleIds.MENU_THEME).enabled);
        assertFalse(cfg.global.get(ModuleIds.MENU_THEME).settings.get("defer_to_essential").getAsBoolean());
        ModuleState crew = cfg.global.get(ModuleIds.CREW);
        assertFalse(crew.settings.get("chat_toasts").getAsBoolean());
        assertFalse(crew.settings.get("share_server").getAsBoolean());
        assertFalse(crew.settings.get("announce_lan").getAsBoolean());
        assertFalse(cfg.global.get(ModuleIds.CAPES).enabled);
    }

    @Test
    void survivesGsonRoundTrip() {
        Gson gson = new Gson();
        ConfigV2 cfg = ConfigMigrator.fromV1(json("{\"hudPosition\":\"TOP_CENTER\"}"));
        ConfigV2 back = gson.fromJson(gson.toJson(cfg), ConfigV2.class).sanitise();
        HudPlacement bar = back.profiles.get(ConfigV2.DEFAULT_PROFILE).get(ModuleIds.INFO_BAR).placement;
        assertNotNull(bar);
        assertEquals(HudPlacement.Anchor.TOP_CENTER, bar.anchor);
        assertEquals(2, back.version);
    }

    @Test
    void sanitiseRepairsNulls() {
        ConfigV2 cfg = new Gson().fromJson("{\"version\":2,\"activeProfile\":null,\"global\":null}", ConfigV2.class)
                .sanitise();
        assertEquals(ConfigV2.DEFAULT_PROFILE, cfg.activeProfile);
        assertNotNull(cfg.global);
        assertNotNull(cfg.allowedRestricted);
        assertTrue(cfg.profiles.containsKey(ConfigV2.DEFAULT_PROFILE));
    }
}
