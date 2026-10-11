package dev.blurredclient.mod.module.feature;

import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.ModeSetting;
import dev.blurredclient.mod.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Frame-rate helpers. Each one only changes what <em>your</em> client draws
 * or how often it draws it — never the game state.
 */
public final class PerformanceModules {
    private PerformanceModules() {}

    /**
     * Adjust the frame-rate limit Minecraft is about to apply. Called from
     * the menu/inactivity limiter (one mixin, branched per version).
     *
     * <ul>
     *   <li>Vanilla holds every screen at 60 FPS when no world is loaded, which
     *       is why menu animations looked choppier than the rest of the game.
     *       With "Smooth menus" on, menus use your normal max FPS.</li>
     *   <li>Dynamic FPS caps the rate while the window is in the background.</li>
     * </ul>
     */
    public static int adjustFramerate(int limit) {
        MinecraftClient client = MinecraftClient.getInstance();
        int result = limit;
        if (limit == 60 && client.world == null && Modules.APPEARANCE.smoothMenus()) {
            result = client.options.getMaxFps().getValue();
        }
        if (Modules.DYNAMIC_FPS.isActive() && !client.isWindowFocused()) {
            result = Math.min(result, Modules.DYNAMIC_FPS.limit());
        }
        return result;
    }

    /** Lower the frame rate while the game isn't the focused window. */
    public static final class DynamicFps extends Module {
        private final SliderSetting limit;

        public DynamicFps() {
            super("dynamic_fps", "Dynamic FPS", "Cap the frame rate while Minecraft is in the background, to save power and heat.",
                    Category.PERFORMANCE, true);
            this.limit = add(new SliderSetting("limit", "Background FPS", "Frame rate while the window isn't focused.",
                    15, 1, 60, 1, " fps"));
        }

        public int limit() {
            return limit.intValue();
        }
    }

    /** Draw fewer particles. Blurred's own effects are never culled. */
    public static final class ParticleLimiter extends Module {
        private final SliderSetting keep;

        public ParticleLimiter() {
            super("particle_limiter", "Particle limiter", "Spawn only a share of particles. Big frame-rate win in busy fights and farms.",
                    Category.PERFORMANCE, false);
            this.keep = add(new SliderSetting("keep", "Keep", "Share of particles that still appear.", 50, 5, 100, 5, "%"));
        }

        /** Whether this particle should be dropped. */
        public boolean drop() {
            return isActive() && ThreadLocalRandom.current().nextInt(100) >= keep.intValue();
        }
    }

    /** Don't draw entities beyond a distance (they're still there; you just don't render them). */
    public static final class EntityDistance extends Module {
        private final SliderSetting distance;
        private final BoolSetting keepPlayers;

        public EntityDistance() {
            super("entity_distance", "Entity distance", "Skip drawing mobs and items further away than this. They still exist; they're just not drawn.",
                    Category.PERFORMANCE, false);
            this.distance = add(new SliderSetting("distance", "Distance", "Entities beyond this aren't drawn.", 48, 8, 128, 4, " blocks"));
            this.keepPlayers = add(new BoolSetting("players", "Always draw players", "Never hide other players.", true));
        }

        /** True if this entity should be skipped. */
        public boolean cull(Entity e, double camX, double camY, double camZ) {
            if (!isActive() || e == MinecraftClient.getInstance().player) {
                return false;
            }
            if (keepPlayers.isOn() && e instanceof PlayerEntity) {
                return false;
            }
            double d = distance.get();
            return e.squaredDistanceTo(camX, camY, camZ) > d * d;
        }
    }

    /**
     * How the item in your hand swings: a different motion (smooth arc,
     * overhead chop, stab, flip, slide, jab), its speed, and whether it dips
     * after every hit. Only how it looks to you; attack speed is unchanged.
     */
    public static final class Swing extends Module {
        public enum Style {
            VANILLA("Vanilla"), SMOOTH("Smooth arc"), SLASH("Slash"), CHOP("Chop"), STAB("Stab"), SPIN("Flip"), SLIDE("Slide"),
            PUNCH("Jab");

            private final String label;

            Style(String label) {
                this.label = label;
            }

            public String label() {
                return label;
            }
        }

        private final ModeSetting<Style> style;
        private final SliderSetting speed;
        private final BoolSetting noDip;
        private final SliderSetting direction;
        private final BoolSetting mirror;

