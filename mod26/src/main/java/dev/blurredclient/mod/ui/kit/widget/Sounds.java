package dev.blurredclient.mod.ui.kit.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * UI feedback sounds. The same click vanilla buttons make, at the same
 * (quiet) volume, so Blurred's controls sound like the rest of the game.
 */
public final class Sounds {
    private Sounds() {}

    public static void click() {
        // Yarn renamed master() to ui() in the 1.21.11 mappings; same method.
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }
}
