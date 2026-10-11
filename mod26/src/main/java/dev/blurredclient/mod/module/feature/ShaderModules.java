package dev.blurredclient.mod.module.feature;

import dev.blurredclient.mod.fx.ShaderFx;
import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.setting.ModeSetting;

/** Modules built on shaders. Only on 1.21.11 for now. */
public final class ShaderModules {
    private ShaderModules() {}

    private abstract static class ShaderModule extends Module {
        ShaderModule(String id, String name, String description) {
            super(id, name, description, Category.VISUAL, false);
        }

        @Override
        public boolean isAvailable() {
            return ShaderFx.supported();
        }

        @Override
        public String unavailableReason() {
            return "Needs Minecraft 1.21.11 for now.";
        }
    }

    /** A colour grade over the whole game, like a shader pack's look. */
    public static final class Ambience extends ShaderModule {
        public enum Look {
            SHADER("Shader pack", "shader"), GOLDEN("Golden hour", "golden"), ARCTIC("Arctic", "arctic"),
            CINEMATIC("Cinematic", "cinematic"), DREAMY("Dreamy glow", "dreamy"), VIVID("Vivid", "vivid"), NOIR("Noir", "noir"),
            DUSK("Dusk", "dusk"), MOODY("Moody", "moody"), MIDNIGHT("Midnight", "midnight"), HORROR("Horror", "horror");

            private final String label;
            private final String id;

            Look(String label, String id) {
                this.label = label;
                this.id = id;
            }

            public String label() {
                return label;
            }
        }

        private final ModeSetting<Look> look;

        public Ambience() {
            super("ambience", "Ambience",
                    "Shader-pack style: soft shadows in corners (ambient occlusion), bloom, distance haze and filmic colour, in several looks.");
            this.look = add(new ModeSetting<>("look", "Look", "The colour grade.", Look.SHADER, Look::label));
        }

        public String lookId() {
            return look.get().id;
        }
    }

    /**
     * Camera motion blur like a shader pack's: each pixel smears along the way
     * it actually moved on screen, so turning blurs the scene, running blurs
     * nearby blocks more than the distance, and your hand stays sharp.
     */
    public static final class MotionBlur extends ShaderModule {
        private final dev.blurredclient.mod.setting.SliderSetting strength;

        public MotionBlur() {
            super("motion_blur", "Motion blur", "Real camera motion blur: things smear the way they move on screen, your hand stays sharp.");
            this.strength = add(new dev.blurredclient.mod.setting.SliderSetting("strength", "Strength",
                    "How long the smear is.", 0.5, 0.1, 1.0, 0.05, ""));
        }

        public String strengthId() {
            return "on";
        }

        public float amount() {
            return strength.floatValue();
        }
    }

    /** Leaves, grass, ferns and vines sway in the wind, with gusts. */
    public static final class WavingFoliage extends ShaderModule {
        public WavingFoliage() {
            super("waving_foliage", "Waving foliage",
                    "Leaves, grass, ferns and vines sway in the wind, with gusts. Turning it on reloads resources once.");
        }
    }

    /** Darker, wet-looking ground with puddles that mirror the sky and raindrop ripples. */
    public static final class WetGround extends ShaderModule {
        public WetGround() {
            super("wet_ground", "Wet ground",
                    "The ground looks rained on: darker, with puddles that reflect the sky and ripple. Reloads resources once.");
        }
    }
}
