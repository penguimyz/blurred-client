package dev.blurredclient.mod.screen;

import dev.blurredclient.mod.Theme;
import dev.blurredclient.mod.ui.BlurredFont;
import dev.blurredclient.mod.ui.OceanUi;
import dev.blurredclient.mod.bridge.ChatLine;
import dev.blurredclient.mod.bridge.Friend;
import dev.blurredclient.mod.bridge.LauncherBridge;
import dev.blurredclient.mod.hud.HudRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
//? if >=1.21.10
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

import java.util.ArrayList;
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
 * <h2>What you can do from here</h2>
 *
 * <ul>
 *   <li><b>Join.</b> A crew member playing somewhere gets a ▶ on their row that
 *       drops you onto the same server — including a singleplayer world they've
 *       opened to LAN, which to everything downstream is just an address.</li>
 *   <li><b>Add crew.</b> A name and a button at the foot of the sidebar. The
 *       people you want to add are the ones you're playing with right now, and
 *       having to quit to the launcher to type their name is exactly the moment
 *       you don't bother.</li>
 *   <li><b>Answer requests.</b> Incoming requests sit under the crew list with
 *       an accept and a decline, so one isn't left hanging for a session
 *       because the launcher is behind a fullscreen game.</li>
 * </ul>
 *
 * <p>None of this is done here: every action is a line to
 * {@link LauncherBridge}, and the launcher owns the handshake and the roster.
 * The screen is a view plus a set of buttons, which is why it needs no
 * credentials and has no network code.
 */
public class CrewScreen extends Screen {
    private static final int SIDEBAR_W = 150;
    private static final int GAP = 8;
    /** Top of the two columns, below the header bar. */
    private static final int BODY_Y = 40;
    /** Where the sidebar's own heading sits, inside its panel. */
    private static final int SIDEBAR_HEADING_Y = 50;
    /** First row of names, clear of the heading. */
    private static final int LIST_Y = 64;
    private static final int ROW_H = 18;
    private static final int ROW_STEP = 21;

    private TextFieldWidget input;
    private TextFieldWidget nickField;
    private int scroll;
    /** Which conversation the composer sends to. */
    private String target = "";

    /**
     * Field contents survive the re-layout that a roster change triggers —
     * losing half a typed message because someone logged in would be its own
     * small betrayal.
     */
    private String draftMessage = "";
    private String draftNick = "";

    /** What the sidebar currently shows, so drawing and hit-testing agree. */
    private final List<Slot> slots = new ArrayList<>();
    /** Y of the requests heading, or -1 when there are no requests. */
    private int requestsY = -1;
    /** Roster fingerprint, to notice when the sidebar needs rebuilding. */
    private String rosterKey = "";

    /** One sidebar row: a crew member, or a request awaiting an answer. */
    private record Slot(Friend friend, int y, boolean request) {}

    public CrewScreen() {
        super(BlurredFont.of("Crew"));
    }

    @Override
    protected void init() {
        LauncherBridge bridge = LauncherBridge.get();
        rosterKey = rosterKey(bridge);

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
                BlurredFont.of("Message"));
        this.input.setMaxLength(400);
        this.input.setText(draftMessage);
        this.input.setChangedListener(s -> draftMessage = s);
        this.input.setPlaceholder(BlurredFont.of(
                target.isEmpty() ? "Pick a conversation" : "Message " + target));
        this.addSelectableChild(this.input);

        this.addDrawableChild(ButtonWidget.builder(BlurredFont.of("Send"), b -> submit())
                .dimensions(chatX + chatW - 52, this.height - 29, 48, 20)
                .build());

