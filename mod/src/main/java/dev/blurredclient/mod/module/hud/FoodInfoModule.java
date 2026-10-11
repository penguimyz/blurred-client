package dev.blurredclient.mod.module.hud;

import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Food info, like AppleSkin: your saturation drawn over the hunger bar, a
 * flashing preview of what the food in your hand would fill, and hunger and
 * saturation in food tooltips. Sits exactly on vanilla's hunger bar.
 *
 * <p>Exhaustion isn't shown: the server never tells the client it (AppleSkin
 * needs its server half for that).
 */
public final class FoodInfoModule extends HudModule {
    private static final int ROW_W = 81;
    private static final int ROW_H = 9;

    private final BoolSetting saturation;
    private final BoolSetting preview;
    private final BoolSetting tooltips;

    public FoodInfoModule() {
        super("food_info", "Food info", "Like AppleSkin: saturation on your hunger bar, what food would fill, and food values in tooltips.",
                Category.HUD, false, new HudPlacement(HudPlacement.Anchor.BOTTOM_CENTER, 50, -39));
        this.saturation = add(new BoolSetting("saturation", "Saturation", "Gold over the drumsticks: how much saturation you have.", true));
        this.preview = add(new BoolSetting("preview", "Food preview", "Holding food, the drumsticks it would fill flash.", true));
        this.tooltips = add(new BoolSetting("tooltips", "Tooltips", "Hunger and saturation in food tooltips.", true));
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (isActive() && tooltips.isOn()) {
                FoodComponent food = stack.get(DataComponentTypes.FOOD);
                if (food != null) {
                    lines.add(Text.literal("🍗 +" + food.nutrition() + " hunger   ✦ +"
                            + String.format(java.util.Locale.ROOT, "%.1f", food.saturation()) + " saturation")
                            .formatted(Formatting.GOLD));
                }
            }
        });
    }

    private static boolean shows(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        return p != null && client.interactionManager != null && client.interactionManager.hasStatusBars()
                && !p.hasVehicle() && !client.options.hudHidden;
    }

    @Override
    public Size measure(MinecraftClient client, boolean preview) {
        if (!preview && !shows(client)) {
            return null;
        }
        return new Size(ROW_W, ROW_H);
    }

    /** Exactly over vanilla's hunger row. */
    @Override
    public int[] screenPosition(MinecraftClient client, int screenW, int screenH, int w, int h) {
        return new int[] {screenW / 2 + 91 - ROW_W, screenH - 39};
    }

    @Override
    public void render(DrawContext ctx, MinecraftClient client, boolean preview) {
        int food = 14;
        float sat = 5.5f;
        int gain = 0;
        float satGain = 0f;
        if (!preview) {
            ClientPlayerEntity p = client.player;
            if (p == null) {
                return;
            }
            food = p.getHungerManager().getFoodLevel();
            sat = p.getHungerManager().getSaturationLevel();
            FoodComponent f = held(p);
            if (f != null) {
                gain = f.nutrition();
                satGain = f.saturation();
            }
        } else {
            gain = 4;
            satGain = 2.4f;
        }
        // Icons go right to left: icon i covers food points 2i+1 and 2i+2.
        if (saturation.isOn()) {
            for (int i = 0; i < 10; i++) {
                float s = Math.max(0f, Math.min(2f, sat - i * 2));
                if (s <= 0f) {
                    continue;
                }
                int x = ROW_W - 9 - i * 8;
                // A gold rim around the drumstick, filled in proportion.
                int gold = 0xFFFFC233;
                Draw.rect(ctx, x, 0, x + 9, 1, gold);
                Draw.rect(ctx, x, 8, x + 9, 9, gold);
                Draw.rect(ctx, x, 0, x + 1, 9, gold);
                Draw.rect(ctx, x + 8, 0, x + 9, 9, gold);
                Draw.rect(ctx, x + 1, 9 - 8 * s / 2f, x + 8, 8, Colors.fade(gold, 0.35f));
            }
        }
        if (this.preview.isOn() && gain > 0) {
            float pulse = 0.35f + 0.35f * (float) Math.sin(System.nanoTime() / 1e9 * 6);
            int after = Math.min(20, food + gain);
            for (int point = food + 1; point <= after; point++) {
                int i = (point - 1) / 2;
                boolean half = point % 2 == 1;
                int x = ROW_W - 9 - i * 8;
                // Light up the half (or whole) drumstick this food would fill.
                Draw.rect(ctx, half ? x + 4 : x, 1, x + 8, 8, Colors.fade(0xFFFFFFFF, pulse));
            }
            float satAfter = Math.min(after, sat + satGain);
            for (int i = 0; i < 10; i++) {
                float now = Math.max(0f, Math.min(2f, sat - i * 2));
                float then = Math.max(0f, Math.min(2f, satAfter - i * 2));
                if (then > now) {
                    int x = ROW_W - 9 - i * 8;
                    Draw.rect(ctx, x + 1, 9 - 8 * then / 2f, x + 8, 9 - 8 * now / 2f, Colors.fade(0xFFFFC233, pulse));
                }
            }
        }
    }

    private static FoodComponent held(ClientPlayerEntity p) {
        for (ItemStack stack : new ItemStack[] {p.getMainHandStack(), p.getOffHandStack()}) {
            FoodComponent f = stack.get(DataComponentTypes.FOOD);
            if (f != null) {
                return f;
            }
        }
        return null;
    }
}
