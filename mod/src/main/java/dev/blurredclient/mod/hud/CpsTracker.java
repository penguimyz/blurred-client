package dev.blurredclient.mod.hud;

import dev.blurredclient.mod.mixin.KeyBindingAccessor;
import net.minecraft.client.MinecraftClient;

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
 * never synthesises input. Fed each tick from the attack and use keys'
 * pending presses (see {@link #onTickStart}).
 *
 * <p><b>Threading.</b> Both the client tick and the HUD render happen on the
 * client's main thread, so the plain {@link ArrayDeque}s below are only
 * ever touched from one thread.
 */
public final class CpsTracker {
    private static final long WINDOW_MS = 1000;

    private static final Deque<Long> LEFT = new ArrayDeque<>();
    private static final Deque<Long> RIGHT = new ArrayDeque<>();

    private CpsTracker() {}

    /**
     * Record this tick's clicks. Called at the start of every client tick,
     * before the game consumes the presses.
     *
     * <p>v1 had the counters but nothing ever fed them, so the readout sat at
     * 0/0. Reading the attack and use keybinds' pending press counts covers
     * rebound buttons too, since it follows the binding rather than a mouse
     * button.
     */
    public static void onTickStart(MinecraftClient client) {
        if (client.options == null) {
            return;
        }
        record(LEFT, ((KeyBindingAccessor) client.options.attackKey).blurred$getTimesPressed());
        record(RIGHT, ((KeyBindingAccessor) client.options.useKey).blurred$getTimesPressed());
    }

    private static void record(Deque<Long> clicks, int count) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            clicks.addLast(now);
        }
        // Prune here too, so the deques don't grow while the readout is hidden.
        count(clicks);
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
