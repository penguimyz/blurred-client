package dev.blurredclient.mod.ui.kit;

import dev.blurredclient.mod.ui.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Small vector-style icons built from anti-aliased circles, rings and rounded
 * bars — so they stay smooth at any size, unlike pixel sprites, and need no
 * texture (whose drawing API changes between Minecraft versions).
 *
 * <p>Every icon is drawn into a square of side {@code s} at {@code x, y}.
 */
public final class Icons {
    private Icons() {}

    public enum Icon {
        PERSON, PEOPLE, GLOBE, GEAR, PUZZLE, EXIT, SEARCH, MONITOR, EYE, LIST,
        SLIDERS, STAR, CHAT, PALETTE, PLUS, LAYOUT
    }

    public static void draw(GuiGraphicsExtractor ctx, Icon icon, float x, float y, float s, int color) {
        float cx = x + s / 2f;
        float cy = y + s / 2f;
        float u = s / 12f;
        switch (icon) {
            case PERSON -> {
                Draw.circle(ctx, cx, y + 3.6f * u, 2.6f * u, color);
                bar(ctx, cx - 4.2f * u, y + 7.4f * u, 8.4f * u, 4.2f * u, color);
            }
            case PEOPLE -> {
                Draw.circle(ctx, cx - 2.2f * u, y + 3.8f * u, 2.2f * u, color);
                Draw.circle(ctx, cx + 2.6f * u, y + 4.4f * u, 1.9f * u, color);
                bar(ctx, cx - 5.6f * u, y + 7.4f * u, 7f * u, 4f * u, color);
                bar(ctx, cx + 0.4f * u, y + 7.8f * u, 4.8f * u, 3.6f * u, color);
            }
            case GLOBE -> {
                Draw.ring(ctx, cx, cy, 5f * u, 1.1f * u, color);
                bar(ctx, cx - 5f * u, cy - 0.5f * u, 10f * u, 1.1f * u, color);
                bar(ctx, cx - 0.55f * u, cy - 5f * u, 1.1f * u, 10f * u, color);
            }
            case GEAR -> {
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    Draw.circle(ctx, cx + (float) Math.cos(a) * 4.4f * u, cy + (float) Math.sin(a) * 4.4f * u,
                            1.3f * u, color);
                }
                Draw.ring(ctx, cx, cy, 3.9f * u, 1.6f * u, color);
            }
            case PUZZLE -> {
                bar(ctx, cx - 4f * u, cy - 3f * u, 8f * u, 7f * u, color);
                Draw.circle(ctx, cx, cy - 3.8f * u, 1.6f * u, color);
                Draw.circle(ctx, cx + 4.6f * u, cy + 0.4f * u, 1.6f * u, color);
            }
            case EXIT -> {
                Draw.ring(ctx, cx, cy + 0.6f * u, 4.6f * u, 1.2f * u, color);
                bar(ctx, cx - 0.65f * u, y + 0.8f * u, 1.3f * u, 6f * u, color);
            }
            case SEARCH -> {
                Draw.ring(ctx, cx - 1.2f * u, cy - 1.2f * u, 3.8f * u, 1.2f * u, color);
                for (int i = 0; i < 4; i++) {
                    Draw.circle(ctx, cx + (2.2f + i * 0.9f) * u, cy + (2.2f + i * 0.9f) * u, 0.85f * u, color);
                }
            }
            case MONITOR -> {
                Draw.roundedRectAlways(ctx, x + 1.2f * u, y + 1.8f * u, 9.6f * u, 6.6f * u, 1.4f * u, color);
                bar(ctx, cx - 2.6f * u, y + 9.6f * u, 5.2f * u, 1.2f * u, color);
            }
            case EYE -> {
                Draw.roundedRectAlways(ctx, x + 0.8f * u, cy - 2.6f * u, 10.4f * u, 5.2f * u, 2.6f * u, color);
                Draw.circle(ctx, cx, cy, 1.7f * u, 0xFF101014);
                Draw.circle(ctx, cx, cy, 0.8f * u, color);
            }
            case LIST -> {
                for (int i = 0; i < 3; i++) {
                    Draw.circle(ctx, x + 2f * u, y + (3f + i * 3f) * u, 0.9f * u, color);
                    bar(ctx, x + 4f * u, y + (2.4f + i * 3f) * u, 7f * u, 1.2f * u, color);
                }
            }
            case SLIDERS -> {
                bar(ctx, x + 1f * u, y + 3.4f * u, 10f * u, 1.2f * u, color);
                bar(ctx, x + 1f * u, y + 7.6f * u, 10f * u, 1.2f * u, color);
                Draw.circle(ctx, x + 4f * u, y + 4f * u, 1.8f * u, color);
                Draw.circle(ctx, x + 8f * u, y + 8.2f * u, 1.8f * u, color);
            }
            case STAR -> {
                bar(ctx, cx - 0.7f * u, y + 0.8f * u, 1.4f * u, 10.4f * u, color);
                bar(ctx, x + 0.8f * u, cy - 0.7f * u, 10.4f * u, 1.4f * u, color);
                Draw.circle(ctx, cx, cy, 2.4f * u, color);
            }
            case CHAT -> {
                Draw.roundedRectAlways(ctx, x + 1f * u, y + 1.6f * u, 10f * u, 7f * u, 2.2f * u, color);
                Draw.circle(ctx, x + 3.4f * u, y + 9.4f * u, 1.2f * u, color);
            }
            case PALETTE -> {
                Draw.ring(ctx, cx, cy, 4.8f * u, 1.3f * u, color);
                Draw.circle(ctx, cx - 1.8f * u, cy - 1.6f * u, 1f * u, color);
                Draw.circle(ctx, cx + 1.6f * u, cy - 1.8f * u, 1f * u, color);
                Draw.circle(ctx, cx + 2f * u, cy + 1.6f * u, 1f * u, color);
            }
            case PLUS -> {
                bar(ctx, cx - 0.7f * u, y + 2f * u, 1.4f * u, 8f * u, color);
                bar(ctx, x + 2f * u, cy - 0.7f * u, 8f * u, 1.4f * u, color);
            }
            case LAYOUT -> {
                Draw.roundedRectAlways(ctx, x + 1f * u, y + 1f * u, 4.4f * u, 10f * u, 1.2f * u, color);
                Draw.roundedRectAlways(ctx, x + 6.6f * u, y + 1f * u, 4.4f * u, 4.4f * u, 1.2f * u, color);
                Draw.roundedRectAlways(ctx, x + 6.6f * u, y + 6.6f * u, 4.4f * u, 4.4f * u, 1.2f * u, color);
            }
        }
    }

    /** A rounded bar with fully round ends. */
    private static void bar(GuiGraphicsExtractor ctx, float x, float y, float w, float h, int color) {
        Draw.roundedRectAlways(ctx, x, y, w, h, Math.min(w, h) / 2f, color);
    }
}
