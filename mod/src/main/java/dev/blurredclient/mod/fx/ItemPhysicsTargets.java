package dev.blurredclient.mod.fx;

/** The exact {@code ItemEntityRenderer.render} overload per version (the bridge overload is skipped). */
public final class ItemPhysicsTargets {
    private ItemPhysicsTargets() {}

    //? if >=1.21.10 {
    public static final String RENDER = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;"
            + "Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;"
            + "Lnet/minecraft/client/render/state/CameraRenderState;)V";
    //?} elif >=1.21.4 {
    /*public static final String RENDER = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;"
            + "Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V";
    *///?} else {
    /*public static final String RENDER = "render(Lnet/minecraft/entity/ItemEntity;FF"
            + "Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V";
    *///?}
}
