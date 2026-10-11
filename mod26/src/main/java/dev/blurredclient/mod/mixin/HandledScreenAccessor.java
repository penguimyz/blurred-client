package dev.blurredclient.mod.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Move a container screen (Cinematic containers). Same fields on 1.21.1-1.21.11. */
@Mixin(AbstractContainerScreen.class)
public interface HandledScreenAccessor {
    @Accessor("leftPos")
    void blurred$setX(int x);

    @Accessor("topPos")
    void blurred$setY(int y);

    @Accessor("leftPos")
    int blurred$x();

    @Accessor("topPos")
    int blurred$y();

    @Accessor("imageWidth")
    int blurred$backgroundWidth();

    @Accessor("imageHeight")
    int blurred$backgroundHeight();
}
