package dev.blurredclient.mod.module;

import dev.blurredclient.mod.module.feature.AppearanceModule;
import dev.blurredclient.mod.module.feature.CrewModule;
import dev.blurredclient.mod.module.feature.LowFireModule;
import dev.blurredclient.mod.module.feature.ViewModelModule;
import dev.blurredclient.mod.module.feature.ZoomModule;
import dev.blurredclient.mod.module.feature.EffectsModules;
import dev.blurredclient.mod.module.feature.PerformanceModules;
import dev.blurredclient.mod.module.hud.ArmorModule;
import dev.blurredclient.mod.module.hud.BlockInfoModule;
import dev.blurredclient.mod.module.hud.CombatInfoModule;
import dev.blurredclient.mod.module.hud.KeystrokesModule;
import dev.blurredclient.mod.module.hud.ModuleListModule;
import dev.blurredclient.mod.module.hud.NotificationsModule;
import dev.blurredclient.mod.module.hud.PotionsModule;
import dev.blurredclient.mod.module.hud.WatermarkModule;
import dev.blurredclient.mod.module.feature.MenuThemeModule;
import dev.blurredclient.mod.module.feature.SimpleModule;
import dev.blurredclient.mod.module.hud.HudModule;
import dev.blurredclient.mod.module.hud.InfoBarModule;
import dev.blurredclient.mod.module.hud.Readouts;

import java.util.ArrayList;
import java.util.List;

/**
 * Every module in the client, with a typed handle for code that needs one.
 *
 * <p>Registration order is display order within a category, and for the info
 * bar it's also the order readouts appear in — which matches v1.
 */
public final class Modules {
    private Modules() {}

    public static final ModuleRegistry REGISTRY = new ModuleRegistry();

    // --- HUD ---
    public static final InfoBarModule INFO_BAR = REGISTRY.register(new InfoBarModule());
    public static final Readouts.Fps FPS = REGISTRY.register(new Readouts.Fps());
    public static final Readouts.Ping PING = REGISTRY.register(new Readouts.Ping());
    public static final Readouts.Coords COORDS = REGISTRY.register(new Readouts.Coords());
    public static final Readouts.Direction DIRECTION = REGISTRY.register(new Readouts.Direction());
    public static final Readouts.Cps CPS = REGISTRY.register(new Readouts.Cps());
    public static final Readouts.Clock CLOCK = REGISTRY.register(new Readouts.Clock());
    public static final Readouts.Speed SPEED = REGISTRY.register(new Readouts.Speed());
    public static final Readouts.Memory MEMORY = REGISTRY.register(new Readouts.Memory());
    public static final Readouts.Server SERVER = REGISTRY.register(new Readouts.Server());
    public static final Readouts.Day DAY = REGISTRY.register(new Readouts.Day());
    public static final ModuleListModule MODULE_LIST = REGISTRY.register(new ModuleListModule());
    public static final NotificationsModule NOTIFICATIONS = REGISTRY.register(new NotificationsModule());
    public static final WatermarkModule WATERMARK = REGISTRY.register(new WatermarkModule());
    public static final KeystrokesModule KEYSTROKES = REGISTRY.register(new KeystrokesModule());
    public static final ArmorModule ARMOR = REGISTRY.register(new ArmorModule());
    public static final PotionsModule POTIONS = REGISTRY.register(new PotionsModule());
    public static final CombatInfoModule COMBAT_INFO = REGISTRY.register(new CombatInfoModule());
    public static final dev.blurredclient.mod.module.hud.HitTypeModule HIT_TYPE =
            REGISTRY.register(new dev.blurredclient.mod.module.hud.HitTypeModule());
    public static final BlockInfoModule BLOCK_INFO = REGISTRY.register(new BlockInfoModule());
    public static final dev.blurredclient.mod.module.hud.NowPlayingModule NOW_PLAYING =
            REGISTRY.register(new dev.blurredclient.mod.module.hud.NowPlayingModule());
    public static final dev.blurredclient.mod.module.feature.EntityCullingModule ENTITY_CULLING =
            REGISTRY.register(new dev.blurredclient.mod.module.feature.EntityCullingModule());
    public static final dev.blurredclient.mod.module.hud.FoodInfoModule FOOD_INFO =
            REGISTRY.register(new dev.blurredclient.mod.module.hud.FoodInfoModule());
    public static final dev.blurredclient.mod.module.hud.LyricsModule LYRICS =
            REGISTRY.register(new dev.blurredclient.mod.module.hud.LyricsModule());

