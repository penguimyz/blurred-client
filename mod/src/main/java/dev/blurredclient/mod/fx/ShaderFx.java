package dev.blurredclient.mod.fx;

import dev.blurredclient.mod.BlurredMod;
import dev.blurredclient.mod.module.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/**
 * The shader-based modules (Ambience looks, Motion blur, Waving foliage, Wet
 * ground), kept in step with the game every tick.
 *
 * <p>Looks and motion blur are one vanilla post-effect chain, picked from
 * pre-built combinations ({@code blurred:<look>_<blur>}); 1.21.11 bakes
 * post-shader settings into those files. Foliage and wet ground are
 * built-in resource packs that replace the terrain shader; turning one on or
 * off reloads resources once.
 *
 * <p>Only on 1.21.11, the version the shader files were written against.
 */
public final class ShaderFx {
    private ShaderFx() {}

    public static boolean supported() {
        //? if >=1.21.11 {
        return true;
        //?} else {
        /*return false;
        *///?}
    }

    private static final String[] PACKS = {"world_foliage", "world_wet", "world_both"};
    private static String activePack = "";
    private static int settle;

    public static void register() {
        //? if >=1.21.11 {
        var container = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(BlurredMod.MOD_ID).orElseThrow();
        for (String p : PACKS) {
            net.fabricmc.fabric.api.resource.ResourceManagerHelper.registerBuiltinResourcePack(
                    Identifier.of(BlurredMod.MOD_ID, p), container,
                    net.minecraft.text.Text.literal("Blurred " + p.replace('_', ' ')),
                    net.fabricmc.fabric.api.resource.ResourcePackActivationType.NORMAL);
        }
        //?}
    }

    public static void tick(MinecraftClient client) {
        if (!supported()) {
            return;
        }
        post(client);
        packs(client);
    }

    private static void post(MinecraftClient client) {
        //? if >=1.21.11 {
        String look = Modules.AMBIENCE.isActive() ? Modules.AMBIENCE.lookId() : "none";
        String blur = Modules.MOTION_BLUR.isActive() ? Modules.MOTION_BLUR.strengthId() : "off";
        Identifier want = look.equals("none") && blur.equals("off") ? null : Identifier.of(BlurredMod.MOD_ID, look + "_" + blur);
        var renderer = client.gameRenderer;
        Identifier now = renderer.getPostProcessorId();
        boolean ours = now != null && now.getNamespace().equals(BlurredMod.MOD_ID);
        if (want != null && !want.equals(now) && (now == null || ours)) {
            ((dev.blurredclient.mod.mixin.GameRendererAccessor) renderer).blurred$setPostProcessor(want);
        } else if (want == null && ours) {
            renderer.clearPostProcessor();
        }
        //?}
    }

    private static boolean synced;

    private static void packs(MinecraftClient client) {
        if (!synced) {
            // Start from whichever of our packs the game already has on (saved in options).
            synced = true;
            for (String p : PACKS) {
                String id = profileId(client.getResourcePackManager(), p);
                if (id != null && client.getResourcePackManager().getEnabledIds().contains(id)) {
                    activePack = p;
                }
            }
        }
        boolean foliage = Modules.WAVING_FOLIAGE.isActive();
        boolean wet = Modules.WET_GROUND.isActive();
        String want = foliage && wet ? "world_both" : foliage ? "world_foliage" : wet ? "world_wet" : "";
        if (want.equals(activePack)) {
            settle = 0;
            return;
        }
        // Wait half a second, so switching both on reloads once, not twice.
        if (++settle < 10 || client.getOverlay() != null) {
            return;
        }
        settle = 0;
        activePack = want;
        var manager = client.getResourcePackManager();
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
