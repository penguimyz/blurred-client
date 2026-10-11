package dev.blurredclient.mod.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.ui.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
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
@Mixin(ChatComponent.class)
public class ChatHudMixin {
    @Unique
    private boolean blurred$pushed;

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("TAIL"))
    private void blurred$messageAdded(CallbackInfo ci) {
        Modules.CHAT_ANIMATIONS.messageAdded();
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("HEAD"))
    private void blurred$slideStart(CallbackInfo ci, @Local(argsOnly = true) GuiGraphicsExtractor ctx) {
        float offset = Modules.CHAT_ANIMATIONS.offset();
        blurred$pushed = offset > 0.01f;
        if (blurred$pushed) {
            Draw.push(ctx);
            Draw.translate(ctx, 0f, offset);
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("RETURN"))
    private void blurred$slideEnd(CallbackInfo ci, @Local(argsOnly = true) GuiGraphicsExtractor ctx) {
        if (blurred$pushed) {
            Draw.pop(ctx);
            blurred$pushed = false;
        }
    }

    @ModifyConstant(method = {"addMessageToQueue(Lnet/minecraft/client/GuiMessage;)V", "addMessageToDisplayQueue"},
            constant = @Constant(intValue = 100), require = 0)
    private int blurred$history(int vanilla) {
        return Modules.LONG_CHAT.limit(vanilla);
    }
}
