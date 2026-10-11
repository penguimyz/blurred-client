package dev.blurredclient.mod.module.hud;

import dev.blurredclient.mod.fx.ScreenProject;
import dev.blurredclient.mod.hud.HudStyle;
import dev.blurredclient.mod.mixin.InteractionManagerAccessor;
import dev.blurredclient.mod.module.HudPlacement;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.ModeSetting;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import dev.blurredclient.mod.ui.kit.Anim;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * What you're looking at, Jade/WAILA-style, in a card beside the block.
 *
 * <p>Shows the block's item (blocks render as their 3D model, as in the
 * inventory), its name, which mod it's from, the tool it needs and whether
 * yours can harvest it, your breaking progress, and growth, power or honey
 * level where the block has them. Mobs show name and health; other players
 * show their name only.
 *
 * <p>Only reads what vanilla's crosshair raycast already found, within
 * reach. It never sees through walls or into containers.
 *
 * <p>"Beside the block" puts the card just right of the block's centre on
 * screen and glides it along as you look around; "Fixed on HUD" uses the HUD
 * editor position like any other module.
 */
public final class BlockInfoModule extends HudModule {
    public enum Mode {
        BESIDE("Beside the block"), HUD("Fixed on HUD");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final int PAD = 5;
    private static final int ICON = 16;

    private final ModeSetting<Mode> mode;
    private final BoolSetting showMod;
    private final BoolSetting showTool;
    private final BoolSetting entities;
    private final Anim fade = new Anim(0f, 11f);
    // Everything that moves eases over real time, so it's equally smooth at any FPS.
    private final Anim posX = new Anim(0f, 16f);
    private final Anim posY = new Anim(0f, 16f);
    private final Anim cardW = new Anim(0f, 18f);
    private final Anim cardH = new Anim(0f, 18f);
    private final Anim swap = new Anim(1f, 14f);
    private final Anim progress = new Anim(0f, 20f);
    private final List<Anim> gaugeFill = new ArrayList<>();
    private boolean placed;
    private float fracX;
    private float fracY;
    /** The last thing looked at, kept so the card can fade out instead of vanishing. */
    private Info shown;
    private Info cached;
    private long cachedFrame = -1;

    public BlockInfoModule() {
        super("block_info", "Block info", "What you're looking at (name, mod, tool, growth) in a card beside the block.",
                false, new HudPlacement(HudPlacement.Anchor.TOP_CENTER, 0, 6));
        this.mode = add(new ModeSetting<>("mode", "Position", "Beside the block you're looking at, or fixed on the HUD.",
                Mode.BESIDE, Mode::label));
        this.showMod = add(new BoolSetting("mod", "Show mod name", "Which mod (or Minecraft) it comes from.", true));
        this.showTool = add(new BoolSetting("tool", "Show tool", "The tool it needs and whether yours can harvest it.", true));
        this.entities = add(new BoolSetting("entities", "Mobs too", "Also show mobs and players you look at.", true));
    }

    /** A labelled meter: growth 3/7, power 12/15, health 14/20. */
    private record Gauge(String label, float value, float max, int colour) {}

    /** The tool chip: what's needed and whether you can harvest it with what you hold. */
    private record Tool(String name, boolean ok) {}

    private record Info(ItemStack icon, String title, String source, Tool tool, List<Gauge> gauges, float progress,
                        Vec3d anchor) {}

