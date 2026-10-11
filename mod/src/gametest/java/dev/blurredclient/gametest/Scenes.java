package dev.blurredclient.gametest;

import dev.blurredclient.mod.fx.ContainerView;
import dev.blurredclient.mod.module.Modules;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import org.lwjgl.glfw.GLFW;

/** Scenes for the in-chest view and the 3D menu. */
final class Scenes {
    private Scenes() {}

    static final String[] NAMES = {"chest", "double_chest", "barrel", "hologram"};

    private static final String ITEMS = "Items:["
            + "{Slot:0b,id:\"minecraft:diamond\",count:12},"
            + "{Slot:2b,id:\"minecraft:golden_apple\",count:3},"
            + "{Slot:4b,id:\"minecraft:diamond_sword\",count:1},"
            + "{Slot:8b,id:\"minecraft:oak_log\",count:32},"
            + "{Slot:10b,id:\"minecraft:ender_pearl\",count:16},"
            + "{Slot:13b,id:\"minecraft:enchanted_golden_apple\",count:1},"
            + "{Slot:19b,id:\"minecraft:iron_ingot\",count:40},"
            + "{Slot:22b,id:\"minecraft:redstone\",count:64},"
            + "{Slot:26b,id:\"minecraft:emerald\",count:7}]";

    static void run(String name, ClientGameTestContext ctx, TestServerContext server) {
        switch (name) {
            case "chest" -> container(ctx, server, "chest",
                    "setblock 0 -60 3 minecraft:chest[facing=north]{" + ITEMS + "}", 20.5f);
            case "double_chest" -> {
                server.runCommand("setblock 0 -60 3 minecraft:air");
                server.runCommand("setblock 0 -60 3 minecraft:chest[facing=north,type=left]{" + ITEMS + "}");
                container(ctx, server, "double_chest",
                        "setblock 1 -60 3 minecraft:chest[facing=north,type=right]", 20.5f);
            }
            case "barrel" -> {
                server.runCommand("setblock 0 -60 3 minecraft:air");
                server.runCommand("setblock 1 -60 3 minecraft:air");
                container(ctx, server, "barrel", "setblock 0 -60 3 minecraft:barrel[facing=up]{" + ITEMS + "}", 20.5f);
            }
            case "hologram" -> hologram(ctx, server);
            default -> {
            }
        }
    }

    private static void hologram(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("setblock 0 -60 3 minecraft:air");
        BlurredGameTest.enable(ctx, Modules.HOLOGRAM, true);
        server.runCommand("tp @p 0.5 -60 0.5 0 0");
        ctx.waitTicks(5);
        ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
        ctx.waitTicks(3);
        ctx.takeScreenshot("hologram_opening");
        ctx.waitTicks(12);
        ctx.takeScreenshot("hologram_open");

        boolean real = ctx.computeOnClient(c -> dev.blurredclient.mod.fx.Hologram.INSTANCE.showsRealMenu());
        // Aim at a module row (real menu: the first panel's third row) and right-click it.
        net.minecraft.util.math.Vec3d target = ctx.computeOnClient(c -> real
                ? dev.blurredclient.mod.fx.Hologram.INSTANCE.panelPoint(dev.blurredclient.gametest.Scenes.AIM_U, dev.blurredclient.gametest.Scenes.AIM_V)
                : dev.blurredclient.mod.fx.Hologram.INSTANCE.tileCentre(2));
        net.minecraft.util.math.Vec3d eye = ctx.computeOnClient(c -> c.player.getEyePos());
        net.minecraft.util.math.Vec3d d = target.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) Math.toDegrees(Math.asin(-d.y / d.length()));
        server.runCommand(String.format(java.util.Locale.ROOT, "tp @p 0.5 -60 0.5 %.3f %.3f", yaw, pitch));
        ctx.waitTicks(6);
        ctx.takeScreenshot("hologram_hover");
        String before = ctx.computeOnClient(c -> real ? enabledList() : describeTile(2));
        ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        ctx.waitTicks(6);
        String after = ctx.computeOnClient(c -> real ? enabledList() : describeTile(2));
        dev.blurredclient.mod.BlurredMod.LOGGER.info("[gametest] hologram right-click: {} -> {}", before, after);
        ctx.takeScreenshot("hologram_toggled");

