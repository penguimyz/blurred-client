package dev.blurredclient.mod.fx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

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
        //? if >=1.21.10 {
        return true;
        //?} else {
        /*return false;
        *///?}
    }

    private static Entity pet;
    private static Object petWorld;
    private static String kind = "";

    /** The mob for {@code type}, created once and reused while the kind stays the same. */
    public static Entity get(String type) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            pet = null;
            return null;
        }
        if (pet != null && type.equals(kind) && petWorld == client.world) {
            return pet;
        }
        kind = type;
        petWorld = client.world;
        pet = create(type);
        return pet;
    }

    public static void clear() {
        pet = null;
        kind = "";
    }

    private static Entity create(String type) {
        //? if >=1.21.10 {
        var world = MinecraftClient.getInstance().world;
        var reason = net.minecraft.entity.SpawnReason.TRIGGERED;
        Entity e = switch (type) {
            case "DOG" -> EntityType.WOLF.create(world, reason);
            case "CAT" -> EntityType.CAT.create(world, reason);
            case "FOX" -> EntityType.FOX.create(world, reason);
            case "BUNNY" -> EntityType.RABBIT.create(world, reason);
            case "AXOLOTL" -> EntityType.AXOLOTL.create(world, reason);
            case "FROG" -> EntityType.FROG.create(world, reason);
            case "ALLAY" -> EntityType.ALLAY.create(world, reason);
            case "PARROT" -> EntityType.PARROT.create(world, reason);
            case "BEE" -> EntityType.BEE.create(world, reason);
            default -> null;
        };
        if (e instanceof net.minecraft.entity.passive.PassiveEntity baby && !type.equals("PARROT")) {
            baby.setBaby(true);
        }
        if (e instanceof net.minecraft.entity.passive.TameableEntity tame) {
            tame.setTamed(true, false);
        }
        if (e != null) {
            e.setSilent(true);
            e.setNoGravity(true);
        }
        return e;
        //?} else {
        /*return null;
        *///?}
    }

    /** Per game tick: walk-cycle and age, so the model animates. */
    public static void tick(Entity e, float speed) {
        //? if >=1.21.10 {
        e.age++;
        if (e instanceof LivingEntity le) {
            le.limbAnimator.updateLimbs(Math.min(1f, speed * 4f), 0.4f, 1f);
        }
        //?}
    }

    /** Place it and draw it through the game's entity renderer. */
    public static void render(WorldDraw.Frame f, Entity e, Vec3d pos, float yaw) {
        //? if >=1.21.10 {
        e.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0f);
        if (e instanceof LivingEntity le) {
            le.setBodyYaw(yaw);
            le.setHeadYaw(yaw);
            le.lastBodyYaw = yaw;
            le.lastHeadYaw = yaw;
        }
        if (!(f.worldState instanceof net.minecraft.client.render.state.WorldRenderState ws)
                || !(f.queue instanceof net.minecraft.client.render.command.OrderedRenderCommandQueue queue)) {
            return;
        }
        var manager = f.client.getEntityRenderDispatcher();
        var state = manager.getAndUpdateRenderState(e, f.tickDelta);
        manager.render(state, ws.cameraRenderState, pos.x - f.cam.x, pos.y - f.cam.y, pos.z - f.cam.z, f.matrices, queue);
        //?}
    }
}
