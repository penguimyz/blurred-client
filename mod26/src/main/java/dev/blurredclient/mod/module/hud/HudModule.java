package dev.blurredclient.mod.module.hud;

import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.module.ModuleState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A module that draws on the HUD and can be placed in the layout editor.
 *
 * <p>The contract is two calls per frame: {@link #measure} says how big the
 * module is right now, then {@link #render} draws it at 0,0 at full size. The
 * caller ({@link HudRenderer}) owns positioning and scaling, so no module has
 * to know about anchors, screen size or the editor.
 *
 * <p>{@code preview} is true inside the layout editor: modules then draw
 * placeholder content at its widest ("999 FPS", a full armour set), so the
 * player lays things out at the size they'll actually reach in play.
 */
public abstract class HudModule extends Module {
    /** A measured size in unscaled GUI pixels. */
    public record Size(int width, int height) {}

    private final HudPlacement defaultPlacement;
    private HudPlacement placement;

    protected HudModule(String id, String name, String description,
                        boolean defaultEnabled, HudPlacement defaultPlacement) {
        this(id, name, description, Category.HUD, defaultEnabled, defaultPlacement);
    }

    /** For HUD modules that belong in another category's panel (Hit timing under Combat). */
    protected HudModule(String id, String name, String description, Category category,
                        boolean defaultEnabled, HudPlacement defaultPlacement) {
        super(id, name, description, category, defaultEnabled);
        this.defaultPlacement = defaultPlacement;
        this.placement = defaultPlacement.copy();
    }

    public HudPlacement placement() {
        return placement;
    }

    public void setPlacement(HudPlacement next) {
        this.placement = next.copy();
    }

    public void resetPlacement() {
        this.placement = defaultPlacement.copy();
    }

    /**
     * Whether this module is positioned and drawn on its own. False for
     * readouts sitting inside the info bar, which the bar draws.
     */
    public boolean drawsItself() {
        return true;
    }

    /**
     * Where to draw this frame, overriding the placement: for modules that
     * follow something in the world (Block info beside its block). Null means
     * use the placement. {@code w, h} are the scaled size.
     */
    public int[] screenPosition(Minecraft client, int screenW, int screenH, int w, int h) {
        return null;
    }

    /** Current size, or {@code null} when there's nothing to draw this frame. */
    public abstract Size measure(Minecraft client, boolean preview);

    /** Draw at 0,0 at full size. Only called after a non-null {@link #measure}. */
    public abstract void render(GuiGraphicsExtractor ctx, Minecraft client, boolean preview);

    @Override
    public ModuleState writeState() {
        ModuleState state = super.writeState();
        state.placement = placement.copy();
        return state;
    }

    @Override
    public void readState(ModuleState state) {
        super.readState(state);
        HudPlacement saved = state == null ? null : state.placement;
        // A hand-edited anchor that doesn't exist comes back as null from Gson.
        if (saved == null || saved.anchor == null || !(saved.scale > 0f)) {
            placement = defaultPlacement.copy();
        } else {
            placement = saved.copy();
            placement.scale = Math.max(0.5f, Math.min(2f, placement.scale));
        }
    }
}
