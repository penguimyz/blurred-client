package dev.blurredclient.mod.module.feature;

import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.TextSetting;

/**
 * "Blurred Client — On a server" on your Discord profile (see
 * social.DiscordPresence).
 *
 * <p>The server address is <b>off by default</b>: a Discord status is visible
 * to everyone who can see your profile, a much wider audience than your crew.
 */
public final class DiscordModule extends Module {
    private final BoolSetting showServer;
    private final TextSetting appId;

    public DiscordModule() {
        super("discord", "Discord presence", "Shows what you're playing on your Discord profile.",
                Category.CREW, true);
        this.showServer = add(new BoolSetting("show_server", "Show the server",
                "Put the server's address in your Discord status. Everyone who can see your profile sees it.", false));
        this.appId = add(new TextSetting("app_id", "App ID",
                "Leave blank to use the launcher's. Only set this to show under a different Discord app.", "", 32));
    }

    public boolean showServer() {
        return showServer.isOn();
    }

    /** This instance's app ID if set, else the one the launcher passes in. */
    public String appId() {
        String own = appId.get();
        if (own != null && !own.isBlank()) {
            return own.trim();
        }
        return System.getProperty("blurred.discord.appId", "").trim();
    }
}
