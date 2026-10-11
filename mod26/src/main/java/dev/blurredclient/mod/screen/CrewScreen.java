package dev.blurredclient.mod.screen;

import dev.blurredclient.mod.Theme;
import dev.blurredclient.mod.ui.BlurredFont;
import dev.blurredclient.mod.ui.OceanUi;
import dev.blurredclient.mod.bridge.ChatLine;
import dev.blurredclient.mod.bridge.Friend;
import dev.blurredclient.mod.bridge.LauncherBridge;
import dev.blurredclient.mod.hud.HudRenderer;
import dev.blurredclient.mod.social.BlurredUsers;
import dev.blurredclient.mod.social.ServerPlayers;
import dev.blurredclient.mod.social.Whispers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

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
 *   <li><b>Talk to anyone on the server.</b> The PLAYERS tab lists everyone on
 *       the server you're on, whatever client they run, and messages them
 *       through the server's own {@code /msg}. See below.</li>
 * </ul>
 *
 * <p>Crew actions are all a line to {@link LauncherBridge}, and the launcher
 * owns the handshake and the roster — which is why this screen needs no
 * credentials for them.
 *
 * <h2>The two rosters, and why there are two</h2>
 *
 * CREW is the launcher's: people whose Blurred launcher is in our lobby. It's
 * the list that can do the good things — presence, one-click join, chat that
 * follows you between servers.
 *
 * <p>PLAYERS is the server's tab list. It contains the Essential user standing
 * next to you and the vanilla player who has never heard of either client, and
 * it is the only roster that can. Essential publishes no API, so there is no
 * way to see their friends or put a message into their client; what both
 * clients do share is the server, so messages to those players go out as
 * {@code /msg} (see {@link Whispers}) and crew requests go out as a request
 * plus a whisper explaining it. Blurred users in that list are marked with the
 * porthole, so it's obvious who you get the good version with.
 */
public class CrewScreen extends Screen {
    private static final int SIDEBAR_W = 150;
    private static final int GAP = 8;
    /** Top of the two columns, below the header bar. */
    private static final int BODY_Y = 40;
    /**
     * The CREW / PLAYERS tab strip, inside the sidebar panel. It replaced a
     * static "CREW" caption, which is why it sits where a heading would.
     */
    private static final int TABS_Y = 46;
    private static final int TAB_H = 16;
    /** First row of names, clear of the tab strip. */
    private static final int LIST_Y = 68;
    private static final int ROW_H = 18;
    private static final int ROW_STEP = 21;

    /** Which roster the sidebar is showing. */
    private enum Tab {
        /** The launcher's crew: Blurred users, with presence and join. */
        CREW,
        /** The server's tab list: everyone here, whatever client they run. */
        PLAYERS
    }

    private EditBox input;
    private EditBox nickField;
    private int scroll;
    private Tab tab = Tab.CREW;
    /** Which conversation the composer sends to. */
    private String target = "";
    /**
     * Whether {@link #target} is a player on this server rather than a crew
     * member or channel.
     *
     * <p>This is what routes a message: the same name can mean both — someone
     * in your crew is often also standing next to you — and the two go to
     * completely different places. Crew chat reaches them wherever they are;
     * {@code /msg} only works while you're both on this server, but works
     * whatever client they run.
     */
    private boolean targetIsPlayer;

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

    /** One row of the PLAYERS tab. */
    private record PlayerSlot(String name, int y, boolean blurred) {}

    private final List<PlayerSlot> playerSlots = new ArrayList<>();

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

        this.input = new EditBox(
                this.font, chatX + 4, this.height - 28, chatW - 60, 18,
                BlurredFont.of("Message"));
        this.input.setMaxLength(400);
        this.input.setValue(draftMessage);
        this.input.setResponder(s -> draftMessage = s);
        this.input.setHint(BlurredFont.of(
                target.isEmpty() ? "Pick a conversation" : "Message " + target));
        this.addWidget(this.input);

        this.addRenderableWidget(Button.builder(BlurredFont.of("Send"), b -> submit())
                .bounds(chatX + chatW - 52, this.height - 29, 48, 20)
                .build());