    private Info info(MinecraftClient client, boolean preview) {
        if (preview) {
            return new Info(new ItemStack(Items.WHEAT), "Wheat Crops", "Minecraft", new Tool("Hoe", true),
                    List.of(new Gauge("Growth", 5, 7, 0xFF8BD450)), 0.6f, null);
        }
        HitResult hit = client.crosshairTarget;
        if (client.world == null || client.player == null || hit == null) {
            return null;
        }
        if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = bh.getBlockPos();
            BlockState state = client.world.getBlockState(pos);
            if (state.isAir()) {
                return null;
            }
            Identifier id = Registries.BLOCK.getId(state.getBlock());
            Tool tool = null;
            if (showTool.isOn()) {
                String name = toolFor(state);
                if (name != null || state.isToolRequired()) {
                    tool = new Tool(name == null ? "Tool" : name, client.player.canHarvest(state));
                }
            }
            List<Gauge> gauges = new ArrayList<>();
            for (Property<?> p : state.getProperties()) {
                String n = p.getName();
                if (n.equals("age") || n.equals("power") || n.equals("level") || n.equals("honey_level")) {
                    Object v = state.get(p);
                    Object m = max(p.getValues());
                    if (v instanceof Integer vi && m instanceof Integer mi) {
                        gauges.add(new Gauge(label(n), vi, mi, gaugeColour(n)));
                    }
                }
            }
            float progress = 0f;
            if (client.interactionManager instanceof InteractionManagerAccessor acc && acc.blurred$isBreaking()) {
                progress = acc.blurred$breakingProgress();
            }
            return new Info(new ItemStack(state.getBlock().asItem()), state.getBlock().getName().getString(),
                    showMod.isOn() ? modName(id.getNamespace()) : null, tool, gauges, progress, Vec3d.ofCenter(pos));
        }
        if (entities.isOn() && hit instanceof EntityHitResult eh) {
            Entity e = eh.getEntity();
            List<Gauge> gauges = new ArrayList<>();
            // Mob health only: other players' health is left out on purpose.
            if (e instanceof LivingEntity le && !(e instanceof PlayerEntity)) {
                gauges.add(new Gauge("Health", le.getHealth(), le.getMaxHealth(), 0xFFFF5468));
            }
            String source = showMod.isOn() ? modName(Registries.ENTITY_TYPE.getId(e.getType()).getNamespace()) : null;
            return new Info(ItemStack.EMPTY, e.getDisplayName().getString(), source, null, gauges, 0f,
                    e.getBoundingBox().getCenter());
        }
        return null;
    }

    private static int gaugeColour(String property) {
        return switch (property) {
            case "age" -> 0xFF8BD450;
            case "power" -> 0xFFFF4A4A;
            case "honey_level" -> 0xFFFFC233;
            default -> 0xFF55CDF2;
        };
    }

    private static String label(String property) {
        return switch (property) {
            case "age" -> "Growth";
            case "power" -> "Power";
            case "honey_level" -> "Honey";
            default -> "Level";
        };
    }

    private static Object max(Collection<?> values) {
        Object last = null;
        for (Object v : values) {
            last = v;
        }
        return last;
    }

    private static String toolFor(BlockState state) {
        if (state.isIn(BlockTags.PICKAXE_MINEABLE)) {
            return "Pickaxe";
        }
        if (state.isIn(BlockTags.AXE_MINEABLE)) {
            return "Axe";
        }
        if (state.isIn(BlockTags.SHOVEL_MINEABLE)) {
            return "Shovel";
        }
        if (state.isIn(BlockTags.HOE_MINEABLE)) {
            return "Hoe";
        }
        return null;
    }

    private static String modName(String namespace) {
        if (namespace.equals("minecraft")) {
            return "Minecraft";
        }
        return FabricLoader.getInstance().getModContainer(namespace)
                .map(m -> m.getMetadata().getName()).orElse(namespace);
    }

    /** This frame's info, computed once per frame (measure, position and render all ask). */
    private Info current(MinecraftClient client, boolean preview) {
        long frame = dev.blurredclient.mod.hud.HudRenderer.frame();
        if (preview || frame != cachedFrame) {
            cachedFrame = frame;
            cached = info(client, preview);
        }
        return cached;
    }

    private static final int W_MIN = 120;
    private static final int TILE = 26;

    /** What to show: what's looked at now, or (fading out) what was. */
    private Info display(MinecraftClient client, boolean preview) {
        Info info = current(client, preview);
        if (preview) {
            return info;
        }
        if (info != null) {
            if (shown == null || !shown.title().equals(info.title())) {
                // A different thing: cross-fade the contents and restart the bars.
                if (shown != null) {
                    swap.snap(0.35f);
                }
                swap.target(1f);
                gaugeFill.clear();
            }
            shown = info;
        }
        float f = fade.follow(info != null);
        if (info == null && f < 0.02f) {
            shown = null;
            placed = false;
        }
        return shown;
    }

