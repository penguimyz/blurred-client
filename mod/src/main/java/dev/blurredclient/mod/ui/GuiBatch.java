package dev.blurredclient.mod.ui;

import net.minecraft.client.gui.DrawContext;

/**
 * Packs many quads into one GUI element.
 *
 * <p>From 1.21.6 Minecraft collects every {@code fill} and texture draw as a
 * separate element, and for each new element checks it against the ones
 * already there — quadratic in the element count. Blurred's anti-aliased
 * shapes are made of many thin spans and its text of one quad per glyph, so
 * a full menu meant thousands of elements and single-digit frame rates (seen
 * in the in-game profiler: 93% of the frame in {@code anyIntersect}).
 *
 * <p>Inside {@link #shape} or {@link #text}, quads are collected and handed
 * over as one element. On 1.21.1/1.21.4 the old immediate renderer has no
 * such cost, so there everything goes straight to {@code DrawContext}.
 */
public final class GuiBatch {
    private GuiBatch() {}

    //? if >=1.21.8 {
    private static Batch active;

    /** Mutable quad list: per quad x1, y1, x2, y2, then colour/uv data as needed. */
    private static final class Batch {
        final DrawContext ctx;
        final boolean textured;
        float[] q = new float[256];
        int n;

        Batch(DrawContext ctx, boolean textured) {
            this.ctx = ctx;
            this.textured = textured;
        }

        void add(float... values) {
            if (n + values.length > q.length) {
                q = java.util.Arrays.copyOf(q, Math.max(q.length * 2, n + values.length));
            }
            System.arraycopy(values, 0, q, n, values.length);
            n += values.length;
        }
    }
    //?}

    /** Run {@code body}, submitting every solid fill it makes as one element. */
    public static void shape(DrawContext ctx, Runnable body) {
        //? if >=1.21.8 {
        if (active != null) {
            body.run();
            return;
        }
        Batch b = new Batch(ctx, false);
        active = b;
        try {
            body.run();
        } finally {
            active = null;
        }
        if (b.n > 0) {
            submit(b, null, null);
        }
        //?} else {
        /*body.run();
        *///?}
    }

