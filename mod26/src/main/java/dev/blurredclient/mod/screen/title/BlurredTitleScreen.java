package dev.blurredclient.mod.screen.title;

import com.mojang.realmsclient.RealmsMainScreen;
import dev.blurredclient.mod.bridge.Friend;
import dev.blurredclient.mod.bridge.LauncherBridge;
import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.screen.CosmeticsScreen;
import dev.blurredclient.mod.screen.CrewScreen;
import dev.blurredclient.mod.screen.clickgui.ClickGuiScreen;
import dev.blurredclient.mod.social.Essential;
import dev.blurredclient.mod.ui.Colors;
import dev.blurredclient.mod.ui.Draw;
import dev.blurredclient.mod.ui.kit.Anim;
import dev.blurredclient.mod.ui.kit.KitComponent;
import dev.blurredclient.mod.ui.kit.Icons;
import dev.blurredclient.mod.ui.kit.KitScreen;
import dev.blurredclient.mod.ui.kit.Skin;
import dev.blurredclient.mod.ui.kit.Ui;
import dev.blurredclient.mod.ui.kit.Widgets;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * Blurred's main menu, replacing the vanilla title screen when the menu theme
 * is on.
 *
 * <p>Two columns over the animated backdrop. Left: the wordmark and a slim
 * menu, rows sliding in one after another when the menu opens. Right: who you
 * are, your crew (who's online and where), and what's new in this version.
 * A quiet row of text actions and the version sit along the bottom.
 *
 * <p>Swapped in by {@code TitleScreenSwapMixin} at {@code setScreen}, so every
 * path that returns to the title screen (quitting a world, Done in Options)
 * lands here. The Minecraft copyright line is kept, as the vanilla title
 * screen shows it.
 */
public class BlurredTitleScreen extends KitScreen {
    private static final int LEFT = 40;
    private static final int ROW_W = 190;
    private static final int ROW_H = 28;
    private static final int ROW_GAP = 4;
    private static final int SIDE_W = 170;

    /** Whether the custom menu should replace vanilla's right now. */
    public static boolean enabled() {
        return Modules.MENU_THEME.isActive() && Modules.MENU_THEME.customTitle() && Essential.shouldStyleMenus();
    }

    public BlurredTitleScreen() {
        super(Component.translatable("narrator.screen.title"));
    }

    @Override
    protected void build(KitComponent root) {
        Minecraft client = Minecraft.getInstance();
        int top = Math.max(24, uiHeight() / 2 - 120);

        root.add(new Wordmark()).setBounds(LEFT, top, 220, 46);

        int y = top + 64;
        int index = 0;
        root.add(new MenuRow(Icons.Icon.PERSON, "Singleplayer", null, index++,
                () -> client.gui.setScreen(new SelectWorldScreen(this)))).setBounds(LEFT, y, ROW_W, ROW_H);
        y += ROW_H + ROW_GAP;

        boolean mp = client.allowsMultiplayer();
        MenuRow multiplayer = new MenuRow(Icons.Icon.PEOPLE, "Multiplayer",
                mp ? null : "Disabled on this account", index++, () -> {
                    if (mp) {
                        client.gui.setScreen(client.options.skipMultiplayerWarning
                                ? new JoinMultiplayerScreen(this) : new SafetyScreen(this));
                    }
                });
        if (client.allowsRealms()) {
            multiplayer.link("Realms", () -> client.gui.setScreen(new RealmsMainScreen(this)));
        }
        root.add(multiplayer).setBounds(LEFT, y, ROW_W, ROW_H);
        y += ROW_H + ROW_GAP;

        root.add(new MenuRow(Icons.Icon.STAR, "Blurred", "Modules and settings", index++,
                () -> client.gui.setScreen(new ClickGuiScreen(this)))).setBounds(LEFT, y, ROW_W, ROW_H);
        y += ROW_H + ROW_GAP;

        root.add(new MenuRow(Icons.Icon.GEAR, "Options", null, index++,
                () -> client.gui.setScreen(new OptionsScreen(this, client.options, false)))).setBounds(LEFT, y, ROW_W, ROW_H);
        y += ROW_H + ROW_GAP;

        if (modMenuScreen() != null) {
            root.add(new MenuRow(Icons.Icon.PUZZLE, "Mods", null, index++,
                    () -> client.gui.setScreen(modMenuScreen()))).setBounds(LEFT, y, ROW_W, ROW_H);
        }

        int sx = uiWidth() - SIDE_W - 24;
        root.add(new AccountCard()).setBounds(sx, 20, SIDE_W, 30);
        root.add(new CrewCard()).setBounds(sx, 56, SIDE_W, 104);
        root.add(new NewsCard()).setBounds(sx, 166, SIDE_W, 78);

        Footer footer = root.add(new Footer());
        footer.item("Exit", client::stop);
        footer.item("Crew & chat", () -> client.gui.setScreen(new CrewScreen()));
        footer.item("Cosmetics", () -> client.gui.setScreen(new CosmeticsScreen(this)));
        footer.setBounds(LEFT, uiHeight() - 24, 260, 12);

        root.add(new VersionLabel()).setBounds(uiWidth() - 280, uiHeight() - 28, 256, 22);
    }

    /** Mod Menu's list screen, if Mod Menu is installed. Reflection: it's optional. */
    private Screen modMenuScreen() {
        if (!FabricLoader.getInstance().isModLoaded("modmenu")) {
            return null;
        }
        try {
            Class<?> type = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
            return (Screen) type.getConstructor(Screen.class).newInstance(this);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }

    private int scriptTick;

    /**
     * Dev check ({@code -PdevScreen=title -PdevScript=1}): log the frame rate
     * once warmed up, save a framebuffer screenshot, quit. No OS input.
     */
    @Override
    public void tick() {
        super.tick();
        if (!"title".equals(System.getProperty("blurred.devScreen")) || System.getProperty("blurred.devScript") == null) {
            return;
        }
        scriptTick++;
        if (scriptTick == 140 || scriptTick == 220) {
            dev.blurredclient.mod.BlurredMod.LOGGER.info("[devcheck] title fps={} (tick {})", this.minecraft.getFps(), scriptTick);
        }
        if (scriptTick == 30) {
            // Apply every mixin now, including ones on classes that only load
            // in a world (inventory screens, item renderers), so a bad target
            // shows up in this check rather than in someone's game.
            try {
                org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
                dev.blurredclient.mod.BlurredMod.LOGGER.info("[devcheck] mixin audit passed");
            } catch (Throwable t) {
                dev.blurredclient.mod.BlurredMod.LOGGER.error("[devcheck] mixin audit failed", t);
            }
        }
        if (scriptTick == 60) {
            net.minecraft.client.Screenshot.grab(
                    this.minecraft.gameDirectory, this.minecraft.gameRenderer.mainRenderTarget(), msg -> {});
        } else if (scriptTick == 224) {
            this.minecraft.stop();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // Esc on the title screen does nothing in vanilla either.
        return false;
    }

    // ------------------------------------------------------------------
    // Pieces
    // ------------------------------------------------------------------

    /** Big lowercase wordmark, a tagline, and a short accent rule. */
    private static final class Wordmark extends KitComponent {
        @Override
        protected boolean hitsSelf() {
            return false;
        }

        @Override
        protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
            Draw.smoothText(ctx, ui.font, "blurred", x - 1, y, 0xFFFFFFFF, 3.4f);
            String mc = FabricLoader.getInstance().getModContainer("minecraft")
                    .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
            ui.skin.drawText(ctx, ui.font, "client  •  Minecraft " + mc, x + 1, y + 32, ui.skin.textDim());
            Draw.rect(ctx, x + 1, y + 44, x + 29, y + 46, ui.skin.accent());
        }
    }

    /**
     * One menu entry: a square icon block, the label (and an optional line
     * under it), and an optional secondary link on the right. Slides in on
     * open; on hover it nudges right and grows an accent edge.
     */
    private static final class MenuRow extends KitComponent {
        private final Icons.Icon icon;
        private final String label;
        private final String sub;
        private final Runnable action;
        private final Anim enter = new Anim(0f, 9f);
        private final long startAt;
        private String linkLabel;
        private Runnable linkAction;

        MenuRow(Icons.Icon icon, String label, String sub, int index, Runnable action) {
            this.icon = icon;
            this.label = label;
            this.sub = sub;
            this.action = action;
            this.startAt = System.nanoTime() + index * 60_000_000L;
        }

        void link(String text, Runnable onClick) {
            this.linkLabel = text;
            this.linkAction = onClick;
        }

        private int linkW(Ui ui) {
            return ui.skin.width(ui.font, linkLabel + "  ›") + 10;
        }

        private boolean overLink(Ui ui, double mx) {
            return linkLabel != null && mx >= x + w - linkW(ui);
        }

        @Override
        protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
            if (System.nanoTime() >= startAt) {
                enter.target(1f);
            }
            float in = enter.get();
            if (in < 0.02f) {
                return;
            }
            float hov = hover.follow(ui.isHovered(this));
            int bx = x + Math.round((1f - in) * -30f + hov * 4f);
            int accent = ui.skin.accent();

            Draw.rect(ctx, bx, y, bx + w, y + h, Colors.fade(Colors.lerp(0xB80E1016, 0xD8141821, hov), in));
            Draw.rect(ctx, bx, y, bx + 2, y + h, Colors.fade(accent, Math.max(0.25f, hov) * in));

            // Icon block.
            Draw.rect(ctx, bx + 2, y, bx + 2 + h, y + h, Colors.fade(Colors.lerp(Colors.fade(accent, 0.18f), accent, hov * 0.85f), in));
            Icons.draw(ctx, icon, bx + 2 + (h - 12) / 2f, y + (h - 12) / 2f, 12, Colors.fade(0xFFFFFFFF, in));

            int tx = bx + h + 12;
            int white = Colors.fade(Colors.lerp(0xFFDDE3EA, 0xFFFFFFFF, hov), in);
            if (sub == null) {
                ui.skin.drawText(ctx, ui.font, label, tx, y + (h - 9) / 2, white);
            } else {
                ui.skin.drawText(ctx, ui.font, label, tx, y + 4, white);
                Draw.smoothText(ctx, ui.font, sub, tx, y + 15, Colors.fade(ui.skin.textFaint(), in), 0.85f);
            }

            if (linkLabel != null) {
                boolean overL = ui.isHovered(this) && overLink(ui, ui.mouseX);
                String t = linkLabel + "  ›";
                int lx = bx + w - ui.skin.width(ui.font, t) - 10;
                ui.skin.drawText(ctx, ui.font, t, lx, y + (h - 9) / 2, Colors.fade(overL ? accent : ui.skin.textDim(), in));
            }
        }

        @Override
        public boolean onMouseDown(double mx, double my, int button) {
            return button == 0;
        }

        @Override
        public void onMouseUp(double mx, double my, int button) {
            if (!contains(mx, my)) {
                return;
            }
            click();
            var font = Minecraft.getInstance().font;
            boolean onLink = linkLabel != null
                    && mx >= x + w - (dev.blurredclient.mod.ui.kit.Skins.current().width(font, linkLabel + "  ›") + 10);
            if (onLink) {
                linkAction.run();
            } else {
                action.run();
            }
        }
    }

    /** A side card with a small uppercase heading. */
    private abstract static class Card extends KitComponent {
        protected int body(GuiGraphicsExtractor ctx, Ui ui, String heading, String right) {
            ui.skin.panel(ctx, x, y, w, h, Skin.Surface.WINDOW);
            Draw.smoothText(ctx, ui.font, heading.toUpperCase(), x + 10, y + 7, ui.skin.textFaint(), 0.8f);
            if (right != null) {
                int rw = Draw.smoothWidth(ui.font, right, 0.8f);
                Draw.smoothText(ctx, ui.font, right, x + w - 10 - rw, y + 7, ui.skin.accent(), 0.8f);
            }
            return y + 22;
        }
    }

    /** Who you're signed in as, and whether the launcher is there. */
    private static final class AccountCard extends KitComponent {
        @Override
        protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
            Minecraft client = Minecraft.getInstance();
            ui.skin.panel(ctx, x, y, w, h, Skin.Surface.WINDOW);
            String name = client.getUser().getName();
            int d = h - 10;
            Draw.rect(ctx, x + 5, y + 5, x + 5 + d, y + 5 + d, ui.skin.accent());
            String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
            ui.skin.drawText(ctx, ui.font, initial, x + 5 + (d - ui.skin.width(ui.font, initial)) / 2,
                    y + 5 + (d - 9) / 2, 0xFFFFFFFF);
            int tx = x + d + 12;
            ui.skin.drawText(ctx, ui.font, Widgets.ellipsize(ui.font, ui.skin, name, w - (tx - x) - 8), tx, y + 4, 0xFFFFFFFF);
            LauncherBridge bridge = LauncherBridge.get();
            boolean up = bridge.isLauncherReachable() || bridge.isChatConnected();
            Draw.circle(ctx, tx + 2.5f, y + 21f, 2.2f, up ? 0xFF3BD87A : 0xFF6B7280);
            Draw.smoothText(ctx, ui.font, up ? "Launcher connected" : "Offline session", tx + 8, y + 16, ui.skin.textFaint(), 0.85f);
        }
    }

    /** Your crew: who's online, and the server they're on. */
    private static final class CrewCard extends Card {
        @Override
        protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
            LauncherBridge bridge = LauncherBridge.get();
            List<Friend> crew = bridge.isChatConnected() ? bridge.crew() : List.of();
            List<Friend> online = new ArrayList<>();
            for (Friend f : crew) {
                if (f.online()) {
                    online.add(f);
                }
            }
            int cy = body(ctx, ui, "Crew", crew.isEmpty() ? null : online.size() + " online");
            if (!bridge.isChatConnected()) {
                for (String line : Widgets.wrap(ui.font, ui.skin, "Open the Blurred launcher and connect to chat to see your crew here.", w - 20)) {
                    ui.skin.drawText(ctx, ui.font, line, x + 10, cy, ui.skin.textDim());
                    cy += 11;
                }
                return;
            }
            if (online.isEmpty()) {
                ui.skin.drawText(ctx, ui.font, "Nobody's online right now.", x + 10, cy, ui.skin.textDim());
                return;
            }
            for (int i = 0; i < Math.min(5, online.size()); i++) {
                Friend f = online.get(i);
                Draw.circle(ctx, x + 12.5f, cy + 4.5f, 2.2f, 0xFF3BD87A);
                ui.skin.drawText(ctx, ui.font, Widgets.ellipsize(ui.font, ui.skin, f.nick(), 70), x + 20, cy, 0xFFFFFFFF);
                String where = f.isJoinable() ? f.server() : "In launcher";
                String shown = Widgets.ellipsize(ui.font, ui.skin, where, w - 100);
                Draw.smoothText(ctx, ui.font, shown, x + w - 10 - Draw.smoothWidth(ui.font, shown, 0.85f), cy + 1,
                        ui.skin.textFaint(), 0.85f);
                cy += 14;
            }
        }
    }

    /** A short changelog for this version. */
    private static final class NewsCard extends Card {
        private static final String[] LINES = {
            "New click GUI — Right Shift",
            "HUD editor, module list, keystrokes",
            "Zoom, view model, low fire"
        };

        @Override
        protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
            int cy = body(ctx, ui, "What's new", null);
            for (String line : LINES) {
                Draw.rect(ctx, x + 10, cy + 4, x + 13, cy + 7, ui.skin.accent());
                ui.skin.drawText(ctx, ui.font, Widgets.ellipsize(ui.font, ui.skin, line, w - 28), x + 19, cy, ui.skin.textDim());
                cy += 15;
            }
        }
    }

    /** Small text actions along the bottom, separated by dots. */
    private static final class Footer extends KitComponent {
        private record Item(String label, Runnable action) {}

        private final List<Item> items = new ArrayList<>();

        void item(String label, Runnable action) {
            items.add(new Item(label, action));
        }

        private int itemAt(Ui ui, double mx) {
            int cx = x;
            for (int i = 0; i < items.size(); i++) {
                int iw = ui.skin.width(ui.font, items.get(i).label());
                if (mx >= cx && mx < cx + iw) {
                    return i;
                }
                cx += iw + 16;
            }
            return -1;
        }

        @Override
        protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
            int hot = ui.isHovered(this) ? itemAt(ui, ui.mouseX) : -1;
            int cx = x;
            for (int i = 0; i < items.size(); i++) {
                String l = items.get(i).label();
                ui.skin.drawText(ctx, ui.font, l, cx, y, i == hot ? ui.skin.accent() : ui.skin.textDim());
                cx += ui.skin.width(ui.font, l) + 16;
                if (i < items.size() - 1) {
                    Draw.circle(ctx, cx - 8f, y + 4.5f, 1f, ui.skin.textFaint());
                }
            }
        }

        @Override
        public boolean onMouseDown(double mx, double my, int button) {
            return button == 0;
        }

        @Override
        public void onMouseUp(double mx, double my, int button) {
            if (!contains(mx, my)) {
                return;
            }
            var font = Minecraft.getInstance().font;
            var skin = dev.blurredclient.mod.ui.kit.Skins.current();
            int cx = x;
            for (Item it : items) {
                int iw = skin.width(font, it.label());
                if (mx >= cx && mx < cx + iw) {
                    click();
                    it.action().run();
                    return;
                }
                cx += iw + 16;
            }
        }
    }

    /** Game and client version, and Mojang's copyright line as vanilla shows it. */
    private static final class VersionLabel extends KitComponent {
        @Override
        protected boolean hitsSelf() {
            return false;
        }

        @Override
        protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
            String blurred = FabricLoader.getInstance().getModContainer("blurred")
                    .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
            String line1 = "Blurred " + blurred.replaceAll("\\+mc.*$", "");
            String line2 = "Copyright Mojang AB. Do not distribute!";
            ui.skin.drawText(ctx, ui.font, line1, x + w - ui.skin.width(ui.font, line1), y, ui.skin.textDim());
            Draw.smoothText(ctx, ui.font, line2, x + w - Draw.smoothWidth(ui.font, line2, 0.85f), y + 11,
                    ui.skin.textFaint(), 0.85f);
        }
    }

    /** The vanilla button click. */
    private static void click() {
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
    }
}
