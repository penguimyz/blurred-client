package dev.blurredclient.mod.module.hud;

import dev.blurredclient.mod.hud.HudStyle;
import dev.blurredclient.mod.hud.Lyrics;
import dev.blurredclient.mod.hud.SystemMedia;
import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.SliderSetting;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Synced lyrics for what you're listening to, karaoke-style: the current line
 * fills in with colour as it's sung, the next line waits underneath, and
 * lines slide up as the song moves on. Works with anything Now playing can
 * see on your computer (Spotify, a browser, ...).
 *
 * <p>Lyrics are looked up on LRCLIB (lrclib.net) by artist and title.
 */
public final class LyricsModule extends HudModule implements dev.blurredclient.mod.fx.WorldDraw.Layer {
    public enum Display {
        PANEL("On the HUD"), WORLD("Floating in 3D");

        private final String label;

        Display(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** A sung line hanging in the world where it appeared. */
    private static final class Floating {
        final String text;
        final net.minecraft.util.math.Vec3d at;
        /** Which way the line faces: set once when it appears, so it stays put rather than turning to follow you. */
        final net.minecraft.util.math.Vec3d right;
        final net.minecraft.util.math.Vec3d down;
        final long born = System.nanoTime();
        long gone = -1;

        Floating(String text, net.minecraft.util.math.Vec3d at, net.minecraft.util.math.Vec3d right, net.minecraft.util.math.Vec3d down) {
            this.text = text;
            this.at = at;
            this.right = right;
            this.down = down;
        }
    }

    private final dev.blurredclient.mod.setting.ModeSetting<Display> display;
    private final java.util.List<Floating> floating = new java.util.ArrayList<>();
    private int floatingLine = -2;
    private int side = 1;
    private final java.util.Random random = new java.util.Random();
    private final SliderSetting size;
    private final SliderSetting distance;
    private final BoolSetting karaoke;
    private final BoolSetting nextLine;
    private final SliderSetting offset;
    private int shownLine = -2;
    private long lineChanged;

    public LyricsModule() {
        super("lyrics", "Lyrics", "Synced lyrics for the song you're playing, filling in as it's sung. Looked up on lrclib.net.",
                Category.HUD, false, new HudPlacement(HudPlacement.Anchor.BOTTOM_CENTER, 0, -62));
        this.display = add(new dev.blurredclient.mod.setting.ModeSetting<>("display", "Display",
                "A panel on your HUD, or lines floating in the world around your view, like the 3D menu.",
                Display.PANEL, Display::label));
        this.size = add(new SliderSetting("size", "Text size", "How big the lyrics are.", 1.2, 0.6, 2.5, 0.05, "x"));
        this.distance = add(new SliderSetting("distance", "Distance", "How far away floating lines appear.", 4.5, 2.0, 10.0, 0.25, " blocks"));
        distance.visibleIf(() -> display.get() == Display.WORLD);
        this.karaoke = add(new BoolSetting("karaoke", "Karaoke fill", "The line fills with colour as it's sung.", true));
        this.nextLine = add(new BoolSetting("next", "Show next line", "The coming line, faded, underneath.", true));
        this.offset = add(new SliderSetting("offset", "Timing", "Nudge the lyrics earlier or later if they're off.", 0, -2, 2, 0.1, "s"));
        dev.blurredclient.mod.fx.WorldDraw.add(this);
    }

    /** Keep the media watcher running for us even if Now playing is off. */
    public boolean wantsMedia() {
        return isActive();
    }

    private record Now(String line, String next, float progress) {}

    private Now now(boolean preview) {
        if (preview) {
            return new Now("And the lyrics fill in as it plays", "with the next line waiting", 0.45f);
        }
        SystemMedia.ensureRunning();
        SystemMedia.Track t = SystemMedia.current();
        if (t == null || !t.playing()) {
            return null;
        }
        List<Lyrics.Line> lines = Lyrics.forSong(t.artist(), t.title(), t.length());
        if (lines.isEmpty()) {
            return null;
        }
        double pos = t.positionNow() + offset.get();
        int i = Lyrics.lineAt(lines, pos);
        if (i < 0) {
            return null;
        }
        if (i != shownLine) {
            shownLine = i;
            lineChanged = System.nanoTime();
        }
        Lyrics.Line cur = lines.get(i);
        double end = i + 1 < lines.size() ? lines.get(i + 1).at() : cur.at() + 4;
        float progress = (float) Math.max(0, Math.min(1, (pos - cur.at()) / Math.max(0.3, (end - cur.at()) * 0.9)));
        String next = i + 1 < lines.size() ? lines.get(i + 1).text() : "";
        if (cur.text().isBlank()) {
            return null;
        }
        return new Now(cur.text(), next, progress);
    }

    @Override
    public Size measure(MinecraftClient client, boolean preview) {
        if (!preview && display.get() == Display.WORLD) {
            return null;
        }
        Now n = now(preview);
        if (n == null) {
            return null;
        }
        var font = client.textRenderer;
        float s = size.floatValue();
        int w = Draw.width(font, HudStyle.bold(n.line()), s);
        if (nextLine.isOn()) {
            w = Math.max(w, Draw.width(font, HudStyle.text(n.next()), s * 0.75f));
        }
        int h = Math.round(9 * s + (nextLine.isOn() ? 9 * s * 0.75f + 3 : 0)) + 8;
        return new Size(w + 16, h);
    }

    @Override
    public void render(DrawContext ctx, MinecraftClient client, boolean preview) {
        Now n = now(preview);
        Size sz = measure(client, preview);
        if (n == null || sz == null) {
            return;
        }
        var font = client.textRenderer;
        float s = size.floatValue();
        float in = preview ? 1f : Math.min(1f, (System.nanoTime() - lineChanged) / 1e9f / 0.25f);
        float rise = (1 - in) * 6;
        HudStyle.panel(ctx, 0, 0, sz.width(), sz.height());
        int accent = HudStyle.accent();
        int lw = Draw.width(font, HudStyle.bold(n.line()), s);
        int x = (sz.width() - lw) / 2;
        int y = Math.round(4 + rise);
        if (karaoke.isOn()) {
            // Sung part bright in the accent, the rest dim; split by characters.
            String line = n.line();
            int cut = Math.round(line.length() * n.progress());
            String sung = line.substring(0, cut);
            Text sungText = HudStyle.bold(sung);
            Draw.text(ctx, font, HudStyle.bold(line), x, y, Colors.fade(0xFF8A90A0, in), s);
            Draw.text(ctx, font, sungText, x, y, Colors.fade(Colors.lerp(accent, 0xFFFFFFFF, 0.25f), in), s);
        } else {
            Draw.text(ctx, font, HudStyle.bold(n.line()), x, y, Colors.fade(0xFFFFFFFF, in), s);
        }
        if (nextLine.isOn() && !n.next().isBlank()) {
            float ns = s * 0.75f;
            int nw = Draw.width(font, HudStyle.text(n.next()), ns);
            Draw.text(ctx, font, HudStyle.text(n.next()), (sz.width() - nw) / 2, Math.round(y + 9 * s + 3), 0xFF6C7280, ns);
        }
    }

    // ---- floating in the world ----

    @Override
    public void render(dev.blurredclient.mod.fx.WorldDraw.Frame f) {
        if (!isActive() || display.get() != Display.WORLD || f.client.player == null) {
            floating.clear();
            floatingLine = -2;
            return;
        }
        Now n = now(false);
        long nowNs = System.nanoTime();
        if (n != null && shownLine != floatingLine) {
            floatingLine = shownLine;
            for (Floating old : floating) {
                if (old.gone < 0) {
                    old.gone = nowNs;
                }
            }
            Vec3d[] bbNow = f.billboard();
            // Face the way you were looking when it appeared, kept upright.
            Vec3d right = new Vec3d(bbNow[0].x, 0, bbNow[0].z).normalize();
            floating.add(new Floating(n.line(), spot(f), right, new Vec3d(0, -1, 0)));
        } else if (n == null) {
            for (Floating old : floating) {
                if (old.gone < 0) {
                    old.gone = nowNs;
                }
            }
            floatingLine = -2;
        }
        floating.removeIf(l -> l.gone >= 0 && (nowNs - l.gone) / 1e9 > 0.8);

        // A large, round face (Varela Round) rendered at high resolution.
        float scale = 0.36f / FONT_PX * size.floatValue();
        var font = f.client.textRenderer;
        int accent = HudStyle.accent();
        for (Floating l : floating) {
            float in = Math.min(1f, (nowNs - l.born) / 1e9f / 0.35f);
            float out = l.gone < 0 ? 0f : Math.min(1f, (nowNs - l.gone) / 1e9f / 0.8f);
            float alpha = ease(in) * (1f - out);
            if (alpha <= 0.01f) {
                continue;
            }
            // Rises into place, then drifts up and away as the next line comes.
            Vec3d at = l.at.add(0, (1 - ease(in)) * -0.15 + out * 0.35, 0);
            Text full = dev.blurredclient.mod.ui.BlurredFont.px(l.text, FONT_PX);
            int w = font.getWidth(full);
            Vec3d down = l.down;
            Vec3d origin = at.subtract(l.right.multiply(w * scale / 2.0)).subtract(down.multiply(FONT_PX * 0.5 * scale));
            boolean current = l.gone < 0 && n != null;
            float progress = current ? n.progress() : 1f;
            f.onPlane(origin, l.right, down, scale, () -> {
                // A soft shadow behind, then the line, the sung part in the accent.
                f.text2d(full, FONT_PX * 0.035f, FONT_PX * 0.035f, Colors.fade(0x60000000, alpha));
                f.matrices.translate(0, 0, -0.02f);
                if (karaoke.isOn() && current) {
                    f.text2d(full, 0, 0, Colors.fade(0xFFB0B6C4, alpha));
                    int cut = Math.round(l.text.length() * progress);
                    f.matrices.translate(0, 0, -0.02f);
                    f.text2d(dev.blurredclient.mod.ui.BlurredFont.px(l.text.substring(0, cut), FONT_PX), 0, 0,
                            Colors.fade(Colors.lerp(accent, 0xFFFFFFFF, 0.25f), alpha));
                } else {
                    f.text2d(full, 0, 0, Colors.fade(0xFFFFFFFF, alpha));
                }
                if (current && nextLine.isOn() && !n.next().isBlank()) {
                    Text next = dev.blurredclient.mod.ui.BlurredFont.px(n.next(), FONT_PX);
                    int nw = font.getWidth(next);
                    f.text2d(next, (w - nw * 0.65f) / 2f, FONT_PX * 1.25f, Colors.fade(0xFFB0B6C4, alpha * 0.75f), 0.65f);
                }
            });
        }
    }

    /**
     * Somewhere in view to put a new line: off to one side of the middle
     * (alternating sides), a little above or below eye level, a few blocks
     * out. It stays there in the world while it's sung.
     */
    private Vec3d spot(dev.blurredclient.mod.fx.WorldDraw.Frame f) {
        Vec3d[] bb = f.billboard();
        side = -side;
        double x = side * (0.28 + random.nextDouble() * 0.22);
        double y = 0.02 + random.nextDouble() * 0.3;
        Vec3d dir = bb[2].add(bb[0].multiply(x)).add(bb[1].multiply(y)).normalize();
        double dist = distance.get() + (random.nextDouble() - 0.5) * 1.5;
        Vec3d at = f.cam.add(dir.multiply(dist));
        // Never inside the ground or a wall: stop short of whatever's in the way...
        var hit = f.client.world.raycast(new net.minecraft.world.RaycastContext(f.cam, at,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE,
                f.client.player));
        if (hit.getType() != net.minecraft.util.hit.HitResult.Type.MISS) {
            at = f.cam.add(dir.multiply(Math.max(1.5, hit.getPos().distanceTo(f.cam) - 0.6)));
        }
        // ...and lift it clear of the floor under the text.
        for (int i = 0; i < 6; i++) {
            var below = net.minecraft.util.math.BlockPos.ofFloored(at.x, at.y - 0.35, at.z);
            var here = net.minecraft.util.math.BlockPos.ofFloored(at);
            var w = f.client.world;
            if (!w.getBlockState(below).getCollisionShape(w, below).isEmpty()
                    || !w.getBlockState(here).getCollisionShape(w, here).isEmpty()) {
                at = at.add(0, 0.5, 0);
            } else {
                break;
            }
        }
        return at;
    }

    // The sharpest size the face is rasterised at: smaller sizes blown up in the world look pixelated.
    private static final int FONT_PX = 64;

    private static float ease(float t) {
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }
}
