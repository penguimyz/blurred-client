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
//? if >=1.21.4 {
public final class SkinShineFeature extends net.minecraft.client.render.entity.feature.FeatureRenderer<
        net.minecraft.client.render.entity.state.PlayerEntityRenderState, net.minecraft.client.render.entity.model.PlayerEntityModel> {
    private final net.minecraft.client.render.entity.model.PlayerEntityModel model;
    /** Full colour; the texture's own transparency lets the skin show through. */
    private static final int TINT = 0xFFFFFFFF;

    public SkinShineFeature(net.minecraft.client.render.entity.feature.FeatureRendererContext<
            net.minecraft.client.render.entity.state.PlayerEntityRenderState, net.minecraft.client.render.entity.model.PlayerEntityModel> context,
                            net.minecraft.client.render.entity.model.PlayerEntityModel model) {
        super(context);
        this.model = model;
        // Only the outer layer: the base parts are hidden, their outer-layer children still draw.
        model.head.hidden = true;
        model.body.hidden = true;
        model.rightArm.hidden = true;
        model.leftArm.hidden = true;
        model.rightLeg.hidden = true;
        model.leftLeg.hidden = true;
    }

    //? if >=1.21.10 {
    @Override
    public void render(net.minecraft.client.util.math.MatrixStack matrices, net.minecraft.client.render.command.OrderedRenderCommandQueue queue,
                       int light, net.minecraft.client.render.entity.state.PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (!dev.blurredclient.mod.module.Modules.SKIN_SHINE.shines(state.id, state.invisible)) {
            return;
        }
        // Glowing and see-through, like spider eyes, wrapped round the outer layer.
        var tex = dev.blurredclient.mod.module.feature.ItemSkinsModule.SKIN_TEXTURE;
        //? if >=1.21.11 {
        var layer = net.minecraft.client.render.RenderLayers.eyes(tex);
        //?} else {
        /*var layer = net.minecraft.client.render.RenderLayer.getEyes(tex);
        *///?}
        // A later batch than the player itself: the shine only paints where the skin was already drawn.
        queue.getBatchingQueue(2).submitModel(model, state, matrices, layer, light,
                net.minecraft.client.render.OverlayTexture.DEFAULT_UV, TINT, null, 0, null);
    }
    //?} else {
    /*@Override
    public void render(net.minecraft.client.util.math.MatrixStack matrices, net.minecraft.client.render.VertexConsumerProvider consumers,
                       int light, net.minecraft.client.render.entity.state.PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (!dev.blurredclient.mod.module.Modules.SKIN_SHINE.shines(state.id, state.invisible)) {
            return;
        }
        model.setAngles(state);
        model.render(matrices, consumers.getBuffer(net.minecraft.client.render.RenderLayer.getEyes(
                dev.blurredclient.mod.module.feature.ItemSkinsModule.SKIN_TEXTURE)), light,
                net.minecraft.client.render.OverlayTexture.DEFAULT_UV, TINT);
    }
    *///?}
}
//?} else {
/*public final class SkinShineFeature {
    private SkinShineFeature() {}
}
*///?}
