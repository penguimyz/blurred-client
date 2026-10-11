package dev.blurredclient.mod.ui.kit.widget;

import dev.blurredclient.mod.ui.kit.KitComponent;
import dev.blurredclient.mod.ui.kit.Skins;
import dev.blurredclient.mod.ui.kit.Ui;
import dev.blurredclient.mod.ui.kit.Widgets;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Text. Wraps to its width when {@link #wrap()} is set, otherwise one line
 * cut with an ellipsis.
 */
public class Label extends KitComponent {
    /** Which of the skin's text colours to use, resolved at draw time. */
    public enum Tone { PRIMARY, DIM, FAINT, ACCENT }

    private final Supplier<String> text;
    private final Tone tone;
    private boolean wrap;
    private boolean centered;

    public Label(String text, Tone tone) {
        this(() -> text, tone);
    }

    public Label(Supplier<String> text, Tone tone) {
        this.text = text;
        this.tone = tone;
    }

    public Label wrap() {
        this.wrap = true;
        return this;
    }

    public Label centered() {
        this.centered = true;
        return this;
    }

    private static int lineHeight() {
        return Minecraft.getInstance().font.lineHeight + 2;
    }

    @Override
    public int preferredHeight(int width) {
        if (!wrap) {
            return lineHeight();
        }
        var font = Minecraft.getInstance().font;
        return Math.max(1, Widgets.wrap(font, Skins.current(), text.get(), width).size()) * lineHeight();
    }

    @Override
    protected boolean hitsSelf() {
        // Text isn't interactive; let clicks and tooltips reach what's behind.
        return tooltip() != null;
    }

    @Override
    protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
        List<String> lines = wrap
                ? Widgets.wrap(ui.font, ui.skin, text.get(), w)
                : List.of(Widgets.ellipsize(ui.font, ui.skin, text.get(), w));
        int color = switch (tone) {
            case PRIMARY -> ui.skin.textPrimary();
            case DIM -> ui.skin.textDim();
            case FAINT -> ui.skin.textFaint();
            case ACCENT -> ui.skin.accent();
        };
        int ly = y + 1;
        for (String line : lines) {
            int lx = centered ? x + (w - ui.skin.width(ui.font, line)) / 2 : x;
            ui.skin.drawText(ctx, ui.font, line, lx, ly, color);
            ly += lineHeight();
        }
    }
}
