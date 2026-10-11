package dev.blurredclient.mod.fx;

import dev.blurredclient.mod.BlurredMod;
import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.module.feature.HandModules;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

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
                    Identifier.fromNamespaceAndPath(BlurredMod.MOD_ID, p), container,
                    net.minecraft.network.chat.Component.literal("Blurred " + p.replace('_', ' ')),
                    net.fabricmc.fabric.api.resource.ResourcePackActivationType.NORMAL);
        }
    }

    public static void tick(Minecraft client) {
        var manager = client.getResourcePackRepository();
        if (!synced) {
            // Start from whichever of our packs the game already has on (saved in options).
            synced = true;
            for (String p : PACKS) {
                String id = profileId(manager, p);
                if (id != null && manager.getSelectedIds().contains(id)) {
                    active = p;
                }
            }
        }
        String want = Modules.SWORD_MODELS.pack();
        if (want.equals(active)) {
            settle = 0;
            return;
        }
        if (++settle < 10 || client.gui.overlay() != null) {
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
                manager.addPack(id);
            } else {
                manager.removePack(id);
            }
        }
        client.options.updateResourcePacks(manager);
    }

    private static String profileId(net.minecraft.server.packs.repository.PackRepository manager, String path) {
        for (String id : manager.getAvailableIds()) {
            if (id.endsWith(path) && id.contains(BlurredMod.MOD_ID)) {
                return id;
            }
        }
        return null;
    }
}
