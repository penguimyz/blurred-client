package dev.blurredclient.mod.module.feature;

import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.setting.KeybindSetting;
import dev.blurredclient.mod.setting.ModeSetting;
import dev.blurredclient.mod.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

/** Modules for the item in your hand: inspecting it, and sword models. */
public final class HandModules {
    private HandModules() {}

    /**
     * CS2-style inspect. Press the key and either:
     * <ul>
     *   <li><b>Showcase</b>: the weapon is held out in front of you on its
     *   side, tip pointing left, and slides slowly across to the right;</li>
     *   <li><b>Spin</b>: you twirl it around your grip a couple of times and
     *   catch it.</li>
     * </ul>
     * Your hand is drawn holding it the whole time. Swinging, using or
     * switching items cuts it short.
     *
     * <p>All motion is worked out in the frame vanilla holds the item in
     * (just before the swing is applied), from vanilla's first-person
     * handheld transform: the blade runs along the sprite's diagonal, its
     * flat faces +Z. Blurred's sword models are built to match that.
     */
    public static final class Inspect extends Module implements Ticking {
        public enum Style {
            SHOWCASE("Showcase", 3.0f), SPIN("Spin", 1.9f), TOSS("Toss", 1.7f), TWIRL("Twirl", 2.0f),
            EDGE("Edge check", 2.8f);

            private final String label;
            private final float length;

            Style(String label, float length) {
                this.label = label;
                this.length = length;
            }

            public String label() {
                return label;
            }
        }

        /** Vanilla's first-person handheld transform (right hand). */
        private static final org.joml.Matrix4f DISPLAY = new org.joml.Matrix4f()
                .translate(1.13f / 16f, 3.2f / 16f, 1.13f / 16f)
                .rotateY((float) Math.toRadians(-90)).rotateZ((float) Math.toRadians(25));
        private static final float DISPLAY_SCALE = 0.68f;
        /** Along the blade, across its flat face, and where the hand grips, in model units from the model's centre. */
        private static final org.joml.Vector3f TIP = new org.joml.Vector3f(1, 1, 0).normalize();
        private static final org.joml.Vector3f FACE = new org.joml.Vector3f(0, 0, 1);
        private static final org.joml.Vector3f GRIP = new org.joml.Vector3f(-0.27f, -0.27f, 0);

        private final KeybindSetting key;
        private final ModeSetting<Style> style;
        private final SliderSetting speed;
        private final dev.blurredclient.mod.setting.BoolSetting hands;
        private boolean down;
        private long start = -1;
        private Style playing = Style.SHOWCASE;
        private ItemStack stack = ItemStack.EMPTY;
        /** This frame's inspect transform and where the grip ends up, for drawing the hand. */
        private final org.joml.Matrix4f frame = new org.joml.Matrix4f();
        private final org.joml.Vector3f grip = new org.joml.Vector3f();
        private boolean framed;

        public Inspect() {
            super("inspect", "Inspect", "Press a key to show off the item in your hand, CS2 style: held out sideways, or spun in your hand.",
                    Category.VISUAL, false);
            this.key = add(new KeybindSetting("key", "Inspect key", "Press to inspect what you're holding.",
                    org.lwjgl.glfw.GLFW.GLFW_KEY_Y));
            this.style = add(new ModeSetting<>("style", "Style",
                    "Showcase holds it out sideways and slides it across; Spin twirls it in your hand.", Style.SHOWCASE, Style::label));
            this.speed = add(new SliderSetting("speed", "Speed", "Above 1 is quicker.", 1.0, 0.5, 2.0, 0.1, "x"));
            this.hands = add(new dev.blurredclient.mod.setting.BoolSetting("hands", "Show your hand",
                    "Draw your hand holding the item while inspecting.", true));
        }

        @Override
        public void tick(MinecraftClient client) {
            ClientPlayerEntity p = client.player;
            boolean pressed = client.currentScreen == null && key.isBound() && pressed(client, key.keyCode());
            if (pressed && !down && p != null && !p.getMainHandStack().isEmpty()) {
                start();
            }
            down = pressed;
            if (start >= 0 && p != null && (p.handSwinging || p.getMainHandStack() != stack || p.isUsingItem())) {
                start = -1;
            }
        }

        /** Starts an inspect now in the chosen style (also used by the in-game tests). */
        public void start() {
            ClientPlayerEntity p = MinecraftClient.getInstance().player;
            stack = p == null ? ItemStack.EMPTY : p.getMainHandStack();
            playing = style.get();
            start = System.nanoTime();
        }

        private static boolean pressed(MinecraftClient client, int code) {
            //? if >=1.21.10 {
            return net.minecraft.client.util.InputUtil.isKeyPressed(client.getWindow(), code);
            //?} else {
            /*return net.minecraft.client.util.InputUtil.isKeyPressed(client.getWindow().getHandle(), code);
            *///?}
        }

