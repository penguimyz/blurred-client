package dev.blurredclient.mod.fx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Drawing in the world: Blurred's effects, the in-chest item view and the 3D
 * menu are geometry placed in the scene, not particles or screen overlays.
 *
 * <p>Fabric's world-render event moved and changed shape across the supported
 * versions (javap-checked): up to 1.21.8 it's {@code rendering.v1} with
 * {@code matrixStack()}; from 1.21.10 it's {@code rendering.v1.world} with
 * {@code matrices()} and a render command queue that items must go through.
 * Items have to be submitted before entities are drawn there, so that's the
 * hook used on 1.21.10+; older versions draw after entities.
 *
 * <p>All positions handed to a {@link Frame} are world coordinates; it makes
 * them camera-relative itself.
 */
public final class WorldDraw {
    private WorldDraw() {}

    /** Something that draws into the world each frame. */
    public interface Layer {
        void render(Frame frame);
    }

    private static final List<Layer> LAYERS = new ArrayList<>();
    /** Drawn after entities and flushed straight away, so mobs can't paint over them. */
    private static final List<Layer> LATE = new ArrayList<>();
    private static long lastNs = System.nanoTime();

    public static void add(Layer layer) {
        LAYERS.add(layer);
    }

    /**
     * A layer drawn after entities: for on-top markers (target ring, hit
     * marks). On 1.21.10+ the normal pass runs before entities, which then
     * cover anything drawn there, even with the depth test off.
     */
    public static void addLate(Layer layer) {
        LATE.add(layer);
    }

    public static void init() {
        //? if >=1.21.10 {
        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.BEFORE_ENTITIES.register(ctx ->
                run(LAYERS, ctx.matrices(), ctx.consumers(), ctx.commandQueue(), ctx.worldState()));
        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.AFTER_ENTITIES.register(ctx ->
                run(LATE, ctx.matrices(), ctx.consumers(), ctx.commandQueue(), ctx.worldState()));
        //?} else {
        /*net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            run(LAYERS, ctx.matrixStack(), ctx.consumers(), null, null);
            run(LATE, ctx.matrixStack(), ctx.consumers(), null, null);
        });
        *///?}
    }

    private static float lastDt;

