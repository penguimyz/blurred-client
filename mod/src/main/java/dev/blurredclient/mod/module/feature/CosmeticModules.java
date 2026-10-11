package dev.blurredclient.mod.module.feature;

import dev.blurredclient.mod.fx.CombatEvents;
import dev.blurredclient.mod.fx.Ent;
import dev.blurredclient.mod.fx.WorldDraw;
import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.ColorSetting;
import dev.blurredclient.mod.setting.ModeSetting;
import dev.blurredclient.mod.setting.SliderSetting;
import dev.blurredclient.mod.setting.TextSetting;
import dev.blurredclient.mod.ui.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Random;

import static dev.blurredclient.mod.module.feature.EffectsModules.alpha;
import static dev.blurredclient.mod.module.feature.EffectsModules.seconds;
import static dev.blurredclient.mod.module.feature.EffectsModules.tint;

/** Cosmetics drawn in the world: a hat, a pet, a new sky, better name tags. Only you see them. */
public final class CosmeticModules {
    private CosmeticModules() {}

    /** A conical straw hat ("Chinese hat") on your head, tilting with your head. */
    public static final class ChineseHat extends Module implements WorldDraw.Layer {
        public enum Look {
            STRAW("Straw"), THEME("Theme"), RAINBOW("Rainbow"), CUSTOM("Custom");

            private final String label;

            Look(String label) {
                this.label = label;
            }

            public String label() {
                return label;
            }
        }

        private final ModeSetting<Look> look;
        private final ColorSetting custom;
        private final SliderSetting size;
        private final BoolSetting others;

        public ChineseHat() {
            super("china_hat", "Chinese hat", "A conical hat on your head, seen in third person. Only you see it.",
                    Category.COSMETIC, false);
            this.look = add(new ModeSetting<>("look", "Look", "Woven straw, your theme colour, rainbow or your own.", Look.STRAW, Look::label));
            this.custom = add(new ColorSetting("custom", "Custom colour", "Used when Look is Custom.", 0xFF26B4E6));
            custom.visibleIf(() -> look.get() == Look.CUSTOM);
            this.size = add(new SliderSetting("size", "Size", "How wide the brim is.", 1.0, 0.6, 1.6, 0.05, "x"));
            this.others = add(new BoolSetting("others", "On other players too", "Put one on everyone (on your screen only).", false));
            // After players are drawn, depth-tested against them: drawn before, the
            // head (which comes later) would paint over the hat wherever they overlap.
            WorldDraw.addLate(this);
        }

        @Override
        public void render(WorldDraw.Frame f) {
            if (!isActive() || f.client.world == null) {
                return;
            }
            for (PlayerEntity p : f.client.world.getPlayers()) {
                boolean self = p == f.client.player;
                if (!self && !others.isOn() || p.isInvisible()) {
                    continue;
                }
                if (self && f.client.options.getPerspective().isFirstPerson()) {
                    continue;
                }
                // Crawling, swimming and gliding tip the whole body over, so the
                // head isn't above the neck any more: the hat shrinks away then.
                boolean upright = (p.getPose() == net.minecraft.entity.EntityPose.STANDING || p.isInSneakingPose())
                        && !gliding(p);
                float k = shown.getOrDefault(p.getId(), upright ? 1f : 0f);
                k += ((upright ? 1f : 0f) - k) * Math.min(1f, f.dt * 10f);
                shown.put(p.getId(), k);
                if (k > 0.02f) {
                    hat(f, p, k);
                }
            }
        }

        private final java.util.Map<Integer, Float> shown = new java.util.HashMap<>();

        private static boolean gliding(PlayerEntity p) {
            //? if >=1.21.4 {
            return p.isGliding();
            //?} else {
            /*return p.isFallFlying();
            *///?}
        }

        private void hat(WorldDraw.Frame f, PlayerEntity p, float k) {
            // The head turns about the neck: 24 model pixels up, lowered 4.2
            // while sneaking, all times the player model's 15/16 scale and
            // the player's own size.
            float scale = (float) (p.getScale() * 0.9375);
            // Sneaking also lowers the whole model by 2 pixels.
            double neck = (24 - (p.isInSneakingPose() ? 4.2 + 2 : 0)) / 16.0 * scale;
            Vec3d pivot = p.getLerpedPos(f.tickDelta).add(0, neck, 0);
            //? if >=1.21.8 {
            float yaw = net.minecraft.util.math.MathHelper.lerpAngleDegrees(f.tickDelta, p.lastHeadYaw, p.headYaw);
            //?} else {
            /*float yaw = net.minecraft.util.math.MathHelper.lerpAngleDegrees(f.tickDelta, p.prevHeadYaw, p.headYaw);
            *///?}
            float pitch = p.getPitch(f.tickDelta);
            float r = 0.62f * size.floatValue() * scale / 0.9375f;
            f.pushAt(pivot);
            f.matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
            f.matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
            f.matrices.scale(k, k, k);
            // The head's top is 8 model pixels above the neck; the brim sits just below it so the hat rests on.
            float baseY = 8 / 16f * scale - 0.075f;
            float apexY = baseY + 0.27f * size.floatValue() * scale / 0.9375f;
            int segments = 36;
            double t = seconds();
            for (int i = 0; i < segments; i++) {
                double a0 = i * Math.PI * 2 / segments;
                double a1 = (i + 1) * Math.PI * 2 / segments;
                int c = colour(i, segments, t);
                int rim = WorldDraw.Frame.shade(c, 0.7f);
                float x0 = (float) Math.cos(a0) * r;
                float z0 = (float) Math.sin(a0) * r;
                float x1 = (float) Math.cos(a1) * r;
                float z1 = (float) Math.sin(a1) * r;
                // Top surface (apex to rim) and the underside, slightly darker.
                f.localQuad(0, apexY, 0, x0, baseY, z0, x1, baseY, z1, 0, apexY, 0, c);
                f.localQuad(0, apexY - 0.03f, 0, x1, baseY - 0.02f, z1, x0, baseY - 0.02f, z0, 0, apexY - 0.03f, 0,
                        WorldDraw.Frame.shade(c, 0.55f));
                // A thin rim band.
                f.localQuad(x0, baseY, z0, x1, baseY, z1, x1, baseY - 0.025f, z1, x0, baseY - 0.025f, z0, rim);
            }
            f.pop();
        }

