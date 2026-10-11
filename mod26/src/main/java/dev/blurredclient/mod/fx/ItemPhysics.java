package dev.blurredclient.mod.fx;

import dev.blurredclient.mod.module.Modules;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * The motion behind Item physics, kept per dropped item so it's continuous:
 * an item in the air spins at a rate that follows how fast it's moving, and
 * when it lands it rolls into its resting pose on a spring (with a little
 * overshoot) instead of jumping there. Flat items rest face up or face down,
 * whichever is nearer; blocks rest on whichever side is nearest.
 *
 * <p>Purely how the item is drawn. Updated once per frame per visible item.
 */
public final class ItemPhysics {
    private ItemPhysics() {}

    /** Rotation about the item's own X axis (degrees), and 0 (in the air) .. 1 (resting). */
    public record Pose(float angle, float settle) {}

    private static final class State {
        float angle;
        float velocity;
        float settle;
        float settleVelocity;
        long lastNs;
    }

    private static final Map<ItemEntity, State> STATES = new WeakHashMap<>();

    public static Pose update(ItemEntity item, boolean flat) {
        long now = System.nanoTime();
        boolean grounded = item.onGround();
        State s = STATES.get(item);
        if (s == null) {
            s = new State();
            // Already lying there when we first see it: start at rest.
            s.angle = grounded ? (flat ? -90f : 0f) : (item.getId() * 47 % 360);
            s.settle = grounded ? 1f : 0f;
            s.lastNs = now;
            STATES.put(item, s);
        }
        float dt = Math.min(0.05f, (now - s.lastNs) / 1e9f);
        s.lastNs = now;
        if (dt <= 0) {
            return new Pose(s.angle, s.settle);
        }

        var module = Modules.ITEM_PHYSICS;
        if (!grounded) {
            // Spin faster the faster it flies; ease toward that rate.
            double speed = item.getDeltaMovement().length() * 20; // blocks per second
            float rate = module.tumbleRate((float) speed);
            s.velocity += (rate - s.velocity) * Math.min(1f, dt * 6f);
            s.angle += s.velocity * dt;
            s.settleVelocity += (-s.settle * 120f - s.settleVelocity * 18f) * dt;
        } else {
            float target = nearestRest(s.angle, flat);
            // Underdamped spring: rolls over and settles with a small wobble.
            float accel = (target - s.angle) * 260f - s.velocity * 17f;
            s.velocity += accel * dt;
            s.angle += s.velocity * dt;
            s.settleVelocity += ((1f - s.settle) * 260f - s.settleVelocity * 22f) * dt;
        }
        s.settle = Math.max(0f, Math.min(1.08f, s.settle + s.settleVelocity * dt));
        // Keep the angle small so the nearest-rest maths stays simple.
        if (s.angle > 720 || s.angle < -720) {
            s.angle %= 360f;
        }
        return new Pose(s.angle, Math.min(1f, s.settle));
    }

    /** Closest resting angle: flat items at -90 + k*180, blocks at k*90. */
    private static float nearestRest(float angle, boolean flat) {
        float step = flat ? 180f : 90f;
        float offset = flat ? -90f : 0f;
        return Math.round((angle - offset) / step) * step + offset;
    }
}
