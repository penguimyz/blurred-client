package dev.blurredclient.mod.module.feature;

import dev.blurredclient.mod.fx.WorldDraw;
import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.ColorSetting;
import dev.blurredclient.mod.setting.ModeSetting;
import dev.blurredclient.mod.setting.SliderSetting;
import dev.blurredclient.mod.ui.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Visual effects drawn as real shapes in the world — glowing rings, light
 * ribbons, impact flashes — rather than vanilla particles. Only you see them,
 * and none of them changes anything about the game.
 *
 * <p>Each effect keeps a short list of what it's showing (a ring where you
 * jumped, a burst where you hit) and draws it every frame through
 * {@link WorldDraw}, so animation runs at your frame rate, not the 20-tick
 * game rate particles move at.
 */
public final class EffectsModules {
    private EffectsModules() {}

    /** Colour modes shared by the effects. */
    public enum Tint {
        ACCENT("Accent"), CUSTOM("Custom"), RAINBOW("Rainbow");

        private final String label;

        Tint(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    static int tint(ModeSetting<Tint> mode, ColorSetting custom, float offset) {
        return switch (mode.get()) {
            case ACCENT -> dev.blurredclient.mod.Theme.chroma(dev.blurredclient.mod.ui.kit.Skins.current().accent(), offset);
            case CUSTOM -> custom.argb();
            case RAINBOW -> Colors.hsv((System.currentTimeMillis() % 4000L) / 4000f + offset, 0.6f, 1f);
        };
    }

    /** {@code argb} with its alpha multiplied by {@code a} (0..1). */
    static int alpha(int argb, float a) {
        int base = argb >>> 24;
        int out = Math.round(base * Math.max(0f, Math.min(1f, a)));
        return (out << 24) | (argb & 0xFFFFFF);
    }

    static float easeOut(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    static double seconds() {
        return System.nanoTime() / 1e9;
    }

    /**
     * A ring of light that spreads across the ground where you jump (and
     * where you land): a bright crisp edge, a soft halo outside it, and a
     * faint glow filling it, all fading as it grows.
     */
    public static final class JumpCircles extends Module implements Ticking, WorldDraw.Layer {
        private record Ring(Vec3d pos, double born, boolean landing) {}

        private static final double LIFE = 0.75;

        private final ModeSetting<Tint> colour;
        private final ColorSetting custom;
        private final BoolSetting onLand;
        private final SliderSetting size;
        private final List<Ring> rings = new ArrayList<>();
        private boolean wasOnGround = true;

        public JumpCircles() {
            super("jump_circles", "Jump circles", "A ring of light spreads across the ground when you jump, and when you land.",
                    Category.EFFECTS, false);
            this.colour = add(new ModeSetting<>("colour", "Colour", "Accent, a colour you pick, or rainbow.", Tint.ACCENT, Tint::label));
            this.custom = add(new ColorSetting("custom", "Custom colour", "Used when Colour is Custom.", 0xFF26B4E6));
            custom.visibleIf(() -> colour.get() == Tint.CUSTOM);
            this.size = add(new SliderSetting("size", "Size", "How wide the ring grows.", 1.1, 0.5, 2.5, 0.1, " blocks"));
            this.onLand = add(new BoolSetting("on_land", "Ring on landing", "Another, smaller ring when you touch down.", true));
            WorldDraw.add(this);
        }

        @Override
        public void tick(MinecraftClient client) {
            ClientPlayerEntity p = client.player;
            if (p == null) {
                return;
            }
            boolean onGround = p.isOnGround();
            boolean jumped = wasOnGround && !onGround && p.getVelocity().y > 0.2;
            boolean landed = !wasOnGround && onGround && onLand.isOn();
            wasOnGround = onGround;
            if (jumped || landed) {
                rings.add(new Ring(new Vec3d(p.getX(), Math.floor(p.getY() * 16) / 16 + 0.015, p.getZ()), seconds(), landed));
            }
        }

        @Override
        public void render(WorldDraw.Frame f) {
            if (rings.isEmpty()) {
                return;
            }
            double now = seconds();
            rings.removeIf(r -> now - r.born() > LIFE || !isActive());
            for (Ring r : rings) {
                float t = (float) ((now - r.born()) / LIFE);
                float e = easeOut(t);
                double reach = size.get() * (r.landing() ? 0.7 : 1.0);
                double radius = 0.18 + reach * e;
                float fade = (float) Math.pow(1 - t, 1.1);
                int c = tint(colour, custom, t * 0.15f);
                // Faint fill, brightening toward the edge.
                f.groundRing(r.pos(), 0, radius * 0.96, alpha(c, 0), alpha(c, 0.22f * fade), 72, true);
                // The crisp edge: colour into a white-hot rim.
                double edge = 0.1 + 0.07 * (1 - t);
                f.groundRing(r.pos(), radius - edge, radius - edge * 0.3, alpha(c, 0.35f * fade), alpha(c, fade), 72, true);
                f.groundRing(r.pos(), radius - edge * 0.3, radius, alpha(c, fade), alpha(0xFFFFFFFF, 0.95f * fade), 72, true);
                // Halo outside it.
                f.groundRing(r.pos(), radius, radius + 0.2 * (1 - t * 0.5), alpha(c, 0.6f * fade), alpha(c, 0), 72, true);
            }
        }
    }

    /**
     * An impact where you hit something: a shockwave ring facing you, a
     * slash of light across the target, or sparks bursting out. Crits are
     * gold and bigger. Purely visual; damage is unchanged.
     */
    public static final class HitEffects extends Module implements WorldDraw.Layer {
        public enum Style {
            SQUARES("Squares"), CUT("Slash cut"), SHOCKWAVE("Shockwave"), SLASH("Slash"), SPARKS("Sparks"), ALL("All");

            private final String label;

            Style(String label) {
                this.label = label;
            }

            public String label() {
                return label;
            }
        }

        /** {@code local} is the hit point relative to the entity's feet, {@code normal} the face it hit. */
        private record Burst(int entityId, Vec3d fallback, Vec3d local, Vec3d normal, double born, boolean crit, long seed) {}

        private static final double LIFE = 0.45;
        private static final int CRIT_GOLD = 0xFFFFC94D;

        private final ModeSetting<Style> style;
        private final ModeSetting<Tint> colour;
        private final ColorSetting custom;
        private final BoolSetting goldCrits;
        private final List<Burst> bursts = new ArrayList<>();
        private final Random random = new Random();

        public HitEffects() {
            super("hit_effects", "Hit effects", "Squares pop off the exact spot you hit, or a shockwave, slash or sparks. Crits are gold.",
                    Category.EFFECTS, false);
            this.style = add(new ModeSetting<>("style", "Style", "What the impact looks like.", Style.SQUARES, Style::label));
            this.colour = add(new ModeSetting<>("colour", "Colour", "Accent, a colour you pick, or rainbow.", Tint.ACCENT, Tint::label));
            this.custom = add(new ColorSetting("custom", "Custom colour", "Used when Colour is Custom.", 0xFFFF5468));
            custom.visibleIf(() -> colour.get() == Tint.CUSTOM);
            this.goldCrits = add(new BoolSetting("gold_crits", "Gold crits", "Critical hits flash gold and hit harder visually.", true));
            WorldDraw.add(this);
            WorldDraw.addLate(this::renderSquares);
        }

        /** Called when you attack an entity (client side, before the attack). */
        public void onHit(Entity target) {
            if (!isActive()) {
                return;
            }
            ClientPlayerEntity p = MinecraftClient.getInstance().player;
            boolean crit = p != null && p.getAttackCooldownProgress(0.5f) > 0.9f && p.fallDistance > 0
                    && !p.isOnGround() && !p.isClimbing() && !p.isTouchingWater() && !p.isSprinting();
            // Where the crosshair meets the hitbox, and which face that is.
            net.minecraft.util.math.Box box = target.getBoundingBox();
            Vec3d hit = box.getCenter();
            Vec3d normal = new Vec3d(0, 1, 0);
            if (p != null) {
                Vec3d eye = p.getEyePos();
                Vec3d end = eye.add(p.getRotationVec(1f).multiply(8));
                hit = box.raycast(eye, end).orElse(null);
                if (hit == null) {
                    hit = box.contains(eye) ? box.getCenter() : closest(box, eye);
                }
                normal = face(box, hit);
            }
            bursts.add(new Burst(target.getId(), hit, hit.subtract(dev.blurredclient.mod.fx.Ent.pos(target)), normal,
                    seconds(), crit, random.nextLong()));
        }

        private static Vec3d closest(net.minecraft.util.math.Box b, Vec3d p) {
            return new Vec3d(Math.max(b.minX, Math.min(b.maxX, p.x)), Math.max(b.minY, Math.min(b.maxY, p.y)),
                    Math.max(b.minZ, Math.min(b.maxZ, p.z)));
        }

        /** The outward normal of the box face nearest {@code p}. */
        private static Vec3d face(net.minecraft.util.math.Box b, Vec3d p) {
            double[] d = {p.x - b.minX, b.maxX - p.x, p.y - b.minY, b.maxY - p.y, p.z - b.minZ, b.maxZ - p.z};
            Vec3d[] n = {new Vec3d(-1, 0, 0), new Vec3d(1, 0, 0), new Vec3d(0, -1, 0), new Vec3d(0, 1, 0),
                    new Vec3d(0, 0, -1), new Vec3d(0, 0, 1)};
            int best = 0;
            for (int i = 1; i < 6; i++) {
                if (d[i] < d[best]) {
                    best = i;
                }
            }
            return n[best];
        }

        @Override
        public void render(WorldDraw.Frame f) {
            if (bursts.isEmpty()) {
                return;
            }
            double now = seconds();
            bursts.removeIf(b -> now - b.born() > LIFE || !isActive());
            Vec3d[] bb = f.billboard();
            for (Burst b : bursts) {
                float t = (float) ((now - b.born()) / LIFE);
                Entity e = f.client.world.getEntityById(b.entityId());
                if (style.get() == Style.SQUARES || style.get() == Style.CUT) {
                    continue;
                }
                Vec3d at = e == null ? b.fallback()
                        : e.getLerpedPos(f.tickDelta).add(0, e.getHeight() * 0.55, 0);
                // Pull the effect a little toward you so it sits in front of the body, not inside it.
                Vec3d toCam = f.cam.subtract(at).normalize();
                at = at.add(toCam.multiply(e == null ? 0.2 : Math.min(0.6, e.getWidth() * 0.6)));
                boolean gold = b.crit() && goldCrits.isOn();
                int c = gold ? CRIT_GOLD : tint(colour, custom, 0f);
                float scale = gold ? 1.35f : 1f;
                Style s = style.get();
                if (s == Style.SHOCKWAVE || s == Style.ALL) {
                    shockwave(f, bb, at, t, c, scale);
                }
                if (s == Style.SLASH || s == Style.ALL) {
                    slash(f, bb, at, t, c, scale, b.seed(), gold);
                }
                if (s == Style.SPARKS || s == Style.ALL) {
                    sparks(f, bb, at, t, c, scale, b.seed());
                }
            }
        }

        /**
         * A square stamped flat on the face you hit, plus little pixel squares
         * knocked off it that tumble and fall.
         */
        private static void squares(WorldDraw.Frame f, Vec3d[] bb, Vec3d at, Vec3d normal, float t, int c, boolean crit, long seed) {
            // Two directions along the face.
            Vec3d u = Math.abs(normal.y) > 0.5 ? new Vec3d(1, 0, 0) : new Vec3d(-normal.z, 0, normal.x);
            Vec3d v = normal.crossProduct(u);
            Vec3d o = at.add(normal.multiply(0.015));
            float e = easeOut(Math.min(1f, t / 0.5f));
            float fade = 1 - t;
            float scale = crit ? 1.3f : 1f;
            // The mark: a white flash that gives way to an outline square growing outwards.
            double half = (0.1 + 0.16 * e) * scale;
            double line = 0.03 * scale * fade + 0.006;
            outline(f, o, u, v, half, line, alpha(c, fade));
            if (t < 0.3f) {
                float k = 1 - t / 0.3f;
                double h = 0.13 * scale * k;
                f.quad(o.add(u.multiply(-h)).add(v.multiply(-h)), o.add(u.multiply(h)).add(v.multiply(-h)),
                        o.add(u.multiply(h)).add(v.multiply(h)), o.add(u.multiply(-h)).add(v.multiply(h)),
                        alpha(0xFFFFFFFF, 0.85f * k), true);
            }
            if (t < 0.6f) {
                // A faint fill inside the outline while it grows.
                float k = 1 - t / 0.6f;
                double h = half - line;
                f.quad(o.add(u.multiply(-h)).add(v.multiply(-h)), o.add(u.multiply(h)).add(v.multiply(-h)),
                        o.add(u.multiply(h)).add(v.multiply(h)), o.add(u.multiply(-h)).add(v.multiply(h)),
                        alpha(c, 0.25f * k), true);
            }
            // Debris: small squares facing you, flying off the face and falling.
            Random r = new Random(seed);
            int n = crit ? 10 : 7;
            double time = t * LIFE;
            for (int i = 0; i < n; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double spread = 0.8 + r.nextDouble() * 1.4;
                Vec3d vel = normal.multiply(1.2 + r.nextDouble() * 1.6)
                        .add(u.multiply(Math.cos(a) * spread)).add(v.multiply(Math.sin(a) * spread)).multiply(scale);
                Vec3d pos = o.add(vel.multiply(time)).add(0, -4.5 * time * time, 0);
                double size = (0.045 + r.nextDouble() * 0.04) * scale * (1 - t * t);
                double spin = r.nextDouble() * 6 + time * (r.nextBoolean() ? 9 : -9);
                Vec3d x = bb[0].multiply(Math.cos(spin)).add(bb[1].multiply(Math.sin(spin))).multiply(size);
                Vec3d y = bb[1].multiply(Math.cos(spin)).subtract(bb[0].multiply(Math.sin(spin))).multiply(size);
                int colour = i % 3 == 0 ? alpha(0xFFFFFFFF, fade) : alpha(c, fade);
                f.quad(pos.subtract(x).subtract(y), pos.add(x).subtract(y), pos.add(x).add(y), pos.subtract(x).add(y), colour, true);
            }
        }

        /** A square outline of half-size {@code h} and line width {@code w} on the plane (u, v). */
        private static void outline(WorldDraw.Frame f, Vec3d o, Vec3d u, Vec3d v, double h, double w, int c) {
            for (int side = 0; side < 4; side++) {
                Vec3d a = side % 2 == 0 ? u : v;
                Vec3d b = side % 2 == 0 ? v : u;
                double sign = side < 2 ? 1 : -1;
                Vec3d mid = o.add(a.multiply(sign * (h - w / 2)));
                Vec3d p0 = mid.add(a.multiply(-w / 2)).add(b.multiply(-h));
                Vec3d p1 = mid.add(a.multiply(w / 2)).add(b.multiply(-h));
                Vec3d p2 = mid.add(a.multiply(w / 2)).add(b.multiply(h));
                Vec3d p3 = mid.add(a.multiply(-w / 2)).add(b.multiply(h));
                f.quad(p0, p1, p2, p3, c, true);
            }
        }

        /** The squares, drawn after the mob and on top of it: models bulge past their hitbox and would hide the mark. */
        private void renderSquares(WorldDraw.Frame f) {
            Style st = style.get();
            if (bursts.isEmpty() || !(st == Style.SQUARES || st == Style.CUT || st == Style.ALL)) {
                return;
            }
            double now = seconds();
            Vec3d[] bb = f.billboard();
            f.overlay(true);
            for (Burst b : bursts) {
                float t = (float) ((now - b.born()) / LIFE);
                if (t >= 1f) {
                    continue;
                }
                Entity e = f.client.world.getEntityById(b.entityId());
                boolean gold = b.crit() && goldCrits.isOn();
                Vec3d spot = e == null ? b.fallback() : e.getLerpedPos(f.tickDelta).add(b.local());
                int c = gold ? CRIT_GOLD : tint(colour, custom, 0f);
                if (st == Style.SQUARES || st == Style.ALL) {
                    squares(f, bb, spot, b.normal(), t, c, gold, b.seed());
                }
                if (st == Style.CUT || st == Style.ALL) {
                    double width = e == null ? 0.8 : Math.max(0.6, e.getWidth());
                    cut(f, bb, spot, t, c, gold, width, b.seed());
                }
            }
            f.overlay(false);
        }

        /**
         * An anime sword cut: a bright line whips across the mob in a blink,
         * thickest in the middle and sharp at the ends, glowing in the
         * colour, then lingers and fades while a few sparks fly off it.
         */
        private static void cut(WorldDraw.Frame f, Vec3d[] bb, Vec3d at, float t, int c, boolean crit, double width, long seed) {
            Random r = new Random(seed * 31 + 7);
            double angle = Math.toRadians((r.nextBoolean() ? 1 : -1) * (25 + r.nextDouble() * 30));
            Vec3d dir = bb[0].multiply(Math.cos(angle)).add(bb[1].multiply(Math.sin(angle)));
            Vec3d side = bb[0].multiply(-Math.sin(angle)).add(bb[1].multiply(Math.cos(angle)));
            double length = (width * 1.4 + 0.7) * (crit ? 1.3 : 1.0);
            float grow = easeOut(Math.min(1f, t / 0.16f));
            float fade = t < 0.3f ? 1f : 1f - (t - 0.3f) / 0.7f;
            Vec3d start = at.subtract(dir.multiply(length / 2));
            Vec3d end = start.add(dir.multiply(length * grow));
            Vec3d mid = start.lerp(end, 0.5);
            double thick = (crit ? 0.07 : 0.05) * (0.4 + 0.6 * fade);
            // Glow, then the white-hot core, each a diamond: sharp ends, full middle.
            diamond(f, start, mid, end, side.multiply(thick * 3.2), alpha(c, 0.35f * fade));
            diamond(f, start, mid, end, side.multiply(thick), alpha(0xFFFFFFFF, fade));
            // Sparks thrown off along the cut.
            int n = crit ? 9 : 6;
            double time = t * LIFE;
            for (int i = 0; i < n; i++) {
                double along = r.nextDouble();
                Vec3d from = start.add(dir.multiply(length * along));
                Vec3d vel = side.multiply((r.nextBoolean() ? 1 : -1) * (0.6 + r.nextDouble())).add(dir.multiply(0.4));
                Vec3d pos = from.add(vel.multiply(time * 2.0)).add(0, -2.5 * time * time, 0);
                double size = 0.025 * (1 - t);
                if (along > grow) {
                    continue;
                }
                f.quad(pos.subtract(bb[0].multiply(size)).subtract(bb[1].multiply(size)),
                        pos.add(bb[0].multiply(size)).subtract(bb[1].multiply(size)),
                        pos.add(bb[0].multiply(size)).add(bb[1].multiply(size)),
                        pos.subtract(bb[0].multiply(size)).add(bb[1].multiply(size)), alpha(i % 2 == 0 ? 0xFFFFFFFF : c, fade), true);
            }
        }

        private static void diamond(WorldDraw.Frame f, Vec3d start, Vec3d mid, Vec3d end, Vec3d half, int colour) {
            f.quad(start, mid.add(half), end, mid.subtract(half), colour, true);
        }

        private static void shockwave(WorldDraw.Frame f, Vec3d[] bb, Vec3d at, float t, int c, float scale) {
            float e = easeOut(t);
            float fade = 1 - t;
            double r = (0.15 + 0.85 * e) * scale;
            double w = (0.09 * fade + 0.012) * scale;
            f.ring(at, bb[0], bb[1], r - w, r, alpha(c, 0), alpha(c, fade), 0, 1, 56, true);
            f.ring(at, bb[0], bb[1], r, r + w * 0.6, alpha(0xFFFFFFFF, fade * 0.9f), alpha(c, 0), 0, 1, 56, true);
            // The flash at the moment of impact.
            if (t < 0.35f) {
                float k = 1 - t / 0.35f;
                f.ring(at, bb[0], bb[1], 0, 0.42 * scale * (0.6 + 0.4 * k), alpha(0xFFFFFFFF, 0.65f * k), alpha(c, 0), 0, 1, 32, true);
            }
        }

        private static void slash(WorldDraw.Frame f, Vec3d[] bb, Vec3d at, float t, int c, float scale, long seed, boolean twin) {
            Random r = new Random(seed);
            int cuts = twin ? 2 : 1;
            for (int i = 0; i < cuts; i++) {
                double tilt = (r.nextDouble() - 0.5) * 1.6 + i * 1.9;
                Vec3d u = bb[0].multiply(Math.cos(tilt)).add(bb[1].multiply(Math.sin(tilt)));
                Vec3d v = bb[1].multiply(Math.cos(tilt)).subtract(bb[0].multiply(Math.sin(tilt)));
                // Sweep across in the first 40%, then thin out and fade.
                float sweep = Math.min(1f, t / 0.4f);
                float fade = t < 0.4f ? 1f : 1 - (t - 0.4f) / 0.6f;
                double radius = 0.75 * scale;
                // The arc around a centre below the target passes through it near 90 degrees.
                double from = 0.07;
                double to = from + 0.36 * easeOut(sweep);
                double thick = 0.11 * scale * fade;
                // Offset the arc's centre so the cut passes through the target.
                Vec3d centre = at.add(v.multiply(-radius * 0.82));
                f.ring(centre, u, v, radius - thick, radius, alpha(c, 0), alpha(c, fade), from, to, 40, true);
                f.ring(centre, u, v, radius - thick * 0.35, radius, alpha(0xFFFFFFFF, 0), alpha(0xFFFFFFFF, fade * 0.9f), from, to, 40, true);
            }
        }

        private static void sparks(WorldDraw.Frame f, Vec3d[] bb, Vec3d at, float t, int c, float scale, long seed) {
            Random r = new Random(seed ^ 0x5DEECE66DL);
            float e = easeOut(t);
            float fade = 1 - t;
            int n = 11;
            for (int i = 0; i < n; i++) {
                double a = (i + r.nextDouble() * 0.7) * Math.PI * 2 / n;
                double speed = (0.55 + r.nextDouble() * 0.6) * scale;
                Vec3d dir = bb[0].multiply(Math.cos(a)).add(bb[1].multiply(Math.sin(a)));
                Vec3d side = bb[0].multiply(-Math.sin(a)).add(bb[1].multiply(Math.cos(a)));
                double head = 0.12 + speed * e;
                double tail = head - (0.28 * (1 - t) + 0.04) * scale;
                double w = 0.028 * scale * fade + 0.004;
                Vec3d h = at.add(dir.multiply(head));
                Vec3d tl = at.add(dir.multiply(Math.max(0.05, tail)));
                f.quad(tl.add(side.multiply(w * 0.3)), h.add(side.multiply(w)), h.subtract(side.multiply(w)),
                        tl.subtract(side.multiply(w * 0.3)),
                        alpha(c, 0), alpha(0xFFFFFFFF, fade), alpha(0xFFFFFFFF, fade), alpha(c, 0), true);
            }
        }
    }

    /**
     * A ribbon of light streaming behind you while you sprint or glide,
     * tapering and fading along its length.
     */
    public static final class Trail extends Module implements WorldDraw.Layer {
        public enum Height {
            FEET("At your feet"), WAIST("At your waist");

            private final String label;

            Height(String label) {
                this.label = label;
            }

            public String label() {
                return label;
            }
        }

        private record Point(Vec3d pos, double time) {}

        private final ModeSetting<Tint> colour;
        private final ColorSetting custom;
        private final BoolSetting onlySprint;
        private final ModeSetting<Height> height;
        private final SliderSetting length;
        private final ArrayDeque<Point> points = new ArrayDeque<>();

        public Trail() {
            super("trail", "Trail", "A ribbon of light streaming behind you while you sprint or fly with an elytra.",
                    Category.EFFECTS, false);
            this.colour = add(new ModeSetting<>("colour", "Colour", "Accent, a colour you pick, or rainbow.", Tint.RAINBOW, Tint::label));
            this.custom = add(new ColorSetting("custom", "Custom colour", "Used when Colour is Custom.", 0xFF26B4E6));
            custom.visibleIf(() -> colour.get() == Tint.CUSTOM);
            this.height = add(new ModeSetting<>("height", "Height", "Where on you the trail starts.", Height.FEET, Height::label));
            this.length = add(new SliderSetting("length", "Length", "How long the trail lingers.", 0.6, 0.2, 2.0, 0.1, "s"));
            this.onlySprint = add(new BoolSetting("only_moving", "Only when sprinting or gliding",
                    "Off draws the trail whenever you're moving.", true));
            WorldDraw.add(this);
        }

        @Override
        public void render(WorldDraw.Frame f) {
            ClientPlayerEntity p = f.client.player;
            double now = seconds();
            double life = length.get();
            if (!isActive() || p == null) {
                points.clear();
                return;
            }
            boolean gliding = gliding(p);
            Vec3d v = p.getVelocity();
            boolean moving = v.horizontalLengthSquared() > 0.003 || gliding;
            boolean emit = moving && (!onlySprint.isOn() || p.isSprinting() || gliding);
            if (emit) {
                double y = gliding ? p.getHeight() * 0.5 : height.get() == Height.FEET ? 0.06 : p.getHeight() * 0.5;
                Vec3d pos = p.getLerpedPos(f.tickDelta).add(0, y, 0);
                Point last = points.peekLast();
                if (last == null || last.pos().squaredDistanceTo(pos) > 0.0025) {
                    points.addLast(new Point(pos, now));
                }
            }
            while (!points.isEmpty() && now - points.peekFirst().time() > life) {
                points.removeFirst();
            }
            if (points.size() < 2) {
                return;
            }
            Iterator<Point> it = points.iterator();
            Point prev = it.next();
            int i = 0;
            int n = points.size();
            while (it.hasNext()) {
                Point cur = it.next();
                i++;
                Vec3d dir = cur.pos().subtract(prev.pos());
                if (dir.lengthSquared() < 1e-6) {
                    prev = cur;
                    continue;
                }
                Vec3d toCam = f.cam.subtract(cur.pos());
                Vec3d side = dir.crossProduct(toCam);
                if (side.lengthSquared() < 1e-8) {
                    prev = cur;
                    continue;
                }
                side = side.normalize();
                float a0 = (float) (1 - (now - prev.time()) / life);
                float a1 = (float) (1 - (now - cur.time()) / life);
                double w0 = 0.3 * a0;
                double w1 = 0.3 * a1;
                int c0 = tint(colour, custom, (float) (i - 1) / n * 0.5f);
                int c1 = tint(colour, custom, (float) i / n * 0.5f);
                Vec3d p0 = prev.pos();
                Vec3d p1 = cur.pos();
                // Soft outer glow fading to nothing at the edges...
                f.quad(p0.add(side.multiply(w0)), p1.add(side.multiply(w1)), p1, p0,
                        alpha(c0, 0), alpha(c1, 0), alpha(c1, a1 * 0.7f), alpha(c0, a0 * 0.7f), true);
                f.quad(p0, p1, p1.subtract(side.multiply(w1)), p0.subtract(side.multiply(w0)),
                        alpha(c0, a0 * 0.7f), alpha(c1, a1 * 0.7f), alpha(c1, 0), alpha(c0, 0), true);
                // ...and a bright core down the middle.
                double k0 = w0 * 0.22;
                double k1 = w1 * 0.22;
                f.quad(p0.add(side.multiply(k0)), p1.add(side.multiply(k1)), p1.subtract(side.multiply(k1)),
                        p0.subtract(side.multiply(k0)), alpha(0xFFFFFFFF, a0 * 0.8f), alpha(0xFFFFFFFF, a1 * 0.8f),
                        alpha(0xFFFFFFFF, a1 * 0.8f), alpha(0xFFFFFFFF, a0 * 0.8f), true);
                prev = cur;
            }
        }
    }

    /**
     * A ring around the feet of what you're fighting — whatever's under your
     * crosshair, or what you last hit, for a few seconds — with a band of
     * light gliding up and down its body. Uses only the crosshair target, so
     * it never shows anything you can't already see.
     */
    public static final class TargetRing extends Module implements WorldDraw.Layer {
        private final ModeSetting<Tint> colour;
        private final ColorSetting custom;
        private final BoolSetting players;
        private int targetId = -1;
        private double lastSeen;
        private float shown;

        public TargetRing() {
            super("target_ring", "Target ring", "A glowing ring and scanning band on whatever you're fighting.",
                    Category.EFFECTS, false);
            this.colour = add(new ModeSetting<>("colour", "Colour", "Accent, a colour you pick, or rainbow.", Tint.ACCENT, Tint::label));
            this.custom = add(new ColorSetting("custom", "Custom colour", "Used when Colour is Custom.", 0xFFFF5468));
            custom.visibleIf(() -> colour.get() == Tint.CUSTOM);
            this.players = add(new BoolSetting("players", "Players too", "Also mark players, not only mobs.", true));
            WorldDraw.addLate(this);
        }

        /** Called when you attack, so the ring stays on what you hit. */
        public void onHit(Entity target) {
            if (target instanceof LivingEntity) {
                targetId = target.getId();
                lastSeen = seconds();
            }
        }

        @Override
        public void render(WorldDraw.Frame f) {
            if (!isActive()) {
                shown = 0;
                return;
            }
            double now = seconds();
            if (f.client.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity le
                    && (players.isOn() || !(le instanceof net.minecraft.entity.player.PlayerEntity))) {
                if (le.getId() != targetId) {
                    shown = 0;
                }
                targetId = le.getId();
                lastSeen = now;
            }
            Entity e = targetId < 0 ? null : f.client.world.getEntityById(targetId);
            boolean live = e instanceof LivingEntity le && le.isAlive() && now - lastSeen < 3;
            shown += ((live ? 1 : 0) - shown) * Math.min(1f, f.dt * 10f);
            if (e == null || shown < 0.02f) {
                return;
            }
            Vec3d feet = e.getLerpedPos(f.tickDelta).add(0, 0.02, 0);
            // Drawn over the mob, so no part of the ring vanishes behind its body.
            // It only ever marks what your crosshair is on (or just hit), so this
            // shows nothing you can't already see.
            f.overlay(true);
            try {
                drawRing(f, e, feet, now);
            } finally {
                f.overlay(false);
            }
        }

        private void drawRing(WorldDraw.Frame f, Entity e, Vec3d feet, double now) {
            double r = e.getWidth() * 0.75 + 0.25;
            int c = tint(colour, custom, 0f);
            float a = shown;
            double spin = now * 0.45;
            // Soft floor glow and four rotating arcs.
            f.groundRing(feet, r * 0.55, r, alpha(c, 0), alpha(c, 0.22f * a), 48, true);
            for (int i = 0; i < 4; i++) {
                double from = spin + i * 0.25;
                f.ring(feet, new Vec3d(1, 0, 0), new Vec3d(0, 0, 1), r - 0.035, r + 0.02,
                        alpha(c, 0.95f * a), alpha(0xFFFFFFFF, 0.8f * a), from, from + 0.16, 16, true);
            }
            // A band of light sweeping up and down the body.
            double h = e.getHeight();
            double y = (Math.sin(now * 2.2) * 0.5 + 0.5) * h;
            Vec3d band = feet.add(0, y, 0);
            double br = e.getWidth() * 0.62 + 0.08;
            f.groundRing(band, br, br + 0.05, alpha(c, 0.9f * a), alpha(c, 0), 48, true);
            f.groundRing(band, br - 0.12, br, alpha(c, 0), alpha(c, 0.25f * a), 48, true);
        }
    }

    /** Elytra gliding: {@code isFallFlying} until 1.21.4 renamed it {@code isGliding} (javap-checked). */
    static boolean gliding(ClientPlayerEntity p) {
        //? if >=1.21.4 {
        return p.isGliding();
        //?} else {
        /*return p.isFallFlying();
        *///?}
    }
}
