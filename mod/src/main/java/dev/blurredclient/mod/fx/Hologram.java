package dev.blurredclient.mod.fx;

import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.ui.BlurredFont;
import dev.blurredclient.mod.ui.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * The 3D menu: Blurred's modules on a holographic panel standing in the
 * world. It appears in front of you, stays where it was put, and you can
 * walk around it. Aim at a module and right-click to switch it; right-click
 * a category tab to change page; scroll while aiming to see more.
 *
 * <p>Drawn as real geometry through {@link WorldDraw} — panel, tiles and
 * text are in the scene, at depth, with the hovered tile lifting toward you.
 * Clicks are found by intersecting your look ray with the panel's plane.
 */
public final class Hologram implements WorldDraw.Layer {
    public static final Hologram INSTANCE = new Hologram();

    private Hologram() {}

    /** Panel size in its own units; one unit is {@link #UNIT} blocks. */
    private static final float W = 230;
    private static final float H = 172;
    private static final float UNIT = 0.0095f;
    private static final int COLS = 2;
    private static final int ROWS = 5;

    /** Block size of one menu unit when the real menu is shown. */
    private static final float GUI_UNIT = 0.0066f;

    /** True: the panel shows the real menu (1.21.10+). False: the simpler built-in panel. */
    private boolean gui;
    private boolean open;
    private boolean closing;
    private float t;
    private long lastNs;
    private Vec3d origin;
    private Vec3d right;
    private Vec3d down;
    private Vec3d normal;
    private Category category = Category.VISUAL;
    private int page;
    /** Where your crosshair meets the panel, in panel units; NaN when it doesn't. */
    private float aimX = Float.NaN;
    private float aimY = Float.NaN;
    private final float[] hover = new float[64];

    /** A clickable region, rebuilt every frame. */
    private record Hit(float x1, float y1, float x2, float y2, Runnable action, int id) {}

    private final List<Hit> hits = new ArrayList<>();
    private int hoveredId = -1;

    public boolean isOpen() {
        return open && !closing;
    }

    public void toggle() {
        if (isOpen()) {
            closing = true;
        } else {
            spawn();
        }
    }

    private float pw() {
        return gui ? HologramGui.W : W;
    }

    private float ph() {
        return gui ? HologramGui.H : H;
    }

    private float pu() {
        return (gui ? GUI_UNIT : UNIT) * Modules.HOLOGRAM.size();
    }

