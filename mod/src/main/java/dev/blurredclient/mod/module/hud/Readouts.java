package dev.blurredclient.mod.module.hud;

import dev.blurredclient.mod.Theme;
import dev.blurredclient.mod.hud.CpsTracker;
import dev.blurredclient.mod.module.ModuleIds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.math.MathHelper;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * The six readouts v1 had, one small module each.
 *
 * <p>Kept together in one file because each is a few lines and they share the
 * colour scales; anything bigger than this (keystrokes, armour) gets its own
 * file.
 */
public final class Readouts {
    private Readouts() {}

    public static final class Fps extends TextHudModule {
        /** Rolling smoothing so the number doesn't strobe every frame. */
        private float smoothed = 60f;

        public Fps() {
            super(ModuleIds.FPS, "Frame rate", "Smoothed, and coloured by how healthy it is.", true, 0);
        }

        @Override
        protected Readout compute(MinecraftClient client, boolean preview) {
            if (preview) {
                return new Readout("999 FPS", Theme.SUCCESS);
            }
            // Exponential smoothing: fast enough to track a real drop, slow
            // enough that the digits stay readable.
            smoothed = MathHelper.lerp(0.08f, smoothed, client.getCurrentFps());
            int fps = Math.round(smoothed);
            return new Readout(fps + " FPS", fpsColor(fps));
        }

        private static int fpsColor(int fps) {
            if (fps >= 100) return Theme.SUCCESS;
            if (fps >= 55) return Theme.TEXT;
            if (fps >= 30) return Theme.WARNING;
            return Theme.DANGER;
        }
    }

    public static final class Ping extends TextHudModule {
        /** Smoothed too: ping arrives from the server in discrete jumps. */
        private float smoothed = -1f;

        public Ping() {
            super(ModuleIds.PING, "Ping", "Your real latency, as the server measures it.", true, 1);
        }

        @Override
        protected Readout compute(MinecraftClient client, boolean preview) {
            if (preview) {
                return new Readout("999 ms", Theme.TEXT);
            }
            int latency = measure(client);
            if (latency < 0) {
                // Reset, so reconnecting doesn't average against a stale value
                // from the previous server.
                smoothed = -1f;
                return null;
            }
            smoothed = smoothed < 0 ? latency : MathHelper.lerp(0.25f, smoothed, latency);
            int ms = Math.round(smoothed);
            return new Readout(ms + " ms", pingColor(ms));
        }

        /**
         * Our own latency, from our player-list entry.
         *
         * <p>Not {@code getCurrentServerEntry().ping}: that is filled in by the
         * multiplayer list's status ping and never updated after connecting, so
         * it's frozen for the session and absent after a direct connect. The
         * player-list entry carries the server's keep-alive round trip,
         * refreshed continuously.
         *
         * @return milliseconds, or -1 in singleplayer / before the first measurement
         */
        private static int measure(MinecraftClient client) {
            if (client.player == null || client.getNetworkHandler() == null) {
                return -1;
            }
            PlayerListEntry self = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
            if (self == null) {
                return -1;
            }
            int latency = self.getLatency();
            // The integrated server reports 0, which is true but not worth a readout.
            return latency <= 0 ? -1 : latency;
        }

        private static int pingColor(int ms) {
            if (ms < 60) return Theme.SUCCESS;
            if (ms < 120) return Theme.TEXT;
            if (ms < 250) return Theme.WARNING;
            return Theme.DANGER;
        }
    }

    public static final class Coords extends TextHudModule {
        public Coords() {
            // Off by default: coordinates on screen end up in every screenshot
            // and stream, leaking where your base is.
            super(ModuleIds.COORDS, "Coordinates",
                    "Off by default — coordinates on screen end up in every screenshot and stream you make.",
                    false, 2);
        }

        @Override
        protected Readout compute(MinecraftClient client, boolean preview) {
            if (preview) {
                return new Readout("-29999 320 -29999", Theme.TEXT);
            }
            if (client.player == null) {
                return null;
            }
            return new Readout(String.format("%.0f %.0f %.0f",
                    client.player.getX(), client.player.getY(), client.player.getZ()), Theme.TEXT);
        }
    }

    public static final class Direction extends TextHudModule {
        public Direction() {
            super(ModuleIds.DIRECTION, "Facing", "Compass direction, to eight points.", false, 3);
        }

        @Override
        protected Readout compute(MinecraftClient client, boolean preview) {
            if (preview) {
                return new Readout("NW", Theme.TEXT);
            }
            return client.player == null ? null : new Readout(facing(client.player.getYaw()), Theme.TEXT);
        }

