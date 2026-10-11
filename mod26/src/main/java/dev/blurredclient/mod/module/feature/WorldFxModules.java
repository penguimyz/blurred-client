package dev.blurredclient.mod.module.feature;

import dev.blurredclient.mod.fx.CombatEvents;
import dev.blurredclient.mod.fx.WorldDraw;
import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.ColorSetting;
import dev.blurredclient.mod.setting.ModeSetting;
import dev.blurredclient.mod.setting.SliderSetting;
import dev.blurredclient.mod.ui.Colors;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static dev.blurredclient.mod.module.feature.EffectsModules.alpha;
import static dev.blurredclient.mod.module.feature.EffectsModules.easeOut;
import static dev.blurredclient.mod.module.feature.EffectsModules.seconds;
import static dev.blurredclient.mod.module.feature.EffectsModules.tint;

/**
 * More effects drawn in the world: deaths, kills, block placing, projectiles,
 * elytra flight and where you died. All only on your screen.
 */
public final class WorldFxModules {
    private WorldFxModules() {}

    /** A small spinning cube flying out and falling, for shatter effects. */
    private static final class Shard {
        Vec3 pos;
        Vec3 vel;
        float rx;
        float ry;
        float spin;
        final float size;
        final int colour;

        Shard(Vec3 pos, Vec3 vel, float size, int colour, Random r) {
            this.pos = pos;
            this.vel = vel;
            this.size = size;
            this.colour = colour;
            this.rx = r.nextFloat() * 360;
            this.ry = r.nextFloat() * 360;
            this.spin = (r.nextFloat() - 0.5f) * 720;
        }

        void step(float dt, double floor) {
            vel = vel.add(0, -9.8 * dt, 0);
            pos = pos.add(vel.scale(dt));
            if (pos.y < floor) {
                pos = new Vec3(pos.x, floor, pos.z);
                vel = new Vec3(vel.x * 0.6, -vel.y * 0.35, vel.z * 0.6);
                spin *= 0.6f;
            }
            rx += spin * dt;
            ry += spin * 0.7f * dt;
        }

