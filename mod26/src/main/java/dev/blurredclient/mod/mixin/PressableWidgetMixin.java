package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.social.Essential;
import dev.blurredclient.mod.ui.OceanUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.ImageButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws vanilla buttons in the ocean style.
 *
 * <p>Targets {@link AbstractButton} rather than {@code Button}: it's the
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
 *   <li><b>Widgets that aren't plain buttons.</b> A voice-chat mod's mute and
 *       deafen buttons are {@code AbstractButton} subclasses that render their
 *       own icons; styling them threw the icon away and drew the accessibility
 *       label in its place.
 *       <p>This started as "nothing outside {@code net.minecraft}", which was
 *       too blunt in the visible direction: Mod Menu's <em>Mods</em> button sits
 *       in the middle of the title screen looking like stock vanilla grey while
 *       everything around it is ocean blue, which is worse than either
 *       extreme. The test is now whether it's a {@link Button} — the
 *       class mods use when they want a normal button — with vanilla's own
 *       widgets still allowed on top of that, so this can only ever style more
 *       than before, never less.</li>
 *   <li><b>Container screens.</b> Inventory, crafting, chests and every modded
 *       GUI built on {@link AbstractContainerScreen}. Their buttons sit on top of a
 *       texture, next to item slots, and are almost all icons — the recipe
 *       book toggle being the obvious one. Painting panels over that is what
 *       made the crafting screen look broken.</li>
 *   <li><b>Icon buttons generally.</b> Anything textured, or small and square,
 *       or with no label. There's nothing to draw but a picture we'd be
 *       covering.</li>
 * </ul>
 */
@Mixin(AbstractButton.class)
public class PressableWidgetMixin {

    /** Widgets at or under this size are icons, not labelled buttons. */
    private static final int ICON_SIZE = 22;

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void blurred$oceanButton(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!Essential.shouldStyleMenus()) {
            return;
        }

        AbstractButton self = (AbstractButton) (Object) this;
        if (!self.visible || !blurred$shouldStyle(self)) {
            return;
        }

        OceanUi.button(
                ctx,
                Minecraft.getInstance().font,
                self.getMessage(),
                self.getX(), self.getY(), self.getWidth(), self.getHeight(),
                self.isHovered(), self.active, blurred$isOurScreen());
        ci.cancel();
    }

    /**
     * Is the screen on show one of ours?
     *
     * <p>Decides whether a button gets rounded corners. Vanilla's menus are
     * square throughout, and a single rounded button among them reads as a
     * rendering fault rather than as styling — so the curve is reserved for the
     * screens the mod draws itself, where everything is rounded and it looks
     * deliberate.
     */
    private static boolean blurred$isOurScreen() {
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen != null && screen.getClass().getName().startsWith("dev.blurredclient.mod.screen");
    }

    private static boolean blurred$shouldStyle(AbstractButton self) {
        // Vanilla's own widgets, plus anyone's Button. A mod that wants a
        // normal button builds a Button; a mod that wants to draw
        // something of its own subclasses further, and lands in the icon tests
        // below. The vanilla-package clause is kept as well as, not instead of,
        // this — so relaxing the rule cannot un-style anything that used to be
        // styled.
        String owner = self.getClass().getName();
        if (!owner.startsWith("net.minecraft.") && !(self instanceof Button)) {
            return false;
        }

        // Vanilla's own icon buttons: 20x20 sprites whose getMessage() is the
        // full accessible label ("Accessibility Settings..."). Styling those
        // drew that whole string inside a 20px box.
        if (self instanceof ImageButton) {
            return false;
        }
        if (self.getWidth() <= ICON_SIZE && self.getHeight() <= ICON_SIZE) {
            return false;
        }
        if (self.getMessage() == null || self.getMessage().getString().isBlank()) {
            return false;
        }

        // Container GUIs are gameplay surfaces, not menus.
        Screen screen = Minecraft.getInstance().gui.screen();
        return !(screen instanceof AbstractContainerScreen<?>);
    }
}
