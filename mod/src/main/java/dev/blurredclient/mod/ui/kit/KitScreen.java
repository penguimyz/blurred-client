package dev.blurredclient.mod.ui.kit;

import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.ui.Draw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

//? if >=1.21.10 {
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
//?}

import java.util.List;

/**
 * Base for every Blurred screen built from the UI kit.
 *
 * <h2>Input</h2>
 *
 * This is the <b>only</b> place that knows Minecraft's input API changed:
 * 1.21.10 replaced {@code mouseClicked(double, double, int)} and friends with
 * {@code Click}, {@code KeyInput} and {@code CharInput} records (measured with
 * javap against every supported version). Both forms are overridden here,
 * behind Stonecutter guards, and turned into plain numbers before any widget
 * sees them. Widgets never branch on version.
 *
 * <h2>Scale</h2>
 *
 * Kit screens don't use Minecraft's GUI scale. At GUI scale 4 every GUI
 * pixel is four screen pixels, which makes menus enormous and turns every
 * curve into a staircase. Instead a kit screen lays itself out in its own
 * units — about two screen pixels each, times the player's "Menu size"
 * setting — and draws through one scale transform. Components never see the
 * difference: {@link #uiWidth()}/{@link #uiHeight()} are the screen size in
 * those units, and mouse positions arrive already converted.
 *
 * <p>Subclasses build their component tree in {@link #build}, which runs on
 * open and on every resize. State that should survive a resize belongs in the
 * screen's fields, not in the components.
 */
public abstract class KitScreen extends Screen {
    private static final long TOOLTIP_DELAY_NS = 450_000_000L;
    /** Screen pixels per UI unit at "Menu size" 1.0. */
    private static final float BASE_PIXELS = 2f;

    private Component root = new Container();
    private Component overlay;
    private Component hovered;
    private Component captured;
    private int capturedButton;
    private Component focused;
    private float scale = 1f;

    private Component tooltipOwner;
    private long tooltipSince;

    protected KitScreen(Text title) {
        super(title);
    }

    /** The open kit screen, if there is one. Lets widgets reach focus and overlays. */
    public static KitScreen active() {
        return MinecraftClient.getInstance().currentScreen instanceof KitScreen k ? k : null;
    }

    /**
     * Whether this screen uses the kit's own scale. False keeps Minecraft's
     * GUI scale — for the HUD editor, which has to line up exactly with the
     * HUD as it's really drawn.
     */
    protected boolean ownScale() {
        return true;
    }

    /** GUI pixels per UI unit for the current window and settings. */
    private float computeScale() {
        if (!ownScale()) {
            return 1f;
        }
        double guiScale = MinecraftClient.getInstance().getWindow().getScaleFactor();
        float wanted = BASE_PIXELS * Modules.APPEARANCE.menuSize();
        return (float) Math.max(0.25, wanted / Math.max(1.0, guiScale));
    }

    /** Screen width in UI units. Use this, not {@code width}, when laying out. */
    public int uiWidth() {
        return Math.round(this.width / scale);
    }

    /** Screen height in UI units. */
    public int uiHeight() {
        return Math.round(this.height / scale);
    }

    public float uiScale() {
        return scale;
    }

    @Override
    protected final void init() {
        scale = computeScale();
        overlay = null;
        captured = null;
        focused = null;
        root = new Container();
        root.setBounds(0, 0, uiWidth(), uiHeight());
        build(root);
    }

    /** Add this screen's components to {@code root}, which covers the whole screen. */
    protected abstract void build(Component root);

    public Skin skin() {
        return Skins.current();
    }

    /** Rebuild after something changed the layout (the skin, the menu size). */
    public void rebuild() {
        this.clearAndInit();
    }

    // ------------------------------------------------------------------
    // Focus, capture, overlays
    // ------------------------------------------------------------------

    public Component hovered() {
        return hovered;
    }

    public Component focused() {
        return focused;
    }

    public Component captured() {
        return captured;
    }

    public void focus(Component next) {
        if (next == focused) {
            return;
        }
        Component previous = focused;
        focused = next;
        if (previous != null) {
            previous.onFocusChanged(false);
        }
        if (next != null) {
            next.onFocusChanged(true);
        }
    }

    /**
     * Show a component above everything else (a dropdown list, the colour
     * picker). It gets input first; clicking anywhere outside it closes it.
     */
    public void openOverlay(Component c) {
        overlay = c;
    }

    public void closeOverlay() {
        if (overlay != null && isInside(focused, overlay)) {
            focus(null);
        }
        overlay = null;
    }

    public Component overlay() {
        return overlay;
    }

