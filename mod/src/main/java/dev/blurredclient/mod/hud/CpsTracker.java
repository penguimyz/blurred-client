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
 * never synthesises input.
 */
public final class CpsTracker {
    private static final long WINDOW_MS = 1000;

    private static final Deque<Long> LEFT = new ArrayDeque<>();
    private static final Deque<Long> RIGHT = new ArrayDeque<>();

    private CpsTracker() {}

    public static void onLeftClick() {
        LEFT.addLast(System.currentTimeMillis());
    }

    public static void onRightClick() {
        RIGHT.addLast(System.currentTimeMillis());
    }

    public static int leftCps() {
        return count(LEFT);
    }

    public static int rightCps() {
        return count(RIGHT);
    }

    private static int count(Deque<Long> clicks) {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) {
            clicks.removeFirst();
        }
        return clicks.size();
    }
}
