package dev.blurredclient.mod.fx;

import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

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
        // 26.x: everything drawn in the world is submitted while the frame's
        // submits are collected; the on-top layers draw over the rest regardless.
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
            run(LAYERS, ctx.poseStack(), ctx.submitNodeCollector(), ctx.levelState());
            run(LATE, ctx.poseStack(), ctx.submitNodeCollector(), ctx.levelState());
        });
    }

    private static float lastDt;

    private static final WorldBuffers BUFFERS = new WorldBuffers();

    private static void run(List<Layer> layers, PoseStack matrices, net.minecraft.client.renderer.SubmitNodeCollector queue, Object worldState) {
        Minecraft client = Minecraft.getInstance();
        WorldBuffers consumers = BUFFERS;
        if (client.level == null || matrices == null || queue == null || layers.isEmpty()) {
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
        consumers.submit(queue);
    }

    /** Partial tick for interpolating entity positions. */
    public static float tickDelta(Minecraft client) {
        return client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
    }

    private static RenderType quads() {
        return net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads();
    }

    /**
     * Layer for glowing shapes. Vanilla's additive "lightning" layer draws to
     * a target that isn't composited on 1.21.11 (seen in the in-game test:
     * nothing showed), so glows use the normal translucent layer and get
     * their softness from alpha falloff instead.
     */
    private static RenderType glowQuads() {
        return quads();
    }

    /** Flat coloured quads, see-through where alpha says so. */
    public static RenderType colorLayer() {
        return quads();
    }

    /** Textured, lit quads: how text is drawn in the world. */
    public static RenderType textLayer(net.minecraft.resources.Identifier texture) {
        return net.minecraft.client.renderer.rendertype.RenderTypes.text(texture);
    }

    /** Drawn over everything (no depth test) — for markers that must not hide behind what they mark. */
    private static RenderType onTop() {
        return net.minecraft.client.renderer.rendertype.RenderTypes.textBackgroundSeeThrough();
    }

    public static final class Frame {
        public final Minecraft client;
        public final PoseStack matrices;
        public final WorldBuffers consumers;
        /** The frame's submit collector. */
        public final net.minecraft.client.renderer.SubmitNodeCollector queue;
        /** The frame's world render state (1.21.10+), else null. */
        public final Object worldState;
        public final Camera camera;
        public final Vec3 cam;
        public final float tickDelta;
        /** Seconds since the previous frame. */
        public final float dt;

        Frame(Minecraft client, PoseStack matrices, WorldBuffers consumers, net.minecraft.client.renderer.SubmitNodeCollector queue,
              Object worldState, float dt) {
            this.client = client;
            this.matrices = matrices;
            this.consumers = consumers;
            this.queue = queue;
            this.worldState = worldState;
            this.camera = client.gameRenderer.mainCamera();
            this.cam = ScreenProject.cameraPos(camera);
            this.tickDelta = tickDelta(client);
            this.dt = dt;
        }

        private Matrix4f pose() {
            return matrices.last().pose();
        }

        private boolean over;

        /** While on, shapes draw on top of the world instead of being hidden behind it. */
        public void overlay(boolean on) {
            this.over = on;
        }

        private RenderType layer(boolean glow) {
            return over ? onTop() : glow ? glowQuads() : quads();
        }

        private void vertex(VertexConsumer vc, Matrix4f m, Vec3 p, int argb) {
            VertexConsumer v = vc.addVertex(m, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z)).setColor(argb);
            if (over) {
                v.setLight(0xF000F0);
            }
        }

        /** A flat four-cornered shape, corners in order, each with its own colour. */
        public void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int ca, int cb, int cc, int cd, boolean glow) {
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

        public void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb, boolean glow) {
            quad(a, b, c, d, argb, argb, argb, argb, glow);
        }

        /**
         * A flat ring lying in the plane spanned by {@code u} and {@code v}
         * (unit vectors) around {@code centre}, from radius {@code r0} to
         * {@code r1}; colour blends from {@code c0} at the inner edge to
         * {@code c1} at the outer. {@code from}/{@code to} (0..1 of a turn)
         * draw only an arc.
         */
        public void ring(Vec3 centre, Vec3 u, Vec3 v, double r0, double r1, int c0, int c1,
                         double from, double to, int segments, boolean glow) {
            double span = (to - from) * Math.PI * 2;
            double start = from * Math.PI * 2;
            Vec3 prevIn = null;
            Vec3 prevOut = null;
            for (int i = 0; i <= segments; i++) {
                double a = start + span * i / segments;
                Vec3 dir = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
                Vec3 in = centre.add(dir.scale(r0));
                Vec3 out = centre.add(dir.scale(r1));
                if (prevIn != null) {
                    quad(prevIn, prevOut, out, in, c0, c1, c1, c0, glow);
                }
                prevIn = in;
                prevOut = out;
            }
        }

        /** A ring on the ground (horizontal). */
        public void groundRing(Vec3 centre, double r0, double r1, int c0, int c1, int segments, boolean glow) {
            ring(centre, new Vec3(1, 0, 0), new Vec3(0, 0, 1), r0, r1, c0, c1, 0, 1, segments, glow);
        }

        // ---- local-space drawing: models made of boxes (pets, hats, shards) ----

        /** Start drawing in a local space whose origin is {@code world}. Pair with {@link #pop}. */
        public void pushAt(Vec3 world) {
            matrices.pushPose();
            matrices.translate((float) (world.x - cam.x), (float) (world.y - cam.y), (float) (world.z - cam.z));
        }

        public void pop() {
            matrices.popPose();
        }

        private void local(VertexConsumer vc, Matrix4f m, float x, float y, float z, int argb) {
            VertexConsumer v = vc.addVertex(m, x, y, z).setColor(argb);
            if (over) {
                v.setLight(0xF000F0);
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
        public void billboardText(Vec3 at, Component text, float scale, int argb, int background) {
            Vec3[] bb = billboard();
            int w = textWidth(text);
            Vec3 down = bb[1].scale(-1);
            Vec3 origin = at.subtract(bb[0].scale(w * scale / 2.0)).subtract(down.scale(4.5 * scale));
            onPlane(origin, bb[0], down, scale, () -> {
                if ((background >>> 24) != 0) {
                    rect2d(-3, -2, w + 3, 10, background, false);
                }
                matrices.translate(0, 0, -0.05f);
                text2d(text, 0, 0, argb);
            });
        }

        /** Unit vectors pointing right and up on screen, for shapes that face the camera. */
        public Vec3[] billboard() {
            double yaw = Math.toRadians(camera.yRot());
            double pitch = Math.toRadians(camera.xRot());
            Vec3 forward = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
            Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
            Vec3 up = right.cross(forward).normalize();
            return new Vec3[] {right, up, forward};
        }

        /**
         * Run {@code body} with the matrix set up so that 2D drawing (x right,
         * y down, in units of {@code scale} blocks) lands on the plane at
         * {@code origin} spanned by {@code right} and {@code down}.
         */
        public void onPlane(Vec3 origin, Vec3 right, Vec3 down, float scale, Runnable body) {
            Vec3 normal = right.cross(down);
            matrices.pushPose();
            Matrix4f basis = new Matrix4f(
                    (float) right.x, (float) right.y, (float) right.z, 0,
                    (float) down.x, (float) down.y, (float) down.z, 0,
                    (float) normal.x, (float) normal.y, (float) normal.z, 0,
                    (float) (origin.x - cam.x), (float) (origin.y - cam.y), (float) (origin.z - cam.z), 1);
            matrices.last().pose().mul(basis);
            matrices.scale(scale, scale, scale);
            try {
                body.run();
            } finally {
                matrices.popPose();
            }
        }

        /** A filled rectangle in the current plane's 2D units (see {@link #onPlane}). */
        public void rect2d(float x1, float y1, float x2, float y2, int argb, boolean glow) {
            VertexConsumer vc = consumers.getBuffer(glow ? glowQuads() : quads());
            Matrix4f m = pose();
            vc.addVertex(m, x1, y1, 0).setColor(argb);
            vc.addVertex(m, x1, y2, 0).setColor(argb);
            vc.addVertex(m, x2, y2, 0).setColor(argb);
            vc.addVertex(m, x2, y1, 0).setColor(argb);
        }

        /** A vertical gradient rectangle in plane units. */
        public void gradient2d(float x1, float y1, float x2, float y2, int top, int bottom, boolean glow) {
            VertexConsumer vc = consumers.getBuffer(glow ? glowQuads() : quads());
            Matrix4f m = pose();
            vc.addVertex(m, x1, y1, 0).setColor(top);
            vc.addVertex(m, x1, y2, 0).setColor(bottom);
            vc.addVertex(m, x2, y2, 0).setColor(bottom);
            vc.addVertex(m, x2, y1, 0).setColor(top);
        }

        /** Text in plane units, at full brightness. */
        public void text2d(Component text, float x, float y, int argb) {
            queue.submitText(matrices, x, y, text.getVisualOrderText(), false, Font.DisplayMode.POLYGON_OFFSET, 0xF000F0, argb, 0, 0);
        }

        /** Text at {@code scale} times the plane's units. */
        public void text2d(Component text, float x, float y, int argb, float scale) {
            matrices.pushPose();
            matrices.translate(x, y, 0);
            matrices.scale(scale, scale, 1f);
            text2d(text, 0, 0, argb);
            matrices.popPose();
        }

        public int textWidth(Component text) {
            return client.font.width(text);
        }

        /**
         * An item, as it looks in an item frame, centred on the current
         * matrix origin and {@code size} units across, at full brightness.
         */
        public void item(ItemStack stack, float size, int seed) {
            if (stack.isEmpty()) {
                return;
            }
            matrices.pushPose();
            matrices.scale(size, size, size);
            var state = new net.minecraft.client.renderer.item.ItemStackRenderState();
            client.getItemModelResolver().updateForTopItem(state, stack, net.minecraft.world.item.ItemDisplayContext.FIXED,
                    client.level, null, seed);
            state.submit(matrices, (net.minecraft.client.renderer.SubmitNodeCollector) queue, 0xF000F0,
                    OverlayTexture.NO_OVERLAY, 0);
            matrices.popPose();
        }
    }
}