    private static void run(List<Layer> layers, MatrixStack matrices, VertexConsumerProvider consumers, Object queue, Object worldState) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || matrices == null || consumers == null || layers.isEmpty()) {
            return;
        }
        if (layers == LAYERS) {
            long now = System.nanoTime();
            lastDt = Math.min(0.1f, (now - lastNs) / 1e9f);
            lastNs = now;
        }
        Frame frame = new Frame(client, matrices, consumers, queue, worldState, lastDt);
        for (Layer layer : layers) {
            try {
                layer.render(frame);
            } catch (RuntimeException e) {
                // One broken effect shouldn't take the frame (or the game) down.
                dev.blurredclient.mod.BlurredMod.LOGGER.error("Blurred world layer failed", e);
                layers.remove(layer);
                break;
            }
        }
        if (layers == LATE && consumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(quads());
            immediate.draw(onTop());
        }
    }

    /** Partial tick for interpolating entity positions. */
    public static float tickDelta(MinecraftClient client) {
        //? if >=1.21.8 {
        return client.getRenderTickCounter().getTickProgress(true);
        //?} else {
        /*return client.getRenderTickCounter().getTickDelta(true);
        *///?}
    }

    private static RenderLayer quads() {
        //? if >=1.21.11 {
        return net.minecraft.client.render.RenderLayers.debugQuads();
        //?} else {
        /*return RenderLayer.getDebugQuads();
        *///?}
    }

    /**
     * Layer for glowing shapes. Vanilla's additive "lightning" layer draws to
     * a target that isn't composited on 1.21.11 (seen in the in-game test:
     * nothing showed), so glows use the normal translucent layer and get
     * their softness from alpha falloff instead.
     */
    private static RenderLayer glowQuads() {
        return quads();
    }

    /** Flat coloured quads, see-through where alpha says so. */
    public static RenderLayer colorLayer() {
        return quads();
    }

    /** Textured, lit quads: how text is drawn in the world. */
    public static RenderLayer textLayer(net.minecraft.util.Identifier texture) {
        //? if >=1.21.11 {
        return net.minecraft.client.render.RenderLayers.text(texture);
        //?} else {
        /*return RenderLayer.getText(texture);
        *///?}
    }

    /** Drawn over everything (no depth test) — for markers that must not hide behind what they mark. */
    private static RenderLayer onTop() {
        //? if >=1.21.11 {
        return net.minecraft.client.render.RenderLayers.textBackgroundSeeThrough();
        //?} else {
        /*return RenderLayer.getTextBackgroundSeeThrough();
        *///?}
    }

    public static final class Frame {
        public final MinecraftClient client;
        public final MatrixStack matrices;
        public final VertexConsumerProvider consumers;
        /** The render command queue (1.21.10+), else null. */
        public final Object queue;
        /** The frame's world render state (1.21.10+), else null. */
        public final Object worldState;
        public final Camera camera;
        public final Vec3d cam;
        public final float tickDelta;
        /** Seconds since the previous frame. */
        public final float dt;

        Frame(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider consumers, Object queue,
              Object worldState, float dt) {
            this.client = client;
            this.matrices = matrices;
            this.consumers = consumers;
            this.queue = queue;
            this.worldState = worldState;
            this.camera = client.gameRenderer.getCamera();
            this.cam = ScreenProject.cameraPos(camera);
            this.tickDelta = tickDelta(client);
            this.dt = dt;
        }

        private Matrix4f pose() {
            return matrices.peek().getPositionMatrix();
        }

        private boolean over;

        /** While on, shapes draw on top of the world instead of being hidden behind it. */
        public void overlay(boolean on) {
            this.over = on;
        }

        private RenderLayer layer(boolean glow) {
            return over ? onTop() : glow ? glowQuads() : quads();
        }

        private void vertex(VertexConsumer vc, Matrix4f m, Vec3d p, int argb) {
            VertexConsumer v = vc.vertex(m, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z)).color(argb);
            if (over) {
                v.light(0xF000F0);
            }
        }

        /** A flat four-cornered shape, corners in order, each with its own colour. */
        public void quad(Vec3d a, Vec3d b, Vec3d c, Vec3d d, int ca, int cb, int cc, int cd, boolean glow) {
            VertexConsumer vc = consumers.getBuffer(layer(glow));
            Matrix4f m = pose();
            vertex(vc, m, a, ca);
            vertex(vc, m, b, cb);
            vertex(vc, m, c, cc);
            vertex(vc, m, d, cd);
            if (over) {
                // The see-through layer culls back faces (the normal one doesn't):
                // add the other winding too. Culling keeps exactly one of the two.
                vertex(vc, m, d, cd);
                vertex(vc, m, c, cc);
                vertex(vc, m, b, cb);
                vertex(vc, m, a, ca);
            }
        }

        public void quad(Vec3d a, Vec3d b, Vec3d c, Vec3d d, int argb, boolean glow) {
            quad(a, b, c, d, argb, argb, argb, argb, glow);
        }

        /**
         * A flat ring lying in the plane spanned by {@code u} and {@code v}
         * (unit vectors) around {@code centre}, from radius {@code r0} to
         * {@code r1}; colour blends from {@code c0} at the inner edge to
         * {@code c1} at the outer. {@code from}/{@code to} (0..1 of a turn)
         * draw only an arc.
         */
        public void ring(Vec3d centre, Vec3d u, Vec3d v, double r0, double r1, int c0, int c1,
                         double from, double to, int segments, boolean glow) {
            double span = (to - from) * Math.PI * 2;
            double start = from * Math.PI * 2;
            Vec3d prevIn = null;
            Vec3d prevOut = null;
            for (int i = 0; i <= segments; i++) {
                double a = start + span * i / segments;
                Vec3d dir = u.multiply(Math.cos(a)).add(v.multiply(Math.sin(a)));
                Vec3d in = centre.add(dir.multiply(r0));
                Vec3d out = centre.add(dir.multiply(r1));
                if (prevIn != null) {
                    quad(prevIn, prevOut, out, in, c0, c1, c1, c0, glow);
                }
                prevIn = in;
                prevOut = out;
            }
        }

        /** A ring on the ground (horizontal). */
        public void groundRing(Vec3d centre, double r0, double r1, int c0, int c1, int segments, boolean glow) {
            ring(centre, new Vec3d(1, 0, 0), new Vec3d(0, 0, 1), r0, r1, c0, c1, 0, 1, segments, glow);
        }

        // ---- local-space drawing: models made of boxes (pets, hats, shards) ----

        /** Start drawing in a local space whose origin is {@code world}. Pair with {@link #pop}. */
        public void pushAt(Vec3d world) {
            matrices.push();
            matrices.translate((float) (world.x - cam.x), (float) (world.y - cam.y), (float) (world.z - cam.z));
        }

        public void pop() {
            matrices.pop();
        }

        private void local(VertexConsumer vc, Matrix4f m, float x, float y, float z, int argb) {
            VertexConsumer v = vc.vertex(m, x, y, z).color(argb);
            if (over) {
                v.light(0xF000F0);
            }
        }

        /** A quad in local space. */
        public void localQuad(float ax, float ay, float az, float bx, float by, float bz,
                              float cx, float cy, float cz, float dx, float dy, float dz, int argb) {
            VertexConsumer vc = consumers.getBuffer(layer(false));
            Matrix4f m = pose();
            local(vc, m, ax, ay, az, argb);
            local(vc, m, bx, by, bz, argb);
            local(vc, m, cx, cy, cz, argb);
            local(vc, m, dx, dy, dz, argb);
        }

        /**
         * A solid box in local space, faces shaded like vanilla blocks (top
         * brightest, bottom darkest) so it reads as 3D without a texture.
         */
        public void box(float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
            int top = argb;
            int side1 = shade(argb, 0.82f);
            int side2 = shade(argb, 0.68f);
            int bottom = shade(argb, 0.52f);
            localQuad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, top);
            localQuad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, bottom);
            localQuad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, side2);
            localQuad(x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, side2);
            localQuad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, side1);
            localQuad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, side1);
        }

        /** {@code argb} with its colour scaled by {@code k}, alpha kept. */
        public static int shade(int argb, float k) {
            int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * k));
            int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * k));
            int b = Math.min(255, Math.round((argb & 0xFF) * k));
            return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
        }

        /** Text facing the camera at {@code at}, {@code scale} blocks per font pixel, centred. */
        public void billboardText(Vec3d at, Text text, float scale, int argb, int background) {
            Vec3d[] bb = billboard();
            int w = textWidth(text);
            Vec3d down = bb[1].multiply(-1);
            Vec3d origin = at.subtract(bb[0].multiply(w * scale / 2.0)).subtract(down.multiply(4.5 * scale));
            onPlane(origin, bb[0], down, scale, () -> {
                if ((background >>> 24) != 0) {
                    rect2d(-3, -2, w + 3, 10, background, false);
                }
                matrices.translate(0, 0, -0.05f);
                text2d(text, 0, 0, argb);
            });
        }

        /** Unit vectors pointing right and up on screen, for shapes that face the camera. */
        public Vec3d[] billboard() {
            double yaw = Math.toRadians(camera.getYaw());
            double pitch = Math.toRadians(camera.getPitch());
            Vec3d forward = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
            Vec3d right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
            Vec3d up = right.crossProduct(forward).normalize();
            return new Vec3d[] {right, up, forward};
        }

        /**
         * Run {@code body} with the matrix set up so that 2D drawing (x right,
         * y down, in units of {@code scale} blocks) lands on the plane at
         * {@code origin} spanned by {@code right} and {@code down}.
         */
        public void onPlane(Vec3d origin, Vec3d right, Vec3d down, float scale, Runnable body) {
            Vec3d normal = right.crossProduct(down);
            matrices.push();
            Matrix4f basis = new Matrix4f(
                    (float) right.x, (float) right.y, (float) right.z, 0,
                    (float) down.x, (float) down.y, (float) down.z, 0,
                    (float) normal.x, (float) normal.y, (float) normal.z, 0,
                    (float) (origin.x - cam.x), (float) (origin.y - cam.y), (float) (origin.z - cam.z), 1);
            matrices.peek().getPositionMatrix().mul(basis);
            matrices.scale(scale, scale, scale);
            try {
                body.run();
            } finally {
                matrices.pop();
            }
        }

        /** A filled rectangle in the current plane's 2D units (see {@link #onPlane}). */
        public void rect2d(float x1, float y1, float x2, float y2, int argb, boolean glow) {
            VertexConsumer vc = consumers.getBuffer(glow ? glowQuads() : quads());
            Matrix4f m = pose();
            vc.vertex(m, x1, y1, 0).color(argb);
            vc.vertex(m, x1, y2, 0).color(argb);
            vc.vertex(m, x2, y2, 0).color(argb);
            vc.vertex(m, x2, y1, 0).color(argb);
        }

        /** A vertical gradient rectangle in plane units. */
        public void gradient2d(float x1, float y1, float x2, float y2, int top, int bottom, boolean glow) {
            VertexConsumer vc = consumers.getBuffer(glow ? glowQuads() : quads());
            Matrix4f m = pose();
            vc.vertex(m, x1, y1, 0).color(top);
            vc.vertex(m, x1, y2, 0).color(bottom);
            vc.vertex(m, x2, y2, 0).color(bottom);
            vc.vertex(m, x2, y1, 0).color(top);
        }

        /** Text in plane units, at full brightness. */
        public void text2d(Text text, float x, float y, int argb) {
            TextRenderer font = client.textRenderer;
            font.draw(text, x, y, argb, false, pose(), consumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, 0xF000F0);
        }

        /** Text at {@code scale} times the plane's units. */
        public void text2d(Text text, float x, float y, int argb, float scale) {
            matrices.push();
            matrices.translate(x, y, 0);
            matrices.scale(scale, scale, 1f);
            text2d(text, 0, 0, argb);
            matrices.pop();
        }

        public int textWidth(Text text) {
            return client.textRenderer.getWidth(text);
        }

        /**
         * An item, as it looks in an item frame, centred on the current
         * matrix origin and {@code size} units across, at full brightness.
         */
        public void item(ItemStack stack, float size, int seed) {
            if (stack.isEmpty()) {
                return;
            }
            matrices.push();
            matrices.scale(size, size, size);
            //? if >=1.21.10 {
            var state = new net.minecraft.client.render.item.ItemRenderState();
            client.getItemModelManager().clearAndUpdate(state, stack, net.minecraft.item.ItemDisplayContext.FIXED,
                    client.world, null, seed);
            state.render(matrices, (net.minecraft.client.render.command.OrderedRenderCommandQueue) queue, 0xF000F0,
                    OverlayTexture.DEFAULT_UV, 0);
            //?} elif >=1.21.8 {
            /*client.getItemRenderer().renderItem(stack, net.minecraft.item.ItemDisplayContext.FIXED, 0xF000F0,
                    OverlayTexture.DEFAULT_UV, matrices, consumers, client.world, seed);
            *///?} elif >=1.21.4 {
            /*client.getItemRenderer().renderItem(stack, net.minecraft.item.ModelTransformationMode.FIXED, 0xF000F0,
                    OverlayTexture.DEFAULT_UV, matrices, consumers, client.world, seed);
            *///?} else {
            /*client.getItemRenderer().renderItem(stack, net.minecraft.client.render.model.json.ModelTransformationMode.FIXED,
                    0xF000F0, OverlayTexture.DEFAULT_UV, matrices, consumers, client.world, seed);
            *///?}
            matrices.pop();
        }
    }
}
