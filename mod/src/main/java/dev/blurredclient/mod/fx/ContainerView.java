package dev.blurredclient.mod.fx;

import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Chests you open from the inside: the camera glides up over the open chest
 * (or barrel, ender chest, shulker box) and its items sit in it as real 3D
 * models, laid out in the container's grid. Hovering one lifts it; clicking
 * works exactly as in the normal screen — the clicks go through vanilla's
 * own slot handling, only *where* each slot is on screen comes from here.
 * Your own inventory stays as a panel at the bottom.
 *
 * <p>The camera move is purely visual: the player's look direction never
 * changes, so nothing different is sent to the server.
 */
public final class ContainerView implements WorldDraw.Layer {
    public static final ContainerView INSTANCE = new ContainerView();

    private ContainerView() {}

    private static final double OPEN_SECONDS = 0.5;

    /** One open container being shown in 3D. */
    private static final class Session {
        HandledScreen<?> screen;
        BlockPos pos;
        Vec3d origin;
        Vec3d right;
        Vec3d down;
        Vec3d normal;
        double cell;
        int rows;
        final List<Slot> slots = new ArrayList<>();
        /** Per slot: screen x, y and pick radius (GUI units); updated every frame. */
        double[][] onScreen;
        Vec3d camPos;
        float camYaw;
        float camPitch;
        float t;
        boolean closing;
        long lastNs = System.nanoTime();
        final float[] lift = new float[54];
        /** Your view when it started closing, to tell a flick away. */
        float closeYaw = Float.NaN;
        float closePitch;
        /** The yaw blend's direction, chosen once so it never flips mid-turn. */
        float yawSpan = Float.NaN;
    }

    private Session session;
    private BlockPos usedPos;
    private long usedNs;

    /** The block you just right-clicked: the one whose screen may be about to open. */
    public void noteUse(BlockPos pos) {
        usedPos = pos.toImmutable();
        usedNs = System.nanoTime();
    }

    // ---- opening and closing ----

