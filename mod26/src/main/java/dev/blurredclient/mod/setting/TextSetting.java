package dev.blurredclient.mod.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A short line of free text, capped in length. */
public final class TextSetting extends Setting<String> {
    private final int maxLength;

    public TextSetting(String key, String label, String hint, String defaultValue, int maxLength) {
        super(key, label, hint, defaultValue);
        this.maxLength = maxLength;
    }

    public int maxLength() {
        return maxLength;
    }

    @Override
    protected String normalise(String next) {
        if (next == null) {
            return "";
        }
        return next.length() > maxLength ? next.substring(0, maxLength) : next;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            set(json.getAsString());
        }
    }
}