    /**
     * A solid quad, top colour {@code c1} and bottom {@code c2}: added to the
     * current shape batch, or drawn directly when there isn't one.
     */
    public static void fill(DrawContext ctx, float x1, float y1, float x2, float y2, int c1, int c2) {
        //? if >=1.21.8 {
        if (active != null && !active.textured) {
            active.add(x1, y1, x2, y2, Float.intBitsToFloat(c1), Float.intBitsToFloat(c2));
            return;
        }
        //?}
        if (c1 == c2) {
            ctx.fill(Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y2), c1);
        } else {
            ctx.fillGradient(Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y2), c1, c2);
        }
    }

    //? if >=1.21.8 {
    /** Start collecting textured quads (all from {@code texture}) as one element. */
    public static Object beginText(DrawContext ctx) {
        if (active != null) {
            return null;
        }
        active = new Batch(ctx, true);
        return active;
    }

    public static boolean glyph(float x1, float y1, float x2, float y2, float u1, float v1, float u2, float v2, int argb) {
        if (active == null || !active.textured) {
            return false;
        }
        active.add(x1, y1, x2, y2, u1, v1, u2, v2, Float.intBitsToFloat(argb));
        return true;
    }

    public static void endText(Object token, net.minecraft.client.texture.AbstractTexture texture,
                               net.minecraft.util.Identifier textureId) {
        if (token == null || token != active) {
            return;
        }
        Batch b = active;
        active = null;
        if (b.n > 0) {
            submit(b, texture, textureId);
        }
    }

    private static void submit(Batch b, net.minecraft.client.texture.AbstractTexture texture,
                               net.minecraft.util.Identifier textureId) {
        DrawContext ctx = b.ctx;
        org.joml.Matrix3x2f pose = new org.joml.Matrix3x2f(ctx.getMatrices());
        net.minecraft.client.gui.ScreenRect scissor = ctx.scissorStack.peekLast();
        int stride = b.textured ? 9 : 6;
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        org.joml.Vector2f v = new org.joml.Vector2f();
        for (int i = 0; i < b.n; i += stride) {
            for (int c = 0; c < 4; c++) {
                float x = (c & 1) == 0 ? b.q[i] : b.q[i + 2];
                float y = (c & 2) == 0 ? b.q[i + 1] : b.q[i + 3];
                pose.transformPosition(x, y, v);
                minX = Math.min(minX, v.x);
                minY = Math.min(minY, v.y);
                maxX = Math.max(maxX, v.x);
                maxY = Math.max(maxY, v.y);
            }
        }
        int left = (int) Math.floor(minX);
        int top = (int) Math.floor(minY);
        net.minecraft.client.gui.ScreenRect bounds = new net.minecraft.client.gui.ScreenRect(left, top,
                Math.max(1, (int) Math.ceil(maxX) - left), Math.max(1, (int) Math.ceil(maxY) - top));
        if (scissor != null) {
            bounds = scissor.intersection(bounds);
            if (bounds == null) {
                return;
            }
        }
        float[] data = java.util.Arrays.copyOf(b.q, b.n);
        net.minecraft.client.texture.TextureSetup setup;
        if (texture == null) {
            setup = net.minecraft.client.texture.TextureSetup.empty();
        } else {
            //? if >=1.21.11 {
            setup = net.minecraft.client.texture.TextureSetup.of(texture.getGlTextureView(), texture.getSampler());
            //?} else {
            /*setup = net.minecraft.client.texture.TextureSetup.withoutGlTexture(texture.getGlTextureView());
            *///?}
        }
        ctx.state.addSimpleElement(new Element(b.textured, data, pose, setup, scissor, bounds, textureId));
    }

    /** One batch as a GUI element. {@code textureId} names the texture for textured batches (else null). */
    public record Element(boolean textured, float[] q, org.joml.Matrix3x2f pose,
                           net.minecraft.client.texture.TextureSetup textureSetup,
                           net.minecraft.client.gui.ScreenRect scissorArea,
                           net.minecraft.client.gui.ScreenRect bounds,
                           net.minecraft.util.Identifier textureId)
            implements net.minecraft.client.gui.render.state.SimpleGuiElementRenderState {

        @Override
        public com.mojang.blaze3d.pipeline.RenderPipeline pipeline() {
            return textured ? net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED : net.minecraft.client.gl.RenderPipelines.GUI;
        }

        //? if >=1.21.10 {
        @Override
        public void setupVertices(net.minecraft.client.render.VertexConsumer vc) {
            emit(vc);
        }
        //?} else {
        /*@Override
        public void setupVertices(net.minecraft.client.render.VertexConsumer vc, float depth) {
            emit(vc);
        }
        *///?}

        /** Write this element's quads to {@code vc} (also used to redraw a menu in the world). */
        public void emit(net.minecraft.client.render.VertexConsumer vc) {
            if (textured) {
                for (int i = 0; i < q.length; i += 9) {
                    int c = Float.floatToRawIntBits(q[i + 8]);
                    vertex(vc, q[i], q[i + 1]).texture(q[i + 4], q[i + 5]).color(c);
                    vertex(vc, q[i], q[i + 3]).texture(q[i + 4], q[i + 7]).color(c);
                    vertex(vc, q[i + 2], q[i + 3]).texture(q[i + 6], q[i + 7]).color(c);
                    vertex(vc, q[i + 2], q[i + 1]).texture(q[i + 6], q[i + 5]).color(c);
                }
            } else {
                for (int i = 0; i < q.length; i += 6) {
                    int c1 = Float.floatToRawIntBits(q[i + 4]);
                    int c2 = Float.floatToRawIntBits(q[i + 5]);
                    vertex(vc, q[i], q[i + 1]).color(c1);
                    vertex(vc, q[i], q[i + 3]).color(c2);
                    vertex(vc, q[i + 2], q[i + 3]).color(c2);
                    vertex(vc, q[i + 2], q[i + 1]).color(c1);
                }
            }
        }

        private net.minecraft.client.render.VertexConsumer vertex(net.minecraft.client.render.VertexConsumer vc, float x, float y) {
            //? if >=1.21.10 {
            return vc.vertex(pose, x, y);
            //?} else {
            /*return vc.vertex(pose, x, y, 0f);
            *///?}
        }
    }
    //?}
}
