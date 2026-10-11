package dev.blurredclient.mod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.blurredclient.mod.module.Modules;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Better name tags draws its own cards, so vanilla's label is skipped for
 * those entities. {@code hasLabel} gained a distance argument in 1.21.4;
 * matched by name, the entity taken with {@code @Local}.
 */
@Mixin(EntityRenderer.class)
public class NametagMixin {
    @ModifyReturnValue(method = "hasLabel", at = @At("RETURN"))
    private boolean blurred$ourTag(boolean vanilla, @Local(argsOnly = true) Entity entity) {
        return vanilla && !Modules.BETTER_NAMETAGS.replaces(entity);
    }
}
