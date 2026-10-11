package dev.blurredclient.mod.module.hud;

import dev.blurredclient.mod.fx.CombatEvents;
import dev.blurredclient.mod.hud.HudStyle;
import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import dev.blurredclient.mod.ui.kit.Anim;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * How many totems you're carrying, and how many you've popped — with a flash
 * when one goes. Other players' pops show on their name tags (Better name
 * tags), counted from the totem animation everyone nearby already sees.
 */
public final class TotemCounterModule extends HudModule {
    private final BoolSetting hideWhenNone;
    private final Anim flash = new Anim(0f, 3f);

    public TotemCounterModule() {
        super("totem_counter", "Totem counter", "Totems you're carrying and how many you've popped.",
                Category.COMBAT, false, new HudPlacement(HudPlacement.Anchor.BOTTOM_CENTER, 110, -2));
        this.hideWhenNone = add(new BoolSetting("hide_none", "Hide with none", "Only show while you have a totem.", false));
        CombatEvents.onTotem(e -> {
            if (e == MinecraftClient.getInstance().player) {
                flash.snap(1f);
            }
        });
    }

    private static int count(MinecraftClient client) {
        if (client.player == null) {
            return 0;
        }
        PlayerInventory inv = client.player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(Items.TOTEM_OF_UNDYING)) {
                n += s.getCount();
            }
        }
        return n;
    }

    @Override
    public Size measure(MinecraftClient client, boolean preview) {
        int n = preview ? 3 : count(client);
        if (!preview && n == 0 && hideWhenNone.isOn()) {
            return null;
        }
        return new Size(44, 20);
    }

    @Override
    public void render(DrawContext ctx, MinecraftClient client, boolean preview) {
        int n = preview ? 3 : count(client);
        int pops = preview ? 1 : client.player == null ? 0 : CombatEvents.pops(client.player);
        float f = flash.get();
        flash.target(0f);
        var font = client.textRenderer;
        HudStyle.panel(ctx, 0, 0, 44, 20);
        if (f > 0.01f) {
            Draw.rect(ctx, 0, 0, 44, 20, Colors.fade(0x60FFD54A, f));
        }
        ctx.drawItem(new ItemStack(Items.TOTEM_OF_UNDYING), 2, 2);
        int c = n == 0 ? 0xFFFF5468 : 0xFFFFFFFF;
        Draw.text(ctx, font, HudStyle.bold(String.valueOf(n)), 21, 3, c, 0.9f);
        Draw.text(ctx, font, HudStyle.text(pops + " used"), 21, 12, 0xFF9AA4B4, 0.55f);
    }
}
