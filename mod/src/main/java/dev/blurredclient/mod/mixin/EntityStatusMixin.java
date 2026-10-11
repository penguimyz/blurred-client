package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.fx.CombatEvents;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Deaths and totem pops, from the entity status the server broadcasts.
 * {@code handleStatus(byte)} is the same on every supported version.
 */
@Mixin(LivingEntity.class)
public class EntityStatusMixin {
    @Inject(method = "handleStatus", at = @At("HEAD"))
    private void blurred$status(byte status, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        // Only the client handles statuses (the server sends them); the thread check makes sure.
        if (net.minecraft.client.MinecraftClient.getInstance().isOnThread()) {
            CombatEvents.status(self, status);
        }
    }
}
