package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.fx.EntityCulling;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity culling: an entity the game would draw is skipped when it's hidden
 * behind solid blocks (see EntityCulling). {@code shouldRender(Entity,
 * Frustum, double, double, double)} is the same on every supported version
 * (javap-checked).
 */
@Mixin(EntityRenderer.class)
public class EntityCullMixin {
    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void blurred$cull(Entity entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z,
                              CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && EntityCulling.culled(entity)) {
            cir.setReturnValue(false);
        }
    }
}
