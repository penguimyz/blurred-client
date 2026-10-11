package dev.blurredclient.mod.fx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;

/**
 * Spawning client-side particles for Blurred's visual effects.
 *
 * <p>Everything here goes through the client world's own particle system, so
 * it's drawn, lit and culled like any vanilla particle — and seen only by
 * you: nothing is sent to the server.
 *
 * <p>{@link #ours()} is true while Blurred is spawning, so the Particle
 * limiter module can let its own effects through.
 */
public final class Fx {
    private Fx() {}

    private static boolean spawning;

    public static boolean ours() {
        return spawning;
    }

    /** A coloured dust mote. The constructor took a Vector3f until 1.21.4 (javap-checked). */
    public static ParticleEffect dust(int argb, float scale) {
        //? if >=1.21.4 {
        return new DustParticleEffect(argb & 0xFFFFFF, scale);
        //?} else {
        /*return new DustParticleEffect(new org.joml.Vector3f(((argb >> 16) & 0xFF) / 255f,
                ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f), scale);
        *///?}
    }

    /**
     * Spawn through the particle manager directly: {@code World#addParticle}
     * was renamed in 1.21.8, while {@code ParticleManager#addParticle} is the
     * same on every supported version (javap-checked).
     */
    public static void spawn(ParticleEffect effect, double x, double y, double z, double vx, double vy, double vz) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }
        spawning = true;
        try {
            client.particleManager.addParticle(effect, x, y, z, vx, vy, vz);
        } finally {
            spawning = false;
        }
    }

    /** A flat ring of {@code count} particles of radius {@code r} around {@code x, z}. */
    public static void ring(ParticleEffect effect, double x, double y, double z, double r, int count, double outward) {
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count;
            double cx = Math.cos(a);
            double cz = Math.sin(a);
            spawn(effect, x + cx * r, y, z + cz * r, cx * outward, 0, cz * outward);
        }
    }
}
