package dev.blurredclient.mod.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets Blurred add a layer to a living-entity renderer (addFeature is protected). */
@Mixin(LivingEntityRenderer.class)
public interface LivingRendererInvoker {
    @SuppressWarnings("rawtypes")
    @Invoker("addLayer")
    boolean blurred$addFeature(RenderLayer feature);
}
