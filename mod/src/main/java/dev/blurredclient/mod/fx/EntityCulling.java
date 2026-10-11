package dev.blurredclient.mod.fx;

import dev.blurredclient.mod.module.Modules;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Entity culling: mobs (and other entities) completely hidden behind solid
 * blocks aren't drawn. Each tick, rays are traced from your eyes to an
 * entity's centre and the corners of its box through the blocks in between;
 * if every ray is stopped by an opaque full block, it's culled until a
 * later check sees it again. Close, glowing and your own entities always draw.
 *
 * <p>Purely a rendering shortcut on your side: nothing is hidden from the
 * game, only skipped when it couldn't be seen anyway.
 */
public final class EntityCulling {
    private EntityCulling() {}

    private static final double ALWAYS_WITHIN = 5.0;
    private static final double MAX_DISTANCE = 128.0;
    /** Entity id -> tick it was last seen; hidden only once it's gone unseen a moment. */
    private static final Map<Integer, Integer> SEEN = new HashMap<>();
    private static int tick;

    /** Whether to skip drawing {@code e} this frame. */
    public static boolean culled(Entity e) {
        if (!Modules.ENTITY_CULLING.isActive()) {
            return false;
        }
        Integer seen = SEEN.get(e.getId());
        return seen != null && tick - seen > 2;
    }

    public static void tick(MinecraftClient client) {
        tick++;
        if (!Modules.ENTITY_CULLING.isActive() || client.world == null || client.player == null) {
            SEEN.clear();
            return;
        }
        Vec3d eye = ScreenProject.cameraPos(client.gameRenderer.getCamera());
        Entity viewer = client.getCameraEntity();
        if (tick % 40 == 0) {
            SEEN.keySet().removeIf(id -> client.world.getEntityById(id) == null);
        }
        for (Entity e : client.world.getEntities()) {
            if (e == viewer || e == client.player || e.isGlowing()) {
                SEEN.put(e.getId(), tick);
                continue;
            }
            double d = e.getBoundingBox().getCenter().distanceTo(eye);
            if (d < ALWAYS_WITHIN || d > MAX_DISTANCE || visible(client.world, eye, e.getBoundingBox().expand(0.25))) {
                SEEN.put(e.getId(), tick);
            } else {
                SEEN.putIfAbsent(e.getId(), tick - 10);
            }
        }
    }

    private static boolean visible(World world, Vec3d eye, Box b) {
        if (clear(world, eye, b.getCenter())) {
            return true;
        }
        for (int i = 0; i < 8; i++) {
            Vec3d corner = new Vec3d((i & 1) == 0 ? b.minX : b.maxX, (i & 2) == 0 ? b.minY : b.maxY,
                    (i & 4) == 0 ? b.minZ : b.maxZ);
            if (clear(world, eye, corner)) {
                return true;
            }
        }
        return false;
    }

    /** Is the straight line from {@code a} to {@code b} free of opaque full blocks? (A voxel walk.) */
    private static boolean clear(World world, Vec3d a, Vec3d b) {
        int x = (int) Math.floor(a.x);
        int y = (int) Math.floor(a.y);
        int z = (int) Math.floor(a.z);
        int ex = (int) Math.floor(b.x);
        int ey = (int) Math.floor(b.y);
        int ez = (int) Math.floor(b.z);
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dz = b.z - a.z;
        int sx = dx > 0 ? 1 : -1;
        int sy = dy > 0 ? 1 : -1;
        int sz = dz > 0 ? 1 : -1;
        double tdx = dx == 0 ? Double.MAX_VALUE : Math.abs(1 / dx);
        double tdy = dy == 0 ? Double.MAX_VALUE : Math.abs(1 / dy);
        double tdz = dz == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
        double tx = dx == 0 ? Double.MAX_VALUE : (sx > 0 ? x + 1 - a.x : a.x - x) * tdx;
        double ty = dy == 0 ? Double.MAX_VALUE : (sy > 0 ? y + 1 - a.y : a.y - y) * tdy;
        double tz = dz == 0 ? Double.MAX_VALUE : (sz > 0 ? z + 1 - a.z : a.z - z) * tdz;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int steps = 0; steps < 400; steps++) {
            if (x == ex && y == ey && z == ez) {
                return true;
            }
            if (tx < ty && tx < tz) {
                x += sx;
                tx += tdx;
            } else if (ty < tz) {
                y += sy;
                ty += tdy;
            } else {
                z += sz;
                tz += tdz;
            }
            if (x == ex && y == ey && z == ez) {
                return true;
            }
            pos.set(x, y, z);
            if (opaque(world, world.getBlockState(pos), pos)) {
                return false;
            }
        }
        return true;
    }

    private static boolean opaque(World world, BlockState state, BlockPos pos) {
        //? if >=1.21.4 {
        return state.isOpaqueFullCube();
        //?} else {
        /*return state.isOpaqueFullCube(world, pos);
        *///?}
    }
}
