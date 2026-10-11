package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.module.Modules;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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
@Mixin(Level.class)
public class BlockChangeMixin {
    @Unique
    private static BlockState blurred$before;

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
    private void blurred$before(BlockPos pos, BlockState state, int flags, int depth, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ClientLevel && Modules.PLACE_ANIMATION.isActive()) {
            blurred$before = ((Level) (Object) this).getBlockState(pos);
        }
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))
    private void blurred$after(BlockPos pos, BlockState state, int flags, int depth, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ClientLevel && blurred$before != null) {
            BlockState before = blurred$before;
            blurred$before = null;
            if (cir.getReturnValueZ()) {
                Modules.PLACE_ANIMATION.changed(pos, before, state);
            }
        }
    }
}
