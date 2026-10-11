package dev.blurredclient.mod.module.feature;

import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;

/** Skip drawing entities hidden behind solid blocks (see fx.EntityCulling). */
public final class EntityCullingModule extends Module {
    public EntityCullingModule() {
        super("entity_culling", "Entity culling",
                "Mobs and other entities hidden behind solid blocks aren't drawn: more FPS around farms, mob grinders and busy areas.",
                Category.PERFORMANCE, true);
    }
}
