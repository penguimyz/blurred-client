package dev.blurredclient.mod.bridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.blurredclient.mod.BlurredMod;
import dev.blurredclient.mod.cosmetic.CapeManager;
import dev.blurredclient.mod.social.BlurredUsers;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client half of the launcher bridge (see {@code commands/bridge.rs}).
 *
 * <p>The launcher owns the single IRC connection; this reads a mirror of it
 * over a loopback socket. That's why the mod has no network code of its own and
 * needs no credentials — it can only ever talk to a launcher on this machine
 * that started this exact process.
 *
 * <p>Connection details arrive as JVM system properties set by the launcher at
 * launch. If they're absent (the game was started some other way), the bridge
 * stays offline and the mod is HUD-only — that's a supported state, not an
 * error, so it's logged at info and never shown as a failure.
 *
 * <p>Threading: one daemon reader thread owns the socket and mutates the shared
 * lists; the render thread only reads them. The collections are copy-on-write
 * so a render pass can iterate without locking or risking a concurrent
 * modification mid-frame.
 */
public final class LauncherBridge {
    private static final LauncherBridge INSTANCE = new LauncherBridge();

    /** Keep scrollback bounded — this is a HUD, not an archive. */
    private static final int MAX_MESSAGES = 200;
    /** How long to wait before retrying a dropped connection. */
    private static final long RECONNECT_DELAY_MS = 5000;

    private final CopyOnWriteArrayList<Friend> friends = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ChatLine> messages = new CopyOnWriteArrayList<>();

    /** Your cape library, mirrored from the launcher for the in-game picker. */
    private final CopyOnWriteArrayList<CapeEntry> capes = new CopyOnWriteArrayList<>();
    private volatile String wornCapeId = "";

    private volatile boolean connected;
    private volatile boolean launcherReachable;
    private volatile String nick = "";
    private volatile BufferedWriter writer;
    /** Set when a new message arrives, so the HUD can raise a toast. */
    private volatile ChatLine lastIncoming;

    private LauncherBridge() {}