        void draw(WorldDraw.Frame f, float fade) {
            f.pushAt(pos);
            f.matrices.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rx));
            f.matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(ry));
            float h = size / 2;
            f.box(-h, -h, -h, h, h, h, alpha(colour, fade));
            f.pop();
        }
    }

    // ------------------------------------------------------------------

    /**
     * Where things die: a soul rising out of the body, a shockwave across the
     * ground, or the body shattering into tumbling cubes.
     */
    public static final class DeathEffects extends Module implements WorldDraw.Layer {
        public enum Style {
            SOUL("Soul"), SHOCKWAVE("Shockwave"), SHATTER("Shatter");

            private final String label;

            Style(String label) {
                this.label = label;
            }

            public String label() {
                return label;
            }
        }

        public enum Who {
            EVERYTHING("Everything"), PLAYERS("Players"), MOBS("Mobs");

            private final String label;

            Who(String label) {
                this.label = label;
            }

            public String label() {
                return label;
            }
        }

        private static final class Effect {
            Vec3 pos;
            double floor;
            double born;
            float height;
            final List<Shard> shards = new ArrayList<>();
        }

        private final ModeSetting<Style> style;
        private final ModeSetting<Who> who;
        private final ModeSetting<EffectsModules.Tint> colour;
        private final ColorSetting custom;
        private final List<Effect> effects = new ArrayList<>();
        private final Random random = new Random();

        public DeathEffects() {
            super("death_effects", "Death effects", "Where something dies: a rising soul, a shockwave, or it shatters into cubes.",
                    Category.EFFECTS, false);
            this.style = add(new ModeSetting<>("style", "Style", "What a death looks like.", Style.SOUL, Style::label));
            this.who = add(new ModeSetting<>("who", "For", "Whose deaths get the effect.", Who.EVERYTHING, Who::label));
            this.colour = add(new ModeSetting<>("colour", "Colour", "Accent, a colour you pick, or rainbow.", EffectsModules.Tint.ACCENT, EffectsModules.Tint::label));
            this.custom = add(new ColorSetting("custom", "Custom colour", "Used when Colour is Custom.", 0xFFB98CFF));
            custom.visibleIf(() -> colour.get() == EffectsModules.Tint.CUSTOM);
            WorldDraw.add(this);
            CombatEvents.onDeath(d -> {
                if (!isActive()) {
                    return;
                }
                boolean player = d.entity() instanceof Player;
                if (who.get() == Who.PLAYERS && !player || who.get() == Who.MOBS && player) {
                    return;
                }
                Effect e = new Effect();
                e.pos = d.pos();
                e.floor = d.entity().getY();
                e.height = d.entity().getBbHeight();
                e.born = seconds();
                if (style.get() == Style.SHATTER) {
                    int c = tint(colour, custom, 0f);
                    for (int i = 0; i < 18; i++) {
                        Vec3 v = new Vec3(random.nextGaussian() * 1.6, 2 + random.nextDouble() * 3, random.nextGaussian() * 1.6);
                        Vec3 p = d.pos().add((random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * e.height, (random.nextDouble() - 0.5) * 0.6);
                        e.shards.add(new Shard(p, v, 0.08f + random.nextFloat() * 0.1f,
                                Colors.lerp(c, 0xFFFFFFFF, random.nextFloat() * 0.4f), random));
                    }
                }
                effects.add(e);
            });
        }

        @Override
        public void render(WorldDraw.Frame f) {
            if (effects.isEmpty()) {
                return;
            }
            double now = seconds();
            effects.removeIf(e -> now - e.born > 2.2 || !isActive());
            int c = tint(colour, custom, 0f);
            for (Effect e : effects) {
                float t = (float) ((now - e.born) / 2.2);
                switch (style.get()) {
                    case SOUL -> soul(f, e, t, c, now);
                    case SHOCKWAVE -> shockwave(f, e, t, c);
                    case SHATTER -> {
                        for (Shard s : e.shards) {
                            s.step(f.dt, e.floor + s.size / 2);
                            s.draw(f, t < 0.7f ? 1f : 1f - (t - 0.7f) / 0.3f);
                        }
                    }
                }
            }
        }

        private static void soul(WorldDraw.Frame f, Effect e, float t, int c, double now) {
            // A glowing orb rising and swaying, with a fading tail and a faint pillar.
            float rise = easeOut(Math.min(1f, t * 1.2f)) * 2.6f;
            float fade = t < 0.6f ? 1f : 1f - (t - 0.6f) / 0.4f;
            Vec3[] bb = f.billboard();
            Vec3 at = e.pos.add(Math.sin(now * 3 + e.born) * 0.15 * t, rise, Math.cos(now * 2.6 + e.born) * 0.15 * t);
            f.ring(at, bb[0], bb[1], 0, 0.22, alpha(0xFFFFFFFF, fade), alpha(c, 0.8f * fade), 0, 1, 24, true);
            f.ring(at, bb[0], bb[1], 0.22, 0.5, alpha(c, 0.5f * fade), alpha(c, 0), 0, 1, 24, true);
            for (int i = 1; i <= 6; i++) {
                Vec3 tail = at.add(0, -i * 0.12, 0);
                float k = (1 - i / 7f) * fade;
                f.ring(tail, bb[0], bb[1], 0, 0.16 * (1 - i / 8f), alpha(c, 0.5f * k), alpha(c, 0), 0, 1, 16, true);
            }
            f.groundRing(new Vec3(e.pos.x, e.floor + 0.02, e.pos.z), 0.2 + t * 1.2, 0.3 + t * 1.25,
                    alpha(c, 0.7f * fade), alpha(c, 0), 48, true);
        }

        private static void shockwave(WorldDraw.Frame f, Effect e, float t, int c) {
            float k = easeOut(Math.min(1f, t * 1.6f));
            float fade = 1 - t;
            Vec3 ground = new Vec3(e.pos.x, e.floor + 0.03, e.pos.z);
            f.groundRing(ground, k * 3.2, k * 3.2 + 0.3, alpha(c, 0), alpha(0xFFFFFFFF, fade), 64, true);
            f.groundRing(ground, k * 3.2 + 0.3, k * 3.2 + 0.7, alpha(c, 0.8f * fade), alpha(c, 0), 64, true);
            // A column of light that collapses as the wave spreads.
            Vec3[] bb = f.billboard();
            double w = 0.35 * (1 - k);
            double h = e.height + 3 * (1 - k);
            Vec3 side = bb[0].scale(w);
            f.quad(ground.subtract(side), ground.add(side), ground.add(side).add(0, h, 0), ground.subtract(side).add(0, h, 0),
                    alpha(c, 0.9f * fade), alpha(c, 0.9f * fade), alpha(c, 0), alpha(c, 0), true);
        }
    }

    // ------------------------------------------------------------------

    /** When you finish something off: a lightning strike, a burst of coins flying to you, or a nova. */
    public static final class KillEffects extends Module implements WorldDraw.Layer {
        public enum Style {
            LIGHTNING("Lightning"), COINS("Coins"), NOVA("Nova");

            private final String label;

            Style(String label) {
                this.label = label;
            }

            public String label() {
                return label;
            }
        }

        private static final class Kill {
            Vec3 pos;
            double floor;
            double born;
            long seed;
            final List<Vec3> coins = new ArrayList<>();
            final List<Vec3> coinVel = new ArrayList<>();
        }

        private final ModeSetting<Style> style;
        private final ModeSetting<EffectsModules.Tint> colour;
        private final ColorSetting custom;
        private final BoolSetting sound;
        private final List<Kill> kills = new ArrayList<>();
        private final Random random = new Random();

        public KillEffects() {
            super("kill_effects", "Kill effects", "When you get a kill: a lightning strike, a burst of coins, or a nova.",
                    Category.EFFECTS, false);
            this.style = add(new ModeSetting<>("style", "Style", "What a kill looks like.", Style.LIGHTNING, Style::label));
            this.colour = add(new ModeSetting<>("colour", "Colour", "Accent, a colour you pick, or rainbow.", EffectsModules.Tint.ACCENT, EffectsModules.Tint::label));
            this.custom = add(new ColorSetting("custom", "Custom colour", "Used when Colour is Custom.", 0xFF9FE8FF));
            custom.visibleIf(() -> colour.get() == EffectsModules.Tint.CUSTOM);
            this.sound = add(new BoolSetting("sound", "Thunder sound", "A crack of thunder with the lightning.", true));
            WorldDraw.add(this);
            CombatEvents.onDeath(d -> {
                if (!isActive() || !d.byYou()) {
                    return;
                }
                Kill k = new Kill();
                k.pos = d.pos();
                k.floor = d.entity().getY();
                k.born = seconds();
                k.seed = random.nextLong();
                if (style.get() == Style.COINS) {
                    for (int i = 0; i < 14; i++) {
                        k.coins.add(d.pos());
                        k.coinVel.add(new Vec3(random.nextGaussian() * 2, 3 + random.nextDouble() * 2.5, random.nextGaussian() * 2));
                    }
                }
                if (style.get() == Style.LIGHTNING && sound.isOn()) {
                    dev.blurredclient.mod.fx.Sfx.play(net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER, 1.3f, 0.35f);
                }
                kills.add(k);
            });
        }

        @Override
        public void render(WorldDraw.Frame f) {
            if (kills.isEmpty()) {
                return;
            }
            double now = seconds();
            kills.removeIf(k -> now - k.born > 1.6 || !isActive());
            int c = tint(colour, custom, 0f);
            for (Kill k : kills) {
                float t = (float) ((now - k.born) / 1.6);
                switch (style.get()) {
                    case LIGHTNING -> lightning(f, k, t, c);
                    case COINS -> coins(f, k, t);
                    case NOVA -> nova(f, k, t, c);
                }
            }
        }

        private static void lightning(WorldDraw.Frame f, Kill k, float t, int c) {
            // A jagged bolt from the sky, flickering, then fading.
            if (t > 0.5f) {
                return;
            }
            float flicker = (int) (t * 30) % 3 == 1 ? 0.4f : 1f;
            float fade = (1 - t / 0.5f) * flicker;
            Random r = new Random(k.seed);
            Vec3[] bb = f.billboard();
            bolt(f, bb, new Vec3(k.pos.x, k.floor, k.pos.z), r, 22, 0.75, 0.05, c, fade, true);
            f.groundRing(new Vec3(k.pos.x, k.floor + 0.03, k.pos.z), 0, 1.4, alpha(0xFFFFFFFF, fade), alpha(c, 0), 40, true);
        }

        /** A jagged bolt climbing from {@code start}; the main one forks a few times. */
        private static void bolt(WorldDraw.Frame f, Vec3[] bb, Vec3 start, Random r, int steps, double step,
                                 double width, int c, float fade, boolean forks) {
            Vec3 prev = start;
            double drift = 0;
            for (int i = 1; i <= steps; i++) {
                drift = drift * 0.6 + r.nextGaussian() * 0.45;
                Vec3 next = prev.add(r.nextGaussian() * 0.3 + drift * 0.3, step * (0.7 + r.nextDouble() * 0.6), r.nextGaussian() * 0.3);
                Vec3 dir = next.subtract(prev);
                Vec3 side = dir.cross(f.cam.subtract(prev)).normalize();
                Vec3 core = side.scale(width);
                Vec3 glow = side.scale(width * 5);
                f.quad(prev.subtract(glow), prev.add(glow), next.add(glow), next.subtract(glow),
                        alpha(c, 0.22f * fade), alpha(c, 0.22f * fade), alpha(c, 0.22f * fade), alpha(c, 0.22f * fade), true);
                f.quad(prev.subtract(core), prev.add(core), next.add(core), next.subtract(core),
                        alpha(0xFFFFFFFF, fade), alpha(0xFFFFFFFF, fade), alpha(0xFFFFFFFF, fade), alpha(0xFFFFFFFF, fade), true);
                if (forks && i % 6 == 3 && r.nextBoolean()) {
                    bolt(f, bb, next, r, 5, step * 0.6, width * 0.6, c, fade * 0.8f, false);
                }
                prev = next;
            }
        }

        private static void coins(WorldDraw.Frame f, Kill k, float t) {
            LocalPlayer p = f.client.player;
            Vec3 to = p == null ? k.pos : p.getPosition(f.tickDelta).add(0, 1, 0);
            for (int i = 0; i < k.coins.size(); i++) {
                Vec3 pos = k.coins.get(i);
                Vec3 vel = k.coinVel.get(i);
                if (t < 0.35f) {
                    // Burst out and up...
                    vel = vel.add(0, -9.8 * f.dt, 0);
                    pos = pos.add(vel.scale(f.dt));
                } else {
                    // ...then fly to you like experience.
                    Vec3 d = to.subtract(pos);
                    pos = pos.add(d.scale(Math.min(1, f.dt * (4 + i * 0.3))));
                }
                k.coins.set(i, pos);
                k.coinVel.set(i, vel);
                if (pos.distanceToSqr(to) < 0.1) {
                    continue;
                }
                f.pushAt(pos);
                f.matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float) (seconds() * 360 + i * 40)));
                f.box(-0.09f, -0.09f, -0.02f, 0.09f, 0.09f, 0.02f, 0xFFFFC94D);
                f.box(-0.06f, -0.06f, -0.025f, 0.06f, 0.06f, 0.025f, 0xFFFFE08A);
                f.pop();
            }
        }

        private static void nova(WorldDraw.Frame f, Kill k, float t, int c) {
            float e = easeOut(t);
            float fade = 1 - t;
            Vec3[] bb = f.billboard();
            f.ring(k.pos, bb[0], bb[1], e * 2.4, e * 2.4 + 0.25, alpha(0xFFFFFFFF, fade), alpha(c, 0), 0, 1, 64, true);
            f.ring(k.pos, bb[0], bb[1], 0, 0.9 * (1 - e), alpha(0xFFFFFFFF, fade), alpha(c, 0), 0, 1, 32, true);
            f.ring(k.pos, new Vec3(1, 0, 0), new Vec3(0, 0, 1), e * 3, e * 3 + 0.2, alpha(c, fade), alpha(c, 0), 0, 1, 64, true);
        }
    }

    // ------------------------------------------------------------------

    /**
     * Blocks you place snap in with a glowing frame that shrinks onto them and a
     * flash; blocks you break burst into small cubes of their colour.
     */
    public static final class PlaceAnimation extends Module implements WorldDraw.Layer {
        private record Placed(BlockPos pos, double born, boolean broke, int colour, List<Shard> shards) {}

        private final BoolSetting breaking;
        private final SliderSetting duration;
        private final List<Placed> list = new ArrayList<>();
        private final Random random = new Random();

        public PlaceAnimation() {
            super("place_animation", "Place animation", "Placed blocks snap in with a glowing frame; broken ones burst into cubes.",
                    Category.EFFECTS, false);
            this.breaking = add(new BoolSetting("breaking", "Animate breaking", "Broken blocks burst apart too.", true));
            this.duration = add(new SliderSetting("duration", "Duration", "How long the animation takes.", 0.3, 0.1, 1.0, 0.05, "s"));
            WorldDraw.add(this);
        }

        /** Called when a block in your world changes. */
        public void changed(BlockPos pos, BlockState before, BlockState after) {
            Minecraft client = Minecraft.getInstance();
            if (!isActive() || client.player == null || client.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > 49) {
                return;
            }
            if (before.isAir() && !after.isAir()) {
                list.add(new Placed(pos.immutable(), seconds(), false, colourOf(after, pos), null));
            } else if (!before.isAir() && after.isAir() && breaking.isOn()) {
                int c = colourOf(before, pos);
                List<Shard> shards = new ArrayList<>();
                for (int i = 0; i < 10; i++) {
                    Vec3 p = Vec3.atCenterOf(pos).add((random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.6);
                    shards.add(new Shard(p, new Vec3(random.nextGaussian(), 1.5 + random.nextDouble() * 2, random.nextGaussian()),
                            0.1f + random.nextFloat() * 0.08f, Colors.lerp(c, 0xFF000000, random.nextFloat() * 0.3f), random));
                }
                list.add(new Placed(pos.immutable(), seconds(), true, c, shards));
            }
        }

        private static int colourOf(BlockState state, BlockPos pos) {
            Minecraft client = Minecraft.getInstance();
            int rgb = state.getMapColor(client.level, pos).col;
            return 0xFF000000 | (rgb == 0 ? 0x9AA4B4 : rgb);
        }

        @Override
        public void render(WorldDraw.Frame f) {
            if (list.isEmpty()) {
                return;
            }
            double now = seconds();
            double life = duration.get();
            list.removeIf(p -> now - p.born() > (p.broke() ? 1.4 : life) || !isActive());
            int accent = dev.blurredclient.mod.Theme.chroma(dev.blurredclient.mod.ui.kit.Skins.current().accent());
            for (Placed p : list) {
                if (p.broke()) {
                    float t = (float) ((now - p.born()) / 1.4);
                    for (Shard s : p.shards()) {
                        s.step(f.dt, p.pos().getY() + s.size / 2);
                        s.draw(f, t < 0.6f ? 1f : 1f - (t - 0.6f) / 0.4f);
                    }
                    continue;
                }
                float t = (float) ((now - p.born()) / life);
                float k = easeOut(t);
                double grow = 0.5 + 0.18 * (1 - k);
                Vec3 c = Vec3.atCenterOf(p.pos());
                int edge = alpha(accent, 1 - t);
                frame(f, c, grow, 0.03, edge);
                // A flash filling the block as it lands.
                if (t < 0.5f) {
                    float a = (0.5f - t) * 0.8f;
                    f.pushAt(c);
                    float h = 0.505f;
                    f.box(-h, -h, -h, h, h, h, alpha(0xFFFFFFFF, a));
                    f.pop();
                }
            }
        }

        /** The twelve edges of a cube of half-size {@code h} around {@code c}, as thin bars. */
        static void frame(WorldDraw.Frame f, Vec3 c, double h, double th, int colour) {
            f.pushAt(c);
            float a = (float) h;
            float w = (float) th;
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    f.box(-a, sy * a - w, sx * a - w, a, sy * a + w, sx * a + w, colour);
                    f.box(sx * a - w, -a, sy * a - w, sx * a + w, a, sy * a + w, colour);
                    f.box(sx * a - w, sy * a - w, -a, sx * a + w, sy * a + w, a, colour);
                }
            }
            f.pop();
        }
    }

    // ------------------------------------------------------------------

    /** A see-through box where the block you're holding would be placed. */
    public static final class PlacePreview extends Module implements WorldDraw.Layer {
        private final SliderSetting opacity;
        private final BoolSetting edges;
        private Vec3 shown;

        public PlacePreview() {
            super("place_preview", "Place preview", "A see-through box showing where the block in your hand will go.",
                    Category.VISUAL, false);
            this.opacity = add(new SliderSetting("opacity", "Opacity", "How solid the preview is.", 0.25, 0.05, 0.8, 0.05, ""));
            this.edges = add(new BoolSetting("edges", "Edges", "Outline the box.", true));
            WorldDraw.add(this);
        }

        @Override
        public void render(WorldDraw.Frame f) {
            Minecraft client = f.client;
            LocalPlayer p = client.player;
            if (!isActive() || p == null || !(p.getMainHandItem().getItem() instanceof BlockItem item)
                    || !(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                    || client.level == null) {
                shown = null;
                return;
            }
            BlockPos target = hit.getBlockPos();
            if (!client.level.getBlockState(target).canBeReplaced()) {
                target = target.relative(hit.getDirection());
            }
            if (!client.level.getBlockState(target).canBeReplaced()) {
                shown = null;
                return;
            }
            Vec3 goal = Vec3.atCenterOf(target);
            // Glide between spots instead of jumping.
            shown = shown == null || shown.distanceToSqr(goal) > 9 ? goal : shown.lerp(goal, Math.min(1, f.dt * 20));
            int rgb = item.getBlock().defaultBlockState().getMapColor(client.level, target).col;
            int c = 0xFF000000 | (rgb == 0 ? 0xC8D2E6 : rgb);
            f.pushAt(shown);
            float h = 0.501f;
            f.box(-h, -h, -h, h, h, h, alpha(Colors.lerp(c, 0xFFFFFFFF, 0.35f), opacity.floatValue()));
            f.pop();
            if (edges.isOn()) {
                PlaceAnimation.frame(f, shown, 0.502, 0.012, alpha(0xFFFFFFFF, 0.8f));
            }
        }
    }

    // ------------------------------------------------------------------

    /** Ribbons of light behind arrows, pearls and other thrown things, with a burst where they land. */
    public static final class ProjectileTrails extends Module implements WorldDraw.Layer {
        private static final class Track {
            final ArrayDeque<double[]> points = new ArrayDeque<>();
            int colour;
            boolean seen;
        }

        private final SliderSetting length;
        private final BoolSetting impact;
        private final ModeSetting<EffectsModules.Tint> colour;
        private final ColorSetting custom;
        private final Map<Integer, Track> tracks = new HashMap<>();
        private final List<double[]> bursts = new ArrayList<>();

        public ProjectileTrails() {
            super("projectile_trails", "Projectile trails", "Ribbons of light behind arrows, pearls and other thrown things.",
                    Category.EFFECTS, false);
            this.length = add(new SliderSetting("length", "Length", "How long the trail lasts.", 0.5, 0.15, 2.0, 0.05, "s"));
            this.impact = add(new BoolSetting("impact", "Impact burst", "A ring where they land.", true));
            this.colour = add(new ModeSetting<>("colour", "Colour", "Accent, a colour you pick, or rainbow.", EffectsModules.Tint.ACCENT, EffectsModules.Tint::label));
            this.custom = add(new ColorSetting("custom", "Custom colour", "Used when Colour is Custom.", 0xFFFFFFFF));
            custom.visibleIf(() -> colour.get() == EffectsModules.Tint.CUSTOM);
            WorldDraw.add(this);
        }

        @Override
        public void render(WorldDraw.Frame f) {
            if (!isActive() || f.client.level == null) {
                tracks.clear();
                bursts.clear();
                return;
            }
            double now = seconds();
            double life = length.get();
            for (Track t : tracks.values()) {
                t.seen = false;
            }
            for (Entity e : f.client.level.entitiesForRendering()) {
                if (!(e instanceof Projectile) || e.distanceToSqr(f.cam) > 96 * 96) {
                    continue;
                }
                Track t = tracks.computeIfAbsent(e.getId(), id -> new Track());
                t.seen = true;
                t.colour = e instanceof ThrownEnderpearl ? 0xFFB06BFF : tint(colour, custom, 0f);
                Vec3 p = e.getPosition(f.tickDelta).add(0, e.getBbHeight() / 2, 0);
                double[] last = t.points.peekLast();
                if (last == null || Math.abs(last[0] - p.x) + Math.abs(last[1] - p.y) + Math.abs(last[2] - p.z) > 0.03) {
                    t.points.addLast(new double[] {p.x, p.y, p.z, now});
                } else if (e.getDeltaMovement().lengthSqr() < 1e-4) {
                    // Stuck in the ground (an arrow): let the trail fade out.
                    t.seen = false;
                }
            }
            Iterator<Map.Entry<Integer, Track>> it = tracks.entrySet().iterator();
            while (it.hasNext()) {
                Track t = it.next().getValue();
                while (!t.points.isEmpty() && now - t.points.peekFirst()[3] > life) {
                    t.points.removeFirst();
                }
                if (!t.seen && t.points.isEmpty()) {
                    it.remove();
                    continue;
                }
                if (!t.seen && impact.isOn() && t.points.size() > 1 && now - t.points.peekLast()[3] < 0.06) {
                    double[] p = t.points.peekLast();
                    bursts.add(new double[] {p[0], p[1], p[2], now, t.colour});
                }
                ribbon(f, t, now, life);
            }
            bursts.removeIf(b -> now - b[3] > 0.5);
            for (double[] b : bursts) {
                float k = (float) ((now - b[3]) / 0.5);
                Vec3[] bb = f.billboard();
                int c = (int) b[4];
                f.ring(new Vec3(b[0], b[1], b[2]), bb[0], bb[1], easeOut(k) * 0.9, easeOut(k) * 0.9 + 0.12,
                        alpha(0xFFFFFFFF, 1 - k), alpha(c, 0), 0, 1, 32, true);
            }
        }

        private static void ribbon(WorldDraw.Frame f, Track t, double now, double life) {
            double[] prev = null;
            for (double[] p : t.points) {
                if (prev != null) {
                    Vec3 a = new Vec3(prev[0], prev[1], prev[2]);
                    Vec3 b = new Vec3(p[0], p[1], p[2]);
                    Vec3 side = b.subtract(a).cross(f.cam.subtract(b));
                    if (side.lengthSqr() > 1e-8) {
                        side = side.normalize();
                        float a0 = (float) (1 - (now - prev[3]) / life);
                        float a1 = (float) (1 - (now - p[3]) / life);
                        double w0 = 0.08 * a0;
                        double w1 = 0.08 * a1;
                        f.quad(a.add(side.scale(w0)), b.add(side.scale(w1)), b.subtract(side.scale(w1)), a.subtract(side.scale(w0)),
                                alpha(t.colour, a0 * 0.8f), alpha(t.colour, a1 * 0.8f), alpha(t.colour, a1 * 0.8f), alpha(t.colour, a0 * 0.8f), true);
                    }
                }
                prev = p;
            }
        }
    }

    // ------------------------------------------------------------------

    /** Vapour trails streaming from both wingtips while you fly an elytra, thicker the faster you go. */
    public static final class Contrails extends Module implements WorldDraw.Layer {
        private final SliderSetting length;
        private final ModeSetting<EffectsModules.Tint> colour;
        private final ColorSetting custom;
        private final ArrayDeque<double[]> left = new ArrayDeque<>();
        private final ArrayDeque<double[]> right = new ArrayDeque<>();

        public Contrails() {
            super("contrails", "Elytra trails", "Vapour trails from your wingtips while flying, thicker at speed.",
                    Category.EFFECTS, false);
            this.length = add(new SliderSetting("length", "Length", "How long they linger.", 1.2, 0.3, 4.0, 0.1, "s"));
            this.colour = add(new ModeSetting<>("colour", "Colour", "Accent, a colour you pick, or rainbow.", EffectsModules.Tint.CUSTOM, EffectsModules.Tint::label));
            this.custom = add(new ColorSetting("custom", "Custom colour", "Used when Colour is Custom.", 0xDDFFFFFF));
            custom.visibleIf(() -> colour.get() == EffectsModules.Tint.CUSTOM);
            WorldDraw.add(this);
        }

        @Override
        public void render(WorldDraw.Frame f) {
            LocalPlayer p = f.client.player;
            double now = seconds();
            double life = length.get();
            if (!isActive() || p == null) {
                left.clear();
                right.clear();
                return;
            }
            if (EffectsModules.gliding(p)) {
                double yaw = Math.toRadians(p.getYRot());
                Vec3 side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw)).scale(0.75);
                Vec3 centre = p.getPosition(f.tickDelta).add(0, 0.4, 0);
                double speed = p.getDeltaMovement().length();
                add(left, centre.subtract(side), now, speed);
                add(right, centre.add(side), now, speed);
            }
            int c = tint(colour, custom, 0f);
            draw(f, left, now, life, c);
            draw(f, right, now, life, c);
        }

        private static void add(ArrayDeque<double[]> q, Vec3 p, double now, double speed) {
            double[] last = q.peekLast();
            if (last == null || Math.abs(last[0] - p.x) + Math.abs(last[1] - p.y) + Math.abs(last[2] - p.z) > 0.1) {
                q.addLast(new double[] {p.x, p.y, p.z, now, speed});
            }
        }

        private static void draw(WorldDraw.Frame f, ArrayDeque<double[]> q, double now, double life, int c) {
            while (!q.isEmpty() && now - q.peekFirst()[3] > life) {
                q.removeFirst();
            }
            double[] prev = null;
            for (double[] p : q) {
                if (prev != null) {
                    Vec3 a = new Vec3(prev[0], prev[1], prev[2]);
                    Vec3 b = new Vec3(p[0], p[1], p[2]);
                    Vec3 side = b.subtract(a).cross(f.cam.subtract(b));
                    if (side.lengthSqr() > 1e-8) {
                        side = side.normalize();
                        float a0 = (float) (1 - (now - prev[3]) / life);
                        float a1 = (float) (1 - (now - p[3]) / life);
                        // Spreads out as it ages, like real vapour.
                        double w0 = (0.05 + 0.25 * (1 - a0)) * Math.min(1.5, 0.5 + prev[4]);
                        double w1 = (0.05 + 0.25 * (1 - a1)) * Math.min(1.5, 0.5 + p[4]);
                        f.quad(a.add(side.scale(w0)), b.add(side.scale(w1)), b.subtract(side.scale(w1)), a.subtract(side.scale(w0)),
                                alpha(c, a0 * 0.55f), alpha(c, a1 * 0.55f), alpha(c, a1 * 0.55f), alpha(c, a0 * 0.55f), true);
                    }
                }
                prev = p;
            }
        }
    }

    // ------------------------------------------------------------------

    /**
     * Where you last died: a beam of light and a distance tag, so you can get
     * your things back. Cleared when you reach it.
     */
    public static final class DeathWaypoints extends Module implements WorldDraw.Layer {
        private final BoolSetting chat;
        private Vec3 spot;
        private String dimension;
        private double when;

        public DeathWaypoints() {
            super("death_waypoints", "Death waypoints", "A beam and distance tag where you died, so you can find your things.",
                    Category.UTILITY, false);
            this.chat = add(new BoolSetting("chat", "Chat message", "Post your death coordinates in chat (only you see it).", true));
            WorldDraw.add(this);
            CombatEvents.onDeath(d -> {
                Minecraft client = Minecraft.getInstance();
                if (!isActive() || d.entity() != client.player || client.level == null) {
                    return;
                }
                spot = dev.blurredclient.mod.fx.Ent.pos(d.entity());
                dimension = client.level.dimension().identifier().toString();
                when = seconds();
                if (chat.isOn()) {
                    client.gui.hud.getChat().addClientSystemMessage(Component.literal(String.format(
                            "§bBlurred§7 » You died at §f%d %d %d", (int) spot.x, (int) spot.y, (int) spot.z)));
                }
            });
        }

        @Override
        public void render(WorldDraw.Frame f) {
            if (!isActive() || spot == null || f.client.level == null || f.client.player == null) {
                return;
            }
            if (!f.client.level.dimension().identifier().toString().equals(dimension)) {
                return;
            }
            double dist = dev.blurredclient.mod.fx.Ent.pos(f.client.player).distanceTo(spot);
            if (dist < 2.5 && seconds() - when > 5) {
                spot = null;
                return;
            }
            int accent = 0xFFFF5468;
            Vec3[] bb = f.billboard();
            Vec3 side = bb[0].scale(0.18);
            Vec3 base = spot;
            Vec3 top = spot.add(0, 60, 0);
            double pulse = 0.7 + 0.3 * Math.sin(seconds() * 3);
            f.quad(base.subtract(side), base.add(side), top.add(side), top.subtract(side),
                    alpha(accent, (float) (0.7 * pulse)), alpha(accent, (float) (0.7 * pulse)), alpha(accent, 0), alpha(accent, 0), true);
            f.groundRing(base.add(0, 0.05, 0), 0.4, 0.55, alpha(accent, 0.9f), alpha(accent, 0), 40, true);
            // The tag stays readable from far away: scale with distance.
            Vec3 tagAt = f.cam.add(spot.add(0, 2.2, 0).subtract(f.cam).normalize().scale(Math.min(dist, 12)));
            float scale = 0.025f * (float) Math.min(dist, 12) / 6f;
            f.overlay(true);
            f.billboardText(tagAt, Component.literal("Death  " + Math.round(dist) + "m"), Math.max(0.012f, scale), 0xFFFFFFFF, 0xA0101018);
            f.overlay(false);
        }
    }
}
