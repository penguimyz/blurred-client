package dev.blurredclient.mod.screen.clickgui;

import dev.blurredclient.mod.ui.Draw;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.setting.Setting;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.kit.Anim;
import dev.blurredclient.mod.ui.kit.Component;
import dev.blurredclient.mod.ui.kit.Ui;
import dev.blurredclient.mod.ui.kit.Widgets;
import dev.blurredclient.mod.ui.kit.widget.Column;
import net.minecraft.client.gui.DrawContext;

/**
 * One module in a category panel: its name, lit in the accent colour when
 * on, with its settings folding out underneath.
 *
 * <p>Left-click toggles, right-click expands — the convention every click GUI
 * uses, so it's what hands already do. Modules that can't be switched off
 * (Crew sharing, Appearance) expand on either button instead.
 */
final class ModuleEntry extends Component {
    static final int ROW = 14;

    final Module module;
    private final Column settings = new Column(2, 0);
    private final Anim on;
    private final Anim open = new Anim(0f, 16f);
    private boolean expanded;

    ModuleEntry(Module module, Runnable save) {
        this.module = module;
        this.on = new Anim(module.isEnabled() ? 1f : 0f, 12f);
        add(settings);
        for (Setting<?> s : module.settings()) {
            settings.add(SettingLines.of(s, save));
        }
        tooltip(module.isAvailable() ? module.description() : module.unavailableReason());
    }

    private boolean hasSettings() {
        return !module.settings().isEmpty();
    }

    private void syncVisibility() {
        for (Component c : settings.children()) {
            if (c instanceof SettingLines.Line line) {
                line.visible = line.setting.isVisible();
            }
        }
        settings.visible = expanded;
    }

    @Override
    public int preferredHeight(int width) {
        syncVisibility();
        return ROW + (expanded ? settings.preferredHeight(width - 10) + 4 : 0);
    }

    @Override
    protected void layout() {
        syncVisibility();
        if (expanded) {
            settings.setBounds(x + 6, y + ROW + 1, w - 10, settings.preferredHeight(w - 10));
        }
    }

    @Override
    public Component hit(double mx, double my) {
        if (!visible || !contains(mx, my)) {
            return null;
        }
        if (my < y + ROW) {
            return this;
        }
        Component inside = settings.hit(mx, my);
        return inside != null ? inside : this;
    }

    @Override
    protected void draw(DrawContext ctx, Ui ui) {
        boolean headerHovered = ui.screen.hovered() == this && ui.mouseY < y + ROW;
        float hov = hover.follow(headerHovered);
        float lit = on.follow(module.isEnabled());
        float opened = open.follow(expanded);

        ui.skin.row(ctx, x, y, w, ROW, hov, lit);
        if (opened > 0.01f) {
            // A thin accent rule down the left of the open settings block.
            Draw.rect(ctx, x + 1, y + ROW, x + 2, y + h - 2, Colors.fade(ui.skin.accent(), opened));
        }

        int base = module.isAvailable() ? ui.skin.textDim() : ui.skin.textFaint();
        int color = Colors.lerp(Colors.lerp(base, ui.skin.textPrimary(), hov), ui.skin.rowOnColor(), lit);
        String name = Widgets.ellipsize(ui.font, ui.skin, module.name(), w - 18);
        int tx = x + (w - ui.skin.boldWidth(ui.font, name)) / 2;
        ui.skin.drawBold(ctx, ui.font, name, tx, y + (ROW - ui.font.fontHeight) / 2 + 1, color);

        if (hasSettings()) {
            dots(ctx, x + w - 7, y + ROW / 2, Colors.fade(ui.skin.textFaint(), 0.6f + 0.4f * Math.max(hov, opened)));
        }
    }

    /** A vertical ellipsis: "this has settings". */
    private static void dots(DrawContext ctx, int cx, int cy, int color) {
        for (int i = -1; i <= 1; i++) {
            Draw.rect(ctx, cx, cy + i * 3 - 1, cx + 1, cy + i * 3, color);
        }
    }

    @Override
    public boolean onMouseDown(double mx, double my, int button) {
        return my < y + ROW && (button == 0 || button == 1);
    }

    @Override
    public void onMouseUp(double mx, double my, int button) {
        if (!contains(mx, my) || my >= y + ROW) {
            return;
        }
        boolean expandClick = button == 1 || !module.toggleable();
        if (expandClick) {
            if (hasSettings()) {
                expanded = !expanded;
            }
            return;
        }
        module.toggle();
        ClickGuiScreen.onModuleToggled(module);
    }
}