    private static boolean isInside(Component c, Component ancestor) {
        for (Component p = c; p != null; p = p.parent()) {
            if (p == ancestor) {
                return true;
            }
        }
        return false;
    }

    public static boolean shiftDown() {
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Background (blur over the world, or the menu backdrop) comes from
        // the framework. Calling renderBackground again by hand crashes the
        // game on versions with the blur shader, so it's never done here.
        super.render(ctx, mouseX, mouseY, delta);

        // The menu size can change while the screen is open (it's a setting
        // on this very menu); re-lay out when it does.
        if (Math.abs(computeScale() - scale) > 0.001f) {
            rebuild();
        }

        int mx = (int) Math.floor(mouseX / scale);
        int my = (int) Math.floor(mouseY / scale);
        hovered = overlay != null ? overlay.hit(mx, my) : root.hit(mx, my);
        Ui ui = new Ui(this, this.textRenderer, skin(), mx, my);

        Draw.scaled(ctx, 0, 0, scale, () -> {
            Draw.pushUiScale(scale);
            try {
                drawBackdrop(ctx, ui);
                root.render(ctx, ui);
                Ripples.render(ctx);
                if (overlay != null) {
                    overlay.render(ctx, ui);
                }
                drawTooltip(ctx, ui);
            } finally {
                Draw.popUiScale();
            }
        });
    }

    /**
     * Draw just the menu (no background or blur) into {@code ctx}, with the
     * mouse at {@code mouseX, mouseY}. For showing this screen somewhere other
     * than the screen itself: the 3D menu draws it onto a panel in the world.
     */
    public void renderContent(DrawContext ctx, int mouseX, int mouseY) {
        if (Math.abs(computeScale() - scale) > 0.001f) {
            rebuild();
        }
        int mx = (int) Math.floor(mouseX / scale);
        int my = (int) Math.floor(mouseY / scale);
        hovered = overlay != null ? overlay.hit(mx, my) : root.hit(mx, my);
        Ui ui = new Ui(this, this.textRenderer, skin(), mx, my);
        Draw.scaled(ctx, 0, 0, scale, () -> {
            Draw.pushUiScale(scale);
            try {
                root.render(ctx, ui);
                Ripples.render(ctx);
                if (overlay != null) {
                    overlay.render(ctx, ui);
                }
                drawTooltip(ctx, ui);
            } finally {
                Draw.popUiScale();
            }
        });
    }

    /** A full click (press and release) at screen coordinates, from outside the normal input path. */
    public boolean virtualClick(double x, double y, int button) {
        boolean took = kitMouseDown(x / scale, y / scale, button);
        kitMouseUp(x / scale, y / scale, button);
        return took;
    }

    /** A mouse-wheel step at screen coordinates, from outside the normal input path. */
    public boolean virtualScroll(double x, double y, double amount) {
        return kitScroll(x / scale, y / scale, amount);
    }