        // Where does the time go with the real menu in the world?
        Thread sampler = new Thread(() -> BlurredGameTest.profile("hologram"), "blurred-sampler");
        sampler.start();
        ctx.waitTicks(40);
        try {
            sampler.join(10_000);
        } catch (InterruptedException ignored) {
        }
        dev.blurredclient.mod.BlurredMod.LOGGER.info("[gametest] hologram fps {}", (Integer) ctx.computeOnClient(c -> c.getCurrentFps()));
        // Walk around it.
        server.runCommand("tp @p 2.6 -60 1.2 55 8");
        ctx.waitTicks(6);
        ctx.takeScreenshot("hologram_side");
        server.runCommand("tp @p 0.5 -60 0.5 0 0");
        ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
        ctx.waitTicks(10);
        BlurredGameTest.enable(ctx, Modules.HOLOGRAM, false);
    }

    static float AIM_U = 50;
    static float AIM_V = 68;

    /** Names of the modules that are on. */
    private static String enabledList() {
        StringBuilder b = new StringBuilder();
        for (var m : Modules.REGISTRY.all()) {
            if (m.toggleable() && m.isEnabled()) {
                b.append(m.id()).append(' ');
            }
        }
        return b.toString().trim();
    }

    /** "name=on/off" for the module on tile {@code i} of the Visual page. */
    private static String describeTile(int i) {
        java.util.List<dev.blurredclient.mod.module.Module> list = new java.util.ArrayList<>();
        for (var m : Modules.REGISTRY.all()) {
            if (m.category() == dev.blurredclient.mod.module.Category.VISUAL && m.toggleable()) {
                list.add(m);
            }
        }
        var m = list.get(i);
        return m.name() + "=" + m.isEnabled();
    }

    private static void container(ClientGameTestContext ctx, TestServerContext server, String name,
                                  String place, float pitch) {
        BlurredGameTest.enable(ctx, Modules.CINEMATIC_CONTAINERS, true);
        server.runCommand("kill @e[type=minecraft:item]");
        server.runCommand(place);
        server.runCommand("tp @p 0.5 -60 0.5 0 " + pitch);
        ctx.waitTicks(10);
        ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        ctx.waitTicks(4);
        ctx.takeScreenshot(name + "_opening");
        ctx.waitTicks(16);
        ctx.takeScreenshot(name + "_open");

        // Hover the diamonds (slot 0), then click to pick them up.
        // The first slot with something in it (a double chest's items can be in either half).
        int first = ctx.computeOnClient(c -> {
            var slots = c.player.currentScreenHandler.slots;
            for (int i = 0; i < slots.size(); i++) {
                if (!(slots.get(i).inventory instanceof net.minecraft.entity.player.PlayerInventory)
                        && !slots.get(i).getStack().isEmpty()) {
                    return i;
                }
            }
            return 0;
        });
        double[] p = ctx.computeOnClient(c -> ContainerView.INSTANCE.slotOnScreen(first));
        if (p != null && p[2] > 0) {
            double scale = ctx.computeOnClient(c -> c.getWindow().getScaleFactor());
            ctx.getInput().setCursorPos(p[0] * scale, p[1] * scale);
            ctx.waitTicks(6);
            String probe = ctx.computeOnClient(c -> {
                double[] q = ContainerView.INSTANCE.slotOnScreen(first);
                var w = c.getWindow();
                double mx = c.mouse.getX() * w.getScaledWidth() / (double) w.getWidth();
                double my = c.mouse.getY() * w.getScaledHeight() / (double) w.getHeight();
                var hit = ContainerView.INSTANCE.slotAt(c.currentScreen, mx, my);
                return String.format("slot0=(%.1f,%.1f r%.1f) mouse=(%.1f,%.1f) hit=%s window=%dx%d scaled=%dx%d",
                        q[0], q[1], q[2], mx, my, hit == null ? "none" : hit.getStack().toString(),
                        w.getWidth(), w.getHeight(), w.getScaledWidth(), w.getScaledHeight());
            });
            dev.blurredclient.mod.BlurredMod.LOGGER.info("[gametest] {} probe {}", name, probe);
            ctx.takeScreenshot(name + "_hover");
            ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            ctx.waitTicks(4);
            ctx.takeScreenshot(name + "_picked_up");
            boolean holding = ctx.computeOnClient(c -> !c.player.currentScreenHandler.getCursorStack().isEmpty());
            dev.blurredclient.mod.BlurredMod.LOGGER.info("[gametest] {} click picked up stack: {}", name, holding);
            // Put them back.
            ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            ctx.waitTicks(2);
        } else {
            dev.blurredclient.mod.BlurredMod.LOGGER.warn("[gametest] {} slot 0 not on screen", name);
        }
        ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        ctx.waitTicks(4);
        ctx.takeScreenshot(name + "_closing");
        ctx.waitTicks(12);
    }
}
