package dev.blurredclient.mod.social;

import com.google.gson.JsonObject;
import dev.blurredclient.mod.BlurredMod;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * "Blurred Client — On a server" on your Discord profile.
 *
 * <h2>How this talks to Discord</h2>
 *
 * Discord's desktop app listens on a local IPC socket — a named pipe on
 * Windows, a Unix socket on Linux and macOS — called {@code discord-ipc-N} for
 * N in 0..9 (several, because several Discord builds can run side by side).
 * The wire format is trivial: a little-endian 32-bit opcode, a little-endian
 * 32-bit body length, then that many bytes of UTF-8 JSON.
 *
 * <p>That's the whole protocol we need, which is why this is hand-written
 * rather than pulled in as a library. The mod ships with no dependencies beyond
 * what Minecraft already has, and adding a native-loading RPC SDK to gain forty
 * lines of socket code would be a bad trade — especially one shipping a
 * platform binary into a launcher that has to work on Windows and Linux.
 *
 * <h2>Where the name in the status comes from</h2>
 *
 * Discord derives the bold first line — the "Blurred Client" part — from the
 * <em>name of the application the ID belongs to</em>, not from anything sent
 * over the wire. There is no generic ID meaning "some game", which is why an
 * application ID is required at all rather than optional.
 *
 * <p>The launcher ships Blurred's own and passes it to every instance as a JVM
 * property, so this works with nothing to set up. Overriding it per instance in
 * {@code config/blurred.json} ({@code discordAppId}) points the status at a
 * different application, and therefore renames it.
 *
 * <p>A game launched outside Blurred gets no property and no presence, unless
 * that per-instance override is filled in. That's the intended shape: the ID is
 * the launcher's to supply.
 *
 * <h2>Threading</h2>
 *
 * Everything after the state read happens on one daemon thread. A named pipe
 * write blocks if Discord is busy, and blocking the client thread on it would
 * stutter the game for the sake of a status line. The client thread only ever
 * writes an immutable snapshot into {@link #pending}; the worker picks it up.
 */
public final class DiscordPresence {

    /** Discord's IPC sockets are numbered; try each in turn. */
    private static final int MAX_SOCKETS = 10;

    /** Opcodes. Only these two are needed to publish a status. */
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;

    /** Don't hammer the socket: Discord rate-limits updates to one per 15s. */
    private static final long MIN_UPDATE_INTERVAL_MS = 15_000;

    /** How long to wait before retrying after Discord isn't there. */
    private static final long RECONNECT_DELAY_MS = 60_000;

    /**
     * What we'd like the status to say. Written by the game thread, read by the
     * worker. A record so the two never see a half-updated pair of strings.
     */
    private record Status(String details, String state) {}

    private static final AtomicReference<Status> pending = new AtomicReference<>();

    private static volatile boolean started;
    private static volatile Status published;

    /** When this play session began, for Discord's elapsed-time counter. */
    private static final long SESSION_START = System.currentTimeMillis();

    private DiscordPresence() {}

    // ------------------------------------------------------------------
    // Game side
    // ------------------------------------------------------------------

    /**
     * Work out what we're doing and hand it to the worker.
     *
     * <p>Called every client tick. Deliberately cheap — three field reads and a
     * record allocation — with all the comparing and rate limiting left to the
     * worker, so the tick cost doesn't depend on whether Discord is running.
     */
    public static void update(Minecraft client) {
        var discord = dev.blurredclient.mod.module.Modules.DISCORD;
        if (!discord.isActive() || discord.appId().isEmpty()) {
            return;
        }

        String details;
        String state = "";

        if (client.level == null) {
            details = "On the main menu";
        } else if (client.isLocalServer()) {
            details = "In a singleplayer world";
        } else {
            details = "On a server";
            ServerData entry = client.getCurrentServer();
            // Opt-in, and off unless asked for. A Discord status is public to
            // everyone who can see your profile — a much wider and much less
            // deliberate audience than the crew list this mod otherwise shares
            // the address with. So the status says "On a server" and stops
            // there.
            if (discord.showServer() && entry != null && entry.ip != null) {
                state = entry.ip;
            }
        }

        pending.set(new Status(details, state));
        start();
    }

    /** Spin up the worker on first use. */
    private static synchronized void start() {
        if (started) {
            return;
        }
        started = true;

        Thread worker = new Thread(DiscordPresence::run, "Blurred Discord RPC");
        worker.setDaemon(true);
        worker.start();
    }

    // ------------------------------------------------------------------
    // Worker
    // ------------------------------------------------------------------

    private static void run() {
        while (true) {
            try (Ipc ipc = connect()) {
                if (ipc == null) {
                    // Discord isn't running, or isn't running as us. Not an
                    // error worth logging every minute — most players don't
                    // have it open, and this is decoration.
                    sleep(RECONNECT_DELAY_MS);
                    continue;
                }
                pump(ipc);
            } catch (IOException e) {
                BlurredMod.LOGGER.debug("Discord RPC dropped: {}", e.toString());
            }
            // Whatever we published is gone with the connection.
            published = null;
            sleep(RECONNECT_DELAY_MS);
        }
    }

    /**
     * Publish state changes until the connection breaks.
     *
     * <p>Every write is followed by a read of Discord's reply. That isn't for
     * the reply's contents — it's flow control. Discord answers every frame,
     * and a client that never reads eventually fills the socket buffer and
     * wedges; draining in lockstep keeps this bounded with no parsing.
     */
    private static void pump(Ipc ipc) throws IOException {
        JsonObject hello = new JsonObject();
        hello.addProperty("v", 1);
        hello.addProperty("client_id", dev.blurredclient.mod.module.Modules.DISCORD.appId());
        ipc.send(OP_HANDSHAKE, hello.toString());
        ipc.receive(); // READY, or an error frame if the ID is wrong.

        BlurredMod.LOGGER.info("Discord rich presence connected");

        long lastSent = 0;
        while (true) {
            Status want = pending.get();
            long now = System.currentTimeMillis();

            if (want != null
                    && !want.equals(published)
                    && now - lastSent >= MIN_UPDATE_INTERVAL_MS) {
                ipc.send(OP_FRAME, activityFrame(want));
                ipc.receive();
                published = want;
                lastSent = now;
            }

            // Coarse poll. A status is not a thing anyone watches for latency,
            // and Discord won't accept updates faster than this anyway.
            sleep(2000);
        }
    }

    private static String activityFrame(Status status) {
        JsonObject activity = new JsonObject();
        activity.addProperty("details", status.details());
        if (!status.state().isEmpty()) {
            activity.addProperty("state", status.state());
        }

        JsonObject timestamps = new JsonObject();
        timestamps.addProperty("start", SESSION_START);
        activity.add("timestamps", timestamps);

        // Whether an image appears depends on assets uploaded to the Discord
        // application under these keys. Naming them costs nothing when they
        // don't exist — Discord just renders the status without art.
        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", "logo");
        assets.addProperty("large_text", "Blurred Client");
        activity.add("assets", assets);

        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        args.add("activity", activity);

        JsonObject frame = new JsonObject();
        frame.addProperty("cmd", "SET_ACTIVITY");
        frame.add("args", args);
        frame.addProperty("nonce", UUID.randomUUID().toString());
        return frame.toString();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Discord RPC thread interrupted", e);
        }
    }

    // ------------------------------------------------------------------
    // Transport
    // ------------------------------------------------------------------

    /** One connected IPC socket, whichever kind this platform uses. */
    private interface Ipc extends AutoCloseable {
        void send(int opcode, String json) throws IOException;

        /** Read one frame and discard it. See {@link #pump}. */
        void receive() throws IOException;

        @Override
        void close();
    }

    /** Try each candidate socket until one accepts us, or give up. */
    private static Ipc connect() {
        for (int i = 0; i < MAX_SOCKETS; i++) {
            Ipc ipc = openSocket(i);
            if (ipc != null) {
                return ipc;
            }
        }
        return null;
    }

    private static Ipc openSocket(int index) {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("win")) {
            return openPipe("\\\\.\\pipe\\discord-ipc-" + index);
        }
        for (Path dir : unixSocketDirs()) {
            Ipc ipc = openUnix(dir.resolve("discord-ipc-" + index));
            if (ipc != null) {
                return ipc;
            }
        }
        return null;
    }

    /**
     * Where Discord puts its socket on Linux and macOS.
     *
     * <p>The base directory is the first of {@code XDG_RUNTIME_DIR},
     * {@code TMPDIR}, {@code TMP}, {@code TEMP}, {@code /tmp} that is set — the
     * order Discord itself uses. The subdirectories cover sandboxed installs:
     * Flatpak and Snap each re-root the socket inside their own tree, and a
     * Flatpak Discord is a very normal way to have Discord on Linux.
     */
    private static java.util.List<Path> unixSocketDirs() {
        String base = System.getenv("XDG_RUNTIME_DIR");
        if (base == null) base = System.getenv("TMPDIR");
        if (base == null) base = System.getenv("TMP");
        if (base == null) base = System.getenv("TEMP");
        if (base == null) base = "/tmp";

        Path root = Path.of(base);
        return java.util.List.of(
                root,
                root.resolve("app/com.discordapp.Discord"),
                root.resolve("app/com.discordapp.DiscordCanary"),
                root.resolve("snap.discord"));
    }

    private static Ipc openUnix(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try {
            SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
            channel.connect(UnixDomainSocketAddress.of(path));
            return new ChannelIpc(channel);
        } catch (IOException | UnsupportedOperationException e) {
            return null;
        }
    }

    private static Ipc openPipe(String name) {
        try {
            // A Windows named pipe opened this way behaves as a bidirectional
            // stream; seeking is meaningless on it, so only read/write are used
            // below. An absent pipe throws FileNotFoundException, which is the
            // "Discord isn't running" case rather than a failure.
            RandomAccessFile pipe = new RandomAccessFile(name, "rw");
            return new PipeIpc(pipe);
        } catch (IOException e) {
            return null;
        }
    }

    /** Frame header: opcode and length, both little-endian. */
    private static byte[] header(int opcode, int length) {
        return ByteBuffer.allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(opcode)
                .putInt(length)
                .array();
    }

    private static int lengthOf(byte[] header) {
        return ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(4);
    }

    /** Unix-socket transport. */
    private record ChannelIpc(SocketChannel channel) implements Ipc {
        @Override
        public void send(int opcode, String json) throws IOException {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            writeFully(ByteBuffer.wrap(header(opcode, body.length)));
            writeFully(ByteBuffer.wrap(body));
        }

        @Override
        public void receive() throws IOException {
            ByteBuffer head = readFully(8);
            int length = lengthOf(head.array());
            if (length > 0) {
                readFully(length);
            }
        }

        private void writeFully(ByteBuffer buffer) throws IOException {
            while (buffer.hasRemaining()) {
                if (channel.write(buffer) < 0) {
                    throw new IOException("Discord closed the socket");
                }
            }
        }

        private ByteBuffer readFully(int n) throws IOException {
            ByteBuffer buffer = ByteBuffer.allocate(n);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) {
                    throw new IOException("Discord closed the socket");
                }
            }
            return buffer;
        }

        @Override
        public void close() {
            try {
                channel.close();
            } catch (IOException ignored) {
                // Closing a socket that's already gone is the normal path here.
            }
        }
    }

    /** Windows named-pipe transport. */
    private record PipeIpc(RandomAccessFile pipe) implements Ipc {
        @Override
        public void send(int opcode, String json) throws IOException {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            pipe.write(header(opcode, body.length));
            pipe.write(body);
        }

        @Override
        public void receive() throws IOException {
            byte[] head = new byte[8];
            pipe.readFully(head);
            int length = lengthOf(head);
            if (length > 0) {
                pipe.readFully(new byte[length]);
            }
        }

        @Override
        public void close() {
            try {
                pipe.close();
            } catch (IOException ignored) {
                // As above.
            }
        }
    }
}
