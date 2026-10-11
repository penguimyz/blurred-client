package dev.blurredclient.mod.fx;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

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
        Minecraft client = Minecraft.getInstance();
        return client.options.fov().get() * dev.blurredclient.mod.module.Modules.ZOOM.fovMultiplier();
    }

    /** Camera position: {@code getPos} until 1.21.8 added {@code getCameraPos}; 1.21.11 has only the latter. */
    public static Vec3 cameraPos(Camera camera) {
        return camera.position();
    }

    /** The camera's right-hand direction, horizontal. */
    public static Vec3 right(Camera camera) {
        double yaw = Math.toRadians(camera.yRot());
        return new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
    }

    /**
     * Project {@code point} to GUI coordinates on a {@code w x h} screen.
     *
     * @return {x, y}, or null if the point is behind the camera
     */
    public static double[] project(Vec3 point, int w, int h) {
        Minecraft client = Minecraft.getInstance();
        Camera camera = client.gameRenderer.mainCamera();
        double yaw = Math.toRadians(camera.yRot());
        double pitch = Math.toRadians(camera.xRot());

        Vec3 forward = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 up = right.cross(forward);

        Vec3 rel = point.subtract(cameraPos(camera));
        double z = rel.dot(forward);
        if (z < 0.05) {
            return null;
        }
        double fov = fov();
        double focal = (h / 2.0) / Math.tan(Math.toRadians(fov) / 2.0);
        double sx = w / 2.0 + rel.dot(right) / z * focal;
        double sy = h / 2.0 - rel.dot(up) / z * focal;
        return new double[] {sx, sy};
    }
}
