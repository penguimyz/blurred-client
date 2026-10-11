package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.fx.ContainerView;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In the in-chest view, the container texture is replaced by a panel behind your inventory. */
@Mixin({GenericContainerScreen.class, ShulkerBoxScreen.class})
public class ContainerBackgroundMixin {
    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
    private void blurred$panel(DrawContext ctx, float delta, int mouseX, int mouseY, CallbackInfo ci) {
        HandledScreen<?> self = (HandledScreen<?>) (Object) this;
        if (ContainerView.INSTANCE.active(self)) {
            ContainerView.INSTANCE.drawPanel(ctx, self);
            ci.cancel();
        }
    }
}
