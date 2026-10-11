package dev.blurredclient.mod.ui.kit;

import net.minecraft.client.font.TextRenderer;

import java.util.ArrayList;
import java.util.List;

/** Text-fitting helpers shared by widgets and skins. */
public final class Widgets {
    private Widgets() {}

    /** {@code s}, cut down with an ellipsis if it's wider than {@code maxWidth}. */
    public static String ellipsize(TextRenderer font, Skin skin, String s, int maxWidth) {
        if (skin.width(font, s) <= maxWidth) {
            return s;
        }
        String dots = "…";
        int room = maxWidth - skin.width(font, dots);
        int end = s.length();
        while (end > 0 && skin.width(font, s.substring(0, end)) > room) {
            end--;
        }
        return s.substring(0, end).stripTrailing() + dots;
    }

    /**
     * Word-wrap {@code s} to {@code maxWidth}. A single word wider than the
     * line is broken mid-word rather than overflowing.
     */
    public static List<String> wrap(TextRenderer font, Skin skin, String s, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (skin.width(font, candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            // Break an over-long word into pieces that fit.
            String rest = word;
            while (skin.width(font, rest) > maxWidth && rest.length() > 1) {
                int cut = rest.length() - 1;
                while (cut > 1 && skin.width(font, rest.substring(0, cut)) > maxWidth) {
                    cut--;
                }
                lines.add(rest.substring(0, cut));
                rest = rest.substring(cut);
            }
            line.append(rest);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }
}
