package dev.blurredclient.mod.mixin;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

/** Turning on a post effect by id (Ambience, Motion blur). Only used on 1.21.11. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    //? if >=1.21.11 {
    @org.spongepowered.asm.mixin.gen.Invoker("setPostProcessor")
    void blurred$setPostProcessor(net.minecraft.util.Identifier id);
    //?}
}
