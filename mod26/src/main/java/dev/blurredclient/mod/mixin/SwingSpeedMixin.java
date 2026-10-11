package dev.blurredclient.mod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.blurredclient.mod.module.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Swing animation speed, for your own player only. {@code getHandSwingDuration}
 * only paces the arm animation on the client; attack speed is a separate
 * attribute and is untouched.
 */
@Mixin(LivingEntity.class)
public class SwingSpeedMixin {

    @ModifyReturnValue(method = "getCurrentSwingDuration", at = @At("RETURN"))
    private int blurred$swing(int ticks) {
        if ((Object) this != Minecraft.getInstance().player) {
            return ticks;
        }
        return Modules.SWING.adjust(ticks);
    }
}