        /** Compass point from a yaw angle. */
        static String facing(float yaw) {
            // Normalise to 0..360 then bucket into eight 45-degree sectors,
            // offset by half a sector so each label is centred on its heading.
            float a = MathHelper.wrapDegrees(yaw) + 180f;
            int idx = Math.round(a / 45f) & 7;
            return switch (idx) {
                case 0 -> "N";
                case 1 -> "NE";
                case 2 -> "E";
                case 3 -> "SE";
                case 4 -> "S";
                case 5 -> "SW";
                case 6 -> "W";
                default -> "NW";
            };
        }
    }

    public static final class Cps extends TextHudModule {
        public Cps() {
            super(ModuleIds.CPS, "Clicks per second", "Left and right, over the last second.", false, 4);
        }

        @Override
        protected Readout compute(MinecraftClient client, boolean preview) {
            if (preview) {
                return new Readout("20/20 CPS", Theme.TEXT);
            }
            return new Readout(CpsTracker.leftCps() + "/" + CpsTracker.rightCps() + " CPS", Theme.TEXT);
        }
    }

    public static final class Clock extends TextHudModule {
        /**
         * 12-hour with am/pm — the way a clock in the corner of a screen is
         * read everywhere outside a timetable.
         */
        private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("h:mm a");

        public Clock() {
            super(ModuleIds.CLOCK, "Clock", "The real time. Useful when the game covers your taskbar.", false, 5);
        }

        @Override
        protected Readout compute(MinecraftClient client, boolean preview) {
            return new Readout(preview ? "12:59 PM" : LocalTime.now().format(FORMAT), Theme.TEXT_DIM);
        }
    }

    public static final class Speed extends TextHudModule {
        private double lastX = Double.NaN;
        private double lastZ;
        private long lastTick = -1;
        private float bps;

        public Speed() {
            super(ModuleIds.SPEED, "Speed", "How fast you're moving, in blocks per second (horizontal).", false, 6);
        }

        @Override
        protected Readout compute(MinecraftClient client, boolean preview) {
            if (preview) {
                return new Readout("12.34 b/s", Theme.TEXT);
            }
            if (client.player == null || client.world == null) {
                return null;
            }
            // Sample once per game tick: position deltas between frames are
            // too noisy to read.
            long tick = client.world.getTime();
            if (tick != lastTick) {
                double x = client.player.getX();
                double z = client.player.getZ();
                if (!Double.isNaN(lastX) && tick == lastTick + 1) {
                    double d = Math.sqrt((x - lastX) * (x - lastX) + (z - lastZ) * (z - lastZ)) * 20.0;
                    bps = MathHelper.lerp(0.35f, bps, (float) d);
                }
                lastX = x;
                lastZ = z;
                lastTick = tick;
            }
            return new Readout(String.format("%.2f b/s", bps), Theme.TEXT);
        }
    }

    public static final class Memory extends TextHudModule {
        public Memory() {
            super(ModuleIds.MEMORY, "Memory", "Java memory in use, out of the maximum the game may use.", false, 7);
        }

        @Override
        protected Readout compute(MinecraftClient client, boolean preview) {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
            long max = rt.maxMemory() / (1024 * 1024);
            if (preview) {
                used = 1024;
                max = 4096;
            }
            float f = used / (float) Math.max(1, max);
            int colour = f > 0.85f ? Theme.DANGER : f > 0.65f ? Theme.WARNING : Theme.TEXT;
            return new Readout(used + "/" + max + " MB", colour);
        }
    }

    public static final class Server extends TextHudModule {
        public Server() {
            super(ModuleIds.SERVER, "Server address", "The server you're on, or Singleplayer.", false, 8);
        }

        @Override
        protected Readout compute(MinecraftClient client, boolean preview) {
            if (preview) {
                return new Readout("play.example.net", Theme.TEXT_DIM);
            }
            var entry = client.getCurrentServerEntry();
            return new Readout(entry != null ? entry.address : "Singleplayer", Theme.TEXT_DIM);
        }
    }

    public static final class Day extends TextHudModule {
        public Day() {
            super(ModuleIds.DAY, "Day counter", "Which in-game day it is.", false, 9);
        }

        @Override
        protected Readout compute(MinecraftClient client, boolean preview) {
            if (preview) {
                return new Readout("Day 120", Theme.TEXT);
            }
            if (client.world == null) {
                return null;
            }
            return new Readout("Day " + (client.world.getTimeOfDay() / 24000L + 1), Theme.TEXT);
        }
    }
}