    public static LauncherBridge get() {
        return INSTANCE;
    }

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    /** Start the connection loop. Safe to call once, from client init. */
    public void start() {
        String portProp = System.getProperty("blurred.bridge.port");
        String token = System.getProperty("blurred.bridge.token");

        if (portProp == null || token == null) {
            BlurredMod.LOGGER.info(
                    "No launcher bridge details — running HUD-only. "
                            + "Launch from Blurred Client for crew and chat.");
            return;
        }

        final int port;
        try {
            port = Integer.parseInt(portProp);
        } catch (NumberFormatException e) {
            BlurredMod.LOGGER.warn("Bad bridge port '{}', running HUD-only", portProp);
            return;
        }

        Thread t = new Thread(() -> runForever(port, token), "blurred-bridge");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Reconnect loop. The launcher can be closed and reopened while the game
     * keeps running, so a dropped connection is normal rather than terminal.
     */
    private void runForever(int port, String token) {
        while (true) {
            try {
                connectOnce(port, token);
            } catch (IOException e) {
                BlurredMod.LOGGER.debug("Bridge disconnected: {}", e.toString());
            }

            connected = false;
            launcherReachable = false;
            writer = null;
            // Other people's capes came from the launcher; without it we can't
            // know if they're still current, so drop them rather than show a
            // stale cosmetic. Ours comes straight back on reconnect.
            CapeManager.clear();
            // Same reasoning for the badge: without the launcher we no longer
            // know who's on Blurred, and a stale badge is a wrong claim.
            BlurredUsers.clear();

            try {
                Thread.sleep(RECONNECT_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void connectOnce(int port, String token) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 3000);
            socket.setTcpNoDelay(true);

            BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            JsonObject hello = new JsonObject();
            hello.addProperty("t", "hello");
            hello.addProperty("token", token);
            out.write(hello.toString());
            out.write('\n');
            out.flush();

            this.writer = out;
            this.launcherReachable = true;
            // The launcher's record of where we're playing died with the last
            // connection, so make the game state the source of truth again.
            // Without this, restarting the launcher mid-session leaves the crew
            // unable to join a server we never stopped being on.
            BlurredMod.onBridgeConnected();
            BlurredMod.LOGGER.info("Connected to Blurred Client on port {}", port);

            String line;
            while ((line = in.readLine()) != null) {
                handle(line);
            }
        }
    }

    // ---------------------------------------------------------------
    // Inbound
    // ---------------------------------------------------------------

    private void handle(String line) {
        JsonObject o;
        try {
            o = JsonParser.parseString(line).getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }

        String type = str(o, "t");
        if (type == null) {
            return;
        }

        switch (type) {
            case "welcome" -> {
                nick = strOr(o, "nick", "");
                connected = o.has("connected") && o.get("connected").getAsBoolean();
            }
            case "status" -> {
                nick = strOr(o, "nick", nick);
                connected = o.has("connected") && o.get("connected").getAsBoolean();
            }
            case "friends" -> {
                List<Friend> next = new ArrayList<>();
                o.getAsJsonArray("friends").forEach(el -> {
                    JsonObject f = el.getAsJsonObject();
                    next.add(new Friend(
                            strOr(f, "nick", "?"),
                            strOr(f, "note", ""),
                            f.has("online") && f.get("online").getAsBoolean(),
                            strOr(f, "status", "accepted"),
                            strOr(f, "server", "")));
                });
                // Replace wholesale: the launcher always sends the full roster,
                // so merging could only ever leave stale entries behind.
                friends.clear();
                friends.addAll(next);
            }
            case "presence" -> {
                String who = strOr(o, "nick", "");
                boolean online = o.has("online") && o.get("online").getAsBoolean();
                friends.replaceAll(f ->
                        f.nick().equalsIgnoreCase(who) ? f.withOnline(online) : f);
            }
            case "message" -> {
                ChatLine msg = new ChatLine(
                        strOr(o, "conversation", ""),
                        strOr(o, "from", ""),
                        strOr(o, "text", ""),
                        strOr(o, "kind", "message"),
                        o.has("mine") && o.get("mine").getAsBoolean(),
                        System.currentTimeMillis());
                messages.add(msg);
                while (messages.size() > MAX_MESSAGES) {
                    messages.remove(0);
                }
                if (!msg.mine() && !"system".equals(msg.kind())) {
                    lastIncoming = msg;
                }
            }
            // Everyone currently in the Blurred lobby, i.e. everyone we can be
            // sure is running this client. Drives the in-game badge.
            case "users" -> {
                List<String> next = new ArrayList<>();
                if (o.has("users")) {
                    o.getAsJsonArray("users").forEach(el -> next.add(el.getAsString()));
                }
                BlurredUsers.setAll(next);
            }
            // Incremental roster changes between full pushes, so someone who
            // starts their launcher mid-session gets badged without waiting.
            case "userJoined" -> BlurredUsers.add(strOr(o, "user", ""));
            case "userLeft" -> BlurredUsers.remove(strOr(o, "user", ""));
            // A cape for some player — ours or anyone else's. `data` null means
            // they took it off.
            case "cape" -> {
                String who = strOr(o, "username", "");
                String data = str(o, "data");
                if (!who.isBlank()) {
                    CapeManager.put(who, data);
                    // Having their cape is proof they're on Blurred, and it
                    // arrives sooner than the next lobby roster.
                    if (data != null) {
                        BlurredUsers.add(who);
                    }
                }
            }
            // Bulk delivery on connect, so a player already in view gets their
            // cape without waiting for an individual push.
            case "capes" -> {
                JsonObject all = o.getAsJsonObject("capes");
                if (all != null) {
                    all.entrySet().forEach(e -> {
                        CapeManager.put(e.getKey(), e.getValue().getAsString());
                        BlurredUsers.add(e.getKey());
                    });
                }
            }
            // Your cape library + which one is worn, for the in-game picker.
            case "capeList" -> {
                List<CapeEntry> next = new ArrayList<>();
                if (o.has("capes")) {
                    o.getAsJsonArray("capes").forEach(el -> {
                        JsonObject c = el.getAsJsonObject();
                        next.add(new CapeEntry(strOr(c, "id", ""), strOr(c, "name", "Untitled")));
                    });
                }
                capes.clear();
                capes.addAll(next);
                wornCapeId = strOr(o, "worn", "");
            }
            default -> {
                // Unknown event types are ignored rather than logged: the
                // launcher may be newer than the mod, and that must not spam.
            }
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static String strOr(JsonObject o, String key, String fallback) {
        String v = str(o, key);
        return v == null ? fallback : v;
    }

    // ---------------------------------------------------------------
    // Outbound
    // ---------------------------------------------------------------

    /** Send a chat message. No-op when the launcher isn't reachable. */
    public void send(String conversation, String text) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "send");
        o.addProperty("conversation", conversation);
        o.addProperty("text", text);
        write(o);
    }

    /** Wear a cape by id, or pass null to take it off. */
    public void wearCape(String capeId) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "wearCape");
        if (capeId != null) {
            o.addProperty("id", capeId);
        }
        // Optimistic local update so the picker responds on click; the launcher
        // echoes a fresh capeList back and corrects this if it disagrees.
        wornCapeId = capeId == null ? "" : capeId;
        write(o);
    }

