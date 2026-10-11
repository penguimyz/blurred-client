package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.hud.CpsTracker;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds the CPS readout.
 *
 * <p>Hooks the GLFW button callback, not the client tick: ticks run at 20Hz, so
 * anything above 20 CPS — the entire range a CPS readout exists to show — would
 * be invisible to a poll, and two clicks inside one tick would count as one.
 *
 * <p>Read-only. It observes presses the player already made, never synthesises
 * input, never cancels, and never changes what the button does. An autoclicker
 * and a click counter hook the same method; the difference between them is
 * exactly that.
 *
 * <h2>What 26.x changed</h2>
 *
 * The class is {@code MouseHandler} rather than {@code Mouse}, and the callback
 * is {@code onButton(long, MouseButtonInfo, int)} — the loose button and
 * modifier ints are now a record, the same consolidation 1.21.9 started. The
 * method is private, which is no obstacle: a mixin rewrites the method body
 * rather than calling it.
 */
@Mixin(MouseHandler.class)
public class MouseClickMixin {

    @Inject(method = "onButton", at = @At("HEAD"))
    private void blurred$countClick(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        // Only a press counts. Without this the release comes through the same
        // callback and every click registers twice, showing double the rate.
        if (action != GLFW.GLFW_PRESS) {
            return;
        }
        int button = info.button();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            CpsTracker.onLeftClick();
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            CpsTracker.onRightClick();
        }
    }
}
