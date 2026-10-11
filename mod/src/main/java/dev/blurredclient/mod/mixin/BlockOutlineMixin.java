package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.module.Modules;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >=1.21.4 {
import org.spongepowered.asm.mixin.injection.ModifyVariable;
//?} else {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.shape.VoxelShape;
*///?}

/**
 * Block outline colour. From 1.21.4 {@code drawBlockOutline} takes the colour
 * as its one int argument (javap-checked on 1.21.4-1.21.11); 1.21.1 passes
 * black at 40% as four floats to {@code drawCuboidShapeOutline}.
 */
@Mixin(WorldRenderer.class)
public class BlockOutlineMixin {
    //? if >=1.21.4 {
    @ModifyVariable(method = "drawBlockOutline", at = @At("HEAD"), argsOnly = true)
    private int blurred$outlineColour(int colour) {
        return Modules.BLOCK_OUTLINE.colour(colour);
    }
    //?} else {
    /*@WrapOperation(method = "drawBlockOutline", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;drawCuboidShapeOutline(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/util/shape/VoxelShape;DDDFFFF)V"))
    private void blurred$outlineColour(MatrixStack matrices, VertexConsumer lines, VoxelShape shape,
                                       double x, double y, double z, float r, float g, float b, float a,
                                       Operation<Void> op) {
        if (!Modules.BLOCK_OUTLINE.isActive()) {
            op.call(matrices, lines, shape, x, y, z, r, g, b, a);
            return;
        }
        int c = Modules.BLOCK_OUTLINE.colour(0x66000000);
        op.call(matrices, lines, shape, x, y, z, ((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f,
                (c & 0xFF) / 255f, (c >>> 24) / 255f);
    }
    *///?}
}
