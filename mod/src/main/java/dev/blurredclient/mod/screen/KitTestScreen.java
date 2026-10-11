package dev.blurredclient.mod.screen;

import dev.blurredclient.mod.config.BlurredConfig;
import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.setting.ColorSetting;
import dev.blurredclient.mod.setting.KeybindSetting;
import dev.blurredclient.mod.setting.ModeSetting;
import dev.blurredclient.mod.setting.SliderSetting;
import dev.blurredclient.mod.ui.kit.Component;
import dev.blurredclient.mod.ui.kit.KitScreen;
import dev.blurredclient.mod.ui.kit.Skin;
import dev.blurredclient.mod.ui.kit.widget.Button;
import dev.blurredclient.mod.ui.kit.widget.ColorButton;
import dev.blurredclient.mod.ui.kit.widget.Column;
import dev.blurredclient.mod.ui.kit.widget.Dropdown;
import dev.blurredclient.mod.ui.kit.widget.KeybindButton;
import dev.blurredclient.mod.ui.kit.widget.Label;
import dev.blurredclient.mod.ui.kit.widget.Panel;
import dev.blurredclient.mod.ui.kit.widget.Row;
import dev.blurredclient.mod.ui.kit.widget.ScrollView;
import dev.blurredclient.mod.ui.kit.widget.Slider;
import dev.blurredclient.mod.ui.kit.widget.TextField;
import dev.blurredclient.mod.ui.kit.widget.Toggle;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Every UI kit widget on one screen, in both skins — the Phase 2 check.
 *
 * <p>Development only: opened automatically at startup when the dev client is
 * run with {@code -PdevScreen=kit}. Nothing in a release build links to it.
 * The settings it edits are its own throwaway ones, apart from the
 * Appearance style, which is real so the skin switch can be seen live.
 */
public class KitTestScreen extends KitScreen {
    private enum Size { SMALL, MEDIUM, LARGE }

    // Fields, not locals: they must survive the rebuild on resize.
    private final BoolSetting demoToggle = new BoolSetting("t", "Demo toggle", "", true);
    private final SliderSetting demoSlider = new SliderSetting("s", "Scale", "", 1.0, 0.5, 2.0, 0.05, "x");
    private final ModeSetting<Size> demoMode = new ModeSetting<>("m", "Size", "", Size.MEDIUM,
            s -> s.name().charAt(0) + s.name().substring(1).toLowerCase());
    private final KeybindSetting demoKey = new KeybindSetting("k", "Zoom key", "", GLFW.GLFW_KEY_C);
    private final ColorSetting demoColor = new ColorSetting("c", "Outline", "", 0xC035E0D0);
    private String typed = "";

    private Dropdown<?> styleDropdown;
    private ColorButton colorButton;
    private Toggle demoSwitch;
    private int scriptTick;

    public KitTestScreen() {
        super(Text.literal("UI kit"));
    }

