package dev.blurredclient.mod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.blurredclient.mod.module.Modules;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Item skins: the chosen items get the (re-skinned) shine. Same method on every version. */
@Mixin(ItemStack.class)
public class ItemGlintMixin {
    @ModifyReturnValue(method = "hasFoil", at = @At("RETURN"))
    private boolean blurred$skin(boolean vanilla) {
        return vanilla || Modules.ITEM_SKINS.wants((ItemStack) (Object) this);
    }
}
