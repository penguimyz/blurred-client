package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.config.BlurredConfig;
import dev.blurredclient.mod.cosmetic.CapeManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Swaps in a Blurred cape for any player who has one.
 *
 * <p>Hooks the skin accessor rather than the cape renderer: {@link PlayerSkin}
 * is the single source every renderer reads from, so patching it here means the
 * cape shows up in the world, in the inventory preview and on the elytra
 * without touching three separate render paths.
 *
 * <p>A player without a Blurred cape falls through untouched, which is what
 * keeps ordinary Mojang capes working.
 *
 * <h2>What 26.x changed</h2>
 *
 * The type is now {@code PlayerSkin} (a record of {@code ClientAsset.Texture}s)
 * rather than {@code SkinTextures}, and the partial-update helper is
 * {@link PlayerSkin.Patch} rather than {@code SkinOverride}. The shape is the
 * same — a set of optionals layered over the existing skin — so the logic below
 * is unchanged from the 1.21.x build; only the names and the asset wrapper
 * moved.
 */
@Mixin(AbstractClientPlayer.class)
public class PlayerSkinTexturesMixin {

    /**
     * Vanilla's elytra texture, as the equipment model refers to it.
     *
     * <p>{@code assets/minecraft/equipment/elytra.json} declares the wings
     * layer as {@code minecraft:elytra} with {@code use_player_texture: true},
     * which resolves to this path.
     */
    private static final ClientAsset.ResourceTexture VANILLA_ELYTRA =
            new ClientAsset.ResourceTexture(
                    Identifier.fromNamespaceAndPath("minecraft", "elytra"),
                    Identifier.fromNamespaceAndPath(
                            "minecraft", "textures/entity/equipment/wings/elytra.png"));

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void blurred$applyCape(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        Identifier cape = blurred$capeForSelf();
        if (cape == null) {
            return;
        }

        // Both id and texturePath, pointing at the same thing — and the
        // two-argument constructor specifically.
        //
        // `ResourceTexture` holds an id *and* a texture path, and the one-arg
        // constructor derives the second from the first with
        // `Identifier.withPath(...)`, i.e. it assumes a file inside a resource
        // pack. Ours isn't one: CapeManager registers the decoded PNG straight
        // into the TextureManager under this exact id, so a derived path points
        // at a file that has never existed and the cape renders as the missing
        // texture. Passing both explicitly is what the 1.21.x build does, for
        // the same reason.
        ClientAsset.ResourceTexture asset = new ClientAsset.ResourceTexture(cape, cape);

        // The elytra needs saying explicitly, and leaving it empty is NOT
        // enough. WingsLayer picks its texture like this:
        //
        //     if (skin.elytra() != null) use skin.elytra()
        //     if (skin.cape()   != null) use skin.cape()      <-- falls through
        //
        // so setting a cape silently drags the wings onto the cape sheet too.
        // That's right for a Mojang cape, whose sheet paints wings at x>=22,
        // and wrong for ours, which leave that half transparent — the elytra
        // renders fully see-through and appears to vanish off your back.
        //
        // So when our cape has no wings on it we pin the elytra to vanilla's
        // own texture, which is what the player would have seen anyway.
        String name = self.getGameProfile().name();
        Optional<ClientAsset.ResourceTexture> elytra = CapeManager.hasElytraArt(name)
                ? Optional.of(asset)
                : Optional.of(VANILLA_ELYTRA);

        cir.setReturnValue(cir.getReturnValue().with(PlayerSkin.Patch.create(
                Optional.empty(),    // body — leave the player's own skin
                Optional.of(asset),  // cape
                elytra,
                Optional.empty()))); // model — leave classic/slim alone
    }

    /** The cape for the player this mixin is applied to, or null. */
    private Identifier blurred$capeForSelf() {
        if (!BlurredConfig.get().showCapes) {
            return null;
        }
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        String name = self.getGameProfile().name();
        return name == null ? null : CapeManager.capeFor(name);
    }
}
