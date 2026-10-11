package dev.blurredclient.mod.mixin;

import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to how many times a key was pressed since the game last
 * consumed it.
 *
 * <p>For the CPS readout: peeking at the count at the start of a tick, before
 * the game's input handling drains it, sees every click without consuming any.
 * {@code wasPressed()} would consume them and swallow the player's attacks.
 * The field has the same name in every supported version (checked with javap
 * on 1.21.1–1.21.11), so this needs no Stonecutter branch.
 */
@Mixin(KeyBinding.class)
public interface KeyBindingAccessor {
    @Accessor("timesPressed")
    int blurred$getTimesPressed();
}
