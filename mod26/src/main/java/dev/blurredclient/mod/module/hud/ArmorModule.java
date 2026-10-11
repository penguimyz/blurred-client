package dev.blurredclient.mod.module.hud;

import dev.blurredclient.mod.hud.HudStyle;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.module.ModuleIds;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.ModeSetting;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Your armour and held item, with how much durability each has left.
 *
 * <p>Uses {@code getEquippedStack} rather than the inventory's armour list:
 * 1.21.5 moved armour out of the inventory into equipment slots, and the
 * equipped-stack accessor is the one that works on every supported version.
 */
public final class ArmorModule extends HudModule {
    public enum Layout {
        VERTICAL("Vertical"), HORIZONTAL("Horizontal");

        private final String label;

        Layout(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final ModeSetting<Layout> layout;
    private final BoolSetting held;
    private final BoolSetting percent;

    public ArmorModule() {
        super(ModuleIds.ARMOR, "Armor status", "Your armour and held item with durability left.",
                false, new HudPlacement(HudPlacement.Anchor.MIDDLE_RIGHT, -4, 0));
        this.layout = add(new ModeSetting<>("layout", "Layout", "A column or a row.", Layout.VERTICAL, Layout::label));
        this.held = add(new BoolSetting("held", "Held item", "Include the item in your main hand.", true));
        this.percent = add(new BoolSetting("percent", "As percent", "Durability as a percentage instead of uses left.", false));
    }

    private List<ItemStack> stacks(Minecraft client, boolean preview) {
        List<ItemStack> out = new ArrayList<>();
        if (preview || client.player == null) {
            out.add(new ItemStack(Items.DIAMOND_HELMET));
            out.add(new ItemStack(Items.DIAMOND_CHESTPLATE));
            out.add(new ItemStack(Items.DIAMOND_LEGGINGS));
            out.add(new ItemStack(Items.DIAMOND_BOOTS));
            if (held.isOn()) {
                out.add(new ItemStack(Items.DIAMOND_SWORD));
            }
            return out;
        }
        for (EquipmentSlot slot : SLOTS) {
            ItemStack s = client.player.getItemBySlot(slot);
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        if (held.isOn() && !client.player.getMainHandItem().isEmpty()) {
            out.add(client.player.getMainHandItem());
        }
        return out;
    }

    private String label(ItemStack s) {
        if (!s.isDamageableItem()) {
            return s.getCount() > 1 ? String.valueOf(s.getCount()) : "";
        }
        int left = s.getMaxDamage() - s.getDamageValue();
        return percent.isOn() ? Math.round(100f * left / s.getMaxDamage()) + "%" : String.valueOf(left);
    }

    private static int durabilityColour(ItemStack s) {
        if (!s.isDamageableItem()) {
            return 0xFFFFFFFF;
        }
        float f = 1f - s.getDamageValue() / (float) s.getMaxDamage();
        return Colors.lerp(0xFFFF5468, 0xFF3BD87A, f);
    }

    @Override
    public Size measure(Minecraft client, boolean preview) {
        List<ItemStack> stacks = stacks(client, preview);
        if (stacks.isEmpty()) {
            return null;
        }
        if (layout.get() == Layout.VERTICAL) {
            int widest = 0;
            for (ItemStack s : stacks) {
                widest = Math.max(widest, Draw.width(client.font, HudStyle.text(label(s)), 0.8f));
            }
            return new Size(20 + widest + 4, stacks.size() * 17);
        }
        return new Size(stacks.size() * 22, 26);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, Minecraft client, boolean preview) {
        List<ItemStack> stacks = stacks(client, preview);
        var font = client.font;
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack s = stacks.get(i);
            int x = layout.get() == Layout.VERTICAL ? 0 : i * 22;
            int y = layout.get() == Layout.VERTICAL ? i * 17 : 0;
            ctx.item(s, x, y);
            String l = label(s);
            if (l.isEmpty()) {
                continue;
            }
            if (layout.get() == Layout.VERTICAL) {
                Draw.text(ctx, font, HudStyle.text(l), x + 20, y + 5, durabilityColour(s), 0.8f);
            } else {
                int tw = Draw.width(font, HudStyle.text(l), 0.7f);
                Draw.text(ctx, font, HudStyle.text(l), x + 8 - tw / 2, y + 18, durabilityColour(s), 0.7f);
            }
        }
    }
}
