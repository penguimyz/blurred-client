package dev.blurredclient.mod.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The Minecraft track playing now, for Now playing. Same field on 1.21.1-1.21.11. */
@Mixin(MusicManager.class)
public interface MusicTrackerAccessor {
    @Accessor("currentMusic")
    SoundInstance blurred$current();
}
