package dev.blurredclient.mod.social;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who else in this world is running Blurred Client.
 *
 * <p>There is no packet for this and there shouldn't be — a client-side mod
 * that announces itself to the server is a fingerprint, and one that probes
 * other players is worse. So the answer comes from the side that already knows
 * it: the launcher sits in the Blurred lobby channel, and everyone in that
 * channel is running the client by definition. The launcher pushes that roster
 * over the bridge and this holds it.
 *
 * <p>Cape owners are folded in too. Receiving someone's cape is proof they're
 * on Blurred, and it covers the window between joining a server and the next
 * lobby roster arriving.
 *
 * <p>Consequences worth being honest about: someone running Blurred with the
 * launcher closed, or with chat disconnected, will not be badged. That's the
 * right failure — a badge that appears when we don't actually know would be
 * worse than no badge.
 *
 * <p><b>Threading.</b> Written from the bridge reader thread, read from the
 * render thread, so the backing set is concurrent.
 */
public final class BlurredUsers {
    /** Lowercased usernames. */
    private static final Set<String> KNOWN = ConcurrentHashMap.newKeySet();

    private BlurredUsers() {}

    /** Replace the roster wholesale — the launcher always sends the full list. */
    public static void setAll(Iterable<String> usernames) {
        Set<String> next = ConcurrentHashMap.newKeySet();
        for (String name : usernames) {
            if (name != null && !name.isBlank()) {
                next.add(normalise(name));
            }
        }
        KNOWN.retainAll(next);
        KNOWN.addAll(next);
    }

    /** Note a single user, e.g. because their cape just arrived. */
    public static void add(String username) {
        if (username != null && !username.isBlank()) {
            KNOWN.add(normalise(username));
        }
    }

    /** Forget one user — they left the lobby, so we no longer know. */
    public static void remove(String username) {
        if (username != null && !username.isBlank()) {
            KNOWN.remove(normalise(username));
        }
    }

    public static boolean isBlurredUser(String username) {
        return username != null && KNOWN.contains(normalise(username));
    }

    /** Drop everything — the launcher went away, so we no longer know. */
    public static void clear() {
        KNOWN.clear();
    }

    public static int count() {
        return KNOWN.size();
    }

    /**
     * Exact match, case-folded.
     *
     * <p>Tempting to be cleverer here: an IRC nick picks up a numeric suffix
     * when the name is already taken, so `Steve1` in the lobby is often `Steve`
     * in game, and stripping trailing digits would catch those. It would also
     * badge a genuinely different player called `Steve1` as `Steve`, and a
     * badge that says "this person is on your client" has to be right — a false
     * one is worse than a missing one. So a contested nick just doesn't get
     * badged.
     */
    private static String normalise(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