        /** Seconds into the inspect, or -1 when there isn't one. */
        private float time() {
            if (start < 0 || !isActive()) {
                return -1;
            }
            float t = (System.nanoTime() - start) / 1e9f * speed.floatValue();
            if (t >= playing.length) {
                start = -1;
                return -1;
            }
            return t;
        }

        /**
         * Moves the held item for the inspect. Called for the main hand where
         * the swing would be applied, so the frame is the one vanilla holds
         * items in; {@code side} is 1 for the right arm, -1 for the left.
         */
        public void apply(MatrixStack m, int side) {
            framed = false;
            float t = time();
            if (t < 0) {
                // No inspect: the item sits where vanilla holds it, raised a
                // little with Show hand so the hand holding it is in view.
                frame.identity();
                if (dev.blurredclient.mod.module.Modules.SHOW_HAND.isActive()) {
                    frame.translate(-0.07f * side, 0.16f, 0.02f);
                }
                gripPoint(grip);
                if (side < 0) {
                    grip.x = -grip.x;
                }
                frame.transformPosition(grip);
                framed = true;
                m.multiplyPositionMatrix(frame);
                return;
            }
            org.joml.Matrix4f motion = switch (playing) {
                case SHOWCASE -> showcase(t);
                case SPIN -> spin(t);
                case TOSS -> toss(t);
                case TWIRL -> twirl(t);
                case EDGE -> edge(t);
            };
            if (side < 0) {
                // The left hand is the mirror image.
                org.joml.Matrix4f flip = new org.joml.Matrix4f().scale(-1, 1, 1);
                motion = new org.joml.Matrix4f(flip).mul(motion).mul(flip);
            }
            frame.set(motion);
            gripPoint(grip);
            if (side < 0) {
                grip.x = -grip.x;
            }
            motion.transformPosition(grip);
            framed = true;
            m.multiplyPositionMatrix(motion);
        }

        /** Where the hand holds the item, before the inspect moves it. */
        private static void gripPoint(org.joml.Vector3f out) {
            out.set(GRIP).mul(DISPLAY_SCALE);
            DISPLAY.transformPosition(out);
        }

        private static org.joml.Vector3f along(org.joml.Vector3f v) {
            return DISPLAY.transformDirection(new org.joml.Vector3f(v)).normalize();
        }

        /** Held out on its side, tip to the left, sliding slowly to the right. */
        private static org.joml.Matrix4f showcase(float t) {
            float in = smooth(0f, 0.45f, t);
            float out = smooth(2.55f, 3.0f, t);
            float k = in * (1 - out);
            // The rotation that turns the blade to point left with its flat towards you.
            org.joml.Vector3f a = along(TIP);
            org.joml.Vector3f b = along(FACE);
            org.joml.Matrix3f from = new org.joml.Matrix3f(a, b, new org.joml.Vector3f(a).cross(b));
            org.joml.Vector3f to0 = new org.joml.Vector3f(-1, 0.08f, 0).normalize();
            org.joml.Vector3f to1 = new org.joml.Vector3f(0, 0.25f, 1).normalize();
            org.joml.Matrix3f to = new org.joml.Matrix3f(to0, to1, new org.joml.Vector3f(to0).cross(to1));
            org.joml.Quaternionf turn = new org.joml.Quaternionf().setFromNormalized(new org.joml.Matrix3f(to).mul(from.transpose()));
            // A slow tilt while it slides, to catch the light.
            turn.premul(new org.joml.Quaternionf().rotateY((float) Math.toRadians(14 * Math.sin(t * 1.1f))));
            org.joml.Quaternionf q = new org.joml.Quaternionf().slerp(turn, k);
            // Grip moves from just right of centre across to the right, a little further away.
            float slide = smooth(0.3f, 2.6f, t);
            org.joml.Vector3f target = new org.joml.Vector3f(-0.42f + 0.32f * slide, 0.36f, -0.22f);
            org.joml.Vector3f g = new org.joml.Vector3f();
            gripPoint(g);
            org.joml.Vector3f rotated = q.transform(new org.joml.Vector3f(g));
            org.joml.Vector3f shift = new org.joml.Vector3f(target).sub(rotated).mul(k)
                    .add(new org.joml.Vector3f(g).sub(rotated).mul(1 - k));
            return new org.joml.Matrix4f().translate(shift).rotate(q);
        }