    private void spawn() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        gui = HologramGui.supported();
        HologramGui.reset();
        Vec3d eye = client.player.getEyePos();
        double yaw = Math.toRadians(client.player.getYaw());
        Vec3d look = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
        normal = look.multiply(-1);
        right = new Vec3d(normal.z, 0, -normal.x);
        down = new Vec3d(0, -1, 0);
        double dist = Modules.HOLOGRAM.distance();
        Vec3d centre = eye.add(look.multiply(dist)).add(0, -0.15, 0);
        origin = centre.subtract(right.multiply(pw() * pu() / 2)).subtract(down.multiply(ph() * pu() / 2));
        open = true;
        closing = false;
        t = 0;
        lastNs = System.nanoTime();
        Sounds.click();
    }

    /** World position of a point on the panel, in panel units (for tests). */
    public Vec3d panelPoint(float u, float v) {
        return origin.add(right.multiply(u * pu())).add(down.multiply(v * pu()));
    }

    public boolean showsRealMenu() {
        return gui;
    }

    /** World position of the middle of tile {@code i} (for tests). */
    public Vec3d tileCentre(int i) {
        float tw = (W - 20 - 5) / COLS;
        float x = 10 + (i % COLS) * (tw + 5) + tw / 2;
        float y = 48 + (i / COLS) * (16 + 5) + 8;
        return origin.add(right.multiply(x * pu())).add(down.multiply(y * pu()));
    }

    // ---- input ----

    /** Right-click. True if the menu took it (so nothing in the world is used). */
    public boolean onUse() {
        if (!isOpen() || Float.isNaN(aimX)) {
            return false;
        }
        // Holding right-click repeats "use" every few ticks; one press is one click.
        if (useHeld) {
            return true;
        }
        useHeld = true;
        if (gui) {
            // Right-click is a click on the menu; sneak + right-click opens a module's settings.
            boolean sneak = MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player.isSneaking();
            HologramGui.click(aimX, aimY, sneak ? 1 : 0);
            Sounds.click();
            return true;
        }
        for (Hit h : hits) {
            if (aimX >= h.x1() && aimX < h.x2() && aimY >= h.y1() && aimY < h.y2()) {
                h.action().run();
                Sounds.click();
                return true;
            }
        }
        return true;
    }

    /** True from a right-click until the button is let go. */
    private boolean useHeld;

    /** Left-click: don't punch through the panel. */
    public boolean onAttack() {
        return isOpen() && !Float.isNaN(aimX);
    }

    /** Mouse wheel while aiming at the panel turns the page. */
    public boolean onScroll(double amount) {
        if (!isOpen() || Float.isNaN(aimX) || amount == 0) {
            return false;
        }
        if (gui) {
            HologramGui.scroll(aimX, aimY, amount);
            return true;
        }
        int pages = pages();
        page = Math.max(0, Math.min(pages - 1, page + (amount < 0 ? 1 : -1)));
        return true;
    }

    private List<Module> modules() {
        List<Module> out = new ArrayList<>();
        for (Module m : Modules.REGISTRY.all()) {
            if (m.category() == category && m.toggleable()) {
                out.add(m);
            }
        }
        return out;
    }

    private int pages() {
        return Math.max(1, (modules().size() + COLS * ROWS - 1) / (COLS * ROWS));
    }

    private static List<Category> categories() {
        List<Category> out = new ArrayList<>();
        for (Category c : Category.values()) {
            for (Module m : Modules.REGISTRY.all()) {
                if (m.category() == c && m.toggleable()) {
                    out.add(c);
                    break;
                }
            }
        }
        return out;
    }

    // ---- drawing ----

    @Override
    public void render(WorldDraw.Frame f) {
        if (!open) {
            return;
        }
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        t = closing ? Math.max(0f, t - dt / 0.25f) : Math.min(1f, t + dt / 0.35f);
        if (closing && t <= 0f) {
            open = false;
            return;
        }
        // Walked off, or 3D menu switched off: fold it away.
        if (f.client.player == null || f.client.player.getEyePos().distanceTo(center()) > 14
                || !Modules.HOLOGRAM.isActive()) {
            closing = true;
        }
        float e = 1f - (float) Math.pow(1 - t, 3);
        if (!f.client.options.useKey.isPressed()) {
            useHeld = false;
        }
        aim(f);
        if (gui) {
            renderGui(f, e, now / 1e9);
            return;
        }

        float grow = 0.86f + 0.14f * e;
        Vec3d c = center();
        Vec3d o = c.subtract(right.multiply(W * pu() * grow / 2)).subtract(down.multiply(H * pu() * grow / 2));
        int accent = dev.blurredclient.mod.Theme.chroma(dev.blurredclient.mod.ui.kit.Skins.current().accent());
        double time = now / 1e9;
        hits.clear();

        f.onPlane(o, right, down, pu() * grow, () -> {
            // Soft halo behind the panel, then the panel itself.
            f.gradient2d(-10, -10, W + 10, H + 10, Colors.fade(accent, 0.07f * e), Colors.fade(accent, 0.02f * e), true);
            f.gradient2d(0, 0, W, H, Colors.fade(0xE2101320, e), Colors.fade(0xD8090B12, e), false);
            // Scanline drifting down the glass.
            float scan = (float) ((time * 34) % (H + 30)) - 15;
            f.gradient2d(0, Math.max(0, scan - 14), W, Math.min(H, scan), Colors.fade(accent, 0), Colors.fade(accent, 0.09f * e), true);
            // Edges: a lit top line and corner brackets.
            f.rect2d(0, 0, W, 1.2f, Colors.fade(accent, e), true);
            bracket(f, 0, 0, 1, 1, accent, e);
            bracket(f, W, 0, -1, 1, accent, e);
            bracket(f, 0, H, 1, -1, accent, e);
            bracket(f, W, H, -1, -1, accent, e);

            lift(f, -0.6f);
            f.text2d(BlurredFont.uiBold("blurred"), 10, 8, Colors.fade(0xFFFFFFFF, e));
            f.text2d(BlurredFont.ui("3D menu"), 10 + f.textWidth(BlurredFont.uiBold("blurred ")), 8, Colors.fade(accent, e));
            Text hint = BlurredFont.ui("aim + right-click");
            f.text2d(hint, W - 10 - f.textWidth(hint), 8, Colors.fade(0xFF7C8090, e));
            f.rect2d(10, 22, W - 10, 22.6f, Colors.fade(0x30FFFFFF, e), false);

            tabs(f, accent, e);
            tiles(f, accent, e, time);
            footer(f, accent, e);
            lift(f, 0.6f);

            // Your aim point, so it's clear what you're pointing at.
            if (!Float.isNaN(aimX)) {
                lift(f, -2.5f);
                f.rect2d(aimX - 1.6f, aimY - 1.6f, aimX + 1.6f, aimY + 1.6f, Colors.fade(0xFFFFFFFF, 0.9f * e), false);
                f.rect2d(aimX - 3.2f, aimY - 3.2f, aimX + 3.2f, aimY + 3.2f, Colors.fade(accent, 0.35f * e), true);
                lift(f, 2.5f);
            }
        });
    }

    private Vec3d center() {
        return origin.add(right.multiply(pw() * pu() / 2)).add(down.multiply(ph() * pu() / 2));
    }

    /** The real menu on a glass panel, opening with a grow-and-fade. */
    private void renderGui(WorldDraw.Frame f, float e, double time) {
        float w = pw();
        float h = ph();
        float grow = 0.9f + 0.1f * e;
        float unit = pu() * grow;
        Vec3d c = center();
        Vec3d o = c.subtract(right.multiply(w * unit / 2)).subtract(down.multiply(h * unit / 2));
        int accent = dev.blurredclient.mod.Theme.chroma(dev.blurredclient.mod.ui.kit.Skins.current().accent());
        f.onPlane(o, right, down, unit, () -> {
            // No backing sheet: the menu's own panels float in the air, framed by the corner brackets.
            bracket(f, 0, 0, 1, 1, accent, e);
            bracket(f, w, 0, -1, 1, accent, e);
            bracket(f, 0, h, 1, -1, accent, e);
            bracket(f, w, h, -1, -1, accent, e);
        });
        if (e > 0.3f) {
            int mx = Float.isNaN(aimX) ? -100 : Math.round(aimX);
            int my = Float.isNaN(aimY) ? -100 : Math.round(aimY);
            HologramGui.render(f, o, right, down, unit, mx, my);
        }
        if (!Float.isNaN(aimX)) {
            Vec3d toViewer = right.crossProduct(down).multiply(-1);
            Vec3d at = o.add(right.multiply(aimX * unit)).add(down.multiply(aimY * unit)).add(toViewer.multiply(0.4));
            f.onPlane(at, right, down, unit, () -> {
                f.rect2d(-2.2f, -2.2f, 2.2f, 2.2f, Colors.fade(0xFFFFFFFF, 0.95f * e), false);
                f.rect2d(-4.5f, -4.5f, 4.5f, 4.5f, Colors.fade(accent, 0.35f * e), true);
            });
        }
    }

    /** Move toward (negative) or away from the viewer by {@code z} units. */
    private static void lift(WorldDraw.Frame f, float z) {
        f.matrices.translate(0, 0, z);
    }

    private static void bracket(WorldDraw.Frame f, float x, float y, int sx, int sy, int accent, float e) {
        float len = 12;
        float th = 1.4f;
        int c = Colors.fade(accent, e);
        f.rect2d(Math.min(x, x + sx * len), Math.min(y, y + sy * th), Math.max(x, x + sx * len), Math.max(y, y + sy * th), c, true);
        f.rect2d(Math.min(x, x + sx * th), Math.min(y, y + sy * len), Math.max(x, x + sx * th), Math.max(y, y + sy * len), c, true);
    }

    private void tabs(WorldDraw.Frame f, int accent, float e) {
        float x = 10;
        float y = 28;
        int id = 0;
        for (Category c : categories()) {
            Text label = BlurredFont.ui(c.label());
            float w = f.textWidth(label) * TAB_TEXT + 9;
            if (x + w > W - 10) {
                break;
            }
            boolean on = c == category;
            boolean hot = hoveredId == 100 + id;
            int bg = on ? accent : hot ? 0x40FFFFFF : 0x1EFFFFFF;
            f.rect2d(x, y, x + w, y + 13, Colors.fade(bg, e), false);
            lift(f, -0.3f);
            f.text2d(label, x + 4.5f, y + 3.5f, Colors.fade(on ? 0xFF061018 : 0xFFD8DCE6, e), TAB_TEXT);
            lift(f, 0.3f);
            final Category cat = c;
            hits.add(new Hit(x, y, x + w, y + 13, () -> {
                category = cat;
                page = 0;
            }, 100 + id));
            x += w + 3;
            id++;
        }
    }

    private void tiles(WorldDraw.Frame f, int accent, float e, double time) {
        List<Module> list = modules();
        page = Math.min(page, pages() - 1);
        float top = 48;
        float gap = 5;
        float tw = (W - 20 - gap) / COLS;
        float th = 16;
        int start = page * COLS * ROWS;
        for (int i = 0; i < COLS * ROWS && start + i < list.size(); i++) {
            Module m = list.get(start + i);
            int col = i % COLS;
            int row = i / COLS;
            float x = 10 + col * (tw + gap);
            float y = top + row * (th + gap);
            // Tiles fall into place one after another as the panel opens.
            float appear = Math.max(0f, Math.min(1f, e * 1.8f - i * 0.07f));
            if (appear <= 0) {
                continue;
            }
            boolean hot = hoveredId == i;
            hover[i] += ((hot ? 1f : 0f) - hover[i]) * 0.3f;
            float h = hover[i];
            boolean on = m.isEnabled();
            float a = e * appear;
            float z = -1.2f - 4f * h + (1 - appear) * 6;
            lift(f, z);
            f.rect2d(x, y, x + tw, y + th, Colors.fade(Colors.lerp(0x22FFFFFF, 0x3CFFFFFF, h), a), false);
            if (on) {
                f.rect2d(x, y, x + 2, y + th, Colors.fade(accent, a), true);
            }
            if (h > 0.02f) {
                f.rect2d(x, y + th - 0.8f, x + tw * h, y + th, Colors.fade(accent, a), true);
            }
            lift(f, -0.3f);
            String name = fit(f, m.name(), tw - 34);
            f.text2d(BlurredFont.ui(name), x + 7, y + 4.5f, Colors.fade(on ? 0xFFFFFFFF : 0xFFB4B8C4, a), TILE_TEXT);
            // The switch: a pill with a knob that slides.
            float px = x + tw - 22;
            float py = y + 4;
            f.rect2d(px, py, px + 16, py + 8, Colors.fade(on ? accent : 0x40FFFFFF, a), false);
            float knob = on ? px + 9 : px + 1;
            lift(f, -0.8f);
            f.rect2d(knob, py + 1, knob + 6, py + 7, Colors.fade(0xFFFFFFFF, a), false);
            lift(f, 1.6f - z);
            final Module mod = m;
            hits.add(new Hit(x, y, x + tw, y + th, mod::toggle, i));
        }
        if (list.isEmpty()) {
            f.text2d(BlurredFont.ui("Nothing here yet"), 10, top, Colors.fade(0xFF7C8090, e));
        }
    }

    private void footer(WorldDraw.Frame f, int accent, float e) {
        int pages = pages();
        float y = H - 14;
        Text label = BlurredFont.ui((page + 1) + " / " + pages);
        float w = f.textWidth(label);
        f.text2d(label, (W - w) / 2, y, Colors.fade(0xFF9A9EAA, e));
        if (page > 0) {
            boolean hot = hoveredId == 200;
            f.rect2d(W / 2 - w / 2 - 22, y - 2, W / 2 - w / 2 - 8, y + 9, Colors.fade(hot ? accent : 0x26FFFFFF, e), false);
            f.text2d(BlurredFont.ui("<"), W / 2 - w / 2 - 17, y, Colors.fade(0xFFFFFFFF, e));
            hits.add(new Hit(W / 2 - w / 2 - 22, y - 2, W / 2 - w / 2 - 8, y + 9, () -> page--, 200));
        }
        if (page < pages - 1) {
            boolean hot = hoveredId == 201;
            f.rect2d(W / 2 + w / 2 + 8, y - 2, W / 2 + w / 2 + 22, y + 9, Colors.fade(hot ? accent : 0x26FFFFFF, e), false);
            f.text2d(BlurredFont.ui(">"), W / 2 + w / 2 + 13, y, Colors.fade(0xFFFFFFFF, e));
            hits.add(new Hit(W / 2 + w / 2 + 8, y - 2, W / 2 + w / 2 + 22, y + 9, () -> page++, 201));
        }
        Text full = BlurredFont.ui("Full menu");
        float fw = f.textWidth(full) + 10;
        boolean hot = hoveredId == 202;
        f.rect2d(W - 10 - fw, y - 2, W - 10, y + 9, Colors.fade(hot ? accent : 0x1EFFFFFF, e), false);
        f.text2d(full, W - 5 - fw, y, Colors.fade(hot ? 0xFF061018 : 0xFFD8DCE6, e));
        hits.add(new Hit(W - 10 - fw, y - 2, W - 10, y + 9, () -> {
            closing = true;
            MinecraftClient.getInstance().setScreen(new dev.blurredclient.mod.screen.clickgui.ClickGuiScreen(null));
        }, 202));
    }

    private static final float TAB_TEXT = 0.72f;
    private static final float TILE_TEXT = 0.82f;

    /** Shorten {@code name} with an ellipsis to fit {@code max} units at tile text size. */
    private static String fit(WorldDraw.Frame f, String name, float max) {
        if (f.textWidth(BlurredFont.ui(name)) * TILE_TEXT <= max) {
            return name;
        }
        String cut = name;
        while (cut.length() > 1 && f.textWidth(BlurredFont.ui(cut + "…")) * TILE_TEXT > max) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.stripTrailing() + "…";
    }

    /** Intersect your look ray with the panel; sets the aim point and the hovered region. */
    private void aim(WorldDraw.Frame f) {
        aimX = Float.NaN;
        aimY = Float.NaN;
        hoveredId = -1;
        if (closing || f.client.player == null || f.client.currentScreen != null) {
            return;
        }
        Vec3d eye = f.client.player.getCameraPosVec(f.tickDelta);
        Vec3d dir = f.client.player.getRotationVec(f.tickDelta);
        double denom = dir.dotProduct(normal);
        if (denom >= -1e-4) {
            return;
        }
        double dist = origin.subtract(eye).dotProduct(normal) / denom;
        if (dist <= 0 || dist > 12) {
            return;
        }
        Vec3d hit = eye.add(dir.multiply(dist)).subtract(origin);
        float u = (float) (hit.dotProduct(right) / pu());
        float v = (float) (hit.dotProduct(down) / pu());
        if (u < 0 || v < 0 || u > pw() || v > ph()) {
            return;
        }
        aimX = u;
        aimY = v;
        // Hit regions are from the previous frame; the layout doesn't move between frames.
        for (Hit h : hits) {
            if (u >= h.x1() && u < h.x2() && v >= h.y1() && v < h.y2()) {
                hoveredId = h.id();
            }
        }
    }

    /** UI click, shared with the kit widgets. */
    private static final class Sounds {
        static void click() {
            dev.blurredclient.mod.ui.kit.widget.Sounds.click();
        }
    }
}