    @Override
    protected void build(Component root) {
        int ww = Math.min(420, uiWidth() - 24);
        int wh = Math.min(300, uiHeight() - 24);
        int wx = (uiWidth() - ww) / 2;
        int wy = (uiHeight() - wh) / 2;

        // Left rail, as the mod menu will have.
        Panel rail = root.add(new Panel(Skin.Surface.RAIL, 8));
        Column nav = rail.add(new Column(4, 0));
        nav.add(new Label("BLURRED", Label.Tone.ACCENT));
        for (String c : new String[] {"HUD", "Visual", "Menus", "Utility", "Crew", "Cosmetics"}) {
            nav.add(new Button(c, () -> {}));
        }
        rail.setBounds(wx, wy, 92, wh);

        Panel window = root.add(new Panel(Skin.Surface.WINDOW, 8));
        Column list = new Column(4, 0);
        ScrollView scroll = window.add(new ScrollView(list));

        Runnable save = BlurredConfig::save;
        styleDropdown = new Dropdown<>(Modules.APPEARANCE.styleSetting(), () -> {
            save.run();
            rebuild();
        });
        list.add(new Row("Style", "Glass is the fish tank; pixel art is the crisp ocean look.", styleDropdown, 90));
        demoSwitch = new Toggle(demoToggle::isOn, demoToggle::toggle);
        list.add(new Row("Demo toggle", "Click it; the knob slides and the track fills.", demoSwitch, 30));
        list.add(new Row("Scale", "Drag, scroll, or focus it and use the arrow keys. Shift for big steps.",
                new Slider(demoSlider, () -> {}), 110));
        list.add(new Row("Size", "Opens downward, or upward when there's no room.",
                new Dropdown<>(demoMode, () -> {}), 90));
        list.add(new Row("Zoom key", "Click, then press a key. Escape clears it.",
                new KeybindButton(demoKey, () -> {}), 90));
        colorButton = new ColorButton(demoColor, () -> {});
        list.add(new Row("Outline colour", "Square for saturation and brightness, bars for hue and alpha, or type hex.",
                colorButton, 90));
        list.add(new Row("Search", "Arrows, Home/End, Ctrl to jump words, Ctrl+V to paste.",
                new TextField(typed, 40, s -> typed = s).placeholder("Type here…"), 110));
        list.add(new Row("Buttons", null, new Button("Apply", () -> {}).accent(), 60));
        for (int i = 1; i <= 12; i++) {
            list.add(new Row("Filler row " + i, "Here so the list scrolls. The scrollbar thumb can be dragged.",
                    new Toggle(() -> false, () -> {}).tooltip("Tooltips appear after a short hover."), 30));
        }

        window.setBounds(wx + 98, wy, ww - 98, wh);
    }

    // ------------------------------------------------------------------
    // Self-driving check (dev only, -PdevScript=1)
    // ------------------------------------------------------------------

    /**
     * Clicks through the widgets from inside the game and saves screenshots
     * from Minecraft's own framebuffer, then quits. Goes through the same
     * input path a real click takes ({@link #kitMouseDown}), but never touches
     * the operating system's mouse, keyboard or window focus — so it's safe to
     * run while other programs (or another Minecraft) are in use.
     */
    @Override
    public void tick() {
        super.tick();
        if (System.getProperty("blurred.devScript") == null) {
            return;
        }
        scriptTick++;
        switch (scriptTick) {
            case 60 -> click(styleDropdown);
            case 70 -> shot();
            case 72 -> clickOverlayRow(1);
            case 82 -> {
                click(demoSwitch);
                // Bring the colour row into view; the click below hit-tests
                // like a real one, so it has to be on screen.
                kitScroll(uiWidth() / 2.0 + 40, uiHeight() / 2.0, -3);
            }
            case 100 -> click(colorButton);
            case 110 -> shot();
            case 112 -> {
                closeOverlay();
                Modules.APPEARANCE.styleSetting().set(dev.blurredclient.mod.module.feature.AppearanceModule.Style.MODERN);
                BlurredConfig.save();
                rebuild();
                kitScroll(uiWidth() / 2.0 + 40, uiHeight() / 2.0, -3);
            }
            case 130 -> click(colorButton);
            case 140 -> shot();
            case 144 -> this.client.scheduleStop();
            default -> {}
        }
    }

    private void click(Component c) {
        double cx = c.x + c.w / 2.0;
        double cy = c.y + c.h / 2.0;
        kitMouseDown(cx, cy, 0);
        kitMouseUp(cx, cy, 0);
    }

    private void clickOverlayRow(int row) {
        Component list = overlay();
        if (list == null) {
            return;
        }
        double cx = list.x + 12;
        double cy = list.y + 2 + row * 14 + 7;
        kitMouseDown(cx, cy, 0);
        kitMouseUp(cx, cy, 0);
    }

    private void shot() {
        net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(
                this.client.runDirectory, this.client.getFramebuffer(), msg -> {});
    }
}
