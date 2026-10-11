package dev.blurredclient.mod.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/** A sound only you hear, not tied to a place in the world. */
public final class Sfx {
    private Sfx() {}

    public static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft client = Minecraft.getInstance();
        client.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
