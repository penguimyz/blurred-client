package dev.blurredclient.mod.module.hud;

import dev.blurredclient.mod.hud.CpsTracker;
import dev.blurredclient.mod.hud.HudStyle;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.module.ModuleIds;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import dev.blurredclient.mod.ui.kit.Anim;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

/**
 * W A S D, the mouse buttons with their CPS, and the space bar — lighting up
 * as you press them. Reads the keybindings, not raw keys, so it follows your
 * rebinds.
 *
 * <p>Display only: it observes the state of keys you're already pressing.
 */
public final class KeystrokesModule extends HudModule {
    private static final int K = 18;
    private static final int G = 2;

    private final BoolSetting showMouse;
    private final BoolSetting showSpace;
    private final BoolSetting showShift;
    private final Anim[] press = new Anim[8];

    public KeystrokesModule() {
        super(ModuleIds.KEYSTROKES, "Keystrokes", "Shows your movement keys and clicks as you press them.",
                false, new HudPlacement(HudPlacement.Anchor.BOTTOM_LEFT, 4, -26));
        this.showMouse = add(new BoolSetting("mouse", "Mouse buttons", "Left and right click, with clicks per second.", true));
        this.showSpace = add(new BoolSetting("space", "Space bar", "A wide key for jumping.", true));
        this.showShift = add(new BoolSetting("shift", "Shift", "Your sneak key, next to the space bar.", true));
        for (int i = 0; i < press.length; i++) {
            press[i] = new Anim(0f, 22f);
        }
    }

    @Override
    public Size measure(MinecraftClient client, boolean preview) {
        int rows = 2 + (showMouse.isOn() ? 1 : 0);
        int h = rows * K + (rows - 1) * G + (showSpace.isOn() || showShift.isOn() ? G + 10 : 0);
        return new Size(K * 3 + G * 2, h);
    }

    @Override
    public void render(DrawContext ctx, MinecraftClient client, boolean preview) {
        var o = client.options;
        float t = System.nanoTime() / 1_000_000_000f;
        boolean[] down = preview
                ? new boolean[] {true, false, false, false, ((int) t) % 2 == 0, false, false, false}
                : new boolean[] {pressed(o.forwardKey), pressed(o.leftKey), pressed(o.backKey), pressed(o.rightKey),
                        pressed(o.attackKey), pressed(o.useKey), pressed(o.jumpKey), pressed(o.sneakKey)};

        key(ctx, client, K + G, 0, K, K, "W", 0, down[0]);
        key(ctx, client, 0, K + G, K, K, "A", 1, down[1]);
        key(ctx, client, K + G, K + G, K, K, "S", 2, down[2]);
        key(ctx, client, (K + G) * 2, K + G, K, K, "D", 3, down[3]);
        int y = (K + G) * 2;
        if (showMouse.isOn()) {
            int mw = (K * 3 + G) / 2;
            String l = preview ? "12" : String.valueOf(CpsTracker.leftCps());
            String r = preview ? "4" : String.valueOf(CpsTracker.rightCps());
            mouseKey(ctx, client, 0, y, mw, "LMB", l, 4, down[4]);
            mouseKey(ctx, client, mw + G, y, mw, "RMB", r, 5, down[5]);
            y += K + G;
        }
        // Bottom row: Shift on the left, the space bar filling the rest.
        int full = K * 3 + G * 2;
        int x = 0;
        if (showShift.isOn()) {
            int shiftW = showSpace.isOn() ? K + 6 : full;
            float p = press[7].follow(down[7]);
            Draw.roundedRect(ctx, 0, y, shiftW, 10, 2, Colors.lerp(0x900E0F14, Colors.fade(HudStyle.accent(), 0.9f), p));
            Text label = HudStyle.bold("SHIFT");
            var font = client.textRenderer;
            Draw.text(ctx, font, label, (shiftW - Draw.width(font, label, 0.6f)) / 2, y + 2, 0xFFFFFFFF, 0.6f);
            x = shiftW + G;
        }
        if (showSpace.isOn()) {
            int sw = full - x;
            float p = press[6].follow(down[6]);
            Draw.roundedRect(ctx, x, y, sw, 10, 2, Colors.lerp(0x900E0F14, Colors.fade(HudStyle.accent(), 0.9f), p));
            int barW = Math.round(sw * 0.45f);
            Draw.roundedRect(ctx, x + (sw - barW) / 2, y + 5, barW, 1, 0, Colors.lerp(0xA0FFFFFF, 0xFFFFFFFF, p));
        }
    }

    private static boolean pressed(KeyBinding k) {
        return k.isPressed();
    }

    private void key(DrawContext ctx, MinecraftClient client, int x, int y, int w, int h, String label, int i, boolean down) {
        float p = press[i].follow(down);
        Draw.roundedRect(ctx, x, y, w, h, 2, Colors.lerp(0x900E0F14, Colors.fade(HudStyle.accent(), 0.9f), p));
        Text t = HudStyle.bold(label);
        var font = client.textRenderer;
        int tw = Draw.width(font, t, 0.9f);
        Draw.text(ctx, font, t, x + (w - tw) / 2, y + (h - Draw.height(font, 0.9f)) / 2 + 1, 0xFFFFFFFF, 0.9f);
    }

    private void mouseKey(DrawContext ctx, MinecraftClient client, int x, int y, int w, String label, String cps, int i, boolean down) {
        float p = press[i].follow(down);
        Draw.roundedRect(ctx, x, y, w, K, 2, Colors.lerp(0x900E0F14, Colors.fade(HudStyle.accent(), 0.9f), p));
        var font = client.textRenderer;
        Text a = HudStyle.bold(label);
        Text b = HudStyle.text(cps + " CPS");
        Draw.text(ctx, font, a, x + (w - Draw.width(font, a, 0.75f)) / 2, y + 2, 0xFFFFFFFF, 0.75f);
        Draw.text(ctx, font, b, x + (w - Draw.width(font, b, 0.6f)) / 2, y + 10, 0xFFB4B8C4, 0.6f);
    }
}
