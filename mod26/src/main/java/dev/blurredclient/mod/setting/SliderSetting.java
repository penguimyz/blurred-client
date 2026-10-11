package dev.blurredclient.mod.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * A number in a range, snapped to a step.
 *
 * <p>Snapping happens on every write, including loading from disk, so a
 * hand-edited {@code 1.4999999} can't leave a slider sitting between notches.
 */
public final class SliderSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;
    private final String suffix;

    public SliderSetting(String key, String label, String hint,
                         double defaultValue, double min, double max, double step, String suffix) {
        super(key, label, hint, defaultValue);
        if (!(min < max) || step <= 0) {
            throw new IllegalArgumentException("bad slider range for " + key);
        }
        this.min = min;
        this.max = max;
        this.step = step;
        this.suffix = suffix;
        this.value = normalise(defaultValue);
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double step() {
        return step;
    }

    /** Unit drawn after the number, e.g. {@code "x"} or {@code "%"}. */
    public String suffix() {
        return suffix;
    }

    public float floatValue() {
        return value.floatValue();
    }

    public int intValue() {
        return (int) Math.round(value);
    }

    /** Position along the track, 0..1. Used by the slider widget. */
    public double fraction() {
        return (value - min) / (max - min);
    }

    public void setFraction(double fraction) {
        set(min + Math.max(0d, Math.min(1d, fraction)) * (max - min));
    }

    @Override
    protected Double normalise(Double next) {
        if (next == null || next.isNaN()) {
            return value;
        }
        double snapped = min + Math.round((next - min) / step) * step;
        // Re-round to kill float noise from the multiply (0.1 * 3 = 0.30000000000000004).
        snapped = Math.round(snapped * 1e6) / 1e6;
        return Math.max(min, Math.min(max, snapped));
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            set(json.getAsDouble());
        }
    }
}
