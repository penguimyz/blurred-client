package dev.blurredclient.mod.fx;

import dev.blurredclient.mod.screen.clickgui.ClickGuiScreen;
import dev.blurredclient.mod.ui.GuiBatch;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * The real menu, drawn onto the 3D panel. The click GUI renders into its own
 * off-screen GUI state exactly as it would on screen; each shape and string
 * it produced is then re-drawn on the panel's plane in the world, clipped to
 * the same scroll areas. Your aim point on the panel is its mouse.
 *
 * <p>Needs the GUI render-state system (1.21.10+). Older versions keep the
 * simpler built-in panel.
 */
public final class HologramGui {
    private HologramGui() {}

    /** Virtual screen size the menu lays itself out in. */
    public static final int W = 440;
    public static final int H = 300;

    public static boolean supported() {
        //? if >=1.21.10 {
        return true;
        //?} else {
        /*return false;
        *///?}
    }

    private static ClickGuiScreen screen;

    private static ClickGuiScreen screen() {
        if (screen == null) {
            screen = new ClickGuiScreen(null);
            //? if >=1.21.11 {
            screen.init(W, H);
            //?} else {
            /*screen.init(MinecraftClient.getInstance(), W, H);
            *///?}
        }
        return screen;
    }

    public static void reset() {
        screen = null;
    }

    public static void click(double x, double y, int button) {
        screen().virtualClick(x, y, button);
    }

    public static void scroll(double x, double y, double amount) {
        screen().virtualScroll(x, y, amount);
    }

    /** Draw the menu on the plane at {@code origin} (top-left) spanned by {@code right}/{@code down}. */
    public static void render(WorldDraw.Frame f, Vec3d origin, Vec3d right, Vec3d down, float unit, int mouseX, int mouseY) {
        //? if >=1.21.10 {
        MinecraftClient client = MinecraftClient.getInstance();
        var state = new net.minecraft.client.gui.render.state.GuiRenderState();
        //? if >=1.21.11 {
        var ctx = new net.minecraft.client.gui.DrawContext(client, state, mouseX, mouseY);
        //?} else {
        /*var ctx = new net.minecraft.client.gui.DrawContext(client, state);
        *///?}
        // Glass needs the screen behind it, which a panel in the world doesn't have.
        dev.blurredclient.mod.ui.kit.LiquidGlass.suppress(true);
        try {
            screen().renderContent(ctx, mouseX, mouseY);
        } finally {
            dev.blurredclient.mod.ui.kit.LiquidGlass.suppress(false);
        }
        Vec3d toViewer = right.crossProduct(down).multiply(-1);
        int[] index = {0};
        state.forEachSimpleElement(el -> {
            RenderLayer layer;
            boolean lit;
            if (el.pipeline() == net.minecraft.client.gl.RenderPipelines.GUI) {
                layer = WorldDraw.colorLayer();
                lit = false;
            } else if (el instanceof GuiBatch.Element ge && ge.textured() && ge.textureId() != null) {
                layer = WorldDraw.textLayer(ge.textureId());
                lit = true;
            } else {
                return;
            }
            // Later elements sit a hair closer so they draw over earlier ones (depth-buffer sized steps).
            float lift = 0.00006f * ++index[0];
            var area = el.scissorArea();
            float[] clip = area == null ? null
                    : new float[] {area.getLeft(), area.getTop(), area.getLeft() + area.width(), area.getTop() + area.height()};
            Plane plane = new Plane(f.consumers.getBuffer(layer), f.matrices.peek().getPositionMatrix(),
                    origin.subtract(f.cam).add(toViewer.multiply(lift)), right, down, unit, lit, clip);
            el.setupVertices(plane);
            plane.flush();
        }, net.minecraft.client.gui.render.state.GuiRenderState.LayerFilter.ALL);
        //?}
    }

    /**
     * Takes GUI vertices (already through the element's own transform), groups
     * them into quads, clips each quad to the scroll area, and writes it into
     * the world on the panel's plane.
     */
    private static final class Plane implements VertexConsumer {
        private final VertexConsumer out;
        private final Matrix4f matrix;
        private final Vec3d base;
        private final Vec3d right;
        private final Vec3d down;
        private final float unit;
        private final boolean lit;
        private final float[] clip;
        private final float[] x = new float[4];
        private final float[] y = new float[4];
        private final int[] c = new int[4];
        private final float[] u = new float[4];
        private final float[] v = new float[4];
        private int n = -1;

