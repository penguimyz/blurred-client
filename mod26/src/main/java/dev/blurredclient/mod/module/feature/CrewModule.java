package dev.blurredclient.mod.module.feature;

import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.module.ModuleIds;
import dev.blurredclient.mod.setting.BoolSetting;

/**
 * What the game shares with your crew through the launcher. Always on: it's a
 * group of privacy settings, not a feature you'd switch off as a whole.
 */
public final class CrewModule extends Module {
    private final BoolSetting chatToasts;
    private final BoolSetting shareServer;
    private final BoolSetting announceLan;

    public CrewModule() {
        super(ModuleIds.CREW, "Crew sharing",
                "What the game tells your crew, and how crew messages reach you in game.",
                Category.CREW, true);
        this.chatToasts = add(new BoolSetting("chat_toasts", "Chat toasts",
                "Raise a toast in game when a crew message arrives.", true));
        this.shareServer = add(new BoolSetting("share_server", "Share which server you're on",
                "Lets your crew click to join you. Only the address, only to your crew.", true));
        this.announceLan = add(new BoolSetting("announce_lan", "Announce LAN worlds",
                "When you open a singleplayer world to LAN, tell your crew the address so they get a one-click join.",
                true));
        announceLan.visibleIf(shareServer::isOn);
    }

    @Override
    public boolean toggleable() {
        return false;
    }

    public boolean chatToasts() {
        return chatToasts.isOn();
    }

    public boolean shareServer() {
        return shareServer.isOn();
    }

    /** LAN announcing only ever happens while server sharing is on too. */
    public boolean announceLan() {
        return shareServer.isOn() && announceLan.isOn();
    }
}
