package dev.blurredclient.mod.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

/** Turning on a post effect by id (Ambience, Motion blur). Only used on 1.21.11. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @org.spongepowered.asm.mixin.gen.Invoker("setPostEffect")
    void blurred$setPostProcessor(net.minecraft.resources.Identifier id);
}
