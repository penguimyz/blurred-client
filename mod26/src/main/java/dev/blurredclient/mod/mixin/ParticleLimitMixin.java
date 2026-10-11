package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.fx.Fx;
import dev.blurredclient.mod.module.Modules;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Particle limiter: drops a share of new particles. Blurred's own effects pass through. */
@Mixin(ParticleEngine.class)
public class ParticleLimitMixin {

    @Inject(method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true)
    private void blurred$limit(ParticleOptions effect, double x, double y, double z, double vx, double vy, double vz,
                               CallbackInfoReturnable<Particle> cir) {
        if (!Fx.ours() && Modules.PARTICLE_LIMITER.drop()) {
            cir.setReturnValue(null);
        }
    }
}
