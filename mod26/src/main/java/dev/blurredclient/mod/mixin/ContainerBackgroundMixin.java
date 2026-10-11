package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.fx.ContainerView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In the in-chest view, the container texture is replaced by a panel behind your inventory. */
@Mixin({ContainerScreen.class, ShulkerBoxScreen.class})
public class ContainerBackgroundMixin {
    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void blurred$panel(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (ContainerView.INSTANCE.active(self)) {
            ContainerView.INSTANCE.drawPanel(ctx, self);
            ci.cancel();
        }
    }
}