        public Swing() {
            super("swing", "Swing animation", "Change how your item swings: smooth, chop, stab, flip, slide or jab, and how fast.",
                    Category.VISUAL, false);
            this.style = add(new ModeSetting<>("style", "Style", "The motion of each swing.", Style.SMOOTH, Style::label));
            this.speed = add(new SliderSetting("speed", "Swing speed", "Above 1 swings faster, below 1 slower.", 1.0, 0.3, 3.0, 0.1, "x"));
            this.noDip = add(new BoolSetting("no_dip", "No cooldown dip",
                    "The item stays up after a hit instead of sinking while your attack recharges (1.7 feel).", true));
            this.direction = add(new SliderSetting("direction", "Direction",
                    "Turn the whole swing motion around the middle of the screen, to suit how your view model holds the item.",
                    0, -180, 180, 5, "°"));
            this.mirror = add(new BoolSetting("mirror", "Mirror", "Swing the other way across the screen.", false));
        }

        public int adjust(int ticks) {
            return isActive() ? Math.max(1, Math.round(ticks / speed.floatValue())) : ticks;
        }

        public boolean customStyle() {
            return isActive() && style.get() != Style.VANILLA;
        }

        public boolean noCooldownDip() {
            return isActive() && noDip.isOn();
        }

        /**
         * Replace vanilla's swing for {@code progress} (0..1). {@code side} is
         * 1 for the right arm, -1 for the left. Vanilla has already moved the
         * item by its swing translate; that's undone first.
         */
        public void apply(net.minecraft.client.util.math.MatrixStack m, int side, float progress) {
            float p = progress;
            double root = Math.sqrt(p);
            float n = -0.4f * (float) Math.sin(root * Math.PI);
            float up = 0.2f * (float) Math.sin(root * Math.PI * 2);
            float fwd = -0.2f * (float) Math.sin(p * Math.PI);
            m.translate(-side * n, -up, -fwd);

            float s = (float) Math.sin(root * Math.PI);
            float arc = (float) Math.sin(p * Math.PI);
            // Direction turns the motion about the view axis; Mirror flips it across.
            float turn = direction.floatValue() * side;
            if (mirror.isOn()) {
                side = -side;
            }
            m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(turn));
            try {
                motion(m, side, p, s, arc);
            } finally {
                m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(-turn));
            }
        }

        private void motion(net.minecraft.client.util.math.MatrixStack m, int side, float p, float s, float arc) {
            switch (style.get()) {
                case SLASH -> {
                    // Wind up to the upper right, cut fast down and across, recover.
                    float wind = smooth(0f, 0.18f, p) * (1f - smooth(0.18f, 0.3f, p));
                    float cut = smooth(0.18f, 0.5f, p);
                    float back = 1f - smooth(0.55f, 1f, p);
                    float k = cut * back;
                    m.translate(side * (0.12f * wind - 0.34f * k), 0.16f * wind - 0.22f * k, -0.12f * (wind + k));
                    m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(side * (25f * wind - 70f * k)));
                    m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(side * (20f * wind - 35f * k)));
                    m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(-30f * wind - 55f * k));
                }
                case SMOOTH -> {
                    m.translate(side * 0.05f * s, 0.04f * s, -0.1f * s);
                    m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(side * -18f * s));
                    m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(side * -14f * s));
                    m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(-50f * s));
                }
                case CHOP -> {
                    m.translate(0f, 0.1f * arc, -0.06f * s);
                    m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(-95f * s + 25f * arc));
                }
                case STAB -> {
                    m.translate(-side * 0.04f * s, 0.03f * s, -0.42f * s);
                    m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(side * -8f * s));
                    m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(-14f * s));
                }
                case SPIN -> {
                    float e = 1f - (1f - p) * (1f - p) * (1f - p);
                    m.translate(0f, 0.06f * arc, -0.08f * arc);
                    m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(-360f * e));
                }
                case SLIDE -> {
                    m.translate(side * -0.16f * s, -0.2f * s, -0.12f * s);
                    m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(side * 30f * s));
                    m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(-24f * s));
                }
                case PUNCH -> {
                    float q = (float) Math.sin(Math.min(1f, p * 1.6f) * Math.PI);
                    m.translate(0f, 0.04f * q, -0.32f * q);
                    m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(-22f * q));
                }
                case VANILLA -> { }
            }
        }

        private static float smooth(float a, float b, float t) {
            float x = Math.max(0f, Math.min(1f, (t - a) / (b - a)));
            return x * x * (3 - 2 * x);
        }
    }
}
