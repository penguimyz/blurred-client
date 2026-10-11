package dev.blurredclient.mod.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.blurredclient.mod.fx.ContainerView;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The container's own slots live in the world while the in-chest view is
 * open: the screen skips drawing them, and finding "the slot under the
 * mouse" asks {@link ContainerView} where each one is on screen. Everything
 * else — clicking, dragging, shift-clicking, tooltips — is vanilla's.
 *
 * <p>Matched by name with {@code @Local} for the parameters that moved
 * between versions ({@code drawSlot} gained mouse coordinates in 1.21.11,
 * {@code isClickOutsideBounds} lost an argument in 1.21.10).
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {
    @Shadow
    protected Slot hoveredSlot;

    @Inject(method = "getHoveredSlot", at = @At("HEAD"), cancellable = true)
    private void blurred$slotInWorld(double x, double y, CallbackInfoReturnable<Slot> cir) {
        Slot s = ContainerView.INSTANCE.slotAt((AbstractContainerScreen<?>) (Object) this, x, y);
        if (s != null) {
            cir.setReturnValue(s);
        }
    }

    @Inject(method = "getHoveredSlot", at = @At("RETURN"), cancellable = true)
    private void blurred$noHiddenSlots(double x, double y, CallbackInfoReturnable<Slot> cir) {
        Slot s = cir.getReturnValue();
        // The container's 2D slots aren't drawn; don't let them be clicked where they used to be.
        if (s != null && ContainerView.INSTANCE.inWorld(s)
                && ContainerView.INSTANCE.slotAt((AbstractContainerScreen<?>) (Object) this, x, y) != s) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void blurred$gridIsInside(CallbackInfoReturnable<Boolean> cir,
                                      @Local(argsOnly = true, ordinal = 0) double x,
                                      @Local(argsOnly = true, ordinal = 1) double y) {
        if (ContainerView.INSTANCE.overGrid((AbstractContainerScreen<?>) (Object) this, x, y)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
    private void blurred$skipWorldSlot(CallbackInfo ci, @Local(argsOnly = true) Slot slot) {
        if (ContainerView.INSTANCE.inWorld(slot)) {
            ci.cancel();
        }
    }

    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    private void blurred$noLabels(CallbackInfo ci) {
        if (ContainerView.INSTANCE.active((AbstractContainerScreen<?>) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = {"extractSlotHighlightBack", "extractSlotHighlightFront"}, at = @At("HEAD"), cancellable = true)
    private void blurred$noHighlight(CallbackInfo ci) {
        if (hoveredSlot != null && ContainerView.INSTANCE.inWorld(hoveredSlot)) {
            ci.cancel();
        }
    }
}
