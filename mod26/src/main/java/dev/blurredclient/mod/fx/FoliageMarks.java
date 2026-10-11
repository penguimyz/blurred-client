package dev.blurredclient.mod.fx;

/**
 * Tells the Waving foliage shader which vertices are leaves and plants.
 *
 * <p>In 1.21.11 grass blocks, leaves and plants share one render pass, so the
 * shader can't tell them apart. This marks leaf, vine and plant models as
 * they load: each vertex's alpha becomes 254 minus how much it may move
 * (0-10). The game ignores an alpha that close to opaque; the shader reads
 * it, sways only marked vertices and sets the alpha back to 255. Plant roots
 * get 0, so plants bend instead of sliding.
 *
 * <p>Only on 1.21.11, like the shader.
 */
public final class FoliageMarks {
    private FoliageMarks() {}

    public static void register() {
        net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(plugin ->
                plugin.modifyBlockModelAfterBake().register((model, context) -> {
                    float[] weight = weights(context.state());
                    return weight == null ? model : new Marked(model, weight);
                }));
    }

    /** How far a vertex may move at the bottom and top of the block, or null to leave it still. */
    private static float[] weights(net.minecraft.world.level.block.state.BlockState state) {
        net.minecraft.world.level.block.Block block = state.getBlock();
        if (block instanceof net.minecraft.world.level.block.LeavesBlock) {
            return new float[] {0.6f, 0.6f};
        }
        if (block instanceof net.minecraft.world.level.block.VineBlock) {
            return new float[] {0.8f, 0.8f};
        }
        if (block instanceof net.minecraft.world.level.block.LilyPadBlock || block instanceof net.minecraft.world.level.block.MushroomBlock) {
            return null;
        }
        if (block instanceof net.minecraft.world.level.block.DoublePlantBlock
                && state.hasProperty(net.minecraft.world.level.block.DoublePlantBlock.HALF)) {
            return state.getValue(net.minecraft.world.level.block.DoublePlantBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
                    ? new float[] {0.5f, 1f} : new float[] {0f, 0.5f};
        }
        if (block instanceof net.minecraft.world.level.block.VegetationBlock) {
            return new float[] {0f, 1f};
        }
        return null;
    }

    private static final class Marked extends net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel {
        private final net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform mark;

        Marked(net.minecraft.client.renderer.block.dispatch.BlockStateModel wrapped, float[] weight) {
            super(wrapped);
            this.mark = quad -> {
                for (int i = 0; i < 4; i++) {
                    float y = Math.max(0f, Math.min(1f, quad.y(i)));
                    float w = weight[0] + (weight[1] - weight[0]) * y;
                    int alpha = 254 - Math.round(w * 10f);
                    quad.color(i, (quad.color(i) & 0x00FFFFFF) | (alpha << 24));
                }
                return true;
            };
        }

        @Override
        public void emitQuads(net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter emitter,
                              net.minecraft.client.renderer.block.BlockAndTintGetter blockView, net.minecraft.core.BlockPos pos,
                              net.minecraft.world.level.block.state.BlockState state, net.minecraft.util.RandomSource random,
                              java.util.function.Predicate<net.minecraft.core.Direction> cullTest) {
            emitter.pushTransform(mark);
            try {
                super.emitQuads(emitter, blockView, pos, state, random, cullTest);
            } finally {
                emitter.popTransform();
            }
        }
    }
}
