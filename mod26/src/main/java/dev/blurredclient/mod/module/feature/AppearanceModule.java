package dev.blurredclient.mod.module.feature;

import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.module.ModuleIds;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.ModeSetting;
import dev.blurredclient.mod.setting.SliderSetting;

/**
 * How Blurred's own screens look: modern dark panels (default), "fish tank"
 * glass, or pixel art.
 *
 * <p>Global rather than per profile — the look of the client is a preference
 * about you, not about what you're playing.
 */
public final class AppearanceModule extends Module {
    public enum Corners {
        SQUARE("Square"),
        ROUNDED("Rounded");

        private final String label;

        Corners(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Background {
        CONSTELLATION("Constellation"), AURORA("Aurora"), STARFIELD("Starfield"), WAVES("Waves"),
        SYNTHWAVE("Synthwave"), BOKEH("Bokeh");

        private final String label;

        Background(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Style {
        MODERN("Modern"),
        GLASS("Glass"),
        LIQUID("Liquid glass"),
        PIXEL("Pixel art");

        private final String label;

        Style(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final ModeSetting<Style> style;
    private final BoolSetting tankEffects;
    private final ModeSetting<Corners> corners;
    private final SliderSetting menuSize;
    private final BoolSetting animatedBackground;
    private final ModeSetting<Background> background;
    private final BoolSetting reactiveBackground;
    private final BoolSetting smoothMenus;
    private final BoolSetting rgb;
    private final BoolSetting outlines;
    private final SliderSetting rgbSpeed;

    public AppearanceModule() {
        super(ModuleIds.APPEARANCE, "Appearance",
                "The look of Blurred's menus: modern dark panels, fish-tank glass, Apple-style liquid glass, or pixel art.",
                Category.MENUS, true);
        this.style = add(new ModeSetting<>("style", "Style",
                "Modern is flat and dark with a blue accent. Glass is rounded and translucent, like a fish tank. Liquid glass is clear, thick glass that catches the light, with water ripples when you click. Pixel art is square and crisp.",
                Style.MODERN, Style::label));
        this.tankEffects = add(new BoolSetting("tank_effects", "Bubbles and light ripples",
                "Tiny bubbles rising inside panels and slow caustic light drifting across them.", true));
        tankEffects.visibleIf(() -> style.get() == Style.GLASS);
        this.corners = add(new ModeSetting<>("corners", "Corners",
                "Square, flat edges (default), or rounded corners. Applies to the Modern style and the HUD.",
                Corners.SQUARE, Corners::label));
        corners.visibleIf(() -> style.get() == Style.MODERN);
        this.menuSize = add(new SliderSetting("menu_size", "Menu size",
                "How big Blurred's menus are. Independent of Minecraft's GUI scale.",
                0.8, 0.5, 1.5, 0.05, "x"));
        this.background = add(new ModeSetting<>("background", "Background",
                "What's behind Blurred's menus out of a world: linked particles, an aurora, a starfield, waves, a synthwave sunset or soft bokeh lights.",
                Background.CONSTELLATION, Background::label));
        this.animatedBackground = add(new BoolSetting("animated_background", "Animated background",
                "Drifting glows and particles behind the menus. Off gives a still background.", true));
        this.reactiveBackground = add(new BoolSetting("reactive_background", "Reactive background",
                "The background follows your cursor: a soft light, parallax, and particles that link to it.", true));
        this.smoothMenus = add(new BoolSetting("smooth_menus", "Smooth menus",
                "Minecraft caps menus at 60 FPS when you're not in a world. This lets them run at your normal max FPS, so animations are smoother.",
                true));
        this.outlines = add(new BoolSetting("outlines", "Outlines",
                "A thin accent-coloured edge, with a faint glow, around menu windows, panels and popups.", false));
        this.rgb = add(new BoolSetting("rgb", "RGB mode",
                "The accent colour cycles through the rainbow everywhere: menus, HUD, keystrokes, outlines and effects set to Accent.",
                false));
        this.rgbSpeed = add(new SliderSetting("rgb_speed", "RGB speed", "How fast the colours cycle.",
                1.0, 0.2, 4, 0.1, "x"));
        rgbSpeed.visibleIf(rgb::isOn);
        animatedBackground.visibleIf(() -> style.get() != Style.PIXEL);
        reactiveBackground.visibleIf(() -> style.get() != Style.PIXEL);
    }

    /**
     * Whether shapes should get rounded corners. Glass is rounded by nature
     * (it's the fish tank); Modern follows the Corners setting.
     */
    public boolean rounded() {
        return style.get() == Style.GLASS || style.get() == Style.LIQUID || corners.get() == Corners.ROUNDED;
    }

    public boolean smoothMenus() {
        return smoothMenus.isOn();
    }

    public boolean outlines() {
        return outlines.isOn();
    }

    public boolean rgb() {
        return rgb.isOn();
    }

    public float rgbSpeed() {
        return rgbSpeed.floatValue();
    }

    public float menuSize() {
        return menuSize.floatValue();
    }

    public Background background() {
        return background.get();
    }

    public boolean animatedBackground() {
        return animatedBackground.isOn();
    }

    public boolean reactiveBackground() {
        return reactiveBackground.isOn() && animatedBackground.isOn();
    }

    @Override
    public boolean toggleable() {
        return false;
    }

    @Override
    public boolean perProfile() {
        return false;
    }

    public Style style() {
        return style.get();
    }

    public ModeSetting<Style> styleSetting() {
        return style;
    }

    public boolean tankEffects() {
        return tankEffects.isOn();
    }
}
