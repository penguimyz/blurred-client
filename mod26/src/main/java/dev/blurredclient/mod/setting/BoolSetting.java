package dev.blurredclient.mod.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** An on/off switch. */
public final class BoolSetting extends Setting<Boolean> {
    public BoolSetting(String key, String label, String hint, boolean defaultValue) {
        super(key, label, hint, defaultValue);
    }

    public boolean isOn() {
        return value;
    }

    public void toggle() {
        value = !value;
    }

    @Override
    protected Boolean normalise(Boolean next) {
        return next != null && next;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) {
            value = json.getAsBoolean();
        }
    }
}
