package dev.blurredclient.mod.ui.kit;

import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A rectangle in a {@link KitScreen}'s tree that can draw and take input.
 *
 * <h2>Events</h2>
 *
 * The screen hit-tests to find the deepest component under the mouse, then
 * offers the event to it and each ancestor in turn until one returns true. So
 * a button inside a card handles its own click, and a scroll over that button
 * bubbles up to the scroll view around it without either knowing about the
 * other. Whoever accepts a mouse-down receives the matching drags and the
 * mouse-up, even if the pointer leaves it, which is what makes dragging a
 * slider past its end work.
 *
 * <p>Positions are absolute screen coordinates, set by the parent's
 * {@link #layout()}. There's no transform stack to reason about when
 * hit-testing.
 */
public abstract class Component {
    public int x;
    public int y;
    public int w;
    public int h;
    public boolean visible = true;

    private Component parent;
    private final List<Component> children = new ArrayList<>();
    private String tooltip;

    /** Hover amount, eased, for skins to fade on. */
    protected final Anim hover = new Anim(0f, 16f);

    /** Set position and size, then lay out children. Returns this for chaining. */
    public Component setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        layout();
        return this;
    }

    /** Move by an offset, children included, without re-running layout. */
    public void shift(int dx, int dy) {
        x += dx;
        y += dy;
        for (Component c : children) {
            c.shift(dx, dy);
        }
    }

    /** Position children. Called whenever this component's bounds are set. */
    protected void layout() {}

    public <C extends Component> C add(C child) {
        ((Component) child).parent = this;
        children.add(child);
        return child;
    }

    /** Move {@code child} to the end, so it draws on top and is hit-tested first. */
    public void bringToFront(Component child) {
        if (children.remove(child)) {
            children.add(child);
        }
    }

    public void clearChildren() {
        for (Component c : children) {
            c.parent = null;
        }
        children.clear();
    }

    public List<Component> children() {
        return Collections.unmodifiableList(children);
    }

    public Component parent() {
        return parent;
    }

    /**
     * The height this component wants at a given width, for stacking layouts.
     * Wrapped text needs the width to know how many lines it takes.
     */
    public int preferredHeight(int width) {
        return h;
    }

    public Component tooltip(String text) {
        this.tooltip = text;
        return this;
    }

    public String tooltip() {
        return tooltip;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /**
     * The deepest visible component at a point, or null. Children are tested
     * last-first, so whatever is drawn on top wins.
     */
    public Component hit(double mx, double my) {
        if (!visible || !contains(mx, my)) {
            return null;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            Component found = children.get(i).hit(mx, my);
            if (found != null) {
                return found;
            }
        }
        return hitsSelf() ? this : null;
    }

    /** False for pure layout containers, so clicks on empty space fall through. */
    protected boolean hitsSelf() {
        return true;
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    public final void render(DrawContext ctx, Ui ui) {
        if (!visible) {
            return;
        }
        draw(ctx, ui);
        drawChildren(ctx, ui);
        drawOver(ctx, ui);
    }

    /** Draw this component, beneath its children. */
    protected void draw(DrawContext ctx, Ui ui) {}

    protected void drawChildren(DrawContext ctx, Ui ui) {
        for (Component c : children) {
            c.render(ctx, ui);
        }
    }

    /** Draw on top of the children (scrollbars, overlays on a card). */
    protected void drawOver(DrawContext ctx, Ui ui) {}

    // ------------------------------------------------------------------
    // Input. Return true to accept the event and stop it bubbling.
    // ------------------------------------------------------------------

    public boolean onMouseDown(double mx, double my, int button) {
        return false;
    }

    public void onMouseUp(double mx, double my, int button) {}

    public void onDrag(double mx, double my, int button) {}

    public boolean onScroll(double mx, double my, double amount) {
        return false;
    }

    /** Only called while focused. Key codes are GLFW's. */
    public boolean onKey(int key, int scancode, int modifiers) {
        return false;
    }

    /** Only called while focused. */
    public boolean onChar(String typed) {
        return false;
    }

    public boolean focusable() {
        return false;
    }

    public void onFocusChanged(boolean focused) {}
}
