package dev.blurredclient.mod.mixin;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Adds Skin shine's layer to the player renderer. The constructor is
 * {@code (EntityRendererFactory.Context, boolean slim)} from 1.21.4
 * (javap-checked); 1.21.1 renders players differently and gets no layer.
 */
@Mixin(AvatarRenderer.class)
public abstract class PlayerShineMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @org.spongepowered.asm.mixin.injection.Inject(method = "<init>", at = @org.spongepowered.asm.mixin.injection.At("TAIL"))
    private void blurred$shine(net.minecraft.client.renderer.entity.EntityRendererProvider.Context ctx, boolean slim,
                               org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        // By id: 1.21.11 has two fields named PLAYER_SLIM, which javac can't tell apart.
        var layer = new net.minecraft.client.model.geom.ModelLayerLocation(
                net.minecraft.resources.Identifier.withDefaultNamespace(slim ? "player_slim" : "player"), "main");
        var root = ctx.bakeLayer(layer);
        var model = new net.minecraft.client.model.player.PlayerModel(root, slim);
        ((LivingRendererInvoker) this).blurred$addFeature(new dev.blurredclient.mod.fx.SkinShineFeature(
                (net.minecraft.client.renderer.entity.RenderLayerParent) this, model));
    }
}
