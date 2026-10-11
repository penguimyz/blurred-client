package dev.blurredclient.mod.ui.kit.widget;

import dev.blurredclient.mod.ui.kit.Component;
import dev.blurredclient.mod.ui.kit.Skin;
import dev.blurredclient.mod.ui.kit.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * One setting as a card: its name, a sentence saying what it does, and the
 * control on the right. The sentence is the part that matters — a label like
 * "Island" tells you nothing on its own — so it gets room to wrap.
 */
public class Row extends Component {
    private static final int PAD = 6;
    private static final int CONTROL_H = 14;

    private final Label title;
    private final Label hint;
    private final Component control;
    private final int controlW;

    public Row(String title, String hint, Component control, int controlW) {
        this.title = add(new Label(title, Label.Tone.PRIMARY));
        this.hint = add(new Label(hint == null ? "" : hint, Label.Tone.FAINT).wrap());
        this.hint.visible = hint != null && !hint.isEmpty();
        this.control = add(control);
        this.controlW = controlW;
    }

    private int textW(int width) {
        return width - controlW - PAD * 3;
    }

    private static int lineH() {
        return MinecraftClient.getInstance().textRenderer.fontHeight + 2;
    }

    @Override
    public int preferredHeight(int width) {
        int text = lineH() + (hint.visible ? hint.preferredHeight(textW(width)) + 1 : 0);
        return Math.max(CONTROL_H, text) + PAD * 2;
    }

    @Override
    protected void layout() {
        int tw = textW(w);
        title.setBounds(x + PAD, y + PAD, tw, lineH());
        if (hint.visible) {
            hint.setBounds(x + PAD, y + PAD + lineH() + 1, tw, hint.preferredHeight(tw));
        }
        control.setBounds(x + w - PAD - controlW, y + (h - CONTROL_H) / 2, controlW, CONTROL_H);
    }

    @Override
    protected void draw(DrawContext ctx, Ui ui) {
        ui.skin.panel(ctx, x, y, w, h, Skin.Surface.CARD);
    }
}