        buildSidebar(bridge);
    }

    // ------------------------------------------------------------------
    // Sidebar
    // ------------------------------------------------------------------

    /**
     * The tab strip, then whichever roster is selected.
     *
     * <p>The strip is always drawn, including in singleplayer where PLAYERS is
     * empty — a control that appears and disappears with the world is harder to
     * learn than one that's simply empty sometimes.
     */
    private void buildSidebar(LauncherBridge bridge) {
        slots.clear();
        playerSlots.clear();
        requestsY = -1;
        // Only the crew tab has an add-by-name field. Cleared rather than left
        // pointing at the previous tab's widget, which is no longer a child of
        // this screen and would draw over the player list if rendered.
        this.nickField = null;

        int left = GAP + 4;
        int rowW = SIDEBAR_W - 8;
        int tabW = (rowW - 2) / 2;

        this.addRenderableWidget(Button.builder(BlurredFont.of("Crew"), b -> switchTab(Tab.CREW))
                .bounds(left, TABS_Y, tabW, TAB_H)
                .tooltip(Tooltip.create(BlurredFont.of("Your Blurred crew — presence, chat and one-click join")))
                .build());
        this.addRenderableWidget(Button.builder(BlurredFont.of("Here"), b -> switchTab(Tab.PLAYERS))
                .bounds(left + tabW + 2, TABS_Y, rowW - tabW - 2, TAB_H)
                .tooltip(Tooltip.create(BlurredFont.of(
                        "Everyone on this server, whatever client they run — message them or add them")))
                .build());

        if (tab == Tab.PLAYERS) {
            buildPlayerList();
        } else {
            buildCrewList(bridge);
        }
    }

    private void switchTab(Tab next) {
        if (tab != next) {
            tab = next;
            this.rebuildWidgets();
        }
    }

    /** Crew rows, then requests, then the add-crew field along the bottom. */
    private void buildCrewList(LauncherBridge bridge) {
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

            this.addRenderableWidget(Button.builder(
                            BlurredFont.of(f.nick()),
                            b -> {
                                target = friend.nick();
                                targetIsPlayer = false;
                                rebuildPlaceholder();
                            })
                    .bounds(left, y, nameW, ROW_H)
                    .tooltip(Tooltip.create(BlurredFont.of(
                            f.online() ? "Online — click to open a DM" : "Offline")))
                    .build());

            // Joining is its own button rather than a second meaning for the
            // row: a click that sometimes opens a chat and sometimes tears down
            // your world connection is not a click anyone can make confidently.
            if (joinable) {
                this.addRenderableWidget(Button.builder(
                                BlurredFont.of("▶"), b -> join(friend.server()))
                        .bounds(left + rowW - 18, y, 18, ROW_H)
                        .tooltip(Tooltip.create(BlurredFont.of("Join " + friend.server())))
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
                    this.addRenderableWidget(Button.builder(
                                    BlurredFont.of("✔"),
                                    b -> LauncherBridge.get().acceptFriend(friend.nick()))
                            .bounds(left + rowW - 38, y, 18, ROW_H)
                            .tooltip(Tooltip.create(BlurredFont.of("Add " + friend.nick() + " to your crew")))
                            .build());
                }

                this.addRenderableWidget(Button.builder(
                                BlurredFont.of("✖"),
                                b -> LauncherBridge.get().declineFriend(friend.nick()))
                        .bounds(left + rowW - 18, y, 18, ROW_H)
                        .tooltip(Tooltip.create(BlurredFont.of(
                                friend.isPendingIn() ? "Decline" : "Withdraw the request")))
                        .build());

                y += ROW_STEP;
            }
        }

        this.nickField = new EditBox(
                this.font, left, addY + 1, rowW - 22, ROW_H, BlurredFont.of("Add crew"));
        this.nickField.setMaxLength(16);
        this.nickField.setValue(draftNick);
        this.nickField.setResponder(s -> draftNick = s);
        this.nickField.setHint(BlurredFont.of("Add by name"));
        this.addWidget(this.nickField);

        this.addRenderableWidget(Button.builder(BlurredFont.of("+"), b -> sendRequest())
                .bounds(left + rowW - 20, addY, 20, 20)
                .tooltip(Tooltip.create(BlurredFont.of(
                        "Send a crew request. They need Blurred Client open to receive it.")))
                .build());
    }

    /**
     * Everyone on this server: click to message, ✚ to add to your crew.
     *
     * <p>The add button does two things at once, and both are needed. The crew
     * request is what a Blurred user accepts with a click; the whisper is what
     * tells everyone else that a request exists at all, since nothing in their
     * client is going to mention it. Sending only one of the two leaves either
     * a request nobody hears about or a message with nothing behind it.
     */
    private void buildPlayerList() {
        if (this.minecraft == null) {
            return;
        }

        int left = GAP + 4;
        int rowW = SIDEBAR_W - 8;
        int listBottom = this.height - GAP - 6;

        int y = LIST_Y;
        for (String name : ServerPlayers.others(this.minecraft)) {
            if (y + ROW_H > listBottom) {
                break;
            }
            boolean blurred = BlurredUsers.isBlurredUser(name);
            playerSlots.add(new PlayerSlot(name, y, blurred));

            final String player = name;
            this.addRenderableWidget(Button.builder(
                            BlurredFont.of(name),
                            b -> {
                                target = player;
                                targetIsPlayer = true;
                                rebuildPlaceholder();
                            })
                    .bounds(left, y, rowW - 20, ROW_H)
                    .tooltip(Tooltip.create(BlurredFont.of(
                            blurred
                                    ? name + " is on Blurred — click to message them here"
                                    : "Click to message " + name + " through the server")))
                    .build());

            this.addRenderableWidget(Button.builder(
                            BlurredFont.of("✚"), b -> addPlayerToCrew(player))
                    .bounds(left + rowW - 18, y, 18, ROW_H)
                    .tooltip(Tooltip.create(BlurredFont.of("Add " + name + " to your crew")))
                    .build());

            y += ROW_STEP;
        }
    }

    /**
     * Send a crew request, and tell them about it in a way they'll see.
     *
     * <p>See {@link Whispers#sendCrewInvite}. The whisper is skipped for players
     * we already know are on Blurred: their client shows the request properly,
     * so a whisper about it would just be noise.
     */
    private void addPlayerToCrew(String player) {
        LauncherBridge bridge = LauncherBridge.get();
        bridge.addFriend(player, "");
        if (this.minecraft != null && !BlurredUsers.isBlurredUser(player)) {
            Whispers.sendCrewInvite(this.minecraft, player, bridge.nick());
        }
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
    private String rosterKey(LauncherBridge bridge) {
        StringBuilder sb = new StringBuilder();
        sb.append(tab).append('|');
        if (tab == Tab.PLAYERS) {
            // The server's roster moves the same way the crew's does — someone
            // joins, someone leaves — and drives the same widget rebuild.
            if (this.minecraft != null) {
                for (String name : ServerPlayers.others(this.minecraft)) {
                    sb.append(name).append(BlurredUsers.isBlurredUser(name)).append('|');
                }
            }
            return sb.toString();
        }
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
            this.rebuildWidgets();
        }
    }

    private void rebuildPlaceholder() {
        if (this.input != null) {
            this.input.setHint(BlurredFont.of(
                    (targetIsPlayer ? "Whisper " : "Message ") + target));
        }
    }

    /** Disconnect from the current world and connect to a crew member's server. */
    private void join(String address) {
        if (this.minecraft == null) {
            return;
        }
        ServerData info = new ServerData("Crew server", address, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(
                new JoinMultiplayerScreen(new net.minecraft.client.gui.screens.TitleScreen()),
                this.minecraft,
                ServerAddress.parseString(address),
                info,
                false,
                null);
    }

    /**
     * Send whatever's typed, down whichever pipe the target needs.
     *
     * <p>A player from the PLAYERS tab goes out as a server whisper; a crew
     * member or channel goes to the launcher. Neither is a fallback for the
     * other — see {@link #targetIsPlayer}.
     */
    private void submit() {
        String text = this.input.getValue().trim();
        if (text.isEmpty() || target.isEmpty()) {
            return;
        }

        if (targetIsPlayer) {
            if (this.minecraft == null || !Whispers.send(this.minecraft, target, text)) {
                return;
            }
        } else {
            LauncherBridge.get().send(target, text);
        }
        this.input.setValue("");
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
        String nick = this.nickField.getValue().trim();
        if (nick.isEmpty()) {
            return;
        }
        LauncherBridge.get().addFriend(nick, "");
        this.nickField.setValue("");
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
    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == 257 || input.key() == 335) {
            return onEnter();
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = Math.max(0, scroll - (int) Math.signum(vertical));
        return true;
    }

    /**
     * Background plus this screen's chrome.
     *
     * <p>All the panel drawing lives here rather than in {@link #render},
     * because {@code extractBackground} is the one method the framework
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
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractBackground(ctx, mouseX, mouseY, delta);

        LauncherBridge bridge = LauncherBridge.get();

        int chatX = SIDEBAR_W + GAP * 2;
        int chatW = this.width - chatX - GAP;

        header(ctx, bridge);

        // Sidebar
        OceanUi.panel(ctx, GAP, BODY_Y, SIDEBAR_W, this.height - BODY_Y - GAP, false);

        // Empty-state lines. Each says what would fill the list rather than
        // just that it's empty — "no players" on a screen you reached from
        // singleplayer tells you nothing you didn't know.
        if (tab == Tab.PLAYERS) {
            if (this.minecraft != null && !ServerPlayers.onServer(this.minecraft)) {
                ctx.text(this.font, BlurredFont.of("Not on a server"),
                        GAP + 6, LIST_Y + 4, Theme.TEXT_FAINT);
            } else if (playerSlots.isEmpty()) {
                ctx.text(this.font, BlurredFont.of("Nobody else here"),
                        GAP + 6, LIST_Y + 4, Theme.TEXT_FAINT);
            }
        } else if (bridge.crew().isEmpty()) {
            ctx.text(this.font, BlurredFont.of("No crew yet"),
                    GAP + 6, LIST_Y + 4, Theme.TEXT_FAINT);
        }

        if (requestsY >= 0) {
            OceanUi.heading(ctx, this.font, "REQUESTS", GAP + 6, requestsY, SIDEBAR_W - 12);
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
    private void header(GuiGraphicsExtractor ctx, LauncherBridge bridge) {
        OceanUi.panel(ctx, GAP, GAP, this.width - GAP * 2, 26, false);
        HudRenderer.porthole(ctx, GAP + 16, GAP + 13, 6);
        ctx.text(this.font, BlurredFont.of("BLURRED"),
                GAP + 28, GAP + 9, Theme.accent());

        Component conversation = BlurredFont.of(target.isEmpty() ? "No conversation" : target);
        int conversationW = this.font.width(conversation);
        int conversationX = this.width - GAP - 6 - conversationW;
        ctx.text(this.font, conversation, conversationX, GAP + 9,
                target.isEmpty() ? Theme.TEXT_FAINT : Theme.accent());

        String status = !bridge.isLauncherReachable()
                ? "Launcher offline — start Blurred Client for crew and chat"
                : bridge.isChatConnected()
                        ? "Connected as " + bridge.nick()
                        : "Launcher up, chat not connected";

        // Trimmed against the conversation title rather than the window, so the
        // two never overlap on a narrow window or at a large GUI scale.
        int statusX = GAP + 96;
        String trimmed = this.font.plainSubstrByWidth(status, Math.max(0, conversationX - statusX - 8));
        ctx.text(this.font, BlurredFont.of(trimmed), statusX, GAP + 9,
                bridge.isChatConnected() ? Theme.TEXT_DIM : Theme.WARNING);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Background and panels come from extractBackground, which the framework
        // calls for us — see the note there. Widgets are drawn by super.
        super.extractRenderState(ctx, mouseX, mouseY, delta);

        LauncherBridge bridge = LauncherBridge.get();

        // Presence dots and request labels sit ON TOP of the row widgets, so
        // they have to be drawn after super has rendered those.
        for (Slot slot : slots) {
            if (slot.request()) {
                Friend f = slot.friend();
                String label = this.font.plainSubstrByWidth(f.nick(), SIDEBAR_W - 58);
                ctx.text(this.font, BlurredFont.of(label),
                        GAP + 8, slot.y() + 5, f.isPendingIn() ? Theme.TEXT : Theme.TEXT_FAINT);
            } else {
                ctx.fill(GAP + 8, slot.y() + 7, GAP + 12, slot.y() + 11,
                        slot.friend().online() ? Theme.ONLINE : Theme.OFFLINE);
            }
        }

        // The porthole marks who in the server list is also on Blurred — i.e.
        // who you get presence and cross-server chat with, rather than only a
        // whisper that stops working when one of you leaves.
        for (PlayerSlot slot : playerSlots) {
            if (slot.blurred()) {
                HudRenderer.porthole(ctx, GAP + 10, slot.y() + 9, 3);
            }
        }

        int chatX = SIDEBAR_W + GAP * 2;
        renderTranscript(ctx, bridge, chatX, this.width - chatX - GAP);

        // Added with addSelectableChild, not addDrawableChild, so they aren't
        // in the list super just rendered.
        this.input.extractRenderState(ctx, mouseX, mouseY, delta);
        if (this.nickField != null) {
            this.nickField.extractRenderState(ctx, mouseX, mouseY, delta);
        }
    }

    /**
     * Draw the transcript bottom-up from the newest line, so the most recent
     * message is always visible and older ones fall off the top — the same rule
     * the launcher's transcript follows.
     */
    private void renderTranscript(GuiGraphicsExtractor ctx, LauncherBridge bridge, int chatX, int chatW) {
        if (targetIsPlayer) {
            renderWhisperThread(ctx, chatX, chatW);
            return;
        }

        List<ChatLine> all = bridge.messages();
        int lineH = this.font.lineHeight + 2;
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
                    : m.mine() ? Theme.accent() : Theme.TEXT;

            // Wrap long lines rather than clipping them.
            List<net.minecraft.util.FormattedCharSequence> wrapped =
                    this.font.split(BlurredFont.of(text), chatW - 12);
            for (int w = wrapped.size() - 1; w >= 0 && y >= top; w--) {
                ctx.text(this.font, wrapped.get(w), chatX + 6, y, color);
                y -= lineH;
            }
            shown++;
        }

        if (shown == 0) {
            ctx.text(this.font,
                    BlurredFont.of("Nothing here yet."), chatX + 6, top, Theme.TEXT_FAINT);
        }
    }

    /**
     * The outgoing half of a whisper conversation, plus a note saying so.
     *
     * <p>Only our own lines appear, and the panel says as much rather than
     * looking like a chat window that's mysteriously one-sided. Their replies
     * land in the game's chat box — see {@link Whispers} for why capturing them
     * would produce a transcript with holes in it, which is the one thing worse
     * than sending you to the chat box.
     */
    private void renderWhisperThread(GuiGraphicsExtractor ctx, int chatX, int chatW) {
        int lineH = this.font.lineHeight + 2;
        int top = BODY_Y + 6;
        int y = this.height - 40 - lineH;

        // The footnote is pinned to the bottom, under the messages, because
        // that's where the eye lands after reading what it just sent.
        String hint = BlurredUsers.isBlurredUser(target)
                ? target + " is on Blurred — add them as crew for chat that follows you both."
                : "Sent through the server as /msg. " + target + "'s reply arrives in your chat box.";
        for (var line : this.font.split(BlurredFont.of(hint), chatW - 12)) {
            ctx.text(this.font, line, chatX + 6, y, Theme.TEXT_FAINT);
            y -= lineH;
        }
        y -= 4;

        List<String> sent = Whispers.sentTo(target);
        if (sent.isEmpty()) {
            ctx.text(this.font,
                    BlurredFont.of("Nothing sent to " + target + " yet."),
                    chatX + 6, top, Theme.TEXT_FAINT);
            return;
        }

        for (int i = sent.size() - 1; i >= 0 && y >= top; i--) {
            List<net.minecraft.util.FormattedCharSequence> wrapped =
                    this.font.split(BlurredFont.of("→ " + sent.get(i)), chatW - 12);
            for (int w = wrapped.size() - 1; w >= 0 && y >= top; w--) {
                ctx.text(this.font, wrapped.get(w), chatX + 6, y, Theme.ACCENT);
                y -= lineH;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        // Don't pause singleplayer: this is a chat overlay, and pausing to read
        // a message would be worse than the message.
        return false;
    }
}
