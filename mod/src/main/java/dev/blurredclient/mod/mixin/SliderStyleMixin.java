package dev.blurredclient.mod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.blurredclient.mod.social.Essential;
import dev.blurredclient.mod.ui.kit.Skins;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sliders (FOV, volume, render distance...) in the active style.
 *
 * <p>Vanilla draws a track texture and then a handle texture; the first call
 * is replaced by the style's slider (track, fill and knob) and the second is
 * skipped. The label is still drawn by vanilla. Same three-era branching as
 * {@link TextFieldStyleMixin}; from 1.21.4 the call carries a colour argument.
 */
@Mixin(SliderWidget.class)
public abstract class SliderStyleMixin {
    @Shadow
    protected double value;

    private boolean blurred$style() {
        SliderWidget self = (SliderWidget) (Object) this;
        return Essential.shouldStyleMenus() && self.getClass().getName().startsWith("net.minecraft.");
    }

    /** True for the handle call: vanilla's handle is 8 wide, the track is the widget's width. */
    private boolean blurred$isHandle(int w) {
        return w <= 8 && ((SliderWidget) (Object) this).getWidth() > 8;
    }

    private void blurred$draw(DrawContext ctx, int x, int y, int w, int h) {
        SliderWidget self = (SliderWidget) (Object) this;
        // Track down the middle third, so vanilla's label still reads on top.
        Skins.current().field(ctx, x, y, w, h, self.isHovered() ? 1f : 0f, self.isFocused());
        int inset = 4;
        Skins.current().slider(ctx, x + inset, y + h - 6, w - inset * 2, 4, (float) value,
                self.isHovered() ? 1f : 0f, false);
    }

    //? if >=1.21.8 {
    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIII)V"))
    private void blurred$slider(DrawContext ctx, com.mojang.blaze3d.pipeline.RenderPipeline pipeline, Identifier tex,
                                int x, int y, int w, int h, int color, Operation<Void> original) {
        if (!blurred$style()) {
            original.call(ctx, pipeline, tex, x, y, w, h, color);
        } else if (!blurred$isHandle(w)) {
            blurred$draw(ctx, x, y, w, h);
        }
    }
    //?} elif >=1.21.4 {
    /*@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIIII)V"))
    private void blurred$slider(DrawContext ctx, java.util.function.Function<?, ?> layers, Identifier tex,
                                int x, int y, int w, int h, int color, Operation<Void> original) {
        if (!blurred$style()) {
            original.call(ctx, layers, tex, x, y, w, h, color);
        } else if (!blurred$isHandle(w)) {
            blurred$draw(ctx, x, y, w, h);
        }
    }
    *///?} else {
    /*@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V"))
    private void blurred$slider(DrawContext ctx, Identifier tex, int x, int y, int w, int h, Operation<Void> original) {
        if (!blurred$style()) {
            original.call(ctx, tex, x, y, w, h);
        } else if (!blurred$isHandle(w)) {
            blurred$draw(ctx, x, y, w, h);
        }
    }
    *///?}
}
