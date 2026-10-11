package dev.blurredclient.mod.module.feature;

import dev.blurredclient.mod.fx.Ent;
import dev.blurredclient.mod.fx.WorldDraw;
import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.ColorSetting;
import dev.blurredclient.mod.setting.ModeSetting;
import dev.blurredclient.mod.setting.SliderSetting;
import dev.blurredclient.mod.ui.Colors;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Hitboxes, like F3+B, in your own colours: one colour normally and another
 * once the entity is close enough for your attack to reach. Drawn as crisp
 * lines with an optional faint fill.
 */
public final class HitboxModule extends Module implements WorldDraw.Layer {
    public enum Which {
        LIVING("Mobs and players"), PLAYERS("Players"), MOBS("Mobs"), ALL("Everything");

        private final String label;

        Which(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final ModeSetting<Which> which;
    private final ColorSetting colour;
    private final BoolSetting rangeColour;
    private final ColorSetting inRange;
    private final BoolSetting needAim;
    private final BoolSetting rainbow;
    private final SliderSetting width;
    private final BoolSetting fill;
    private final SliderSetting distance;

    public HitboxModule() {
        super("hitboxes", "Hitboxes", "Entity hitboxes in your own colours, changing colour when they're in reach.",
                Category.VISUAL, false);
        this.which = add(new ModeSetting<>("which", "Show for", "Which entities get a hitbox.", Which.LIVING, Which::label));
        this.colour = add(new ColorSetting("colour", "Colour", "The hitbox colour.", 0xFFFFFFFF));
        this.rangeColour = add(new BoolSetting("range_colour", "Colour when in reach",
                "Switch to another colour once your attack can reach it.", true));
        this.inRange = add(new ColorSetting("in_range", "In-reach colour", "The colour when it's in reach.", 0xFFFF4D5E));
        inRange.visibleIf(rangeColour::isOn);
        this.needAim = add(new BoolSetting("need_aim", "Only while aimed at it",
                "Only swap colour for the mob your crosshair is on (and in reach).", true));
        needAim.visibleIf(rangeColour::isOn);
        this.rainbow = add(new BoolSetting("rainbow", "Rainbow", "The colour cycles through the rainbow.", false));
        this.width = add(new SliderSetting("width", "Line width", "How thick the lines are.", 1.5, 0.5, 4, 0.1, "px"));
        this.fill = add(new BoolSetting("fill", "Fill", "A faint fill inside the box.", false));
        this.distance = add(new SliderSetting("distance", "Distance", "Only within this many blocks.", 32, 8, 96, 1, " blocks"));
        WorldDraw.add(this);
    }

    private boolean wanted(Entity e, ClientPlayerEntity me) {
        if (e == me || e.isInvisibleTo(me)) {
            return false;
        }
        return switch (which.get()) {
            case LIVING -> e instanceof LivingEntity;
            case PLAYERS -> e instanceof PlayerEntity;
            case MOBS -> e instanceof LivingEntity && !(e instanceof PlayerEntity);
            case ALL -> true;
        };
    }

    @Override
    public void render(WorldDraw.Frame f) {
        ClientPlayerEntity me = f.client.player;
        if (!isActive() || me == null || f.client.world == null) {
            return;
        }
        double max = distance.get();
        Vec3d eye = me.getCameraPosVec(f.tickDelta);
        double reach = me.getEntityInteractionRange();
        for (Entity e : f.client.world.getEntities()) {
            if (!wanted(e, me) || e.squaredDistanceTo(me) > max * max) {
                continue;
            }
            // Follow the entity smoothly between ticks.
            Vec3d shift = e.getLerpedPos(f.tickDelta).subtract(Ent.pos(e));
            Box box = e.getBoundingBox().offset(shift);
            boolean aimed = !needAim.isOn() || (f.client.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hit
                    && hit.getEntity() == e);
            boolean near = rangeColour.isOn() && aimed && distance(box, eye) <= reach;
            int c = near ? inRange.argb() : colour.argb();
            if (rainbow.isOn()) {
                // Each box a little along the rainbow from the last; in reach it runs brighter.
                float hue = (float) ((System.nanoTime() / 1e9 * 0.25 + e.getId() * 0.13) % 1.0);
                c = Colors.hsv(hue, near ? 0.45f : 0.75f, 1f);
            }
            box(f, box, c);
        }
    }

    private static double distance(Box b, Vec3d p) {
        double dx = Math.max(Math.max(b.minX - p.x, 0), p.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - p.y, 0), p.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - p.z, 0), p.z - b.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void box(WorldDraw.Frame f, Box b, int c) {
        Vec3d[] v = {
                new Vec3d(b.minX, b.minY, b.minZ), new Vec3d(b.maxX, b.minY, b.minZ),
                new Vec3d(b.maxX, b.minY, b.maxZ), new Vec3d(b.minX, b.minY, b.maxZ),
                new Vec3d(b.minX, b.maxY, b.minZ), new Vec3d(b.maxX, b.maxY, b.minZ),
                new Vec3d(b.maxX, b.maxY, b.maxZ), new Vec3d(b.minX, b.maxY, b.maxZ),
        };
        if (fill.isOn()) {
            int fc = Colors.fade(c, 0.12f);
            int[][] faces = {{0, 1, 2, 3}, {4, 7, 6, 5}, {0, 4, 5, 1}, {1, 5, 6, 2}, {2, 6, 7, 3}, {3, 7, 4, 0}};
            for (int[] q : faces) {
                f.quad(v[q[0]], v[q[1]], v[q[2]], v[q[3]], fc, false);
            }
        }
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            line(f, v[e[0]], v[e[1]], c);
        }
    }

    /** A line of constant on-screen thickness: a thin quad turned to face the camera. */
    private void line(WorldDraw.Frame f, Vec3d a, Vec3d b, int c) {
        Vec3d dir = b.subtract(a);
        Vec3d mid = a.add(b).multiply(0.5);
        Vec3d toCam = f.cam.subtract(mid);
        Vec3d side = dir.crossProduct(toCam);
        double len = side.length();
        if (len < 1e-6) {
            return;
        }
        // About width.get() pixels at this distance, for a typical screen.
        double half = width.get() * 0.0011 * toCam.length();
        side = side.multiply(half / len);
        f.quad(a.subtract(side), a.add(side), b.add(side), b.subtract(side), c, false);
    }
}
