package dev.blurredclient.mod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.blurredclient.mod.fx.ItemPhysics;
import dev.blurredclient.mod.fx.ItemPhysicsTargets;
import dev.blurredclient.mod.module.Modules;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import dev.blurredclient.mod.fx.ItemPhysicsState;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Quaternionfc;

/**
 * Item physics: dropped items tumble while airborne and roll into a resting
 * pose when they land, with no bobbing or idle spinning. The motion itself
 * is {@link ItemPhysics}; this applies it.
 *
 * <p>Every version's {@code render} does the same four things, javap-checked:
 * a {@code MathHelper.sin} bob, a {@code translate} up by the bob plus half
 * the model's height, {@code ItemEntity.getRotation} for the spin, and a
 * {@code multiply} by that rotation. The bob goes to zero, the spin becomes a
 * fixed heading, and after the heading the item is turned by the physics
 * angle; the lift blends toward lying on the ground as the item settles, so
 * nothing jumps when it lands.
 */
@Mixin(ItemEntityRenderer.class)
public class ItemPhysicsMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
            at = @At("TAIL"))
    private void blurred$pose(ItemEntity entity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (!Modules.ITEM_PHYSICS.isActive()) {
            return;
        }
        boolean flat = blurred$isFlat(state);
        ItemPhysics.Pose pose = ItemPhysics.update(entity, flat);
        ((ItemPhysicsState) state).blurred$setPose(pose.angle(), pose.settle(), flat);
    }

    // ---- the bob: sin(...) * 0.1 + 0.1, so -1 makes it 0 ----

    @WrapOperation(method = ItemPhysicsTargets.RENDER,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(D)F"))
    private float blurred$noBob(double v, Operation<Float> op) {
        return Modules.ITEM_PHYSICS.isActive() ? -1f : op.call(v);
    }

    // ---- the spin: a fixed heading per item instead ----

    @WrapOperation(method = ItemPhysicsTargets.RENDER,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;getSpin(FF)F"))
    private float blurred$heading(float age, float offset, Operation<Float> op) {
        return Modules.ITEM_PHYSICS.isActive() ? offset : op.call(age, offset);
    }

    // ---- the lift: blends toward lying on the ground as a flat item settles ----

    @WrapOperation(method = ItemPhysicsTargets.RENDER,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
    private void blurred$rest(PoseStack matrices, float x, float y, float z, Operation<Void> op,
                              @Local(argsOnly = true) ItemEntityRenderState subject) {
        if (!Modules.ITEM_PHYSICS.isActive() || !blurred$flat(subject)) {
            op.call(matrices, x, y, z);
            return;
        }
        float s = blurred$settle(subject);
        op.call(matrices, x, y + (blurred$restHeight(subject) - y) * s, z);
    }

    // ---- after the heading: the physics angle ----

    @WrapOperation(method = ItemPhysicsTargets.RENDER,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V", ordinal = 0))
    private void blurred$turn(PoseStack matrices, Quaternionfc heading, Operation<Void> op,
                              @Local(argsOnly = true) ItemEntityRenderState subject) {
        op.call(matrices, heading);
        if (!Modules.ITEM_PHYSICS.isActive()) {
            return;
        }
        if (blurred$flat(subject)) {
            // The model's middle isn't at its origin; re-centre as it lies down.
            matrices.translate(0f, 0f, blurred$centreShift(subject) * blurred$settle(subject));
            matrices.mulPose(Axis.XP.rotationDegrees(blurred$angle(subject)));
        } else {
            // Blocks turn about their own middle, so resting on any side keeps them above ground.
            float pivot = blurred$blockMiddle(subject);
            matrices.translate(0f, pivot, 0f);
            matrices.mulPose(Axis.XP.rotationDegrees(blurred$angle(subject)));
            matrices.translate(0f, -pivot, 0f);
        }
    }

    // ---- per-version facts about the item being drawn ----

    private static float blurred$angle(ItemEntityRenderState s) {
        return ((ItemPhysicsState) s).blurred$angle();
    }

    private static float blurred$settle(ItemEntityRenderState s) {
        return ((ItemPhysicsState) s).blurred$settle();
    }

    private static boolean blurred$flat(ItemEntityRenderState s) {
        return ((ItemPhysicsState) s).blurred$flat();
    }

    private static boolean blurred$isFlat(ItemEntityRenderState s) {
        // A flat sprite's model is a sixteenth of a block deep; a block's is far deeper.
        return s.item.getModelBoundingBox().getZsize() < 0.1;
    }

    private static float blurred$restHeight(ItemEntityRenderState s) {
        return (float) -s.item.getModelBoundingBox().minZ + 0.005f;
    }

    private static float blurred$centreShift(ItemEntityRenderState s) {
        var box = s.item.getModelBoundingBox();
        return (float) ((box.minY + box.maxY) / 2);
    }

    private static float blurred$blockMiddle(ItemEntityRenderState s) {
        return blurred$centreShift(s);
    }
}
