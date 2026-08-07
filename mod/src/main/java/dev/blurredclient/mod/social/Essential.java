package dev.blurredclient.mod.social;

import dev.blurredclient.mod.config.BlurredConfig;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Getting along with Essential.
 *
 * <p>Essential is the other client-side mod most Blurred users are likely to
 * have, and it overlaps with us in exactly the places that hurt: it reskins the
 * main menu, it adds its own social panel, and it hosts worlds for friends.
 * Two mods reskinning the same screen produce unreadable chrome, and two mods
 * both claiming to be "the" social layer produce a confusing client.
 *
 * <p>So the rule is that Essential wins the overlap. Its menu treatment is the
 * more invasive of the two and its social features are the ones people install
 * it for; ours yield rather than fight. Everything Blurred does that Essential
 * doesn't — the HUD, capes, crew chat mirrored from the launcher, the client
 * badge — carries on unchanged.
 *
 * <h2>What this cannot do</h2>
 *
 * There is no interop <em>API</em> here, because Essential does not publish
 * one. Nothing in this class calls into Essential; it only asks the mod loader
 * whether Essential is installed and then gets out of the way. Anything more —
 * reading Essential's friend list, joining an Essential-hosted world through
 * their relay — would mean reflecting into another mod's internals, which
 * breaks on their next update and is not something to build a feature on.
 * Joining between the two clients works through the one mechanism both
 * understand: a real Minecraft server address, which is what
 * {@link LanWorlds} shares.
 */
public final class Essential {
    /** Their Fabric mod id. */
    private static final String MOD_ID = "essential";

    /** Resolved once — the loaded mod set cannot change at runtime. */
    private static Boolean present;

    private Essential() {}

    public static boolean isPresent() {
        if (present == null) {
            present = FabricLoader.getInstance().isModLoaded(MOD_ID);
        }
        return present;
    }

    /**
     * Should Blurred restyle Minecraft's menus right now?
     *
     * <p>False when Essential is installed and the user hasn't overridden it,
     * so the two don't paint over each other.
     */
    public static boolean shouldStyleMenus() {
        BlurredConfig cfg = BlurredConfig.get();
        if (!cfg.styleMenus) {
            return false;
        }
        return !(cfg.deferToEssential && isPresent());
    }
}
