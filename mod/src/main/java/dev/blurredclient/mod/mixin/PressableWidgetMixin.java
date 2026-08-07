package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.social.Essential;
import dev.blurredclient.mod.ui.OceanUi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
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
 *
 * <h2>What it deliberately leaves alone</h2>
 *
 * The reach of this injection is the whole problem with it. Cancelling
 * {@code renderWidget} replaces whatever the widget was going to draw with a
 * labelled rectangle — which is right for a text button and destructive for
 * anything that draws a picture. Three categories are skipped, and each one is
 * a bug that was reported:
 *
 * <ul>
 *   <li><b>Other mods' widgets.</b> A voice-chat mod's mute and deafen buttons
 *       are {@code PressableWidget} subclasses that render their own icons.
 *       Styling them threw the icon away and drew the accessibility label in
 *       its place. No mod's custom widget is ours to repaint.</li>
 *   <li><b>Container screens.</b> Inventory, crafting, chests and every modded
 *       GUI built on {@link HandledScreen}. Their buttons sit on top of a
 *       texture, next to item slots, and are almost all icons — the recipe
 *       book toggle being the obvious one. Painting panels over that is what
 *       made the crafting screen look broken.</li>
 *   <li><b>Icon buttons generally.</b> Anything textured, or small and square,
 *       or with no label. There's nothing to draw but a picture we'd be
 *       covering.</li>
 * </ul>
 */
@Mixin(PressableWidget.class)
public class PressableWidgetMixin {

    /** Widgets at or under this size are icons, not labelled buttons. */
    private static final int ICON_SIZE = 22;

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void blurred$oceanButton(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!Essential.shouldStyleMenus()) {
            return;
        }

        PressableWidget self = (PressableWidget) (Object) this;
        if (!self.visible || !blurred$shouldStyle(self)) {
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

    private static boolean blurred$shouldStyle(PressableWidget self) {
        // Only vanilla's own widgets. Another mod's button draws whatever that
        // mod decided it draws, and replacing it is never an improvement.
        String owner = self.getClass().getName();
        if (!owner.startsWith("net.minecraft.")) {
            return false;
        }

        // Vanilla's own icon buttons: 20x20 sprites whose getMessage() is the
        // full accessible label ("Accessibility Settings..."). Styling those
        // drew that whole string inside a 20px box.
        if (self instanceof TexturedButtonWidget) {
            return false;
        }
        if (self.getWidth() <= ICON_SIZE && self.getHeight() <= ICON_SIZE) {
            return false;
        }
        if (self.getMessage() == null || self.getMessage().getString().isBlank()) {
            return false;
        }

        // Container GUIs are gameplay surfaces, not menus.
        Screen screen = MinecraftClient.getInstance().currentScreen;
        return !(screen instanceof HandledScreen<?>);
    }
}
