package dev.blurredclient.mod.module.hud;

import dev.blurredclient.mod.hud.HudStyle;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.SliderSetting;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Timing help for melee spacing: as a target closes in, this reads WAIT,
 * then "HIT NOW!!!" inside your window (3.0 to 2.9 blocks by default, set it
 * to taste), then TOO LATE once they're inside it. A strip underneath shows
 * the distance sliding through the window.
 *
 * <p>The target is the living thing in front of your crosshair, found with a
 * ray that stops at blocks — so it's only ever something you can see — out
 * to 6 blocks, so it can say WAIT before the target is in reach. It never
 * attacks, aims or clicks for you.
 */
public final class CombatInfoModule extends HudModule {
    private static final double LOOK_RANGE = 6.0;
    private static final float STRIP_FAR = 4.2f;
    private static final float STRIP_NEAR = 2.2f;

    private final SliderSetting windowStart;
    private final SliderSetting windowEnd;
    private final BoolSetting showDistance;
    private final BoolSetting onlyWithTarget;
    private final dev.blurredclient.mod.ui.kit.Anim pulse = new dev.blurredclient.mod.ui.kit.Anim(0f, 14f);

    public CombatInfoModule() {
        super("combat_info", "Hit timing", "HIT NOW!!! when your target is in your sweet spot (3 to 2.9 blocks), TOO LATE once they're closer.",
                dev.blurredclient.mod.module.Category.COMBAT, false, new HudPlacement(HudPlacement.Anchor.CENTER, 0, 34));
        this.windowStart = add(new SliderSetting("window_start", "Window starts at", "HIT NOW from this distance...", 3.0, 2.0, 4.5, 0.05, " blocks"));
        this.windowEnd = add(new SliderSetting("window_end", "Window ends at", "...down to this one. Closer is TOO LATE.", 2.9, 1.5, 4.5, 0.05, " blocks"));
        this.showDistance = add(new BoolSetting("distance", "Show distance", "The exact distance, in blocks.", true));
        this.onlyWithTarget = add(new BoolSetting("only_target", "Only with a target", "Hide when nothing's in front of you.", true));
    }

    enum Phase {
        NONE("NO TARGET", 0xFF7C8090), WAIT("WAIT", 0xFFB4B8C4), CHARGING("CHARGING", 0xFFFFB347),
        NOW("HIT NOW!!!", 0xFF3BD87A), LATE("TOO LATE", 0xFFFF5468);

        final String label;
        final int colour;

        Phase(String label, int colour) {
            this.label = label;
            this.colour = colour;
        }
    }

    private record State(Phase phase, float charge, double distance) {}

    private State state(MinecraftClient client, boolean preview) {
        if (preview) {
            return new State(Phase.NOW, 1f, 2.95);
        }
        ClientPlayerEntity p = client.player;
        if (p == null) {
            return null;
        }
        float charge = p.getAttackCooldownProgress(0.5f);
        Entity target = target(client, LOOK_RANGE);
        if (target == null) {
            return new State(Phase.NONE, charge, -1);
        }
        double d = distanceTo(p.getEyePos(), target.getBoundingBox());
        double start = Math.max(windowStart.get(), windowEnd.get());
        double end = Math.min(windowStart.get(), windowEnd.get());
        Phase phase;
        if (d > start) {
            phase = Phase.WAIT;
        } else if (d >= end) {
            phase = charge > 0.9f ? Phase.NOW : Phase.CHARGING;
        } else {
            phase = Phase.LATE;
        }
        return new State(phase, charge, d);
    }

