package dev.blurredclient.mod.module.feature;

import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.module.ModuleIds;
import dev.blurredclient.mod.setting.SliderSetting;

/**
 * Lowers the flames drawn over your view while you're on fire.
 *
 * <p>The burning itself is untouched — you still take the damage and still see
 * that you're alight at the bottom of the screen. This only stops the overlay
 * covering the middle of it.
 */
public final class LowFireModule extends Module {
    private final SliderSetting drop;

    public LowFireModule() {
        super(ModuleIds.LOW_FIRE, "Low fire",
                "Lower the fire overlay so it doesn't cover the middle of your screen. You can still see you're burning.",
                Category.VISUAL, false);
        this.drop = add(new SliderSetting("drop", "How far down", "0 is vanilla; higher pushes the flames further down.",
                0.3, 0, 0.6, 0.02, ""));
    }

    public float drop() {
        return drop.floatValue();
    }
}
