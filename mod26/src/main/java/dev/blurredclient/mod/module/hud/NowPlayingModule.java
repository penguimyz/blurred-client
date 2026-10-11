package dev.blurredclient.mod.module.hud;

import dev.blurredclient.mod.hud.HudStyle;
import dev.blurredclient.mod.hud.SystemMedia;
import dev.blurredclient.mod.mixin.MusicTrackerAccessor;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.ModeSetting;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;

/**
 * The song you're listening to: whatever your computer is playing (Spotify,
 * YouTube in a browser, ...) or, failing that, Minecraft's own music, with
 * the artist, a progress bar and little equaliser bars that dance while it
 * plays.
 */
public final class NowPlayingModule extends HudModule {
    public enum Source {
        AUTO("Computer, then Minecraft"), SYSTEM("Computer only"), MINECRAFT("Minecraft only");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final int WIDTH = 150;
    private static final int ART = 22;

    private final ModeSetting<Source> source;
    private final BoolSetting showPaused;
    private final BoolSetting progress;

    private record Song(String title, String artist, boolean playing, double position, double length) {}

    public NowPlayingModule() {
        super("now_playing", "Now playing", "The song you're listening to, from your computer or Minecraft's music.",
                false, new HudPlacement(HudPlacement.Anchor.MIDDLE_LEFT, 4, 0));
        this.source = add(new ModeSetting<>("source", "Source",
                "Your computer's music (Spotify, browser, etc. - Windows only), Minecraft's music, or both.",
                Source.AUTO, Source::label));
        this.showPaused = add(new BoolSetting("paused", "Show when paused", "Keep the card up while the music is paused.", true));
        this.progress = add(new BoolSetting("progress", "Progress bar", "How far into the song you are.", true));
    }

    private Song song(Minecraft client, boolean preview) {
        if (preview) {
            return new Song("Blurred Waves", "Blurred FM", true, 74, 201);
        }
        if (source.get() != Source.MINECRAFT) {
            SystemMedia.ensureRunning();
            SystemMedia.Track t = SystemMedia.current();
            if (t != null && (t.playing() || showPaused.isOn())) {
                String by = t.artist().isEmpty() ? t.app() : t.app().isEmpty() ? t.artist() : t.artist() + "  ·  " + t.app();
                return new Song(t.title(), by, t.playing(), t.positionNow(), t.length());
            }
        } else if (!othersWantMedia()) {
            SystemMedia.stop();
        }
        if (source.get() == Source.SYSTEM) {
            return null;
        }
        return minecraftMusic(client);
    }

    private static Song minecraftMusic(Minecraft client) {
        SoundInstance current = ((MusicTrackerAccessor) client.getMusicManager()).blurred$current();
        if (current == null || !client.getSoundManager().isActive(current)) {
            return null;
        }
        Sound sound = current.getSound();
        if (sound == null) {
            return null;
        }
        // "minecraft:music/game/sweden" -> "Sweden"
        String path = sound.getLocation().getPath();
        String name = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
        StringBuilder title = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                title.append(title.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return new Song(title.toString(), "Minecraft", true, -1, 0);
    }

    /** Lyrics and dancing pets also need to know what's playing. */
    private static boolean othersWantMedia() {
        return dev.blurredclient.mod.module.Modules.LYRICS.wantsMedia() || dev.blurredclient.mod.module.Modules.PETS.wantsMedia();
    }

    /** Called every tick: the watcher process shouldn't outlive the module. */
    public void tickWatcher() {
        if ((!isActive() || source.get() == Source.MINECRAFT) && !othersWantMedia()) {
            SystemMedia.stop();
        }
    }

    @Override
    public Size measure(Minecraft client, boolean preview) {
        Song s = song(client, preview);
        if (s == null) {
            return null;
        }
        return new Size(WIDTH, ART + 8);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, Minecraft client, boolean preview) {
        Song s = song(client, preview);
        if (s == null) {
            return;
        }
        int w = WIDTH;
        int h = ART + 8;
        var font = client.font;
        HudStyle.panel(ctx, 0, 0, w, h);

        // "Album art": an accent tile with equaliser bars.
        int accent = HudStyle.accent();
        Draw.rect(ctx, 4, 4, 4 + ART, 4 + ART, accent);
        double t = System.nanoTime() / 1e9;
        for (int i = 0; i < 4; i++) {
            float level = s.playing()
                    ? (float) (0.35 + 0.65 * Math.abs(Math.sin(t * (3.1 + i * 1.7) + i * 1.3)))
                    : 0.2f;
            int bh = Math.max(2, Math.round((ART - 8) * level));
            int bx = 4 + 4 + i * 4;
            Draw.rect(ctx, bx, 4 + ART - 4 - bh, bx + 2, 4 + ART - 4, 0xF0FFFFFF);
        }

        int tx = 4 + ART + 6;
        int maxW = w - tx - 5;
        Draw.text(ctx, font, HudStyle.bold(fit(font, s.title(), maxW, 0.85f, true)), tx, 5, 0xFFFFFFFF, 0.85f);
        String sub = s.artist();
        String time = s.position() >= 0 && s.length() > 0 ? clock(s.position()) + " / " + clock(s.length()) : "";
        if (!s.playing()) {
            time = "Paused";
        }
        int timeW = time.isEmpty() ? 0 : Draw.width(font, HudStyle.text(time), 0.7f) + 6;
        Draw.text(ctx, font, HudStyle.text(fit(font, sub, maxW - timeW, 0.7f, false)), tx, 15, 0xFFB4B8C4, 0.7f);
        if (!time.isEmpty()) {
            Draw.text(ctx, font, HudStyle.text(time), w - 5 - timeW + 6, 15, 0xFF7C8090, 0.7f);
        }

        if (progress.isOn() && s.length() > 0 && s.position() >= 0) {
            float f = (float) Math.max(0, Math.min(1, s.position() / s.length()));
            Draw.rect(ctx, tx, h - 5, w - 5, h - 3, 0x40FFFFFF);
            Draw.rect(ctx, tx, h - 5, tx + (w - 5 - tx) * f, h - 3, Colors.fade(accent, 1f));
        }
    }

    private static String fit(net.minecraft.client.gui.Font font, String s, int maxW, float scale, boolean bold) {
        if (width(font, s, scale, bold) <= maxW) {
            return s;
        }
        String cut = s;
        while (cut.length() > 1 && width(font, cut + "…", scale, bold) > maxW) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.stripTrailing() + "…";
    }

    private static int width(net.minecraft.client.gui.Font font, String s, float scale, boolean bold) {
        Component t = bold ? HudStyle.bold(s) : HudStyle.text(s);
        return Draw.width(font, t, scale);
    }

    private static String clock(double seconds) {
        int s = (int) Math.max(0, seconds);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }
}
