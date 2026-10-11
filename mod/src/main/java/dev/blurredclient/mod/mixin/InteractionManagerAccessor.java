package dev.blurredclient.mod.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only: how far through breaking the current block you are. Same names on 1.21.1-1.21.11. */
@Mixin(ClientPlayerInteractionManager.class)
public interface InteractionManagerAccessor {
    @Accessor("currentBreakingProgress")
    float blurred$breakingProgress();

    @Accessor("breakingBlock")
    boolean blurred$isBreaking();
}
