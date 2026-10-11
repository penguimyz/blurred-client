package dev.blurredclient.mod.module.feature;

import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;

/**
 * A module that is only an on/off switch. The behaviour lives wherever the
 * game is hooked (a mixin, usually), which checks {@link #isActive()}.
 */
public final class SimpleModule extends Module {
    public SimpleModule(String id, String name, String description, Category category, boolean defaultEnabled) {
        super(id, name, description, category, defaultEnabled);
    }
}
