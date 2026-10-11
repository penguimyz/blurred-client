package dev.blurredclient.mod.module.hud;

import dev.blurredclient.mod.hud.HudStyle;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.module.ModuleIds;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.ui.Draw;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * A small client tag: the Blurred mark and name, optionally with your FPS and
 * the time — the strip modern clients put in the corner.
 */
public final class WatermarkModule extends HudModule {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm a");
    private static final float SCALE = 0.85f;

    private final BoolSetting showFps;
    private final BoolSetting showTime;

    public WatermarkModule() {
        super(ModuleIds.WATERMARK, "Watermark", "The Blurred tag in the corner, with FPS and time if you want them.",
                false, new HudPlacement(HudPlacement.Anchor.TOP_LEFT, 4, 22));
        this.showFps = add(new BoolSetting("fps", "Show FPS", "Frame rate after the name.", true));
        this.showTime = add(new BoolSetting("time", "Show time", "The real time after the name.", false));
    }

    private String tail(Minecraft client, boolean preview) {
        StringBuilder sb = new StringBuilder();
        if (showFps.isOn()) {
            sb.append("  |  ").append(preview ? 144 : client.getFps()).append(" fps");
        }
        if (showTime.isOn()) {
            sb.append("  |  ").append(preview ? "12:30 PM" : LocalTime.now().format(CLOCK));
        }
        return sb.toString();
    }

    @Override
    public Size measure(Minecraft client, boolean preview) {
        var font = client.font;
        int w = 18 + Draw.width(font, HudStyle.bold("Blurred"), SCALE)
                + Draw.width(font, HudStyle.text(tail(client, preview)), SCALE) + 7;
        return new Size(w, 15);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, Minecraft client, boolean preview) {
        Size s = measure(client, preview);
        var font = client.font;
        HudStyle.panel(ctx, 0, 0, s.width(), s.height());
        int accent = HudStyle.accent();
        Draw.roundedRect(ctx, 3, 3, 9, 9, 3, accent);
        Draw.circle(ctx, 7.5f, 8.2f, 2.3f, 0xFFFFFFFF);
        int x = 16;
        Component name = HudStyle.bold("Blurred");
        int ty = (s.height() - Draw.height(font, SCALE)) / 2 + 1;
        Draw.text(ctx, font, name, x, ty, 0xFFFFFFFF, SCALE);
        x += Draw.width(font, name, SCALE);
        Draw.text(ctx, font, HudStyle.text(tail(client, preview)), x, ty, 0xFFB4B8C4, SCALE);
    }
}
