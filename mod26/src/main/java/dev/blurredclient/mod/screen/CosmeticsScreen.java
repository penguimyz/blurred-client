package dev.blurredclient.mod.screen;

import dev.blurredclient.mod.Theme;
import dev.blurredclient.mod.ui.BlurredFont;
import dev.blurredclient.mod.bridge.CapeEntry;
import dev.blurredclient.mod.bridge.LauncherBridge;
import dev.blurredclient.mod.config.BlurredConfig;
import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.cosmetic.CapeManager;
import dev.blurredclient.mod.ui.OceanUi;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

/**
 * In-game cosmetics: pick a cape without alt-tabbing to the launcher.
 *
 * <p>Capes come from the launcher over the bridge, and wearing one sends a
 * command back — the launcher owns the library and the announcement to other
 * players, so this screen is a remote control rather than a second source of
 * truth.
 *
 * <p>Skins are deliberately not editable here. Changing a skin is a real Mojang
 * API call against your account, it needs the launcher's auth tokens, and it
 * affects every client you play on — that belongs on a screen where the
 * consequences can be spelled out, not behind a keybind mid-game.
 */
public class CosmeticsScreen extends Screen {
    private final Screen parent;
    private int scroll;

    public CosmeticsScreen(Screen parent) {
        super(BlurredFont.of("Cosmetics"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        LauncherBridge bridge = LauncherBridge.get();
        List<CapeEntry> capes = bridge.capes();

        int panelX = this.width / 2 - 150;
        int y = 60;

        for (int i = scroll; i < capes.size() && y < this.height - 70; i++) {
            CapeEntry cape = capes.get(i);
            boolean worn = cape.id().equals(bridge.wornCapeId());

            this.addRenderableWidget(Button.builder(
                            BlurredFont.of(worn ? "Worn" : "Wear"),
                            b -> {
                                bridge.wearCape(worn ? null : cape.id());
                                // Optimistic: the launcher echoes the change
                                // back, but waiting a round trip to redraw
                                // feels broken on a click.
                                this.rebuildWidgets();
                            })
                    .bounds(panelX + 230, y, 60, 20)
                    .build());
            y += 26;
        }

        this.addRenderableWidget(Button.builder(
                        BlurredFont.of("Take cape off"),
                        b -> {
                            bridge.wearCape(null);
                            this.rebuildWidgets();
                        })
                .bounds(this.width / 2 - 155, this.height - 52, 100, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                        BlurredFont.of(Modules.CAPES.isEnabled() ? "Capes: on" : "Capes: off"),
                        b -> {
                            Modules.CAPES.toggle();
                            BlurredConfig.save();
                            this.rebuildWidgets();
                        })
                .bounds(this.width / 2 - 50, this.height - 52, 100, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                        BlurredFont.of("Done"), b -> this.onClose())
                .bounds(this.width / 2 + 55, this.height - 52, 100, 20)
                .build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int max = Math.max(0, LauncherBridge.get().capes().size() - 1);
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(vertical)));
        if (next != scroll) {
            scroll = next;
            this.rebuildWidgets();
        }
        return true;
    }

    /**
     * Everything is drawn here rather than in {@link #render} for the same
     * reason as {@link CrewScreen}: {@code extractBackground} is called exactly
     * once per frame by the framework, and calling it a second time by hand
     * crashes in-game with "Can only blur once per frame". Which side calls it
     * differs between 1.21.1 and 1.21.11, so overriding it is the only
     * placement correct on both.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractBackground(ctx, mouseX, mouseY, delta);

        LauncherBridge bridge = LauncherBridge.get();
        int panelX = this.width / 2 - 155;
        int panelW = 310;

        // Header with the mark, so this reads as part of the client.
        int logoW = OceanUi.logoWidth(this.font, 12);
        OceanUi.logo(ctx, this.font, (this.width - logoW) / 2, 14, 12);
        ctx.centeredText(
                this.font, BlurredFont.of("COSMETICS"), this.width / 2, 34, Theme.TEXT_DIM);

        if (!bridge.isLauncherReachable()) {
            ctx.centeredText(
                    this.font,
                    BlurredFont.of("Launcher offline — start Blurred Client to manage capes"),
                    this.width / 2, this.height / 2, Theme.WARNING);
            return;
        }

        List<CapeEntry> capes = bridge.capes();
        if (capes.isEmpty()) {
            ctx.centeredText(
                    this.font,
                    BlurredFont.of("No capes yet — draw one in the launcher's Cosmetics tab"),
                    this.width / 2, this.height / 2, Theme.TEXT_FAINT);
            return;
        }

        int y = 56;
        for (int i = scroll; i < capes.size() && y < this.height - 70; i++) {
            CapeEntry cape = capes.get(i);
            boolean worn = cape.id().equals(bridge.wornCapeId());

            OceanUi.button(ctx, this.font, BlurredFont.of(""),
                    panelX, y, panelW, 24, false, true);

            // A marker block rather than a texture preview of the cape.
            //
            // `drawTexture` is the single least version-stable method in the
            // client — its parameter list and the RenderLayer it takes changed
            // repeatedly across the versions this mod targets. Previewing the
            // cape here would have meant a Stonecutter branch per version for a
            // 10x18 thumbnail, and the real preview already exists on the
            // launcher's Cosmetics tab where it costs nothing.
            boolean registered = worn && CapeManager.capeFor(bridge.nick()) != null;
            ctx.fill(panelX + 6, y + 6, panelX + 18, y + 18,
                    worn ? Theme.accent() : 0xFF2A4756);
            if (registered) {
                ctx.fill(panelX + 9, y + 11, panelX + 15, y + 13, 0x66041824);
            }

            ctx.text(this.font, BlurredFont.of(cape.name()),
                    panelX + 26, y + 8, worn ? Theme.accent() : Theme.TEXT);

            y += 26;
        }

        ctx.centeredText(
                this.font,
                BlurredFont.of("Capes show to other Blurred Client players only"),
                this.width / 2, this.height - 30, Theme.TEXT_FAINT);

    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Panels and text are drawn in extractBackground; super draws the widgets.
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
