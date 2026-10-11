package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.module.Modules;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Block changes in your own world, for Place animation. Every client-side
 * block change, from you or a server update, goes through
 * {@code setBlockState(BlockPos, BlockState, int, int)}, the same on every
 * supported version. Only the client world is watched.
 */
@Mixin(World.class)
public class BlockChangeMixin {
    @Unique
    private static BlockState blurred$before;

    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z", at = @At("HEAD"))
    private void blurred$before(BlockPos pos, BlockState state, int flags, int depth, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ClientWorld && Modules.PLACE_ANIMATION.isActive()) {
            blurred$before = ((World) (Object) this).getBlockState(pos);
        }
    }

    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z", at = @At("RETURN"))
    private void blurred$after(BlockPos pos, BlockState state, int flags, int depth, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ClientWorld && blurred$before != null) {
            BlockState before = blurred$before;
            blurred$before = null;
            if (cir.getReturnValueZ()) {
                Modules.PLACE_ANIMATION.changed(pos, before, state);
            }
        }
    }
}
