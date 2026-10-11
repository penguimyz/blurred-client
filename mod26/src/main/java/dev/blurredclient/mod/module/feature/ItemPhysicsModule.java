package dev.blurredclient.mod.module.feature;

import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.SliderSetting;

/**
 * Dropped items behave like objects: they tumble while they fly (faster the
 * harder they're thrown), then roll over and settle flat on the ground —
 * blocks on a side — instead of hovering and spinning in place. Purely how
 * they're drawn; where an item actually is, and picking it up, is untouched.
 */
public final class ItemPhysicsModule extends Module {
    private final BoolSetting tumble;
    private final SliderSetting tumbleSpeed;

    public ItemPhysicsModule() {
        super("item_physics", "Item physics",
                "Dropped items tumble through the air and roll to rest flat on the ground instead of floating and spinning.",
                Category.VISUAL, false);
        this.tumble = add(new BoolSetting("tumble", "Tumble in the air", "Items spin end over end while they fly.", true));
        this.tumbleSpeed = add(new SliderSetting("tumble_speed", "Tumble speed", "How fast they turn in the air.",
                1.0, 0.2, 3, 0.1, "x"));
        tumbleSpeed.visibleIf(tumble::isOn);
    }

    /** Spin rate in degrees per second for an item flying at {@code speed} blocks per second. */
    public float tumbleRate(float speed) {
        if (!tumble.isOn()) {
            return 0f;
        }
        return Math.max(140f, Math.min(900f, speed * 120f)) * tumbleSpeed.floatValue();
    }
}
