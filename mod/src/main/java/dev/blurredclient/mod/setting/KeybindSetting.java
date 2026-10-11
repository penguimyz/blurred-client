package dev.blurredclient.mod.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * A key, stored as its GLFW key code. {@link #NONE} means unbound.
 *
 * <p>For keys that only matter inside Blurred's own screens and modules.
 * Anything the player expects to find in vanilla Controls (the menu key, zoom)
 * is registered as a real {@code KeyBinding} instead, so it shows up there and
 * takes part in conflict detection.
 */
public final class KeybindSetting extends Setting<Integer> {
    public static final int NONE = -1;

    public KeybindSetting(String key, String label, String hint, int defaultKey) {
        super(key, label, hint, defaultKey);
    }

    public int keyCode() {
        return value;
    }

    public boolean isBound() {
        return value != NONE;
    }

    @Override
    protected Integer normalise(Integer next) {
        return next == null ? NONE : next;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            value = json.getAsInt();
        }
    }
}