    /** Called after a screen's init. Starts a session for supported containers. */
    public void onScreenInit(Screen screen, int width, int height) {
        if (!(screen instanceof GenericContainerScreen) && !(screen instanceof ShulkerBoxScreen)) {
            return;
        }
        HandledScreen<?> handled = (HandledScreen<?>) screen;
        if (session != null && session.screen == screen) {
            placeGui(handled, height);
            return;
        }
        if (!Modules.CINEMATIC_CONTAINERS.isActive() || !Modules.CINEMATIC_CONTAINERS.insideView()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }
        // The block you clicked (the screen arrives a moment later, and you
        // may have walked on); else whatever you're looking at.
        BlockPos pos = null;
        if (usedPos != null && System.nanoTime() - usedNs < 3_000_000_000L
                && client.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(usedPos)) < 64) {
            pos = usedPos;
        } else if (client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            pos = hit.getBlockPos();
        }
        usedPos = null;
        if (pos == null) {
            return;
        }
        Session s = new Session();
        s.screen = handled;
        s.pos = pos;
        for (Slot slot : handled.getScreenHandler().slots) {
            if (!(slot.inventory instanceof PlayerInventory)) {
                s.slots.add(slot);
            }
        }
        if (s.slots.isEmpty() || s.slots.size() % 9 != 0 || !layout(s, client.world.getBlockState(s.pos), client)) {
            return;
        }
        s.onScreen = new double[s.slots.size()][3];
        session = s;
        placeGui(handled, height);
    }

    /** Move the GUI so your inventory panel sits at the bottom, clear of the chest. */
    private static void placeGui(HandledScreen<?> screen, int height) {
        var acc = (dev.blurredclient.mod.mixin.HandledScreenAccessor) screen;
        acc.blurred$setY(height - acc.blurred$backgroundHeight() - 4);
    }

    /** True while {@code screen} is shown in 3D. */
    public boolean active(Screen screen) {
        return session != null && !session.closing && session.screen == screen;
    }

    /** Is this one of the container's slots (shown in 3D, not in the GUI)? */
    public boolean inWorld(Slot slot) {
        return session != null && !session.closing && session.slots.contains(slot);
    }

    /** True from the moment a container opens in 3D until its camera has glided back. */
    public boolean showing() {
        return session != null;
    }

    /** 0..1 how far into the 3D view we are (for hiding the hand, etc). */
    public float amount() {
        return session == null ? 0f : ease(session.t);
    }

    // ---- where everything goes ----

    /**
     * Works out the grid (on the chest floor, the barrel's open face, ...)
     * and the camera pose looking into it. False if this block isn't one
     * we know how to open up.
     */
    private static boolean layout(Session s, BlockState state, MinecraftClient client) {
        int rows = s.slots.size() / 9;
        s.rows = rows;
        Vec3d centre = Vec3d.ofCenter(s.pos);
        Direction toPlayer = horizontalToward(client, centre);
        double width;
        double depth;
        double surface;
        if (state.getBlock() instanceof ChestBlock && state.contains(ChestBlock.CHEST_TYPE)) {
            Direction facing = state.get(ChestBlock.FACING);
            if (state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE && rows > 3) {
                // Centre between the two halves.
                Direction other = ChestBlock.getFacing(state);
                centre = centre.add(other.getOffsetX() * 0.5, 0, other.getOffsetZ() * 0.5);
                width = 1.8;
            } else {
                width = 0.84;
            }
            setUp(s, facing);
            depth = 0.84;
            // The chest model isn't hollow: what you see inside is the top of
            // its base, 10/16 up. Items sit on that.
            surface = s.pos.getY() + 10 / 16.0 + 0.012;
        } else if (state.getBlock() instanceof EnderChestBlock) {
            setUp(s, state.get(EnderChestBlock.FACING));
            width = 0.84;
            depth = 0.84;
            surface = s.pos.getY() + 10 / 16.0 + 0.012;
        } else if (state.getBlock() instanceof BarrelBlock) {
            Direction facing = state.get(BarrelBlock.FACING);
            if (facing == Direction.UP) {
                setUp(s, toPlayer);
                surface = s.pos.getY() + 1.03;
            } else if (facing.getAxis().isHorizontal()) {
                s.normal = vec(facing);
                s.down = new Vec3d(0, -1, 0);
                s.right = vec(facing.rotateYCounterclockwise());
                centre = centre.add(s.normal.multiply(0.53));
                surface = Double.NaN;
            } else {
                return false;
            }
            width = 0.96;
            depth = 0.96;
        } else if (state.getBlock() instanceof ShulkerBoxBlock) {
            if (state.get(ShulkerBoxBlock.FACING) != Direction.UP) {
                return false;
            }
            setUp(s, toPlayer);
            width = 0.84;
            depth = 0.84;
            // Shulker base is half a block; the lid lifts off above it.
            surface = s.pos.getY() + 0.5 + 0.012;
        } else {
            return false;
        }
        if (!Double.isNaN(surface)) {
            centre = new Vec3d(centre.x, surface, centre.z);
        }
        s.cell = Math.min(width / 9, depth / rows);
        s.origin = centre.subtract(s.right.multiply(s.cell * 4.5)).subtract(s.down.multiply(s.cell * rows / 2.0));
        camera(s, centre, client);
        return true;
    }

    /** A grid lying on a horizontal surface, read from the side the block faces. */
    private static void setUp(Session s, Direction front) {
        s.normal = new Vec3d(0, 1, 0);
        s.down = vec(front);
        s.right = vec(front.rotateYCounterclockwise());
    }

    private static Vec3d vec(Direction d) {
        return new Vec3d(d.getOffsetX(), d.getOffsetY(), d.getOffsetZ());
    }

    private static Direction horizontalToward(MinecraftClient client, Vec3d from) {
        Vec3d eye = client.player.getEyePos();
        return Direction.getFacing(eye.x - from.x, 0, eye.z - from.z);
    }

    /**
     * Camera above and slightly in front of the grid, far enough that the
     * grid fills about two thirds of the screen width, aimed so the grid sits
     * in the upper part of the screen (the inventory panel takes the bottom).
     */
    private static void camera(Session s, Vec3d centre, MinecraftClient client) {
        double fov = Math.toRadians(ScreenProject.fov());
        var window = client.getWindow();
        double aspect = window.getFramebufferWidth() / (double) Math.max(1, window.getFramebufferHeight());
        double hfov = 2 * Math.atan(Math.tan(fov / 2) * aspect);
        double gridW = s.cell * 9;
        double gridH = s.cell * s.rows;
        // About 40% of the screen width; never closer than a block, or the
        // camera ends up inside the chest's lid.
        double dist = Math.max(0.95, Math.max((gridW / 0.42) / (2 * Math.tan(hfov / 2)),
                (gridH / 0.34) / (2 * Math.tan(fov / 2))));
        Vec3d dir = s.normal.multiply(1).add(s.down.multiply(0.42)).normalize();
        Vec3d cam = centre.add(dir.multiply(dist));
        // Don't put the camera inside a wall or ceiling.
        // Start clear of the container's own box, or the ray "hits" it straight away.
        var hit = client.world.raycast(new RaycastContext(centre.add(dir.multiply(0.75)), cam,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, client.player));
        if (hit.getType() != HitResult.Type.MISS) {
            cam = centre.add(dir.multiply(Math.max(0.8, hit.getPos().distanceTo(centre) - 0.15)));
        }
        // Aim a little toward the viewer's "down" so the grid rides high on screen.
        double visibleH = 2 * cam.distanceTo(centre) * Math.tan(fov / 2);
        Vec3d aim = centre.add(s.down.multiply(visibleH * 0.16));
        Vec3d d = aim.subtract(cam);
        s.camPos = cam;
        s.camYaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        s.camPitch = (float) Math.toDegrees(Math.asin(-d.y / d.length()));
    }

    // ---- per frame ----

    private void advance() {
        Session s = session;
        if (s == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (!s.closing && client.currentScreen != s.screen) {
            s.closing = true;
        }
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - s.lastNs) / 1e9f);
        s.lastNs = now;
        float step = (float) (dt / OPEN_SECONDS);
        if (s.closing) {
            // Turning away while it closes hands the view back faster: a
            // quick flick shouldn't have to wait for the glide.
            float turned = 0f;
            if (client.player != null) {
                if (Float.isNaN(s.closeYaw)) {
                    s.closeYaw = client.player.getYaw();
                    s.closePitch = client.player.getPitch();
                }
                turned = Math.abs(wrap(client.player.getYaw() - s.closeYaw)) + Math.abs(client.player.getPitch() - s.closePitch);
            }
            step *= 1.6f + turned / 12f;
        }
        s.t = s.closing ? Math.max(0f, s.t - step) : Math.min(1f, s.t + step);
        if (s.closing && s.t <= 0f) {
            session = null;
        }
    }

    private static float ease(float t) {
        return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    /** Called at the end of the camera's update: blend toward the view into the chest. */
    public void applyCamera(Camera camera, CameraSetter setter) {
        advance();
        Session s = session;
        if (s == null) {
            return;
        }
        float k = ease(s.t);
        Vec3d from = ScreenProject.cameraPos(camera);
        Vec3d pos = from.lerp(s.camPos, k);
        // Keep turning the same way round for the whole glide: taking the
        // shortest way each frame flips sides when you turn past 180 degrees.
        float span = wrap(s.camYaw - camera.getYaw());
        if (Float.isNaN(s.yawSpan) || Math.abs(span - s.yawSpan) > 180f) {
            float unwrapped = span;
            if (!Float.isNaN(s.yawSpan)) {
                while (unwrapped - s.yawSpan > 180f) {
                    unwrapped -= 360f;
                }
                while (unwrapped - s.yawSpan < -180f) {
                    unwrapped += 360f;
                }
            }
            span = unwrapped;
        }
        s.yawSpan = span;
        float yaw = camera.getYaw() + span * k;
        float pitch = camera.getPitch() + (s.camPitch - camera.getPitch()) * k;
        setter.set(pos, yaw, pitch);
    }

    /** Lets the camera mixin hand over its protected setters. */
    public interface CameraSetter {
        void set(Vec3d pos, float yaw, float pitch);
    }

    private static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d >= 180f) {
            d -= 360f;
        }
        if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    private Vec3d cellCentre(Session s, int i, double up) {
        int row = i / 9;
        int col = i % 9;
        return s.origin.add(s.right.multiply((col + 0.5) * s.cell)).add(s.down.multiply((row + 0.5) * s.cell))
                .add(s.normal.multiply(up));
    }

    @Override
    public void render(WorldDraw.Frame f) {
        Session s = session;
        if (s == null || s.t <= 0f) {
            return;
        }
        float k = ease(s.t);
        var window = f.client.getWindow();
        int gw = window.getScaledWidth();
        int gh = window.getScaledHeight();
        double now = System.nanoTime() / 1e9;
        int accent = dev.blurredclient.mod.Theme.chroma(dev.blurredclient.mod.ui.kit.Skins.current().accent());
        int hovered = s.closing ? -1 : hoveredIndex(s, f.client);

        for (int i = 0; i < s.slots.size(); i++) {
            Slot slot = s.slots.get(i);
            ItemStack stack = slot.getStack();
            boolean hot = i == hovered;
            s.lift[i] += ((hot ? 1f : 0f) - s.lift[i]) * Math.min(1f, f.dt * 14f);
            float lift = s.lift[i];
            // Items rise in row by row as the view opens.
            float appear = Math.max(0f, Math.min(1f, k * 1.6f - (i / 9) * 0.12f));
            if (appear <= 0f) {
                continue;
            }

            Vec3d at = cellCentre(s, i, s.cell * (0.18 + 0.55 * lift) + (1 - appear) * 0.15);
            // Where this slot is on screen, for clicks and hover.
            double[] p = ScreenProject.project(at, gw, gh);
            double[] e = ScreenProject.project(at.add(s.right.multiply(s.cell * 0.5)), gw, gh);
            if (p != null && e != null) {
                s.onScreen[i][0] = p[0];
                s.onScreen[i][1] = p[1];
                s.onScreen[i][2] = Math.hypot(e[0] - p[0], e[1] - p[1]);
            } else {
                s.onScreen[i][2] = 0;
            }
            if (stack.isEmpty()) {
                continue;
            }
            float size = (float) (s.cell * 1.12 * (0.6 + 0.4 * appear) * (1 + 0.25 * lift));
            float spin = (float) (lift * Math.sin(now * 3.0) * 12);
            int seed = i;
            f.onPlane(at, s.right, s.down, 1f, () -> {
                // Plane axes are x right, y toward the viewer's bottom, z into
                // the surface; turn the item so it faces up out of it.
                f.matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180f));
                f.matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spin));
                f.item(stack, size, seed);
            });
            if (stack.getCount() > 1) {
                Vec3d corner = cellCentre(s, i, s.cell * 0.3).add(s.right.multiply(s.cell * 0.1))
                        .add(s.down.multiply(s.cell * 0.12));
                Text count = Text.literal(String.valueOf(stack.getCount()));
                float scale = (float) (s.cell / 26.0);
                f.onPlane(corner, s.right, s.down, scale, () -> f.text2d(count, 0, 0, Colors.fade(0xFFFFFFFF, appear)));
            }
        }
    }

    /**
     * The grid: a plate and a crisp frame for every slot, so you can see
     * exactly where each one is. Drawn after the chest itself (a late
     * layer), or the chest's own inside would paint over it.
     */
    public void renderGrid(WorldDraw.Frame f) {
        Session s = session;
        if (s == null || s.t <= 0f) {
            return;
        }
        float k = ease(s.t);
        int accent = dev.blurredclient.mod.Theme.chroma(dev.blurredclient.mod.ui.kit.Skins.current().accent());
        int hovered = s.closing ? -1 : hoveredIndex(s, f.client);
        double half = s.cell * 0.46;
        double rim = s.cell * 0.07;
        for (int i = 0; i < s.slots.size(); i++) {
            float appear = Math.max(0f, Math.min(1f, k * 1.6f - (i / 9) * 0.12f));
            if (appear <= 0f) {
                continue;
            }
            boolean hot = i == hovered;
            boolean empty = s.slots.get(i).getStack().isEmpty();
            Vec3d base = cellCentre(s, i, 0.004);
            int plate = hot ? Colors.fade(accent, 0.55f) : empty ? 0x3CFFFFFF : 0x5AFFFFFF;
            int edge = hot ? accent : 0xD0FFFFFF;
            cell(f, s, base, half, Colors.fade(plate, appear));
            frame(f, s, base, half, rim, Colors.fade(edge, appear * (hot ? 1f : 0.85f)));
        }
    }

    private static Vec3d at(Session s, Vec3d base, double x, double y) {
        return base.add(s.right.multiply(x)).add(s.down.multiply(y));
    }

    private static void cell(WorldDraw.Frame f, Session s, Vec3d base, double half, int colour) {
        f.quad(at(s, base, -half, -half), at(s, base, -half, half), at(s, base, half, half), at(s, base, half, -half),
                colour, false);
    }

    private static void frame(WorldDraw.Frame f, Session s, Vec3d base, double half, double rim, int colour) {
        Vec3d b = base.add(s.normal.multiply(0.002));
        f.quad(at(s, b, -half, -half), at(s, b, -half, -half + rim), at(s, b, half, -half + rim), at(s, b, half, -half), colour, false);
        f.quad(at(s, b, -half, half - rim), at(s, b, -half, half), at(s, b, half, half), at(s, b, half, half - rim), colour, false);
        f.quad(at(s, b, -half, -half), at(s, b, -half, half), at(s, b, -half + rim, half), at(s, b, -half + rim, -half), colour, false);
        f.quad(at(s, b, half - rim, -half), at(s, b, half - rim, half), at(s, b, half, half), at(s, b, half, -half), colour, false);
    }

    private int hoveredIndex(Session s, MinecraftClient client) {
        var window = client.getWindow();
        double mx = client.mouse.getX() * window.getScaledWidth() / (double) window.getWidth();
        double my = client.mouse.getY() * window.getScaledHeight() / (double) window.getHeight();
        return pick(s, mx, my);
    }

    private static int pick(Session s, double mx, double my) {
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < s.slots.size(); i++) {
            double r = s.onScreen[i][2];
            if (r <= 0) {
                continue;
            }
            double d = Math.hypot(mx - s.onScreen[i][0], my - s.onScreen[i][1]);
            if (d < r * 1.05 && d < bestD) {
                best = i;
                bestD = d;
            }
        }
        return best;
    }

    /** Where container slot {@code i} is on screen (GUI units: x, y, radius), for tests. */
    public double[] slotOnScreen(int i) {
        return session == null || i >= session.onScreen.length ? null : session.onScreen[i].clone();
    }

    /** The container slot under the mouse, or null. */
    public Slot slotAt(Screen screen, double mx, double my) {
        if (!active(screen) || session.t < 0.9f) {
            return null;
        }
        int i = pick(session, mx, my);
        return i < 0 ? null : session.slots.get(i);
    }

    /** Is the mouse over the chest's grid (so a click there isn't "outside the GUI")? */
    public boolean overGrid(Screen screen, double mx, double my) {
        if (!active(screen)) {
            return false;
        }
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (double[] p : session.onScreen) {
            if (p[2] <= 0) {
                continue;
            }
            minX = Math.min(minX, p[0] - p[2]);
            minY = Math.min(minY, p[1] - p[2]);
            maxX = Math.max(maxX, p[0] + p[2]);
            maxY = Math.max(maxY, p[1] + p[2]);
        }
        return mx >= minX && mx <= maxX && my >= minY && my <= maxY;
    }

    // ---- the 2D part: your inventory panel ----

    /** Replaces the container screen's background: just a panel behind your own inventory. */
    public void drawPanel(DrawContext ctx, HandledScreen<?> screen) {
        var acc = (dev.blurredclient.mod.mixin.HandledScreenAccessor) screen;
        int x0 = acc.blurred$x();
        int y0 = acc.blurred$y();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot.inventory instanceof PlayerInventory) {
                minX = Math.min(minX, slot.x);
                minY = Math.min(minY, slot.y);
                maxX = Math.max(maxX, slot.x + 16);
                maxY = Math.max(maxY, slot.y + 16);
            }
        }
        if (minX == Integer.MAX_VALUE) {
            return;
        }
        float k = amount();
        int left = x0 + minX - 7;
        int top = y0 + minY - 16;
        int right = x0 + maxX + 7;
        int bottom = y0 + maxY + 7;
        int slideY = Math.round((1 - k) * 30);
        int accent = dev.blurredclient.mod.Theme.chroma(dev.blurredclient.mod.ui.kit.Skins.current().accent());
        Draw.rect(ctx, left, top + slideY, right, bottom + slideY, Colors.fade(0xE60E0F14, k));
        Draw.rect(ctx, left, top + slideY, right, top + 1 + slideY, Colors.fade(accent, k));
        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot.inventory instanceof PlayerInventory) {
                int sx = x0 + slot.x;
                int sy = y0 + slot.y + slideY;
                Draw.rect(ctx, sx, sy, sx + 16, sy + 16, Colors.fade(0x24FFFFFF, k));
            }
        }
        var font = MinecraftClient.getInstance().textRenderer;
        Draw.text(ctx, font, dev.blurredclient.mod.hud.HudStyle.text("Inventory"), left + 6, top + 4 + slideY,
                Colors.fade(0xFFB4B8C4, k), 0.75f);

        // The container's name and how full it is, as a chip at the top.
        Session s = session;
        if (s != null) {
            int used = 0;
            for (Slot slot : s.slots) {
                if (!slot.getStack().isEmpty()) {
                    used++;
                }
            }
            String label = screen.getTitle().getString() + "   " + used + " / " + s.slots.size();
            Text t = dev.blurredclient.mod.hud.HudStyle.bold(label);
            int w = Draw.width(font, t, 0.85f) + 16;
            int cx = screen.width / 2;
            int cy = 8 - Math.round((1 - k) * 20);
            Draw.rect(ctx, cx - w / 2, cy, cx + w / 2, cy + 16, Colors.fade(0xD00E0F14, k));
            Draw.rect(ctx, cx - w / 2, cy + 15, cx + w / 2, cy + 16, Colors.fade(accent, k));
            Draw.text(ctx, font, t, cx - w / 2 + 8, cy + 4, Colors.fade(0xFFFFFFFF, k), 0.85f);
        }
    }
}
