package dev.blurredclient.mod.mixin;

import net.minecraft.client.sound.MusicTracker;
import net.minecraft.client.sound.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The Minecraft track playing now, for Now playing. Same field on 1.21.1-1.21.11. */
@Mixin(MusicTracker.class)
public interface MusicTrackerAccessor {
    @Accessor("current")
    SoundInstance blurred$current();
}
