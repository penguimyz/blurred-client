package dev.blurredclient.mod.module.feature;

import dev.blurredclient.mod.BlurredMod;
import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.ModeSetting;
import dev.blurredclient.mod.setting.SliderSetting;
import dev.blurredclient.mod.setting.TextSetting;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.kit.Tex;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Animated skins on your swords and tools: a moving galaxy, flames, rainbow,
 * lightning, ocean light, or your own image or GIF, shimmering across the
 * item's texture. Works by giving those items the enchantment shine and
 * swapping the shine's texture for an animated one — so enchanted items
 * wear the skin too. Only on your screen.
 *
 * <p>Your own images go in {@code .minecraft/config/blurred/overlays/}.
 */
public final class ItemSkinsModule extends Module {
    public enum Style {
        GALAXY("Galaxy"), FLAME("Flame"), RAINBOW("Rainbow"), LIGHTNING("Lightning"), OCEAN("Ocean"), IMAGE("Your image");

        private final String label;

        Style(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Apply {
        SWORDS("Swords"), WEAPONS("Weapons and tools"), EVERYTHING("Every item");

        private final String label;

        Apply(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final int SIZE = 128;
    private static final Identifier GLINT = ItemRenderer.ITEM_ENCHANTMENT_GLINT;
    /** The animated texture Skin shine wraps round your skin's outer layer. */
    public static final Identifier SKIN_TEXTURE = Identifier.of("blurred", "textures/misc/skin_shine.png");
    private boolean onItems;
    private boolean onEntities;

    private final ModeSetting<Style> style;
    private final ModeSetting<Apply> apply;
    private final TextSetting image;
    private final SliderSetting speed;
    private final SliderSetting brightness;
    private final BoolSetting onlyHeld;

    private NativeImageBackedTexture texture;
    private NativeImage pixels;
    private long lastFrame;
    private List<int[]> frames = new ArrayList<>();
    private String loadedImage = "";
    private Style lastStyle;

    public ItemSkinsModule() {
        super("item_skins", "Item skins", "Animated galaxy, flame, rainbow or your own image shimmering over your swords and tools.",
                Category.COSMETIC, false);
        this.style = add(new ModeSetting<>("style", "Skin", "What plays over the item.", Style.GALAXY, Style::label));
        this.image = add(new TextSetting("image", "Image file", "A PNG or GIF in .minecraft/config/blurred/overlays/ (e.g. fire.gif).", "", 64));
        image.visibleIf(() -> style.get() == Style.IMAGE);
        this.apply = add(new ModeSetting<>("apply", "Items", "Which items get the skin.", Apply.WEAPONS, Apply::label));
        this.speed = add(new SliderSetting("speed", "Speed", "How fast it moves.", 1.0, 0.2, 3.0, 0.1, "x"));
        this.brightness = add(new SliderSetting("brightness", "Brightness", "How strongly it shows.", 1.4, 0.3, 2.0, 0.05, "x"));
        this.onlyHeld = add(new BoolSetting("only_held", "Only in your hands", "Off: also in your inventory and on the ground.", false));
    }

    /** Whether {@code stack} should shine with the skin. */
    public boolean wants(ItemStack stack) {
        if (!isActive() || stack.isEmpty()) {
            return false;
        }
        if (onlyHeld.isOn()) {
            var p = MinecraftClient.getInstance().player;
            if (p == null || (p.getMainHandStack() != stack && p.getOffHandStack() != stack)) {
                return false;
            }
        }
        return switch (apply.get()) {
            case SWORDS -> stack.isIn(ItemTags.SWORDS);
            case WEAPONS -> stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.AXES) || stack.isIn(ItemTags.PICKAXES)
                    || stack.isIn(ItemTags.SHOVELS) || stack.isIn(ItemTags.HOES) || stack.isDamageable();
            case EVERYTHING -> true;
        };
    }

    /** Hand the shine textures back to the game; it reloads its own when next used. */
    private void drop() {
        var manager = MinecraftClient.getInstance().getTextureManager();
        if (onItems) {
            manager.destroyTexture(GLINT);
        }
        if (onEntities) {
            manager.destroyTexture(SKIN_TEXTURE);
        }
        texture = null;
        pixels = null;
        onItems = false;
        onEntities = false;
    }

    @Override
    protected void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (texture != null && client != null) {
            client.execute(() -> {
                // Drop ours; the game loads its own shine texture again next time it's used.
                client.getTextureManager().destroyTexture(GLINT);
                texture = null;
                pixels = null;
            });
        }
    }

    /** Called each frame: (re)draw the animated texture. */
    public void frame() {
        boolean items = isActive();
        boolean skin = dev.blurredclient.mod.module.Modules.SKIN_SHINE.isActive();
        if (!items && !skin) {
            if (texture != null) {
                drop();
            }
            return;
        }
        if (texture != null && (items != onItems || skin != onEntities)) {
            // Which shines wear the skin changed: start over with the right ones.
            drop();
        }
        long now = System.nanoTime();
        if (texture != null && now - lastFrame < 33_000_000L) {
            return;
        }
        lastFrame = now;
        if (texture == null) {
            create();
        }
        double t = now / 1e9 * speed.get();
        float k = brightness.floatValue();
        Style s = style.get();
        if (s == Style.IMAGE) {
            loadImage();
        }
        int frameIndex = frames.isEmpty() ? 0 : (int) ((now / 1e9 * 12 * speed.get()) % frames.size());
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int c = switch (s) {
                    case GALAXY -> galaxy(x, y, t);
                    case FLAME -> flame(x, y, t);
                    case RAINBOW -> Colors.hsv((x + y) / (float) (SIZE * 2) + (float) (t * 0.25), 0.75f, 1f);
                    case LIGHTNING -> lightning(x, y, t);
                    case OCEAN -> ocean(x, y, t);
                    case IMAGE -> frames.isEmpty() ? 0xFF000000 : frames.get(frameIndex)[y * SIZE + x];
                };
                Tex.setPixel(pixels, x, y, scale(c, k));
            }
        }
        texture.upload();
        lastStyle = s;
    }

