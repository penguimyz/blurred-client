package dev.blurredclient.mod.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Move a container screen (Cinematic containers). Same fields on 1.21.1-1.21.11. */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x")
    void blurred$setX(int x);

    @Accessor("y")
    void blurred$setY(int y);

    @Accessor("x")
    int blurred$x();

    @Accessor("y")
    int blurred$y();

    @Accessor("backgroundWidth")
    int blurred$backgroundWidth();

    @Accessor("backgroundHeight")
    int blurred$backgroundHeight();
}
