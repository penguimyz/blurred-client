package dev.blurredclient.mod.module;

import com.google.gson.JsonElement;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A module's saved state, exactly as it appears in {@code blurred.json}.
 *
 * <p>Separate from {@link Module} so the file can carry state for modules this
 * build doesn't have — one from a newer version, or a restricted one that
 * isn't allowed in this instance — and write it back untouched instead of
 * dropping it.
 *
 * <p>Fields are nullable on purpose: anything missing falls back to the
 * module's default rather than to Gson's zero value.
 */
public final class ModuleState {
    public Boolean enabled;
    public Map<String, JsonElement> settings = new LinkedHashMap<>();
    /** Only present for HUD modules. */
    public HudPlacement placement;
}
