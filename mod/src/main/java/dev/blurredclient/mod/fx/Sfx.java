package dev.blurredclient.mod.fx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;

/** A sound only you hear, not tied to a place in the world. */
public final class Sfx {
    private Sfx() {}

    public static void play(SoundEvent sound, float pitch, float volume) {
        MinecraftClient client = MinecraftClient.getInstance();
        //? if >=1.21.11 {
        client.getSoundManager().play(PositionedSoundInstance.ui(sound, pitch, volume));
        //?} else {
        /*client.getSoundManager().play(PositionedSoundInstance.master(sound, pitch, volume));
        *///?}
    }
}
