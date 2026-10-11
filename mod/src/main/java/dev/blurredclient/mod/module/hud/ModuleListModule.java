package dev.blurredclient.mod.module.hud;

import dev.blurredclient.mod.hud.HudStyle;
import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.module.ModuleIds;
import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.ModeSetting;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import dev.blurredclient.mod.ui.kit.Anim;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The list of switched-on modules down the side of the screen, longest name
 * first, each with an accent bar — the "ArrayList" every client HUD has.
 *
 * <p>Rows slide in and out as modules are toggled rather than popping, and the
 * list lines up against whichever side it's anchored to.
 */
public final class ModuleListModule extends HudModule {
    public enum Colour {
        STATIC("Accent"), RAINBOW("Rainbow"), WAVE("Wave");

        private final String label;

        Colour(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final float SCALE = 0.85f;
    private static final int ROW_H = 11;

    private final ModeSetting<Colour> colour;
    private final BoolSetting includeHud;
    private final BoolSetting background;
    private final Map<String, Anim> slide = new HashMap<>();

    public ModuleListModule() {
        super(ModuleIds.MODULE_LIST, "Module list",
                "Lists the modules you have on, along the edge of the screen.",
                false, new HudPlacement(HudPlacement.Anchor.TOP_RIGHT, -2, 2));
        this.colour = add(new ModeSetting<>("colour", "Colour", "Accent colour, a moving rainbow, or a wave.",
                Colour.WAVE, Colour::label));
        this.includeHud = add(new BoolSetting("include_hud", "Show HUD modules",
                "Also list readouts like Frame rate and Clock.", false));
        this.background = add(new BoolSetting("background", "Background", "A dark strip behind each row.", true));
    }

    private boolean listed(Module m) {
        return m != this && m.toggleable() && (includeHud.isOn() || m.category() != Category.HUD);
    }

    private List<String> names(MinecraftClient client, boolean preview) {
        List<String> out = new ArrayList<>();
        if (preview) {
            out.add("Zoom");
            out.add("View model");
            out.add("Numeric ping");
            out.add("Low fire");
            return out;
        }
        for (Module m : Modules.REGISTRY.all()) {
            if (!listed(m)) {
                continue;
            }
            Anim a = slide.computeIfAbsent(m.id(), k -> new Anim(m.isEnabled() ? 1f : 0f, 10f));
            a.target(m.isActive() ? 1f : 0f);
            if (a.get() > 0.01f) {
                out.add(m.name());
            }
        }
        var font = client.textRenderer;
        out.sort(Comparator.comparingInt((String s) -> font.getWidth(HudStyle.text(s))).reversed());
        return out;
    }

    private float shownFraction(String name, boolean preview) {
        if (preview) {
            return 1f;
        }
        for (Module m : Modules.REGISTRY.all()) {
            if (m.name().equals(name)) {
                Anim a = slide.get(m.id());
                return a == null ? 1f : a.get();
            }
        }
        return 1f;
    }

    @Override
    public Size measure(MinecraftClient client, boolean preview) {
        List<String> names = names(client, preview);
        if (names.isEmpty()) {
            return null;
        }
        int widest = 0;
        for (String n : names) {
            widest = Math.max(widest, Draw.width(client.textRenderer, HudStyle.text(n), SCALE));
        }
        return new Size(widest + 9, names.size() * ROW_H);
    }

    @Override
    public void render(DrawContext ctx, MinecraftClient client, boolean preview) {
        List<String> names = names(client, preview);
        Size size = measure(client, preview);
        if (size == null) {
            return;
        }
        var font = client.textRenderer;
        boolean right = placement().anchor.fx >= 0.5f;
        float t = (System.nanoTime() / 1_000_000L % 100_000L) / 1000f;
        int y = 0;
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i);
            float f = shownFraction(n, preview);
            Text text = HudStyle.text(n);
            int tw = Draw.width(font, text, SCALE);
            int rowW = tw + 9;
            int slideOff = Math.round((1f - f) * (rowW + 4));
            int x = right ? size.width() - rowW + slideOff : -slideOff;
            int rowH = Math.round(ROW_H * f);
            int color = colourFor(i, names.size(), t);

            if (background.isOn()) {
                Draw.rect(ctx, x, y, x + rowW, y + rowH, Colors.fade(0x900E0F14, f));
            }
            int barX = right ? x + rowW - 2 : x;
            Draw.rect(ctx, barX, y, barX + 2, y + rowH, Colors.fade(color, f));
            int tx = right ? x + 3 : x + 5;
            Draw.text(ctx, font, text, tx, y + (rowH - Draw.height(font, SCALE)) / 2 + 1, Colors.fade(color, f), SCALE);
            y += rowH;
        }
    }

    private int colourFor(int index, int count, float t) {
        return switch (colour.get()) {
            case STATIC -> HudStyle.accent();
            case RAINBOW -> Colors.hsv((t * 0.15f + index * 0.06f) % 1f, 0.55f, 1f);
            case WAVE -> {
                float wave = 0.5f + 0.5f * (float) Math.sin(t * 2.2f - index * 0.45f);
                yield Colors.lerp(HudStyle.accent(), 0xFF35E0D0, wave);
            }
        };
    }
}
