package dev.blurredclient.mod.ui.kit.widget;

import dev.blurredclient.mod.ui.kit.KitComponent;
import dev.blurredclient.mod.ui.kit.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A clipped, scrollable window onto one taller child.
 *
 * <p>Scrolling eases toward its target instead of jumping a fixed step, which
 * is most of what makes a list feel smooth with a notched mouse wheel. The
 * scrollbar thumb can be dragged.
 *
 * <p>Children outside the visible area can't be hovered or clicked: hit
 * testing stops at this component's bounds, so a row scrolled up under a
 * header can't steal the header's clicks.
 */
public class ScrollView extends KitComponent {
    private static final int BAR_W = 4;
    private static final int GUTTER = BAR_W + 4;
    private static final double WHEEL_STEP = 28;

    private final KitComponent content;
    private double offset;
    private double target;
    private long lastFrame = System.nanoTime();
    private boolean draggingThumb;
    private double dragGrabY;

    public ScrollView(KitComponent content) {
        this.content = add(content);
    }

    /** Full width when everything fits; leaves room for the scrollbar only when it doesn't. */
    private int contentWidth() {
        return content.preferredHeight(w) > h ? w - GUTTER : w;
    }

    private int contentHeight() {
        return content.preferredHeight(contentWidth());
    }

    private double maxOffset() {
        return Math.max(0, contentHeight() - h);
    }

    @Override
    protected void layout() {
        target = Math.min(target, maxOffset());
        offset = Math.min(offset, maxOffset());
        content.setBounds(x, y - (int) Math.round(offset), contentWidth(), contentHeight());
    }

    /** Scroll so the content's top is at the top (e.g. after switching category). */
    public void scrollToTop() {
        target = 0;
        offset = 0;
        layout();
    }

    @Override
    protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;

        target = Math.max(0, Math.min(maxOffset(), target));
        double next = offset + (target - offset) * (1 - Math.exp(-18 * dt));
        if (Math.abs(target - next) < 0.5) {
            next = target;
        }
        offset = next;

        // Content can grow (a section expanded); re-lay out when it does.
        int desiredH = contentHeight();
        int desiredY = y - (int) Math.round(offset);
        if (content.h != desiredH || content.w != contentWidth()) {
            content.setBounds(x, desiredY, contentWidth(), desiredH);
        } else if (content.y != desiredY) {
            content.shift(0, desiredY - content.y);
        }
    }

    @Override
    protected void drawChildren(GuiGraphicsExtractor ctx, Ui ui) {
        dev.blurredclient.mod.ui.Draw.scissor(ctx, x, y, x + w, y + h);
        super.drawChildren(ctx, ui);
        dev.blurredclient.mod.ui.Draw.unscissor(ctx);
    }

    @Override
    protected void drawOver(GuiGraphicsExtractor ctx, Ui ui) {
        double max = maxOffset();
        if (max <= 0) {
            return;
        }
        int[] thumb = thumb();
        float hov = hover.follow(draggingThumb || (ui.isHovered(this) && ui.mouseX >= x + w - GUTTER));
        ui.skin.scrollbar(ctx, x + w - BAR_W - 1, y, BAR_W, h, thumb[0], thumb[1], hov);
    }

    /** {thumbY, thumbH} in screen pixels. */
    private int[] thumb() {
        int total = Math.max(1, contentHeight());
        int thumbH = Math.max(16, h * h / total);
        double max = maxOffset();
        int thumbY = y + (int) Math.round((h - thumbH) * (max <= 0 ? 0 : offset / max));
        return new int[] {thumbY, thumbH};
    }

    @Override
    public KitComponent hit(double mx, double my) {
        if (!visible || !contains(mx, my)) {
            return null;
        }
        if (maxOffset() <= 0 || mx < x + w - GUTTER) {
            KitComponent inside = content.hit(mx, my);
            if (inside != null) {
                return inside;
            }
        }
        return this;
    }

    @Override
    public boolean onScroll(double mx, double my, double amount) {
        if (maxOffset() <= 0) {
            return false;
        }
        target = Math.max(0, Math.min(maxOffset(), target - amount * WHEEL_STEP));
        return true;
    }

    @Override
    public boolean onMouseDown(double mx, double my, int button) {
        if (button != 0 || maxOffset() <= 0 || mx < x + w - GUTTER) {
            return false;
        }
        int[] thumb = thumb();
        draggingThumb = true;
        // Grab the thumb where it was clicked; clicking the track jumps it there.
        dragGrabY = my >= thumb[0] && my < thumb[0] + thumb[1] ? my - thumb[0] : thumb[1] / 2.0;
        onDrag(mx, my, button);
        return true;
    }

    @Override
    public void onDrag(double mx, double my, int button) {
        if (!draggingThumb) {
            return;
        }
        int[] thumb = thumb();
        double travel = Math.max(1, h - thumb[1]);
        double f = (my - dragGrabY - y) / travel;
        target = Math.max(0, Math.min(maxOffset(), f * maxOffset()));
        offset = target;
    }

    @Override
    public void onMouseUp(double mx, double my, int button) {
        draggingThumb = false;
    }
}
