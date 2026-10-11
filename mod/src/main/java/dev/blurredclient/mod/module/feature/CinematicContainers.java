package dev.blurredclient.mod.module.feature;

import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.ModeSetting;
import dev.blurredclient.mod.setting.SliderSetting;
import dev.blurredclient.mod.ui.kit.Anim;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.util.hit.HitResult;

/**
 * Opening a chest (or any block with an inventory) zooms the camera in on it
 * and drops the background blur, so you see the chest open in the world
 * while you use it — optionally with the inventory moved aside.
 *
 * <p>Only your own field of view and the GUI change. The camera doesn't turn
 * (turning it would send a new look direction to the server), so the zoom
 * centres on wherever you were looking — which, since you just clicked the
 * chest, is the chest.
 */
public final class CinematicContainers extends Module {
    public enum Side {
        CENTER("Centre"), RIGHT("Right side");

        private final String label;

        Side(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final BoolSetting inside;
    private final SliderSetting zoom;
    private final ModeSetting<Side> side;
    private final BoolSetting noDim;
    private final Anim amount = new Anim(0f, 7f);

    public CinematicContainers() {
        super("cinematic_containers", "Cinematic containers",
                "Open chests from the inside in 3D, and zoom in on furnaces and other blocks you open.",
                Category.VISUAL, false);
        this.inside = add(new BoolSetting("inside", "Open up chests in 3D",
                "Chests, ender chests, barrels and shulker boxes: the camera looks inside and the items sit in it as 3D models. Click them like normal.",
                true));
        this.zoom = add(new SliderSetting("zoom", "Zoom", "How far the camera zooms in on the block.", 1.8, 1.1, 4, 0.1, "x"));
        this.side = add(new ModeSetting<>("side", "Inventory position", "Keep it in the middle, or move it aside so the block shows.",
                Side.RIGHT, Side::label));
        this.noDim = add(new BoolSetting("no_dim", "Clear background", "No blur or darkening behind the inventory.", true));
    }

    public boolean insideView() {
        return inside.isOn();
    }

    /** True while a block's inventory is open and the zoom effect applies (not the 3D view). */
    public boolean engaged() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (dev.blurredclient.mod.fx.ContainerView.INSTANCE.showing()) {
            return false;
        }
        return isActive() && client.currentScreen instanceof HandledScreen<?>
                && client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK;
    }

    /** Field-of-view multiplier, eased in and out. */
    public float fovMultiplier() {
        float a = amount.follow(engaged());
        return 1f / (1f + (zoom.floatValue() - 1f) * a);
    }

    public boolean clearBackground() {
        var screen = MinecraftClient.getInstance().currentScreen;
        return dev.blurredclient.mod.fx.ContainerView.INSTANCE.active(screen) || engaged() && noDim.isOn();
    }

    public boolean moveAside() {
        return engaged() && side.get() == Side.RIGHT;
    }
}
