package dev.blurredclient.mod.module.hud;

import dev.blurredclient.mod.fx.Sfx;
import dev.blurredclient.mod.hud.HudStyle;
import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.module.feature.Ticking;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.SliderSetting;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Warns before your armour or the tool in your hand breaks: a card listing
 * each item that's low, with how much is left, and a sound the moment one
 * crosses the line.
 */
public final class DurabilityAlertModule extends HudModule implements Ticking {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final SliderSetting warnAt;
    private final BoolSetting armour;
    private final BoolSetting held;
    private final BoolSetting sound;
    /** Items already warned about, so the sound plays once per item. */
    private final Set<String> warned = new HashSet<>();

    public DurabilityAlertModule() {
        super("durability_alert", "Durability alert", "Warns when your armour or held tool is about to break.",
                Category.UTILITY, false, new HudPlacement(HudPlacement.Anchor.CENTER, 0, -40));
        this.warnAt = add(new SliderSetting("warn_at", "Warn at", "Warn when this much durability is left.", 10, 1, 50, 1, "%"));
        this.armour = add(new BoolSetting("armour", "Armour", "Warn about armour.", true));
        this.held = add(new BoolSetting("held", "Held items", "Warn about what's in your hands.", true));
        this.sound = add(new BoolSetting("sound", "Sound", "Play a sound when something gets low.", true));
    }

    private record Low(ItemStack stack, float left) {}

    private List<Low> low(Minecraft client) {
        List<Low> out = new ArrayList<>();
        if (client.player == null) {
            return out;
        }
        for (EquipmentSlot slot : SLOTS) {
            boolean hand = slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
            if (hand ? !held.isOn() : !armour.isOn()) {
                continue;
            }
            ItemStack stack = client.player.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamageableItem()) {
                continue;
            }
            float left = 1f - stack.getDamageValue() / (float) stack.getMaxDamage();
            if (left * 100f <= warnAt.floatValue()) {
                out.add(new Low(stack, left));
            }
        }
        return out;
    }

    @Override
    public void tick(Minecraft client) {
        Set<String> now = new HashSet<>();
        for (Low l : low(client)) {
            String key = l.stack().getItem().toString() + "@" + System.identityHashCode(l.stack());
            now.add(key);
            if (!warned.contains(key) && sound.isOn()) {
                Sfx.play(SoundEvents.ANVIL_LAND, 1.6f, 0.35f);
            }
        }
        warned.clear();
        warned.addAll(now);
    }

    @Override
    public Size measure(Minecraft client, boolean preview) {
        int n = preview ? 2 : low(client).size();
        if (n == 0) {
            return null;
        }
        return new Size(22 * n + 4, 30);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, Minecraft client, boolean preview) {
        List<Low> list = preview
                ? List.of(new Low(new ItemStack(Items.DIAMOND_CHESTPLATE), 0.06f), new Low(new ItemStack(Items.IRON_SWORD), 0.09f))
                : low(client);
        if (list.isEmpty()) {
            return;
        }
        var font = client.font;
        float flash = (float) (0.6 + 0.4 * Math.sin(System.nanoTime() / 1e9 * 8));
        int w = 22 * list.size() + 4;
        HudStyle.panel(ctx, 0, 0, w, 30);
        Draw.rect(ctx, 0, 0, w, 1, Colors.fade(0xFFFF5468, flash));
        int x = 5;
        for (Low l : list) {
            ctx.item(l.stack(), x, 3);
            String pct = Math.round(l.left() * 100) + "%";
            int pw = Draw.width(font, HudStyle.bold(pct), 0.6f);
            int c = l.left() < 0.05f ? Colors.fade(0xFFFF5468, flash) : 0xFFFFB347;
            Draw.text(ctx, font, HudStyle.bold(pct), x + 8 - pw / 2, 21, c, 0.6f);
            x += 22;
        }
    }
}
