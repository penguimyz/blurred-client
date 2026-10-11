package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.module.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws your hand holding the item while Inspect plays. Vanilla hides the
 * arm whenever you hold something; this draws it with vanilla's own arm
 * renderer ({@code renderArm}, the one used for maps), moved so its hand
 * closes on the item's grip.
 *
 * <p>{@code renderItem}'s render-target and display-context parameter types
 * changed across versions (javap-checked), hence one injector per version.
 */
@Mixin(HeldItemRenderer.class)
public abstract class InspectHandMixin {
    //? if >=1.21.10 {
    @Shadow
    protected abstract void renderArm(MatrixStack matrices, net.minecraft.client.render.command.OrderedRenderCommandQueue queue, int light, Arm arm);

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("HEAD"))
    private void blurred$inspectHand(LivingEntity entity, ItemStack stack, net.minecraft.item.ItemDisplayContext context,
                                     MatrixStack matrices, net.minecraft.client.render.command.OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        Arm arm = arm(entity, stack, context == net.minecraft.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                context == net.minecraft.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
        if (arm != null) {
            matrices.push();
            Modules.INSPECT.placeHand(matrices, arm == Arm.RIGHT ? 1 : -1);
            renderArm(matrices, queue, light, arm);
            matrices.pop();
        }
    }
    //?} elif >=1.21.8 {
    /*@Shadow
    protected abstract void renderArm(MatrixStack matrices, net.minecraft.client.render.VertexConsumerProvider consumers, int light, Arm arm);

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void blurred$inspectHand(LivingEntity entity, ItemStack stack, net.minecraft.item.ItemDisplayContext context,
                                     MatrixStack matrices, net.minecraft.client.render.VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        Arm arm = arm(entity, stack, context == net.minecraft.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                context == net.minecraft.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
        if (arm != null) {
            matrices.push();
            Modules.INSPECT.placeHand(matrices, arm == Arm.RIGHT ? 1 : -1);
            renderArm(matrices, consumers, light, arm);
            matrices.pop();
        }
    }
    *///?} elif >=1.21.4 {
    /*@Shadow
    protected abstract void renderArm(MatrixStack matrices, net.minecraft.client.render.VertexConsumerProvider consumers, int light, Arm arm);

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void blurred$inspectHand(LivingEntity entity, ItemStack stack, net.minecraft.item.ModelTransformationMode context, boolean leftHanded,
                                     MatrixStack matrices, net.minecraft.client.render.VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        Arm arm = arm(entity, stack, context == net.minecraft.item.ModelTransformationMode.FIRST_PERSON_RIGHT_HAND,
                context == net.minecraft.item.ModelTransformationMode.FIRST_PERSON_LEFT_HAND);
        if (arm != null) {
            matrices.push();
            Modules.INSPECT.placeHand(matrices, arm == Arm.RIGHT ? 1 : -1);
            renderArm(matrices, consumers, light, arm);
            matrices.pop();
        }
    }
    *///?} else {
    /*@Shadow
    protected abstract void renderArm(MatrixStack matrices, net.minecraft.client.render.VertexConsumerProvider consumers, int light, Arm arm);

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void blurred$inspectHand(LivingEntity entity, ItemStack stack, net.minecraft.client.render.model.json.ModelTransformationMode context, boolean leftHanded,
                                     MatrixStack matrices, net.minecraft.client.render.VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        Arm arm = arm(entity, stack, context == net.minecraft.client.render.model.json.ModelTransformationMode.FIRST_PERSON_RIGHT_HAND,
                context == net.minecraft.client.render.model.json.ModelTransformationMode.FIRST_PERSON_LEFT_HAND);
        if (arm != null) {
            matrices.push();
            Modules.INSPECT.placeHand(matrices, arm == Arm.RIGHT ? 1 : -1);
            renderArm(matrices, consumers, light, arm);
            matrices.pop();
        }
    }
    *///?}

    /** The arm to draw, when this is the inspected main-hand item in first person; else null. */
    private static Arm arm(LivingEntity entity, ItemStack stack, boolean right, boolean left) {
        var player = MinecraftClient.getInstance().player;
        if (entity != player || player == null || !(right || left) || !Modules.INSPECT.drawHand()
                || stack != player.getMainHandStack()) {
            return null;
        }
        return right ? Arm.RIGHT : Arm.LEFT;
    }
}
