package dev.blurredclient.mod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.blurredclient.mod.social.Essential;
import dev.blurredclient.mod.ui.kit.Skins;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.Identifier;
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
@Mixin(EditBox.class)
public class TextFieldStyleMixin {

    private boolean blurred$style() {
        EditBox self = (EditBox) (Object) this;
        return Essential.shouldStyleMenus() && self.getClass().getName().startsWith("net.minecraft.");
    }

    private void blurred$draw(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        EditBox self = (EditBox) (Object) this;
        Skins.current().field(ctx, x, y, w, h, self.isHovered() ? 1f : 0f, self.isFocused());
    }

    @WrapOperation(method = "extractWidgetRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void blurred$field(GuiGraphicsExtractor ctx, com.mojang.blaze3d.pipeline.RenderPipeline pipeline, Identifier tex,
                               int x, int y, int w, int h, Operation<Void> original) {
        if (blurred$style()) {
            blurred$draw(ctx, x, y, w, h);
        } else {
            original.call(ctx, pipeline, tex, x, y, w, h);
        }
    }
}
