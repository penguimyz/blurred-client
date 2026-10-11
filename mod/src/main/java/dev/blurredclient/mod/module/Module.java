package dev.blurredclient.mod.module;

import dev.blurredclient.mod.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One feature of the client, with an on/off state and its settings.
 *
 * <p>Everything the mod menu, the HUD editor and the launcher know about a
 * feature comes from here: its id, name, one-line description, category and
 * {@link Setting}s. A module never builds its own settings UI.
 *
 * <p>No Minecraft imports in this base class, so state handling is unit
 * tested. Modules that draw or hook the game extend it in their own packages.
 */
public abstract class Module {
    private final String id;
    private final String name;
    private final String description;
    private final Category category;
    private final boolean defaultEnabled;
    private final List<Setting<?>> settings = new ArrayList<>();
    /** Press to switch the module on or off. Listed after the module's own settings. */
    private final dev.blurredclient.mod.setting.KeybindSetting toggleKey = new dev.blurredclient.mod.setting.KeybindSetting(
            "toggle_key", "Toggle key", "Press this key to switch the module on or off.",
            dev.blurredclient.mod.setting.KeybindSetting.NONE);
    private boolean enabled;

    protected Module(String id, String name, String description, Category category, boolean defaultEnabled) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.defaultEnabled = defaultEnabled;
        this.enabled = defaultEnabled;
    }

    /** Register a setting. Call from the subclass constructor, in display order. */
    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Category category() {
        return category;
    }

    public List<Setting<?>> settings() {
        if (!toggleable()) {
            return Collections.unmodifiableList(settings);
        }
        List<Setting<?>> all = new ArrayList<>(settings);
        all.add(toggleKey);
        return Collections.unmodifiableList(all);
    }

    /** The key that toggles this module, or null for modules that can't be switched off. */
    public dev.blurredclient.mod.setting.KeybindSetting toggleKey() {
        return toggleable() ? toggleKey : null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Enabled and allowed to run right now. What render/tick code should check. */
    public boolean isActive() {
        return enabled && isAvailable();
    }

    public void setEnabled(boolean next) {
        if (next == enabled) {
            return;
        }
        enabled = next;
        if (next) {
            onEnable();
        } else {
            onDisable();
        }
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    /**
     * Whether the module has an on/off switch at all. Some exist only to group
     * settings (Crew sharing, for example) and are always on.
     */
    public boolean toggleable() {
        return true;
    }

    /**
     * Whether the module can run in this game. False when another installed
     * mod already does the job (Jade for block info), or when a server has
     * switched it off. The menu shows why via {@link #unavailableReason()}.
     */
    public boolean isAvailable() {
        return true;
    }

    /** One line for the menu when {@link #isAvailable()} is false. */
    public String unavailableReason() {
        return "";
    }

    /** Whether this module's state is stored per profile or shared by all. */
    public boolean perProfile() {
        return category.perProfile();
    }

    protected void onEnable() {}

    protected void onDisable() {}

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public ModuleState writeState() {
        ModuleState state = new ModuleState();
        state.enabled = enabled;
        for (Setting<?> s : settings()) {
            state.settings.put(s.key(), s.toJson());
        }
        return state;
    }

    /**
     * Apply saved state, or reset to defaults when {@code state} is null.
     * Unknown setting keys are ignored; missing ones go back to default, so
     * switching profile never leaves a value over from the previous one.
     */
    public void readState(ModuleState state) {
        boolean nextEnabled = state != null && state.enabled != null ? state.enabled : defaultEnabled;
        if (!toggleable()) {
            nextEnabled = true;
        }
        // Assign directly: loading a config isn't the player toggling
        // something, so the enable/disable hooks shouldn't fire.
        enabled = nextEnabled;

        for (Setting<?> s : settings()) {
            s.reset();
            if (state != null && state.settings != null) {
                s.fromJson(state.settings.get(s.key()));
            }
        }
    }
}
