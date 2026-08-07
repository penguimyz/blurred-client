package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.social.Essential;
import dev.blurredclient.mod.ui.OceanUi;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the title screen's rotating panorama with a submarine hanging in
 * deep water.
 *
 * <p>This needs its own mixin rather than riding on {@link ScreenBackgroundMixin}:
 * {@code TitleScreen} <b>overrides</b> {@code renderBackground} to draw the
 * panorama and never calls {@code super}, so an injection on {@code Screen}
 * never fires here. That's why the menu kept showing the vanilla panorama while
 * every other screen had already turned to water.
 *
 * <p>No Blurred branding is drawn. An earlier version put the mark and the
 * player's name above the Minecraft logo; it crowded the game's own title and
 * was removed on request.
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void blurred$submarineBackground(
            DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // Essential replaces the title screen too; see Essential#shouldStyleMenus.
        if (!Essential.shouldStyleMenus()) {
            return;
        }

        TitleScreen self = (TitleScreen) (Object) this;
        OceanUi.tick(delta);
        OceanUi.background(ctx, self.width, self.height);

        // Sat low and right of centre so the vanilla logo and the button column
        // stay clear of it — the submarine is scenery, not a thing to read
        // around.
        int scale = Math.max(2, self.width / 420);
        OceanUi.submarine(ctx, (int) (self.width * 0.72), (int) (self.height * 0.34), scale);

        ci.cancel();
    }
}
