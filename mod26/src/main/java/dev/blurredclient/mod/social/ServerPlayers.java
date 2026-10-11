package dev.blurredclient.mod.social;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Everyone on the server you're currently playing on.
 *
 * <p>Read straight off the client's player list — the same data the tab list
 * draws. That matters for what this is for: it's the one roster that contains
 * players regardless of which client they run. Blurred's own crew list only
 * knows people whose launcher is in our lobby; this knows the Essential user
 * standing next to you, the vanilla player who has never heard of either
 * client, and the friend you were going to add anyway.
 *
 * <p>No caching and no snapshotting: {@code getPlayerList()} is already a live
 * view maintained by the network handler, and a copy would only ever be a
 * staler version of it.
 */
public final class ServerPlayers {

    private ServerPlayers() {}

    /**
     * Everyone else on this server, alphabetically.
     *
     * <p>Ourselves excluded — a "message this player" button next to your own
     * name is a button that can only ever do something silly.
     *
     * @return the names, or an empty list in singleplayer and on the menus
     */
    public static List<String> others(Minecraft client) {
        ClientPacketListener net = client.getConnection();
        if (net == null || client.player == null) {
            return List.of();
        }

        // From the session rather than from our own GameProfile: the profile
        // became a record in 1.21.9's authlib, so reading a name off one needs
        // a version branch, and the session has carried the same accessor
        // throughout.
        String self = client.getUser().getName();
        List<String> names = new ArrayList<>();
        for (PlayerInfo entry : net.getOnlinePlayers()) {
            String name = nameOf(entry);
            if (!name.isEmpty() && !name.equalsIgnoreCase(self)) {
                names.add(name);
            }
        }
        names.sort(Comparator.comparing(n -> n.toLowerCase(Locale.ROOT)));
        return names;
    }

    /** True when there's a server player list to show at all. */
    public static boolean onServer(Minecraft client) {
        return client.getConnection() != null && client.player != null;
    }

    /**
     * The account name on a player list entry.
     *
     * <p>Mojang's {@code GameProfile} became a record in the authlib that ships
     * with 1.21.9, so the accessor lost its {@code get} prefix — the same
     * branch {@code PlayerListPingMixin} carries.
     */
    private static String nameOf(PlayerInfo entry) {
        if (entry.getProfile() == null) {
            return "";
        }
        String name = entry.getProfile().name();
        return name == null ? "" : name;
    }
}
