package dev.blurredclient.mod.ui.kit.widget;

import dev.blurredclient.mod.setting.ColorSetting;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.kit.KitComponent;
import dev.blurredclient.mod.ui.kit.KitScreen;
import dev.blurredclient.mod.ui.kit.Skin;
import dev.blurredclient.mod.ui.kit.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A colour swatch with its hex code. Clicking opens a picker: a
 * saturation/brightness square, a hue bar, an alpha bar and a hex field.
 */
public class ColorButton extends KitComponent {
    private final ColorSetting setting;
    private final Runnable onChange;

    public ColorButton(ColorSetting setting, Runnable onChange) {
        this.setting = setting;
        this.onChange = onChange;
    }

    /** Light/dark checks under a translucent colour, so alpha is visible. */
    static void checker(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, 0xFF9AA7AD);
        int s = 3;
        for (int yy = 0; yy < h; yy += s) {
            for (int xx = (yy / s) % 2 * s; xx < w; xx += s * 2) {
                ctx.fill(x + xx, y + yy, x + Math.min(w, xx + s), y + Math.min(h, yy + s), 0xFFE4ECEF);
            }
        }
    }

    @Override
    protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
        float hov = hover.follow(ui.isHovered(this));
        ui.skin.field(ctx, x, y, w, h, hov, ui.screen.overlay() instanceof Picker p && p.owner() == this);
        int sw = h - 6;
        checker(ctx, x + 3, y + 3, sw, sw);
        ctx.fill(x + 3, y + 3, x + 3 + sw, y + 3 + sw, setting.argb());
        String hex = ColorSetting.toHex(setting.argb());
        ui.skin.drawText(ctx, ui.font, hex, x + sw + 8, y + (h - ui.font.lineHeight) / 2 + 1, ui.skin.textDim());
    }

    @Override
    public boolean onMouseDown(double mx, double my, int button) {
        return button == 0;
    }

    @Override
    public void onMouseUp(double mx, double my, int button) {
        KitScreen screen = KitScreen.active();
        if (screen == null || !contains(mx, my)) {
            return;
        }
        Sounds.click();
        Picker picker = new Picker();
        int pw = Picker.WIDTH;
        int ph = Picker.HEIGHT;
        int px = Math.min(x, screen.width - pw - 4);
        int py = y + h + 2 + ph <= screen.height - 4 ? y + h + 2 : Math.max(4, y - 2 - ph);
        picker.setBounds(px, py, pw, ph);
        screen.openOverlay(picker);
    }

    /** The popup. Keeps its own HSV so hue survives dragging to black or grey. */
    final class Picker extends KitComponent {
        static final int PAD = 6;
        static final int SQUARE = 100;
        static final int BAR = 8;
        static final int WIDTH = SQUARE + PAD * 2;
        static final int HEIGHT = PAD + 70 + 4 + BAR + 4 + BAR + 4 + 14 + PAD;

        private float hue;
        private float sat;
        private float val;
        private float alpha;
        private Region dragging = Region.NONE;
        private final TextField hexField;

        private enum Region { NONE, SQUARE, HUE, ALPHA }

        Picker() {
            float[] hsv = Colors.toHsv(setting.argb());
            hue = hsv[0];
            sat = hsv[1];
            val = hsv[2];
            alpha = Colors.alpha(setting.argb()) / 255f;
            hexField = add(new TextField(ColorSetting.toHex(setting.argb()), 9, this::typedHex));
        }

        ColorButton owner() {
            return ColorButton.this;
        }

        private int sqY() {
            return y + PAD;
        }

        private int hueY() {
            return sqY() + 70 + 4;
        }

        private int alphaY() {
            return hueY() + BAR + 4;
        }

        @Override
        protected void layout() {
            hexField.setBounds(x + PAD, alphaY() + BAR + 4, SQUARE, 14);
        }

        private void typedHex(String text) {
            Integer parsed = ColorSetting.parseHex(text.trim());
            if (parsed == null) {
                return;
            }
            float[] hsv = Colors.toHsv(parsed);
            hue = hsv[0];
            sat = hsv[1];
            val = hsv[2];
            alpha = Colors.alpha(parsed) / 255f;
            setting.set(parsed);
            onChange.run();
        }

        private void apply() {
            int rgb = Colors.hsv(hue, sat, val) & 0x00FFFFFF;
            int argb = (Math.round(alpha * 255f) << 24) | rgb;
            setting.set(argb);
            hexField.setText(ColorSetting.toHex(argb));
            onChange.run();
        }

        @Override
        protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
            ui.skin.panel(ctx, x, y, w, h, Skin.Surface.POPUP);
            int sx = x + PAD;

            // Saturation left-to-right, brightness top-to-bottom: one vertical
            // gradient per column from the pure colour down to black.
            for (int i = 0; i < SQUARE; i++) {
                int top = Colors.hsv(hue, i / (float) (SQUARE - 1), 1f);
                ctx.fillGradient(sx + i, sqY(), sx + i + 1, sqY() + 70, top, 0xFF000000);
            }
            int mx = sx + Math.round(sat * (SQUARE - 1));
            int my = sqY() + Math.round((1f - val) * 69);
            marker(ctx, mx, my);

            for (int i = 0; i < SQUARE; i++) {
                ctx.fill(sx + i, hueY(), sx + i + 1, hueY() + BAR, Colors.hsv(i / (float) SQUARE, 1f, 1f));
            }
            barMarker(ctx, sx + Math.round(hue * (SQUARE - 1)), hueY());

            checker(ctx, sx, alphaY(), SQUARE, BAR);
            int rgb = Colors.hsv(hue, sat, val) & 0x00FFFFFF;
            for (int i = 0; i < SQUARE; i++) {
                int a = Math.round(i / (float) (SQUARE - 1) * 255f);
                ctx.fill(sx + i, alphaY(), sx + i + 1, alphaY() + BAR, (a << 24) | rgb);
            }
            barMarker(ctx, sx + Math.round(alpha * (SQUARE - 1)), alphaY());
        }

        private static void marker(GuiGraphicsExtractor ctx, int cx, int cy) {
            ctx.fill(cx - 3, cy - 3, cx + 4, cy - 2, 0xFFFFFFFF);
            ctx.fill(cx - 3, cy + 3, cx + 4, cy + 4, 0xFFFFFFFF);
            ctx.fill(cx - 3, cy - 3, cx - 2, cy + 4, 0xFFFFFFFF);
            ctx.fill(cx + 3, cy - 3, cx + 4, cy + 4, 0xFFFFFFFF);
            ctx.fill(cx - 2, cy - 2, cx + 3, cy - 1, 0xFF000000);
        }

        private static void barMarker(GuiGraphicsExtractor ctx, int cx, int y) {
            ctx.fill(cx - 1, y - 2, cx + 2, y + BAR + 2, 0xFFFFFFFF);
            ctx.fill(cx, y - 1, cx + 1, y + BAR + 1, 0xFF000000);
        }

        private Region regionAt(double mx, double my) {
            int sx = x + PAD;
            if (mx < sx - 2 || mx > sx + SQUARE + 2) {
                return Region.NONE;
            }
            if (my >= sqY() && my < sqY() + 70) {
                return Region.SQUARE;
            }
            if (my >= hueY() - 2 && my < hueY() + BAR + 2) {
                return Region.HUE;
            }
            if (my >= alphaY() - 2 && my < alphaY() + BAR + 2) {
                return Region.ALPHA;
            }
            return Region.NONE;
        }

        @Override
        public boolean onMouseDown(double mx, double my, int button) {
            if (button != 0) {
                return false;
            }
            dragging = regionAt(mx, my);
            if (dragging == Region.NONE) {
                // Clicked the panel's empty space: swallow it, so the popup
                // doesn't close and nothing behind it reacts.
                return true;
            }
            onDrag(mx, my, button);
            return true;
        }

        @Override
        public void onDrag(double mx, double my, int button) {
            float fx = clamp01((float) (mx - (x + PAD)) / (SQUARE - 1));
            switch (dragging) {
                case SQUARE -> {
                    sat = fx;
                    val = 1f - clamp01((float) (my - sqY()) / 69f);
                }
                case HUE -> hue = Math.min(fx, 0.999f);
                case ALPHA -> alpha = fx;
                case NONE -> {
                    return;
                }
            }
            apply();
        }

        @Override
        public void onMouseUp(double mx, double my, int button) {
            dragging = Region.NONE;
        }

        private static float clamp01(float f) {
            return Math.max(0f, Math.min(1f, f));
        }
    }
}
