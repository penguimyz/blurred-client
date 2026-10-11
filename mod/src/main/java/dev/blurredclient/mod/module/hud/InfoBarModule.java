package dev.blurredclient.mod.module.hud;

import dev.blurredclient.mod.hud.HudStyle;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.module.ModuleIds;
import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.setting.ModeSetting;
import dev.blurredclient.mod.ui.Draw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * The merged readout bar: every {@link TextHudModule} with "In info bar" on,
 * drawn as one object.
 *
 * <h2>The island</h2>
 *
 * Readouts used to be a vertical stack of separate panels down the top-left
 * corner — one box per number, which at three or four readouts took a
 * meaningful bite out of the screen and looked like a debug dump. The island
 * is a single pill with the values divided inside it: one object instead of
 * five, reading as part of the window chrome rather than as something covering
 * the game. A stack is still better with five or more readouts on, so it stays
 * a choice.
 */
public final class InfoBarModule extends HudModule {
    public enum Style {
        ISLAND, STACK;

        public String label() {
            return this == ISLAND ? "One bar" : "Stacked rows";
        }
    }

    private final ModeSetting<Style> style;

    public InfoBarModule() {
        super(ModuleIds.INFO_BAR, "Info bar",
                "The merged bar the readouts sit in. Turn readouts on and off on their own cards.",
                true, new HudPlacement(HudPlacement.Anchor.TOP_LEFT, 4, 4));
        this.style = add(new ModeSetting<>("style", "Layout",
                "One bar with dividers, or a separate row per readout.",
                Style.ISLAND, Style::label));
    }

    /** This frame's readouts from every active module that's in the bar. */
    private List<TextHudModule.Readout> readouts(MinecraftClient client, boolean preview) {
        List<TextHudModule.Readout> out = new ArrayList<>();
        for (Module m : Modules.REGISTRY.all()) {
            if (m instanceof TextHudModule t && t.isActive() && t.inBar()) {
                TextHudModule.Readout r = t.current(client, preview);
                if (r != null) {
                    out.add(r);
                }
            }
        }
        return out;
    }

    @Override
    public Size measure(MinecraftClient client, boolean preview) {
        List<TextHudModule.Readout> items = readouts(client, preview);
        if (items.isEmpty()) {
            return null;
        }
        var font = client.textRenderer;
        int lineH = Draw.height(font, HudStyle.TEXT_SCALE);
        int rowH = lineH + HudStyle.PAD_Y * 2;

        if (style.get() == Style.ISLAND) {
            int content = 0;
            for (int i = 0; i < items.size(); i++) {
                content += textWidth(client, items.get(i));
                if (i > 0) {
                    content += HudStyle.GAP * 2;
                }
            }
            return new Size(HudStyle.PAD_X * 2 + content, rowH);
        }

        int widest = 0;
        for (TextHudModule.Readout r : items) {
            widest = Math.max(widest, HudStyle.PAD_X * 2 + textWidth(client, r));
        }
        return new Size(widest, items.size() * rowH + (items.size() - 1) * HudStyle.ROW_GAP);
    }

    @Override
    public void render(DrawContext ctx, MinecraftClient client, boolean preview) {
        List<TextHudModule.Readout> items = readouts(client, preview);
        Size size = measure(client, preview);
        if (items.isEmpty() || size == null) {
            return;
        }
        if (style.get() == Style.ISLAND) {
            renderIsland(ctx, client, items, size);
        } else {
            renderStack(ctx, client, items, size);
        }
    }

    private void renderIsland(DrawContext ctx, MinecraftClient client,
                              List<TextHudModule.Readout> items, Size size) {
        var font = client.textRenderer;
        int w = size.width();
        int h = size.height();
        int lineH = Draw.height(font, HudStyle.TEXT_SCALE);

        HudStyle.island(ctx, 0, 0, w, h);

        int cursor = HudStyle.PAD_X;
        int textY = (h - lineH) / 2;
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                // Divider sits in the middle of the gap, inset vertically so it
                // reads as a separator rather than as a wall.
                int dx = cursor + HudStyle.GAP;
                Draw.rect(ctx, dx, 3, dx + 1, h - 3, 0x3A7DE2F0);
                cursor += HudStyle.GAP * 2;
            }
            TextHudModule.Readout item = items.get(i);
            Text text = HudStyle.text(item.text());
            Draw.text(ctx, font, text, cursor, textY, item.color(), HudStyle.TEXT_SCALE);
            cursor += Draw.width(font, text, HudStyle.TEXT_SCALE);
        }
    }

    /**
     * One panel per readout. Rows line up with the side the bar is anchored
     * to — flush right when it sits on the right edge — as v1's stack did.
     */
    private void renderStack(DrawContext ctx, MinecraftClient client,
                             List<TextHudModule.Readout> items, Size size) {
        var font = client.textRenderer;
        int lineH = Draw.height(font, HudStyle.TEXT_SCALE);
        int rowH = lineH + HudStyle.PAD_Y * 2;
        float align = placement().anchor.fx;

        int y = 0;
        for (TextHudModule.Readout item : items) {
            int rowW = HudStyle.PAD_X * 2 + textWidth(client, item);
            int x = Math.round(align * (size.width() - rowW));
            HudStyle.panel(ctx, x, y, rowW, rowH);
            Draw.text(ctx, font, HudStyle.text(item.text()), x + HudStyle.PAD_X, y + (rowH - lineH) / 2,
                    item.color(), HudStyle.TEXT_SCALE);
            y += rowH + HudStyle.ROW_GAP;
        }
    }

    private static int textWidth(MinecraftClient client, TextHudModule.Readout r) {
        return Draw.width(client.textRenderer, HudStyle.text(r.text()), HudStyle.TEXT_SCALE);
    }
}
