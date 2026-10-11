package dev.blurredclient.mod.screen.clickgui;

import dev.blurredclient.mod.ui.Draw;
import dev.blurredclient.mod.config.ConfigV2;
import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.kit.Anim;
import dev.blurredclient.mod.ui.kit.Component;
import dev.blurredclient.mod.ui.kit.Icons;
import dev.blurredclient.mod.ui.kit.Skin;
import dev.blurredclient.mod.ui.kit.Ui;
import dev.blurredclient.mod.ui.kit.widget.Column;
import dev.blurredclient.mod.ui.kit.widget.ScrollView;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A floating panel holding one category's modules. Dragged by its header,
 * folded up with the dash on the right (or a right-click on the header).
 *
 * <p>Re-lays itself out every frame: expanding a module or a setting that
 * shows another setting changes the height, and recomputing a few dozen
 * rectangles is cheaper than tracking which change invalidates what.
 */
final class CategoryPanel extends Component {
    static final int WIDTH = 104;
    static final int HEADER = 17;
    private static final int MAX_BODY = 260;

    final Category category;
    final ConfigV2.PanelState state;
    private final Column list = new Column(0, 0);
    private final ScrollView scroll;
    private final List<ModuleEntry> entries = new ArrayList<>();
    private final Anim fold;
    private final Runnable onMoved;

    private boolean dragging;
    private double grabX;
    private double grabY;

    CategoryPanel(Category category, List<Module> modules, ConfigV2.PanelState state, Runnable save, Runnable onMoved) {
        this.category = category;
        this.state = state;
        this.onMoved = onMoved;
        this.fold = new Anim(state.collapsed ? 0f : 1f, 14f);
        for (Module m : modules) {
            entries.add(list.add(new ModuleEntry(m, save)));
        }
        this.scroll = add(new ScrollView(list));
    }

    /** The row for a module id, or null. */
    ModuleEntry entry(String moduleId) {
        for (ModuleEntry e : entries) {
            if (e.module.id().equals(moduleId)) {
                return e;
            }
        }
        return null;
    }

    /** Show only modules matching {@code query}; returns whether any are left. */
    boolean filter(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        boolean any = false;
        for (ModuleEntry e : entries) {
            e.visible = q.isEmpty() || e.module.name().toLowerCase(Locale.ROOT).contains(q)
                    || e.module.description().toLowerCase(Locale.ROOT).contains(q);
            any |= e.visible;
        }
        return any;
    }

    /** Height when fully open (or just the header when folded), for laying panels out. */
    public int fullHeight() {
        int full = Math.min(list.preferredHeight(WIDTH), MAX_BODY);
        return HEADER + (state.collapsed ? 0 : full + 3);
    }

    public ConfigV2.PanelState state() {
        return state;
    }

    private int bodyHeight() {
        int full = Math.min(list.preferredHeight(WIDTH), MAX_BODY);
        return Math.round(full * fold.follow(!state.collapsed));
    }

    @Override
    protected void layout() {
        int body = bodyHeight();
        scroll.visible = body > 0;
        scroll.setBounds(x, y + HEADER, w, body);
    }

    @Override
    public Component hit(double mx, double my) {
        if (!visible || !contains(mx, my)) {
            return null;
        }
        if (my < y + HEADER) {
            return this;
        }
        Component inside = scroll.hit(mx, my);
        return inside != null ? inside : this;
    }

    @Override
    protected void draw(DrawContext ctx, Ui ui) {
        int body = bodyHeight();
        this.h = HEADER + body + (body > 0 ? 3 : 0);
        layout();

        ui.skin.panel(ctx, x, y, w, h, Skin.Surface.WINDOW);
        // Accent rule under the header, fading out toward the right.
        for (int i = 0; i < w - 8; i += 2) {
            float f = 1f - i / (float) (w - 8);
            Draw.rect(ctx, x + 4 + i, y + HEADER - 1, x + 6 + i, y + HEADER, Colors.fade(ui.skin.accent(), 0.25f + 0.75f * f));
        }

        int ty = y + (HEADER - ui.font.fontHeight) / 2 + 1;
        Icons.draw(ctx, icon(category), x + 6, y + (HEADER - 9) / 2f, 9, ui.skin.accent());
        ui.skin.drawBold(ctx, ui.font, category.label(), x + 19, ty, ui.skin.textPrimary());

        // "on / switchable" — modules that are always on (Crew sharing,
        // Appearance) aren't counted, so a panel of them shows no count.
        int enabled = 0;
        int total = 0;
        for (ModuleEntry e : entries) {
            if (e.module.toggleable()) {
                total++;
                if (e.module.isEnabled()) {
                    enabled++;
                }
            }
        }
        if (total > 0) {
            String count = enabled + "/" + total;
            int cw = ui.skin.width(ui.font, count);
            ui.skin.drawText(ctx, ui.font, count, x + w - 16 - cw, ty, ui.skin.textFaint());
        }

        // Fold control: a dash, brighter on hover.
        boolean overFold = ui.screen.hovered() == this && ui.mouseX >= x + w - 13 && ui.mouseY < y + HEADER;
        int dash = overFold ? ui.skin.textPrimary() : ui.skin.textDim();
        Draw.rect(ctx, x + w - 11, y + HEADER / 2 - 1, x + w - 5, y + HEADER / 2, dash);
    }

    @Override
    public boolean onMouseDown(double mx, double my, int button) {
        if (my >= y + HEADER) {
            return false;
        }
        if (button == 1 || (button == 0 && mx >= x + w - 13)) {
            state.collapsed = !state.collapsed;
            onMoved.run();
            return true;
        }
        if (button != 0) {
            return false;
        }
        dragging = true;
        grabX = mx - x;
        grabY = my - y;
        return true;
    }

    @Override
    public void onDrag(double mx, double my, int button) {
        if (!dragging) {
            return;
        }
        int nx = (int) Math.round(mx - grabX);
        int ny = (int) Math.round(my - grabY);
        moveTo(nx, ny);
    }

    @Override
    public void onMouseUp(double mx, double my, int button) {
        if (dragging) {
            dragging = false;
            onMoved.run();
        }
    }

    static Icons.Icon icon(Category c) {
        return switch (c) {
            case HUD -> Icons.Icon.MONITOR;
            case VISUAL -> Icons.Icon.EYE;
            case EFFECTS -> Icons.Icon.STAR;
            case COMBAT -> Icons.Icon.PLUS;
            case PERFORMANCE -> Icons.Icon.GEAR;
            case MENUS -> Icons.Icon.LIST;
            case UTILITY -> Icons.Icon.SLIDERS;
            case CREW -> Icons.Icon.PEOPLE;
            case COSMETIC -> Icons.Icon.PALETTE;
        };
    }

    /** Move, keeping at least the header on screen. */
    void moveTo(int nx, int ny) {
        var screen = dev.blurredclient.mod.ui.kit.KitScreen.active();
        if (screen != null) {
            nx = Math.max(0, Math.min(screen.uiWidth() - w, nx));
            ny = Math.max(0, Math.min(screen.uiHeight() - HEADER, ny));
        }
        shift(nx - x, ny - y);
        state.x = nx;
        state.y = ny;
    }
}
