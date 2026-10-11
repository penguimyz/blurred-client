package dev.blurredclient.mod.module.feature;

import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.setting.ColorSetting;
import dev.blurredclient.mod.setting.ModeSetting;

/**
 * Recolours the outline around the block you're looking at. The colour you
 * pick includes its transparency; Rainbow cycles, Accent follows the menus.
 */
public final class BlockOutlineModule extends Module {
    private final ModeSetting<EffectsModules.Tint> colour;
    private final ColorSetting custom;

    public BlockOutlineModule() {
        super("block_outline", "Block outline", "Your own colour (or rainbow) for the outline around the block you look at.",
                Category.VISUAL, false);
        this.colour = add(new ModeSetting<>("colour", "Colour", "Accent, a colour you pick, or rainbow.",
                EffectsModules.Tint.CUSTOM, EffectsModules.Tint::label));
        this.custom = add(new ColorSetting("custom", "Custom colour", "Used when Colour is Custom. Alpha sets how see-through it is.",
                0xCC26B4E6));
        custom.visibleIf(() -> colour.get() == EffectsModules.Tint.CUSTOM);
    }

    /** The outline colour to draw, given vanilla's. */
    public int colour(int vanilla) {
        if (!isActive()) {
            return vanilla;
        }
        int c = EffectsModules.tint(colour, custom, 0f);
        // Accent and Rainbow come back opaque; keep them a touch see-through like vanilla's.
        return colour.get() == EffectsModules.Tint.CUSTOM ? c : (0xCC << 24) | (c & 0xFFFFFF);
    }
}
