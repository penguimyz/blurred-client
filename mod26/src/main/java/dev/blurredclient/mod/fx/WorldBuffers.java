package dev.blurredclient.mod.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Stands in for the buffer source 26.x removed. World effects write their
 * vertices here as before (one recorder per render type, positions already
 * transformed), and {@link #submit} hands each type's vertices to the
 * frame's submit collector as one piece of custom geometry, replayed when
 * the game renders the frame.
 */
public final class WorldBuffers {
    private final Map<RenderType, Recorder> recorders = new LinkedHashMap<>();

    public VertexConsumer getBuffer(RenderType type) {
        return recorders.computeIfAbsent(type, t -> new Recorder());
    }

    /** Passes everything recorded to {@code collector} and starts over. */
    public void submit(SubmitNodeCollector collector) {
        PoseStack identity = new PoseStack();
        for (Map.Entry<RenderType, Recorder> e : recorders.entrySet()) {
            Recorder r = e.getValue();
            if (r.count == 0) {
                continue;
            }
            Recorder copy = r.copy();
            collector.submitCustomGeometry(identity, e.getKey(), (pose, vc) -> copy.replay(vc));
            r.clear();
        }
    }

    /** One render type's vertices. Per vertex: what was set, and the values. */
    static final class Recorder implements VertexConsumer {
        private static final int STRIDE = 13;
        private static final int COLOR = 1, UV = 2, UV1 = 4, UV2 = 8, NORMAL = 16, WIDTH = 32;

        private float[] d = new float[STRIDE * 64];
        private int count;

        private int base() {
            return (count - 1) * STRIDE;
        }

        private void flag(int f) {
            int b = base() + 12;
            d[b] = Float.intBitsToFloat(Float.floatToRawIntBits(d[b]) | f);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if ((count + 1) * STRIDE > d.length) {
                d = Arrays.copyOf(d, d.length * 2);
            }
            count++;
            int b = base();
            Arrays.fill(d, b, b + STRIDE, 0f);
            d[b] = x;
            d[b + 1] = y;
            d[b + 2] = z;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return setColor((a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF));
        }

        @Override
        public VertexConsumer setColor(int argb) {
            d[base() + 3] = Float.intBitsToFloat(argb);
            flag(COLOR);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            d[base() + 4] = u;
            d[base() + 5] = v;
            flag(UV);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            d[base() + 6] = Float.intBitsToFloat(u << 16 | (v & 0xFFFF));
            flag(UV1);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            d[base() + 7] = Float.intBitsToFloat(u << 16 | (v & 0xFFFF));
            flag(UV2);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            d[base() + 8] = x;
            d[base() + 9] = y;
            d[base() + 10] = z;
            flag(NORMAL);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            d[base() + 11] = width;
            flag(WIDTH);
            return this;
        }

        Recorder copy() {
            Recorder r = new Recorder();
            r.d = Arrays.copyOf(d, count * STRIDE);
            r.count = count;
            return r;
        }

        void clear() {
            count = 0;
        }

        void replay(VertexConsumer vc) {
            for (int i = 0; i < count; i++) {
                int b = i * STRIDE;
                int flags = Float.floatToRawIntBits(d[b + 12]);
                vc.addVertex(d[b], d[b + 1], d[b + 2]);
                if ((flags & COLOR) != 0) {
                    vc.setColor(Float.floatToRawIntBits(d[b + 3]));
                }
                if ((flags & UV) != 0) {
                    vc.setUv(d[b + 4], d[b + 5]);
                }
                if ((flags & UV1) != 0) {
                    int p = Float.floatToRawIntBits(d[b + 6]);
                    vc.setUv1(p >> 16, (short) p);
                }
                if ((flags & UV2) != 0) {
                    int p = Float.floatToRawIntBits(d[b + 7]);
                    vc.setUv2(p >> 16, (short) p);
                }
                if ((flags & NORMAL) != 0) {
                    vc.setNormal(d[b + 8], d[b + 9], d[b + 10]);
                }
                if ((flags & WIDTH) != 0) {
                    vc.setLineWidth(d[b + 11]);
                }
            }
        }
    }
}
