package dev.blurredclient.mod.screen.clickgui;

import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.ColorSetting;
import dev.blurredclient.mod.setting.KeybindSetting;
import dev.blurredclient.mod.setting.ModeSetting;
import dev.blurredclient.mod.setting.Setting;
import dev.blurredclient.mod.setting.SliderSetting;
import dev.blurredclient.mod.setting.TextSetting;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import dev.blurredclient.mod.ui.kit.Anim;
import dev.blurredclient.mod.ui.kit.KitComponent;
import dev.blurredclient.mod.ui.kit.KitScreen;
import dev.blurredclient.mod.ui.kit.Ui;
import dev.blurredclient.mod.ui.kit.Widgets;
import dev.blurredclient.mod.ui.kit.widget.ColorButton;
import dev.blurredclient.mod.ui.kit.widget.KeybindButton;
import dev.blurredclient.mod.ui.kit.widget.TextField;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Compact one-line controls for a module's settings, as they appear expanded
 * under a module in the click GUI. Narrower and denser than the kit's
 * {@code Row}: a panel is ~110px wide, so there's no room for a sentence of
 * explanation inline — that goes in the tooltip.
 */
final class SettingLines {
    private SettingLines() {}

    /** The control for {@code setting}. {@code save} runs after every change. */
    static Line of(Setting<?> setting, Runnable save) {
        if (setting instanceof BoolSetting b) {
            return new BoolLine(b, save);
        }
        if (setting instanceof SliderSetting s) {
            return new SliderLine(s, save);
        }
        if (setting instanceof ModeSetting<?> m) {
            return new ModeLine<>(m, save);
        }
        if (setting instanceof ColorSetting c) {
            return new Labelled(c, new ColorButton(c, save), 62);
        }
        if (setting instanceof KeybindSetting k) {
            return new Labelled(k, new KeybindButton(k, save), 46);
        }
        if (setting instanceof dev.blurredclient.mod.setting.PixelGridSetting g) {
            return new PainterLine(g, save);
        }
        if (setting instanceof TextSetting t) {
            return new Labelled(t, new TextField(t.get(), t.maxLength(), v -> {
                t.set(v);
                save.run();
            }), 56);
        }
        throw new IllegalArgumentException("no control for " + setting.getClass().getSimpleName());
    }

    /** Base: remembers its setting so the module row can hide it when it's not applicable. */
    abstract static class Line extends KitComponent {
        final Setting<?> setting;

        Line(Setting<?> setting, int height) {
            this.setting = setting;
            this.h = height;
            tooltip(setting.hint());
        }

        @Override
        public int preferredHeight(int width) {
            return h;
        }

        static int textY(Ui ui, int y, int h) {
            return y + (h - ui.font.lineHeight) / 2 + 1;
        }
    }

    /**
     * Paint pixels: left-drag paints, right-drag erases. The middle is
     * marked so you can line things up on it; the cells under the cursor
     * light up.
     */
    static final class PainterLine extends Line {
        private final dev.blurredclient.mod.setting.PixelGridSetting grid;
        private final Runnable save;
        private boolean painting;
        private boolean paintOn;

        PainterLine(dev.blurredclient.mod.setting.PixelGridSetting grid, Runnable save) {
            super(grid, 92);
            this.grid = grid;
            this.save = save;
        }

        private int cell() {
            return Math.max(3, Math.min(6, (w - 4) / grid.size()));
        }

        private int left() {
            return x + (w - cell() * grid.size()) / 2;
        }

        private int top() {
            return y + 11;
        }

        @Override
        protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
            ui.skin.drawText(ctx, ui.font, Widgets.ellipsize(ui.font, ui.skin, grid.label(), w - 2), x + 1, y + 1,
                    ui.skin.textDim());
            int n = grid.size();
            int c = cell();
            int lx = left();
            int ty = top();
            Draw.rect(ctx, lx - 1, ty - 1, lx + n * c + 1, ty + n * c + 1, 0x80000000);
            int mid = n / 2;
            int hx = ui.isHovered(this) ? (int) Math.floor((ui.mouseX - lx) / (double) c) : -1;
            int hy = ui.isHovered(this) ? (int) Math.floor((ui.mouseY - ty) / (double) c) : -1;
            for (int gy = 0; gy < n; gy++) {
                for (int gx = 0; gx < n; gx++) {
                    int px = lx + gx * c;
                    int py = ty + gy * c;
                    int col;
                    if (grid.on(gx, gy)) {
                        col = 0xFFFFFFFF;
                    } else if (gx == mid || gy == mid) {
                        col = 0x30FFFFFF;
                    } else {
                        col = (gx + gy) % 2 == 0 ? 0x18FFFFFF : 0x10FFFFFF;
                    }
                    Draw.rect(ctx, px, py, px + c - (c > 3 ? 1 : 0), py + c - (c > 3 ? 1 : 0), col);
                    if (gx == hx && gy == hy) {
                        Draw.rect(ctx, px, py, px + c, py + c, Colors.fade(ui.skin.accent(), 0.6f));
                    }
                }
            }
        }

        private void paintAt(double mx, double my) {
            int c = cell();
            int gx = (int) Math.floor((mx - left()) / c);
            int gy = (int) Math.floor((my - top()) / c);
            if (gx >= 0 && gy >= 0 && gx < grid.size() && gy < grid.size() && grid.on(gx, gy) != paintOn) {
                grid.paint(gx, gy, paintOn);
                save.run();
            }
        }

        @Override
        public boolean onMouseDown(double mx, double my, int button) {
            if (button != 0 && button != 1) {
                return false;
            }
            painting = true;
            paintOn = button == 0;
            paintAt(mx, my);
            return true;
        }

