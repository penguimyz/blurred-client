package dev.blurredclient.mod.ui.kit;

import net.minecraft.client.gui.DrawContext;

/**
 * Real liquid glass: rounded slabs that bend the world behind them.
 *
 * <p>Just before the menu layer is drawn each frame (and before vanilla's
 * background blur), the frame so far, which is the world, is copied into a
 * texture. Glass slabs are drawn with Blurred's own shader
 * ({@code shaders/core/liquid_glass}), which samples that copy through a
 * rounded-rectangle lens: bending strongest at the rim, a little colour
 * fringing, a faint frost and a specular edge.
 *
 * <p>1.21.11 only (its GPU and GUI APIs, javap-checked); elsewhere, and when
 * a menu is drawn somewhere other than the screen (the 3D menu),
 * {@link #available()} is false and {@link LiquidSkin} draws its flat look.
 */
public final class LiquidGlass {
    private LiquidGlass() {}

    /** Set while a menu is drawn onto something other than the screen. */
    private static int suppressed;
    private static boolean wanted;
    /** A menu marked its background/content split this frame; the copy is taken there (see {@link #markBackgroundDone}). */
    private static boolean pending;

    public static void suppress(boolean on) {
        suppressed += on ? 1 : -1;
    }

    public static boolean available() {
        //? if >=1.21.11 {
        // Out of a world, the menu's own background is part of the menu layer: nothing to see through.
        // In a world the world is behind the menu; out of one, only a menu that
        // marked where its background ends has something to see through.
        return suppressed <= 0 && (pending || net.minecraft.client.MinecraftClient.getInstance().world != null);
        //?} else {
        /*return false;
        *///?}
    }

    /**
     * A glass slab at {@code x, y} (GUI units, current transform), {@code r}
     * corner radius, tinted by {@code tint}'s RGB (white for clear glass).
     */
    public static void draw(DrawContext ctx, int x, int y, int w, int h, int r, int tint) {
        //? if >=1.21.11 {
        if (w <= 0 || h <= 0) {
            return;
        }
        var view = view();
        if (view == null) {
            return;
        }
        wanted = true;
        org.joml.Matrix3x2f pose = new org.joml.Matrix3x2f(ctx.getMatrices());
        net.minecraft.client.gui.ScreenRect scissor = ctx.scissorStack.peekLast();
        org.joml.Vector2f a = pose.transformPosition(x, y, new org.joml.Vector2f());
        org.joml.Vector2f b = pose.transformPosition(x + w, y + h, new org.joml.Vector2f());
        int left = (int) Math.floor(Math.min(a.x, b.x));
        int top = (int) Math.floor(Math.min(a.y, b.y));
        net.minecraft.client.gui.ScreenRect bounds = new net.minecraft.client.gui.ScreenRect(left, top,
                Math.max(1, (int) Math.ceil(Math.max(a.x, b.x)) - left), Math.max(1, (int) Math.ceil(Math.max(a.y, b.y)) - top));
        if (scissor != null) {
            bounds = scissor.intersection(bounds);
            if (bounds == null) {
                return;
            }
        }
        int radius = Math.max(0, Math.min(255, Math.round(r / 64f * 255f)));
        int color = (radius << 24) | (tint & 0xFFFFFF);
        float[] splash = Ripples.latest(x, y, w, h);
        ctx.state.addSimpleElement(new Slab(x, y, w, h, color, splash, pose,
                net.minecraft.client.texture.TextureSetup.of(view, sampler()), scissor, bounds));
        //?}
    }

    //? if >=1.21.11 {
    /**
     * Position; UV0 = position in the slab; Color = tint and radius; UV1 =
     * the latest splash's centre in the slab (quarter units); UV2 = slab
     * size (quarter units); Normal = (t, 1 - t, 0) for a splash t of the way
     * through, (0, 0, 1) for none.
     */
    private static final com.mojang.blaze3d.vertex.VertexFormat FORMAT = com.mojang.blaze3d.vertex.VertexFormat.builder()
            .add("Position", com.mojang.blaze3d.vertex.VertexFormatElement.POSITION)
            .add("UV0", com.mojang.blaze3d.vertex.VertexFormatElement.UV0)
            .add("Color", com.mojang.blaze3d.vertex.VertexFormatElement.COLOR)
            .add("UV1", com.mojang.blaze3d.vertex.VertexFormatElement.UV1)
            .add("UV2", com.mojang.blaze3d.vertex.VertexFormatElement.UV2)
            .add("Normal", com.mojang.blaze3d.vertex.VertexFormatElement.NORMAL)
            .padding(1)
            .build();