    public List<CapeEntry> capes() {
        return Collections.unmodifiableList(capes);
    }

    public String wornCapeId() {
        return wornCapeId;
    }

    /** Tell the launcher which server we're on, so crew can join. */
    public void reportServer(String server) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "playing");
        o.addProperty("server", server == null ? "" : server);
        write(o);
    }

    /**
     * Ask to join someone's crew.
     *
     * <p>The launcher owns the handshake — this only says who. Fire and forget:
     * the answer comes back as a roster push or, if it couldn't be sent, as a
     * system line in the transcript, both of which the crew screen already
     * shows. There is nothing useful for the mod to do with a return value it
     * would have to invent a request/response protocol to receive.
     */
    public void addFriend(String nick, String note) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "addFriend");
        o.addProperty("nick", nick);
        o.addProperty("note", note == null ? "" : note);
        write(o);
    }

    /** Accept a request someone sent us. */
    public void acceptFriend(String nick) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "acceptFriend");
        o.addProperty("nick", nick);
        write(o);
    }

    /**
     * Turn down an incoming request, or withdraw one of ours — the same button
     * either way, because from the user's side both are "make this go away".
     */
    public void declineFriend(String nick) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "declineFriend");
        o.addProperty("nick", nick);
        write(o);
    }

    private void write(JsonObject o) {
        BufferedWriter w = this.writer;
        if (w == null) {
            return;
        }
        try {
            synchronized (w) {
                w.write(o.toString());
                w.write('\n');
                w.flush();
            }
        } catch (IOException e) {
            // The reader thread will notice and reconnect; nothing to do here.
            BlurredMod.LOGGER.debug("Bridge write failed: {}", e.toString());
        }
    }

    // ---------------------------------------------------------------
    // Read-only views for the HUD and screens
    // ---------------------------------------------------------------

    /** True when the launcher is reachable AND its chat is connected. */
    public boolean isChatConnected() {
        return launcherReachable && connected;
    }

    public boolean isLauncherReachable() {
        return launcherReachable;
    }

    public String nick() {
        return nick;
    }

    public List<Friend> friends() {
        return Collections.unmodifiableList(friends);
    }

    /** Accepted crew only — pending requests are handled in the launcher. */
    public List<Friend> crew() {
        List<Friend> out = new ArrayList<>();
        for (Friend f : friends) {
            if (f.isAccepted()) {
                out.add(f);
            }
        }
        out.sort((a, b) -> {
            if (a.online() != b.online()) {
                return a.online() ? -1 : 1;
            }
            return a.nick().compareToIgnoreCase(b.nick());
        });
        return out;
    }

    /**
     * Requests waiting on someone: incoming first, because those are the ones
     * that need a decision, then the ones we've sent and are waiting on.
     */
    public List<Friend> pending() {
        List<Friend> out = new ArrayList<>();
        for (Friend f : friends) {
            if (f.isPendingIn()) {
                out.add(f);
            }
        }
        for (Friend f : friends) {
            if (f.isPendingOut()) {
                out.add(f);
            }
        }
        return out;
    }

    public List<ChatLine> messages() {
        return Collections.unmodifiableList(messages);
    }

    /** Take the pending incoming message, if any, clearing it. */
    public ChatLine pollIncoming() {
        ChatLine m = lastIncoming;
        lastIncoming = null;
        return m;
    }
}
