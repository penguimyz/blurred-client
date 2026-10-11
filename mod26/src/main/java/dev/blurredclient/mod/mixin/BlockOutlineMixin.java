package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.module.Modules;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Block outline colour. From 1.21.4 {@code drawBlockOutline} takes the colour
 * as its one int argument (javap-checked on 1.21.4-1.21.11); 1.21.1 passes
 * black at 40% as four floats to {@code drawCuboidShapeOutline}.
 */
@Mixin(LevelRenderer.class)
public class BlockOutlineMixin {
    @ModifyVariable(method = "submitHitOutline", at = @At("HEAD"), argsOnly = true)
    private int blurred$outlineColour(int colour) {
        return Modules.BLOCK_OUTLINE.colour(colour);
    }
}
