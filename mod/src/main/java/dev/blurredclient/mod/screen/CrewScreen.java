package dev.blurredclient.mod.screen;

import dev.blurredclient.mod.Theme;
import dev.blurredclient.mod.bridge.ChatLine;
import dev.blurredclient.mod.bridge.Friend;
import dev.blurredclient.mod.bridge.LauncherBridge;
import dev.blurredclient.mod.hud.HudRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
//? if >=1.21.10
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

import java.util.List;

/**
 * The in-game crew panel: who's online, where they're playing, and the chat
 * stream — without alt-tabbing back to the launcher.
 *
 * <p>Laid out as two columns in the launcher's shape: crew on the left,
 * transcript and composer on the right. Styling comes from {@link Theme} and
 * {@link HudRenderer#panel}, so it reads as the same product as the launcher
 * rather than as a generic mod screen.
 *
 * <p>Everything shown here is a read-only view of {@link LauncherBridge} state.
 * The screen never talks to the network itself.
 */
public class CrewScreen extends Screen {
    private static final int SIDEBAR_W = 150;
    private static final int GAP = 8;

    private TextFieldWidget input;
    private int scroll;
    /** Which conversation the composer sends to. */
    private String target = "";

    public CrewScreen() {
        super(Text.literal("Crew"));
    }

    @Override
    protected void init() {
        LauncherBridge bridge = LauncherBridge.get();

        // Default the composer at the first channel we've seen traffic on, so
        // pressing the key and typing "just works" in the common case.
        if (target.isEmpty()) {
            for (ChatLine m : bridge.messages()) {
                if (m.conversation().startsWith("#")) {
                    target = m.conversation();
                    break;
                }
            }
        }

        int chatX = SIDEBAR_W + GAP * 2;
        int chatW = this.width - chatX - GAP;

        this.input = new TextFieldWidget(
                this.textRenderer, chatX + 4, this.height - 28, chatW - 60, 18,
                Text.literal("Message"));
        this.input.setMaxLength(400);
        this.input.setPlaceholder(Text.literal(
                target.isEmpty() ? "Pick a conversation" : "Message " + target));
        this.addSelectableChild(this.input);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Send"), b -> submit())
                .dimensions(chatX + chatW - 52, this.height - 29, 48, 20)
                .build());

