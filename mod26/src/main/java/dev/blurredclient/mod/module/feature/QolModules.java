package dev.blurredclient.mod.module.feature;

import dev.blurredclient.mod.fx.CombatEvents;
import dev.blurredclient.mod.fx.Sfx;
import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.ModeSetting;
import dev.blurredclient.mod.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Smaller quality-of-life modules, each a switch plus a setting or two. */
public final class QolModules {
    private QolModules() {}

    /** The hotbar's selected-slot frame glides to the new slot instead of jumping. */
    public static final class SmoothHotbar extends Module {
        private final SliderSetting speed;
        private float x = Float.NaN;
        private long lastNs = System.nanoTime();

        public SmoothHotbar() {
            super("smooth_hotbar", "Smooth hotbar", "The hotbar selection slides to the new slot instead of jumping.",
                    Category.HUD, false);
            this.speed = add(new SliderSetting("speed", "Glide speed", "How fast it slides.", 18, 6, 40, 1, ""));
        }

        /** Where to draw the selection frame, given where vanilla wants it. */
        public int smooth(int target) {
            if (!isActive()) {
                x = Float.NaN;
                return target;
            }
            long now = System.nanoTime();
            float dt = Math.min(0.1f, (now - lastNs) / 1e9f);
            lastNs = now;
            if (Float.isNaN(x) || Math.abs(target - x) > 200) {
                x = target;
            }
            x += (target - x) * Math.min(1f, dt * speed.floatValue());
            return Math.round(x);
        }
    }

    /** New chat messages slide up into place instead of the chat jumping a line. */
    public static final class ChatAnimations extends Module {
        private final SliderSetting time;
        private long lastMessage;

        public ChatAnimations() {
            super("chat_animations", "Chat animations", "New chat messages slide in smoothly.", Category.HUD, false);
            this.time = add(new SliderSetting("time", "Slide time", "How long the slide takes.", 0.15, 0.05, 0.5, 0.01, "s"));
        }

        public void messageAdded() {
            lastMessage = System.nanoTime();
        }

        /** How far below its place the chat is drawn right now (GUI units). */
        public float offset() {
            if (!isActive()) {
                return 0f;
            }
            float t = (float) ((System.nanoTime() - lastMessage) / 1e9 / time.get());
            if (t >= 1f) {
                return 0f;
            }
            float u = 1f - t;
            return 9f * u * u * u;
        }
    }

    /** Keeps far more chat history than vanilla's 100 lines. */
    public static final class LongChat extends Module {
        private final SliderSetting lines;

        public LongChat() {
            super("long_chat", "Longer chat history", "Keep thousands of chat lines instead of 100, so you can scroll back further.",
                    Category.UTILITY, false);
            this.lines = add(new SliderSetting("lines", "Lines kept", "How many lines to remember.", 5000, 200, 20000, 100, ""));
        }

        public int limit(int vanilla) {
            return isActive() ? Math.max(vanilla, lines.intValue()) : vanilla;
        }
    }

    /** Your own sound when a hit lands, and another for a kill. */
    public static final class HitSounds extends Module {
        public enum Sound {
            DING("Ding"), POP("Pop"), PUNCH("Punch"), CRIT("Crit"), CHIME("Chime"), COIN("Coin"), CLICK("Click"), BELL("Bell");

            private final String label;

            Sound(String label) {
                this.label = label;
            }

            public String label() {
                return label;
            }

            SoundEvent event() {
                return switch (this) {
                    case DING -> SoundEvents.ARROW_HIT_PLAYER;
                    case POP -> SoundEvents.ITEM_PICKUP;
                    case PUNCH -> SoundEvents.PLAYER_ATTACK_STRONG;
                    case CRIT -> SoundEvents.PLAYER_ATTACK_CRIT;
                    case CHIME -> SoundEvents.AMETHYST_BLOCK_CHIME;
                    case COIN -> SoundEvents.EXPERIENCE_ORB_PICKUP;
                    case CLICK -> SoundEvents.UI_BUTTON_CLICK.value();
                    case BELL -> SoundEvents.NOTE_BLOCK_BELL.value();
                };
            }
        }

        private final ModeSetting<Sound> sound;
        private final ModeSetting<Sound> killSound;
        private final SliderSetting volume;
        private final SliderSetting pitch;
        private final BoolSetting comboPitch;

        public HitSounds() {
            super("hit_sounds", "Hit sounds", "Plays a sound of your choice when a hit lands, and another on a kill.",
                    Category.COMBAT, false);
            this.sound = add(new ModeSetting<>("sound", "Hit sound", "Sound for each hit.", Sound.DING, Sound::label));
            this.killSound = add(new ModeSetting<>("kill_sound", "Kill sound", "Sound when you finish something off.", Sound.BELL, Sound::label));
            this.volume = add(new SliderSetting("volume", "Volume", "How loud.", 0.6, 0.1, 1.0, 0.05, ""));
            this.pitch = add(new SliderSetting("pitch", "Pitch", "Higher or lower.", 1.0, 0.5, 2.0, 0.05, "x"));
            this.comboPitch = add(new BoolSetting("combo", "Rising combo", "Each hit in a quick combo plays a little higher.", true));
            CombatEvents.onAttack(e -> {
                if (isActive()) {
                    float p = pitch.floatValue();
                    if (comboPitch.isOn()) {
                        p *= 1f + Math.min(8, CombatEvents.combo() - 1) * 0.06f;
                    }
                    Sfx.play(sound.get().event(), p, volume.floatValue());
                }
            });
            CombatEvents.onDeath(d -> {
                if (isActive() && d.byYou()) {
                    Sfx.play(killSound.get().event(), pitch.floatValue() * 0.8f, volume.floatValue());
                }
            });
        }
    }

    /** Rejoins a server after you're kicked or disconnected, with a countdown you can cancel. */
    public static final class AutoReconnect extends Module {
        private final SliderSetting delay;
        private final SliderSetting attempts;

        public AutoReconnect() {
            super("auto_reconnect", "Auto reconnect", "Rejoins the server after a disconnect, with a countdown you can cancel.",
                    Category.UTILITY, false);
            this.delay = add(new SliderSetting("delay", "Delay", "Seconds before rejoining.", 5, 1, 30, 1, "s"));
            this.attempts = add(new SliderSetting("attempts", "Attempts", "Give up after this many tries in a row.", 5, 1, 20, 1, ""));
        }

        public int delaySeconds() {
            return delay.intValue();
        }

        public int attempts() {
            return attempts.intValue();
        }
    }

    /** No Minecraft "random" model rotation: identical blocks all face the same way. */
    public static final class NoBlockRotation extends Module {
        public NoBlockRotation() {
            super("no_block_rotation", "No block rotation",
                    "Grass, stone, sand and the like all use the same model rotation, so textures line up.",
                    Category.VISUAL, false);
        }

        @Override
        protected void onEnable() {
            rebuild();
        }

        @Override
        protected void onDisable() {
            rebuild();
        }

        private static void rebuild() {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.levelRenderer != null && client.level != null) {
                client.execute(client.levelExtractor::allChanged);
            }
        }
    }
}
