package dev.blurredclient.mod.mixin;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets Blurred add a layer to a living-entity renderer (addFeature is protected). */
@Mixin(LivingEntityRenderer.class)
public interface LivingRendererInvoker {
    @SuppressWarnings("rawtypes")
    @Invoker("addFeature")
    boolean blurred$addFeature(FeatureRenderer feature);
}
