package dev.blurredclient.mod.fx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/**
 * Where a point in the world appears on screen.
 *
 * <p>Done from the camera's position, yaw, pitch and the FOV setting rather
 * than Minecraft's projection matrices, whose types and access changed across
 * the supported versions. It ignores the small FOV changes from sprinting and
 * speed effects, which is fine for placing a label beside a block.
 */
public final class ScreenProject {
    private ScreenProject() {}

    /** The field of view the world was last drawn with (degrees), 0 before the first frame. */
    private static volatile float worldFov;

    public static void recordFov(float fov) {
        worldFov = fov;
    }

    /** The world's current field of view, including zoom, sprinting and effects. */
    public static double fov() {
        if (worldFov > 0) {
            return worldFov;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        return client.options.getFov().getValue() * dev.blurredclient.mod.module.Modules.ZOOM.fovMultiplier();
    }

    /** Camera position: {@code getPos} until 1.21.8 added {@code getCameraPos}; 1.21.11 has only the latter. */
    public static Vec3d cameraPos(Camera camera) {
        //? if >=1.21.8 {
        return camera.getCameraPos();
        //?} else {
        /*return camera.getPos();
        *///?}
    }

    /** The camera's right-hand direction, horizontal. */
    public static Vec3d right(Camera camera) {
        double yaw = Math.toRadians(camera.getYaw());
        return new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
    }

    /**
     * Project {@code point} to GUI coordinates on a {@code w x h} screen.
     *
     * @return {x, y}, or null if the point is behind the camera
     */
    public static double[] project(Vec3d point, int w, int h) {
        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = client.gameRenderer.getCamera();
        double yaw = Math.toRadians(camera.getYaw());
        double pitch = Math.toRadians(camera.getPitch());

        Vec3d forward = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3d right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3d up = right.crossProduct(forward);

        Vec3d rel = point.subtract(cameraPos(camera));
        double z = rel.dotProduct(forward);
        if (z < 0.05) {
            return null;
        }
        double fov = fov();
        double focal = (h / 2.0) / Math.tan(Math.toRadians(fov) / 2.0);
        double sx = w / 2.0 + rel.dotProduct(right) / z * focal;
        double sy = h / 2.0 - rel.dotProduct(up) / z * focal;
        return new double[] {sx, sy};
    }
}