        @Override
        public void onDrag(double mx, double my, int button) {
            if (painting) {
                paintAt(mx, my);
            }
        }

        @Override
        public void onMouseUp(double mx, double my, int button) {
            painting = false;
        }
    }

    /** Small switch, then the label. The whole line is the click target. */
    static final class BoolLine extends Line {
        private final BoolSetting setting;
        private final Runnable save;
        private final Anim on;

        BoolLine(BoolSetting setting, Runnable save) {
            super(setting, 13);
            this.setting = setting;
            this.save = save;
            this.on = new Anim(setting.isOn() ? 1f : 0f, 14f);
        }

        @Override
        protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
            float hov = hover.follow(ui.isHovered(this));
            ui.skin.toggle(ctx, x + 1, y + (h - 8) / 2, 15, 8, on.follow(setting.isOn()), hov, true);
            String label = Widgets.ellipsize(ui.font, ui.skin, setting.label(), w - 22);
            ui.skin.drawText(ctx, ui.font, label, x + 21, textY(ui, y, h), setting.isOn() ? ui.skin.textPrimary() : ui.skin.textDim());
        }

        @Override
        public boolean onMouseDown(double mx, double my, int button) {
            return button == 0;
        }

        @Override
        public void onMouseUp(double mx, double my, int button) {
            if (contains(mx, my)) {
                setting.toggle();
                save.run();
            }
        }
    }

    /** Label and value on one line, the track beneath. */
    static final class SliderLine extends Line {
        private static final int TRACK_H = 8;
        private final SliderSetting setting;
        private final Runnable save;

        SliderLine(SliderSetting setting, Runnable save) {
            super(setting, 22);
            this.setting = setting;
            this.save = save;
        }

        @Override
        protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
            float hov = hover.follow(ui.isHovered(this));
            int ty = y + 1;
            String value = format(setting) + setting.suffix();
            int vw = ui.skin.width(ui.font, value);
            ui.skin.drawText(ctx, ui.font, Widgets.ellipsize(ui.font, ui.skin, setting.label(), w - vw - 6),
                    x + 1, ty, ui.skin.textDim());
            ui.skin.drawText(ctx, ui.font, value, x + w - vw - 1, ty, ui.skin.textPrimary());
            ui.skin.slider(ctx, x + 1, y + h - TRACK_H - 1, w - 2, TRACK_H, (float) setting.fraction(), hov,
                    ui.isPressed(this));
        }

        static String format(SliderSetting s) {
            double v = s.get();
            if (s.step() >= 1 && v == Math.rint(v)) {
                return Long.toString(Math.round(v));
            }
            int decimals = Math.max(0, Math.min(3, (int) Math.ceil(-Math.log10(s.step()))));
            return String.format("%." + decimals + "f", v);
        }

        private void setFrom(double mx) {
            double before = setting.get();
            setting.setFraction((mx - x - 5) / Math.max(1, w - 10));
            if (setting.get() != before) {
                save.run();
            }
        }

        @Override
        public boolean onMouseDown(double mx, double my, int button) {
            if (button != 0) {
                return false;
            }
            setFrom(mx);
            return true;
        }

        @Override
        public void onDrag(double mx, double my, int button) {
            setFrom(mx);
        }

        @Override
        public boolean onScroll(double mx, double my, double amount) {
            int mult = KitScreen.shiftDown() ? 10 : 1;
            setting.set(setting.get() + Math.signum(amount) * mult * setting.step());
            save.run();
            return true;
        }
    }

    /**
     * A filled box reading "Label · Value". Left-click steps forward through
     * the modes, right-click steps back — faster than a dropdown when there
     * are only two or three choices, which is nearly always.
     */
    static final class ModeLine<E extends Enum<E>> extends Line {
        private final ModeSetting<E> setting;
        private final Runnable save;

        ModeLine(ModeSetting<E> setting, Runnable save) {
            super(setting, 14);
            this.setting = setting;
            this.save = save;
        }

        @Override
        protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
            float hov = hover.follow(ui.isHovered(this));
            int accent = ui.skin.accent();
            Draw.roundedRect(ctx, x, y + 1, w, h - 2, 2, Colors.lerp(Colors.fade(accent, 0.55f), Colors.fade(accent, 0.8f), hov));
            String text = setting.label() + " • " + setting.labelOf(setting.get());
            text = Widgets.ellipsize(ui.font, ui.skin, text, w - 6);
            int tx = x + (w - ui.skin.width(ui.font, text)) / 2;
            ui.skin.drawText(ctx, ui.font, text, tx, textY(ui, y, h), 0xFFFFFFFF);
        }

        @Override
        public boolean onMouseDown(double mx, double my, int button) {
            return button == 0 || button == 1;
        }

        @Override
        public void onMouseUp(double mx, double my, int button) {
            if (!contains(mx, my)) {
                return;
            }
            if (button == 1) {
                setting.cycleBack();
            } else {
                setting.cycle();
            }
            save.run();
        }
    }

    /** Label on the left, a kit widget on the right. */
    static final class Labelled extends Line {
        private final KitComponent control;
        private final int controlW;

        Labelled(Setting<?> setting, KitComponent control, int controlW) {
            super(setting, 15);
            this.control = add(control);
            this.controlW = controlW;
        }

        @Override
        protected void layout() {
            control.setBounds(x + w - controlW, y + 1, controlW, h - 2);
        }

        @Override
        protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
            String label = Widgets.ellipsize(ui.font, ui.skin, setting.label(), w - controlW - 4);
            ui.skin.drawText(ctx, ui.font, label, x + 1, textY(ui, y, h), ui.skin.textDim());
        }
    }
}
