package dev.blurredclient.mod.module.hud;

import com.mojang.blaze3d.platform.InputConstants;
import dev.blurredclient.mod.hud.HudStyle;
import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A list of the modules you've given a key, with the key and whether each is on. */
public final class KeybindsHudModule extends HudModule {
    public KeybindsHudModule() {
        super("keybinds_hud", "Keybinds", "Shows the modules you've bound to keys, and which are on.",
                Category.HUD, false, new HudPlacement(HudPlacement.Anchor.MIDDLE_LEFT, 4, 40));
    }

    private record Row(String key, String name, boolean on) {}

    private static List<Row> rows(boolean preview) {
        List<Row> out = new ArrayList<>();
        if (preview) {
            out.add(new Row("R", "Zoom", false));
            out.add(new Row("G", "Item physics", true));
            return out;
        }
        for (Module m : Modules.REGISTRY.all()) {
            if (m.toggleKey() != null && m.toggleKey().isBound()) {
                out.add(new Row(keyName(m.toggleKey().keyCode()), m.name(), m.isEnabled()));
            }
        }
        return out;
    }

    static String keyName(int code) {
        String s = InputConstants.Type.KEYSYM.getOrCreate(code).getDisplayName().getString();
        return s.length() > 6 ? s.substring(0, 6) : s.toUpperCase();
    }

    @Override
    public Size measure(Minecraft client, boolean preview) {
        List<Row> rows = rows(preview);
        if (rows.isEmpty()) {
            return null;
        }
        var font = client.font;
        int w = 0;
        for (Row r : rows) {
            w = Math.max(w, Draw.width(font, HudStyle.text(r.name()), 0.75f) + Draw.width(font, HudStyle.bold(r.key()), 0.7f));
        }
        return new Size(w + 28, rows.size() * 12 + 4);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, Minecraft client, boolean preview) {
        List<Row> rows = rows(preview);
        Size s = measure(client, preview);
        if (s == null) {
            return;
        }
        var font = client.font;
        HudStyle.panel(ctx, 0, 0, s.width(), s.height());
        int y = 2;
        int accent = HudStyle.accent();
        for (Row r : rows) {
            int kw = Draw.width(font, HudStyle.bold(r.key()), 0.7f) + 6;
            Draw.rect(ctx, 3, y + 1, 3 + kw, y + 10, 0x30FFFFFF);
            Draw.text(ctx, font, HudStyle.bold(r.key()), 6, y + 2, 0xFFFFFFFF, 0.7f);
            Draw.text(ctx, font, HudStyle.text(r.name()), 3 + kw + 5, y + 2, r.on() ? 0xFFFFFFFF : 0xFF7C8090, 0.75f);
            Draw.circle(ctx, s.width() - 6, y + 5.5f, 2f, r.on() ? accent : Colors.fade(0xFFFFFFFF, 0.25f));
            y += 12;
        }
    }
}
