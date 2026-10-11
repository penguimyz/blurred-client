package dev.blurredclient.mod.module.feature;

import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.module.ModuleIds;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.ModeSetting;
import dev.blurredclient.mod.setting.SliderSetting;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;

/**
 * Move, turn and resize the items you hold in first person — with a separate
 * setup per kind of item if you want one (swords out to the side, blocks
 * small, bows centred...).
 *
 * <p>Purely where your own hands are drawn on your own screen: it changes no
 * hitbox, reach or timing, and nobody else sees it. The off hand gets the
 * mirror image, so one set of sliders keeps both hands symmetrical.
 *
 * <p>The menu shows one set of sliders at a time: pick a kind under
 * "Editing", switch on "Custom for this type", adjust. Kinds without their
 * own setup use Default.
 */
public final class ViewModelModule extends Module {
    /** The view-model transform applied to the main hand this frame (identity when none). */
    public static final org.joml.Matrix4f MAIN_TRANSFORM = new org.joml.Matrix4f();

    /** Kinds of held item that can have their own setup. */
    public enum Kind {
        DEFAULT("Default", ""),
        SWORD("Swords", "sword_"),
        TOOL("Tools", "tool_"),
        BLOCK("Blocks", "block_"),
        FOOD("Food", "food_"),
        RANGED("Bows", "ranged_");

        private final String label;
        private final String prefix;

        Kind(String label, String prefix) {
            this.label = label;
            this.prefix = prefix;
        }

        public String label() {
            return label;
        }
    }

    /** One complete transform. */
    public static final class Setup {
        final BoolSetting custom;
        final SliderSetting x;
        final SliderSetting y;
        final SliderSetting z;
        final SliderSetting scale;
        final SliderSetting rotX;
        final SliderSetting rotY;
        final SliderSetting rotZ;

        Setup(ViewModelModule m, Kind kind, ModeSetting<Kind> editing) {
            String p = kind.prefix;
            java.util.function.BooleanSupplier shown = () -> editing.get() == kind;
            if (kind == Kind.DEFAULT) {
                custom = null;
            } else {
                custom = m.add(new BoolSetting(p + "custom", "Custom for " + kind.label.toLowerCase(),
                        "Use a separate setup for " + kind.label.toLowerCase() + " instead of Default.", false));
                custom.visibleIf(shown);
            }
            java.util.function.BooleanSupplier sliders = kind == Kind.DEFAULT ? shown
                    : () -> editing.get() == kind && custom.isOn();
            x = slider(m, p + "x", "Sideways", "Positive moves the item outward.", 0, -1, 1, 0.02, "", sliders);
            y = slider(m, p + "y", "Up / down", "Positive raises the item.", 0, -1, 1, 0.02, "", sliders);
            z = slider(m, p + "z", "Forward / back", "Negative pushes the item further away.", 0, -1, 1, 0.02, "", sliders);
            scale = slider(m, p + "scale", "Size", "1 is vanilla size.", 1, 0.3, 2, 0.05, "x", sliders);
            rotX = slider(m, p + "rot_x", "Tilt", "Rotate around the side-to-side axis.", 0, -180, 180, 5, "°", sliders);
            rotY = slider(m, p + "rot_y", "Turn", "Rotate around the vertical axis.", 0, -180, 180, 5, "°", sliders);
            rotZ = slider(m, p + "rot_z", "Roll", "Rotate around the forward axis.", 0, -180, 180, 5, "°", sliders);
        }

        private static SliderSetting slider(ViewModelModule m, String key, String label, String hint,
                                            double def, double min, double max, double step, String suffix,
                                            java.util.function.BooleanSupplier visible) {
            SliderSetting s = m.add(new SliderSetting(key, label, hint, def, min, max, step, suffix));
            s.visibleIf(visible);
            return s;
        }

        public float x() { return x.floatValue(); }
        public float y() { return y.floatValue(); }
        public float z() { return z.floatValue(); }
        public float scale() { return scale.floatValue(); }
        public float rotX() { return rotX.floatValue(); }
        public float rotY() { return rotY.floatValue(); }
        public float rotZ() { return rotZ.floatValue(); }
    }

    private final ModeSetting<Kind> editing;
    private final Map<Kind, Setup> setups = new EnumMap<>(Kind.class);

    /** The stack being drawn right now (set by the held-item mixin before the transform). */
    private static ItemStack current = ItemStack.EMPTY;

    public ViewModelModule() {
        super(ModuleIds.VIEW_MODEL, "View model",
                "Move, rotate and resize held items, with a separate setup per kind of item if you like.",
                Category.VISUAL, false);
        this.editing = add(new ModeSetting<>("editing", "Editing", "Which kind of item the sliders below are for.",
                Kind.DEFAULT, Kind::label));
        for (Kind k : Kind.values()) {
            setups.put(k, new Setup(this, k, editing));
        }
    }

    public static void setCurrent(ItemStack stack) {
        current = stack == null ? ItemStack.EMPTY : stack;
    }

    /** The setup for the item currently being drawn. */
    public Setup setup() {
        Kind k = kindOf(current);
        Setup s = setups.get(k);
        return k != Kind.DEFAULT && s.custom.isOn() ? s : setups.get(Kind.DEFAULT);
    }

    static Kind kindOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return Kind.DEFAULT;
        }
        if (stack.is(ItemTags.SWORDS)) {
            return Kind.SWORD;
        }
        if (stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES)) {
            return Kind.TOOL;
        }
        if (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem
                || stack.getItem() instanceof TridentItem) {
            return Kind.RANGED;
        }
        if (stack.get(DataComponents.FOOD) != null) {
            return Kind.FOOD;
        }
        if (stack.getItem() instanceof BlockItem) {
            return Kind.BLOCK;
        }
        return Kind.DEFAULT;
    }
}