    @Override
    public Size measure(MinecraftClient client, boolean preview) {
        Info info = display(client, preview);
        if (info == null) {
            return null;
        }
        Size full = fullSize(client, info);
        if (preview) {
            return full;
        }
        if (cardW.get() < 1f) {
            cardW.snap(full.width());
            cardH.snap(full.height());
        }
        cardW.target(full.width());
        cardH.target(full.height());
        return new Size(Math.round(cardW.get()), Math.round(cardH.get()));
    }

    private Size fullSize(MinecraftClient client, Info info) {
        var font = client.textRenderer;
        int text = Draw.width(font, HudStyle.bold(info.title()), 1f);
        if (info.source() != null) {
            text = Math.max(text, Draw.width(font, HudStyle.text(info.source()), 0.7f) + 8);
        }
        int w = Math.max(W_MIN, PAD * 2 + TILE + 7 + text + 4);
        int h = PAD * 2 + TILE;
        if (info.tool() != null || !info.gauges().isEmpty()) {
            h += 5;
        }
        if (info.tool() != null) {
            h += 13;
        }
        h += info.gauges().size() * 14;
        if (info.progress() > 0) {
            h += 4;
        }
        return new Size(w, h);
    }

    @Override
    public int[] screenPosition(MinecraftClient client, int screenW, int screenH, int w, int h) {
        Info info = current(client, false);
        if (mode.get() != Mode.BESIDE || info == null || info.anchor() == null) {
            return null;
        }
        var camera = client.gameRenderer.getCamera();
        Vec3d beside = info.anchor().add(ScreenProject.right(camera).multiply(0.65));
        double[] p = ScreenProject.project(beside, screenW, screenH);
        if (p == null) {
            return null;
        }
        float tx = (float) Math.max(4, Math.min(screenW - w - 4, p[0]));
        float ty = (float) Math.max(4, Math.min(screenH - h - 4, p[1] - h / 2.0));
        // Glide toward the target instead of jumping between blocks.
        if (!placed || Math.abs(tx - posX.get()) > screenW / 2f) {
            posX.snap(tx);
            posY.snap(ty);
            placed = true;
        }
        posX.target(tx);
        posY.target(ty);
        float x = posX.get();
        float y = posY.get();
        // Whole pixels here; the rest is drawn as an offset, so it moves smoothly.
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        fracX = x - ix;
        fracY = y - iy;
        return new int[] {ix, iy};
    }

    @Override
    public void render(DrawContext ctx, MinecraftClient client, boolean preview) {
        Info info = display(client, preview);
        Size size = measure(client, preview);
        if (info == null || size == null) {
            return;
        }
        float in = preview ? 1f : fade.get();
        if (in <= 0.01f) {
            return;
        }
        int w = size.width();
        int h = size.height();
        boolean beside = !preview && mode.get() == Mode.BESIDE;
        // Scale in from slightly smaller as it appears, out as it goes.
        float pop = 0.92f + 0.08f * in;
        Draw.push(ctx);
        Draw.translate(ctx, (beside ? fracX : 0f) + w * (1 - pop) / 2f, (beside ? fracY : 0f) + h * (1 - pop) / 2f);
        try {
            float alpha = in;
            float content = preview ? 1f : swap.get();
            Draw.scaled(ctx, 0, 0, pop, () -> {
                Draw.scissor(ctx, 0, 0, w, h);
                try {
                    card(ctx, client, info, w, h, alpha, content);
                } finally {
                    Draw.unscissor(ctx);
                }
            });
        } finally {
            Draw.pop(ctx);
        }
    }

