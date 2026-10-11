package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.fx.Fx;
import dev.blurredclient.mod.module.Modules;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Particle limiter: drops a share of new particles. Blurred's own effects pass through. */
@Mixin(ParticleManager.class)
public class ParticleLimitMixin {

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true)
    private void blurred$limit(ParticleEffect effect, double x, double y, double z, double vx, double vy, double vz,
                               CallbackInfoReturnable<Particle> cir) {
        if (!Fx.ours() && Modules.PARTICLE_LIMITER.drop()) {
            cir.setReturnValue(null);
        }
    }
}
