package dev.blurredclient.mod.hud;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Clicks-per-second for each mouse button.
 *
 * <p>Keeps the timestamps of clicks inside a one-second window and reports the
 * size of that window, which is the honest definition of CPS — a decaying
 * average would lag the number people are actually watching.
 *
 * <p>Read-only bookkeeping: it observes clicks the player already made and
 * never synthesises input. Fed by
 * {@link dev.blurredclient.mod.mixin.MouseClickMixin}.
 *
 * <p><b>Threading.</b> Both the GLFW button callback and the HUD render happen
 * on the client's main thread, so the plain {@link ArrayDeque}s below are only
 * ever touched from one thread.
 */
public final class CpsTracker {
    private static final long WINDOW_MS = 1000;

    private static final Deque<Long> LEFT = new ArrayDeque<>();
    private static final Deque<Long> RIGHT = new ArrayDeque<>();

    private CpsTracker() {}

    public static void onLeftClick() {
        record(LEFT);
    }

    public static void onRightClick() {
        record(RIGHT);
    }

    /**
     * Note a click, and drop the ones that have aged out.
     *
     * <p>The pruning is the point of doing this here rather than only in
     * {@link #count}: the readout is off by default, and with nothing ever
     * reading the deques a session's worth of clicks would sit in them growing
     * without bound. Pruning on the way in makes the memory a function of your
     * click rate rather than of how long you've been playing, whether or not
     * anything is displaying the number.
     */
    private static void record(Deque<Long> clicks) {
        clicks.addLast(System.currentTimeMillis());
        prune(clicks);
    }

    public static int leftCps() {
        return count(LEFT);
    }

    public static int rightCps() {
        return count(RIGHT);
    }

    private static int count(Deque<Long> clicks) {
        prune(clicks);
        return clicks.size();
    }

    private static void prune(Deque<Long> clicks) {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) {
            clicks.removeFirst();
        }
    }
}
