package dev.blurredclient.mod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.blurredclient.mod.BlurredMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mod settings, stored as {@code config/blurred.json} in the instance.
 *
 * <p>Per-instance rather than global on purpose: instances in this launcher are
 * isolated by design, and a HUD layout that suits a PvP instance rarely suits a
 * modded one.
 *
 * <p>Gson comes along with Minecraft, so this needs no extra dependency.
 */
public final class BlurredConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static BlurredConfig instance;

    // --- HUD ---
    public boolean hudEnabled = true;
    public boolean showFps = true;
    public boolean showPing = true;
    // Off by default: the HUD is FPS and ping only. Coordinates are what the F3
    // screen is for, and leaving them on-screen leaks your base location into
    // every screenshot and stream.
    public boolean showCoords = false;
    public boolean showDirection = false;
    public boolean showCps = false;

    // --- Crew ---
    /** Show a toast in-game when a crew message arrives. */
    public boolean chatToasts = true;
    /** Announce which server you're on to your crew, enabling "join friend". */
    public boolean shareServer = true;

    // --- Cosmetics ---
    /** Render Blurred capes for yourself and crew who have one. */
    public boolean showCapes = true;

    // --- Menus ---
    /**
     * Restyle Minecraft's own menus in the ocean theme (background, buttons,
     * title-screen branding).
     *
     * <p>A single switch on purpose: reskinning vanilla UI is the change most
     * likely to collide with another mod that does the same, so turning it off
     * has to be one obvious step rather than a hunt through sub-options.
     */
    public boolean styleMenus = true;

    /** Show the real ping in the tab list instead of the five-bar signal icon. */
    public boolean numericPing = true;

    public static BlurredConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("blurred.json");
    }

    private static BlurredConfig load() {
        Path p = path();
        if (Files.exists(p)) {
            try {
                BlurredConfig loaded = GSON.fromJson(Files.readString(p), BlurredConfig.class);
                if (loaded != null) {
                    return loaded;
                }
            } catch (IOException | RuntimeException e) {
                // A corrupt config must not stop the game from starting — fall
                // back to defaults and say so.
                BlurredMod.LOGGER.warn("Could not read blurred.json, using defaults", e);
            }
        }
        return new BlurredConfig();
    }

    public void save() {
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(this));
        } catch (IOException e) {
            BlurredMod.LOGGER.warn("Could not save blurred.json", e);
        }
    }
}
