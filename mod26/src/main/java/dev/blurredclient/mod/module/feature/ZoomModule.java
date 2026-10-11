package dev.blurredclient.mod.module.feature;

import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.module.ModuleIds;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.SliderSetting;
import dev.blurredclient.mod.ui.kit.Anim;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Hold a key to zoom, OptiFine-style. Scroll while zoomed to zoom further.
 *
 * <p>Narrows your own field of view and nothing else — the same thing a
 * spyglass does, without the item. The key is a real keybinding (default C,
 * like OptiFine), so it's rebindable in Controls.
 */
public final class ZoomModule extends Module {
    private final SliderSetting factor;
    private final BoolSetting smooth;
    private final BoolSetting scroll;
    private final Anim anim = new Anim(1f, 14f);
    private KeyMapping key;
    private float scrollMult = 1f;

    public ZoomModule() {
        super(ModuleIds.ZOOM, "Zoom", "Hold the zoom key (C by default) to zoom in. Scroll to zoom further.",
                Category.VISUAL, true);
        this.factor = add(new SliderSetting("factor", "Zoom amount", "How far the key zooms in.", 4, 2, 15, 0.5, "x"));
        this.smooth = add(new BoolSetting("smooth", "Smooth", "Ease in and out instead of snapping.", true));
        this.scroll = add(new BoolSetting("scroll", "Scroll to adjust", "Mouse wheel zooms further while the key is held.", true));
    }

    public void bindKey(KeyMapping key) {
        this.key = key;
    }

    public boolean zooming() {
        return isActive() && key != null && key.isDown() && Minecraft.getInstance().gui.screen() == null;
    }

    /** Multiply the field of view by this. 1 when not zooming. */
    public float fovMultiplier() {
        boolean on = zooming();
        if (!on) {
            scrollMult = 1f;
        }
        float target = on ? 1f / (factor.floatValue() * scrollMult) : 1f;
        if (!smooth.isOn()) {
            anim.snap(target);
            return target;
        }
        anim.target(target);
        return anim.get();
    }

    /** Called on mouse wheel; returns true if zoom used it (so the hotbar doesn't scroll). */
    public boolean onScroll(double amount) {
        if (!zooming() || !scroll.isOn() || amount == 0) {
            return false;
        }
        scrollMult = Math.max(0.5f, Math.min(4f, scrollMult * (amount > 0 ? 1.15f : 1f / 1.15f)));
        return true;
    }
}
