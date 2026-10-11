package dev.blurredclient.mod.module.hud;

import dev.blurredclient.mod.fx.CombatEvents;
import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.ColorSetting;
import dev.blurredclient.mod.setting.ModeSetting;
import dev.blurredclient.mod.setting.SliderSetting;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.kit.Anim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Your own crosshair, drawn like Minecraft's: whole GUI pixels, and by
 * default the same colour-inverting blend vanilla uses, so it shows on any
 * background. Shape, arm length, gap, thickness and dot are all in pixels;
 * it can open up while you move or as your swing recharges, and kick when a
 * hit lands. Replaces vanilla's (hidden while this is on).
 *
 * <p>Shapes are drawn into a pixel grid first and then emitted as row runs,
 * so nothing is drawn twice: an inverted pixel drawn twice would cancel out.
 */
public final class CrosshairModule extends HudModule {
    public enum Style {
        CROSS("Cross"), CROSS_DOT("Cross + dot"), DOT("Dot"), CIRCLE("Circle"), CIRCLE_DOT("Circle + dot"),
        T("T"), CHEVRON("Chevron"), BRACKETS("Brackets"), PAINTED("Painted (your own)");

        private final String label;

        Style(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Dynamic {
        OFF("Off"), MOVEMENT("While moving"), CHARGE("Attack charge");

        private final String label;

        Dynamic(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Odd, so there's a centre pixel, and placed like vanilla's 15-pixel crosshair. */
    private static final int BOX = 47;
    private static final int MID = BOX / 2;
    private static final Identifier PIXEL = Identifier.fromNamespaceAndPath("blurred", "hud/pixel");

    private static final int PAINT = 15;

    private final ModeSetting<Style> style;
    private final dev.blurredclient.mod.setting.PixelGridSetting painted;
    private final BoolSetting mirrorPaint;
    private final SliderSetting length;
    private final SliderSetting gap;
    private final SliderSetting thickness;
    private final SliderSetting dotSize;
    private final BoolSetting invert;
    private final ColorSetting colour;
    private final BoolSetting outline;
    private final ModeSetting<Dynamic> dynamic;
    private final BoolSetting hitKick;
    private final Anim spread = new Anim(0f, 10f);
    private final Anim kick = new Anim(0f, 9f);
    private final boolean[] grid = new boolean[BOX * BOX];

    public CrosshairModule() {
        super("crosshair", "Crosshair", "A pixel crosshair like Minecraft's, in any shape and size, reacting to movement and hits.",
                Category.HUD, false, new HudPlacement(HudPlacement.Anchor.CENTER, 0, 0));
        this.style = add(new ModeSetting<>("style", "Shape", "The crosshair's shape. Painted is one you draw yourself.", Style.CROSS, Style::label));
        this.mirrorPaint = add(new BoolSetting("paint_mirror", "Mirror while painting",
                "Every pixel you paint is copied to the other three quarters, so it stays symmetrical.", true));
        mirrorPaint.visibleIf(() -> style.get() == Style.PAINTED);
        this.painted = add(new dev.blurredclient.mod.setting.PixelGridSetting("painted", "Paint it",
                "Left-drag to paint pixels, right-drag to erase. The faint lines mark the centre.", PAINT, defaultPainting()))
                .mirrorWhen(mirrorPaint::isOn);
        painted.visibleIf(() -> style.get() == Style.PAINTED);
        this.length = add(new SliderSetting("length", "Length", "How long each arm is, in pixels.", 7, 1, 14, 1, "px"));
        this.gap = add(new SliderSetting("gap", "Gap", "Empty pixels around the centre.", 0, 0, 8, 1, "px"));
        this.thickness = add(new SliderSetting("thickness", "Thickness", "Line thickness, in pixels.", 1, 1, 4, 1, "px"));
        this.dotSize = add(new SliderSetting("dot", "Dot size", "Size of the centre dot, in pixels.", 1, 1, 4, 1, "px"));
        this.invert = add(new BoolSetting("invert", "Invert like vanilla",
                "Inverts whatever is behind it, like Minecraft's crosshair, so it always shows. Off uses your colour.", true));
        this.colour = add(new ColorSetting("colour", "Colour", "Crosshair colour (alpha too).", 0xFFFFFFFF));
        colour.visibleIf(() -> !invert.isOn());
        this.outline = add(new BoolSetting("outline", "Outline", "A one-pixel dark edge so it shows on bright backgrounds.", true));
        outline.visibleIf(() -> !invert.isOn());
        this.dynamic = add(new ModeSetting<>("dynamic", "Opens up", "Widen the gap while you move, or while your swing recharges.",
                Dynamic.OFF, Dynamic::label));
        this.hitKick = add(new BoolSetting("hit_kick", "React to hits", "Jumps out a couple of pixels when a hit lands.", true));
        CombatEvents.onAttack(e -> {
            if (isActive()) {
                kick.snap(1f);
            }
        });
    }

    /** Vanilla's crosshair is skipped while this draws its own. */
    public boolean replacesVanilla() {
        Minecraft client = Minecraft.getInstance();
        return isActive() && client.options.getCameraType().isFirstPerson();
    }

    @Override
    public Size measure(Minecraft client, boolean preview) {
        if (!preview && !client.options.getCameraType().isFirstPerson()) {
            return null;
        }
        return new Size(BOX, BOX);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, Minecraft client, boolean preview) {
        float open = 0f;
        if (!preview && client.player != null) {
            open = switch (dynamic.get()) {
                case MOVEMENT -> spread.follow(client.player.getDeltaMovement().horizontalDistanceSqr() > 0.002) * 3f;
                case CHARGE -> (1f - client.player.getAttackStrengthScale(0.5f)) * 4f;
                case OFF -> 0f;
            };
        }
        float k = kick.get();
        kick.target(0f);
        int g = Math.round(gap.floatValue() + open + (hitKick.isOn() ? k * 2f : 0f));
        int len = Math.round(length.floatValue());
        int t = Math.max(1, Math.round(thickness.floatValue()));
        int dot = Math.max(1, Math.round(dotSize.floatValue()));

        java.util.Arrays.fill(grid, false);
        shape(g, len, t, dot);
        if (invert.isOn()) {
            inverted(ctx);
            return;
        }
        int c = colour.argb();
        if (outline.isOn()) {
            int edge = Colors.fade(0xC0000000, Colors.alpha(c) / 255f);
            for (int y = 0; y < BOX; y++) {
                for (int x = 0; x < BOX; x++) {
                    if (!on(x, y) && near(x, y)) {
                        ctx.fill(x, y, x + 1, y + 1, edge);
                    }
                }
            }
        }
        runs((x, y, w) -> ctx.fill(x, y, x + w, y + 1, c));
    }

    private void inverted(GuiGraphicsExtractor ctx) {
        ctx.nextStratum();
        runs((x, y, w) -> ctx.blitSprite(net.minecraft.client.renderer.RenderPipelines.CROSSHAIR, PIXEL, x, y, w, 1));
    }

    private interface Run {
        void draw(int x, int y, int width);
    }

    /** Each row's runs of lit pixels, as one strip each. */
    private void runs(Run run) {
        for (int y = 0; y < BOX; y++) {
            int x = 0;
            while (x < BOX) {
                if (!on(x, y)) {
                    x++;
                    continue;
                }
                int start = x;
                while (x < BOX && on(x, y)) {
                    x++;
                }
                run.draw(start, y, x - start);
            }
        }
    }

    private boolean on(int x, int y) {
        return x >= 0 && y >= 0 && x < BOX && y < BOX && grid[y * BOX + x];
    }

    private boolean near(int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (on(x + dx, y + dy)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void set(int dx, int dy) {
        int x = MID + dx;
        int y = MID + dy;
        if (x >= 0 && y >= 0 && x < BOX && y < BOX) {
            grid[y * BOX + x] = true;
        }
    }

    /** A filled square of side {@code size}, centred on the middle pixel. */
    private void square(int size) {
        int lo = -(size - 1) / 2;
        for (int y = lo; y < lo + size; y++) {
            for (int x = lo; x < lo + size; x++) {
                set(x, y);
            }
        }
    }

    /** A plain plus to start painting from. */
    private static String defaultPainting() {
        StringBuilder b = new StringBuilder();
        int mid = PAINT / 2;
        for (int y = 0; y < PAINT; y++) {
            for (int x = 0; x < PAINT; x++) {
                boolean arm = (x == mid && Math.abs(y - mid) >= 2 && Math.abs(y - mid) <= 5)
                        || (y == mid && Math.abs(x - mid) >= 2 && Math.abs(x - mid) <= 5);
                b.append(arm || (x == mid && y == mid) ? '1' : '0');
            }
        }
        return b.toString();
    }

    private void shape(int g, int len, int t, int dot) {
        Style s = style.get();
        if (s == Style.PAINTED) {
            int mid = PAINT / 2;
            // Opening up and hit kicks push each pixel out from the middle.
            int push = g;
            for (int y = 0; y < PAINT; y++) {
                for (int x = 0; x < PAINT; x++) {
                    if (painted.on(x, y)) {
                        int dx = x - mid;
                        int dy = y - mid;
                        set(dx + Integer.signum(dx) * push, dy + Integer.signum(dy) * push);
                    }
                }
            }
            return;
        }
        int lo = -(t - 1) / 2;
        int hi = lo + t - 1;
        if (s == Style.CROSS || s == Style.CROSS_DOT || s == Style.T) {
            for (int b = lo; b <= hi; b++) {
                for (int d = 1; d <= len; d++) {
                    if (s != Style.T) {
                        set(b, lo - g - d);
                    }
                    set(b, hi + g + d);
                    set(lo - g - d, b);
                    set(hi + g + d, b);
                }
            }
            if (g == 0 && s != Style.CROSS_DOT) {
                square(t);
            }
        }
        if (s == Style.CIRCLE || s == Style.CIRCLE_DOT) {
            float r = g + len * 0.5f + 1.5f;
            float half = t / 2f;
            int reach = (int) Math.ceil(r + half) + 1;
            for (int y = -reach; y <= reach; y++) {
                for (int x = -reach; x <= reach; x++) {
                    float dist = (float) Math.sqrt(x * x + y * y);
                    if (Math.abs(dist - r) < half + 0.01f) {
                        set(x, y);
                    }
                }
            }
        }
        if (s == Style.CHEVRON) {
            for (int x = -len; x <= len; x++) {
                int y = g / 2 + 1 + Math.round(Math.abs(x) * 0.7f);
                for (int b = 0; b < t; b++) {
                    set(x, y + b);
                }
            }
        }
        if (s == Style.BRACKETS) {
            int r = g + len;
            int arm = Math.max(2, Math.round(len * 0.6f));
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    for (int a = 0; a < arm; a++) {
                        for (int b = 0; b < t; b++) {
                            set(sx * (r - a), sy * (r - b));
                            set(sx * (r - b), sy * (r - a));
                        }
                    }
                }
            }
        }
        if (s == Style.DOT || s == Style.CROSS_DOT || s == Style.CIRCLE_DOT || s == Style.BRACKETS) {
            square(dot);
        }
    }
}