        /** Twirled around the grip, twice, then caught. */
        private static org.joml.Matrix4f spin(float t) {
            float lift = smooth(0f, 0.25f, t) * (1 - smooth(1.55f, 1.9f, t));
            float turns = smooth(0.15f, 1.35f, t);
            // A small overshoot as you catch it.
            float catchBack = (float) Math.sin(smooth(1.3f, 1.65f, t) * Math.PI) * 0.12f;
            float angle = (float) (Math.PI * 2 * 2 * turns) - catchBack;
            org.joml.Vector3f g = new org.joml.Vector3f();
            gripPoint(g);
            org.joml.Vector3f axis = along(FACE);
            return new org.joml.Matrix4f()
                    .translate(-0.06f * lift, 0.08f * lift, 0.02f * lift)
                    .translate(g).rotate(-angle, axis).translate(-g.x, -g.y, -g.z);
        }

        /** Flipped up into the air end over end, and caught. */
        private static org.joml.Matrix4f toss(float t) {
            float flight = Math.max(0f, Math.min(1f, (t - 0.25f) / 0.95f));
            float height = 4f * flight * (1 - flight) * 0.42f;
            // A little wind-up before the throw and a dip as it lands in your hand.
            float windup = (float) Math.sin(Math.min(1f, t / 0.25f) * Math.PI) * 0.05f;
            float land = (float) Math.sin(smooth(1.2f, 1.55f, t) * Math.PI) * 0.06f;
            float angle = (float) (Math.PI * 2 * 2 * smooth(0.25f, 1.2f, t));
            org.joml.Vector3f g = new org.joml.Vector3f();
            gripPoint(g);
            org.joml.Vector3f axis = along(FACE);
            return new org.joml.Matrix4f()
                    .translate(-0.08f * flight, height - windup - land, 0)
                    .translate(g).rotate(angle, axis).translate(-g.x, -g.y, -g.z);
        }

        /** Rolled around its own blade, flashing flat, edge, flat. */
        private static org.joml.Matrix4f twirl(float t) {
            float lift = smooth(0f, 0.3f, t) * (1 - smooth(1.65f, 2.0f, t));
            float angle = (float) (Math.PI * 2 * 2 * smooth(0.2f, 1.6f, t));
            org.joml.Vector3f g = new org.joml.Vector3f();
            gripPoint(g);
            org.joml.Vector3f axis = along(TIP);
            return new org.joml.Matrix4f()
                    .translate(-0.1f * lift, 0.1f * lift, 0.03f * lift)
                    .translate(g).rotate(angle, axis).translate(-g.x, -g.y, -g.z);
        }

        /** Held upright in front of you and tilted side to side, like checking the edge. */
        private static org.joml.Matrix4f edge(float t) {
            float in = smooth(0f, 0.4f, t);
            float out = smooth(2.35f, 2.8f, t);
            float k = in * (1 - out);
            org.joml.Quaternionf wobble = new org.joml.Quaternionf()
                    .rotateY((float) Math.toRadians(40 * Math.sin(smooth(0.4f, 2.35f, t) * Math.PI * 2)));
            return hold(new org.joml.Vector3f(0.12f, 1, 0), new org.joml.Vector3f(0.6f, 0, 1),
                    new org.joml.Vector3f(-0.3f, 0.12f, -0.12f), wobble, k);
        }

        /**
         * The item turned so its tip points along {@code tip} and its flat
         * faces {@code face} (camera space), its grip moved to {@code target}
         * (the hand's frame), then {@code extra} applied; all blended in by {@code k}.
         */
        private static org.joml.Matrix4f hold(org.joml.Vector3f tip, org.joml.Vector3f face, org.joml.Vector3f target,
                                              org.joml.Quaternionf extra, float k) {
            org.joml.Vector3f a = along(TIP);
            org.joml.Vector3f b = along(FACE);
            org.joml.Matrix3f from = new org.joml.Matrix3f(a, b, new org.joml.Vector3f(a).cross(b));
            org.joml.Vector3f to0 = new org.joml.Vector3f(tip).normalize();
            org.joml.Vector3f to1 = new org.joml.Vector3f(face);
            to1.sub(new org.joml.Vector3f(to0).mul(to1.dot(to0))).normalize();
            org.joml.Matrix3f to = new org.joml.Matrix3f(to0, to1, new org.joml.Vector3f(to0).cross(to1));
            org.joml.Quaternionf turn = new org.joml.Quaternionf().setFromNormalized(new org.joml.Matrix3f(to).mul(from.transpose()));
            turn.premul(extra);
            org.joml.Quaternionf q = new org.joml.Quaternionf().slerp(turn, k);
            org.joml.Vector3f g = new org.joml.Vector3f();
            gripPoint(g);
            org.joml.Vector3f rotated = q.transform(new org.joml.Vector3f(g));
            org.joml.Vector3f shift = new org.joml.Vector3f(target).sub(rotated).mul(k)
                    .add(new org.joml.Vector3f(g).sub(rotated).mul(1 - k));
            return new org.joml.Matrix4f().translate(shift).rotate(q);
        }

