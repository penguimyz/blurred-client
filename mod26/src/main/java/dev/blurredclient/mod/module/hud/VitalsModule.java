package dev.blurredclient.mod.module.hud;

import dev.blurredclient.mod.fx.Sfx;
import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.ColorSetting;
import dev.blurredclient.mod.setting.ModeSetting;
import dev.blurredclient.mod.setting.SliderSetting;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.GuiBatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;

/**
 * When your health is low the edges of the screen darken red and pulse like
 * a heartbeat — faster the lower you go — with an optional heartbeat sound.
 */
public final class VitalsModule extends HudModule {
    public enum Style {
        PULSE("Pulse"), STEADY("Steady");

        private final String label;

        Style(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final SliderSetting threshold;
    private final ModeSetting<Style> style;
    private final ColorSetting colour;
    private final SliderSetting strength;
    private final BoolSetting heartbeat;
    private float shown;
    private double phase;
    private long lastNs = System.nanoTime();
    private boolean beatPlayed;

    public VitalsModule() {
        super("vitals", "Low health effect", "The screen edges pulse red when your health is low, with a heartbeat.",
                Category.HUD, false, new HudPlacement(HudPlacement.Anchor.TOP_LEFT, 0, 0));
        this.threshold = add(new SliderSetting("threshold", "Starts at", "Health (in hearts) below which it shows.", 4, 1, 10, 0.5, " hearts"));
        this.style = add(new ModeSetting<>("style", "Style", "Pulse with a heartbeat, or a steady glow.", Style.PULSE, Style::label));
        this.colour = add(new ColorSetting("colour", "Colour", "Colour of the edges.", 0xFFD0101E));
        this.strength = add(new SliderSetting("strength", "Strength", "How strong the effect is.", 0.8, 0.2, 1.0, 0.05, ""));
        this.heartbeat = add(new BoolSetting("heartbeat", "Heartbeat sound", "A soft thump with each pulse.", false));
    }

    @Override
    public boolean drawsItself() {
        return true;
    }

    @Override
    public Size measure(Minecraft client, boolean preview) {
        return new Size(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
    }

    @Override
    public int[] screenPosition(Minecraft client, int screenW, int screenH, int w, int h) {
        return new int[] {0, 0};
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, Minecraft client, boolean preview) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        float danger = 0f;
        if (preview) {
            danger = 0.6f;
        } else if (client.player != null && client.player.isAlive()) {
            float hearts = client.player.getHealth() / 2f;
            float limit = threshold.floatValue();
            danger = hearts >= limit ? 0f : 0.45f + 0.55f * (1f - hearts / limit);
        }
        shown += (danger - shown) * Math.min(1f, dt * 4f);
        if (shown < 0.01f) {
            return;
        }
        // Heart rate climbs as health falls: 70 to 150 beats a minute.
        double bpm = 70 + 80 * shown;
        phase += dt * bpm / 60.0;
        float beat = 1f;
        if (style.get() == Style.PULSE) {
            double p = phase % 1.0;
            // Two-part thump: a strong beat then a weaker echo.
            beat = (float) (0.55 + 0.45 * Math.max(Math.exp(-p * 14), 0.6 * Math.exp(-Math.abs(p - 0.22) * 18)));
            if (p < 0.05 && !beatPlayed) {
                beatPlayed = true;
                if (heartbeat.isOn() && !preview) {
                    Sfx.play(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.6f, 0.5f * shown);
                }
            } else if (p > 0.5) {
                beatPlayed = false;
            }
        }
        float a = Math.min(1f, shown * strength.floatValue() * beat);
        int w = client.getWindow().getGuiScaledWidth();
        int h = client.getWindow().getGuiScaledHeight();
        int c = colour.argb();
        int edge = Colors.fade(c, a);
        int clear = c & 0x00FFFFFF;
        float band = Math.min(w, h) * (0.18f + 0.12f * shown);
        GuiBatch.shape(ctx, () -> {
            GuiBatch.fill(ctx, 0, 0, w, band, edge, clear);
            GuiBatch.fill(ctx, 0, h - band, w, h, clear, edge);
            // Sides: vertical strips stepping down in strength.
            int steps = 24;
            for (int i = 0; i < steps; i++) {
                float f = 1f - i / (float) steps;
                int col = Colors.fade(c, a * f * f);
                float x0 = band * i / steps;
                float x1 = band * (i + 1) / steps;
                GuiBatch.fill(ctx, x0, 0, x1, h, col, col);
                GuiBatch.fill(ctx, w - x1, 0, w - x0, h, col, col);
            }
        });
    }
}
