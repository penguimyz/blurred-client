package dev.blurredclient.mod.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * A small square of on/off pixels you paint yourself (the crosshair
 * painter). Stored as a string of '0' and '1', row by row.
 */
public final class PixelGridSetting extends Setting<String> {
    private final int size;
    private java.util.function.BooleanSupplier mirror = () -> false;

    public PixelGridSetting(String key, String label, String hint, int size, String defaultValue) {
        super(key, label, hint, defaultValue);
        this.size = size;
    }

    public int size() {
        return size;
    }

    /** Paint symmetrically (both axes) while this is true. */
    public PixelGridSetting mirrorWhen(java.util.function.BooleanSupplier mirror) {
        this.mirror = mirror;
        return this;
    }

    public boolean mirrored() {
        return mirror.getAsBoolean();
    }

    public boolean on(int x, int y) {
        int i = y * size + x;
        return x >= 0 && y >= 0 && x < size && y < size && i < value.length() && value.charAt(i) == '1';
    }

    /** Set one pixel (and its mirror images, if mirroring). */
    public void paint(int x, int y, boolean on) {
        char[] c = value.toCharArray();
        put(c, x, y, on);
        if (mirrored()) {
            put(c, size - 1 - x, y, on);
            put(c, x, size - 1 - y, on);
            put(c, size - 1 - x, size - 1 - y, on);
        }
        set(new String(c));
    }

    private void put(char[] c, int x, int y, boolean on) {
        if (x >= 0 && y >= 0 && x < size && y < size) {
            c[y * size + x] = on ? '1' : '0';
        }
    }

    public void clear() {
        set("0".repeat(size * size));
    }

    @Override
    protected String normalise(String next) {
        int n = size * size;
        StringBuilder b = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            b.append(next != null && i < next.length() && next.charAt(i) == '1' ? '1' : '0');
        }
        return b.toString();
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