        /**
         * How much the inspect has taken over the item right now (0..1): the
         * View model's offsets fade out by this much so every inspect plays
         * from the same, vanilla pose.
         */
        public float takeover() {
            float t = time();
            if (t < 0) {
                return 0f;
            }
            return smooth(0f, 0.2f, t) * (1 - smooth(playing.length - 0.25f, playing.length, t));
        }

        /** True while an inspect is moving the item this frame and the hand should be drawn. */
        public boolean drawHand() {
            if (!framed) {
                return false;
            }
            return (hands.isOn() && time() >= 0) || dev.blurredclient.mod.module.Modules.SHOW_HAND.isActive();
        }

        /**
         * Puts the matrices where vanilla's map-holding arm ({@code renderArm})
         * will end with its hand on the grip, its arm reaching in from the
         * bottom corner. {@code matrices} are in the inspect frame; this undoes
         * it so the arm itself doesn't spin with the item.
         */
        public void placeHand(MatrixStack matrices, int side) {
            float f = side;
            matrices.multiplyPositionMatrix(new org.joml.Matrix4f(frame).invert());
            // Take the View model's turn back out too, or the arm twists with it;
            // its offset still applies to where the hand goes.
            org.joml.Matrix4f vm = ViewModelModule.MAIN_TRANSFORM;
            matrices.multiplyPositionMatrix(new org.joml.Matrix4f(vm).invert());
            org.joml.Vector3f at = vm.transformPosition(new org.joml.Vector3f(grip));
            // What renderArm does before drawing the arm, and where its hand is after that.
            org.joml.Matrix4f vanilla = new org.joml.Matrix4f()
                    .rotateY((float) Math.toRadians(92)).rotateX((float) Math.toRadians(45))
                    .rotateZ((float) Math.toRadians(f * -41)).translate(f * 0.3f, -1.1f, 0.45f);
            org.joml.Vector3f hand = new org.joml.Vector3f(f * -0.375f, 0.72f, 0f);
            // Shoulder-to-hand direction we want: up, inwards and forwards from the corner.
            org.joml.Vector3f reach = new org.joml.Vector3f(-0.28f * f, 0.5f, -0.82f).normalize();
            org.joml.Quaternionf aim = new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0, 1, 0), reach);
            // Roll so the palm faces the handle.
            aim.rotateY((float) Math.toRadians(f * 70));
            org.joml.Matrix4f want = new org.joml.Matrix4f().translate(at).rotate(aim).translate(-hand.x, -hand.y, -hand.z);
            matrices.multiplyPositionMatrix(want.mul(vanilla.invert()));
        }

        private static float smooth(float a, float b, float t) {
            float x = Math.max(0f, Math.min(1f, (t - a) / (b - a)));
            return x * x * (3 - 2 * x);
        }
    }

    /**
     * 3D models for every sword: katana, claymore, tanto or scythe, each in
     * the sword's material. Built-in resource packs; switching reloads
     * resources once.
     */
    public static final class SwordModels extends Module {
        public enum Model {
            KATANA("Katana", "swords_katana"), CLAYMORE("Claymore", "swords_claymore"),
            TANTO("Tanto", "swords_tanto"), SCYTHE("Scythe", "swords_scythe"),
            KARAMBIT("Karambit", "swords_karambit"), LIGHTSABER("Lightsaber", "swords_lightsaber");

            private final String label;
            private final String pack;

            Model(String label, String pack) {
                this.label = label;
                this.pack = pack;
            }

            public String label() {
                return label;
            }
        }

        private final ModeSetting<Model> model;

        public SwordModels() {
            super("sword_models", "Sword models",
                    "Swaps every sword for a 3D model: katana, claymore, tanto, scythe, karambit or lightsaber, in its own material. Reloads resources once.",
                    Category.VISUAL, false);
            this.model = add(new ModeSetting<>("model", "Model", "Which weapon your swords look like.", Model.KATANA, Model::label));
        }

        /** The pack to have on, or "" for none. */
        public String pack() {
            return isActive() ? model.get().pack : "";
        }

        public static String[] packs() {
            Model[] all = Model.values();
            String[] out = new String[all.length];
            for (int i = 0; i < all.length; i++) {
                out[i] = all[i].pack;
            }
            return out;
        }
    }

    /** Your hand, drawn holding whatever you carry in first person, not just when it's empty. */
    public static final class ShowHand extends Module {
        public ShowHand() {
            super("show_hand", "Show hand", "Draw your hand holding the item in first person, like an empty hand is.",
                    Category.VISUAL, false);
        }
    }
}
