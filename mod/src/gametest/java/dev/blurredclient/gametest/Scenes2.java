package dev.blurredclient.gametest;

import dev.blurredclient.mod.BlurredMod;
import dev.blurredclient.mod.module.Modules;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.option.Perspective;
import org.lwjgl.glfw.GLFW;

/** Scenes for the HUD, world-effect, cosmetic and shader modules. */
final class Scenes2 {
    private Scenes2() {}

    static final String[] NAMES = {"hud2", "combat2", "blocks", "cosmetics", "nametags", "skins", "shaders", "swords", "liquid", "misc"};

    static void run(String name, ClientGameTestContext ctx, TestServerContext server) {
        switch (name) {
            case "hud2" -> hud(ctx, server);
            case "combat2" -> combat(ctx, server);
            case "blocks" -> blocks(ctx, server);
            case "cosmetics" -> cosmetics(ctx, server);
            case "nametags" -> nametags(ctx, server);
            case "skins" -> skins(ctx, server);
            case "shaders" -> shaders(ctx, server);
            case "swords" -> swords(ctx, server);
            case "liquid" -> liquid(ctx, server);
            case "misc" -> misc(ctx, server);
            default -> {
            }
        }
    }

    private static void misc(ClientGameTestContext ctx, TestServerContext server) {
        // Food info: hungry, holding bread, in survival so the hunger bar shows.
        server.runCommand("gamemode survival @p");
        server.runCommand("effect give @p minecraft:hunger 3 120");
        ctx.waitTicks(50);
        server.runCommand("effect clear @p");
        server.runCommand("item replace entity @p weapon.mainhand with minecraft:bread");
        BlurredGameTest.enable(ctx, Modules.FOOD_INFO, true);
        BlurredGameTest.enable(ctx, Modules.SHOW_HAND, true);
        ctx.waitTicks(10);
        ctx.takeScreenshot("misc_food_hand");
        server.runCommand("gamemode creative @p");
        // A Slash swing, caught part way.
        server.runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_sword");
        BlurredGameTest.enable(ctx, Modules.SWING, true);
        BlurredGameTest.set(ctx, Modules.SWING, "style", dev.blurredclient.mod.module.feature.PerformanceModules.Swing.Style.SLASH);
        ctx.waitTicks(10);
        ctx.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitTicks(2);
        ctx.takeScreenshot("misc_slash_swing");
        BlurredGameTest.enable(ctx, Modules.SHOW_HAND, false);
        BlurredGameTest.set(ctx, Modules.HITBOXES, "rainbow", true);
        // Hitboxes: one pig in reach, one out of it. Block info on a block. Painted crosshair.
        server.runCommand("kill @e[type=minecraft:pig]");
        server.runCommand("summon minecraft:pig 0.5 -60 2.8 {NoAI:1b,Silent:1b,Invulnerable:1b}");
        server.runCommand("summon minecraft:pig 3.5 -60 7.5 {NoAI:1b,Silent:1b,Invulnerable:1b}");
        server.runCommand("setblock -1 -60 3 minecraft:crafting_table");
        server.runCommand("tp @p 0.5 -60 0.5 0 15");
        BlurredGameTest.enable(ctx, Modules.HITBOXES, true);
        BlurredGameTest.enable(ctx, Modules.BLOCK_INFO, true);
        BlurredGameTest.enable(ctx, Modules.CROSSHAIR, true);
        BlurredGameTest.set(ctx, Modules.CROSSHAIR, "style", dev.blurredclient.mod.module.hud.CrosshairModule.Style.PAINTED);
        ctx.waitTicks(20);
        ctx.takeScreenshot("misc_hitboxes");
        server.runCommand("tp @p 0.5 -60 0.5 -30 30");
        ctx.waitTicks(20);
        ctx.takeScreenshot("misc_block_info");
        // Outlines and the profile menu, in the click GUI.
        BlurredGameTest.set(ctx, Modules.APPEARANCE, "outlines", true);
        ctx.setScreen(() -> new dev.blurredclient.mod.screen.clickgui.ClickGuiScreen(null));
        ctx.waitTicks(15);
        double[] at = ctx.computeOnClient(c -> {
            var screen = (dev.blurredclient.mod.ui.kit.KitScreen) c.currentScreen;
            double sc = c.getWindow().getScaleFactor();
            for (int y = screen.height - 4; y > screen.height - 60; y -= 2) {
                for (int x = 0; x < screen.width / 2; x += 3) {
                    var hit = screen.hitAt(x, y);
                    if (hit != null && hit.getClass().getSimpleName().equals("Button")) {
                        return new double[] {x * sc + 8, y * sc};
                    }
                }
            }
            return new double[] {0, 0};
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTicks(2);
        ctx.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitTicks(8);
        ctx.takeScreenshot("misc_profiles");
        ctx.setScreen(() -> null);
        BlurredGameTest.set(ctx, Modules.APPEARANCE, "outlines", false);
        BlurredGameTest.enable(ctx, Modules.HITBOXES, false);
        server.runCommand("kill @e[type=minecraft:pig]");
    }

    private static void liquid(ClientGameTestContext ctx, TestServerContext server) {
        // Something busy behind the menus, so the glass has something to bend.
        String[] wool = {"red", "orange", "yellow", "lime", "cyan", "blue", "purple", "white", "black"};
        for (int i = 0; i < 30; i++) {
            server.runCommand("fill " + (i - 15) + " -60 8 " + (i - 15) + " -48 8 minecraft:" + wool[i % wool.length] + "_wool");
        }
        server.runCommand("fill -15 -56 7 14 -55 7 minecraft:glowstone");
        server.runCommand("tp @p 0.5 -60 0.5 0 -8");
        BlurredGameTest.set(ctx, Modules.APPEARANCE, "style",
                dev.blurredclient.mod.module.feature.AppearanceModule.Style.LIQUID);
        ctx.setScreen(() -> new dev.blurredclient.mod.screen.clickgui.ClickGuiScreen(null));
        ctx.waitTicks(20);
        ctx.takeScreenshot("liquid_gui");
        // Click the middle of the screen's first module row it can find, then catch the ripple.
        double[] at = ctx.computeOnClient(c -> {
            var screen = (dev.blurredclient.mod.ui.kit.KitScreen) c.currentScreen;
            double s = c.getWindow().getScaleFactor();
            for (int y = 40; y < screen.height; y += 2) {
                for (int x = 20; x < screen.width / 2; x += 4) {
                    var hit = screen.hitAt(x, y);
                    if (hit != null && hit.getClass().getSimpleName().equals("ModuleEntry")) {
                        return new double[] {x * s, y * s};
                    }
                }
            }
            return new double[] {200 * s, 120 * s};
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTicks(3);
        ctx.takeScreenshot("liquid_hover");
        ctx.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitTicks(2);
        ctx.takeScreenshot("liquid_ripple_1");
        ctx.waitTicks(4);
        ctx.takeScreenshot("liquid_ripple_2");
        ctx.waitTicks(20);
        ctx.takeScreenshot("liquid_after");
        ctx.setScreen(() -> null);
        BlurredGameTest.set(ctx, Modules.APPEARANCE, "style",
                dev.blurredclient.mod.module.feature.AppearanceModule.Style.MODERN);
    }

    private static void swords(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("tp @p 0.5 -60 0.5 0 10");
        server.runCommand("clear @p");
        String[] mats = {"diamond", "netherite", "golden", "iron", "stone", "wooden", "copper"};
        for (int i = 0; i < mats.length; i++) {
            server.runCommand("item replace entity @p hotbar." + i + " with minecraft:" + mats[i] + "_sword");
        }
        ctx.waitTicks(40);
        ctx.takeScreenshot("swords_vanilla_first");
        BlurredGameTest.perspective(ctx, net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT);
        ctx.waitTicks(3);
        ctx.takeScreenshot("swords_vanilla_third");
        BlurredGameTest.perspective(ctx, net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
        ctx.waitTicks(3);
        ctx.takeScreenshot("swords_vanilla_back");
        BlurredGameTest.perspective(ctx, net.minecraft.client.option.Perspective.FIRST_PERSON);
        BlurredGameTest.enable(ctx, Modules.SWORD_MODELS, true);
        for (var model : dev.blurredclient.mod.module.feature.HandModules.SwordModels.Model.values()) {
            BlurredGameTest.set(ctx, Modules.SWORD_MODELS, "model", model);
            ctx.waitTicks(20);
            ctx.waitFor(c -> c.getOverlay() == null, 1200);
            ctx.waitTicks(10);
            String name = model.name().toLowerCase();
            ctx.takeScreenshot("swords_" + name + "_first");
            BlurredGameTest.perspective(ctx, net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT);
            ctx.waitTicks(3);
            ctx.takeScreenshot("swords_" + name + "_third");
            BlurredGameTest.perspective(ctx, net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
            ctx.waitTicks(3);
            ctx.takeScreenshot("swords_" + name + "_back");
            BlurredGameTest.perspective(ctx, net.minecraft.client.option.Perspective.FIRST_PERSON);
        }
        BlurredGameTest.set(ctx, Modules.SWORD_MODELS, "model",
                dev.blurredclient.mod.module.feature.HandModules.SwordModels.Model.KATANA);
        ctx.waitTicks(20);
        ctx.waitFor(c -> c.getOverlay() == null, 1200);
        ctx.waitTicks(10);
        BlurredGameTest.enable(ctx, Modules.INSPECT, true);
        for (var st : dev.blurredclient.mod.module.feature.HandModules.Inspect.Style.values()) {
            BlurredGameTest.set(ctx, Modules.INSPECT, "style", st);
            ctx.waitTicks(2);
            ctx.runOnClient(c -> Modules.INSPECT.start());
            for (int i = 1; i <= 5; i++) {
                ctx.waitTicks(st == dev.blurredclient.mod.module.feature.HandModules.Inspect.Style.SPIN ? 6 : 11);
                ctx.takeScreenshot("swords_inspect_" + st.name().toLowerCase() + "_" + i);
            }
            ctx.waitTicks(30);
        }
        BlurredGameTest.enable(ctx, Modules.INSPECT, false);
        BlurredGameTest.enable(ctx, Modules.SWORD_MODELS, false);
        ctx.waitTicks(20);
        ctx.waitFor(c -> c.getOverlay() == null, 1200);
    }

    private static void hud(ClientGameTestContext ctx, TestServerContext server) {
        BlurredGameTest.enable(ctx, Modules.CROSSHAIR, true);
        BlurredGameTest.enable(ctx, Modules.VITALS, true);
        BlurredGameTest.enable(ctx, Modules.TOTEM_COUNTER, true);
        BlurredGameTest.enable(ctx, Modules.DURABILITY_ALERT, true);
        BlurredGameTest.enable(ctx, Modules.KEYBINDS_HUD, true);
        BlurredGameTest.enable(ctx, Modules.SMOOTH_HOTBAR, true);
        server.runCommand("gamemode survival @p");
        server.runCommand("tp @p 0.5 -60 0.5 0 10");
        server.runCommand("give @p minecraft:totem_of_undying 2");
        server.runCommand("item replace entity @p armor.chest with minecraft:iron_chestplate[minecraft:damage=235]");
        server.runCommand("damage @p 15 minecraft:generic");
        ctx.waitTicks(30);
        ctx.takeScreenshot("hud2_low_health");
        server.runCommand("effect give @p minecraft:instant_health 1 5");
        server.runCommand("gamemode creative @p");
        ctx.waitTicks(5);
        BlurredGameTest.enable(ctx, Modules.VITALS, false);
    }

    private static void combat(ClientGameTestContext ctx, TestServerContext server) {
        BlurredGameTest.enable(ctx, Modules.HIT_MARKERS, true);
        BlurredGameTest.enable(ctx, Modules.KILL_EFFECTS, true);
        BlurredGameTest.enable(ctx, Modules.DEATH_EFFECTS, true);
        BlurredGameTest.enable(ctx, Modules.HIT_SOUNDS, true);
        server.runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_sword");
        server.runCommand("tp @p 0.5 -60 0.5 0 20");
        server.runCommand("kill @e[type=minecraft:pig]");
        server.runCommand("summon minecraft:pig 0.5 -60 3.0 {NoAI:1b,Silent:1b}");
        ctx.waitTicks(25);
        ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitTicks(2);
        ctx.takeScreenshot("combat2_hit_marker");
        server.runCommand("kill @e[type=minecraft:pig]");
        ctx.waitTicks(3);
        ctx.takeScreenshot("combat2_kill_lightning");
        ctx.waitTicks(8);
        ctx.takeScreenshot("combat2_death_soul");
        BlurredGameTest.set(ctx, Modules.DEATH_EFFECTS, "style",
                dev.blurredclient.mod.module.feature.WorldFxModules.DeathEffects.Style.SHATTER);
        BlurredGameTest.set(ctx, Modules.KILL_EFFECTS, "style",
                dev.blurredclient.mod.module.feature.WorldFxModules.KillEffects.Style.COINS);
        server.runCommand("summon minecraft:pig 0.5 -60 3.0 {NoAI:1b,Silent:1b}");
        ctx.waitTicks(25);
        ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitTicks(2);
        server.runCommand("kill @e[type=minecraft:pig]");
        ctx.waitTicks(6);
        ctx.takeScreenshot("combat2_shatter_coins");
        server.runCommand("kill @e[type=minecraft:item]");
        server.runCommand("kill @e[type=minecraft:experience_orb]");
    }

    private static void blocks(ClientGameTestContext ctx, TestServerContext server) {
        BlurredGameTest.enable(ctx, Modules.PLACE_ANIMATION, true);
        BlurredGameTest.enable(ctx, Modules.PLACE_PREVIEW, true);
        BlurredGameTest.enable(ctx, Modules.PROJECTILE_TRAILS, true);
        server.runCommand("item replace entity @p weapon.mainhand with minecraft:oak_planks");
        server.runCommand("tp @p 0.5 -60 0.5 0 40");
        ctx.waitTicks(10);
        ctx.takeScreenshot("blocks_preview");
        server.runCommand("setblock 0 -60 3 minecraft:stone");
        ctx.waitTicks(2);
        ctx.takeScreenshot("blocks_place_anim");
        ctx.waitTicks(10);
        server.runCommand("setblock 0 -60 3 minecraft:air");
        ctx.waitTicks(4);
        ctx.takeScreenshot("blocks_break_anim");
        server.runCommand("tp @p 0.5 -60 0.5 0 -5");
        server.runCommand("summon minecraft:arrow 3 -58 0 {Motion:[-0.2,0.25,1.2]}");
        server.runCommand("summon minecraft:ender_pearl -3 -58 1 {Motion:[0.2,0.3,1.0]}");
        ctx.waitTicks(8);
        ctx.takeScreenshot("blocks_projectile_trails");
        ctx.waitTicks(30);
        server.runCommand("kill @e[type=minecraft:arrow]");
    }

    private static void cosmetics(ClientGameTestContext ctx, TestServerContext server) {
        BlurredGameTest.enable(ctx, Modules.CHINESE_HAT, true);
        BlurredGameTest.enable(ctx, Modules.PETS, true);
        server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
        server.runCommand("tp @p 0.5 -60 0.5 0 15");
        BlurredGameTest.perspective(ctx, Perspective.THIRD_PERSON_FRONT);
        ctx.waitTicks(30);
        ctx.takeScreenshot("cosmetics_hat_dog");
        BlurredGameTest.enable(ctx, Modules.ITEM_SKINS, true);
        BlurredGameTest.set(ctx, Modules.ITEM_SKINS, "style", dev.blurredclient.mod.module.feature.ItemSkinsModule.Style.RAINBOW);
        BlurredGameTest.enable(ctx, Modules.SKIN_SHINE, true);
        ctx.waitTicks(10);
        ctx.takeScreenshot("cosmetics_skin_shine");
        BlurredGameTest.enable(ctx, Modules.SKIN_SHINE, false);
        BlurredGameTest.enable(ctx, Modules.ITEM_SKINS, false);
        ctx.getInput().holdKey(o -> o.sneakKey);
        ctx.waitTicks(10);
        ctx.takeScreenshot("cosmetics_hat_sneak");
        ctx.getInput().releaseKey(o -> o.sneakKey);
        ctx.waitTicks(5);
        for (var kind : dev.blurredclient.mod.module.feature.CosmeticModules.Pets.Kind.values()) {
            BlurredGameTest.set(ctx, Modules.PETS, "kind", kind);
            ctx.waitTicks(4);
            ctx.takeScreenshot("cosmetics_pet_" + kind.name().toLowerCase());
        }
        BlurredGameTest.perspective(ctx, Perspective.THIRD_PERSON_BACK);
        ctx.getInput().holdKey(o -> o.forwardKey);
        ctx.waitTicks(10);
        ctx.takeScreenshot("cosmetics_walking");
        ctx.getInput().releaseKey(o -> o.forwardKey);
        BlurredGameTest.perspective(ctx, Perspective.FIRST_PERSON);
        BlurredGameTest.enable(ctx, Modules.PETS, false);
        BlurredGameTest.enable(ctx, Modules.CHINESE_HAT, false);

        BlurredGameTest.enable(ctx, Modules.CUSTOM_SKY, true);
        BlurredGameTest.set(ctx, Modules.CUSTOM_SKY, "aurora", true);
        server.runCommand("time set 13500");
        server.runCommand("tp @p 0.5 -60 0.5 0 -25");
        ctx.waitTicks(10);
        ctx.takeScreenshot("cosmetics_sky_dusk");
        server.runCommand("time set noon");
        ctx.waitTicks(5);
        ctx.takeScreenshot("cosmetics_sky_noon");
        BlurredGameTest.enable(ctx, Modules.CUSTOM_SKY, false);
    }

    private static void nametags(ClientGameTestContext ctx, TestServerContext server) {
        BlurredGameTest.enable(ctx, Modules.BETTER_NAMETAGS, true);
        server.runCommand("tp @p 0.5 -60 0.5 0 0");
        server.runCommand("summon minecraft:pig 0.5 -60 5 {NoAI:1b,CustomName:'\"Sir Oinks\"',Health:6f}");
        ctx.waitTicks(20);
        ctx.takeScreenshot("nametags_card");
        server.runCommand("kill @e[type=minecraft:pig]");
    }

    private static void skins(ClientGameTestContext ctx, TestServerContext server) {
        BlurredGameTest.enable(ctx, Modules.ITEM_SKINS, true);
        server.runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_sword");
        server.runCommand("tp @p 0.5 -60 0.5 0 0");
        for (var style : new String[] {"GALAXY", "FLAME", "RAINBOW"}) {
            BlurredGameTest.set(ctx, Modules.ITEM_SKINS, "style",
                    dev.blurredclient.mod.module.feature.ItemSkinsModule.Style.valueOf(style));
            ctx.waitTicks(10);
            ctx.takeScreenshot("skins_" + style.toLowerCase());
        }
        BlurredGameTest.enable(ctx, Modules.ITEM_SKINS, false);
    }

    private static void shaders(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("tp @p 0.5 -60 0.5 0 10");
        // A little scene: trees, grass and a path.
        server.runCommand("fill -4 -60 4 4 -60 9 minecraft:short_grass");
        server.runCommand("fill -3 -60 7 -3 -57 7 minecraft:oak_log");
        server.runCommand("fill -5 -56 5 -1 -54 9 minecraft:oak_leaves[persistent=true]");
        server.runCommand("fill 2 -61 2 3 -61 12 minecraft:stone_bricks");
        ctx.waitTicks(20);
        ctx.takeScreenshot("shaders_before");
        for (var look : new String[] {"SHADER", "MOODY", "MIDNIGHT", "HORROR"}) {
            BlurredGameTest.enable(ctx, Modules.AMBIENCE, true);
            BlurredGameTest.set(ctx, Modules.AMBIENCE, "look",
                    dev.blurredclient.mod.module.feature.ShaderModules.Ambience.Look.valueOf(look));
            ctx.waitTicks(5);
            ctx.takeScreenshot("shaders_look_" + look.toLowerCase());
        }
        BlurredGameTest.enable(ctx, Modules.AMBIENCE, false);
        BlurredGameTest.enable(ctx, Modules.MOTION_BLUR, true);
        ctx.waitTicks(3);
        // Turn a little every frame, like moving the mouse, then shoot mid-turn.
        for (int i = 0; i < 6; i++) {
            ctx.runOnClient(c -> {
                c.player.setYaw(c.player.getYaw() + 5f);
                c.player.lastYaw = c.player.getYaw();
            });
            ctx.waitTicks(1);
        }
        ctx.runOnClient(c -> {
            c.player.setYaw(c.player.getYaw() + 5f);
            c.player.lastYaw = c.player.getYaw();
        });
        ctx.takeScreenshot("shaders_motion_blur");
        BlurredGameTest.enable(ctx, Modules.MOTION_BLUR, false);
        server.runCommand("tp @p 0.5 -60 0.5 0 10");

        BlurredGameTest.enable(ctx, Modules.WAVING_FOLIAGE, true);
        BlurredGameTest.enable(ctx, Modules.WET_GROUND, true);
        // Wait for the resource reload the packs trigger.
        ctx.waitTicks(20);
        ctx.waitFor(c -> c.getOverlay() == null, 1200);
        ctx.waitTicks(40);
        String packs = ctx.computeOnClient(c -> String.join(",", c.getResourcePackManager().getEnabledIds()));
        BlurredMod.LOGGER.info("[gametest] packs on: {}", packs);
        ctx.takeScreenshot("shaders_foliage_wet");
        ctx.waitTicks(10);
        ctx.takeScreenshot("shaders_foliage_wet_later");
        BlurredGameTest.enable(ctx, Modules.WAVING_FOLIAGE, false);
        BlurredGameTest.enable(ctx, Modules.WET_GROUND, false);
        ctx.waitTicks(20);
        ctx.waitFor(c -> c.getOverlay() == null, 1200);
    }
}