    /**
     * The living thing your crosshair is on, up to {@code range} blocks, not
     * through blocks. Vanilla's own crosshair target wins when there is one.
     */
    static Entity target(MinecraftClient client, double range) {
        if (client.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity) {
            return hit.getEntity();
        }
        ClientPlayerEntity p = client.player;
        if (p == null || client.world == null) {
            return null;
        }
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1f);
        Vec3d end = eye.add(look.multiply(range));
        HitResult block = client.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, p));
        double limit = block.getType() == HitResult.Type.MISS ? range : eye.distanceTo(block.getPos());
        Entity best = null;
        double bestD = limit;
        Box area = p.getBoundingBox().stretch(look.multiply(range)).expand(1);
        for (Entity e : client.world.getOtherEntities(p, area,
                en -> en instanceof LivingEntity && en.isAlive() && !en.isSpectator())) {
            var hit = e.getBoundingBox().raycast(eye, end);
            if (hit.isPresent()) {
                double d = eye.distanceTo(hit.get());
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
        }
        return best;
    }

    /** Distance from the eye to the nearest point of the box — what reach is measured against. */
    static double distanceTo(Vec3d eye, Box box) {
        double dx = Math.max(Math.max(box.minX - eye.x, 0), eye.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - eye.y, 0), eye.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - eye.z, 0), eye.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static final int W = 168;
    private static final int H = 50;

    @Override
    public Size measure(MinecraftClient client, boolean preview) {
        State s = state(client, preview);
        if (s == null || onlyWithTarget.isOn() && s.phase() == Phase.NONE) {
            return null;
        }
        return new Size(W, H);
    }

    @Override
    public void render(DrawContext ctx, MinecraftClient client, boolean preview) {
        State s = state(client, preview);
        if (s == null) {
            return;
        }
        var font = client.textRenderer;
        boolean now = s.phase() == Phase.NOW;
        float glow = pulse.follow(now);
        HudStyle.panel(ctx, 0, 0, W, H);
        // Green wash and edge while it's time to hit.
        if (glow > 0.01f) {
            Draw.rect(ctx, 0, 0, W, H, Colors.fade(0x303BD87A, glow));
            Draw.rect(ctx, 0, 0, W, 1, Colors.fade(0xFF3BD87A, glow));
            Draw.rect(ctx, 0, H - 1, W, H, Colors.fade(0xFF3BD87A, glow));
        }

        // Headline, with a little punch when it flips to HIT NOW.
        float size = 1.9f + 0.18f * glow * (float) (0.5 + 0.5 * Math.sin(System.nanoTime() / 1e9 * 14));
        String head = s.phase().label;
        int hw = Draw.width(font, HudStyle.bold(head), size);
        Draw.text(ctx, font, HudStyle.bold(head), (W - hw) / 2, 5, s.phase().colour, size);

        // Distance strip: far on the left, close on the right; the window glows.
        int sx = 10;
        int sw = W - 20;
        int sy = H - 13;
        Draw.rect(ctx, sx, sy, sx + sw, sy + 4, 0x30FFFFFF);
        float a = stripX(Math.max(windowStart.get(), windowEnd.get()));
        float b = stripX(Math.min(windowStart.get(), windowEnd.get()));
        Draw.rect(ctx, sx + sw * a, sy - 1, sx + sw * b + 1, sy + 5, now ? 0xFF3BD87A : 0x993BD87A);
        Draw.rect(ctx, sx + sw * b + 1, sy, sx + sw, sy + 4, 0x40FF5468);
        if (s.distance() >= 0) {
            float m = stripX(s.distance());
            float mx = sx + sw * m;
            Draw.rect(ctx, mx - 1.5f, sy - 4, mx + 1.5f, sy + 8, 0xFFFFFFFF);
        }
        // Charge, as a hairline under the strip.
        Draw.rect(ctx, sx, sy + 7, sx + sw * Math.min(1f, s.charge()), sy + 8.5f,
                Colors.lerp(0xAAFFB347, 0xAA3BD87A, s.charge()));

        if (showDistance.isOn() && s.distance() >= 0) {
            String d = String.format("%.2f", s.distance());
            int dw = Draw.width(font, HudStyle.text(d), 0.7f);
            Draw.text(ctx, font, HudStyle.text(d), W - 5 - dw, 3, 0xFF9A9EAA, 0.7f);
        }
    }

    /** 0 at {@link #STRIP_FAR}, 1 at {@link #STRIP_NEAR}. */
    private static float stripX(double distance) {
        return (float) Math.max(0, Math.min(1, (STRIP_FAR - distance) / (STRIP_FAR - STRIP_NEAR)));
    }

    // ---- hit type, shared with the Hit type indicator ----

    /** What the next swing would do. */
    public enum HitType {
        CRIT("CRIT", "falling", 0xFFFFC94D), SPRINT("SPRINT", "knockback", 0xFF55CDF2),
        SWEEP("SWEEP", "sword, standing", 0xFFB98CFF), NORMAL("NORMAL", "plain hit", 0xFFB4B8C4);

        public final String label;
        public final String why;
        public final int colour;

        HitType(String label, String why, int colour) {
            this.label = label;
            this.why = why;
            this.colour = colour;
        }
    }

    /**
     * Mirrors the conditions in vanilla's {@code PlayerEntity#attack}, assuming
     * the swing is charged: crit when falling (not on ground, climbing, in
     * water, blind or riding, and not sprinting); sprint hit when sprinting;
     * sweep on the ground with a sword while barely moving; else normal.
     */
    public static HitType predict(ClientPlayerEntity p) {
        boolean crit = p.fallDistance > 0 && !p.isOnGround() && !p.isClimbing() && !p.isTouchingWater()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS) && !p.hasVehicle() && !p.isSprinting();
        if (crit) {
            return HitType.CRIT;
        }
        if (p.isSprinting()) {
            return HitType.SPRINT;
        }
        Vec3d v = p.getVelocity();
        if (p.isOnGround() && p.getMainHandStack().isIn(ItemTags.SWORDS) && v.horizontalLengthSquared() < 0.01) {
            return HitType.SWEEP;
        }
        return HitType.NORMAL;
    }
}
