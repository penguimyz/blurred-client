package dev.blurredclient.mod.ui.kit;

/**
 * A float that eases toward a target over real time.
 *
 * <p>Exponential approach rather than a fixed-duration tween: retargeting
 * mid-animation (hovering off a button before it finished lighting up) just
 * carries on from where it is, with no jump and no bookkeeping. Time-based,
 * so it runs at the same speed at 30 and 300 FPS.
 */
public final class Anim {
    private final float speed;
    private float value;
    private float target;
    private long last = System.nanoTime();

    /**
     * @param initial starting value (and target)
     * @param speed   how fast it closes the gap; ~12 is a snappy UI transition,
     *                ~5 a slow drift
     */
    public Anim(float initial, float speed) {
        this.value = initial;
        this.target = initial;
        this.speed = speed;
    }

    /** The current value, advanced to now. Safe to call several times a frame. */
    public float get() {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
        last = now;
        value += (target - value) * (1f - (float) Math.exp(-speed * dt));
        if (Math.abs(target - value) < 0.001f) {
            value = target;
        }
        return value;
    }

    public void target(float next) {
        get();
        target = next;
    }

    /** Target 1 when {@code on}, else 0, and return the current value. */
    public float follow(boolean on) {
        target(on ? 1f : 0f);
        return get();
    }

    /** Jump straight to {@code v} with no animation. */
    public void snap(float v) {
        value = v;
        target = v;
        last = System.nanoTime();
    }

    public float target() {
        return target;
    }
}
