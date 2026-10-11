package dev.blurredclient.mod.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Pets drawn with Minecraft's own mob models and textures: a copy of the mob
 * that exists only on your client (never added to the world, never sent
 * anywhere), posed and animated by Blurred, rendered through the game's
 * entity renderer so it's lit, shadowed and textured like the real thing.
 *
 * <p>Needs the render-state entity pipeline (1.21.10+). Older versions use
 * the block-built pets.
 */
public final class FakePets {
    private FakePets() {}

    public static boolean supported() {
        return true;
    }

    private static Entity pet;
    private static Object petWorld;
    private static String kind = "";

    /** The mob for {@code type}, created once and reused while the kind stays the same. */
    public static Entity get(String type) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            pet = null;
            return null;
        }
        if (pet != null && type.equals(kind) && petWorld == client.level) {
            return pet;
        }
        kind = type;
        petWorld = client.level;
        pet = create(type);
        return pet;
    }

    public static void clear() {
        pet = null;
        kind = "";
    }

    private static Entity create(String type) {
        var world = Minecraft.getInstance().level;
        var reason = net.minecraft.world.entity.EntitySpawnReason.TRIGGERED;
        Entity e = switch (type) {
            case "DOG" -> EntityTypes.WOLF.create(world, reason);
            case "CAT" -> EntityTypes.CAT.create(world, reason);
            case "FOX" -> EntityTypes.FOX.create(world, reason);
            case "BUNNY" -> EntityTypes.RABBIT.create(world, reason);
            case "AXOLOTL" -> EntityTypes.AXOLOTL.create(world, reason);
            case "FROG" -> EntityTypes.FROG.create(world, reason);
            case "ALLAY" -> EntityTypes.ALLAY.create(world, reason);
            case "PARROT" -> EntityTypes.PARROT.create(world, reason);
            case "BEE" -> EntityTypes.BEE.create(world, reason);
            default -> null;
        };
        if (e instanceof net.minecraft.world.entity.AgeableMob baby && !type.equals("PARROT")) {
            baby.setBaby(true);
        }
        if (e instanceof net.minecraft.world.entity.TamableAnimal tame) {
            tame.setTame(true, false);
        }
        if (e != null) {
            e.setSilent(true);
            e.setNoGravity(true);
        }
        return e;
    }

    /** Per game tick: walk-cycle and age, so the model animates. */
    public static void tick(Entity e, float speed) {
        e.tickCount++;
        if (e instanceof LivingEntity le) {
            le.walkAnimation.update(Math.min(1f, speed * 4f), 0.4f, 1f);
        }
    }

    /** Place it and draw it through the game's entity renderer. */
    public static void render(WorldDraw.Frame f, Entity e, Vec3 pos, float yaw) {
        e.snapTo(pos.x, pos.y, pos.z, yaw, 0f);
        if (e instanceof LivingEntity le) {
            le.setYBodyRot(yaw);
            le.setYHeadRot(yaw);
            le.yBodyRotO = yaw;
            le.yHeadRotO = yaw;
        }
        if (!(f.worldState instanceof net.minecraft.client.renderer.state.level.LevelRenderState ws)
                || !(f.queue instanceof net.minecraft.client.renderer.SubmitNodeCollector queue)) {
            return;
        }
        var manager = f.client.getEntityRenderDispatcher();
        var state = manager.extractEntity(e, f.tickDelta);
        manager.submit(state, ws.cameraRenderState, pos.x - f.cam.x, pos.y - f.cam.y, pos.z - f.cam.z, f.matrices, queue);
    }
}