        buildSidebar(bridge);
    }

    // ------------------------------------------------------------------
    // Sidebar
    // ------------------------------------------------------------------

    /** Crew rows, then requests, then the add-crew field along the bottom. */
    private void buildSidebar(LauncherBridge bridge) {
        slots.clear();
        requestsY = -1;

        int left = GAP + 4;
        int rowW = SIDEBAR_W - 8;
        int addY = addRowY();
        int listBottom = addY - 8;

        int y = LIST_Y;
        for (Friend f : bridge.crew()) {
            if (y + ROW_H > listBottom) {
                break;
            }
            slots.add(new Slot(f, y, false));

            final Friend friend = f;
            boolean joinable = f.isJoinable();
            int nameW = joinable ? rowW - 20 : rowW;

            this.addDrawableChild(ButtonWidget.builder(
                            BlurredFont.of(f.nick()),
                            b -> {
                                target = friend.nick();
                                rebuildPlaceholder();
                            })
                    .dimensions(left, y, nameW, ROW_H)
                    .tooltip(Tooltip.of(BlurredFont.of(
                            f.online() ? "Online — click to open a DM" : "Offline")))
                    .build());

            // Joining is its own button rather than a second meaning for the
            // row: a click that sometimes opens a chat and sometimes tears down
            // your world connection is not a click anyone can make confidently.
            if (joinable) {
                this.addDrawableChild(ButtonWidget.builder(
                                BlurredFont.of("▶"), b -> join(friend.server()))
                        .dimensions(left + rowW - 18, y, 18, ROW_H)
                        .tooltip(Tooltip.of(BlurredFont.of("Join " + friend.server())))
                        .build());
            }

            y += ROW_STEP;
        }

        List<Friend> pending = bridge.pending();
        if (!pending.isEmpty() && y + ROW_H + 14 <= listBottom) {
            requestsY = y + 3;
            y += 15;

            for (Friend f : pending) {
                if (y + ROW_H > listBottom) {
                    break;
                }
                slots.add(new Slot(f, y, true));
                final Friend friend = f;

                if (f.isPendingIn()) {
                    this.addDrawableChild(ButtonWidget.builder(
                                    BlurredFont.of("✔"),
                                    b -> LauncherBridge.get().acceptFriend(friend.nick()))
                            .dimensions(left + rowW - 38, y, 18, ROW_H)
                            .tooltip(Tooltip.of(BlurredFont.of("Add " + friend.nick() + " to your crew")))
                            .build());
                }

                this.addDrawableChild(ButtonWidget.builder(
                                BlurredFont.of("✖"),
                                b -> LauncherBridge.get().declineFriend(friend.nick()))
                        .dimensions(left + rowW - 18, y, 18, ROW_H)
                        .tooltip(Tooltip.of(BlurredFont.of(
                                friend.isPendingIn() ? "Decline" : "Withdraw the request")))
                        .build());

                y += ROW_STEP;
            }
        }

        this.nickField = new TextFieldWidget(
                this.textRenderer, left, addY + 1, rowW - 22, ROW_H, BlurredFont.of("Add crew"));
        this.nickField.setMaxLength(16);
        this.nickField.setText(draftNick);
        this.nickField.setChangedListener(s -> draftNick = s);
        this.nickField.setPlaceholder(BlurredFont.of("Add by name"));
        this.addSelectableChild(this.nickField);

        this.addDrawableChild(ButtonWidget.builder(BlurredFont.of("+"), b -> sendRequest())
                .dimensions(left + rowW - 20, addY, 20, 20)
                .tooltip(Tooltip.of(BlurredFont.of(
                        "Send a crew request. They need Blurred Client open to receive it.")))
                .build());
    }

    private int addRowY() {
        return this.height - GAP - 26;
    }

    /**
     * A fingerprint of everything the sidebar draws.
     *
     * <p>The roster changes underneath this screen — someone logs in, a request
     * arrives, a friend joins a server — and each of those changes which
     * buttons should exist, not just what they say. Comparing a fingerprint
     * each tick and rebuilding on a difference is what keeps the widgets and
     * the drawn rows from disagreeing, which is the classic way a list like
     * this ends up with a click landing on the wrong person.
     */
    private static String rosterKey(LauncherBridge bridge) {
        StringBuilder sb = new StringBuilder();
        for (Friend f : bridge.crew()) {
            sb.append(f.nick()).append(f.online()).append(f.server()).append('|');
        }
        for (Friend f : bridge.pending()) {
            sb.append(f.nick()).append(f.status()).append('|');
        }
        return sb.toString();
    }

    /**
     * Rebuild the sidebar when the roster moves under us.
     *
     * <p>Done on the tick rather than in {@code render} deliberately: replacing
     * the widget list is not something to do halfway through drawing it.
     */
    @Override
    public void tick() {
        super.tick();
        if (!rosterKey(LauncherBridge.get()).equals(rosterKey)) {
            this.clearAndInit();
        }
    }

    private void rebuildPlaceholder() {
        if (this.input != null) {
            this.input.setPlaceholder(BlurredFont.of("Message " + target));
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

    /**
     * Send a crew request to whoever is named in the field.
     *
     * <p>Whether it lands is the launcher's business to report — it answers
     * with a system line in the transcript either way, which is already on
     * screen next to this. Clearing the field is the only local feedback that
     * would be wrong to skip.
     */
    private void sendRequest() {
        String nick = this.nickField.getText().trim();
        if (nick.isEmpty()) {
            return;
        }
        LauncherBridge.get().addFriend(nick, "");
        this.nickField.setText("");
    }

    /** Enter sends whichever field has focus. */
    private boolean onEnter() {
        if (this.nickField != null && this.nickField.isFocused()) {
            sendRequest();
        } else {
            submit();
        }
        return true;
    }

    // Enter (257) and numpad Enter (335) send; Escape closes via super.
    // 1.21.9+ replaced the three int parameters with a KeyInput record.
    //? if >=1.21.10 {
    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == 257 || input.key() == 335) {
            return onEnter();
        }
        return super.keyPressed(input);
    }
    //?} else {
    /*@Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            return onEnter();
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

        int chatX = SIDEBAR_W + GAP * 2;
        int chatW = this.width - chatX - GAP;

        header(ctx, bridge);

        // Sidebar
        OceanUi.panel(ctx, GAP, BODY_Y, SIDEBAR_W, this.height - BODY_Y - GAP, false);
        ctx.drawTextWithShadow(this.textRenderer, BlurredFont.of("CREW"),
                GAP + 6, SIDEBAR_HEADING_Y, Theme.TEXT_FAINT);

        if (bridge.crew().isEmpty()) {
            ctx.drawTextWithShadow(this.textRenderer, BlurredFont.of("No crew yet"),
                    GAP + 6, LIST_Y + 4, Theme.TEXT_FAINT);
        }

        if (requestsY >= 0) {
            OceanUi.heading(ctx, this.textRenderer, "REQUESTS", GAP + 6, requestsY, SIDEBAR_W - 12);
        }

        // Chat column
        OceanUi.panel(ctx, chatX, BODY_Y, chatW, this.height - BODY_Y - 34, false);
    }

    /**
     * The top bar: who we are on the left, which conversation we're in on the
     * right.
     *
     * <p>The conversation name used to float above the transcript panel, which
     * put the one piece of state that changes as you click around in the one
     * place nothing else lived — it read as a stray label rather than as a
     * title. On the bar it sits opposite the connection status, where a window
     * title belongs, and the space above the transcript goes back to the
     * transcript.
     */
    private void header(DrawContext ctx, LauncherBridge bridge) {
        OceanUi.panel(ctx, GAP, GAP, this.width - GAP * 2, 26, false);
        HudRenderer.porthole(ctx, GAP + 16, GAP + 13, 6);
        ctx.drawTextWithShadow(this.textRenderer, BlurredFont.of("BLURRED"),
                GAP + 28, GAP + 9, Theme.ACCENT);

        Text conversation = BlurredFont.of(target.isEmpty() ? "No conversation" : target);
        int conversationW = this.textRenderer.getWidth(conversation);
        int conversationX = this.width - GAP - 6 - conversationW;
        ctx.drawTextWithShadow(this.textRenderer, conversation, conversationX, GAP + 9,
                target.isEmpty() ? Theme.TEXT_FAINT : Theme.ACCENT);

        String status = !bridge.isLauncherReachable()
                ? "Launcher offline — start Blurred Client for crew and chat"
                : bridge.isChatConnected()
                        ? "Connected as " + bridge.nick()
                        : "Launcher up, chat not connected";

        // Trimmed against the conversation title rather than the window, so the
        // two never overlap on a narrow window or at a large GUI scale.
        int statusX = GAP + 96;
        String trimmed = this.textRenderer.trimToWidth(status, Math.max(0, conversationX - statusX - 8));
        ctx.drawTextWithShadow(this.textRenderer, BlurredFont.of(trimmed), statusX, GAP + 9,
                bridge.isChatConnected() ? Theme.TEXT_DIM : Theme.WARNING);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Background and panels come from renderBackground, which the framework
        // calls for us — see the note there. Widgets are drawn by super.
        super.render(ctx, mouseX, mouseY, delta);

        LauncherBridge bridge = LauncherBridge.get();

        // Presence dots and request labels sit ON TOP of the row widgets, so
        // they have to be drawn after super has rendered those.
        for (Slot slot : slots) {
            if (slot.request()) {
                Friend f = slot.friend();
                String label = this.textRenderer.trimToWidth(f.nick(), SIDEBAR_W - 58);
                ctx.drawTextWithShadow(this.textRenderer, BlurredFont.of(label),
                        GAP + 8, slot.y() + 5, f.isPendingIn() ? Theme.TEXT : Theme.TEXT_FAINT);
            } else {
                ctx.fill(GAP + 8, slot.y() + 7, GAP + 12, slot.y() + 11,
                        slot.friend().online() ? Theme.ONLINE : Theme.OFFLINE);
            }
        }

        int chatX = SIDEBAR_W + GAP * 2;
        renderTranscript(ctx, bridge, chatX, this.width - chatX - GAP);

        // Added with addSelectableChild, not addDrawableChild, so they aren't
        // in the list super just rendered.
        this.input.render(ctx, mouseX, mouseY, delta);
        this.nickField.render(ctx, mouseX, mouseY, delta);
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
        int top = BODY_Y + 6;
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
                    this.textRenderer.wrapLines(BlurredFont.of(text), chatW - 12);
            for (int w = wrapped.size() - 1; w >= 0 && y >= top; w--) {
                ctx.drawTextWithShadow(this.textRenderer, wrapped.get(w), chatX + 6, y, color);
                y -= lineH;
            }
            shown++;
        }

        if (shown == 0) {
            ctx.drawTextWithShadow(this.textRenderer,
                    BlurredFont.of("Nothing here yet."), chatX + 6, top, Theme.TEXT_FAINT);
        }
    }

    @Override
    public boolean shouldPause() {
        // Don't pause singleplayer: this is a chat overlay, and pausing to read
        // a message would be worse than the message.
        return false;
    }
}
