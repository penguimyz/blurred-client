package dev.blurredclient.mod.module.hud;

import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

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
                FoodProperties food = stack.get(DataComponents.FOOD);
                if (food != null) {
                    lines.add(Component.literal("🍗 +" + food.nutrition() + " hunger   ✦ +"
                            + String.format(java.util.Locale.ROOT, "%.1f", food.saturation()) + " saturation")
                            .withStyle(ChatFormatting.GOLD));
                }
            }
        });
    }

    private static boolean shows(Minecraft client) {
        LocalPlayer p = client.player;
        return p != null && client.gameMode != null && client.gameMode.canHurtPlayer()
                && !p.isPassenger() && !client.gui.hud.isHidden();
    }

    @Override
    public Size measure(Minecraft client, boolean preview) {
        if (!preview && !shows(client)) {
            return null;
        }
        return new Size(ROW_W, ROW_H);
    }

    /** Exactly over vanilla's hunger row. */
    @Override
    public int[] screenPosition(Minecraft client, int screenW, int screenH, int w, int h) {
        return new int[] {screenW / 2 + 91 - ROW_W, screenH - 39};
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, Minecraft client, boolean preview) {
        int food = 14;
        float sat = 5.5f;
        int gain = 0;
        float satGain = 0f;
        if (!preview) {
            LocalPlayer p = client.player;
            if (p == null) {
                return;
            }
            food = p.getFoodData().getFoodLevel();
            sat = p.getFoodData().getSaturationLevel();
            FoodProperties f = held(p);
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

    private static FoodProperties held(LocalPlayer p) {
        for (ItemStack stack : new ItemStack[] {p.getMainHandItem(), p.getOffhandItem()}) {
            FoodProperties f = stack.get(DataComponents.FOOD);
            if (f != null) {
                return f;
            }
        }
        return null;
    }
}