    private static final com.mojang.blaze3d.pipeline.RenderPipeline PIPELINE = com.mojang.blaze3d.pipeline.RenderPipeline
            .builder(net.minecraft.client.gl.RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
            .withLocation(net.minecraft.util.Identifier.of("blurred", "pipeline/liquid_glass"))
            .withVertexShader(net.minecraft.util.Identifier.of("blurred", "core/liquid_glass"))
            .withFragmentShader(net.minecraft.util.Identifier.of("blurred", "core/liquid_glass"))
            .withSampler("Sampler0")
            .withBlend(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT)
            .withVertexFormat(FORMAT,
                    com.mojang.blaze3d.vertex.VertexFormat.DrawMode.QUADS)
            .withDepthTestFunction(com.mojang.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST)
            .build();

    private static com.mojang.blaze3d.textures.GpuTexture copy;
    private static com.mojang.blaze3d.textures.GpuTextureView copyView;
    private static net.minecraft.client.gl.GpuSampler sampler;

    private static net.minecraft.client.gl.GpuSampler sampler() {
        if (sampler == null) {
            sampler = com.mojang.blaze3d.systems.RenderSystem.getDevice().createSampler(
                    com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE, com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,
                    com.mojang.blaze3d.textures.FilterMode.LINEAR, com.mojang.blaze3d.textures.FilterMode.LINEAR,
                    1, java.util.OptionalDouble.empty());
        }
        return sampler;
    }

    /** The texture the world is copied into, matching the window's size. */
    private static com.mojang.blaze3d.textures.GpuTextureView view() {
        var fb = net.minecraft.client.MinecraftClient.getInstance().getFramebuffer();
        int w = fb.textureWidth;
        int h = fb.textureHeight;
        if (copy == null || copy.isClosed() || copy.getWidth(0) != w || copy.getHeight(0) != h) {
            if (copyView != null) {
                copyView.close();
            }
            if (copy != null) {
                copy.close();
            }
            var device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
            copy = device.createTexture(() -> "Blurred liquid glass", com.mojang.blaze3d.textures.GpuTexture.USAGE_COPY_DST
                    | com.mojang.blaze3d.textures.GpuTexture.USAGE_TEXTURE_BINDING,
                    com.mojang.blaze3d.textures.TextureFormat.RGBA8, w, h, 1, 1);
            copyView = device.createTextureView(copy);
        }
        return copyView;
    }
    //?}

    /**
     * A menu has drawn its background (or none, in a world): the copy for
     * this frame's glass is taken here, at vanilla's blur point, so the
     * glass sees the background but not the menu. Vanilla's blur is
     * skipped for that frame (see {@link #takeBlur}).
     */
    public static void markBackgroundDone(DrawContext ctx) {
        //? if >=1.21.11 {
        if (suppressed > 0) {
            return;
        }
        pending = true;
        ctx.applyBlur();
        //?}
    }

    /**
     * At vanilla's blur point: if a menu marked it for glass, copy the frame
     * so far instead of blurring. True if the blur should be skipped.
     */
    public static boolean takeBlur() {
        //? if >=1.21.11 {
        if (!pending) {
            return false;
        }
        pending = false;
        copyNow();
        return true;
        //?} else {
        /*return false;
        *///?}
    }

    /** Called just before the menu layer is drawn: copy the world for any glass this frame. */
    public static void beforeGui() {
        //? if >=1.21.11 {
        // The menu's own split point will take the copy; nothing to do yet.
        if (pending || !wanted) {
            return;
        }
        copyNow();
        //?}
    }

    private static void copyNow() {
        //? if >=1.21.11 {
        if (copy == null || copy.isClosed()) {
            return;
        }
        wanted = false;
        var fb = net.minecraft.client.MinecraftClient.getInstance().getFramebuffer();
        var src = fb.getColorAttachment();
        if (src == null || src.getWidth(0) != copy.getWidth(0) || src.getHeight(0) != copy.getHeight(0)) {
            return;
        }
        com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder()
                .copyTextureToTexture(src, copy, 0, 0, 0, 0, 0, copy.getWidth(0), copy.getHeight(0));
        //?}
    }

    //? if >=1.21.11 {
    /** One slab, as a GUI element on Blurred's glass pipeline. */
    private record Slab(int x, int y, int w, int h, int color, float[] splash, org.joml.Matrix3x2f pose,
                        net.minecraft.client.texture.TextureSetup textureSetup,
                        net.minecraft.client.gui.ScreenRect scissorArea,
                        net.minecraft.client.gui.ScreenRect bounds)
            implements net.minecraft.client.gui.render.state.SimpleGuiElementRenderState {

        @Override
        public com.mojang.blaze3d.pipeline.RenderPipeline pipeline() {
            return PIPELINE;
        }

        @Override
        public void setupVertices(net.minecraft.client.render.VertexConsumer vc) {
            int sw = Math.min(32767, w * 4);
            int sh = Math.min(32767, h * 4);
            int cx = splash == null ? 0 : Math.round(splash[0] * 4);
            int cy = splash == null ? 0 : Math.round(splash[1] * 4);
            float age = splash == null ? -1f : splash[2];
            corner(vc, x, y, 0, 0, cx, cy, sw, sh, age);
            corner(vc, x, y + h, 0, h, cx, cy, sw, sh, age);
            corner(vc, x + w, y + h, w, h, cx, cy, sw, sh, age);
            corner(vc, x + w, y, w, 0, cx, cy, sw, sh, age);
        }

        private void corner(net.minecraft.client.render.VertexConsumer vc, float px, float py, float u, float v,
                            int cx, int cy, int sw, int sh, float age) {
            // The splash's progress as a ratio of two components, which survives the
            // normal being normalised; z set means no splash.
            float nx = age < 0 ? 0f : age;
            float ny = age < 0 ? 0f : 1f - age;
            float nz = age < 0 ? 1f : 0f;
            vc.vertex(pose, px, py).texture(u, v).color(color).overlay(cx, cy).light(sw, sh).normal(nx, ny, nz);
        }
    }
    //?}
}
