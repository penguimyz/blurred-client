package dev.blurredclient.mod.cosmetic;

import dev.blurredclient.mod.BlurredMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds every Blurred cape the launcher has told us about, keyed by Minecraft
 * username.
 *
 * <p>Capes are pushed over the launcher bridge as base64 PNGs — see
 * {@code commands/capes.rs} for how the launcher collects them from other
 * players over IRC. This class only has to decode, register a texture, and hand
 * out an {@link Identifier} when a renderer asks.
 *
 * <p><b>Threading.</b> Cape data arrives on the bridge reader thread, but
 * texture registration must happen on the render thread — calling into the
 * texture manager from anywhere else is how you get a silent GL crash. So
 * decoding is split: the raw bytes are stashed from any thread, and the actual
 * upload is deferred to the first {@link #capeFor} call, which by definition is
 * already on the render thread.
 */
public final class CapeManager {
    /** username (lowercased) -> decoded texture id, once uploaded. */
    private static final Map<String, Identifier> UPLOADED = new ConcurrentHashMap<>();
    /** username (lowercased) -> base64 PNG waiting to be uploaded. */
    private static final Map<String, String> PENDING = new ConcurrentHashMap<>();
    /** Usernames whose data failed to decode, so we stop retrying every frame. */
    private static final Map<String, Boolean> FAILED = new ConcurrentHashMap<>();

    private CapeManager() {}

    /**
     * Record a cape for a player. Safe to call from any thread; the texture is
     * uploaded lazily on the render thread.
     *
     * @param base64Png a 64x32 (or 64x64) cape sheet, base64-encoded, or null
     *                  to clear the player's cape
     */
    public static void put(String username, String base64Png) {
        if (username == null || username.isBlank()) {
            return;
        }
        String key = username.toLowerCase(Locale.ROOT);

        if (base64Png == null || base64Png.isBlank()) {
            PENDING.remove(key);
            FAILED.remove(key);
            Identifier old = UPLOADED.remove(key);
            if (old != null) {
                MinecraftClient.getInstance().execute(
                        () -> MinecraftClient.getInstance().getTextureManager().destroyTexture(old));
            }
            return;
        }

        // Replacing an existing cape: drop the upload so it re-decodes.
        PENDING.put(key, base64Png);
        FAILED.remove(key);
        UPLOADED.remove(key);
    }

    /** Forget everything — used when the launcher connection drops. */
    public static void clear() {
        PENDING.clear();
        FAILED.clear();
        UPLOADED.clear();
    }

    /**
     * The cape texture for a player, or null if they have none.
     *
     * <p>Must be called from the render thread: it may upload a texture.
     */
    public static Identifier capeFor(String username) {
        if (username == null) {
            return null;
        }
        String key = username.toLowerCase(Locale.ROOT);

        Identifier existing = UPLOADED.get(key);
        if (existing != null) {
            return existing;
        }
        if (FAILED.containsKey(key)) {
            return null;
        }

        String data = PENDING.remove(key);
        if (data == null) {
            return null;
        }

        Identifier id = upload(key, data);
        if (id == null) {
            // Mark it failed rather than leaving it pending, or a malformed
            // cape would be re-decoded on every single frame this player is
            // on screen.
            FAILED.put(key, Boolean.TRUE);
            return null;
        }
        UPLOADED.put(key, id);
        return id;
    }

    private static Identifier upload(String key, String base64Png) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64Png);
            NativeImage image;
            try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
                image = NativeImage.read(in);
            }

            // Cape sheets are 64x32 (legacy) or 64x64. Anything else isn't a
            // cape and would render as garbage stretched over the model.
            if (image.getWidth() != 64 || (image.getHeight() != 32 && image.getHeight() != 64)) {
                BlurredMod.LOGGER.warn(
                        "Ignoring cape for {}: expected a 64x32 or 64x64 sheet, got {}x{}",
                        key, image.getWidth(), image.getHeight());
                image.close();
                return null;
            }

            Identifier id = Identifier.of(BlurredMod.MOD_ID, "capes/" + sanitize(key));
            // 1.21.9+ takes a name supplier used as the GL debug label; older
            // versions take the image alone.
            //? if >=1.21.8 {
            NativeImageBackedTexture texture = new NativeImageBackedTexture(id::toString, image);
            //?} else {
            /*NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
             *///?}
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            return id;
            // RuntimeException covers the IllegalArgumentException that a bad
            // base64 payload throws — listing both is a compile error, since
            // multi-catch alternatives can't be related by subclassing.
        } catch (IOException | RuntimeException e) {
            BlurredMod.LOGGER.warn("Could not decode cape for {}: {}", key, e.toString());
            return null;
        }
    }

    /**
     * Identifier paths accept only {@code [a-z0-9_.-/]}. Minecraft usernames are
     * already a subset of that once lowercased, but a malformed name off the
     * network must not be able to throw here — so anything unexpected is
     * replaced rather than rejected.
     */
    private static String sanitize(String key) {
        StringBuilder sb = new StringBuilder(key.length());
        for (char c : key.toCharArray()) {
            sb.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' ? c : '_');
        }
        return sb.toString();
    }
}
