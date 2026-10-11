package dev.blurredclient.mod.screen;

import dev.blurredclient.mod.Theme;
import dev.blurredclient.mod.config.BlurredConfig;
import dev.blurredclient.mod.social.Essential;
import dev.blurredclient.mod.ui.BlurredFont;
import dev.blurredclient.mod.ui.OceanUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Everything in {@code blurred.json}, as a screen.
 *
 * <p>The config used to be a JSON file and two buttons buried in the cosmetics
 * screen, which meant most of what the mod can do was undiscoverable unless you
 * went looking on disk. Every option now has a control, a section it belongs
 * to, and a sentence saying what it does — the sentence being the part that
 * matters, because "Force gamemode" style labels tell you nothing on their own.
 *
 * <h2>Layout</h2>
 *
 * A single column of full-width rows rather than a grid of small buttons. Each
 * row is a toggle on the right and an explanation on the left, so the
 * explanation has room to be a real sentence. Rows scroll as a block; there is
 * no widget virtualisation because there are two dozen options, not two
 * thousand.
 */
public class ConfigScreen extends Screen {
    private static final int ROW_H = 24;
    private static final int ROW_GAP = 2;
    private static final int TOGGLE_W = 62;
    private static final int SECTION_GAP = 16;

    private final Screen parent;
    private final List<Entry> entries = new ArrayList<>();
    private int scroll;
    /** Total content height, worked out during layout. Drives scroll clamping. */
    private int contentHeight;

    public ConfigScreen(Screen parent) {
        super(BlurredFont.of("Blurred settings"));
        this.parent = parent;
    }

    /** A heading, or an option with its control. */
    private sealed interface Entry {
        record Section(String title) implements Entry {}

        record Option(String label, String hint, Supplier<String> value, Runnable toggle)
                implements Entry {}
    }

    // ------------------------------------------------------------------

    @Override
    protected void init() {
        BlurredConfig cfg = BlurredConfig.get();
        entries.clear();

        entries.add(new Entry.Section("Heads-up display"));
        onOff(cfg, "Show the HUD", "The whole overlay. F1 hides it too, like any other chrome.",
                () -> cfg.hudEnabled, () -> cfg.hudEnabled = !cfg.hudEnabled);
        entries.add(new Entry.Option(
                "Position",
                "Which corner of the top edge the readout sits against.",
                () -> cfg.hudPosition.label(),
                () -> {
                    cfg.hudPosition = cfg.hudPosition.next();
                    cfg.save();
                }));
        onOff(cfg, "Merge into one bar",
                "Draw every readout in a single pill instead of a stack of separate boxes.",
                () -> cfg.hudIsland, () -> cfg.hudIsland = !cfg.hudIsland);

        entries.add(new Entry.Section("What the HUD shows"));
        onOff(cfg, "Frame rate", "Smoothed, and coloured by how healthy it is.",
                () -> cfg.showFps, () -> cfg.showFps = !cfg.showFps);
        onOff(cfg, "Ping", "Your real latency, as the server measures it.",
                () -> cfg.showPing, () -> cfg.showPing = !cfg.showPing);
        onOff(cfg, "Coordinates",
                "Off by default — coordinates on screen end up in every screenshot and stream you make.",
                () -> cfg.showCoords, () -> cfg.showCoords = !cfg.showCoords);
        onOff(cfg, "Facing", "Compass direction, to eight points.",
                () -> cfg.showDirection, () -> cfg.showDirection = !cfg.showDirection);
        onOff(cfg, "Clicks per second", "Left and right, over the last second.",
                () -> cfg.showCps, () -> cfg.showCps = !cfg.showCps);
        onOff(cfg, "Clock", "The real time. Useful when the game covers your taskbar.",
                () -> cfg.showClock, () -> cfg.showClock = !cfg.showClock);

        entries.add(new Entry.Section("Menus and players"));
        onOff(cfg, "Ocean menu theme",
                "Restyle Minecraft's own menus. Container screens like the crafting table are always left alone.",
                () -> cfg.styleMenus, () -> cfg.styleMenus = !cfg.styleMenus);
        onOff(cfg, "Numeric ping in the tab list",
                "A number instead of five signal bars, which can't tell 30ms from 140ms.",
                () -> cfg.numericPing, () -> cfg.numericPing = !cfg.numericPing);
        onOff(cfg, "Mark other Blurred players",
                "Show the client mark beside players who are also on Blurred. Needs the launcher running and chat connected.",
                () -> cfg.showClientBadge, () -> cfg.showClientBadge = !cfg.showClientBadge);

        entries.add(new Entry.Section("Crew"));
        onOff(cfg, "Chat toasts", "Raise a toast in game when a crew message arrives.",
                () -> cfg.chatToasts, () -> cfg.chatToasts = !cfg.chatToasts);
        onOff(cfg, "Share which server you're on",
                "Lets your crew click to join you. Only the address, only to your crew.",
                () -> cfg.shareServer, () -> cfg.shareServer = !cfg.shareServer);
        onOff(cfg, "Announce LAN worlds",
                "When you open a singleplayer world to LAN, tell your crew the address so they get a one-click join.",
                () -> cfg.announceLanWorlds, () -> cfg.announceLanWorlds = !cfg.announceLanWorlds);

        entries.add(new Entry.Section("Discord"));
        onOff(cfg, "Discord status",
                cfg.discordAppId().isEmpty()
                        ? "Set a Discord application ID in the launcher's Settings to switch this on."
                        : "Show what you're playing on your Discord profile.",
                () -> cfg.discordEnabled, () -> cfg.discordEnabled = !cfg.discordEnabled);
        onOff(cfg, "Include the server address",
                "Off by default — a Discord profile is a much wider audience than your crew, and it's the server owner's traffic too.",
                () -> cfg.discordShowServer, () -> cfg.discordShowServer = !cfg.discordShowServer);

        entries.add(new Entry.Section("Cosmetics"));
        onOff(cfg, "Show capes", "Render Blurred capes for yourself and anyone else wearing one.",
                () -> cfg.showCapes, () -> cfg.showCapes = !cfg.showCapes);

        if (Essential.isPresent()) {
            entries.add(new Entry.Section("Essential"));
            onOff(cfg, "Let Essential own the menus",
                    "Essential restyles the main menu too. With this on, Blurred stands down so the two don't draw over each other.",
                    () -> cfg.deferToEssential, () -> cfg.deferToEssential = !cfg.deferToEssential);
        }

        layout();
    }

