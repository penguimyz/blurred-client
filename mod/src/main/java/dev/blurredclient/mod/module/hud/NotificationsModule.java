package dev.blurredclient.mod.module.hud;

import dev.blurredclient.mod.hud.HudStyle;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.module.ModuleIds;
import dev.blurredclient.mod.setting.SliderSetting;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import dev.blurredclient.mod.ui.kit.Icons;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Toast cards in the corner — "Enabled · Zoom" — when a module is switched,
 * and for anything else that wants to tell you something briefly.
 *
 * <p>Cards slide in from the side, stack newest at the bottom, and slide out
 * after a few seconds. The stack grows upward from the module's placement, so
 * anchored bottom-right (the default) it builds up from the corner.
 */
public final class NotificationsModule extends HudModule {
    private static final int CARD_W = 118;
    private static final int CARD_H = 24;
    private static final int GAP = 3;
    private static final int MAX = 5;

    private record Toast(String title, String body, int color, Icons.Icon icon, long born) {}

    private static final Deque<Toast> TOASTS = new ArrayDeque<>();

    private final SliderSetting seconds;

    public NotificationsModule() {
        super(ModuleIds.NOTIFICATIONS, "Notifications",
                "Cards in the corner when you switch a module on or off, and for crew messages.",
                true, new HudPlacement(HudPlacement.Anchor.BOTTOM_RIGHT, -4, -4));
        this.seconds = add(new SliderSetting("seconds", "Show for", "How long each card stays up.",
                2.5, 1, 8, 0.5, "s"));
    }

    /** Queue a card. Safe to call from the client thread at any time. */
    public static void push(String title, String body, int color, Icons.Icon icon) {
        TOASTS.addLast(new Toast(title, body, color, icon, System.nanoTime()));
        while (TOASTS.size() > MAX) {
            TOASTS.removeFirst();
        }
    }

    public static void moduleToggled(String moduleName, boolean on) {
        push(on ? "Enabled" : "Disabled", moduleName, on ? 0xFF3BD87A : 0xFFFF5468,
                on ? Icons.Icon.PLUS : Icons.Icon.EXIT);
    }

    /** Seconds since birth, and drop expired cards. */
    private List<float[]> live() {
        float life = seconds.floatValue();
        long now = System.nanoTime();
        List<float[]> out = new ArrayList<>();
        Iterator<Toast> it = TOASTS.iterator();
        while (it.hasNext()) {
            Toast t = it.next();
            float age = (now - t.born()) / 1_000_000_000f;
            if (age > life + 0.4f) {
                it.remove();
            } else {
                out.add(new float[] {age});
            }
        }
        return out;
    }

    @Override
    public Size measure(MinecraftClient client, boolean preview) {
        int n = preview ? 2 : live().size();
        if (n == 0) {
            return null;
        }
        return new Size(CARD_W, n * CARD_H + (n - 1) * GAP);
    }

    @Override
    public void render(DrawContext ctx, MinecraftClient client, boolean preview) {
        List<Toast> cards = new ArrayList<>(TOASTS);
        if (preview) {
            long now = System.nanoTime();
            cards = List.of(new Toast("Enabled", "Zoom", 0xFF3BD87A, Icons.Icon.PLUS, now - 1_000_000_000L),
                    new Toast("Disabled", "Keystrokes", 0xFFFF5468, Icons.Icon.EXIT, now - 1_000_000_000L));
        }
        float life = seconds.floatValue();
        long now = System.nanoTime();
        var font = client.textRenderer;
        int y = 0;
        for (Toast t : cards) {
            float age = (now - t.born()) / 1_000_000_000f;
            // Ease in over 0.25s, out over the last 0.4s.
            float in = Math.min(1f, age / 0.25f);
            float out = Math.max(0f, Math.min(1f, (life + 0.4f - age) / 0.4f));
            float shown = easeOut(Math.min(in, out));
            int dx = Math.round((1f - shown) * (CARD_W + 8));

            int x = dx;
            Draw.roundedRect(ctx, x, y, CARD_W, CARD_H, 2, Colors.fade(0xD00E0F14, shown));
            Draw.roundedRect(ctx, x + 4, y + 4, 16, 16, 2, Colors.fade(t.color(), shown));
            Icons.draw(ctx, t.icon(), x + 7, y + 7, 10, Colors.fade(0xFFFFFFFF, shown));
            Draw.text(ctx, font, HudStyle.bold(t.title()), x + 25, y + 4, Colors.fade(0xFFFFFFFF, shown), 0.8f);
            Draw.text(ctx, font, HudStyle.text(t.body()), x + 25, y + 13, Colors.fade(0xFFB4B8C4, shown), 0.75f);
            // Life bar along the bottom.
            float left = preview ? 0.6f : Math.max(0f, 1f - age / life);
            Draw.rect(ctx, x + 4, y + CARD_H - 2, x + 4 + Math.round((CARD_W - 8) * left), y + CARD_H - 1,
                    Colors.fade(t.color(), shown * 0.8f));
            y += CARD_H + GAP;
        }
    }

    private static float easeOut(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }
}