    private void create() {
        pixels = new NativeImage(SIZE, SIZE, false);
        //? if >=1.21.11 {
        texture = new NativeImageBackedTexture(() -> "blurred item skin", pixels) {
            {
                // The shine scrolls its texture, so it has to repeat.
                this.sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().get(
                        com.mojang.blaze3d.textures.AddressMode.REPEAT, com.mojang.blaze3d.textures.AddressMode.REPEAT,
                        com.mojang.blaze3d.textures.FilterMode.LINEAR, com.mojang.blaze3d.textures.FilterMode.LINEAR, false);
            }
        };
        //?} elif >=1.21.8 {
        /*texture = new NativeImageBackedTexture(() -> "blurred item skin", pixels);
        texture.setClamp(false);
        texture.setFilter(true, false);
        *///?} elif >=1.21.4 {
        /*texture = new NativeImageBackedTexture(pixels);
        texture.setClamp(false);
        texture.setFilter(true, false);
        *///?} else {
        /*texture = new NativeImageBackedTexture(pixels);
        texture.setFilter(true, false);
        *///?}
        onItems = isActive();
        onEntities = dev.blurredclient.mod.module.Modules.SKIN_SHINE.isActive();
        var manager = MinecraftClient.getInstance().getTextureManager();
        if (onItems) {
            manager.registerTexture(GLINT, texture);
        }
        if (onEntities) {
            manager.registerTexture(SKIN_TEXTURE, texture);
        }
    }

