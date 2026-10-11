package dev.blurredclient.mod.module.hud;

import dev.blurredclient.mod.hud.HudStyle;
import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * What kind of hit your next swing will be — CRIT, SPRINT, SWEEP or NORMAL —
 * always shown, just under the crosshair, separate from Hit timing. A bar
 * along the bottom fills as your swing recharges; until it's full the label
 * is dimmed, because an uncharged swing can't crit or sweep.
 */
public final class HitTypeModule extends HudModule {
    private final BoolSetting showReason;
    private final dev.blurredclient.mod.ui.kit.Anim swap = new dev.blurredclient.mod.ui.kit.Anim(1f, 16f);
    private CombatInfoModule.HitType last = CombatInfoModule.HitType.NORMAL;
    private int fromColour = CombatInfoModule.HitType.NORMAL.colour;

    public HitTypeModule() {
        super("hit_type", "Hit type", "Always shows whether your next hit will be a crit, sprint, sweep or normal hit.",
                Category.COMBAT, false, new HudPlacement(HudPlacement.Anchor.CENTER, 0, 12));
        this.showReason = add(new BoolSetting("reason", "Show why", "A word on why: falling, knockback, sword...", false));
    }

    private static final float SCALE = 0.8f;

    @Override
    public Size measure(MinecraftClient client, boolean preview) {
        var font = client.textRenderer;
        int w = 14 + Draw.width(font, HudStyle.bold("SPRINT"), SCALE);
        if (showReason.isOn()) {
            w += 4 + Draw.width(font, HudStyle.text("sword, standing"), 0.6f);
        }
        return new Size(w + 6, 13);
    }

    @Override
    public void render(DrawContext ctx, MinecraftClient client, boolean preview) {
        ClientPlayerEntity p = client.player;
        CombatInfoModule.HitType type = preview || p == null ? CombatInfoModule.HitType.SPRINT : CombatInfoModule.predict(p);
        float charge = preview || p == null ? 1f : p.getAttackCooldownProgress(0.5f);
        if (type != last) {
            fromColour = last.colour;
            last = type;
            swap.snap(0f);
        }
        float t = swap.follow(true);
        int colour = Colors.lerp(fromColour, type.colour, t);
        boolean ready = charge > 0.9f;
        var font = client.textRenderer;
        Size s = measure(client, preview);
        HudStyle.panel(ctx, 0, 0, s.width(), s.height());

        // A square marker in the hit type's colour, and the swing charge as a
        // bar along the bottom edge.
        Draw.rect(ctx, 4, 4, 9, 9, colour);
        Draw.rect(ctx, 0, s.height() - 1.5f, s.width(), s.height(), 0x30FFFFFF);
        Draw.rect(ctx, 0, s.height() - 1.5f, s.width() * Math.min(1f, charge), s.height(),
                ready ? colour : Colors.fade(colour, 0.6f));

        int text = ready ? colour : Colors.fade(colour, 0.55f);
        float slide = (1 - t) * 4;
        Draw.text(ctx, font, HudStyle.bold(type.label), 14, Math.round(3 + slide), Colors.fade(text, t), SCALE);
        if (showReason.isOn()) {
            int x = 14 + Draw.width(font, HudStyle.bold(type.label), SCALE) + 4;
            Draw.text(ctx, font, HudStyle.text(type.why), x, 4, 0xFF8A8E9A, 0.6f);
        }
    }
}
