package dev.blurredclient.mod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.blurredclient.mod.social.Essential;
import dev.blurredclient.mod.ui.kit.Skins;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Text boxes everywhere (chat, signs, command blocks, the world name...) drawn
 * in the active style instead of vanilla's texture.
 *
 * <p>The background is one {@code drawGuiTexture} call whose signature changed
 * twice (plain; a render-layer function in 1.21.4; a render pipeline from
 * 1.21.8 — javap-checked), so the wrapper is branched; the text, caret and
 * selection are still drawn by vanilla on top.
 */
@Mixin(TextFieldWidget.class)
public class TextFieldStyleMixin {

    private boolean blurred$style() {
        TextFieldWidget self = (TextFieldWidget) (Object) this;
        return Essential.shouldStyleMenus() && self.getClass().getName().startsWith("net.minecraft.");
    }

    private void blurred$draw(DrawContext ctx, int x, int y, int w, int h) {
        TextFieldWidget self = (TextFieldWidget) (Object) this;
        Skins.current().field(ctx, x, y, w, h, self.isHovered() ? 1f : 0f, self.isFocused());
    }

    //? if >=1.21.8 {
    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V"))
    private void blurred$field(DrawContext ctx, com.mojang.blaze3d.pipeline.RenderPipeline pipeline, Identifier tex,
                               int x, int y, int w, int h, Operation<Void> original) {
        if (blurred$style()) {
            blurred$draw(ctx, x, y, w, h);
        } else {
            original.call(ctx, pipeline, tex, x, y, w, h);
        }
    }
    //?} elif >=1.21.4 {
    /*@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIII)V"))
    private void blurred$field(DrawContext ctx, java.util.function.Function<?, ?> layers, Identifier tex,
                               int x, int y, int w, int h, Operation<Void> original) {
        if (blurred$style()) {
            blurred$draw(ctx, x, y, w, h);
        } else {
            original.call(ctx, layers, tex, x, y, w, h);
        }
    }
    *///?} else {
    /*@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V"))
    private void blurred$field(DrawContext ctx, Identifier tex, int x, int y, int w, int h, Operation<Void> original) {
        if (blurred$style()) {
            blurred$draw(ctx, x, y, w, h);
        } else {
            original.call(ctx, tex, x, y, w, h);
        }
    }
    *///?}
}
