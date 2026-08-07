package dev.blurredclient.mod.social;

import dev.blurredclient.mod.BlurredMod;
import dev.blurredclient.mod.bridge.LauncherBridge;
import dev.blurredclient.mod.config.BlurredConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;

/**
 * Playing a singleplayer world with other people.
 *
 * <p>Minecraft already has the mechanism: Open to LAN turns the integrated
 * server into a real one on a real port. What it doesn't do is tell anyone. The
 * host reads an IP out loud, and it only works for people on the same network.
 *
 * <p>This closes the first half of that gap. When a world goes open, the
 * address is pushed to the launcher, which shares it with your crew the same
 * way it shares which server you're on — so a crew member gets a one-click join
 * instead of a dictated number. It's the same {@code playing} channel the mod
 * already uses for multiplayer servers, deliberately: to everyone else a LAN
 * world and a dedicated server are just an address, so they need no new
 * handling and nothing has to know which it is.
 *
 * <h2>Who can actually join</h2>
 *
 * <ul>
 *   <li><b>Blurred users on your network:</b> click to join, straight away.</li>
 *   <li><b>Essential users on your network:</b> the address is a normal server
 *       address, so it works in their multiplayer list like any other. They
 *       don't get the one-click, because Essential has no API for us to hand it
 *       to.</li>
 *   <li><b>Anyone outside your network:</b> not without port forwarding, on
 *       either client. Essential's own world hosting tunnels through their
 *       relay servers; Blurred has no relay and doesn't pretend to. The
 *       launcher's Servers tab is the honest answer for playing with people
 *       who aren't on your LAN.</li>
 * </ul>
 */
public final class LanWorlds {
    /** Last address reported, so a tick loop only sends on a real change. */
    private static String reported = "";

    private LanWorlds() {}

    /**
     * Called each tick. Reports the LAN address when a world is open, and
     * clears it when it isn't.
     *
     * @return the address currently being shared, or an empty string
     */
    public static String poll(MinecraftClient client) {
        String current = address(client);
        if (!current.equals(reported)) {
            reported = current;
            LauncherBridge.get().reportServer(current);
            if (!current.isEmpty()) {
                BlurredMod.LOGGER.info("World open to LAN at {} — shared with your crew", current);
            }
        }
        return current;
    }

    /** Forget what we last reported, so the next poll re-sends. */
    public static void reset() {
        reported = "";
    }

    /** True when a singleplayer world is currently published to LAN. */
    public static boolean isOpen(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        return server != null && server.isRemote();
    }

    /** The address to hand out, or empty when there's nothing to hand out. */
    private static String address(MinecraftClient client) {
        if (!BlurredConfig.get().announceLanWorlds || !BlurredConfig.get().shareServer) {
            return "";
        }
        IntegratedServer server = client.getServer();
        if (server == null || !server.isRemote()) {
            return "";
        }
        int port = server.getServerPort();
        if (port <= 0) {
            return "";
        }

        // Must be the LAN address, not localhost. The integrated server binds
        // every interface, so localhost is technically correct and completely
        // useless to anyone else — it would resolve to their own machine.
        String host = lanAddress();
        return host == null ? "" : host + ":" + port;
    }

    /**
     * This machine's address on the local network.
     *
     * <p>Walks the interfaces looking for one that is up, not loopback, not
     * virtual, and holds a site-local IPv4. Site-local is the filter that
     * matters: it's what picks the 192.168/10./172.16 address a router handed
     * out, rather than a link-local autoconfiguration address or a VPN's
     * tunnel endpoint, both of which are up and neither of which anyone can
     * reach you on.
     *
     * @return the address, or null if there's no usable one (no network, or
     *         only interfaces we can't vouch for)
     */
    private static String lanAddress() {
        try {
            var interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface nif = interfaces.nextElement();
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) {
                    continue;
                }
                var addresses = nif.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    if (address instanceof java.net.Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (java.net.SocketException e) {
            BlurredMod.LOGGER.debug("Could not enumerate network interfaces: {}", e.toString());
        }
        return null;
    }
}
