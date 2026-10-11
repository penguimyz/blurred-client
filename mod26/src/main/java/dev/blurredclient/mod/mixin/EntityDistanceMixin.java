package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.module.Modules;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity distance: skip drawing far entities. {@code shouldRender} is the
 * same on every version, but its class was renamed in 1.21.10
 * ({@code EntityRenderDispatcher} to {@code EntityRenderManager}, javap-checked).
 */
@Mixin(net.minecraft.client.renderer.entity.EntityRenderDispatcher.class)
public class EntityDistanceMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void blurred$distance(Entity entity, Frustum frustum, double x, double y, double z,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (Modules.ENTITY_DISTANCE.cull(entity, x, y, z)) {
            cir.setReturnValue(false);
        }
    }
}
