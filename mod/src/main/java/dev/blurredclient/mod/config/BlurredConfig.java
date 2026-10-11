package dev.blurredclient.mod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.blurredclient.mod.BlurredMod;
import dev.blurredclient.mod.module.Modules;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Reads and writes {@code config/blurred.json} in the instance.
 *
 * <p>Per-instance rather than global on purpose: instances in this launcher
 * are isolated by design, and a HUD layout that suits a PvP instance rarely
 * suits a modded one.
 *
 * <p>The live state is the modules themselves ({@link Modules#REGISTRY}); this
 * class only moves it to and from disk. A v1 file is converted once, with the
 * original kept alongside as {@code blurred.json.v1.bak}.
 *
 * <p>Gson comes along with Minecraft, so this needs no extra dependency.
 */
public final class BlurredConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BlurredConfig() {}

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("blurred.json");
    }

    /** Load from disk into the modules. Never throws: a bad file means defaults. */
    public static void load() {
        Modules.REGISTRY.load(read());
    }

    private static ConfigV2 read() {
        Path p = path();
        if (!Files.exists(p)) {
            return new ConfigV2();
        }
        try {
            JsonElement root = JsonParser.parseString(Files.readString(p));
            if (!root.isJsonObject()) {
                throw new IllegalStateException("not a JSON object");
            }
            if (ConfigMigrator.isV1(root.getAsJsonObject())) {
                ConfigV2 migrated = ConfigMigrator.fromV1(root.getAsJsonObject());
                Files.copy(p, p.resolveSibling("blurred.json.v1.bak"), StandardCopyOption.REPLACE_EXISTING);
                write(migrated);
                BlurredMod.LOGGER.info("Migrated blurred.json to v2 (old file kept as blurred.json.v1.bak)");
                return migrated;
            }
            ConfigV2 loaded = GSON.fromJson(root, ConfigV2.class);
            return loaded == null ? new ConfigV2() : loaded;
        } catch (IOException | RuntimeException e) {
            // A corrupt config must not stop the game from starting — fall
            // back to defaults and say so.
            BlurredMod.LOGGER.warn("Could not read blurred.json, using defaults", e);
            return new ConfigV2();
        }
    }

    /** Capture every module's state and write it out. */
    public static void save() {
        write(Modules.REGISTRY.capture());
    }

    private static void write(ConfigV2 cfg) {
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            // Write-then-move, so a crash mid-write can't leave half a file
            // that resets everything to defaults on the next start.
            Path tmp = p.resolveSibling("blurred.json.tmp");
            Files.writeString(tmp, GSON.toJson(cfg));
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            BlurredMod.LOGGER.warn("Could not save blurred.json", e);
        }
    }
}