        Plane(VertexConsumer out, Matrix4f matrix, Vec3d base, Vec3d right, Vec3d down, float unit, boolean lit, float[] clip) {
            this.out = out;
            this.matrix = matrix;
            this.base = base;
            this.right = right;
            this.down = down;
            this.unit = unit;
            this.lit = lit;
            this.clip = clip;
        }

        @Override
        public VertexConsumer vertex(float px, float py, float pz) {
            if (n == 3) {
                flush();
            }
            n++;
            x[n] = px;
            y[n] = py;
            c[n] = 0xFFFFFFFF;
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            if (n >= 0) {
                c[n] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            return this;
        }

        //? if >=1.21.11 {
        @Override
        public VertexConsumer color(int argb) {
            if (n >= 0) {
                c[n] = argb;
            }
            return this;
        }

        @Override
        public VertexConsumer lineWidth(float width) {
            return this;
        }
        //?}

        @Override
        public VertexConsumer texture(float tu, float tv) {
            if (n >= 0) {
                u[n] = tu;
                v[n] = tv;
            }
            return this;
        }

        @Override
        public VertexConsumer overlay(int a, int b) {
            return this;
        }

        @Override
        public VertexConsumer light(int a, int b) {
            return this;
        }

        @Override
        public VertexConsumer normal(float a, float b, float d) {
            return this;
        }

        void flush() {
            if (n != 3) {
                n = -1;
                return;
            }
            n = -1;
            float minX = Math.min(Math.min(x[0], x[1]), Math.min(x[2], x[3]));
            float maxX = Math.max(Math.max(x[0], x[1]), Math.max(x[2], x[3]));
            float minY = Math.min(Math.min(y[0], y[1]), Math.min(y[2], y[3]));
            float maxY = Math.max(Math.max(y[0], y[1]), Math.max(y[2], y[3]));
            if (clip != null) {
                if (maxX <= clip[0] || minX >= clip[2] || maxY <= clip[1] || minY >= clip[3]) {
                    return;
                }
                boolean axisAligned = (x[0] == x[1] || y[0] == y[1]) && maxX > minX && maxY > minY;
                if (axisAligned) {
                    for (int i = 0; i < 4; i++) {
                        float cx = Math.max(clip[0], Math.min(clip[2], x[i]));
                        float cy = Math.max(clip[1], Math.min(clip[3], y[i]));
                        // Pull the texture coordinate along with the clipped corner.
                        int ox = opposite(i, true);
                        int oy = opposite(i, false);
                        if (cx != x[i] && x[ox] != x[i]) {
                            u[i] = u[i] + (u[ox] - u[i]) * (cx - x[i]) / (x[ox] - x[i]);
                        }
                        if (cy != y[i] && y[oy] != y[i]) {
                            v[i] = v[i] + (v[oy] - v[i]) * (cy - y[i]) / (y[oy] - y[i]);
                        }
                        x[i] = cx;
                        y[i] = cy;
                    }
                }
            }
            for (int i = 0; i < 4; i++) {
                double wx = base.x + right.x * x[i] * unit + down.x * y[i] * unit;
                double wy = base.y + right.y * x[i] * unit + down.y * y[i] * unit;
                double wz = base.z + right.z * x[i] * unit + down.z * y[i] * unit;
                VertexConsumer vtx = out.vertex(matrix, (float) wx, (float) wy, (float) wz).color(c[i]);
                if (lit) {
                    vtx.texture(u[i], v[i]).light(0xF000F0);
                }
            }
        }

        /** The corner across from {@code i} horizontally (or vertically), for UV interpolation. */
        private int opposite(int i, boolean horizontal) {
            for (int j = 0; j < 4; j++) {
                if (j != i && (horizontal ? y[j] == y[i] && x[j] != x[i] : x[j] == x[i] && y[j] != y[i])) {
                    return j;
                }
            }
            return i;
        }
    }
}
