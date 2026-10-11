package dev.blurredclient.mod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.blurredclient.mod.fx.ItemPhysics;
import dev.blurredclient.mod.fx.ItemPhysicsTargets;
import dev.blurredclient.mod.module.Modules;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >=1.21.4 {
import dev.blurredclient.mod.fx.ItemPhysicsState;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?} else {
/*import net.minecraft.client.MinecraftClient;
*///?}
//? if >=1.21.8 {
import org.joml.Quaternionfc;
//?} else {
/*import org.joml.Quaternionf;
*///?}

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

    //? if >=1.21.4 {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V",
            at = @At("TAIL"))
    private void blurred$pose(ItemEntity entity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (!Modules.ITEM_PHYSICS.isActive()) {
            return;
        }
        boolean flat = blurred$isFlat(state);
        ItemPhysics.Pose pose = ItemPhysics.update(entity, flat);
        ((ItemPhysicsState) state).blurred$setPose(pose.angle(), pose.settle(), flat);
    }
    //?}

    // ---- the bob: sin(...) * 0.1 + 0.1, so -1 makes it 0 ----

    //? if >=1.21.11 {
    @WrapOperation(method = ItemPhysicsTargets.RENDER,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;sin(D)F"))
    private float blurred$noBob(double v, Operation<Float> op) {
        return Modules.ITEM_PHYSICS.isActive() ? -1f : op.call(v);
    }
    //?} else {
    /*@WrapOperation(method = ItemPhysicsTargets.RENDER,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;sin(F)F"))
    private float blurred$noBob(float v, Operation<Float> op) {
        return Modules.ITEM_PHYSICS.isActive() ? -1f : op.call(v);
    }
    *///?}

    // ---- the spin: a fixed heading per item instead ----

    //? if >=1.21.4 {
    @WrapOperation(method = ItemPhysicsTargets.RENDER,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ItemEntity;getRotation(FF)F"))
    private float blurred$heading(float age, float offset, Operation<Float> op) {
        return Modules.ITEM_PHYSICS.isActive() ? offset : op.call(age, offset);
    }
    //?} else {
    /*@WrapOperation(method = ItemPhysicsTargets.RENDER,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ItemEntity;getRotation(F)F"))
    private float blurred$heading(ItemEntity entity, float tickDelta, Operation<Float> op) {
        return Modules.ITEM_PHYSICS.isActive() ? entity.uniqueOffset : op.call(entity, tickDelta);
    }
    *///?}

    // ---- the lift: blends toward lying on the ground as a flat item settles ----

    @WrapOperation(method = ItemPhysicsTargets.RENDER,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V", ordinal = 0))
    private void blurred$rest(MatrixStack matrices, float x, float y, float z, Operation<Void> op,
                              //? if >=1.21.4 {
                              @Local(argsOnly = true) ItemEntityRenderState subject) {
                              //?} else {
                              /*@Local(argsOnly = true) ItemEntity subject) {
                              *///?}
        if (!Modules.ITEM_PHYSICS.isActive() || !blurred$flat(subject)) {
            op.call(matrices, x, y, z);
            return;
        }
        float s = blurred$settle(subject);
        op.call(matrices, x, y + (blurred$restHeight(subject) - y) * s, z);
    }

    // ---- after the heading: the physics angle ----

    //? if >=1.21.8 {
    @WrapOperation(method = ItemPhysicsTargets.RENDER,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionfc;)V", ordinal = 0))
    private void blurred$turn(MatrixStack matrices, Quaternionfc heading, Operation<Void> op,
    //?} else {
    /*@WrapOperation(method = ItemPhysicsTargets.RENDER,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionf;)V", ordinal = 0))
    private void blurred$turn(MatrixStack matrices, Quaternionf heading, Operation<Void> op,
    *///?}
                              //? if >=1.21.4 {
                              @Local(argsOnly = true) ItemEntityRenderState subject) {
                              //?} else {
                              /*@Local(argsOnly = true) ItemEntity subject) {
                              *///?}
        op.call(matrices, heading);
        if (!Modules.ITEM_PHYSICS.isActive()) {
            return;
        }
        if (blurred$flat(subject)) {
            // The model's middle isn't at its origin; re-centre as it lies down.
            matrices.translate(0f, 0f, blurred$centreShift(subject) * blurred$settle(subject));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(blurred$angle(subject)));
        } else {
            // Blocks turn about their own middle, so resting on any side keeps them above ground.
            float pivot = blurred$blockMiddle(subject);
            matrices.translate(0f, pivot, 0f);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(blurred$angle(subject)));
            matrices.translate(0f, -pivot, 0f);
        }
    }

    // ---- per-version facts about the item being drawn ----

    //? if >=1.21.4 {
    private static float blurred$angle(ItemEntityRenderState s) {
        return ((ItemPhysicsState) s).blurred$angle();
    }

    private static float blurred$settle(ItemEntityRenderState s) {
        return ((ItemPhysicsState) s).blurred$settle();
    }

    private static boolean blurred$flat(ItemEntityRenderState s) {
        return ((ItemPhysicsState) s).blurred$flat();
    }
    //?} else {
    /*private static float blurred$angle(ItemEntity e) {
        return blurred$pose(e).angle();
    }

    private static float blurred$settle(ItemEntity e) {
        return blurred$pose(e).settle();
    }

    private static boolean blurred$flat(ItemEntity e) {
        return blurred$isFlat(e);
    }

    // render calls translate before multiply; compute the pose once per frame
    // (on the translate) and reuse it for the multiply.
    private static ItemEntity blurred$last;
    private static ItemPhysics.Pose blurred$lastPose;
    private static long blurred$lastFrame = -1;

    private static ItemPhysics.Pose blurred$pose(ItemEntity e) {
        long frame = dev.blurredclient.mod.hud.HudRenderer.frame();
        if (e != blurred$last || frame != blurred$lastFrame) {
            blurred$last = e;
            blurred$lastFrame = frame;
            blurred$lastPose = ItemPhysics.update(e, blurred$isFlat(e));
        }
        return blurred$lastPose;
    }
    *///?}

    //? if >=1.21.8 {
    private static boolean blurred$isFlat(ItemEntityRenderState s) {
        // A flat sprite's model is a sixteenth of a block deep; a block's is far deeper.
        return s.itemRenderState.getModelBoundingBox().getLengthZ() < 0.1;
    }

    private static float blurred$restHeight(ItemEntityRenderState s) {
        return (float) -s.itemRenderState.getModelBoundingBox().minZ + 0.005f;
    }

    private static float blurred$centreShift(ItemEntityRenderState s) {
        var box = s.itemRenderState.getModelBoundingBox();
        return (float) ((box.minY + box.maxY) / 2);
    }

    private static float blurred$blockMiddle(ItemEntityRenderState s) {
        return blurred$centreShift(s);
    }
    //?} elif >=1.21.4 {
    /*private static boolean blurred$isFlat(ItemEntityRenderState s) {
        return !s.itemRenderState.hasDepth();
    }

    // A block on the ground sits 3/16 up in its ground transform (vanilla block model display).
    private static float blurred$blockMiddle(ItemEntityRenderState s) {
        return 0.1875f;
    }

    private static float blurred$restHeight(ItemEntityRenderState s) {
        return 0.02f;
    }

    private static float blurred$centreShift(ItemEntityRenderState s) {
        return 0f;
    }
    *///?} else {
    /*private static float blurred$blockMiddle(ItemEntity e) {
        return 0.1875f;
    }

    private static boolean blurred$isFlat(ItemEntity e) {
        var client = MinecraftClient.getInstance();
        return !client.getItemRenderer().getModel(e.getStack(), e.getWorld(), null, e.getId()).hasDepth();
    }

    private static float blurred$restHeight(ItemEntity e) {
        return 0.02f;
    }

    private static float blurred$centreShift(ItemEntity e) {
        return 0f;
    }
    *///?}
}
