package dev.blurredclient.mod.fx;

import dev.blurredclient.mod.BlurredMod;
import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.module.feature.HandModules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/**
 * Keeps the Sword models packs in step with the module: at most one is on,
 * and changing it reloads resources once (after a short settle, so flicking
 * through the models doesn't reload for each).
 */
public final class SwordPacks {
    private SwordPacks() {}

    private static final String[] PACKS = HandModules.SwordModels.packs();
    private static String active = "";
    private static boolean synced;
    private static int settle;

    public static void register() {
        var container = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(BlurredMod.MOD_ID).orElseThrow();
        for (String p : PACKS) {
            net.fabricmc.fabric.api.resource.ResourceManagerHelper.registerBuiltinResourcePack(
                    Identifier.of(BlurredMod.MOD_ID, p), container,
                    net.minecraft.text.Text.literal("Blurred " + p.replace('_', ' ')),
                    net.fabricmc.fabric.api.resource.ResourcePackActivationType.NORMAL);
        }
    }

    public static void tick(MinecraftClient client) {
        var manager = client.getResourcePackManager();
        if (!synced) {
            // Start from whichever of our packs the game already has on (saved in options).
            synced = true;
            for (String p : PACKS) {
                String id = profileId(manager, p);
                if (id != null && manager.getEnabledIds().contains(id)) {
                    active = p;
                }
            }
        }
        String want = Modules.SWORD_MODELS.pack();
        if (want.equals(active)) {
            settle = 0;
            return;
        }
        if (++settle < 10 || client.getOverlay() != null) {
            return;
        }
        settle = 0;
        active = want;
        for (String p : PACKS) {
            String id = profileId(manager, p);
            if (id == null) {
                continue;
            }
            if (p.equals(want)) {
                manager.enable(id);
            } else {
                manager.disable(id);
            }
        }
        client.options.refreshResourcePacks(manager);
    }

    private static String profileId(net.minecraft.resource.ResourcePackManager manager, String path) {
        for (String id : manager.getIds()) {
            if (id.endsWith(path) && id.contains(BlurredMod.MOD_ID)) {
                return id;
            }
        }
        return null;
    }
}
