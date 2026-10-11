package dev.blurredclient.mod.fx;

import dev.blurredclient.mod.module.Modules;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

/**
 * Auto reconnect: on the disconnect screen after leaving a server, a button
 * counts down and rejoins; click it to cancel. Gives up after the set
 * number of tries in a row. Never runs for singleplayer.
 */
public final class Reconnect {
    private Reconnect() {}

    private static ServerInfo last;
    private static int tries;

    /** Remember the server you're on (called every tick). */
    public static void track(MinecraftClient client) {
        ServerInfo now = client.getCurrentServerEntry();
        if (now != null) {
            if (last != now) {
                last = now;
            }
            if (client.player != null && client.player.age > 200) {
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
            final ServerInfo server = last;
            final long start = System.nanoTime();
            final boolean[] cancelled = {false};
            ButtonWidget button = ButtonWidget.builder(Text.literal(""), b -> {
                cancelled[0] = true;
                b.setMessage(Text.literal("Auto reconnect cancelled"));
                b.active = false;
            }).dimensions(w / 2 - 100, h - 40, 200, 20).build();
            Screens.getButtons(screen).add(button);
            ScreenEvents.afterTick(screen).register(s -> {
                if (cancelled[0]) {
                    return;
                }
                int left = Modules.AUTO_RECONNECT.delaySeconds() - (int) ((System.nanoTime() - start) / 1_000_000_000L);
                if (left > 0) {
                    button.setMessage(Text.literal("Reconnecting in " + left + "s  (click to cancel)"));
                    return;
                }
                cancelled[0] = true;
                tries++;
                connect(client, server);
            });
        });
    }

    private static void connect(MinecraftClient client, ServerInfo server) {
        Screen parent = new MultiplayerScreen(new TitleScreen());
        ConnectScreen.connect(parent, client, ServerAddress.parse(server.address), server, false, null);
    }
}
