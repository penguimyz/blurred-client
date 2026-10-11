package dev.blurredclient.mod.fx;

import dev.blurredclient.mod.module.Modules;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

/**
 * Auto reconnect: on the disconnect screen after leaving a server, a button
 * counts down and rejoins; click it to cancel. Gives up after the set
 * number of tries in a row. Never runs for singleplayer.
 */
public final class Reconnect {
    private Reconnect() {}

    private static ServerData last;
    private static int tries;

    /** Remember the server you're on (called every tick). */
    public static void track(Minecraft client) {
        ServerData now = client.getCurrentServer();
        if (now != null) {
            if (last != now) {
                last = now;
            }
            if (client.player != null && client.player.tickCount > 200) {
                // Stayed connected for ten seconds: the tries count starts over.
                tries = 0;
            }
        }
    }

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof DisconnectedScreen) || !Modules.AUTO_RECONNECT.isActive() || last == null) {
                return;
            }
            if (tries >= Modules.AUTO_RECONNECT.attempts()) {
                return;
            }
            final ServerData server = last;
            final long start = System.nanoTime();
            final boolean[] cancelled = {false};
            Button button = Button.builder(Component.literal(""), b -> {
                cancelled[0] = true;
                b.setMessage(Component.literal("Auto reconnect cancelled"));
                b.active = false;
            }).bounds(w / 2 - 100, h - 40, 200, 20).build();
            Screens.getWidgets(screen).add(button);
            ScreenEvents.afterTick(screen).register(s -> {
                if (cancelled[0]) {
                    return;
                }
                int left = Modules.AUTO_RECONNECT.delaySeconds() - (int) ((System.nanoTime() - start) / 1_000_000_000L);
                if (left > 0) {
                    button.setMessage(Component.literal("Reconnecting in " + left + "s  (click to cancel)"));
                    return;
                }
                cancelled[0] = true;
                tries++;
                connect(client, server);
            });
        });
    }

    private static void connect(Minecraft client, ServerData server) {
        Screen parent = new JoinMultiplayerScreen(new TitleScreen());
        ConnectScreen.startConnecting(parent, client, ServerAddress.parseString(server.ip), server, false, null);
    }
}
