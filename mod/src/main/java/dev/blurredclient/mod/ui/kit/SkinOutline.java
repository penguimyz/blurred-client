package dev.blurredclient.mod.ui.kit;

import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import net.minecraft.client.gui.DrawContext;

/** Appearance → Outlines: an accent edge, with a faint glow, around menu windows, panels and popups. */
final class SkinOutline {
    private SkinOutline() {}

    static void draw(DrawContext ctx, int x, int y, int w, int h, int r, Skin.Surface surface) {
        if (!Modules.APPEARANCE.outlines() || surface == Skin.Surface.INSET || w < 4 || h < 4) {
            return;
        }
        int accent = dev.blurredclient.mod.Theme.chroma(Skins.current().accent());
        Draw.roundedOutline(ctx, x - 1, y - 1, w + 2, h + 2, r + 1, Colors.fade(accent, 0.28f));
        Draw.roundedOutline(ctx, x, y, w, h, r, Colors.fade(accent, 0.9f));
    }
}
