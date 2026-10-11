package dev.blurredclient.mod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.blurredclient.mod.module.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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
 * <p>{@code item}'s render-target and display-context parameter types
 * changed across versions (javap-checked), hence one injector per version.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class InspectHandMixin {
    @Shadow
    protected abstract void renderMapHand(PoseStack matrices, net.minecraft.client.renderer.SubmitNodeCollector queue, int light, HumanoidArm arm);

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("HEAD"))
    private void blurred$inspectHand(LivingEntity entity, ItemStack stack, net.minecraft.world.item.ItemDisplayContext context,
                                     PoseStack matrices, net.minecraft.client.renderer.SubmitNodeCollector queue, int light, CallbackInfo ci) {
        HumanoidArm arm = arm(entity, stack, context == net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                context == net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
        if (arm != null) {
            matrices.pushPose();
            Modules.INSPECT.placeHand(matrices, arm == HumanoidArm.RIGHT ? 1 : -1);
            renderMapHand(matrices, queue, light, arm);
            matrices.popPose();
        }
    }

    /** The arm to draw, when this is the inspected main-hand item in first person; else null. */
    private static HumanoidArm arm(LivingEntity entity, ItemStack stack, boolean right, boolean left) {
        var player = Minecraft.getInstance().player;
        if (entity != player || player == null || !(right || left) || !Modules.INSPECT.drawHand()
                || stack != player.getMainHandItem()) {
            return null;
        }
        return right ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
    }
}
