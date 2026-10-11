package dev.blurredclient.mod.module.hud;

import dev.blurredclient.mod.hud.HudRenderer;
import dev.blurredclient.mod.hud.HudStyle;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.ui.Draw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * A single line of text on the HUD — FPS, ping, the clock.
 *
 * <p>By default a readout lives inside the {@link InfoBarModule}, the merged
 * pill along the top edge. Switching "In info bar" off pulls it out into its
 * own panel that can be placed anywhere.
 *
 * <p>The readout is computed once per frame and cached, because the bar and
 * the renderer both ask for it, and readouts that smooth their value (FPS,
 * ping) would otherwise step twice a frame.
 */
public abstract class TextHudModule extends HudModule {
    /** One readout: its text and the colour to draw it in. */
    public record Readout(String text, int color) {}

    private final BoolSetting inBar;
    private long cachedFrame = -1;
    private boolean cachedPreview;
    private Readout cached;

    protected TextHudModule(String id, String name, String description,
                            boolean defaultEnabled, int stackIndex) {
        super(id, name, description, defaultEnabled,
                // Where it lands if pulled out of the bar: a column down the
                // left edge, below where the bar sits, one row per readout.
                new HudPlacement(HudPlacement.Anchor.TOP_LEFT, 4, 20 + stackIndex * 14));
        this.inBar = add(new BoolSetting("in_bar", "In info bar",
                "Show this inside the merged bar. Off gives it its own panel you can place anywhere.",
                true));
    }

    public boolean inBar() {
        return inBar.isOn();
    }

    @Override
    public boolean drawsItself() {
        return !inBar.isOn();
    }

    /** Compute the readout. Null when there's nothing to show (e.g. ping in singleplayer). */
    protected abstract Readout compute(MinecraftClient client, boolean preview);

    /** This frame's readout, computed at most once per frame. */
    public Readout current(MinecraftClient client, boolean preview) {
        long frame = HudRenderer.frame();
        if (frame != cachedFrame || preview != cachedPreview) {
            cachedFrame = frame;
            cachedPreview = preview;
            cached = compute(client, preview);
        }
        return cached;
    }

    @Override
    public Size measure(MinecraftClient client, boolean preview) {
        Readout r = current(client, preview);
        if (r == null) {
            return null;
        }
        var font = client.textRenderer;
        Text text = HudStyle.text(r.text());
        return new Size(
                HudStyle.PAD_X * 2 + Draw.width(font, text, HudStyle.TEXT_SCALE),
                Draw.height(font, HudStyle.TEXT_SCALE) + HudStyle.PAD_Y * 2);
    }

    @Override
    public void render(DrawContext ctx, MinecraftClient client, boolean preview) {
        Readout r = current(client, preview);
        Size size = measure(client, preview);
        if (r == null || size == null) {
            return;
        }
        HudStyle.panel(ctx, 0, 0, size.width(), size.height());
        int lineH = Draw.height(client.textRenderer, HudStyle.TEXT_SCALE);
        Draw.text(ctx, client.textRenderer, HudStyle.text(r.text()),
                HudStyle.PAD_X, (size.height() - lineH) / 2, r.color(), HudStyle.TEXT_SCALE);
    }
}