    // --- Visual ---
    public static final SimpleModule NUMERIC_PING = REGISTRY.register(new SimpleModule(
            ModuleIds.NUMERIC_PING, "Numeric ping",
            "Tab list shows ping as a number instead of five signal bars, which can't tell 30ms from 140ms.",
            Category.VISUAL, true));
    public static final SimpleModule CLIENT_BADGE = REGISTRY.register(new SimpleModule(
            ModuleIds.CLIENT_BADGE, "Blurred badge",
            "Show the client mark beside players who are also on Blurred. Needs the launcher running and chat connected.",
            Category.VISUAL, true));

    public static final ViewModelModule VIEW_MODEL = REGISTRY.register(new ViewModelModule());
    public static final LowFireModule LOW_FIRE = REGISTRY.register(new LowFireModule());
    public static final ZoomModule ZOOM = REGISTRY.register(new ZoomModule());
    public static final dev.blurredclient.mod.module.feature.BlockOutlineModule BLOCK_OUTLINE =
            REGISTRY.register(new dev.blurredclient.mod.module.feature.BlockOutlineModule());
    public static final dev.blurredclient.mod.module.feature.ItemPhysicsModule ITEM_PHYSICS =
            REGISTRY.register(new dev.blurredclient.mod.module.feature.ItemPhysicsModule());
    public static final dev.blurredclient.mod.module.feature.CinematicContainers CINEMATIC_CONTAINERS =
            REGISTRY.register(new dev.blurredclient.mod.module.feature.CinematicContainers());
    public static final SimpleModule NO_HURT_CAM = REGISTRY.register(new SimpleModule(
            ModuleIds.NO_HURT_CAM, "No hurt cam",
            "Stop the screen tilting when you take damage. You still take the hit and hear it.",
            Category.VISUAL, false));
    public static final PerformanceModules.Swing SWING = REGISTRY.register(new PerformanceModules.Swing());
    public static final dev.blurredclient.mod.module.feature.QolModules.NoBlockRotation NO_BLOCK_ROTATION =
            REGISTRY.register(new dev.blurredclient.mod.module.feature.QolModules.NoBlockRotation());
    public static final SimpleModule NO_BOB = REGISTRY.register(new SimpleModule(
            "no_bob", "No camera bob", "The view doesn't bob up and down as you walk.", Category.VISUAL, false));

