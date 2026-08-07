package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.social.Essential;
import dev.blurredclient.mod.ui.OceanUi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the menu background with the ocean.
 *
 * <p>Only when no world is loaded. In-game, {@code renderBackground} draws the
 * translucent blur over your world, and covering that with opaque water would
 * mean you couldn't see the game behind the pause menu — which is worse than
 * unstyled. So the pause menu keeps vanilla's backdrop and only its widgets are
 * restyled.
 */
@Mixin(Screen.class)
public class ScreenBackgroundMixin {

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void blurred$oceanBackground(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // Essential reskins the main menu too; see Essential#shouldStyleMenus.
        if (!Essential.shouldStyleMenus()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            return;
        }

        Screen self = (Screen) (Object) this;
        OceanUi.tick(delta);
        OceanUi.background(ctx, self.width, self.height);
        ci.cancel();
    }
}
