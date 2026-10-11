package dev.blurredclient.mod.fx;

/** The exact {@code ItemEntityRenderer.render} overload per version (the bridge overload is skipped). */
public final class ItemPhysicsTargets {
    private ItemPhysicsTargets() {}

    /** 26.x: {@code submit} replaced {@code render}. */
    public static final String RENDER = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";
}
