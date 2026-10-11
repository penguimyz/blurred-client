package dev.blurredclient.mod.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.function.Function;

/**
 * One choice out of an enum.
 *
 * <p>Stored by constant name rather than ordinal, so reordering or inserting
 * a mode later doesn't silently change what everyone's config means.
 */
public final class ModeSetting<E extends Enum<E>> extends Setting<E> {
    private final Class<E> type;
    private final Function<E, String> labeller;

    public ModeSetting(String key, String label, String hint, E defaultValue, Function<E, String> labeller) {
        super(key, label, hint, defaultValue);
        this.type = defaultValue.getDeclaringClass();
        this.labeller = labeller;
    }

    public E[] options() {
        return type.getEnumConstants();
    }

    /** Human label for a mode, e.g. "Top left" for {@code TOP_LEFT}. */
    public String labelOf(E mode) {
        return labeller.apply(mode);
    }

    public void cycle() {
        E[] all = options();
        value = all[(value.ordinal() + 1) % all.length];
    }

    public void cycleBack() {
        E[] all = options();
        value = all[(value.ordinal() - 1 + all.length) % all.length];
    }

    @Override
    protected E normalise(E next) {
        return next == null ? value : next;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value.name());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json == null || !json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
            return;
        }
        String name = json.getAsString();
        for (E option : options()) {
            if (option.name().equals(name)) {
                value = option;
                return;
            }
        }
    }
}
