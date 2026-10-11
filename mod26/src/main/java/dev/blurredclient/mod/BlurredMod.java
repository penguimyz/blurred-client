package dev.blurredclient.mod;

import com.mojang.blaze3d.platform.InputConstants;
import dev.blurredclient.mod.bridge.LauncherBridge;
import dev.blurredclient.mod.config.BlurredConfig;
import dev.blurredclient.mod.hud.CpsTracker;
import dev.blurredclient.mod.hud.HudRenderer;
import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.screen.clickgui.ClickGuiScreen;
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
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the Blurred in-game companion.
 *
 * <p>Client-only by design: everything here is HUD, overlay and a loopback
 * socket to the launcher. Nothing touches world state, movement or packets, so
 * the mod is safe on servers that don't have it and cannot be mistaken for a
 * cheat — which matters, because a client-branded mod that pokes at gameplay is
 * exactly what anticheat flags.
 */
public class BlurredMod implements ClientModInitializer {
    public static final String MOD_ID = "blurred";
    public static final Logger LOGGER = LoggerFactory.getLogger("Blurred");

    private static KeyMapping openCrew;
    private static KeyMapping openMenu;

    /**
     * Last server address reported to the launcher. Kept so the per-tick check
     * only sends on an actual change instead of every tick.
     *
     * <p>Volatile because {@link #onBridgeConnected} clears it from the bridge's
     * reader thread while the client thread is reading it in {@link #trackServer}.
     */
    private static volatile String reportedServer = "";

