package dev.blurredclient.mod.fx;

import dev.blurredclient.mod.module.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Camera motion blur data, recomputed every frame: a matrix that takes a
 * pixel's screen position and depth this frame to where that same point was
 * on screen last frame. The blur shader smears each pixel along that path,
 * so the sky blurs when you turn, nearby blocks blur more than far ones when
 * you move, and nothing ghosts.
 *
 * <p>Uploaded into the post pass's "BlurredMotion" uniform block just before
 * it runs (see PostEffectPassMixin). 1.21.11 only.
 */
public final class MotionBlurState {
    private MotionBlurState() {}

    public static final String BLOCK = "BlurredMotion";

    private static final Matrix4f REPROJECT = new Matrix4f();
    private static Matrix4f lastViewProj;
    private static Vec3d lastCam;
    private static long lastNs;
    private static float shutter = 1f;

    /** Called once per world frame, with the camera used to draw it. */
    public static void capture(WorldDraw.Frame f) {
        MinecraftClient client = f.client;
        Quaternionf rotation = new Quaternionf(f.camera.getRotation());
        Matrix4f view = new Matrix4f().rotation(rotation.conjugate(new Quaternionf()));
        Matrix4f proj = new Matrix4f(client.gameRenderer.getBasicProjectionMatrix((float) ScreenProject.fov()));
        Matrix4f viewProj = new Matrix4f(proj).mul(view);
        long now = System.nanoTime();
        boolean stale = lastViewProj == null || now - lastNs > 200_000_000L;
        if (stale) {
            REPROJECT.identity();
        } else {
            // One frame's movement covers less time the higher the FPS. Scale
            // it to a fixed 1/60 s exposure, so the smear looks the same at
            // 60 FPS and at 800.
            float seconds = (now - lastNs) / 1e9f;
            shutter = Math.max(0.25f, Math.min(12f, (1f / 60f) / Math.max(seconds, 1e-4f)));
            Vec3d moved = f.cam.subtract(lastCam);
            // current clip -> camera-relative position -> last camera's frame -> last clip
            REPROJECT.set(lastViewProj)
                    .translate((float) moved.x, (float) moved.y, (float) moved.z)
                    .mul(new Matrix4f(viewProj).invert());
        }
        lastViewProj = viewProj;
        lastCam = f.cam;
        lastNs = now;
    }

    /** How far to smear along this frame's movement: the Strength slider, scaled to a fixed exposure time. */
    public static float strength() {
        return Modules.MOTION_BLUR.amount() * shutter;
    }

    public static Matrix4f reproject() {
        return REPROJECT;
    }
}
