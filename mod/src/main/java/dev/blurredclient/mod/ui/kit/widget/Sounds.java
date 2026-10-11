package dev.blurredclient.mod.ui.kit.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

/**
 * UI feedback sounds. The same click vanilla buttons make, at the same
 * (quiet) volume, so Blurred's controls sound like the rest of the game.
 */
public final class Sounds {
    private Sounds() {}

    public static void click() {
        // Yarn renamed master() to ui() in the 1.21.11 mappings; same method.
        //? if >=1.21.11 {
        MinecraftClient.getInstance().getSoundManager()
                .play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1f));
        //?} else {
        /*MinecraftClient.getInstance().getSoundManager()
                .play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1f));
        *///?}
    }
}
