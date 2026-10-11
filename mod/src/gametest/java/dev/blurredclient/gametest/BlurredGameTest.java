package dev.blurredclient.gametest;

import dev.blurredclient.mod.BlurredMod;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;
import org.lwjgl.glfw.GLFW;

/**
 * Drives Blurred's in-world features in a throwaway world and saves a
 * screenshot of each, so they can be looked at without anyone playing.
 * Input is simulated inside the game; nothing touches the real mouse,
 * keyboard or any other window.
 *
 * <p>Scene: a flat creative world, standing at (0.5, -60, 0.5) facing south
 * (+Z). Each scene sets up what it needs with commands.
 */
public class BlurredGameTest implements FabricClientGameTest {
    private static final double EYE = -60 + 1.62;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("tp @p 0.5 -60 0.5 0 0");
            ctx.waitTicks(10);

            if (System.getProperty("blurred.only") != null) {
                String only = System.getProperty("blurred.only");
                if (only.equals("effects")) {
                    scene(ctx, "effects", () -> effects(ctx, server));
                }
                for (String extra : extraScenes()) {
                    if (extra.equals(only)) {
                        scene(ctx, extra, () -> Scenes.run(extra, ctx, server));
                    }
                }
                for (String extra : Scenes2.NAMES) {
                    if (extra.equals(only)) {
                        scene(ctx, extra, () -> Scenes2.run(extra, ctx, server));
                    }
                }
            } else {
                runAll(ctx, server);
            }
            // The runner checks the game ends on vanilla's title screen.
            enable(ctx, Modules.MENU_THEME, false);
        }
        ctx.setScreen(net.minecraft.client.gui.screen.TitleScreen::new);
    }

    private static void runAll(ClientGameTestContext ctx, TestServerContext server) {
        {
            scene(ctx, "debug", () -> debugShapes(ctx, server));
            scene(ctx, "effects", () -> effects(ctx, server));
            scene(ctx, "timing", () -> timing(ctx, server));
            scene(ctx, "physics", () -> physics(ctx, server));
            scene(ctx, "swing", () -> swing(ctx, server));
            for (String extra : extraScenes()) {
                scene(ctx, extra, () -> Scenes.run(extra, ctx, server));
            }
            for (String extra : Scenes2.NAMES) {
                scene(ctx, extra, () -> Scenes2.run(extra, ctx, server));
            }
            scene(ctx, "fps", () -> fps(ctx, server));
        }
    }

    /** More scenes, added by {@link Scenes}. */
    private static String[] extraScenes() {
        return Scenes.NAMES;
    }

    private static void scene(ClientGameTestContext ctx, String name, Runnable body) {
        BlurredMod.LOGGER.info("[gametest] scene {}", name);
        try {
            body.run();
        } catch (RuntimeException e) {
            BlurredMod.LOGGER.error("[gametest] scene {} failed", name, e);
        }
        ctx.runOnClient(c -> c.setScreen(null));
        ctx.waitTicks(2);
    }

    /** Turn the player (through the server, so it sticks). */
    static void look(ClientGameTestContext ctx, TestServerContext server, float yaw, float pitch) {
        server.runCommand(String.format(java.util.Locale.ROOT, "execute as @p at @s run tp @s ~ ~ ~ %.2f %.2f", yaw, pitch));
        ctx.waitTicks(2);
    }

    static void enable(ClientGameTestContext ctx, Module module, boolean on) {
        ctx.runOnClient(c -> module.setEnabled(on));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void set(ClientGameTestContext ctx, Module module, String key, Object value) {
        ctx.runOnClient(c -> {
            for (Setting<?> s : module.settings()) {
                if (s.key().equals(key)) {
                    ((Setting) s).set(value);
                    return;
                }
            }
            throw new IllegalArgumentException("no setting " + key + " on " + module.id());
        });
    }

    static void perspective(ClientGameTestContext ctx, Perspective p) {
        ctx.runOnClient(c -> c.options.setPerspective(p));
    }

    // ---- scenes ----

    private static boolean debugOn;

    /** Known shapes at fixed spots: a red plain quad, a green additive one, and a ring. */
    private static void debugShapes(ClientGameTestContext ctx, TestServerContext server) {
        ctx.runOnClient(c -> dev.blurredclient.mod.fx.WorldDraw.add(f -> {
            if (!debugOn) {
                return;
            }
            net.minecraft.util.math.Vec3d o = new net.minecraft.util.math.Vec3d(0.5, -59, 3.5);
            f.quad(o.add(-1.5, 0, 0), o.add(-0.5, 0, 0), o.add(-0.5, 1, 0), o.add(-1.5, 1, 0), 0xFFFF0000, false);
            f.quad(o.add(0.5, 0, 0), o.add(1.5, 0, 0), o.add(1.5, 1, 0), o.add(0.5, 1, 0), 0xFF00FF00, true);
            f.groundRing(new net.minecraft.util.math.Vec3d(0.5, -59.98, 2.5), 0.6, 0.8, 0xFF00FFFF, 0xFF00FFFF, 48, false);
        }));
        debugOn = true;
        server.runCommand("tp @p 0.5 -60 0.5 0 15");
        ctx.waitTicks(5);
        ctx.takeScreenshot("debug_shapes");
        debugOn = false;
    }

    private static void effects(ClientGameTestContext ctx, TestServerContext server) {
        enable(ctx, Modules.JUMP_CIRCLES, true);
        enable(ctx, Modules.HIT_EFFECTS, true);
        enable(ctx, Modules.TARGET_RING, true);
        enable(ctx, Modules.TRAIL, true);

        server.runCommand("tp @p 0.5 -60 0.5 0 0");
        look(ctx, server, 0, 55);
        ctx.waitTicks(5);
        ctx.getInput().pressKey(o -> o.jumpKey);
        ctx.waitTicks(4);
        ctx.takeScreenshot("effects_jump_ring");
        ctx.waitTicks(10);
        ctx.takeScreenshot("effects_landing_ring");

        // Third person, sprinting: the trail.
        perspective(ctx, Perspective.THIRD_PERSON_BACK);
        look(ctx, server, 0, 20);
        ctx.getInput().holdKey(o -> o.sprintKey);
        ctx.getInput().holdKey(o -> o.forwardKey);
        ctx.waitTicks(16);
        ctx.takeScreenshot("effects_trail");
        ctx.getInput().releaseKey(o -> o.forwardKey);
        ctx.getInput().releaseKey(o -> o.sprintKey);
        perspective(ctx, Perspective.FIRST_PERSON);

        // A pig to hit: target ring and the hit effect.
        server.runCommand("tp @p 0.5 -60 0.5 0 0");
        server.runCommand("kill @e[type=minecraft:pig]");
        server.runCommand("summon minecraft:pig 0.5 -60 3.0 {NoAI:1b,Silent:1b,Invulnerable:1b}");
        look(ctx, server, 0, 20);
        ctx.waitTicks(30);
        ctx.takeScreenshot("effects_target_ring");
        for (String style : new String[] {"SQUARES", "SHOCKWAVE", "SLASH", "SPARKS"}) {
            set(ctx, Modules.HIT_EFFECTS, "style",
                    dev.blurredclient.mod.module.feature.EffectsModules.HitEffects.Style.valueOf(style));
            ctx.waitTicks(25);
            ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            ctx.waitTicks(2);
            ctx.takeScreenshot("effects_hit_" + style.toLowerCase());
            if (style.equals("SQUARES")) {
                ctx.waitTicks(3);
                ctx.takeScreenshot("effects_hit_squares_later");
            }
        }
        server.runCommand("kill @e[type=minecraft:pig]");
        enable(ctx, Modules.TRAIL, false);
    }

    private static void timing(ClientGameTestContext ctx, TestServerContext server) {
        enable(ctx, Modules.COMBAT_INFO, true);
        enable(ctx, Modules.HIT_TYPE, true);
        server.runCommand("tp @p 0.5 -60 0.5 0 0");
        look(ctx, server, 0, 10);
        // Pig box is 0.9 wide; eye-to-box distance d at height difference 0.72.
        double[] distances = {3.6, 2.95, 2.5};
        String[] names = {"wait", "hit_now", "too_late"};
        for (int i = 0; i < distances.length; i++) {
            double dz = Math.sqrt(distances[i] * distances[i] - 0.72 * 0.72);
            double z = 0.5 + dz + 0.45;
            server.runCommand("kill @e[type=minecraft:pig]");
            server.runCommand(String.format(java.util.Locale.ROOT, "summon minecraft:pig 0.5 -60 %.3f {NoAI:1b,Silent:1b,Invulnerable:1b}", z));
            ctx.waitTicks(30);
            ctx.takeScreenshot("timing_" + names[i]);
        }
        server.runCommand("kill @e[type=minecraft:pig]");
    }

    private static void physics(ClientGameTestContext ctx, TestServerContext server) {
        enable(ctx, Modules.ITEM_PHYSICS, true);
        server.runCommand("tp @p 0.5 -60 0.5 0 0");
        look(ctx, server, 0, 35);
        server.runCommand("kill @e[type=minecraft:item]");
        String[] items = {"diamond", "golden_apple", "oak_log", "iron_sword", "emerald"};
        for (int i = 0; i < items.length; i++) {
            server.runCommand(String.format(java.util.Locale.ROOT,
                    "summon minecraft:item %.2f -58.5 2.5 {Item:{id:\"minecraft:%s\",count:3},PickupDelay:32767,Motion:[%.2f,0.25,0.12]}",
                    -0.7 + i * 0.6, items[i], (i - 2) * 0.03));
        }
        ctx.waitTicks(3);
        ctx.takeScreenshot("physics_air");
        ctx.waitTicks(6);
        ctx.takeScreenshot("physics_landing");
        ctx.waitTicks(30);
        ctx.takeScreenshot("physics_rest");
        server.runCommand("kill @e[type=minecraft:item]");
    }

    private static void swing(ClientGameTestContext ctx, TestServerContext server) {
        enable(ctx, Modules.SWING, true);
        server.runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_sword");
        server.runCommand("tp @p 0.5 -60 0.5 0 0");
        look(ctx, server, 0, 0);
        for (String style : new String[] {"SMOOTH", "STAB", "SPIN"}) {
            set(ctx, Modules.SWING, "style", dev.blurredclient.mod.module.feature.PerformanceModules.Swing.Style.valueOf(style));
            ctx.waitTicks(10);
            ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            ctx.waitTicks(2);
            ctx.takeScreenshot("swing_" + style.toLowerCase());
        }
    }

    /** Frame rate with no screen, the pause menu, the inventory and the mod menu. */
    private static void fps(ClientGameTestContext ctx, TestServerContext server) {
        ctx.runOnClient(c -> {
            c.options.getEnableVsync().setValue(false);
            c.options.getMaxFps().setValue(260);
        });
        server.runCommand("tp @p 0.5 -60 0.5 0 0");
        look(ctx, server, 0, 0);
        measure(ctx, "world", () -> null);
        measure(ctx, "pause", () -> new net.minecraft.client.gui.screen.GameMenuScreen(true));
        measure(ctx, "inventory", () -> new net.minecraft.client.gui.screen.ingame.InventoryScreen(
                net.minecraft.client.MinecraftClient.getInstance().player));
        measure(ctx, "clickgui", () -> new dev.blurredclient.mod.screen.clickgui.ClickGuiScreen(null));
    }

    /**
     * A tiny sampling profiler: look at the render thread's stack every few
     * milliseconds and count where it is. Logs the hottest Blurred methods
     * and the hottest methods overall.
     */
    static void profile(String name) {
        Thread render = null;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().equals("Render thread")) {
                render = t;
            }
        }
        if (render == null) {
            return;
        }
        java.util.Map<String, Integer> ours = new java.util.HashMap<>();
        java.util.Map<String, Integer> top = new java.util.HashMap<>();
        int samples = 300;
        for (int i = 0; i < samples; i++) {
            StackTraceElement[] st = render.getStackTrace();
            if (st.length > 0) {
                top.merge(st[0].getClassName() + "." + st[0].getMethodName(), 1, Integer::sum);
            }
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (StackTraceElement e : st) {
                if (e.getClassName().startsWith("dev.blurredclient.mod")) {
                    String k = e.getClassName().substring(21) + "." + e.getMethodName();
                    if (seen.add(k)) {
                        ours.merge(k, 1, Integer::sum);
                    }
                }
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                return;
            }
        }
        StringBuilder b = new StringBuilder();
        ours.entrySet().stream().sorted((a, c) -> c.getValue() - a.getValue()).limit(14)
                .forEach(e -> b.append("\n   ours ").append(e.getValue() * 100 / samples).append("% ").append(e.getKey()));
        top.entrySet().stream().sorted((a, c) -> c.getValue() - a.getValue()).limit(8)
                .forEach(e -> b.append("\n   top  ").append(e.getValue() * 100 / samples).append("% ").append(e.getKey()));
        BlurredMod.LOGGER.info("[gametest] profile {}:{}", name, b);
    }

    private static void measure(ClientGameTestContext ctx, String name,
                                java.util.function.Supplier<net.minecraft.client.gui.screen.Screen> screen) {
        ctx.runOnClient(c -> c.setScreen(screen.get()));
        ctx.waitTicks(20);
        // Sample while the game is running (it's paused while this thread works).
        Thread sampler = new Thread(() -> profile(name), "blurred-sampler");
        sampler.start();
        ctx.waitTicks(40);
        try {
            sampler.join(10_000);
        } catch (InterruptedException ignored) {
        }
        int fps = ctx.computeOnClient(c -> c.getCurrentFps());
        BlurredMod.LOGGER.info("[gametest] fps {} = {}", name, fps);
        ctx.takeScreenshot("fps_" + name);
        ctx.runOnClient(c -> c.setScreen(null));
        ctx.waitTicks(5);
    }
}
