package dev.blurredclient.mod.screen.clickgui;

import dev.blurredclient.mod.BlurredMod;
import dev.blurredclient.mod.config.BlurredConfig;
import dev.blurredclient.mod.config.ConfigV2;
import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.module.ModuleRegistry;
import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.ui.kit.KitComponent;
import dev.blurredclient.mod.ui.kit.Icons;
import dev.blurredclient.mod.ui.kit.KitScreen;
import dev.blurredclient.mod.ui.kit.widget.Button;
import dev.blurredclient.mod.ui.kit.widget.TextField;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The mod menu: a floating panel per category, a search box top centre and a
 * profile switch at the bottom. Right Shift opens and closes it.
 *
 * <p>Typing anywhere searches — there's no need to click the box first,
 * because the only text input on this screen that isn't a setting is search.
 */
public class ClickGuiScreen extends KitScreen {
    private static final int GAP = 8;

    private final Screen parent;
    private final List<CategoryPanel> panels = new ArrayList<>();
    private TextField search;
    private String query = "";

    public ClickGuiScreen(Screen parent) {
        super(Component.literal("Blurred"));
        this.parent = parent;
    }

    /** Hook for things that should react to a module being switched (toasts, later). */
    static void onModuleToggled(Module module) {
        dev.blurredclient.mod.module.hud.NotificationsModule.moduleToggled(module.name(), module.isEnabled());
        BlurredConfig.save();
    }

