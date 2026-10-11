package dev.blurredclient.mod.fx;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Entity bits whose names changed across the supported versions. */
public final class Ent {
    private Ent() {}

    /** Position: {@code getPos} until 1.21.10 renamed it {@code getEntityPos} (javap-checked). */
    public static Vec3 pos(Entity e) {
        return e.position();
    }
}
