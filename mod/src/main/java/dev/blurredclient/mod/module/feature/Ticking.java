package dev.blurredclient.mod.module.feature;

import net.minecraft.client.MinecraftClient;

/** A module that does something every client tick while it's active. */
public interface Ticking {
    void tick(MinecraftClient client);
}