    /** True if any two panels cover each other (their full, unfolded size). */
    private boolean overlapping() {
        for (int i = 0; i < panels.size(); i++) {
            for (int j = i + 1; j < panels.size(); j++) {
                var a = panels.get(i).state();
                var b = panels.get(j).state();
                int ah = panels.get(i).fullHeight();
                int bh = panels.get(j).fullHeight();
                boolean x = a.x < b.x + CategoryPanel.WIDTH && b.x < a.x + CategoryPanel.WIDTH;
                boolean y = a.y < b.y + bh && b.y < a.y + ah;
                if (x && y) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Masonry layout: each panel drops into the column whose bottom is
     * highest, so tall panels never run into the ones below them.
     */
    private void arrange() {
        int cols = Math.max(1, (uiWidth() - GAP) / (CategoryPanel.WIDTH + GAP));
        int[] bottom = new int[cols];
        java.util.Arrays.fill(bottom, 30);
        for (CategoryPanel panel : panels) {
            int col = 0;
            for (int c = 1; c < cols; c++) {
                if (bottom[c] < bottom[col]) {
                    col = c;
                }
            }
            int x = GAP + col * (CategoryPanel.WIDTH + GAP);
            int y = bottom[col];
            panel.state().x = x;
            panel.state().y = y;
            panel.moveTo(x, y);
            bottom[col] = y + panel.fullHeight() + GAP;
        }
        BlurredConfig.save();
    }

    @Override
    protected void build(KitComponent root) {
        panels.clear();
        ConfigV2 cfg = Modules.REGISTRY.config();
        ModuleRegistry registry = Modules.REGISTRY;

        int defaultX = GAP;
        int defaultY = 30;
        for (Category category : Category.values()) {
            List<Module> modules = registry.inCategory(category);
            if (modules.isEmpty()) {
                continue;
            }
            // First open: lay panels out left to right, wrapping to a new row.
            if (defaultX + CategoryPanel.WIDTH > uiWidth() - GAP) {
                defaultX = GAP;
                defaultY += 150;
            }
            ConfigV2.PanelState state = cfg.clickGui.computeIfAbsent(category.name(),
                    k -> new ConfigV2.PanelState(0, 0, false));
            if (state.x == 0 && state.y == 0) {
                state.x = defaultX;
                state.y = defaultY;
            }
            defaultX += CategoryPanel.WIDTH + GAP;

            CategoryPanel panel = root.add(new CategoryPanel(category, modules, state,
                    BlurredConfig::save, BlurredConfig::save));
            panel.setBounds(state.x, state.y, CategoryPanel.WIDTH, CategoryPanel.HEADER);
            // Pull back on screen if the window got smaller since last time.
            panel.moveTo(state.x, state.y);
            panels.add(panel);
        }

        if (overlapping()) {
            arrange();
        }

        int sw = Math.min(200, uiWidth() - 20);
        search = root.add(new TextField(query, 40, q -> {
            query = q;
            applyFilter();
        }).placeholder("Search modules…  (just start typing)").padLeft(18));
        search.setBounds((uiWidth() - sw) / 2, 7, sw, 17);
        root.add(new IconOverlay(Icons.Icon.SEARCH)).setBounds(search.x + 5, search.y + 4, 9, 9);

        int barY = uiHeight() - 24;
        // Profiles: switch, make a new one, rename or delete, in a menu above the button.
        int px = uiWidth() / 2 - 116;
        root.add(new Button(() -> "Profile: " + registry.activeProfile(), () -> {
            ProfileMenu menu = new ProfileMenu(() -> {});
            int mh = menu.preferredHeight(ProfileMenu.WIDTH);
            menu.setBounds(px, barY - mh - 4, ProfileMenu.WIDTH, mh);
            openOverlay(menu);
        })).setBounds(px, barY, 112, 16);
        root.add(new Button("Edit HUD layout", () -> Minecraft.getInstance().gui
                .setScreen(new dev.blurredclient.mod.screen.HudEditorScreen(this))).accent())
                .setBounds(uiWidth() / 2 + 4, barY, 112, 16);

        applyFilter();
    }

    /** Draws an icon over whatever is beneath it; clicks pass through. */
    private static final class IconOverlay extends KitComponent {
        private final Icons.Icon icon;

        IconOverlay(Icons.Icon icon) {
            this.icon = icon;
        }

        @Override
        protected boolean hitsSelf() {
            return false;
        }

        @Override
        protected void draw(net.minecraft.client.gui.GuiGraphicsExtractor ctx, dev.blurredclient.mod.ui.kit.Ui ui) {
            Icons.draw(ctx, icon, x, y, w, ui.skin.textFaint());
        }
    }

    private void applyFilter() {
        for (CategoryPanel p : panels) {
            p.visible = p.filter(query);
        }
    }

    @Override
    protected boolean kitMouseDown(double mx, double my, int button) {
        // Clicking a panel raises it above the others.
        for (int i = panels.size() - 1; i >= 0; i--) {
            CategoryPanel p = panels.get(i);
            if (p.visible && p.contains(mx, my)) {
                if (p.parent() != null) {
                    p.parent().bringToFront(p);
                }
                panels.remove(i);
                panels.add(p);
                break;
            }
        }
        return super.kitMouseDown(mx, my, button);
    }

    @Override
    protected boolean kitKey(int key, int scancode, int modifiers) {
        if (super.kitKey(key, scancode, modifiers)) {
            return true;
        }
        // The menu key closes it again, unless you're typing in a field.
        if (!(focused() instanceof TextField) && key == BlurredMod.menuKeyCode()) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    protected boolean kitChar(String typed) {
        if (focused() == null && search != null && !typed.isBlank()) {
            focus(search);
        }
        return super.kitChar(typed);
    }

    @Override
    public void onClose() {
        BlurredConfig.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    // ------------------------------------------------------------------
    // Self-driving check (dev only: -PdevScreen=clickgui -PdevScript=1)
    // ------------------------------------------------------------------

    private int scriptTick;

    /**
     * Exercises the menu through the same input path a real click takes and
     * saves framebuffer screenshots, then quits. Never touches the OS mouse,
     * keyboard or window focus.
     */
    @Override
    public void tick() {
        super.tick();
        if (System.getProperty("blurred.devScript") == null) {
            return;
        }
        scriptTick++;
        switch (scriptTick) {
            case 58 -> dev.blurredclient.mod.BlurredMod.LOGGER.info("[devcheck] click GUI fps={}", this.minecraft.getFps());
            case 60 -> shot();
            case 62 -> {
                clickEntry(Category.HUD, "info_bar", 1);
                clickEntry(Category.HUD, "coords", 0);
                clickEntry(Category.MENUS, "appearance", 0);
                clickEntry(Category.CREW, "crew", 0);
            }
            case 74 -> shot();
            case 76 -> {
                for (char c : "ping".toCharArray()) {
                    kitChar(String.valueOf(c));
                }
            }
            case 86 -> shot();
            case 88 -> {
                search.setText("");
                query = "";
                applyFilter();
                focus(null);
                CategoryPanel hud = panel(Category.HUD);
                double hx = hud.x + 30;
                double hy = hud.y + 6;
                kitMouseDown(hx, hy, 0);
                kitDrag(hx + 140, hy + 40, 0);
                kitMouseUp(hx + 140, hy + 40, 0);
            }
            case 98 -> shot();
            case 100 -> setStyle(dev.blurredclient.mod.module.feature.AppearanceModule.Style.GLASS);
            case 110 -> shot();
            case 112 -> setStyle(dev.blurredclient.mod.module.feature.AppearanceModule.Style.PIXEL);
            case 122 -> shot();
            case 124 -> setStyle(dev.blurredclient.mod.module.feature.AppearanceModule.Style.MODERN);
            case 128 -> this.minecraft.stop();
            default -> {}
        }
    }

    private CategoryPanel panel(Category category) {
        for (CategoryPanel p : panels) {
            if (p.category == category) {
                return p;
            }
        }
        throw new IllegalStateException("no panel for " + category);
    }

    private void clickEntry(Category category, String moduleId, int button) {
        ModuleEntry e = panel(category).entry(moduleId);
        double cx = e.x + e.w / 2.0;
        double cy = e.y + ModuleEntry.ROW / 2.0;
        kitMouseDown(cx, cy, button);
        kitMouseUp(cx, cy, button);
    }

    private void setStyle(dev.blurredclient.mod.module.feature.AppearanceModule.Style style) {
        Modules.APPEARANCE.styleSetting().set(style);
        BlurredConfig.save();
    }

    private void shot() {
        net.minecraft.client.Screenshot.grab(
                this.minecraft.gameDirectory, this.minecraft.gameRenderer.mainRenderTarget(), msg -> {});
    }
}
