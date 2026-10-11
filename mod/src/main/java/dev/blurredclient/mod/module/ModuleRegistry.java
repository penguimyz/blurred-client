package dev.blurredclient.mod.module;

import dev.blurredclient.mod.config.ConfigV2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every module, plus the profile machinery that moves their state in and out
 * of a {@link ConfigV2}.
 *
 * <p>The modules themselves are the live state while the game runs; the config
 * object is only brought up to date by {@link #capture()} before a save or a
 * profile switch. That keeps toggling a module a plain field write rather than
 * a map lookup on every frame.
 *
 * <p>No Minecraft imports: the HUD and the menu sit on top of this, and the
 * profile logic is unit tested on its own.
 */
public final class ModuleRegistry {
    private final Map<String, Module> byId = new LinkedHashMap<>();
    private ConfigV2 config = new ConfigV2().sanitise();

    public <M extends Module> M register(M module) {
        if (byId.putIfAbsent(module.id(), module) != null) {
            throw new IllegalStateException("duplicate module id " + module.id());
        }
        return module;
    }

    /** Every registered module, in registration order. */
    public List<Module> all() {
        return Collections.unmodifiableList(new ArrayList<>(byId.values()));
    }

    public Module get(String id) {
        return byId.get(id);
    }

    public List<Module> inCategory(Category category) {
        List<Module> out = new ArrayList<>();
        for (Module m : byId.values()) {
            if (m.category() == category) {
                out.add(m);
            }
        }
        return out;
    }

    /**
     * Modules whose name, description or any setting label contains
     * {@code query}, case-insensitively. Blank query returns everything.
     */
    public List<Module> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return all();
        }
        List<Module> out = new ArrayList<>();
        for (Module m : byId.values()) {
            if (matches(m, q)) {
                out.add(m);
            }
        }
        return out;
    }

    private static boolean matches(Module m, String q) {
        if (m.name().toLowerCase(Locale.ROOT).contains(q)
                || m.description().toLowerCase(Locale.ROOT).contains(q)) {
            return true;
        }
        return m.settings().stream().anyMatch(s -> s.label().toLowerCase(Locale.ROOT).contains(q));
    }

    // ------------------------------------------------------------------
    // Config and profiles
    // ------------------------------------------------------------------

    public ConfigV2 config() {
        return config;
    }

    /** Adopt {@code next} as the config and push its state into every module. */
    public void load(ConfigV2 next) {
        this.config = next.sanitise();
        applyAll();
    }

    /**
     * Copy every module's live state into the config, under the active profile
     * or the global section as appropriate. States for modules this build
     * doesn't know are left in place.
     */
    public ConfigV2 capture() {
        Map<String, ModuleState> profile = config.profile(config.activeProfile);
        for (Module m : byId.values()) {
            (m.perProfile() ? profile : config.global).put(m.id(), m.writeState());
        }
        return config;
    }

    private void applyAll() {
        Map<String, ModuleState> profile = config.profile(config.activeProfile);
        for (Module m : byId.values()) {
            m.readState((m.perProfile() ? profile : config.global).get(m.id()));
        }
    }

    public String activeProfile() {
        return config.activeProfile;
    }

    public List<String> profileNames() {
        return new ArrayList<>(config.profiles.keySet());
    }

    /** Save the current profile's state, then load {@code name}'s (creating it empty). */
    public void switchProfile(String name) {
        if (name == null || name.isBlank() || name.equals(config.activeProfile)) {
            return;
        }
        capture();
        config.activeProfile = name;
        applyAll();
    }

    /**
     * Create {@code name} as a copy of the current profile and switch to it.
     *
     * @return false if a profile with that name already exists
     */
    public boolean duplicateProfile(String name) {
        if (name == null || name.isBlank() || config.profiles.containsKey(name)) {
            return false;
        }
        capture();
        Map<String, ModuleState> copy = new LinkedHashMap<>();
        for (Module m : byId.values()) {
            if (m.perProfile()) {
                copy.put(m.id(), m.writeState());
            }
        }
        config.profiles.put(name, copy);
        config.activeProfile = name;
        return true;
    }

    /** @return false if the name is taken, blank, or {@code from} doesn't exist */
    public boolean renameProfile(String from, String to) {
        if (to == null || to.isBlank() || !config.profiles.containsKey(from) || config.profiles.containsKey(to)) {
            return false;
        }
        // Rebuild the map so the renamed profile keeps its position in the list.
        Map<String, Map<String, ModuleState>> renamed = new LinkedHashMap<>();
        config.profiles.forEach((k, v) -> renamed.put(k.equals(from) ? to : k, v));
        config.profiles = renamed;
        if (config.activeProfile.equals(from)) {
            config.activeProfile = to;
        }
        return true;
    }

    /**
     * Delete a profile. The last remaining profile can't be deleted; deleting
     * the active one switches to the first that's left.
     */
    public boolean deleteProfile(String name) {
        if (!config.profiles.containsKey(name) || config.profiles.size() <= 1) {
            return false;
        }
        boolean wasActive = name.equals(config.activeProfile);
        config.profiles.remove(name);
        if (wasActive) {
            config.activeProfile = config.profiles.keySet().iterator().next();
            applyAll();
        }
        return true;
    }
}