    // --- Effects ---
    public static final EffectsModules.JumpCircles JUMP_CIRCLES = REGISTRY.register(new EffectsModules.JumpCircles());
    public static final EffectsModules.HitEffects HIT_EFFECTS = REGISTRY.register(new EffectsModules.HitEffects());
    public static final EffectsModules.TargetRing TARGET_RING = REGISTRY.register(new EffectsModules.TargetRing());
    public static final EffectsModules.Trail TRAIL = REGISTRY.register(new EffectsModules.Trail());
    public static final dev.blurredclient.mod.module.feature.WorldFxModules.DeathEffects DEATH_EFFECTS = REGISTRY.register(new dev.blurredclient.mod.module.feature.WorldFxModules.DeathEffects());
    public static final dev.blurredclient.mod.module.feature.WorldFxModules.KillEffects KILL_EFFECTS = REGISTRY.register(new dev.blurredclient.mod.module.feature.WorldFxModules.KillEffects());
    public static final dev.blurredclient.mod.module.feature.WorldFxModules.PlaceAnimation PLACE_ANIMATION = REGISTRY.register(new dev.blurredclient.mod.module.feature.WorldFxModules.PlaceAnimation());
    public static final dev.blurredclient.mod.module.feature.WorldFxModules.PlacePreview PLACE_PREVIEW = REGISTRY.register(new dev.blurredclient.mod.module.feature.WorldFxModules.PlacePreview());
    public static final dev.blurredclient.mod.module.feature.WorldFxModules.ProjectileTrails PROJECTILE_TRAILS = REGISTRY.register(new dev.blurredclient.mod.module.feature.WorldFxModules.ProjectileTrails());
    public static final dev.blurredclient.mod.module.feature.WorldFxModules.Contrails CONTRAILS = REGISTRY.register(new dev.blurredclient.mod.module.feature.WorldFxModules.Contrails());
    public static final dev.blurredclient.mod.module.feature.WorldFxModules.DeathWaypoints DEATH_WAYPOINTS = REGISTRY.register(new dev.blurredclient.mod.module.feature.WorldFxModules.DeathWaypoints());
    public static final dev.blurredclient.mod.module.feature.CosmeticModules.ChineseHat CHINESE_HAT = REGISTRY.register(new dev.blurredclient.mod.module.feature.CosmeticModules.ChineseHat());
    public static final dev.blurredclient.mod.module.feature.CosmeticModules.Pets PETS = REGISTRY.register(new dev.blurredclient.mod.module.feature.CosmeticModules.Pets());
    public static final dev.blurredclient.mod.module.feature.CosmeticModules.CustomSky CUSTOM_SKY = REGISTRY.register(new dev.blurredclient.mod.module.feature.CosmeticModules.CustomSky());
    public static final dev.blurredclient.mod.module.feature.CosmeticModules.BetterNametags BETTER_NAMETAGS = REGISTRY.register(new dev.blurredclient.mod.module.feature.CosmeticModules.BetterNametags());
    public static final dev.blurredclient.mod.module.feature.CosmeticModules.SkinShine SKIN_SHINE = REGISTRY.register(new dev.blurredclient.mod.module.feature.CosmeticModules.SkinShine());
    public static final dev.blurredclient.mod.module.feature.ItemSkinsModule ITEM_SKINS = REGISTRY.register(new dev.blurredclient.mod.module.feature.ItemSkinsModule());
    public static final dev.blurredclient.mod.module.feature.ShaderModules.Ambience AMBIENCE = REGISTRY.register(new dev.blurredclient.mod.module.feature.ShaderModules.Ambience());
    public static final dev.blurredclient.mod.module.feature.DiscordModule DISCORD = REGISTRY.register(new dev.blurredclient.mod.module.feature.DiscordModule());
    public static final dev.blurredclient.mod.module.feature.HitboxModule HITBOXES = REGISTRY.register(new dev.blurredclient.mod.module.feature.HitboxModule());
    public static final dev.blurredclient.mod.module.feature.HandModules.ShowHand SHOW_HAND = REGISTRY.register(new dev.blurredclient.mod.module.feature.HandModules.ShowHand());
    public static final dev.blurredclient.mod.module.feature.HandModules.Inspect INSPECT = REGISTRY.register(new dev.blurredclient.mod.module.feature.HandModules.Inspect());
    public static final dev.blurredclient.mod.module.feature.HandModules.SwordModels SWORD_MODELS = REGISTRY.register(new dev.blurredclient.mod.module.feature.HandModules.SwordModels());
    public static final dev.blurredclient.mod.module.feature.ShaderModules.MotionBlur MOTION_BLUR = REGISTRY.register(new dev.blurredclient.mod.module.feature.ShaderModules.MotionBlur());
    public static final dev.blurredclient.mod.module.feature.ShaderModules.WavingFoliage WAVING_FOLIAGE = REGISTRY.register(new dev.blurredclient.mod.module.feature.ShaderModules.WavingFoliage());
    public static final dev.blurredclient.mod.module.feature.ShaderModules.WetGround WET_GROUND = REGISTRY.register(new dev.blurredclient.mod.module.feature.ShaderModules.WetGround());

