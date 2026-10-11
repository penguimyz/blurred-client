package dev.blurredclient.mod.setting;

import com.google.gson.JsonElement;

import java.util.function.BooleanSupplier;

/**
 * One configurable value on a module.
 *
 * <p>The type of setting decides which control the menu draws for it, so a
 * module only declares <em>what</em> it can be configured with — never how
 * that's presented. That is what lets the menu, the launcher's Blurred tab and
 * the config file all agree on a module's options without each keeping a copy.
 *
 * <p>Deliberately free of Minecraft imports, so it can be unit tested without
 * a game on the classpath.
 *
 * @param <T> the value type
 */
public abstract class Setting<T> {
    private final String key;
    private final String label;
    private final String hint;
    private final T defaultValue;
    protected T value;
    private BooleanSupplier visibleIf = () -> true;

    protected Setting(String key, String label, String hint, T defaultValue) {
        this.key = key;
        this.label = label;
        this.hint = hint;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    /** Stable id used in the config file. Never shown to the player. */
    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    /** One sentence saying what the setting actually does. */
    public String hint() {
        return hint;
    }

    public T get() {
        return value;
    }

    public T defaultValue() {
        return defaultValue;
    }

    /** Set the value. Subclasses normalise it (clamp, snap) before storing. */
    public void set(T next) {
        this.value = normalise(next);
    }

    public void reset() {
        this.value = defaultValue;
    }

    /** Hook for subclasses that need to clamp or validate. */
    protected T normalise(T next) {
        return next;
    }

    /**
     * Only show this setting when {@code condition} holds — e.g. a colour that
     * only matters while its parent toggle is on. Returns this for chaining.
     */
    public Setting<T> visibleIf(BooleanSupplier condition) {
        this.visibleIf = condition;
        return this;
    }

    public boolean isVisible() {
        return visibleIf.getAsBoolean();
    }

    public abstract JsonElement toJson();

    /**
     * Read a stored value. A value of the wrong shape — hand-edited, or from a
     * future version — leaves the setting at its current value instead of
     * throwing, because a bad config must never stop the game from starting.
     */
    public abstract void fromJson(JsonElement json);
}
