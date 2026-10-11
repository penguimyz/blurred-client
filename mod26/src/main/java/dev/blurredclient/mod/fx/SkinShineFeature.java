package dev.blurredclient.mod.fx;

/**
 * Skin shine: draws the outer layer of a player's skin (hat, jacket, sleeves,
 * trousers) again, glowing with Item skins' animated texture (drawn like
 * spider eyes: bright and see-through). Uses its own copy of the player model with the base
 * body hidden, posed the same way each frame, so only the outer layer gets
 * it and it moves exactly with the body.
 *
 * <p>1.21.4+ (render states); the render-target type changed in 1.21.10.
 */
public final class SkinShineFeature extends net.minecraft.client.renderer.entity.layers.RenderLayer<
        net.minecraft.client.renderer.entity.state.AvatarRenderState, net.minecraft.client.model.player.PlayerModel> {
    private final net.minecraft.client.model.player.PlayerModel model;
    /** Full colour; the texture's own transparency lets the skin show through. */
    private static final int TINT = 0xFFFFFFFF;

    public SkinShineFeature(net.minecraft.client.renderer.entity.RenderLayerParent<
            net.minecraft.client.renderer.entity.state.AvatarRenderState, net.minecraft.client.model.player.PlayerModel> context,
                            net.minecraft.client.model.player.PlayerModel model) {
        super(context);
        this.model = model;
        // Only the outer layer: the base parts are hidden, their outer-layer children still draw.
        model.head.skipDraw = true;
        model.body.skipDraw = true;
        model.rightArm.skipDraw = true;
        model.leftArm.skipDraw = true;
        model.rightLeg.skipDraw = true;
        model.leftLeg.skipDraw = true;
    }

    @Override
    public void submit(com.mojang.blaze3d.vertex.PoseStack matrices, net.minecraft.client.renderer.SubmitNodeCollector queue,
                       int light, net.minecraft.client.renderer.entity.state.AvatarRenderState state, float limbAngle, float limbDistance) {
        if (!dev.blurredclient.mod.module.Modules.SKIN_SHINE.shines(state.id, state.isInvisible)) {
            return;
        }
        // Glowing and see-through, like spider eyes, wrapped round the outer layer.
        var tex = dev.blurredclient.mod.module.feature.ItemSkinsModule.SKIN_TEXTURE;
        var layer = net.minecraft.client.renderer.rendertype.RenderTypes.eyes(tex);
        // A later batch than the player itself: the shine only paints where the skin was already drawn.
        queue.order(2).submitModel(model, state, matrices, layer, light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, TINT, null, 0, null);
    }
}