        private int colour(int i, int n, double t) {
            return switch (look.get()) {
                case STRAW -> i % 2 == 0 ? 0xFFD9B46C : 0xFFC59B55;
                case THEME -> dev.blurredclient.mod.Theme.chroma(dev.blurredclient.mod.ui.kit.Skins.current().accent(), i / (float) n);
                case RAINBOW -> Colors.hsv((float) (i / (double) n + t * 0.1), 0.6f, 1f);
                case CUSTOM -> custom.argb();
            };
        }
    }

    // ------------------------------------------------------------------

    /**
     * A little pet that follows you around: a dog, cat, penguin, fox or
     * bunny, built from blocks like a mini model, walking, hopping and
     * wagging. Not a Minecraft mob — it only exists on your screen.
     */
    public static final class Pets extends Module implements WorldDraw.Layer, Ticking {
        public enum Kind {
            DOG("Wolf pup", false), CAT("Kitten", false), FOX("Fox kit", false), BUNNY("Bunny", false),
            AXOLOTL("Axolotl", false), FROG("Frog", false), PENGUIN("Penguin", false),
            ALLAY("Allay", true), PARROT("Parrot", true), BEE("Bee", true);

            private final String label;
            /** Flies alongside your shoulder instead of walking behind you. */
            final boolean flies;

            Kind(String label, boolean flies) {
                this.label = label;
                this.flies = flies;
            }

            public String label() {
                return label;
            }
        }

        private final ModeSetting<Kind> kind;
        private final SliderSetting size;
        private final TextSetting name;
        private final BoolSetting dance;
        private float dancing;
        private Vec3d pos;
        private float yaw;
        private float followYaw = Float.NaN;
        private float walk;
        private float moving;
        private double ground;
        private final Random random = new Random();
        private double nextGlance;
        private float headTurn;

        private Vec3d lastPos;

        public Pets() {
            super("pets", "Pet", "A wolf pup, kitten, fox kit, bunny, allay, parrot, bee or penguin that follows you. Only you see it.",
                    Category.COSMETIC, false);
            this.kind = add(new ModeSetting<>("kind", "Pet", "Which animal.", Kind.DOG, Kind::label));
            this.size = add(new SliderSetting("size", "Size", "How big it is.", 1.0, 0.6, 1.6, 0.05, "x"));
            this.name = add(new TextSetting("name", "Name", "Shown above it. Leave empty for none.", "", 24));
            this.dance = add(new BoolSetting("dance", "Dance to music",
                    "When music is playing (anything Now playing can see) and it's not following you, it dances.", true));
            WorldDraw.add(this);
        }

        @Override
        protected void onDisable() {
            pos = null;
            dev.blurredclient.mod.fx.FakePets.clear();
        }

        /** Real Minecraft model for this kind (1.21.10+), or null for the block-built one. */
        private net.minecraft.entity.Entity model() {
            if (!dev.blurredclient.mod.fx.FakePets.supported() || kind.get() == Kind.PENGUIN) {
                return null;
            }
            return dev.blurredclient.mod.fx.FakePets.get(kind.get().name());
        }

        @Override
        public void tick(MinecraftClient client) {
            var e = model();
            if (e != null && pos != null) {
                float speed = lastPos == null ? 0f : (float) pos.subtract(lastPos).horizontalLength();
                lastPos = pos;
                dev.blurredclient.mod.fx.FakePets.tick(e, speed);
            }
        }