    private void card(DrawContext ctx, MinecraftClient client, Info info, int w, int h, float a, float content) {
        var font = client.textRenderer;
        int accent = HudStyle.accent();

        // Card: dark body, a lighter header band, an accent hairline on top.
        Draw.rect(ctx, 0, 0, w, h, Colors.fade(0xE00D0F15, a));
        Draw.rect(ctx, 0, 0, w, PAD * 2 + TILE, Colors.fade(0x14FFFFFF, a));
        Draw.rect(ctx, 0, 0, w, 1, Colors.fade(accent, a));

        // Icon tile, tinted with the accent, the item drawn large in it.
        int tx = PAD;
        int ty = PAD;
        Draw.rect(ctx, tx, ty, tx + TILE, ty + TILE, Colors.fade((0x30 << 24) | (accent & 0xFFFFFF), a));
        Draw.rect(ctx, tx, ty + TILE - 1, tx + TILE, ty + TILE, Colors.fade(accent, a * 0.8f));
        if (!info.icon().isEmpty()) {
            Draw.scaled(ctx, tx + 3, ty + 3, 1.25f, () -> ctx.drawItem(info.icon(), 0, 0));
        } else {
            String letter = info.title().isEmpty() ? "?" : info.title().substring(0, 1).toUpperCase();
            int lw = Draw.width(font, HudStyle.bold(letter), 1.4f);
            Draw.text(ctx, font, HudStyle.bold(letter), tx + (TILE - lw) / 2, ty + 7, Colors.fade(0xFFFFFFFF, a), 1.4f);
        }

        a = a * content;
        int x = tx + TILE + 7;
        Draw.text(ctx, font, HudStyle.bold(info.title()), x, ty + 3, Colors.fade(0xFFFFFFFF, a), 1f);
        if (info.source() != null) {
            Draw.rect(ctx, x, ty + 17, x + 3, ty + 20, Colors.fade(accent, a));
            Draw.text(ctx, font, HudStyle.text(info.source()), x + 6, ty + 15, Colors.fade(0xFF9AA4B4, a), 0.7f);
        }

        int y = PAD * 2 + TILE + 5;
        if (info.tool() != null) {
            Tool t = info.tool();
            int c = t.ok() ? 0xFF3BD87A : 0xFFFF5468;
            String label = t.name() + (t.ok() ? "  can harvest" : "  can't harvest");
            int cw = Draw.width(font, HudStyle.text(label), 0.7f) + 12;
            Draw.rect(ctx, PAD, y, PAD + cw, y + 10, Colors.fade((0x2A << 24) | (c & 0xFFFFFF), a));
            Draw.rect(ctx, PAD, y, PAD + 2, y + 10, Colors.fade(c, a));
            Draw.text(ctx, font, HudStyle.text(label), PAD + 7, y + 2, Colors.fade(c, a), 0.7f);
            y += 13;
        }
        int gi = 0;
        for (Gauge g : info.gauges()) {
            String left = g.label();
            String right = g.value() != Math.floor(g.value())
                    ? String.format("%.1f / %.0f", g.value(), g.max())
                    : String.format("%.0f / %.0f", g.value(), g.max());
            Draw.text(ctx, font, HudStyle.text(left), PAD, y, Colors.fade(0xFFB4B8C4, a), 0.7f);
            int rw = Draw.width(font, HudStyle.text(right), 0.7f);
            Draw.text(ctx, font, HudStyle.text(right), w - PAD - rw, y, Colors.fade(0xFFFFFFFF, a), 0.7f);
            float target = g.max() <= 0 ? 0f : Math.max(0f, Math.min(1f, g.value() / g.max()));
            while (gaugeFill.size() <= gi) {
                gaugeFill.add(new Anim(0f, 12f));
            }
            Anim fill = gaugeFill.get(gi++);
            fill.target(target);
            float f = fill.get();
            Draw.rect(ctx, PAD, y + 8, w - PAD, y + 10, Colors.fade(0x26FFFFFF, a));
            Draw.rect(ctx, PAD, y + 8, PAD + (w - PAD * 2) * f, y + 10, Colors.fade(g.colour(), a));
            y += 14;
        }

        // Breaking progress runs along the whole bottom edge.
        progress.target(info.progress());
        float pr = progress.get();
        if (pr > 0.005f) {
            Draw.rect(ctx, 0, h - 2, w, h, Colors.fade(0x26FFFFFF, a));
            Draw.rect(ctx, 0, h - 2, w * pr, h, Colors.fade(accent, a));
        }
    }
}
