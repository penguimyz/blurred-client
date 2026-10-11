package dev.blurredclient.mod.fx;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/** Entity bits whose names changed across the supported versions. */
public final class Ent {
    private Ent() {}

    /** Position: {@code getPos} until 1.21.10 renamed it {@code getEntityPos} (javap-checked). */
    public static Vec3d pos(Entity e) {
        //? if >=1.21.10 {
        return e.getEntityPos();
        //?} else {
        /*return e.getPos();
        *///?}
    }
}