    private static int scale(int argb, float k) {
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * k));
        int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * k));
        int b = Math.min(255, Math.round((argb & 0xFF) * k));
        // Partly see-through: the item shine adds by colour and ignores this, while
        // Skin shine's glowing layer uses it to let your skin show through.
        return 0x80000000 | (r << 16) | (g << 8) | b;
    }

    // ---- patterns; each tiles seamlessly because it's built from whole periods ----

    private static final double TAU = Math.PI * 2;

    private static double wave(int x, int y, int fx, int fy, double phase) {
        return Math.sin(TAU * (x * fx + y * fy) / SIZE + phase);
    }

    private static int galaxy(int x, int y, double t) {
        double n = wave(x, y, 1, 2, t * 0.6) * 0.5 + wave(x, y, 3, -1, t * 0.4 + 1) * 0.3 + wave(x, y, -2, 3, t * 0.8 + 2) * 0.2;
        n = n * 0.5 + 0.5;
        int nebula = Colors.lerp(0xFF1A0A3A, Colors.lerp(0xFF9B4BFF, 0xFF3FE0FF, (float) (wave(x, y, 1, 1, t * 0.3) * 0.5 + 0.5)),
                (float) Math.pow(n, 2.2));
        int h = (x * 73856093) ^ (y * 19349663);
        if ((h & 255) < 3) {
            float tw = (float) (0.5 + 0.5 * Math.sin(t * 4 + (h >> 8 & 63)));
            return Colors.lerp(nebula, 0xFFFFFFFF, tw);
        }
        return nebula;
    }

    private static int flame(int x, int y, double t) {
        double rise = y + t * 40;
        double n = wave(x, (int) rise, 2, 1, 0) * 0.5 + wave(x, (int) rise, -3, 2, 1.3) * 0.3 + wave(x, (int) rise, 5, 3, 2.1) * 0.2;
        double v = Math.max(0, n * 0.5 + 0.5);
        v = Math.pow(v, 1.6);
        if (v < 0.33) {
            return Colors.lerp(0xFF000000, 0xFFB01E0A, (float) (v / 0.33));
        }
        if (v < 0.66) {
            return Colors.lerp(0xFFB01E0A, 0xFFFF8A1C, (float) ((v - 0.33) / 0.33));
        }
        return Colors.lerp(0xFFFF8A1C, 0xFFFFF1A8, (float) ((v - 0.66) / 0.34));
    }

    private static int lightning(int x, int y, double t) {
        int beat = (int) (t * 2.5);
        double flash = Math.max(0, 1 - ((t * 2.5) % 1) * 3);
        double bolt = Math.abs(wave(x, y, 1, 0, beat * 1.7) * 18 + wave(x, y, 0, 3, beat) * 6 - (x - SIZE / 2.0) * 0.6);
        double core = Math.max(0, 1 - bolt / 3);
        double glow = Math.max(0, 1 - bolt / 14) * 0.4;
        float v = (float) Math.min(1, (core + glow) * flash + 0.08);
        return Colors.lerp(0xFF05070F, core > 0.5 ? 0xFFFFFFFF : 0xFF7FC9FF, v);
    }

    private static int ocean(int x, int y, double t) {
        double c = Math.abs(wave(x, y, 2, 1, t)) * Math.abs(wave(x, y, -1, 2, t * 1.3 + 1));
        double v = Math.pow(1 - c, 6);
        return Colors.lerp(0xFF031A2E, 0xFF8FF2FF, (float) Math.min(1, v * 1.2));
    }

    // ---- your own image ----

    private void loadImage() {
        String name = image.get() == null ? "" : image.get().trim();
        if (name.equals(loadedImage)) {
            return;
        }
        loadedImage = name;
        frames = new ArrayList<>();
        if (name.isEmpty()) {
            return;
        }
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("blurred").resolve("overlays");
        Path file = dir.resolve(name).normalize();
        if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
            BlurredMod.LOGGER.warn("Item skins: no image at {}", file);
            return;
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return;
            }
            ImageReader reader = readers.next();
            reader.setInput(in);
            int count = Math.min(120, reader.getNumImages(true));
            for (int i = 0; i < count; i++) {
                frames.add(resample(reader.read(i)));
            }
            reader.dispose();
        } catch (Exception e) {
            BlurredMod.LOGGER.warn("Item skins: couldn't read {}", file, e);
        }
    }

    private static int[] resample(BufferedImage img) {
        int[] out = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                out[y * SIZE + x] = img.getRGB(x * img.getWidth() / SIZE, y * img.getHeight() / SIZE) | 0xFF000000;
            }
        }
        return out;
    }
}