    // --- HUD and quality of life ---
    public static final dev.blurredclient.mod.module.hud.CrosshairModule CROSSHAIR =
            REGISTRY.register(new dev.blurredclient.mod.module.hud.CrosshairModule());
    public static final dev.blurredclient.mod.module.hud.HitMarkersModule HIT_MARKERS =
            REGISTRY.register(new dev.blurredclient.mod.module.hud.HitMarkersModule());
    public static final dev.blurredclient.mod.module.hud.VitalsModule VITALS =
            REGISTRY.register(new dev.blurredclient.mod.module.hud.VitalsModule());
    public static final dev.blurredclient.mod.module.hud.DurabilityAlertModule DURABILITY_ALERT =
            REGISTRY.register(new dev.blurredclient.mod.module.hud.DurabilityAlertModule());
    public static final dev.blurredclient.mod.module.hud.TotemCounterModule TOTEM_COUNTER =
            REGISTRY.register(new dev.blurredclient.mod.module.hud.TotemCounterModule());
    public static final dev.blurredclient.mod.module.hud.KeybindsHudModule KEYBINDS_HUD =
            REGISTRY.register(new dev.blurredclient.mod.module.hud.KeybindsHudModule());
    public static final dev.blurredclient.mod.module.feature.QolModules.SmoothHotbar SMOOTH_HOTBAR =
            REGISTRY.register(new dev.blurredclient.mod.module.feature.QolModules.SmoothHotbar());
    public static final dev.blurredclient.mod.module.feature.QolModules.ChatAnimations CHAT_ANIMATIONS =
            REGISTRY.register(new dev.blurredclient.mod.module.feature.QolModules.ChatAnimations());
    public static final dev.blurredclient.mod.module.feature.QolModules.LongChat LONG_CHAT =
            REGISTRY.register(new dev.blurredclient.mod.module.feature.QolModules.LongChat());
    public static final dev.blurredclient.mod.module.feature.QolModules.HitSounds HIT_SOUNDS =
            REGISTRY.register(new dev.blurredclient.mod.module.feature.QolModules.HitSounds());
    public static final dev.blurredclient.mod.module.feature.QolModules.AutoReconnect AUTO_RECONNECT =
            REGISTRY.register(new dev.blurredclient.mod.module.feature.QolModules.AutoReconnect());

    // --- Performance ---
    public static final PerformanceModules.DynamicFps DYNAMIC_FPS = REGISTRY.register(new PerformanceModules.DynamicFps());
    public static final PerformanceModules.ParticleLimiter PARTICLE_LIMITER = REGISTRY.register(new PerformanceModules.ParticleLimiter());
    public static final PerformanceModules.EntityDistance ENTITY_DISTANCE = REGISTRY.register(new PerformanceModules.EntityDistance());

    // --- Menus ---
    public static final AppearanceModule APPEARANCE = REGISTRY.register(new AppearanceModule());
    public static final MenuThemeModule MENU_THEME = REGISTRY.register(new MenuThemeModule());
    public static final dev.blurredclient.mod.module.feature.HologramMenuModule HOLOGRAM =
            REGISTRY.register(new dev.blurredclient.mod.module.feature.HologramMenuModule());

    // --- Crew ---
    public static final CrewModule CREW = REGISTRY.register(new CrewModule());

    // --- Cosmetic ---
    public static final SimpleModule CAPES = REGISTRY.register(new SimpleModule(
            ModuleIds.CAPES, "Capes",
            "Render Blurred capes for yourself and anyone else wearing one.",
            Category.COSMETIC, true));

    /** Every HUD module, in registration order. */
    public static List<HudModule> hud() {
        List<HudModule> out = new ArrayList<>();
        for (Module m : REGISTRY.all()) {
            if (m instanceof HudModule h) {
                out.add(h);
            }
        }
        return out;
    }
}
