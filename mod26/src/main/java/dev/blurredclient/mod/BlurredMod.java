package dev.blurredclient.mod;

import com.mojang.blaze3d.platform.InputConstants;
import dev.blurredclient.mod.bridge.LauncherBridge;
import dev.blurredclient.mod.config.BlurredConfig;
import dev.blurredclient.mod.hud.HudRenderer;
import dev.blurredclient.mod.screen.ConfigScreen;
import dev.blurredclient.mod.screen.CosmeticsScreen;
import dev.blurredclient.mod.screen.CrewScreen;
import dev.blurredclient.mod.social.DiscordPresence;
import dev.blurredclient.mod.social.Essential;
import dev.blurredclient.mod.social.LanWorlds;
import dev.blurredclient.mod.social.Whispers;
import dev.blurredclient.mod.ui.BlurredFont;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the Minecraft 26.x build of the Blurred companion.
 *
 * <p>Client-only by design, same as the 1.21.x build: everything here is HUD,
 * overlay and a loopback socket to the launcher. Nothing touches world state,
 * movement or packets.
 *
 * <h2>What differs from the 1.21.x build</h2>
 *
 * The two trees are separate because 26.x dropped obfuscation and Fabric
 * dropped yarn with it — see {@code mod26/settings.gradle}. Beyond the renames,
 * two things genuinely changed shape and are worth naming here:
 *
 * <ul>
 *   <li><b>The HUD.</b> {@code HudRenderCallback} is gone. HUD content is now
 *       registered as a named {@link
 *       net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement} in an
 *       ordered list, which is a better fit anyway: ours goes last, so it
 *       draws over vanilla's rather than racing it.</li>
 *   <li><b>Rendering.</b> Drawing is no longer immediate-mode against a
 *       {@code DrawContext}; screens and HUD elements *extract render state*
 *       into a {@code GuiGraphicsExtractor} which the (now possibly Vulkan)
 *       backend replays. The primitives we use — {@code fill} and {@code text}
 *       — survived with the same shape, which is the only reason the HUD ported
 *       as cleanly as it did.</li>
 * </ul>
 *
 *   <li><b>Reaching the screens.</b> Fabric renamed the keybind helper to
 *       {@code KeyMappingHelper} and {@code Screens.getButtons} to
 *       {@link Screens#getWidgets}, which now returns every widget rather than
 *       only the buttons — so the menu entries are appended to that list
 *       instead.</li>
 * </ul>
 */
public class BlurredMod implements ClientModInitializer {
    public static final String MOD_ID = "blurred";
    public static final Logger LOGGER = LoggerFactory.getLogger("Blurred");

    /** Our HUD element's id, as the registry knows it. */
    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(MOD_ID, "hud");

    private static KeyMapping openCrew;

    /**
     * Last server address reported to the launcher, so the per-tick check only
     * sends on an actual change. Volatile: cleared from the bridge's reader
     * thread while the client thread reads it.
     */
    private static volatile String reportedServer = "";

    @Override
    public void onInitializeClient() {
        LOGGER.info("Blurred companion starting (Minecraft 26.x build)");

        BlurredConfig.get();
        LauncherBridge.get().start();

        if (Essential.isPresent()) {
            LOGGER.info("Essential detected — Blurred will leave the menus alone.");
        }

        // MISC rather than a category of our own: a new Category has to be
        // registered into the game's list to appear in the controls screen at
        // all, and one keybind doesn't justify a section.
        openCrew = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.blurred.crew",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                KeyMapping.Category.MISC));

        // Last in the list, so the readout sits over vanilla's HUD rather than
        // under it. `addLast` replaces the old HudRenderCallback registration.
        HudElementRegistry.addLast(
                HUD_ID, (ctx, tickCounter) -> HudRenderer.render(ctx, Minecraft.getInstance()));

        ClientTickEvents.END_CLIENT_TICK.register(BlurredMod::onTick);
        registerMenuButtons();
    }

    /**
     * Add Crew, Cosmetics and Settings entries to the title and pause menus.
     *
     * <p>Fabric's screen event rather than a mixin per screen: it fires after
     * vanilla has laid its own widgets out, so ours can be placed relative to
     * the real layout, and it doesn't fight other mods adding buttons to the
     * same screens.
     */
    private static void registerMenuButtons() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof TitleScreen) && !(screen instanceof PauseScreen)) {
                return;
            }

            // Bottom-left, stacked upward from above the version string.
            // Vanilla's centred column varies in height with realms and mods,
            // so overlapping it is how added buttons end up unclickable.
            int x = 6;
            int y = height - 98;

            add(screen, "menu.blurred.crew", x, y, b -> client.gui.setScreen(new CrewScreen()));
            add(screen, "menu.blurred.cosmetics", x, y + 24,
                    b -> client.gui.setScreen(new CosmeticsScreen(screen)));
            add(screen, "menu.blurred.settings", x, y + 48,
                    b -> client.gui.setScreen(new ConfigScreen(screen)));
        });
    }

    /**
     * Append one menu entry.
     *
     * <p>{@code Screens.getWidgets} replaced {@code getButtons} in 26.x and
     * hands back every widget on the screen, not just the buttons — adding to
     * it is still how a mod contributes one.
     */
    private static void add(
            net.minecraft.client.gui.screens.Screen screen,
            String translationKey,
            int x,
            int y,
            Button.OnPress onPress) {
        Screens.getWidgets(screen).add(Button.builder(
                        BlurredFont.apply(Component.translatable(translationKey)), onPress)
                .bounds(x, y, 92, 20)
                .build());
    }

    /**
     * Forget what we last told the launcher, so the next tick re-sends it.
     *
     * <p>Called when the bridge connects: a launcher that restarted knows
     * nothing, and the change detection in {@link #trackServer} would otherwise
     * never mention the server we've been on for the last hour.
     */
    public static void onBridgeConnected() {
        reportedServer = "";
        LanWorlds.reset();
    }

    private static void onTick(Minecraft client) {
        while (openCrew.consumeClick()) {
            client.gui.setScreen(new CrewScreen());
        }

        trackServer(client);
        DiscordPresence.update(client);
    }

    /**
     * Report where we're playing, whether that's someone else's server or a
     * world of ours that's open to LAN. A real server wins.
     */
    private static void trackServer(Minecraft client) {
        if (!BlurredConfig.get().shareServer) {
            if (!reportedServer.isEmpty()) {
                reportedServer = "";
                LauncherBridge.get().reportServer("");
                LanWorlds.reset();
            }
            return;
        }

        ServerData entry = client.getCurrentServer();
        if (entry != null) {
            LanWorlds.reset();
            if (!entry.ip.equals(reportedServer)) {
                reportedServer = entry.ip;
                // A different server is a different set of people; a whisper
                // thread with someone who isn't here can't be continued.
                Whispers.reset();
                LauncherBridge.get().reportServer(reportedServer);
            }
            return;
        }

        reportedServer = LanWorlds.poll(client);
    }
}
