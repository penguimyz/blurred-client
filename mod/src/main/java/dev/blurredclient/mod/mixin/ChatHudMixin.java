package dev.blurredclient.mod.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.ui.Draw;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chat animations (draw the chat a little lower right after a message
 * arrives, easing up into place) and longer history (the 100-line limit is
 * a constant in both add methods on every version, javap-checked).
 * {@code render} gained a text-renderer argument in 1.21.11.
 */
@Mixin(ChatHud.class)
public class ChatHudMixin {
    @Unique
    private boolean blurred$pushed;

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("TAIL"))
    private void blurred$messageAdded(CallbackInfo ci) {
        Modules.CHAT_ANIMATIONS.messageAdded();
    }

    //? if >=1.21.11 {
    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIIZ)V", at = @At("HEAD"))
    *///?}
    private void blurred$slideStart(CallbackInfo ci, @Local(argsOnly = true) DrawContext ctx) {
        float offset = Modules.CHAT_ANIMATIONS.offset();
        blurred$pushed = offset > 0.01f;
        if (blurred$pushed) {
            Draw.push(ctx);
            Draw.translate(ctx, 0f, offset);
        }
    }

    //? if >=1.21.11 {
    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V", at = @At("RETURN"))
    //?} else {
    /*@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIIZ)V", at = @At("RETURN"))
    *///?}
    private void blurred$slideEnd(CallbackInfo ci, @Local(argsOnly = true) DrawContext ctx) {
        if (blurred$pushed) {
            Draw.pop(ctx);
            blurred$pushed = false;
        }
    }

    @ModifyConstant(method = {"addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V", "addVisibleMessage"},
            constant = @Constant(intValue = 100), require = 0)
    private int blurred$history(int vanilla) {
        return Modules.LONG_CHAT.limit(vanilla);
    }
}
