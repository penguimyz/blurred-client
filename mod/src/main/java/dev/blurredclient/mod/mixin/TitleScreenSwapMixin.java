package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.screen.title.BlurredTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps vanilla's title screen for Blurred's whenever something opens it.
 *
 * <p>At {@code setScreen} rather than in {@code TitleScreen} itself, so every
 * route back to the menu — startup, leaving a world, Done in Options — goes
 * through one check. Only the exact vanilla class is swapped: a mod that
 * subclasses the title screen keeps its own.
 */
@Mixin(MinecraftClient.class)
public class TitleScreenSwapMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void blurred$swapTitle(Screen screen, CallbackInfo ci) {
        if (screen != null && screen.getClass() == TitleScreen.class && BlurredTitleScreen.enabled()) {
            ((MinecraftClient) (Object) this).setScreen(new BlurredTitleScreen());
            ci.cancel();
        }
    }
}