    /** Register a boolean option; saving is handled centrally. */
    private void onOff(BlurredConfig cfg, String label, String hint, BooleanSupplier get, Runnable flip) {
        entries.add(new Entry.Option(
                label,
                hint,
                () -> get.getAsBoolean() ? "On" : "Off",
                () -> {
                    flip.run();
                    cfg.save();
                }));
    }

    /** Place a button for every option row at its scrolled position. */
    private void layout() {
        this.clearWidgets();

        int panelX = panelX();
        int panelW = panelW();
        int y = top() - scroll;
        contentHeight = 0;

        for (Entry entry : entries) {
            if (entry instanceof Entry.Section) {
                y += SECTION_GAP;
                contentHeight += SECTION_GAP;
                continue;
            }
            Entry.Option option = (Entry.Option) entry;

            // Only build widgets for rows on screen. Buttons outside the
            // viewport would still be clickable through the header and footer.
            if (y + ROW_H > top() && y < bottom()) {
                this.addRenderableWidget(Button.builder(
                                BlurredFont.of(option.value().get()),
                                b -> {
                                    option.toggle().run();
                                    this.rebuildWidgets();
                                })
                        .bounds(panelX + panelW - TOGGLE_W - 8, y + 2, TOGGLE_W, ROW_H - 4)
                        .tooltip(Tooltip.create(Component.literal(option.hint())))
                        .build());
            }

            y += ROW_H + ROW_GAP;
            contentHeight += ROW_H + ROW_GAP;
        }

        this.addRenderableWidget(Button.builder(BlurredFont.of("Done"), b -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 26, 100, 20)
                .build());
    }

    private int panelX() {
        return Math.max(12, this.width / 2 - 190);
    }

    private int panelW() {
        return Math.min(380, this.width - 24);
    }

    private int top() {
        return 42;
    }

    private int bottom() {
        return this.height - 34;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (bottom() - top()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int next = Math.max(0, Math.min(maxScroll(), scroll - (int) (vertical * 18)));
        if (next != scroll) {
            scroll = next;
            layout();
        }
        return true;
    }

    /**
     * Chrome and labels.
     *
     * <p>Drawn from {@code renderBackground} for the same reason as
     * {@link CrewScreen}: the framework calls it exactly once per frame, and
     * calling it again by hand is a hard crash in game.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractBackground(ctx, mouseX, mouseY, delta);

        int panelX = panelX();
        int panelW = panelW();

        int logoW = OceanUi.logoWidth(this.font, 12);
        OceanUi.logo(ctx, this.font, (this.width - logoW) / 2, 12, 12);
        ctx.centeredText(
                this.font, BlurredFont.of("SETTINGS"), this.width / 2, 30, Theme.TEXT_DIM);

        OceanUi.panel(ctx, panelX - 6, top() - 4, panelW + 12, bottom() - top() + 8, false);

        // ctx.enableScissor keeps rows from painting over the header and the
        // Done button once the list is scrolled.
        ctx.enableScissor(panelX - 6, top(), panelX + panelW + 6, bottom());

        int y = top() - scroll;
        for (Entry entry : entries) {
            if (entry instanceof Entry.Section section) {
                if (y + SECTION_GAP > top() - 20 && y < bottom()) {
                    OceanUi.heading(ctx, this.font, section.title().toUpperCase(),
                            panelX, y + 4, panelW - 4);
                }
                y += SECTION_GAP;
                continue;
            }

            Entry.Option option = (Entry.Option) entry;
            if (y + ROW_H > top() && y < bottom()) {
                // Alternating row wash, so a long list stays scannable.
                ctx.fill(panelX - 2, y, panelX + panelW + 2, y + ROW_H, 0x14092230);

                ctx.text(this.font, BlurredFont.of(option.label()),
                        panelX + 4, y + 4, Theme.TEXT);

                String hint = this.font.plainSubstrByWidth(
                        option.hint(), panelW - TOGGLE_W - 22);
                ctx.text(this.font, BlurredFont.of(hint),
                        panelX + 4, y + 14, Theme.TEXT_FAINT);
            }
            y += ROW_H + ROW_GAP;
        }

        ctx.disableScissor();

        if (maxScroll() > 0) {
            // Scroll indicator: a bar whose length is the visible fraction.
            int trackH = bottom() - top();
            int barH = Math.max(16, trackH * trackH / Math.max(1, contentHeight));
            int barY = top() + (trackH - barH) * scroll / maxScroll();
            ctx.fill(panelX + panelW + 6, top(), panelX + panelW + 8, bottom(), 0x30092230);
            ctx.fill(panelX + panelW + 6, barY, panelX + panelW + 8, barY + barH, Theme.ACCENT_GLOW);
        }
    }

    @Override
    public void onClose() {
        BlurredConfig.get().save();
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
