package dev.blurredclient.mod.module.hud;

import dev.blurredclient.mod.fx.CombatEvents;
import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.setting.ColorSetting;
import dev.blurredclient.mod.setting.ModeSetting;
import dev.blurredclient.mod.setting.SliderSetting;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A marker around your crosshair when a hit lands — four ticks that snap
 * out and fade — and a bigger red one with a burst ring for a kill.
 */
public final class HitMarkersModule extends HudModule {
    public enum Style {
        TICKS("Ticks"), CORNERS("Corners"), RING("Ring");

        private final String label;

        Style(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private record Marker(long born, boolean kill) {}

    private static final int BOX = 64;

    private final ModeSetting<Style> style;
    private final ColorSetting hitColour;
    private final ColorSetting killColour;
    private final SliderSetting size;
    private final SliderSetting duration;
    private final List<Marker> markers = new ArrayList<>();

    public HitMarkersModule() {
        super("hit_markers", "Hit markers", "A marker at your crosshair when a hit lands, and a bigger red one for kills.",
                Category.COMBAT, false, new HudPlacement(HudPlacement.Anchor.CENTER, 0, 0));
        this.style = add(new ModeSetting<>("style", "Style", "Shape of the marker.", Style.TICKS, Style::label));
        this.hitColour = add(new ColorSetting("hit_colour", "Hit colour", "Colour for a normal hit.", 0xFFFFFFFF));
        this.killColour = add(new ColorSetting("kill_colour", "Kill colour", "Colour for a kill.", 0xFFFF4A5A));
        this.size = add(new SliderSetting("size", "Size", "How big the marker is.", 1.0, 0.5, 2.0, 0.1, "x"));
        this.duration = add(new SliderSetting("duration", "Duration", "How long it stays.", 0.35, 0.15, 1.0, 0.05, "s"));
        CombatEvents.onAttack(e -> {
            if (isActive()) {
                markers.add(new Marker(System.nanoTime(), false));
            }
        });
        CombatEvents.onDeath(d -> {
            if (isActive() && d.byYou()) {
                markers.add(new Marker(System.nanoTime(), true));
            }
        });
    }

    @Override
    public Size measure(Minecraft client, boolean preview) {
        if (!preview && markers.isEmpty()) {
            return null;
        }
        return new Size(BOX, BOX);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, Minecraft client, boolean preview) {
        long now = System.nanoTime();
        double life = duration.get() * 1e9;
        if (preview) {
            draw(ctx, 0.3f, false);
            return;
        }
        markers.removeIf(m -> now - m.born() > life * (m.kill() ? 2 : 1));
        for (Marker m : markers) {
            float t = (float) ((now - m.born()) / (life * (m.kill() ? 2 : 1)));
            draw(ctx, t, m.kill());
        }
    }

    private void draw(GuiGraphicsExtractor ctx, float t, boolean kill) {
        float c = BOX / 2f;
        float s = size.floatValue() * (kill ? 1.5f : 1f);
        float snap = 1f - (float) Math.pow(1 - Math.min(1f, t * 3f), 3);
        float fade = t < 0.5f ? 1f : 1f - (t - 0.5f) / 0.5f;
        int col = Colors.fade(kill ? killColour.argb() : hitColour.argb(), fade);
        float inner = (3f + 3f * snap) * s;
        float outer = inner + 5f * s;
        float th = (kill ? 1.8f : 1.3f) * s;
        switch (style.get()) {
            case TICKS -> {
                for (int i = 0; i < 4; i++) {
                    double a = Math.PI / 4 + i * Math.PI / 2;
                    float dx = (float) Math.cos(a);
                    float dy = (float) Math.sin(a);
                    Draw.line(ctx, c + dx * inner, c + dy * inner, c + dx * outer, c + dy * outer, th, col);
                }
            }
            case CORNERS -> {
                float r = outer;
                float b = 4f * s;
                Draw.rect(ctx, c - r, c - r, c - r + b, c - r + th, col);
                Draw.rect(ctx, c - r, c - r, c - r + th, c - r + b, col);
                Draw.rect(ctx, c + r - b, c - r, c + r, c - r + th, col);
                Draw.rect(ctx, c + r - th, c - r, c + r, c - r + b, col);
                Draw.rect(ctx, c - r, c + r - th, c - r + b, c + r, col);
                Draw.rect(ctx, c - r, c + r - b, c - r + th, c + r, col);
                Draw.rect(ctx, c + r - b, c + r - th, c + r, c + r, col);
                Draw.rect(ctx, c + r - th, c + r - b, c + r, c + r, col);
            }
            case RING -> Draw.ring(ctx, c, c, outer, th, col);
        }
        if (kill) {
            // A burst ring expanding past the marker.
            float r = outer + 14f * snap * s;
            Draw.ring(ctx, c, c, r, 1.2f, Colors.fade(col, (1 - t) * 0.8f));
        }
    }

    @Override
    public int[] screenPosition(Minecraft client, int screenW, int screenH, int w, int h) {
        return new int[] {(screenW - w) / 2, (screenH - h) / 2};
    }
}
