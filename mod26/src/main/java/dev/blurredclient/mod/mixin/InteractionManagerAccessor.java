package dev.blurredclient.mod.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only: how far through breaking the current block you are. Same names on 1.21.1-1.21.11. */
@Mixin(MultiPlayerGameMode.class)
public interface InteractionManagerAccessor {
    @Accessor("destroyProgress")
    float blurred$breakingProgress();

    @Accessor("isDestroying")
    boolean blurred$isBreaking();
}
