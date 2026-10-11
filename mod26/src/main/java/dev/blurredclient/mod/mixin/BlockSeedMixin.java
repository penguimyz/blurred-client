package dev.blurredclient.mod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.blurredclient.mod.module.Modules;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * No block rotation: the per-position seed that picks a block's random model
 * variant is the same everywhere. {@code getRenderingSeed(BlockPos)} is the
 * same on every supported version.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public class BlockSeedMixin {
    @ModifyReturnValue(method = "getSeed", at = @At("RETURN"))
    private long blurred$sameSeed(long seed) {
        return Modules.NO_BLOCK_ROTATION.isActive() ? 0L : seed;
    }
}
