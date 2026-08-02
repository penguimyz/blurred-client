package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.config.BlurredConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shows the real latency in the tab list instead of the five-bar signal icon.
 *
 * <p>The bars quantise everything into five buckets, so 30ms and 140ms look
 * identical at four bars — which is most of the range anyone actually cares
 * about. A number is strictly more information in the same space.
 *
 * <p>Right-aligned to where the icon used to sit, so column widths are
 * unchanged and the list doesn't reflow.
 */
@Mixin(PlayerListHud.class)
public class PlayerListPingMixin {

    @Inject(method = "renderLatencyIcon", at = @At("HEAD"), cancellable = true)
    private void blurred$drawPingNumber(
            DrawContext ctx, int width, int x, int y, PlayerListEntry entry, CallbackInfo ci) {

        if (!BlurredConfig.get().numericPing) {
            return;
        }

        int latency = entry.getLatency();
        MinecraftClient client = MinecraftClient.getInstance();

        // A negative latency means the server never reported one — same case
        // vanilla draws its "unknown" icon for.
        Text label = Text.literal(latency < 0 ? "--" : Integer.toString(latency));

        int color = latency < 0 ? 0xFF6E93A2
                : latency < 60 ? 0xFF45E6A8
                : latency < 120 ? 0xFF9BE86B
                : latency < 200 ? 0xFFFFC75A
                : latency < 320 ? 0xFFFF9A4A
                : 0xFFFF6B81;

        // Right-align inside the slot the icon occupied (vanilla insets by ~11px).
        int textWidth = client.textRenderer.getWidth(label);
        ctx.drawTextWithShadow(client.textRenderer, label, x + width - textWidth - 1, y, color);

        ci.cancel();
    }
}
