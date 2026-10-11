package dev.blurredclient.mod.module.feature;

import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.setting.SliderSetting;

/**
 * Right Shift in a world opens the menu as a 3D hologram standing in front
 * of you instead of a flat screen. Aim at a module and right-click to switch
 * it; walk around it; press the key again to put it away.
 */
public final class HologramMenuModule extends Module {
    private final SliderSetting distance;
    private final SliderSetting size;

    public HologramMenuModule() {
        super("hologram_menu", "3D menu",
                "In a world, the menu key opens a hologram panel in front of you: aim and right-click modules to toggle them.",
                Category.MENUS, false);
        this.distance = add(new SliderSetting("distance", "Distance", "How far in front of you it appears.",
                2.3, 1.4, 4.0, 0.1, " blocks"));
        this.size = add(new SliderSetting("size", "Size", "How big the menu is.", 1.3, 0.7, 2.0, 0.05, "x"));
    }

    public float size() {
        return size.floatValue();
    }

    public double distance() {
        return distance.get();
    }
}
