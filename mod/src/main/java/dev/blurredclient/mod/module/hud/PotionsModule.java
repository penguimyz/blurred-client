package dev.blurredclient.mod.module.hud;

import dev.blurredclient.mod.hud.HudStyle;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.module.ModuleIds;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * Your active potion effects: name, level and time left, with the effect's
 * own colour as a dot. Blinks in the last ten seconds.
 */
public final class PotionsModule extends HudModule {
    private static final String[] ROMAN = {"", "", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};
    private static final float SCALE = 0.8f;
    private static final int ROW_H = 13;

    private record Line(String text, int colour, boolean ending) {}

    public PotionsModule() {
        super(ModuleIds.POTIONS, "Potion effects", "Your active effects, their level and how long they have left.",
                false, new HudPlacement(HudPlacement.Anchor.MIDDLE_LEFT, 4, 0));
    }

    private List<Line> lines(MinecraftClient client, boolean preview) {
        List<Line> out = new ArrayList<>();
        if (preview || client.player == null) {
            out.add(new Line("Speed II  1:30", 0xFF33EBFF, false));
            out.add(new Line("Strength  0:08", 0xFFFFC700, true));
            return out;
        }
        for (StatusEffectInstance e : client.player.getStatusEffects()) {
            int amp = e.getAmplifier() + 1;
            String level = amp < ROMAN.length ? ROMAN[amp] : " " + amp;
            String time = e.isInfinite() ? "∞" : format(e.getDuration());
            out.add(new Line(e.getEffectType().value().getName().getString() + level + "  " + time,
                    0xFF000000 | e.getEffectType().value().getColor(), !e.isInfinite() && e.getDuration() < 200));
        }
        return out;
    }

    private static String format(int ticks) {
        int s = ticks / 20;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    @Override
    public Size measure(MinecraftClient client, boolean preview) {
        List<Line> lines = lines(client, preview);
        if (lines.isEmpty()) {
            return null;
        }
        int widest = 0;
        for (Line l : lines) {
            widest = Math.max(widest, Draw.width(client.textRenderer, HudStyle.text(l.text()), SCALE));
        }
        return new Size(widest + 18, lines.size() * ROW_H + 4);
    }

    @Override
    public void render(DrawContext ctx, MinecraftClient client, boolean preview) {
        List<Line> lines = lines(client, preview);
        Size s = measure(client, preview);
        if (s == null) {
            return;
        }
        HudStyle.panel(ctx, 0, 0, s.width(), s.height());
        var font = client.textRenderer;
        boolean blinkOff = (System.currentTimeMillis() / 400) % 2 == 0;
        int y = 2;
        for (Line l : lines) {
            float a = l.ending() && blinkOff ? 0.45f : 1f;
            Draw.circle(ctx, 7.5f, y + ROW_H / 2f, 2.5f, Colors.fade(l.colour(), a));
            Draw.text(ctx, font, HudStyle.text(l.text()), 14, y + (ROW_H - Draw.height(font, SCALE)) / 2 + 1,
                    Colors.fade(0xFFFFFFFF, a), SCALE);
            y += ROW_H;
        }
    }
}