        @Override
        public void render(WorldDraw.Frame f) {
            ClientPlayerEntity p = f.client.player;
            if (!isActive() || p == null || f.client.world == null) {
                pos = null;
                return;
            }
            float dt = f.dt;
            Vec3d me = p.getLerpedPos(f.tickDelta);
            // Which way "behind you" is follows where you walk, not where you
            // look: turning on the spot shouldn't send the pet running round you.
            Vec3d vel = p.getVelocity();
            boolean walking = vel.horizontalLengthSquared() > 0.0015;
            if (Float.isNaN(followYaw)) {
                followYaw = p.getYaw();
            } else if (walking) {
                float travel = (float) Math.toDegrees(Math.atan2(-vel.x, vel.z));
                followYaw += MathHelper.wrapDegrees(travel - followYaw) * Math.min(1f, dt * 3f);
            }
            double yawRad = Math.toRadians(followYaw);
            Vec3d back = new Vec3d(Math.sin(yawRad), 0, -Math.cos(yawRad));
            Vec3d right = new Vec3d(-Math.cos(yawRad), 0, -Math.sin(yawRad));
            boolean flies = kind.get().flies;
            Vec3d goal = flies
                    ? me.add(back.multiply(0.35)).add(right.multiply(0.75)).add(0, p.getStandingEyeHeight() - 0.15, 0)
                    : me.add(back.multiply(1.1)).add(right.multiply(0.9));
            if (pos == null || pos.squaredDistanceTo(me) > 14 * 14) {
                pos = goal;
                ground = goal.y;
            }
            // Standing still: stay put while it's at a comfortable distance.
            double fromMe = new Vec3d(pos.x - me.x, 0, pos.z - me.z).length();
            if (!walking && !flies && fromMe > 0.7 && fromMe < 2.6) {
                goal = new Vec3d(pos.x, goal.y, pos.z);
            }
            Vec3d flat = new Vec3d(goal.x - pos.x, 0, goal.z - pos.z);
            double dist = flat.length();
            float want = dist > 0.35 ? 1f : 0f;
            moving += (want - moving) * Math.min(1f, dt * 6f);
            if (dist > 0.05) {
                double speed = Math.min(dist * 3.2, 9);
                Vec3d step = flat.normalize().multiply(Math.min(dist, speed * dt));
                pos = pos.add(step);
                if (moving > 0.3f) {
                    float target = (float) Math.toDegrees(Math.atan2(-flat.x, flat.z));
                    yaw += MathHelper.wrapDegrees(target - yaw) * Math.min(1f, dt * 8f);
                }
                walk += (float) (step.length() * 9);
            }
            if (moving < 0.3f) {
                // Idle: turn to look at you now and then.
                Vec3d toMe = me.subtract(pos);
                float target = (float) Math.toDegrees(Math.atan2(-toMe.x, toMe.z));
                yaw += MathHelper.wrapDegrees(target - yaw) * Math.min(1f, dt * 2f);
            }
            if (flies) {
                // Hover by your shoulder, bobbing gently.
                double bob = Math.sin(seconds() * 2.4) * 0.08;
                ground += (goal.y + bob - ground) * Math.min(1, dt * 6);
            } else {
                // Stand on the ground under it.
                Vec3d top = new Vec3d(pos.x, me.y + 2, pos.z);
                var hit = f.client.world.raycast(new RaycastContext(top, top.add(0, -6, 0), RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE, p));
                double floor = hit.getType() == HitResult.Type.MISS ? me.y : hit.getPos().y;
                ground += (floor - ground) * Math.min(1, dt * 12);
            }
            pos = new Vec3d(pos.x, ground, pos.z);

            // Dancing: bounce on the beat, sway and wiggle, while music plays and it's standing still.
            boolean music = dance.isOn() && musicPlaying();
            dancing += ((music && moving < 0.3f ? 1f : 0f) - dancing) * Math.min(1f, dt * 4f);
            Vec3d drawAt = pos;
            float drawYaw = yaw;
            if (dancing > 0.01f) {
                double beat = seconds() * Math.PI * 2 * 2.0;  // about 120 beats a minute
                double bounce = Math.abs(Math.sin(beat / 2)) * 0.14 * dancing;
                drawAt = pos.add(Math.sin(beat / 4) * 0.08 * dancing, bounce, 0);
                drawYaw = yaw + (float) Math.sin(beat / 4) * 35f * dancing;
                if (kind.get().flies) {
                    // Flyers loop in little circles instead.
                    drawAt = pos.add(Math.cos(beat / 4) * 0.25 * dancing, Math.sin(beat / 2) * 0.12 * dancing,
                            Math.sin(beat / 4) * 0.25 * dancing);
                }
            }

            var model = model();
            if (model != null) {
                // Minecraft's own model: lit, textured and animated by the game.
                f.matrices.push();
                float sz = size.floatValue();
                if (sz != 1f) {
                    Vec3d rel = pos.subtract(f.cam);
                    f.matrices.translate((float) rel.x, (float) rel.y, (float) rel.z);
                    f.matrices.scale(sz, sz, sz);
                    f.matrices.translate((float) -rel.x, (float) -rel.y, (float) -rel.z);
                }
                dev.blurredclient.mod.fx.FakePets.render(f, model, drawAt, drawYaw);
                f.matrices.pop();
                label(f);
                return;
            }

            double now = seconds();
            if (now > nextGlance) {
                nextGlance = now + 2 + random.nextDouble() * 4;
                headTurn = (random.nextFloat() - 0.5f) * 50;
            }
            float light = brightness(f.client, pos);
            float s = size.floatValue() / 16f;
            f.pushAt(drawAt);
            f.matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-drawYaw));
            if (dancing > 0.01f) {
                f.matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin(seconds() * Math.PI * 2) * 12f * dancing));
            }
            float hop = 0f;
            if (kind.get() == Kind.BUNNY) {
                hop = (float) Math.abs(Math.sin(walk * 0.5)) * 0.25f * moving;
            }
            f.matrices.translate(0, hop, 0);
            f.matrices.scale(s, s, s);
            switch (kind.get()) {
                case FOX -> fox(f, light, now);
                case CAT -> cat(f, light, now);
                case PENGUIN -> penguin(f, light, now);
                case BUNNY, FROG, AXOLOTL -> bunny(f, light, now);
                default -> dog(f, light, now, 0xFFC08A55, 0xFF8C5E36);
            }
            f.pop();
            // Soft contact shadow so it sits on the ground.
            f.groundRing(pos.add(0, 0.02, 0), 0, 0.32 * size.floatValue(), 0x55000000, 0x00000000, 24, false);
            label(f);
        }

        /** Keep the media watcher running while pets may dance. */
        public boolean wantsMedia() {
            return isActive() && dance.isOn();
        }

        private static boolean musicPlaying() {
            dev.blurredclient.mod.hud.SystemMedia.ensureRunning();
            var t = dev.blurredclient.mod.hud.SystemMedia.current();
            return t != null && t.playing();
        }

        private void label(WorldDraw.Frame f) {
            String label = name.get();
            if (label != null && !label.isBlank()) {
                double h = kind.get().flies ? 0.75 : 0.95;
                f.billboardText(pos.add(0, h * size.floatValue(), 0), Text.literal(label), 0.016f, 0xFFFFFFFF, 0x80000000);
            }
        }

        private static float brightness(MinecraftClient client, Vec3d at) {
            int level = client.world.getLightLevel(BlockPos.ofFloored(at.add(0, 0.5, 0)));
            return 0.35f + 0.65f * level / 15f;
        }

        /** A box rotated by {@code pitch} degrees around a pivot (all in pixels, 1/16 block). */
        private static void part(WorldDraw.Frame f, float px, float py, float pz, float pitch, float roll,
                                 float x0, float y0, float z0, float x1, float y1, float z1, int colour, float light) {
            f.matrices.push();
            f.matrices.translate(px, py, pz);
            if (pitch != 0) {
                f.matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
            }
            if (roll != 0) {
                f.matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
            }
            f.box(x0 - px, y0 - py, z0 - pz, x1 - px, y1 - py, z1 - pz, WorldDraw.Frame.shade(colour, light));
            f.matrices.pop();
        }

        private float legSwing() {
            return (float) Math.sin(walk) * 40f * moving;
        }

        private void legs(WorldDraw.Frame f, float light, int c, float w, float h, float front, float backZ) {
            float s = legSwing();
            part(f, -w, h, front, s, 0, -w - 1, 0, front - 1, -w + 1, h, front + 1, c, light);
            part(f, w, h, front, -s, 0, w - 1, 0, front - 1, w + 1, h, front + 1, c, light);
            part(f, -w, h, backZ, -s, 0, -w - 1, 0, backZ - 1, -w + 1, h, backZ + 1, c, light);
            part(f, w, h, backZ, s, 0, w - 1, 0, backZ - 1, w + 1, h, backZ + 1, c, light);
        }

        private void dog(WorldDraw.Frame f, float light, double now, int coat, int dark) {
            legs(f, light, coat, 2, 4, 3.5f, -3.5f);
            part(f, 0, 7, 0, 0, 0, -3, 4, -5, 3, 10, 5, coat, light);
            float look = headTurn * (1 - moving);
            f.matrices.push();
            f.matrices.translate(0, 9, 5);
            f.matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(look));
            part(f, 0, 0, 0, 0, 0, -3, -1, 0, 3, 5, 5, coat, light);
            part(f, 0, 0, 0, 0, 0, -1.5f, -1, 5, 1.5f, 1.5f, 8, dark, light);
            part(f, 0, 0, 0, 0, 0, -0.6f, 0.6f, 8, 0.6f, 1.6f, 8.3f, 0xFF1A1A1A, light);
            part(f, 0, 0, 0, 0, 0, -3, 3, 1, -1.5f, 6, 2.5f, dark, light);
            part(f, 0, 0, 0, 0, 0, 1.5f, 3, 1, 3, 6, 2.5f, dark, light);
            part(f, 0, 0, 0, 0, 0, -2.2f, 2, 4.9f, -1.2f, 3, 5.1f, 0xFF111111, light);
            part(f, 0, 0, 0, 0, 0, 1.2f, 2, 4.9f, 2.2f, 3, 5.1f, 0xFF111111, light);
            f.matrices.pop();
            float wag = (float) Math.sin(now * (moving > 0.3f ? 14 : 8)) * 35;
            f.matrices.push();
            f.matrices.translate(0, 9, -5);
            f.matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(wag));
            part(f, 0, 0, 0, -40, 0, -0.75f, -0.75f, -5, 0.75f, 0.75f, 0, coat, light);
            f.matrices.pop();
        }

        private void fox(WorldDraw.Frame f, float light, double now) {
            int orange = 0xFFE07B2E;
            legs(f, light, 0xFF2A1A12, 1.8f, 3.5f, 3, -3);
            part(f, 0, 6, 0, 0, 0, -2.5f, 3.5f, -4.5f, 2.5f, 8.5f, 4.5f, orange, light);
            f.matrices.push();
            f.matrices.translate(0, 8, 4.5f);
            f.matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(headTurn * (1 - moving)));
            part(f, 0, 0, 0, 0, 0, -3, -1.5f, 0, 3, 3.5f, 4, orange, light);
            part(f, 0, 0, 0, 0, 0, -1.2f, -1.5f, 4, 1.2f, 0.5f, 7, 0xFFF2EDE6, light);
            part(f, 0, 0, 0, 0, 0, -0.5f, -0.3f, 7, 0.5f, 0.5f, 7.3f, 0xFF111111, light);
            part(f, 0, 0, 0, 0, 0, -3, 3.5f, 1, -1.2f, 6.5f, 2.2f, orange, light);
            part(f, 0, 0, 0, 0, 0, 1.2f, 3.5f, 1, 3, 6.5f, 2.2f, orange, light);
            part(f, 0, 0, 0, 0, 0, -2.2f, 1, 3.9f, -1.2f, 2, 4.1f, 0xFF111111, light);
            part(f, 0, 0, 0, 0, 0, 1.2f, 1, 3.9f, 2.2f, 2, 4.1f, 0xFF111111, light);
            f.matrices.pop();
            float sway = (float) Math.sin(now * 3) * 15;
            f.matrices.push();
            f.matrices.translate(0, 7, -4.5f);
            f.matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sway));
            part(f, 0, 0, 0, -20, 0, -2, -2, -8, 2, 2, 0, orange, light);
            part(f, 0, 0, 0, -20, 0, -2.05f, -2.05f, -10, 2.05f, 2.05f, -8, 0xFFF2EDE6, light);
            f.matrices.pop();
        }

        private void cat(WorldDraw.Frame f, float light, double now) {
            int fur = 0xFF5B5B66;
            legs(f, light, fur, 1.5f, 4, 3, -3.5f);
            part(f, 0, 6, 0, 0, 0, -2, 4, -5, 2, 8, 4.5f, fur, light);
            f.matrices.push();
            f.matrices.translate(0, 8, 4.5f);
            f.matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(headTurn * (1 - moving)));
            part(f, 0, 0, 0, 0, 0, -2.5f, -1, 0, 2.5f, 3.5f, 4, fur, light);
            part(f, 0, 0, 0, 0, 0, -1, -1, 4, 1, 0.5f, 5, 0xFFB0B0BA, light);
            part(f, 0, 0, 0, 0, 0, -2.5f, 3.5f, 1, -1, 5, 2, fur, light);
            part(f, 0, 0, 0, 0, 0, 1, 3.5f, 1, 2.5f, 5, 2, fur, light);
            part(f, 0, 0, 0, 0, 0, -1.8f, 1.5f, 3.9f, -0.8f, 2.5f, 4.1f, 0xFF7CD957, light);
            part(f, 0, 0, 0, 0, 0, 0.8f, 1.5f, 3.9f, 1.8f, 2.5f, 4.1f, 0xFF7CD957, light);
            f.matrices.pop();
            // Tail up in a curve.
            float sway = (float) Math.sin(now * 2.2) * 12;
            f.matrices.push();
            f.matrices.translate(0, 7.5f, -5);
            f.matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sway));
            part(f, 0, 0, 0, -50, 0, -0.5f, -0.5f, -6, 0.5f, 0.5f, 0, fur, light);
            f.matrices.pop();
        }

        private void penguin(WorldDraw.Frame f, float light, double now) {
            int back = 0xFF1B2130;
            int backLight = 0xFF2A3346;
            int white = 0xFFF6F4EE;
            int cream = 0xFFFFF1D6;
            int orange = 0xFFF2A23A;
            int yolk = 0xFFFFC85A;
            float waddle = (float) Math.sin(walk) * 9 * moving;
            float bob = (float) Math.abs(Math.sin(walk)) * 0.5f * moving;
            f.matrices.push();
            f.matrices.translate(0, bob, 0);
            f.matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(waddle));
            // A rounded, egg-shaped body: wider in the middle, stacked in layers.
            float[][] layers = {{1, 2.6f, 2.2f}, {2.5f, 3.2f, 2.7f}, {4.5f, 3.4f, 2.9f}, {6.5f, 3.3f, 2.8f}, {8.5f, 3.0f, 2.6f}, {10, 2.6f, 2.3f}};
            for (int i = 0; i < layers.length - 1; i++) {
                float y0 = layers[i][0];
                float y1 = layers[i + 1][0];
                float hw = layers[i][1];
                float hd = layers[i][2];
                part(f, 0, 0, 0, 0, 0, -hw, y0, -hd, hw, y1, hd * 0.85f, i % 2 == 0 ? back : backLight, light);
                // The belly: cream low down, white higher up.
                int belly = Colors.lerp(cream, white, i / (float) (layers.length - 2));
                part(f, 0, 0, 0, 0, 0, -hw * 0.72f, y0, hd * 0.85f - 0.01f, hw * 0.72f, y1, hd + 0.05f, belly, light);
            }
            // Tail.
            part(f, 0, 0, 0, 0, 0, -1, 1, -2.9f, 1, 2, -2.2f, back, light);
            // Head, turning a little now and then, tilting while idle.
            float look = (float) Math.sin(now * 0.7) * 25 * (1 - moving);
            float tilt = (float) Math.sin(now * 1.3) * 6 * (1 - moving);
            f.matrices.push();
            f.matrices.translate(0, 10, 0);
            f.matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(look));
            f.matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(tilt));
            part(f, 0, 0, 0, 0, 0, -2.4f, 0, -2.1f, 2.4f, 3.4f, 2.1f, back, light);
            part(f, 0, 0, 0, 0, 0, -2.0f, 3.4f, -1.8f, 2.0f, 4.2f, 1.8f, backLight, light);
            // Emperor-penguin cheek patches and a white chin.
            part(f, 0, 0, 0, 0, 0, -2.45f, 0.6f, -0.6f, -2.35f, 2.2f, 1.2f, yolk, light);
            part(f, 0, 0, 0, 0, 0, 2.35f, 0.6f, -0.6f, 2.45f, 2.2f, 1.2f, yolk, light);
            part(f, 0, 0, 0, 0, 0, -1.6f, 0, 2.05f, 1.6f, 1.3f, 2.2f, white, light);
            // Eyes, with a glint; blinking every few seconds.
            boolean blink = (now % 3.7) < 0.12;
            float eyeH = blink ? 0.15f : 0.8f;
            for (int sx = -1; sx <= 1; sx += 2) {
                part(f, 0, 0, 0, 0, 0, sx * 1.55f - 0.45f, 2.1f, 2.05f, sx * 1.55f + 0.45f, 2.1f + eyeH, 2.2f, 0xFF0D0D10, light);
                if (!blink) {
                    part(f, 0, 0, 0, 0, 0, sx * 1.55f - 0.3f, 2.55f, 2.2f, sx * 1.55f - 0.05f, 2.8f, 2.26f, 0xFFFFFFFF, 1f);
                }
            }
            // Beak: dark on top, orange underneath, a little open while it's walking.
            float open = moving > 0.4f ? (float) Math.max(0, Math.sin(now * 8)) * 0.25f : 0f;
            part(f, 0, 0, 0, 0, 0, -0.7f, 1.6f, 2.1f, 0.7f, 2.1f, 3.9f, 0xFF2A2A30, light);
            part(f, 0, 0, 0, 0, 0, -0.6f, 1.15f - open, 2.1f, 0.6f, 1.6f - open, 3.6f, orange, light);
            f.matrices.pop();
            // Flippers: two segments each, flapping when it hurries.
            float flap = (float) Math.sin(now * 7) * (6 + 24 * moving) + 12;
            for (int sx = -1; sx <= 1; sx += 2) {
                f.matrices.push();
                f.matrices.translate(sx * 3.2f, 8.5f, 0);
                f.matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sx * flap));
                part(f, 0, 0, 0, 0, 0, sx > 0 ? 0 : -0.7f, -3.5f, -1.4f, sx > 0 ? 0.7f : 0, 0, 1.4f, back, light);
                part(f, 0, 0, 0, 0, 0, sx > 0 ? 0 : -0.6f, -6f, -1f, sx > 0 ? 0.6f : 0, -3.5f, 1.0f, backLight, light);
                f.matrices.pop();
            }
            f.matrices.pop();
            // Webbed feet with toes, stepping in turn.
            float s = legSwing() * 0.5f;
            for (int sx = -1; sx <= 1; sx += 2) {
                float swing = sx < 0 ? s : -s;
                part(f, sx * 1.4f, 1, 0, swing, 0, sx * 1.4f - 1.1f, 0, -0.6f, sx * 1.4f + 1.1f, 0.8f, 2.2f, orange, light);
                part(f, sx * 1.4f, 1, 0, swing, 0, sx * 1.4f - 1.2f, 0, 2.2f, sx * 1.4f - 0.5f, 0.6f, 3.0f, orange, light);
                part(f, sx * 1.4f, 1, 0, swing, 0, sx * 1.4f + 0.5f, 0, 2.2f, sx * 1.4f + 1.2f, 0.6f, 3.0f, orange, light);
            }
        }

        private void bunny(WorldDraw.Frame f, float light, double now) {
            int fur = 0xFFE9E3DA;
            part(f, 0, 3, 0, 0, 0, -2.5f, 1, -3.5f, 2.5f, 6, 3, fur, light);
            part(f, 0, 0, 0, 0, 0, -2.5f, 0, -3, -1, 1.5f, 0, fur, light);
            part(f, 0, 0, 0, 0, 0, 1, 0, -3, 2.5f, 1.5f, 0, fur, light);
            part(f, 0, 0, 0, 0, 0, -1.5f, 0, 1, -0.5f, 1.5f, 3, fur, light);
            part(f, 0, 0, 0, 0, 0, 0.5f, 0, 1, 1.5f, 1.5f, 3, fur, light);
            part(f, 0, 6, 3, 0, 0, -2, 4.5f, 2, 2, 8, 5.5f, fur, light);
            float ear = (float) Math.sin(now * 2) * 6 - 10 * moving;
            part(f, -1, 8, 3.5f, ear, 0, -1.6f, 8, 3, -0.4f, 13, 4, fur, light);
            part(f, 1, 8, 3.5f, ear, 0, 0.4f, 8, 3, 1.6f, 13, 4, fur, light);
            part(f, 0, 0, 0, 0, 0, -1.2f, 7.2f, 5.4f, -0.4f, 7.8f, 5.6f, 0xFF111111, light);
            part(f, 0, 0, 0, 0, 0, 0.4f, 7.2f, 5.4f, 1.2f, 7.8f, 5.6f, 0xFF111111, light);
            part(f, 0, 0, 0, 0, 0, -0.4f, 6, 5.5f, 0.4f, 6.6f, 5.8f, 0xFFF29AA8, light);
            part(f, 0, 0, 0, 0, 0, -1, 3, -4.5f, 1, 5, -3.5f, 0xFFFFFFFF, light);
        }
    }

    // ------------------------------------------------------------------

    /**
     * A new sky: painted gradients (sunset, ocean, vaporwave, aurora night,
     * void) on a dome around you, with stars, a sun and moon, and optional
     * aurora curtains. Follows the day and night cycle if you like.
     */
    public static final class CustomSky extends Module implements WorldDraw.Layer {
        public enum Preset {
            SUNSET("Sunset", 0xFF2B1F5C, 0xFFFF8A5B, 0xFF0B0B1E, 0xFF5A2A6E),
            OCEAN("Ocean", 0xFF0F4C81, 0xFF9AD8F2, 0xFF050B1A, 0xFF13325A),
            VAPORWAVE("Vaporwave", 0xFF3A1C71, 0xFFFF6BCB, 0xFF120A2A, 0xFF5B2A8A),
            AURORA("Aurora night", 0xFF0A1A3A, 0xFF2A6F8F, 0xFF02040C, 0xFF0B2A3A),
            VOID("Void", 0xFF05050A, 0xFF1A1030, 0xFF000000, 0xFF0A0614),
            CUSTOM("Custom", 0, 0, 0, 0);

            private final String label;
            final int dayTop;
            final int dayHorizon;
            final int nightTop;
            final int nightHorizon;

            Preset(String label, int dayTop, int dayHorizon, int nightTop, int nightHorizon) {
                this.label = label;
                this.dayTop = dayTop;
                this.dayHorizon = dayHorizon;
                this.nightTop = nightTop;
                this.nightHorizon = nightHorizon;
            }

            public String label() {
                return label;
            }
        }

        private final ModeSetting<Preset> preset;
        private final ColorSetting top;
        private final ColorSetting horizon;
        private final BoolSetting followDay;
        private final BoolSetting stars;
        private final BoolSetting aurora;
        private final BoolSetting sunMoon;
        private float[][] starField;

        public CustomSky() {
            super("custom_sky", "Custom sky", "Paints a new sky: sunset, ocean, vaporwave, aurora night or void, with stars and aurora.",
                    Category.VISUAL, false);
            this.preset = add(new ModeSetting<>("preset", "Sky", "The look.", Preset.SUNSET, Preset::label));
            this.top = add(new ColorSetting("top", "Top colour", "Used when Sky is Custom.", 0xFF1B2A6B));
            this.horizon = add(new ColorSetting("horizon", "Horizon colour", "Used when Sky is Custom.", 0xFFFF9E6B));
            top.visibleIf(() -> preset.get() == Preset.CUSTOM);
            horizon.visibleIf(() -> preset.get() == Preset.CUSTOM);
            this.followDay = add(new BoolSetting("follow_day", "Follow day and night", "Darken toward the night colours after sunset.", true));
            this.stars = add(new BoolSetting("stars", "Stars", "Twinkling stars when it's dark.", true));
            this.aurora = add(new BoolSetting("aurora", "Aurora", "Northern-lights curtains across the sky.", false));
            this.sunMoon = add(new BoolSetting("sun_moon", "Sun and moon", "Draw a glowing sun and moon.", true));
            WorldDraw.add(this);
        }

        @Override
        public void render(WorldDraw.Frame f) {
            if (!isActive() || f.client.world == null) {
                return;
            }
            if (!f.client.world.getDimension().hasSkyLight() || f.client.world.getDimension().hasCeiling()) {
                return;
            }
            float angle = skyAngle(f.client.world.getTimeOfDay() + f.tickDelta);
            float day = MathHelper.clamp((float) Math.cos(angle * Math.PI * 2) * 2f + 0.5f, 0f, 1f);
            if (!followDay.isOn()) {
                day = 1f;
            }
            Preset p = preset.get();
            int topC = p == Preset.CUSTOM ? top.argb() : Colors.lerp(p.nightTop, p.dayTop, day);
            int horC = p == Preset.CUSTOM ? horizon.argb() : Colors.lerp(p.nightHorizon, p.dayHorizon, day);
            double r = Math.min(f.client.options.getClampedViewDistance() * 16 + 48, 900);
            Vec3d c = f.cam;
            int rings = 10;
            int segs = 32;
            for (int i = 0; i < rings; i++) {
                double e0 = Math.toRadians(-8 + i * 98.0 / rings);
                double e1 = Math.toRadians(-8 + (i + 1) * 98.0 / rings);
                int c0 = colourAt(e0, topC, horC);
                int c1 = colourAt(e1, topC, horC);
                for (int j = 0; j < segs; j++) {
                    double a0 = j * Math.PI * 2 / segs;
                    double a1 = (j + 1) * Math.PI * 2 / segs;
                    f.quad(sphere(c, r, e0, a0), sphere(c, r, e0, a1), sphere(c, r, e1, a1), sphere(c, r, e1, a0),
                            c0, c0, c1, c1, false);
                }
            }
            float dark = 1f - day;
            if (stars.isOn() && dark > 0.05f) {
                starsAt(f, c, r * 0.98, dark);
            }
            if (aurora.isOn()) {
                auroraAt(f, c, r * 0.9, 0.35f + 0.65f * dark);
            }
            if (sunMoon.isOn()) {
                double sa = angle * Math.PI * 2;
                Vec3d sunDir = new Vec3d(-Math.sin(sa), Math.cos(sa), 0).normalize();
                Vec3d up = new Vec3d(0, 0, 1);
                Vec3d side = sunDir.crossProduct(up).normalize();
                disc(f, c.add(sunDir.multiply(r * 0.95)), side, up, r * 0.05, 0xFFFFF4D6, 0xFFFFB45A);
                disc(f, c.subtract(sunDir.multiply(r * 0.95)), side, up, r * 0.03, 0xFFE8EEFF, 0xFF8AA0D0);
            }
        }

        /** Vanilla's sun angle (0..1 of a turn, 0 at noon) from the time of day. */
        private static float skyAngle(double time) {
            double d = MathHelper.fractionalPart(time / 24000.0 - 0.25);
            double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
            return (float) (d * 2.0 + e) / 3.0f;
        }

        private static int colourAt(double elevation, int top, int horizon) {
            double k = Math.max(0, Math.sin(elevation));
            int c = Colors.lerp(horizon, top, (float) Math.pow(k, 0.55));
            // Fade out just below the horizon so terrain and fog show through.
            float a = elevation < 0 ? (float) Math.max(0, 1 + elevation / Math.toRadians(8)) : 1f;
            return alpha(c, a);
        }

        private static Vec3d sphere(Vec3d c, double r, double elevation, double azimuth) {
            double ce = Math.cos(elevation);
            return c.add(Math.cos(azimuth) * ce * r, Math.sin(elevation) * r, Math.sin(azimuth) * ce * r);
        }

        private void starsAt(WorldDraw.Frame f, Vec3d c, double r, float dark) {
            if (starField == null) {
                Random rnd = new Random(1337);
                starField = new float[260][4];
                for (float[] s : starField) {
                    s[0] = (float) (rnd.nextDouble() * Math.PI * 2);
                    s[1] = (float) Math.asin(0.08 + rnd.nextDouble() * 0.92);
                    s[2] = 0.4f + rnd.nextFloat() * 0.9f;
                    s[3] = rnd.nextFloat() * 10;
                }
            }
            Vec3d[] bb = f.billboard();
            double t = seconds();
            for (float[] s : starField) {
                Vec3d at = sphere(c, r, s[1], s[0]);
                float tw = (float) (0.6 + 0.4 * Math.sin(t * 2 + s[3]));
                double size = r * 0.0025 * s[2];
                int col = alpha(0xFFFFFFFF, dark * tw);
                Vec3d dx = bb[0].multiply(size);
                Vec3d dy = bb[1].multiply(size);
                f.quad(at.subtract(dx).subtract(dy), at.add(dx).subtract(dy), at.add(dx).add(dy), at.subtract(dx).add(dy), col, true);
            }
        }

        private static void auroraAt(WorldDraw.Frame f, Vec3d c, double r, float strength) {
            double t = seconds();
            for (int band = 0; band < 3; band++) {
                int strips = 40;
                double baseAz = Math.toRadians(-110 + band * 25);
                for (int i = 0; i < strips; i++) {
                    double a0 = baseAz + i * Math.toRadians(140.0 / strips);
                    double a1 = baseAz + (i + 1) * Math.toRadians(140.0 / strips);
                    double w0 = Math.sin(a0 * 3 + t * 0.4 + band) * 0.06;
                    double w1 = Math.sin(a1 * 3 + t * 0.4 + band) * 0.06;
                    double low = Math.toRadians(14 + band * 4);
                    double high = Math.toRadians(42 + band * 6);
                    float shimmer0 = (float) (0.5 + 0.5 * Math.sin(a0 * 9 + t * 1.3 + band));
                    float shimmer1 = (float) (0.5 + 0.5 * Math.sin(a1 * 9 + t * 1.3 + band));
                    int g0 = alpha(0xFF46F5A0, 0.38f * strength * shimmer0);
                    int g1 = alpha(0xFF46F5A0, 0.38f * strength * shimmer1);
                    int clear = alpha(0xFF46F5A0, 0f);
                    int top0 = alpha(0xFF9B6BFF, 0f);
                    double mid = low + (high - low) * 0.3;
                    // Soft at the bottom, brightest low down, fading to violet at the top.
                    f.quad(sphere(c, r, low + w0, a0), sphere(c, r, low + w1, a1), sphere(c, r, mid + w1, a1), sphere(c, r, mid + w0, a0),
                            clear, clear, g1, g0, true);
                    f.quad(sphere(c, r, mid + w0, a0), sphere(c, r, mid + w1, a1), sphere(c, r, high + w1, a1), sphere(c, r, high + w0, a0),
                            g0, g1, top0, top0, true);
                }
            }
        }

        private static void disc(WorldDraw.Frame f, Vec3d at, Vec3d u, Vec3d v, double r, int core, int glow) {
            f.ring(at, u, v, 0, r, core, core, 0, 1, 32, false);
            f.ring(at, u, v, r, r * 3, alpha(glow, 0.5f), alpha(glow, 0), 0, 1, 32, true);
        }
    }

    // ------------------------------------------------------------------

    /**
     * Name tags as clean cards: the name, distance, ping for players, a health
     * bar for mobs (never for players), and totems used. Replaces vanilla's.
     */
    public static final class BetterNametags extends Module implements WorldDraw.Layer {
        private final SliderSetting range;
        private final BoolSetting distance;
        private final BoolSetting ping;
        private final BoolSetting mobHealth;
        private final BoolSetting pops;
        private final BoolSetting constantSize;

        public BetterNametags() {
            super("better_nametags", "Better name tags", "Clean name cards with distance, ping, mob health and totems used.",
                    Category.VISUAL, false);
            this.range = add(new SliderSetting("range", "Range", "Show tags within this distance.", 48, 8, 128, 4, " blocks"));
            this.distance = add(new BoolSetting("distance", "Distance", "How far away they are.", true));
            this.ping = add(new BoolSetting("ping", "Ping", "Players' connection ping.", true));
            this.mobHealth = add(new BoolSetting("mob_health", "Mob health", "A health bar under mobs' names (not players').", true));
            this.pops = add(new BoolSetting("pops", "Totems used", "How many totems a player has popped.", true));
            this.constantSize = add(new BoolSetting("constant", "Same size at any distance", "Stay readable far away.", true));
            WorldDraw.add(this);
        }

        /** Whether vanilla's label should be skipped for {@code e} (we draw our own). */
        public boolean replaces(Entity e) {
            return isActive() && e instanceof LivingEntity && e != MinecraftClient.getInstance().player;
        }

        @Override
        public void render(WorldDraw.Frame f) {
            if (!isActive() || f.client.world == null) {
                return;
            }
            double max = range.get();
            for (Entity e : f.client.world.getEntities()) {
                if (!(e instanceof LivingEntity le) || e == f.client.player || e.isInvisible()) {
                    continue;
                }
                boolean player = e instanceof PlayerEntity;
                if (!player && !e.hasCustomName()) {
                    continue;
                }
                double d = e.getLerpedPos(f.tickDelta).distanceTo(f.cam);
                if (d > max) {
                    continue;
                }
                card(f, le, player, d);
            }
        }

        private void card(WorldDraw.Frame f, LivingEntity e, boolean player, double d) {
            var font = f.client.textRenderer;
            Text name = e.getDisplayName();
            StringBuilder info = new StringBuilder();
            if (distance.isOn()) {
                info.append(Math.round(d)).append("m");
            }
            if (player && ping.isOn() && f.client.getNetworkHandler() != null) {
                PlayerListEntry entry = f.client.getNetworkHandler().getPlayerListEntry(e.getUuid());
                if (entry != null) {
                    if (!info.isEmpty()) {
                        info.append("  ");
                    }
                    info.append(entry.getLatency()).append("ms");
                }
            }
            int popCount = CombatEvents.pops(e);
            if (player && pops.isOn() && popCount > 0) {
                if (!info.isEmpty()) {
                    info.append("  ");
                }
                info.append("-").append(popCount).append(" totem").append(popCount == 1 ? "" : "s");
            }
            Text infoText = Text.literal(info.toString());
            int nw = font.getWidth(name);
            int iw = info.isEmpty() ? 0 : font.getWidth(infoText);
            int w = Math.max(nw, (int) (iw * 0.75f)) + 8;
            boolean bar = !player && mobHealth.isOn();
            int h = 12 + (info.isEmpty() ? 0 : 8) + (bar ? 4 : 0);
            float scale = constantSize.isOn() ? (float) Math.max(0.025, 0.0042 * d) : 0.025f;
            Vec3d at = e.getLerpedPos(f.tickDelta).add(0, e.getHeight() + 0.35 + h * scale * 0.5, 0);
            Vec3d[] bb = f.billboard();
            Vec3d down = bb[1].multiply(-1);
            Vec3d origin = at.subtract(bb[0].multiply(w * scale / 2.0)).subtract(down.multiply(h * scale / 2.0));
            int accent = dev.blurredclient.mod.Theme.chroma(dev.blurredclient.mod.ui.kit.Skins.current().accent());
            f.overlay(!e.isSneaky());
            f.onPlane(origin, bb[0], down, scale, () -> {
                f.rect2d(0, 0, w, h, 0xB80D0F15, false);
                f.rect2d(0, 0, w, 1, accent, false);
                f.matrices.translate(0, 0, -0.1f);
                f.text2d(name, (w - nw) / 2f, 2.5f, 0xFFFFFFFF);
                if (iw > 0) {
                    f.text2d(infoText, (w - iw * 0.75f) / 2f, 12, 0xFF9AA4B4, 0.75f);
                }
                if (bar) {
                    float frac = MathHelper.clamp(e.getHealth() / e.getMaxHealth(), 0, 1);
                    f.rect2d(3, h - 4, w - 3, h - 2, 0x40FFFFFF, false);
                    f.matrices.translate(0, 0, -0.05f);
                    f.rect2d(3, h - 4, 3 + (w - 6) * frac, h - 2, Colors.lerp(0xFFFF5468, 0xFF3BD87A, frac), false);
                }
            });
            f.overlay(false);
        }
    }

    /**
     * Your skin's outer layer (hat, jacket, sleeves, trousers) shimmers with
     * the same animated skin as Item skins (its style, speed and brightness).
     * Only on your screen. 1.21.4+.
     */
    public static final class SkinShine extends Module {
        private final BoolSetting others;

        public SkinShine() {
            super("skin_shine", "Skin shine",
                    "Your skin's outer layer shimmers with the same animated skin as Item skins (pick the look there).",
                    Category.COSMETIC, false);
            this.others = add(new BoolSetting("others", "Other players too", "Everyone's outer layer shines (on your screen only).", false));
        }

        @Override
        public boolean isAvailable() {
            //? if >=1.21.4 {
            return true;
            //?} else {
            /*return false;
            *///?}
        }

        @Override
        public String unavailableReason() {
            return "Needs Minecraft 1.21.4 or newer.";
        }

        /** Whether the player with entity id {@code id} gets the shine. */
        public boolean shines(int id, boolean invisible) {
            var player = MinecraftClient.getInstance().player;
            return isActive() && !invisible && player != null && (id == player.getId() || others.isOn());
        }
    }
}
