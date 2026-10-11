package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.cosmetic.CapeManager;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//? if >=1.21.10 {
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import java.util.Optional;
//?} else {
/*import net.minecraft.client.util.SkinTextures;
 *///?}

/**
 * Swaps in a Blurred cape for any player who has one.
 *
 * <p>This hooks the skin accessor rather than the cape renderer itself, and
 * that choice matters: {@code SkinTextures} is the single source every renderer
 * reads from, so patching it here means the cape shows up in the world, in the
 * inventory preview, on the elytra slot and anywhere else the game draws a
 * player — one injection instead of one per render path.
 *
 * <p>This is the file that drifts most across versions, which is why the whole
 * injection is branched rather than patched piecemeal:
 *
 * <ul>
 *   <li><b>1.21.9+</b> — the method is {@code getSkin}, {@code SkinTextures}
 *       lives in {@code net.minecraft.entity.player} and its components are
 *       {@code AssetInfo.TextureAsset} rather than raw identifiers. It also
 *       gained {@code withOverride}, which is the game's own supported way to
 *       layer a replacement texture over a resolved skin — better than
 *       rebuilding the record, because it keeps working when the record gains
 *       fields.</li>
 *   <li><b>Older</b> — the method is {@code getSkinTextures},
 *       {@code SkinTextures} lives in {@code net.minecraft.client.util} and is a
 *       six-component record of plain identifiers, so the only option is to
 *       rebuild it by hand.</li>
 * </ul>
 *
 * <p>A player without a Blurred cape falls through untouched either way, which
 * is what keeps ordinary Mojang capes working.
 */
@Mixin(AbstractClientPlayerEntity.class)
public class PlayerSkinTexturesMixin {

    //? if >=1.21.10 {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void blurred$applyCape(CallbackInfoReturnable<SkinTextures> cir) {
        Identifier cape = blurred$capeForSelf();
        if (cape == null) {
            return;
        }
        // Both id and texturePath point at our registered texture: the renderer
        // reads texturePath(), and matching id() keeps the asset consistent for
        // anything that inspects it.
        AssetInfo.TextureAssetInfo asset = new AssetInfo.TextureAssetInfo(cape, cape);
        cir.setReturnValue(cir.getReturnValue().withOverride(SkinTextures.SkinOverride.create(
                Optional.empty(),   // body — leave the player's own skin
                Optional.of(asset), // cape
                Optional.of(blurred$capeHasElytra() ? asset : blurred$vanillaElytra()),
                Optional.empty()))); // model — leave classic/slim alone
    }
    //?} else {
    /*@Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void blurred$applyCape(CallbackInfoReturnable<SkinTextures> cir) {
        Identifier cape = blurred$capeForSelf();
        if (cape == null) {
            return;
        }
        SkinTextures original = cir.getReturnValue();
        cir.setReturnValue(new SkinTextures(
                original.texture(),
                original.textureUrl(),
                cape,
                blurred$capeHasElytra() ? cape : BLURRED$VANILLA_ELYTRA,
                original.model(),
                original.secure()));
    }
     *///?}

    /**
     * Vanilla's own elytra texture.
     *
     * <p>The path moved when the equipment model system landed: it's
     * {@code textures/entity/elytra.png} up to 1.21.1 and
     * {@code textures/entity/equipment/wings/elytra.png} from 1.21.4 on.
     */
    //? if >=1.21.4 {
    private static final Identifier BLURRED$VANILLA_ELYTRA =
            Identifier.ofVanilla("textures/entity/equipment/wings/elytra.png");
    //?} else {
    /*private static final Identifier BLURRED$VANILLA_ELYTRA =
            Identifier.ofVanilla("textures/entity/elytra.png");
     *///?}

    //? if >=1.21.10 {
    /** The vanilla elytra, wrapped as the asset the newer API expects. */
    private AssetInfo.TextureAssetInfo blurred$vanillaElytra() {
        return new AssetInfo.TextureAssetInfo(BLURRED$VANILLA_ELYTRA, BLURRED$VANILLA_ELYTRA);
    }
    //?}

    /**
     * Should this cape take over the elytra too?
     *
     * <p>Only when its sheet actually has wings painted on it. Wearing an
     * elytra makes the game render it <em>from the cape texture</em>, so
     * pointing it at a sheet whose elytra half is blank draws fully
     * transparent wings — the elytra disappears off your back. See
     * {@link CapeManager#hasElytraArt}.
     *
     * <p>A boolean rather than the override itself, because the type that
     * override is expressed in differs between versions and this doesn't.
     */
    private boolean blurred$capeHasElytra() {
        return CapeManager.hasElytraArt(blurred$selfName());
    }

    /**
     * The cape for the player this mixin is applied to, or null.
     *
     * <p>Shared by both branches so the config check, the name lookup and the
     * null handling exist once — only the record shuffling above is
     * version-specific.
     */
    private Identifier blurred$capeForSelf() {
        if (!Modules.CAPES.isActive()) {
            return null;
        }
        String name = blurred$selfName();
        return name == null ? null : CapeManager.capeFor(name);
    }

    /**
     * This player's account name.
     *
     * <p>Mojang's {@code GameProfile} became a record in the authlib that ships
     * with 1.21.9, so the accessor lost its {@code get} prefix.
     */
    private String blurred$selfName() {
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
        //? if >=1.21.10 {
        return self.getGameProfile().name();
        //?} else {
        /*return self.getGameProfile().getName();
         *///?}
    }
}
