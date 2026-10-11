package dev.blurredclient.mod.social;

import dev.blurredclient.mod.BlurredMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Messaging a player who isn't on Blurred — an Essential user, a vanilla
 * player, anyone on the server.
 *
 * <h2>Why this goes through the server's own chat</h2>
 *
 * Blurred's crew chat runs over IRC through the launcher, and reaches exactly
 * the people whose launcher is in our lobby. An Essential user is not one of
 * them, and cannot be made into one: Essential publishes no API, so there is no
 * supported way to look up their friend list, address their relay, or put a
 * message into their client. Reaching into another mod's internals by
 * reflection would break on their next update, and building a social feature on
 * top of that would be dishonest about how solid it is.
 *
 * <p>What both clients unambiguously share is the Minecraft server you are both
 * standing on. Its {@code /msg} is a real, private, cross-client message
 * channel that has worked since 2011 and needs no cooperation from anyone's
 * mod. So that's the pipe: {@link #send} issues a normal chat command, and the
 * other player receives it however their client shows whispers.
 *
 * <h2>What this deliberately does not do</h2>
 *
 * Replies are not mirrored back into the crew panel. They arrive in the game's
 * own chat, which is where whispers have always arrived and where the recipient
 * is already looking. Capturing them would mean either matching the rendered
 * text of {@code commands.message.display.incoming} — which every server with a
 * chat plugin reformats — or a mixin whose target moves between versions, and
 * either one silently shows a *partial* conversation. A conversation that looks
 * complete but is missing messages is worse than one you read in the chat box.
 *
 * <p>So the panel is honest about direction: it sends, and it tells you it
 * sent. The reply appears in chat.
 */
public final class Whispers {

    /**
     * What we've sent, keyed by lowercased player name.
     *
     * <p>Outgoing only — see the class note on why replies aren't captured. The
     * point of keeping it is that a composer with no record of what you just
     * typed gives you no way to tell a sent message from a swallowed one.
     * Concurrent because the screen reads it while the client thread writes.
     */
    private static final Map<String, List<String>> SENT = new ConcurrentHashMap<>();

    /** Per-conversation cap. This is a session scratchpad, not a log. */
    private static final int MAX_PER_PLAYER = 50;

    /** Vanilla's own limit on a chat message, and the server will enforce it. */
    private static final int MAX_LENGTH = 256;

    private Whispers() {}

    /**
     * Send a private message to a player on the current server.
     *
     * @return true if it went out; false when there's no server to send it on
     */
    public static boolean send(Minecraft client, String player, String text) {
        ClientPacketListener net = client.getConnection();
        if (net == null || player.isBlank() || text.isBlank()) {
            return false;
        }

        // `/msg` rather than `/tell` or `/w`: all three are the same vanilla
        // command, and `msg` is the spelling nearly every chat plugin also
        // accepts as the primary alias.
        String command = "msg " + player + " " + text;
        if (command.length() > MAX_LENGTH) {
            command = command.substring(0, MAX_LENGTH);
        }

        // sendChatCommand, not sendChatMessage: the leading slash is implied,
        // and going through the command path means the server sees a command
        // rather than a line of public chat beginning with a slash — which is
        // the difference between a private message and telling everyone.
        net.sendCommand(command);

        List<String> thread =
                SENT.computeIfAbsent(player.toLowerCase(Locale.ROOT), k -> new CopyOnWriteArrayList<>());
        thread.add(text);
        while (thread.size() > MAX_PER_PLAYER) {
            thread.remove(0);
        }

        BlurredMod.LOGGER.debug("Whispered {}", player);
        return true;
    }

    /**
     * Invite a player to add you on Blurred, in a message they'll actually
     * receive whatever client they run.
     *
     * <p>Paired with the crew request rather than replacing it: the request is
     * what a Blurred user accepts with one click, and this is what tells
     * everyone else that a request exists and what it was for. On its own,
     * either half is a dead end — a request nobody knows about, or a message
     * with nothing behind it.
     */
    public static boolean sendCrewInvite(Minecraft client, String player, String self) {
        return send(
                client,
                player,
                "I've added you as crew on Blurred Client"
                        + (self == null || self.isBlank() ? "" : " (I'm " + self + ")")
                        + " — open Blurred's Crew panel to accept.");
    }

    /** Have we whispered this player this session? */
    public static boolean hasWhispered(String player) {
        return player != null && SENT.containsKey(player.toLowerCase(Locale.ROOT));
    }

    /** What we've sent this player this session, oldest first. */
    public static List<String> sentTo(String player) {
        if (player == null) {
            return List.of();
        }
        return SENT.getOrDefault(player.toLowerCase(Locale.ROOT), List.of());
    }

    /** Forget the session's conversations — called when leaving a server. */
    public static void reset() {
        SENT.clear();
    }
}