    @Override
    public void onInitializeClient() {
        LOGGER.info("Blurred companion starting");

        BlurredConfig.load();
        LauncherBridge.get().start();

        if (Essential.isPresent()) {
            LOGGER.info(
                    "Essential detected — Blurred will {} the menus. HUD, capes and crew are unaffected.",
                    Essential.shouldStyleMenus() ? "still style" : "leave");
        }

        // 1.21.9+ made categories a type rather than a translation key. MISC
        // rather than a custom category: a new Category has to be registered
        // into the game's list to appear in the controls screen at all, and one
        // keybind doesn't justify a section of its own.
        openCrew = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.blurred.crew", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, KeyMapping.Category.MISC));
        openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.blurred.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, KeyMapping.Category.MISC));
        Modules.ZOOM.bindKey(KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.blurred.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, KeyMapping.Category.MISC)));

        dev.blurredclient.mod.fx.WorldDraw.init();
        dev.blurredclient.mod.fx.Reconnect.init();
        dev.blurredclient.mod.fx.ShaderFx.register();
        dev.blurredclient.mod.fx.FoliageMarks.register();
        dev.blurredclient.mod.fx.SwordPacks.register();
        dev.blurredclient.mod.fx.WorldDraw.add(f -> Modules.ITEM_SKINS.frame());
        dev.blurredclient.mod.fx.WorldDraw.add(f -> {
            if (Modules.MOTION_BLUR.isActive()) {
                dev.blurredclient.mod.fx.MotionBlurState.capture(f);
            }
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> dev.blurredclient.mod.fx.CombatEvents.reset());
        dev.blurredclient.mod.fx.WorldDraw.add(dev.blurredclient.mod.fx.ContainerView.INSTANCE);
        dev.blurredclient.mod.fx.WorldDraw.addLate(dev.blurredclient.mod.fx.ContainerView.INSTANCE::renderGrid);
        dev.blurredclient.mod.fx.WorldDraw.add(dev.blurredclient.mod.fx.Hologram.INSTANCE);
        // 26.x: HUD content is a named element in Fabric's ordered list; ours goes last.
        HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
                (context, tickCounter) -> HudRenderer.render(context, Minecraft.getInstance()));

        ClientTickEvents.START_CLIENT_TICK.register(CpsTracker::onTickStart);
        ClientTickEvents.END_CLIENT_TICK.register(BlurredMod::onTick);
        // Client-side only: fires when *you* attack, used for hit particles.
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClientSide()) {
                dev.blurredclient.mod.fx.CombatEvents.attacked(entity);
                Modules.HIT_EFFECTS.onHit(entity);
                Modules.TARGET_RING.onHit(entity);
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
        registerMenuButtons();
        Runtime.getRuntime().addShutdownHook(new Thread(dev.blurredclient.mod.hud.SystemMedia::stop, "Blurred media stop"));
    }

    /**
     * Add Crew and Cosmetics entries to the title and pause menus.
     *
     * <p>Uses Fabric's screen events rather than a mixin per screen: the event
     * fires after vanilla has laid its own widgets out, so ours can be placed
     * relative to the real layout, and it doesn't fight other mods adding
     * buttons to the same screens.
     */
    private static void registerMenuButtons() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            openDevScreen(client, screen);
            dev.blurredclient.mod.fx.ContainerView.INSTANCE.onScreenInit(screen, width, height);
            // Cinematic containers: move the inventory to the right so the block shows.
            if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> handled
                    && Modules.CINEMATIC_CONTAINERS.moveAside()) {
                var acc = (dev.blurredclient.mod.mixin.HandledScreenAccessor) handled;
                acc.blurred$setX(width - acc.blurred$backgroundWidth() - 24);
            }
            boolean title = screen instanceof TitleScreen;
            boolean pause = screen instanceof PauseScreen;
            if (!title && !pause) {
                return;
            }

            // Bottom-left, stacked upward from above the version string.
            // Vanilla's centred column varies in height with realms and mods,
            // so overlapping it is how added buttons end up unclickable — and
            // the bottom line is already taken by "Minecraft 1.21.11/Fabric".
            int x = 6;
            int y = height - 98;

            Screens.getWidgets(screen).add(Button.builder(
                            BlurredFont.apply(Component.translatable("menu.blurred.crew")),
                            b -> client.gui.setScreen(new CrewScreen()))
                    .bounds(x, y, 92, 20)
                    .build());

            Screens.getWidgets(screen).add(Button.builder(
                            BlurredFont.apply(Component.translatable("menu.blurred.cosmetics")),
                            b -> client.gui.setScreen(new CosmeticsScreen(screen)))
                    .bounds(x, y + 24, 92, 20)
                    .build());

            Screens.getWidgets(screen).add(Button.builder(
                            BlurredFont.apply(Component.translatable("menu.blurred.settings")),
                            b -> client.gui.setScreen(new ClickGuiScreen(screen)))
                    .bounds(x, y + 48, 92, 20)
                    .build());
        });
    }

    /** The key currently bound to open the mod menu, so the menu can close on it too. */
    public static int menuKeyCode() {
        return openMenu == null ? -1 : KeyMappingHelper.getBoundKeyOf(openMenu).getValue();
    }

    private static boolean devScreenOpened;

    /**
     * In a development run started with {@code -PdevScreen=kit}, jump straight
     * from the title screen to the UI kit test screen. Never active in a
     * release build: it's gated on Fabric's development-environment flag.
     */
    private static void openDevScreen(Minecraft client, net.minecraft.client.gui.screens.Screen screen) {
        boolean title = screen instanceof TitleScreen
                || screen instanceof dev.blurredclient.mod.screen.title.BlurredTitleScreen;
        if (devScreenOpened || !title
                || !net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        String which = System.getProperty("blurred.devScreen");
        if ("kit".equals(which)) {
            devScreenOpened = true;
            client.schedule(() -> client.gui.setScreen(new dev.blurredclient.mod.screen.KitTestScreen()));
        } else if ("hud".equals(which)) {
            devScreenOpened = true;
            // Turn every HUD module on so the editor shows them all.
            for (var m : dev.blurredclient.mod.module.Modules.hud()) {
                m.setEnabled(true);
            }
            client.schedule(() -> client.gui.setScreen(new dev.blurredclient.mod.screen.HudEditorScreen(screen)));
        } else if ("clickgui".equals(which)) {
            devScreenOpened = true;
            client.schedule(() -> client.gui.setScreen(new ClickGuiScreen(screen)));
        }
    }

    /**
     * Forget what we last told the launcher, so the next tick re-sends it.
     *
     * <p>Called when the bridge connects. The launcher holds "where this player
     * is" in memory only, so a launcher that restarted — or one that started
     * after the game did — knows nothing, and the change detection in
     * {@link #trackServer} would happily never mention the server we've been on
     * for the last hour. Clearing the record turns the next tick into a fresh
     * report.
     */
    public static void onBridgeConnected() {
        reportedServer = "";
        LanWorlds.reset();
    }

    private static void onTick(Minecraft client) {
        while (openCrew.consumeClick()) {
            client.gui.setScreen(new CrewScreen());
        }
        while (openMenu.consumeClick()) {
            if (client.gui.screen() == null) {
                if (Modules.HOLOGRAM.isActive() && client.level != null) {
                    dev.blurredclient.mod.fx.Hologram.INSTANCE.toggle();
                } else {
                    client.gui.setScreen(new ClickGuiScreen(null));
                }
            }
        }

        trackServer(client);
        dev.blurredclient.mod.fx.KeyToggles.tick(client);
        dev.blurredclient.mod.fx.Reconnect.track(client);
        dev.blurredclient.mod.fx.ShaderFx.tick(client);
        dev.blurredclient.mod.fx.SwordPacks.tick(client);
        dev.blurredclient.mod.fx.EntityCulling.tick(client);
        Modules.NOW_PLAYING.tickWatcher();

        for (var m : Modules.REGISTRY.all()) {
            if (m instanceof dev.blurredclient.mod.module.feature.Ticking t && m.isActive()) {
                t.tick(client);
            }
        }
        DiscordPresence.update(client);
    }

    /**
     * Report where we're playing, whether that's someone else's server or a
     * world of ours that's open to LAN.
     *
     * <p>Order matters: a real server wins. You cannot be connected to one and
     * hosting the other at the same time, but checking the multiplayer case
     * first means the transition on disconnect is a single clean change rather
     * than a frame of ambiguity.
     */
    private static void trackServer(Minecraft client) {
        if (!Modules.CREW.shareServer()) {
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
                // A different server is a different set of people, and a
                // whisper thread with someone who isn't here any more is a
                // conversation you can't continue. Drop it with the server.
                Whispers.reset();
                LauncherBridge.get().reportServer(reportedServer);
            }
            return;
        }

        // Singleplayer: share the address only once the world is open to LAN.
        // LanWorlds owns its own change detection, so this just mirrors the
        // result into our record of what was last sent.
        reportedServer = LanWorlds.poll(client);
    }

}