        // Crew rows are buttons so they're keyboard-navigable, which a
        // hand-drawn list wouldn't be.
        List<Friend> crew = bridge.crew();
        int y = 46;
        for (Friend f : crew) {
            if (y > this.height - 60) {
                break;
            }
            final Friend friend = f;
            this.addDrawableChild(ButtonWidget.builder(
                            Text.literal(f.nick()),
                            b -> {
                                if (friend.isJoinable()) {
                                    join(friend.server());
                                } else {
                                    target = friend.nick();
                                    rebuildPlaceholder();
                                }
                            })
                    .dimensions(GAP, y, SIDEBAR_W, 18)
                    .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(
                            friend.isJoinable()
                                    ? "Playing on " + friend.server() + " — click to join"
                                    : friend.online()
                                            ? "Online — click to open a DM"
                                            : "Offline")))
                    .build());
            y += 21;
        }
    }

    private void rebuildPlaceholder() {
        if (this.input != null) {
            this.input.setPlaceholder(Text.literal("Message " + target));
        }
    }

    /** Disconnect from the current world and connect to a crew member's server. */
    private void join(String address) {
        if (this.client == null) {
            return;
        }
        ServerInfo info = new ServerInfo("Crew server", address, ServerInfo.ServerType.OTHER);
        ConnectScreen.connect(
                new MultiplayerScreen(new net.minecraft.client.gui.screen.TitleScreen()),
                this.client,
                ServerAddress.parse(address),
                info,
                false,
                null);
    }

    private void submit() {
        String text = this.input.getText().trim();
        if (text.isEmpty() || target.isEmpty()) {
            return;
        }
        LauncherBridge.get().send(target, text);
        this.input.setText("");
    }

    // Enter (257) and numpad Enter (335) send; Escape closes via super.
    // 1.21.9+ replaced the three int parameters with a KeyInput record.
    //? if >=1.21.10 {
    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == 257 || input.key() == 335) {
            submit();
            return true;
        }
        return super.keyPressed(input);
    }
    //?} else {
    /*@Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
     *///?}

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = Math.max(0, scroll - (int) Math.signum(vertical));
        return true;
    }

    /**
     * Background plus this screen's chrome.
     *
     * <p>All the panel drawing lives here rather than in {@link #render},
     * because {@code renderBackground} is the one method the framework
     * guarantees to call <b>exactly once per frame</b> — and calling it a second
     * time by hand is a hard crash in-game:
     *
     * <pre>java.lang.IllegalStateException: Can only blur once per frame</pre>
     *
     * <p>Which side calls it differs by version — 1.21.1 calls it from
     * {@code Screen.render}, 1.21.11 from {@code renderWithTooltip} before
     * {@code render} — so overriding it is the only placement that is correct
     * on both without a version branch.
     */
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);

        LauncherBridge bridge = LauncherBridge.get();

        // Header
        HudRenderer.panel(ctx, GAP, GAP, this.width - GAP * 2, 26);
        HudRenderer.porthole(ctx, GAP + 16, GAP + 13, 6);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("BLURRED"),
                GAP + 28, GAP + 9, Theme.ACCENT);

        String status = !bridge.isLauncherReachable()
                ? "Launcher offline — start Blurred Client for crew and chat"
                : bridge.isChatConnected()
                        ? "Connected as " + bridge.nick()
                        : "Launcher up, chat not connected";
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(status),
                GAP + 96, GAP + 9,
                bridge.isChatConnected() ? Theme.TEXT_DIM : Theme.WARNING);

        // Sidebar
        HudRenderer.panel(ctx, GAP, 40, SIDEBAR_W, this.height - 40 - GAP);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("CREW"),
                GAP + 5, 30, Theme.TEXT_FAINT);

        if (bridge.crew().isEmpty()) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal("No crew yet"),
                    GAP + 6, 50, Theme.TEXT_FAINT);
        }

        // Chat column
        int chatX = SIDEBAR_W + GAP * 2;
        int chatW = this.width - chatX - GAP;
        HudRenderer.panel(ctx, chatX, 40, chatW, this.height - 40 - 34);

        ctx.drawTextWithShadow(this.textRenderer,
                Text.literal(target.isEmpty() ? "No conversation" : target),
                chatX + 5, 30, Theme.TEXT_FAINT);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Background and panels come from renderBackground, which the framework
        // calls for us — see the note there. Widgets are drawn by super.
        super.render(ctx, mouseX, mouseY, delta);

        LauncherBridge bridge = LauncherBridge.get();

        // Presence dots sit ON TOP of the crew row buttons, so they have to be
        // drawn after super has rendered those widgets.
        int y = 46;
        for (Friend f : bridge.crew()) {
            if (y > this.height - 60) {
                break;
            }
            ctx.fill(GAP + SIDEBAR_W - 10, y + 7, GAP + SIDEBAR_W - 6, y + 11,
                    f.online() ? Theme.ONLINE : Theme.OFFLINE);
            if (f.isJoinable()) {
                ctx.drawTextWithShadow(this.textRenderer, Text.literal("▶"),
                        GAP + SIDEBAR_W - 22, y + 5, Theme.ACCENT);
            }
            y += 21;
        }

        int chatX = SIDEBAR_W + GAP * 2;
        renderTranscript(ctx, bridge, chatX, this.width - chatX - GAP);

        // Added with addSelectableChild, not addDrawableChild, so it isn't in
        // the list super just rendered.
        this.input.render(ctx, mouseX, mouseY, delta);
    }

    /**
     * Draw the transcript bottom-up from the newest line, so the most recent
     * message is always visible and older ones fall off the top — the same rule
     * the launcher's transcript follows.
     */
    private void renderTranscript(DrawContext ctx, LauncherBridge bridge, int chatX, int chatW) {
        List<ChatLine> all = bridge.messages();
        int lineH = this.textRenderer.fontHeight + 2;
        int bottom = this.height - 40;
        int top = 46;
        int y = bottom - lineH;

        int shown = 0;
        for (int i = all.size() - 1 - scroll; i >= 0 && y >= top; i--) {
            ChatLine m = all.get(i);
            // Only the selected conversation, plus system notices which belong
            // to none and are always worth seeing.
            if (!target.isEmpty()
                    && !m.conversation().equalsIgnoreCase(target)
                    && !m.isSystem()) {
                continue;
            }

            String text = m.isSystem()
                    ? m.text()
                    : "<" + m.from() + "> " + m.text();
            int color = m.isSystem()
                    ? Theme.TEXT_FAINT
                    : m.mine() ? Theme.ACCENT : Theme.TEXT;

            // Wrap long lines rather than clipping them.
            List<net.minecraft.text.OrderedText> wrapped =
                    this.textRenderer.wrapLines(Text.literal(text), chatW - 12);
            for (int w = wrapped.size() - 1; w >= 0 && y >= top; w--) {
                ctx.drawTextWithShadow(this.textRenderer, wrapped.get(w), chatX + 6, y, color);
                y -= lineH;
            }
            shown++;
        }

        if (shown == 0) {
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("Nothing here yet."), chatX + 6, top, Theme.TEXT_FAINT);
        }
    }

    @Override
    public boolean shouldPause() {
        // Don't pause singleplayer: this is a chat overlay, and pausing to read
        // a message would be worse than the message.
        return false;
    }
}
