package dev.blurredclient.mod.ui.kit.widget;

import dev.blurredclient.mod.setting.ModeSetting;
import dev.blurredclient.mod.ui.kit.Anim;
import dev.blurredclient.mod.ui.kit.KitComponent;
import dev.blurredclient.mod.ui.kit.KitScreen;
import dev.blurredclient.mod.ui.kit.Skin;
import dev.blurredclient.mod.ui.kit.Ui;
import dev.blurredclient.mod.ui.kit.Widgets;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Pick one option of a {@link ModeSetting} from a list that drops down over
 * everything else. Opens upward when there isn't room below.
 */
public class Dropdown<E extends Enum<E>> extends KitComponent {
    private static final int ROW_H = 14;

    private final ModeSetting<E> setting;
    private final Runnable onChange;

    public Dropdown(ModeSetting<E> setting, Runnable onChange) {
        this.setting = setting;
        this.onChange = onChange;
    }

    private boolean isOpen(KitScreen screen) {
        return screen != null && screen.overlay() instanceof Dropdown<?>.Options o && o.owner() == this;
    }

    @Override
    protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
        boolean open = isOpen(ui.screen);
        float hov = hover.follow(open || ui.isHovered(this));
        ui.skin.field(ctx, x, y, w, h, hov, open);
        String label = Widgets.ellipsize(ui.font, ui.skin, setting.labelOf(setting.get()), w - 18);
        ui.skin.drawText(ctx, ui.font, label, x + 5, y + (h - ui.font.lineHeight) / 2 + 1, ui.skin.textPrimary());
        chevron(ctx, x + w - 10, y + h / 2 - 1, open, ui.skin.textDim());
    }

    /** A small filled triangle, drawn with fills so no font needs the glyph. */
    static void chevron(GuiGraphicsExtractor ctx, int cx, int cy, boolean up, int color) {
        for (int i = 0; i < 3; i++) {
            int row = up ? cy + 2 - i : cy + i;
            ctx.fill(cx - 2 + i, row, cx + 3 - i, row + 1, color);
        }
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
        if (isOpen(screen)) {
            screen.closeOverlay();
            return;
        }
        Options list = new Options();
        int listH = setting.options().length * ROW_H + 4;
        int below = y + h + 2;
        int ly = below + listH <= screen.height - 4 ? below : Math.max(4, y - 2 - listH);
        list.setBounds(x, ly, Math.max(w, 80), listH);
        screen.openOverlay(list);
    }

    /** The open list. Lives in the screen's overlay slot while shown. */
    final class Options extends KitComponent {
        private final Anim[] rowHover = new Anim[setting.options().length];

        Options() {
            for (int i = 0; i < rowHover.length; i++) {
                rowHover[i] = new Anim(0f, 18f);
            }
        }

        Dropdown<E> owner() {
            return Dropdown.this;
        }

        private int rowAt(double my) {
            int row = (int) Math.floor((my - y - 2) / ROW_H);
            return row >= 0 && row < rowHover.length ? row : -1;
        }

        @Override
        protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
            ui.skin.panel(ctx, x, y, w, h, Skin.Surface.POPUP);
            int hoverRow = ui.isHovered(this) ? rowAt(ui.mouseY) : -1;
            E[] options = setting.options();
            for (int i = 0; i < options.length; i++) {
                int ry = y + 2 + i * ROW_H;
                ui.skin.highlight(ctx, x + 2, ry, w - 4, ROW_H, rowHover[i].follow(i == hoverRow));
                boolean current = options[i] == setting.get();
                String label = Widgets.ellipsize(ui.font, ui.skin, setting.labelOf(options[i]), w - 16);
                ui.skin.drawText(ctx, ui.font, label, x + 7, ry + (ROW_H - ui.font.lineHeight) / 2 + 1,
                        current ? ui.skin.accent() : ui.skin.textPrimary());
            }
        }

        @Override
        public boolean onMouseDown(double mx, double my, int button) {
            return button == 0;
        }

        @Override
        public void onMouseUp(double mx, double my, int button) {
            int row = contains(mx, my) ? rowAt(my) : -1;
            if (row < 0) {
                return;
            }
            Sounds.click();
            E picked = setting.options()[row];
            if (picked != setting.get()) {
                setting.set(picked);
                onChange.run();
            }
            KitScreen screen = KitScreen.active();
            if (screen != null) {
                screen.closeOverlay();
            }
        }
    }
}