    /**
     * Out of a world, kit screens always get the animated backdrop (or the
     * pixel ocean), whether or not the vanilla-menu restyle is on — they're
     * Blurred's own screens. In a world, the framework's blur stays.
     *
     * <p>Overriding this (not drawing it from {@link #render}) is what keeps
     * it to once a frame: the framework calls it itself, from {@code render}
     * on older versions and from {@code renderWithTooltip} on newer ones.
     */
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (this.client != null && this.client.world == null) {
            if (skin() == PixelSkin.INSTANCE) {
                dev.blurredclient.mod.ui.OceanUi.tick(delta);
                dev.blurredclient.mod.ui.OceanUi.background(ctx, this.width, this.height);
            } else {
                Backdrop.drawScreen(ctx, this.width, this.height, mouseX, mouseY);
                if (skin() == LiquidSkin.INSTANCE) {
                    // Glass panels see this backdrop: copy it before the menu goes on top.
                    LiquidGlass.markBackgroundDone(ctx);
                }
            }
            return;
        }
        if (skin() == LiquidSkin.INSTANCE && LiquidGlass.available()) {
            // Liquid glass shows the world sharp and bends it; no blur behind.
            LiquidGlass.markBackgroundDone(ctx);
            return;
        }
        super.renderBackground(ctx, mouseX, mouseY, delta);
    }

    /** Drawn under the component tree. In a world, a light dim so panels read. */
    protected void drawBackdrop(DrawContext ctx, Ui ui) {
        if (this.client != null && this.client.world != null) {
            ui.skin.scrim(ctx, uiWidth(), uiHeight());
        }
    }

    private void drawTooltip(DrawContext ctx, Ui ui) {
        Component owner = null;
        for (Component c = hovered; c != null; c = c.parent()) {
            if (c.tooltip() != null) {
                owner = c;
                break;
            }
        }
        long now = System.nanoTime();
        if (owner != tooltipOwner) {
            tooltipOwner = owner;
            tooltipSince = now;
        }
        if (owner == null || captured != null || now - tooltipSince < TOOLTIP_DELAY_NS) {
            return;
        }

        List<String> lines = Widgets.wrap(ui.font, ui.skin, owner.tooltip(), 180);
        int textW = 0;
        for (String line : lines) {
            textW = Math.max(textW, ui.skin.width(ui.font, line));
        }
        int lineH = ui.font.fontHeight + 2;
        int boxW = textW + 12;
        int boxH = lines.size() * lineH + 8;
        int bx = Math.min(ui.mouseX + 10, uiWidth() - boxW - 4);
        int by = Math.min(ui.mouseY + 12, uiHeight() - boxH - 4);
        if (by < 4) {
            by = 4;
        }

        ui.skin.panel(ctx, bx, by, boxW, boxH, Skin.Surface.POPUP);
        int ty = by + 5;
        for (String line : lines) {
            ui.skin.drawText(ctx, ui.font, line, bx + 6, ty, ui.skin.textPrimary());
            ty += lineH;
        }
    }

    // ------------------------------------------------------------------
    // Input, normalised. kit* methods take UI units.
    // ------------------------------------------------------------------

    /** The component under a point in screen coordinates (for tests and tools). */
    public Component hitAt(double x, double y) {
        return hitTop(x / scale, y / scale);
    }

    private Component hitTop(double mx, double my) {
        return overlay != null ? overlay.hit(mx, my) : root.hit(mx, my);
    }

    protected boolean kitMouseDown(double mx, double my, int button) {
        if (overlay != null && overlay.hit(mx, my) == null) {
            closeOverlay();
            return true;
        }
        Component target = hitTop(mx, my);

        Component f = target;
        while (f != null && !f.focusable()) {
            f = f.parent();
        }
        focus(f);

        for (Component c = target; c != null; c = c.parent()) {
            if (c.onMouseDown(mx, my, button)) {
                captured = c;
                capturedButton = button;
                if (skin().ripples()) {
                    // Spread across the whole panel or button clicked, like a drop in water.
                    Component area = c;
                    while (area.parent() != null && area.parent() != root) {
                        area = area.parent();
                    }
                    Ripples.spawn((float) mx, (float) my, area.x, area.y, area.w, area.h);
                }
                return true;
            }
        }
        return target != null;
    }

    protected boolean kitMouseUp(double mx, double my, int button) {
        if (captured == null || button != capturedButton) {
            return false;
        }
        Component c = captured;
        captured = null;
        c.onMouseUp(mx, my, button);
        return true;
    }

    protected boolean kitDrag(double mx, double my, int button) {
        if (captured == null || button != capturedButton) {
            return false;
        }
        captured.onDrag(mx, my, button);
        return true;
    }

    protected boolean kitScroll(double mx, double my, double amount) {
        for (Component c = hitTop(mx, my); c != null; c = c.parent()) {
            if (c.onScroll(mx, my, amount)) {
                return true;
            }
        }
        return false;
    }

    protected boolean kitKey(int key, int scancode, int modifiers) {
        if (focused != null && focused.onKey(key, scancode, modifiers)) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (overlay != null) {
                closeOverlay();
                return true;
            }
            if (focused != null) {
                focus(null);
                return true;
            }
        }
        return false;
    }

    protected boolean kitChar(String typed) {
        return focused != null && focused.onChar(typed);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return kitScroll(mouseX / scale, mouseY / scale, vertical)
                || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    //? if >=1.21.10 {
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return kitMouseDown(click.x() / scale, click.y() / scale, click.button()) || super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        return kitMouseUp(click.x() / scale, click.y() / scale, click.button()) || super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        return kitDrag(click.x() / scale, click.y() / scale, click.button()) || super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        return kitKey(input.key(), input.scancode(), input.modifiers()) || super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        return kitChar(input.asString()) || super.charTyped(input);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mx, double my, int button) {
        return kitMouseDown(mx / scale, my / scale, button) || super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return kitMouseUp(mx / scale, my / scale, button) || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return kitDrag(mx / scale, my / scale, button) || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        return kitKey(key, scancode, modifiers) || super.keyPressed(key, scancode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return kitChar(String.valueOf(chr)) || super.charTyped(chr, modifiers);
    }
    *///?}

    @Override
    public boolean shouldPause() {
        return false;
    }
}
