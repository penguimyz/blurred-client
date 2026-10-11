package dev.blurredclient.mod.ui.kit;

import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.module.feature.AppearanceModule;

/** The skin the Appearance module currently selects. */
public final class Skins {
    private Skins() {}

    public static Skin current() {
        return switch (Modules.APPEARANCE.style()) {
            case MODERN -> ModernSkin.INSTANCE;
            case GLASS -> GlassSkin.INSTANCE;
            case LIQUID -> LiquidSkin.INSTANCE;
            case PIXEL -> PixelSkin.INSTANCE;
        };
    }
}
