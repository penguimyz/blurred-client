package dev.blurredclient.mod.bridge;

/**
 * One chat line relayed from the launcher.
 *
 * @param conversation channel name, or the other party's nick for a DM
 * @param kind         "message" | "action" | "notice" | "system"
 * @param mine         true when we sent it
 * @param receivedAt   local arrival time, used for toast expiry
 */
public record ChatLine(
        String conversation,
        String from,
        String text,
        String kind,
        boolean mine,
        long receivedAt) {

    public boolean isSystem() {
        return "system".equals(kind);
    }
}
