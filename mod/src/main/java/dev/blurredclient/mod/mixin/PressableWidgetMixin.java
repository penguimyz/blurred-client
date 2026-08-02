package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.config.BlurredConfig;
import dev.blurredclient.mod.ui.OceanUi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws vanilla buttons in the ocean style.
 *
 * <p>Targets {@link PressableWidget} rather than {@code ButtonWidget}: it's the
 * shared base for buttons, toggles and most other pressables, so one injection
 * covers nearly every button in the game instead of just the plain ones.
 *
 * <p>Cancelling at HEAD means the vanilla nine-slice texture never draws, so
 * there's no double-render and no dependence on the widget texture atlas — which
 * is also why this survives across Minecraft versions unchanged.
 *
 * <p>{@code renderWidget} is {@code final} in newer versions. That prevents
 * *overriding* it in a subclass; it does not prevent a mixin injecting into it,
 * because mixins rewrite the method body rather than extend the class.
 */
@Mixin(PressableWidget.class)
public class PressableWidgetMixin {

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void blurred$oceanButton(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!BlurredConfig.get().styleMenus) {
            return;
        }

        PressableWidget self = (PressableWidget) (Object) this;
        if (!self.visible) {
            return;
        }

        // Leave icon buttons alone.
        //
        // The Language and Accessibility buttons on the title screen are 20x20
        // sprites whose getMessage() is the full accessible label — "Accessibility
        // Settings...". Styling them drew that whole string inside a 20px box, so
        // it spilled across its neighbours and the bottom row became unreadable.
        // Vanilla renders these as textures, and it should keep doing so.
        if (self instanceof TexturedButtonWidget) {
            return;
        }

        OceanUi.button(
                ctx,
                MinecraftClient.getInstance().textRenderer,
                self.getMessage(),
                self.getX(), self.getY(), self.getWidth(), self.getHeight(),
                self.isHovered(), self.active);
        ci.cancel();
    }
}
