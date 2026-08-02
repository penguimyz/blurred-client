package dev.blurredclient.mod.bridge;

/**
 * A crew member as relayed by the launcher. Mirrors {@code models/chat.rs}.
 *
 * @param server the Minecraft server they're on, or empty if unknown — this is
 *               what makes "join" possible from the in-game crew list.
 */
public record Friend(String nick, String note, boolean online, String status, String server) {

    public boolean isAccepted() {
        return "accepted".equals(status);
    }

    public boolean isJoinable() {
        return online && server != null && !server.isBlank();
    }

    /** Records are immutable, so a presence update rebuilds rather than sets. */
    public Friend withOnline(boolean nowOnline) {
        return new Friend(nick, note, nowOnline, status, server);
    }
}
