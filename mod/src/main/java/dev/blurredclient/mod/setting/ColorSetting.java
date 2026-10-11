package dev.blurredclient.mod.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * An ARGB colour.
 *
 * <p>Stored as {@code "#AARRGGBB"} text rather than a raw int, so the config
 * file stays readable and the launcher can hand it straight to CSS.
 */
public final class ColorSetting extends Setting<Integer> {
    public ColorSetting(String key, String label, String hint, int defaultArgb) {
        super(key, label, hint, defaultArgb);
    }

    public int argb() {
        return value;
    }

    public static String toHex(int argb) {
        return String.format("#%08X", argb);
    }

    /**
     * Parse {@code #RRGGBB} (opaque) or {@code #AARRGGBB}.
     *
     * @return the colour, or {@code null} if the text isn't one
     */
    public static Integer parseHex(String text) {
        if (text == null) {
            return null;
        }
        String hex = text.startsWith("#") ? text.substring(1) : text;
        if (hex.length() != 6 && hex.length() != 8) {
            return null;
        }
        try {
            long parsed = Long.parseLong(hex, 16);
            return hex.length() == 6 ? (int) (0xFF000000L | parsed) : (int) parsed;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    protected Integer normalise(Integer next) {
        return next == null ? value : next;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(toHex(value));
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            Integer parsed = parseHex(json.getAsString());
            if (parsed != null) {
                value = parsed;
            }
        }
    }
}
