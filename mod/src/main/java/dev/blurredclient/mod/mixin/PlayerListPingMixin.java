package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.Theme;
import dev.blurredclient.mod.config.BlurredConfig;
import dev.blurredclient.mod.social.BlurredUsers;
import dev.blurredclient.mod.ui.BlurredFont;
import dev.blurredclient.mod.ui.Draw;
import dev.blurredclient.mod.hud.HudRenderer;
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
 * The right-hand end of each tab list row: who's on Blurred, and their real
 * latency.
 *
 * <p><b>Ping as a number.</b> The bars quantise everything into five buckets,
 * so 30ms and 140ms look identical at four bars — which is most of the range
 * anyone actually cares about. A number is strictly more information in the
 * same space.
 *
 * <p>It's drawn small, with its unit, hard against the right edge of the row.
 * At full font size a three-digit latency is nearly as wide as a short player
 * name, which made the tab list read as two columns of equal weight when only
 * one of them is the point; and a bare number with no unit could as easily have
 * been a score. Three colours carry the meaning at a glance — blue is fine,
 * yellow is noticeable, red is a problem — so the digits themselves only matter
 * once you've decided to look.
 *
 * <p><b>The client badge.</b> Other Blurred players get the porthole mark
 * beside their latency. Drawn rather than textured, for the same reason
 * everything else here is: no atlas to bind, and a 7px mark stays crisp at any
 * GUI scale where a downscaled logo would turn to mush.
 *
 * <p>Both are right-aligned into the slot the vanilla icon occupied, so column
 * widths are unchanged and the list doesn't reflow.
 */
@Mixin(PlayerListHud.class)
public class PlayerListPingMixin {

    /** Latency text size, as a fraction of the game font. */
    private static final float BLURRED$SCALE = 0.7f;

    /** Clear water. Anything under this plays as if it were local. */
    private static final int BLURRED$GOOD = 0xFF5AC8FF;

    @Inject(method = "renderLatencyIcon", at = @At("HEAD"), cancellable = true)
    private void blurred$drawPingNumber(
            DrawContext ctx, int width, int x, int y, PlayerListEntry entry, CallbackInfo ci) {

        BlurredConfig cfg = BlurredConfig.get();
        MinecraftClient client = MinecraftClient.getInstance();

        boolean badge = cfg.showClientBadge && BlurredUsers.isBlurredUser(blurred$nameOf(entry));

        if (!cfg.numericPing) {
            // Still badge them, then let vanilla draw its bars where it wants.
            if (badge) {
                HudRenderer.porthole(ctx, x + width - 15, y + 4, 3);
            }
            return;
        }

        int latency = entry.getLatency();

        // A negative latency means the server never reported one — same case
        // vanilla draws its "unknown" icon for.
        Text label = BlurredFont.of(latency < 0 ? "--" : latency + "ms");

        int color = latency < 0 ? Theme.TEXT_FAINT
                : latency < 120 ? BLURRED$GOOD
                : latency < 250 ? Theme.WARNING
                : Theme.DANGER;

        // Flush with the right edge of the row — the same edge vanilla's icon
        // ends on, rather than a pixel short of it. Being both smaller and hard
        // right, the number now sits well clear of the player's name instead of
        // reading as a second column beside it.
        int textWidth = Draw.width(client.textRenderer, label, BLURRED$SCALE);
        int textX = x + width - textWidth;
        // Centre the shrunken line in the 8px row the icon would have filled.
        int textY = y + Math.round((8f - 8f * BLURRED$SCALE) / 2f);
        Draw.text(ctx, client.textRenderer, label, textX, textY, color, BLURRED$SCALE);

        if (badge) {
            // Just left of the number, with a pixel of air either side.
            HudRenderer.porthole(ctx, textX - 5, y + 4, 3);
        }

        ci.cancel();
    }

    /**
     * The account name on a tab list entry.
     *
     * <p>Mojang's {@code GameProfile} became a record in the authlib that ships
     * with 1.21.9, so the accessor lost its {@code get} prefix.
     */
    private static String blurred$nameOf(PlayerListEntry entry) {
        if (entry.getProfile() == null) {
            return "";
        }
        //? if >=1.21.10 {
        return entry.getProfile().name();
        //?} else {
        /*return entry.getProfile().getName();
         *///?}
    }
}
