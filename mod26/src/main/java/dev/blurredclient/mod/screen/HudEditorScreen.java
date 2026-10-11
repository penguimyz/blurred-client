package dev.blurredclient.mod.screen;

import dev.blurredclient.mod.config.BlurredConfig;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.module.hud.HudModule;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import dev.blurredclient.mod.ui.kit.KitComponent;
import dev.blurredclient.mod.ui.kit.KitScreen;
import dev.blurredclient.mod.ui.kit.Ui;
import dev.blurredclient.mod.ui.kit.widget.Button;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Drag the HUD around.
 *
 * <p>Every enabled HUD module is drawn at its real position and size with
 * placeholder content at its widest ("999 FPS"), so you lay things out at the
 * size they'll reach in play. Drag to move — edges snap to the screen edges,
 * the centre lines and other modules, with guide lines while snapped; hold
 * Alt to place freely. Scroll over a module to resize it, right-click to
 * reset it, arrow keys nudge the selected one (Shift for 10px).
 *
 * <p>Uses Minecraft's GUI scale rather than the kit's own, because what's
 * being edited is the HUD, which is drawn in GUI pixels.
 */
public class HudEditorScreen extends KitScreen {
    private static final int SNAP = 4;

    private final Screen parent;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("Edit HUD layout"));
        this.parent = parent;
    }

    @Override
    protected boolean ownScale() {
        return false;
    }

    @Override
    protected void build(KitComponent root) {
        root.add(new Canvas()).setBounds(0, 0, uiWidth(), uiHeight());
        int bw = 70;
        int cx = uiWidth() / 2;
        root.add(new Button("Reset all", this::resetAll)).setBounds(cx - bw - 3, 6, bw, 16);
        root.add(new Button("Done", this::onClose).accent()).setBounds(cx + 3, 6, bw, 16);
    }

    private void resetAll() {
        for (HudModule m : Modules.hud()) {
            m.resetPlacement();
        }
        BlurredConfig.save();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (this.minecraft != null && this.minecraft.level != null) {
            // No blur: you're placing things over the game, so see the game.
            ctx.fill(0, 0, this.width, this.height, 0x28000000);
            return;
        }
        super.extractBackground(ctx, mouseX, mouseY, delta);
    }

    @Override
    protected void drawBackdrop(GuiGraphicsExtractor ctx, Ui ui) {
        // Nothing: the HUD itself is what's on show.
    }

    private int scriptTick;

    /** Dev check ({@code -PdevScreen=hud -PdevScript=1}): screenshot, quit. No OS input. */
    @Override
    public void tick() {
        super.tick();
        if (!"hud".equals(System.getProperty("blurred.devScreen")) || System.getProperty("blurred.devScript") == null) {
            return;
        }
        scriptTick++;
        if (scriptTick == 50) {
            dev.blurredclient.mod.module.hud.NotificationsModule.moduleToggled("Zoom", true);
        } else if (scriptTick == 60) {
            net.minecraft.client.Screenshot.grab(
                    this.minecraft.gameDirectory, this.minecraft.gameRenderer.mainRenderTarget(), msg -> {});
        } else if (scriptTick == 64) {
            this.minecraft.stop();
        }
    }

    @Override
    protected boolean kitKey(int key, int scancode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.kitKey(key, scancode, modifiers);
    }

    @Override
    public void onClose() {
        BlurredConfig.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    /** The editing surface: draws the HUD and handles dragging. */
    private final class Canvas extends KitComponent {
        private HudModule selected;
        private HudModule hoveredModule;
        private boolean dragging;
        private int grabX;
        private int grabY;
        private Integer guideX;
        private Integer guideY;

        private record Box(HudModule module, int x, int y, int w, int h) {
            boolean contains(double mx, double my) {
                return mx >= x && my >= y && mx < x + w && my < y + h;
            }
        }

        private List<Box> boxes() {
            Minecraft client = Minecraft.getInstance();
            List<Box> out = new ArrayList<>();
            for (HudModule m : Modules.hud()) {
                if (!m.isActive() || !m.drawsItself()) {
                    continue;
                }
                HudModule.Size size = m.measure(client, true);
                if (size == null) {
                    continue;
                }
                HudPlacement p = m.placement();
                int w = Math.round(size.width() * p.scale);
                int h = Math.round(size.height() * p.scale);
                out.add(new Box(m, p.left(this.w, w), p.top(this.h, h), w, h));
            }
            return out;
        }

        private Box boxOf(HudModule m) {
            for (Box b : boxes()) {
                if (b.module() == m) {
                    return b;
                }
            }
            return null;
        }

        private Box boxAt(double mx, double my) {
            List<Box> all = boxes();
            for (int i = all.size() - 1; i >= 0; i--) {
                if (all.get(i).contains(mx, my)) {
                    return all.get(i);
                }
            }
            return null;
        }

        @Override
        protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
            Minecraft client = Minecraft.getInstance();
            Box over = boxAt(ui.mouseX, ui.mouseY);
            hoveredModule = over == null ? null : over.module();

            // Where vanilla's hotbar sits, so nothing gets parked on top of it.
            int hx = this.w / 2 - 91;
            ctx.fill(hx, this.h - 22, hx + 182, this.h, 0x22FFFFFF);
            ui.skin.drawText(ctx, ui.font, "Hotbar", this.w / 2 - ui.skin.width(ui.font, "Hotbar") / 2,
                    this.h - 15, 0x60FFFFFF);

            // Centre lines, faint, for orientation.
            ctx.fill(this.w / 2, 0, this.w / 2 + 1, this.h, 0x14FFFFFF);
            ctx.fill(0, this.h / 2, this.w, this.h / 2 + 1, 0x14FFFFFF);

            for (Box b : boxes()) {
                HudPlacement p = b.module().placement();
                Draw.scaled(ctx, b.x(), b.y(), p.scale, () -> b.module().render(ctx, client, true));

                boolean hot = b.module() == hoveredModule || b.module() == selected;
                int edge = hot ? ui.skin.accent() : 0x50FFFFFF;
                Draw.roundedOutline(ctx, b.x() - 2, b.y() - 2, b.w() + 4, b.h() + 4, 3, edge);
                if (hot) {
                    String label = b.module().name() + "  " + Math.round(p.scale * 100) + "%";
                    int ly = b.y() - 12 < 0 ? b.y() + b.h() + 4 : b.y() - 12;
                    int lw = ui.skin.width(ui.font, label) + 8;
                    Draw.roundedRect(ctx, b.x() - 2, ly - 1, lw, 11, 3, Colors.fade(ui.skin.accent(), 0.85f));
                    ui.skin.drawText(ctx, ui.font, label, b.x() + 2, ly + 1, 0xFFFFFFFF);
                }
            }

            if (dragging) {
                if (guideX != null) {
                    ctx.fill(guideX, 0, guideX + 1, this.h, ui.skin.accent());
                }
                if (guideY != null) {
                    ctx.fill(0, guideY, this.w, guideY + 1, ui.skin.accent());
                }
            }

            String hint = "Drag to move · Scroll to resize · Right-click to reset · Alt to place freely · Esc when done";
            ui.skin.drawText(ctx, ui.font, hint, this.w / 2 - ui.skin.width(ui.font, hint) / 2, 28, 0xB0FFFFFF);
        }

        @Override
        public boolean onMouseDown(double mx, double my, int button) {
            Box b = boxAt(mx, my);
            if (b == null) {
                selected = null;
                return false;
            }
            selected = b.module();
            if (button == 1) {
                b.module().resetPlacement();
                BlurredConfig.save();
                return true;
            }
            if (button != 0) {
                return false;
            }
            dragging = true;
            grabX = (int) Math.round(mx) - b.x();
            grabY = (int) Math.round(my) - b.y();
            return true;
        }

        @Override
        public void onDrag(double mx, double my, int button) {
            if (!dragging || selected == null) {
                return;
            }
            Box b = boxOf(selected);
            if (b == null) {
                return;
            }
            int left = (int) Math.round(mx) - grabX;
            int top = (int) Math.round(my) - grabY;
            guideX = null;
            guideY = null;
            if (!altDown()) {
                int[] sx = snap(left, b.w(), true, b);
                left = sx[0];
                guideX = sx[1] == Integer.MIN_VALUE ? null : sx[1];
                int[] sy = snap(top, b.h(), false, b);
                top = sy[0];
                guideY = sy[1] == Integer.MIN_VALUE ? null : sy[1];
            }
            place(selected, left, top, b.w(), b.h());
        }

        /**
         * Snap one axis. Returns {new position, guide line or MIN_VALUE}.
         * Candidates: screen edges, screen centre, and every other module's
         * edges (aligned to, or butted against).
         */
        private int[] snap(int pos, int size, boolean horizontal, Box self) {
            int screen = horizontal ? this.w : this.h;
            List<int[]> lines = new ArrayList<>(); // {target position for pos, guide}
            lines.add(new int[] {2, 2});
            lines.add(new int[] {screen - size - 2, screen - 2});
            lines.add(new int[] {(screen - size) / 2, screen / 2});
            for (Box o : boxes()) {
                if (o.module() == self.module()) {
                    continue;
                }
                int start = horizontal ? o.x() : o.y();
                int len = horizontal ? o.w() : o.h();
                lines.add(new int[] {start, start});                      // align starts
                lines.add(new int[] {start + len - size, start + len});   // align ends
                lines.add(new int[] {start + len + 3, start + len + 3});  // butt after
                lines.add(new int[] {start - size - 3, start - 3});       // butt before
            }
            int[] best = {pos, Integer.MIN_VALUE};
            int bestDist = SNAP + 1;
            for (int[] l : lines) {
                int d = Math.abs(l[0] - pos);
                if (d < bestDist) {
                    bestDist = d;
                    best = new int[] {l[0], l[1]};
                }
            }
            return best;
        }

        private void place(HudModule m, int left, int top, int w, int h) {
            m.setPlacement(HudPlacement.nearest(left, top, w, h, this.w, this.h, m.placement().scale));
        }

        @Override
        public void onMouseUp(double mx, double my, int button) {
            if (dragging) {
                dragging = false;
                BlurredConfig.save();
            }
        }

        @Override
        public boolean onScroll(double mx, double my, double amount) {
            Box b = boxAt(mx, my);
            if (b == null) {
                return false;
            }
            HudPlacement p = b.module().placement().copy();
            p.scale = Math.max(0.5f, Math.min(2f, Math.round((p.scale + (amount > 0 ? 0.05f : -0.05f)) * 20f) / 20f));
            b.module().setPlacement(p);
            BlurredConfig.save();
            return true;
        }

        @Override
        public boolean focusable() {
            return true;
        }

        @Override
        public boolean onKey(int key, int scancode, int modifiers) {
            if (selected == null) {
                return false;
            }
            int step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1;
            int dx = key == GLFW.GLFW_KEY_LEFT ? -step : key == GLFW.GLFW_KEY_RIGHT ? step : 0;
            int dy = key == GLFW.GLFW_KEY_UP ? -step : key == GLFW.GLFW_KEY_DOWN ? step : 0;
            if (dx == 0 && dy == 0) {
                return false;
            }
            Box b = boxOf(selected);
            if (b != null) {
                place(selected, b.x() + dx, b.y() + dy, b.w(), b.h());
                BlurredConfig.save();
            }
            return true;
        }

        private boolean altDown() {
            long handle = Minecraft.getInstance().getWindow().handle();
            return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
        }
    }
}
