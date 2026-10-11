import { useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import { useChatStore } from "../store/chatStore";
import { useAccountStore } from "../store/accountStore";
import { Icon } from "../components/Icon";
import { navLabel } from "../lib/nav";
import { currentServer } from "../lib/tauri";
import type { ChatMessage, Friend } from "../types/chat";

/**
 * Sonar — the chat screen.
 *
 * Three columns in the shape people already know from every chat client:
 * conversations + friends on the left, the transcript in the middle, the
 * channel roster on the right (channels only). The backend is IRC (see
 * commands/chat.rs), and the UI is upfront about what that means rather than
 * pretending to be a proprietary friends system: nicks aren't owned, and
 * "crew" is a personal bookmark list, not a mutual friendship.
 */
export function Chat() {
  const {
    connected,
    connecting,
    nick,
    error,
    conversations,
    activeKey,
    wire,
    connect,
    disconnect,
    send,
    openConversation,
    closeConversation,
    joinChannel,
    refreshFriends,
    addFriend,
    acceptFriend,
    declineFriend,
    removeFriend,
    crew,
    incoming,
    outgoing,
  } = useChatStore();

  const accounts = useAccountStore((s) => s.accounts);
  const [draft, setDraft] = useState("");
  const [showAddFriend, setShowAddFriend] = useState(false);
  const [showJoin, setShowJoin] = useState(false);

  const activeAccount = useMemo(
    () =>
      accounts
        .slice()
        .sort((a, b) => (b.lastUsed ?? "").localeCompare(a.lastUsed ?? ""))[0],
    [accounts]
  );

  useEffect(() => {
    wire();
    refreshFriends();
  }, [wire, refreshFriends]);

  // Where you're playing, for the invite controls below. Polled rather than
  // pushed: it changes when the game joins a server or a world is opened to
  // LAN, neither of which the launcher is told about directly — the mod reports
  // it over the bridge and the launcher just holds the latest value.
  const [world, setWorld] = useState<string | null>(null);
  useEffect(() => {
    let stopped = false;
    const read = () =>
      currentServer()
        .then((s) => !stopped && setWorld(s && s.trim() ? s : null))
        .catch(() => {});
    read();
    const timer = window.setInterval(read, 5000);
    return () => {
      stopped = true;
      window.clearInterval(timer);
    };
  }, []);

  /**
   * Send someone the address of the world you're in.
   *
   * A plain DM, not a new protocol. It lands in their Sonar transcript and — if
   * they're in the game — in the crew panel's, where the address is already
   * clickable because the mod offers a join for any crew member's server. The
   * value here is the nudge: presence tells them you're *somewhere*, an invite
   * tells them you want them there.
   */
  function invite(nick: string) {
    if (!world) return;
    send(nick, `Come and play — join me at ${world}`).catch(() => {});
  }

  function inviteAllOnline() {
    if (!world) return;
    for (const f of crew()) {
      if (f.online) invite(f.nick);
    }
  }

  const active = activeKey ? conversations[activeKey] : undefined;
  const channels = Object.values(conversations).filter((c) => c.isChannel);
  const dms = Object.values(conversations).filter((c) => !c.isChannel);

  function submit() {
    const text = draft.trim();
    if (!text || !active) return;
    send(active.name, text).catch(() => {});
    setDraft("");
  }

  return (
    <div style={{ display: "flex", height: "100%", overflow: "hidden" }}>
      {/* ---- Left: conversations + crew ---- */}
      <aside
        style={{
          width: 232,
          flexShrink: 0,
          display: "flex",
          flexDirection: "column",
          borderRight: "1px solid var(--glass-border)",
          padding: "16px 10px",
          gap: 4,
          overflowY: "auto",
        }}
      >
        <ConnectionPill
          connected={connected}
          connecting={connecting}
          nick={nick}
          onConnect={() => activeAccount && connect(activeAccount.username)}
          onDisconnect={disconnect}
          canConnect={!!activeAccount}
        />

        {error && (
          <div
            style={{
              fontSize: 11,
              color: "var(--danger)",
              padding: "6px 10px",
              lineHeight: 1.4,
            }}
          >
            {error}
          </div>
        )}

        <SectionHeader label="Channels" actionLabel="Join a channel" onAction={() => setShowJoin(true)} />
        {channels.length === 0 && <Empty text="No channels open." />}
        {channels.map((c) => (
          <ConversationRow
            key={c.name}
            label={c.name}
            unread={c.unread}
            active={activeKey === c.name.toLowerCase()}
            onClick={() => openConversation(c.name)}
            onClose={() => closeConversation(c.name)}
          />
        ))}

        <SectionHeader label="Direct" />
        {dms.length === 0 && <Empty text="No direct messages." />}
        {dms.map((c) => (
          <ConversationRow
            key={c.name}
            label={c.name}
            unread={c.unread}
            active={activeKey === c.name.toLowerCase()}
            onClick={() => openConversation(c.name)}
            onClose={() => closeConversation(c.name)}
          />
        ))}

        {/* Incoming requests sit above the crew — they need an answer. */}
        {incoming().length > 0 && (
          <>
            <SectionHeader label={`Requests (${incoming().length})`} />
            {incoming().map((f) => (
              <div
                key={f.nick}
                style={{
                  padding: "9px 10px",
                  marginBottom: 6,
                  borderRadius: "var(--radius-sm)",
                  border: "1px solid var(--accent)",
                  background: "rgba(53,224,208,0.08)",
                }}
              >
                <div style={{ fontSize: 13, fontWeight: 600 }}>{f.nick}</div>
                {f.note && f.note !== "-" && (
                  <div
                    style={{
                      fontSize: 11,
                      color: "var(--text-secondary)",
                      lineHeight: 1.45,
                      margin: "3px 0 2px",
                    }}
                  >
                    “{f.note}”
                  </div>
                )}
                <div style={{ fontSize: 10, color: "var(--text-tertiary)", marginBottom: 7 }}>
                  wants to join your crew
                </div>
                <div style={{ display: "flex", gap: 6 }}>
                  <button
                    className="accent"
                    onClick={() => acceptFriend(f.nick).catch(() => {})}
                    style={{ flex: 1, fontSize: 11, padding: "4px 8px" }}
                  >
                    Accept
                  </button>
                  <button
                    onClick={() => declineFriend(f.nick).catch(() => {})}
                    style={{ flex: 1, fontSize: 11, padding: "4px 8px" }}
                  >
                    Decline
                  </button>
                </div>
              </div>
            ))}
          </>
        )}

        <SectionHeader label="Crew" actionLabel="Send a friend request" onAction={() => setShowAddFriend(true)} />

        {/* Inviting people to your world belongs here, next to the people. It
            used to live nowhere: the mod shared your address with the crew
            automatically, so a friend could see you were somewhere and join —
            but there was no way to actually *ask* anyone. */}
        <WorldInvite world={world} onInviteAll={inviteAllOnline} anyOnline={crew().some((f) => f.online)} />

        {crew().length === 0 && <Empty text="Nobody on your crew yet." />}
        {crew().map((f) => (
          <CrewRow
            key={f.nick}
            friend={f}
            world={world}
            onOpen={() => openConversation(f.nick)}
            onInvite={() => invite(f.nick)}
            onRemove={() => {
              if (confirm(`Remove ${f.nick} from your crew?`)) {
                removeFriend(f.nick).catch(() => {});
              }
            }}
          />
        ))}

        {/* Sent requests. Worth showing because there's a real chance one is
            never answered — the other side has to be running Blurred Client. */}
        {outgoing().length > 0 && (
          <>
            <SectionHeader label="Sent" />
            {outgoing().map((f) => (
              <div key={f.nick} style={{ display: "flex", alignItems: "center" }}>
                <div className="chat-entry" style={{ cursor: "default", opacity: 0.7 }}>
                  <span className="presence" />
                  <span style={{ flex: 1, overflow: "hidden", textOverflow: "ellipsis" }}>
                    {f.nick}
                  </span>
                  <span style={{ fontSize: 10, color: "var(--text-tertiary)" }}>waiting</span>
                </div>
                <button
                  className="bare"
                  onClick={() => removeFriend(f.nick).catch(() => {})}
                  title="Cancel request"
                  style={{
                    background: "transparent",
                    border: "none",
                    color: "var(--text-tertiary)",
                    cursor: "pointer",
                    padding: 4,
                    display: "flex",
                  }}
                >
                  <Icon name="close" size={13} />
                </button>
              </div>
            ))}
          </>
        )}
      </aside>

      {/* ---- Middle: transcript ---- */}
      <section style={{ flex: 1, display: "flex", flexDirection: "column", minWidth: 0 }}>
        <header
          style={{
            display: "flex",
            alignItems: "center",
            gap: 10,
            padding: "14px 18px",
            borderBottom: "1px solid var(--glass-border)",
          }}
        >
          <Icon name={active?.isChannel ? "sonar" : "bubble"} size={17} />
          <span style={{ fontSize: 14, fontFamily: "var(--font-pixel)" }}>
            {active?.name ?? navLabel("chat")}
          </span>
          {active?.isChannel && active.users.length > 0 && (
            <span style={{ fontSize: 12, color: "var(--text-tertiary)" }}>
              {active.users.length} aboard
            </span>
          )}
        </header>

        {active ? (
          <Transcript conversation={active.name} messages={active.messages} />
        ) : (
          <div
            style={{
              flex: 1,
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              gap: 8,
              color: "var(--text-tertiary)",
              padding: 32,
              textAlign: "center",
            }}
          >
            <Icon name="sonar" size={40} />
            <p style={{ margin: 0, fontSize: 14 }}>
              {connected ? "Pick a channel to start listening." : "Not connected."}
            </p>
            <p style={{ margin: 0, fontSize: 12, maxWidth: 380, lineHeight: 1.5 }}>
              Sonar runs on IRC, so there's no account to make — you chat under
              your Minecraft name. Nicks are first-come, not reserved.
            </p>
          </div>
        )}

        {/* Composer */}
        <div style={{ padding: 14, borderTop: "1px solid var(--glass-border)" }}>
          <div style={{ display: "flex", gap: 8 }}>
            <input
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault();
                  submit();
                }
              }}
              disabled={!connected || !active}
              placeholder={
                !connected
                  ? "Connect to send a message"
                  : active
                    ? `Message ${active.name}`
                    : "Pick a conversation"
              }
              style={{
                flex: 1,
                padding: "10px 14px",
                borderRadius: 0,
                border: "1px solid var(--glass-border)",
                background: "rgba(0,20,30,0.3)",
                color: "var(--text-primary)",
                fontSize: 13.5,
              }}
            />
            <button
              className="accent"
              onClick={submit}
              disabled={!connected || !active || !draft.trim()}
              style={{
                borderRadius: 0,
                width: 42,
                padding: 0,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
              aria-label="Send"
            >
              <Icon name="send" size={17} />
            </button>
          </div>
        </div>
      </section>

      {/* ---- Right: channel roster ---- */}
      {active?.isChannel && active.users.length > 0 && (
        <aside
          style={{
            width: 170,
            flexShrink: 0,
            borderLeft: "1px solid var(--glass-border)",
            padding: "16px 10px",
            overflowY: "auto",
          }}
        >
          <SectionHeader label={`Aboard (${active.users.length})`} />
          {active.users.map((u) => (
            <button
              key={u}
              className="chat-entry"
              onClick={() => openConversation(u)}
              title={`Message ${u}`}
            >
              <span
                className="presence online"
                style={{ opacity: u.toLowerCase() === nick.toLowerCase() ? 1 : 0.55 }}
              />
              <span style={{ overflow: "hidden", textOverflow: "ellipsis" }}>{u}</span>
            </button>
          ))}
        </aside>
      )}

      {showAddFriend && (
        <PromptModal
          title="Send a friend request"
          label="Nick"
          hint="Their chat nick — usually their Minecraft name. They have to accept, and they need to be running Blurred Client and be online to receive it."
          confirmLabel="Send request"
          onCancel={() => setShowAddFriend(false)}
          onConfirm={async (value, note) => {
            await addFriend(value, note ?? "");
            setShowAddFriend(false);
          }}
          noteLabel="Say hello (optional)"
          notePlaceholder={`e.g. it's ${nick || "you"} from the SMP`}
          withNote
        />
      )}

      {showJoin && (
        <PromptModal
          title="Join a channel"
          label="Channel"
          hint="For example #blurred-client. The # is added if you leave it off."
          confirmLabel="Join"
          onCancel={() => setShowJoin(false)}
          onConfirm={async (value) => {
            await joinChannel(value);
            setShowJoin(false);
          }}
        />
      )}
    </div>
  );
}

/** The message list. Auto-sticks to the bottom unless the user has scrolled up. */
function Transcript({ conversation, messages }: { conversation: string; messages: ChatMessage[] }) {
  const ref = useRef<HTMLDivElement>(null);
  const stick = useRef(true);

  // Track whether the view is pinned to the bottom. Reading this on scroll (not
  // on render) is what stops a new message from yanking someone out of the
  // backlog they're reading.
  function onScroll() {
    const el = ref.current;
    if (!el) return;
    stick.current = el.scrollHeight - el.scrollTop - el.clientHeight < 60;
  }

  useEffect(() => {
    if (stick.current && ref.current) {
      ref.current.scrollTop = ref.current.scrollHeight;
    }
  }, [messages]);

  // Switching conversations always lands at the newest message.
  useEffect(() => {
    stick.current = true;
    if (ref.current) ref.current.scrollTop = ref.current.scrollHeight;
  }, [conversation]);

  return (
    <div ref={ref} onScroll={onScroll} style={{ flex: 1, overflowY: "auto", padding: "12px 0" }}>
      {messages.length === 0 && (
        <p style={{ color: "var(--text-tertiary)", fontSize: 13, padding: "0 18px" }}>
          Nothing here yet. Say something.
        </p>
      )}
      {messages.map((m, i) => {
        // Collapse the author column for consecutive messages from the same
        // person, the way every readable chat client does.
        const prev = messages[i - 1];
        const grouped =
          prev && prev.from === m.from && prev.kind === m.kind && m.kind === "message";

        if (m.kind === "system") {
          return (
            <div key={i} className="chat-row system">
              <span className="chat-author" />
              <span className="chat-body">{m.text}</span>
            </div>
          );
        }

        if (m.kind === "action") {
          return (
            <div key={i} className="chat-row system">
              <span className="chat-author" />
              <span className="chat-body">
                {m.from} {m.text}
              </span>
            </div>
          );
        }

        return (
          <div key={i} className={`chat-row ${m.mine ? "self" : ""}`}>
            <span
              className="chat-author"
              style={{
                color: m.mine ? "var(--accent)" : nickColor(m.from),
                visibility: grouped ? "hidden" : "visible",
              }}
            >
              {m.from}
            </span>
            <span className="chat-body" style={m.kind === "notice" ? { opacity: 0.75 } : undefined}>
              {m.text}
            </span>
            <span className="chat-time">
              {new Date(m.ts).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
            </span>
          </div>
        );
      })}
    </div>
  );
}

// Stable per-nick color so people are visually distinguishable in a busy
// channel. Constrained to the cyan/teal/green band so it reads as part of the
// ocean palette instead of a random rainbow.
function nickColor(nickName: string): string {
  let h = 0;
  for (let i = 0; i < nickName.length; i++) h = (h * 31 + nickName.charCodeAt(i)) | 0;
  return `hsl(${150 + (Math.abs(h) % 110)} 60% 68%)`;
}

function ConnectionPill({
  connected,
  connecting,
  nick,
  onConnect,
  onDisconnect,
  canConnect,
}: {
  connected: boolean;
  connecting: boolean;
  nick: string;
  onConnect: () => void;
  onDisconnect: () => void;
  canConnect: boolean;
}) {
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: 8,
        padding: "8px 10px",
        marginBottom: 8,
        borderRadius: "var(--radius-md)",
        background: "var(--glass-bg-elevated)",
        border: "1px solid var(--glass-border)",
      }}
    >
      <Icon name={connected ? "wifi" : "wifiOff"} size={15} style={{ color: connected ? "var(--success)" : "var(--text-tertiary)" }} />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 12, fontWeight: 600, overflow: "hidden", textOverflow: "ellipsis" }}>
          {connected ? nick : connecting ? "Diving…" : "Offline"}
        </div>
        <div style={{ fontSize: 10, color: "var(--text-tertiary)" }}>
          {connected ? "Connected" : canConnect ? "Not connected" : "No account"}
        </div>
      </div>
      <button
        className="bare"
        onClick={connected ? onDisconnect : onConnect}
        disabled={!canConnect || connecting}
        style={{
          background: "transparent",
          border: "1px solid var(--glass-border)",
          borderRadius: "var(--radius-sm)",
          color: "var(--text-secondary)",
          fontSize: 11,
          padding: "4px 8px",
          cursor: canConnect && !connecting ? "pointer" : "not-allowed",
          opacity: canConnect && !connecting ? 1 : 0.5,
        }}
      >
        {connected ? "Leave" : "Dive"}
      </button>
    </div>
  );
}

function SectionHeader({
  label,
  actionLabel,
  onAction,
}: {
  label: string;
  actionLabel?: string;
  onAction?: () => void;
}) {
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "12px 10px 4px",
        fontSize: 10.5,
        fontWeight: 700,
        letterSpacing: "0.08em",
        textTransform: "uppercase",
        color: "var(--text-tertiary)",
      }}
    >
      <span>{label}</span>
      {onAction && (
        <button
          className="bare"
          onClick={onAction}
          title={actionLabel}
          aria-label={actionLabel}
          style={{
            background: "transparent",
            border: "none",
            color: "var(--text-tertiary)",
            cursor: "pointer",
            padding: 0,
            display: "flex",
          }}
        >
          <Icon name="plus" size={14} />
        </button>
      )}
    </div>
  );
}

/**
 * The strip above the crew list: where you're playing, and one button to ask
 * everyone over.
 *
 * Present in all three states rather than appearing only when there's something
 * to invite people to — the states are the explanation. "Open your world to
 * LAN" is the instruction someone needs at exactly the moment they went looking
 * for an invite button and expected it to work.
 */
function WorldInvite({
  world,
  anyOnline,
  onInviteAll,
}: {
  world: string | null;
  anyOnline: boolean;
  onInviteAll: () => void;
}) {
  return (
    <div
      style={{
        margin: "2px 10px 8px",
        padding: "8px 10px",
        border: `1px solid ${world ? "var(--accent)" : "var(--glass-border)"}`,
        background: world ? "rgba(53,224,208,0.07)" : "transparent",
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 4 }}>
        <Icon name="globe" size={12} />
        <span style={{ fontSize: 10.5, fontWeight: 700, letterSpacing: "0.06em", textTransform: "uppercase", color: "var(--text-tertiary)" }}>
          Your world
        </span>
      </div>

      {world ? (
        <>
          <div
            style={{
              fontSize: 11,
              fontFamily: "var(--font-mono)",
              color: "var(--text-secondary)",
              wordBreak: "break-all",
              marginBottom: 7,
            }}
          >
            {world}
          </div>
          <button
            className="accent"
            onClick={onInviteAll}
            disabled={!anyOnline}
            title={
              anyOnline
                ? "Send everyone online the address"
                : "Nobody on your crew is online right now"
            }
            style={{ width: "100%", fontSize: 11, padding: "5px 8px" }}
          >
            Invite everyone online
          </button>
        </>
      ) : (
        <div style={{ fontSize: 10.5, color: "var(--text-tertiary)", lineHeight: 1.55 }}>
          Start a world and use Open to LAN — or join a server — and the address
          shows here for you to invite crew to.
        </div>
      )}
    </div>
  );
}

/**
 * One crew member: presence, where they're playing, and the two things you'd
 * want to do about it.
 *
 * The invite button is disabled rather than hidden when there's nowhere to
 * invite them to. A control that vanishes teaches nothing; one that's greyed
 * out with a reason on hover teaches what to do about it.
 */
function CrewRow({
  friend,
  world,
  onOpen,
  onInvite,
  onRemove,
}: {
  friend: Friend;
  world: string | null;
  onOpen: () => void;
  onInvite: () => void;
  onRemove: () => void;
}) {
  const canInvite = !!world && friend.online;

  return (
    <div style={{ display: "flex", alignItems: "center" }}>
      <button className="chat-entry" onClick={onOpen} title={friend.note || undefined}>
        <span className={`presence ${friend.online ? "online" : ""}`} />
        <span style={{ flex: 1, minWidth: 0, overflow: "hidden" }}>
          <span style={{ display: "block", overflow: "hidden", textOverflow: "ellipsis" }}>
            {friend.nick}
          </span>
          {friend.server && (
            <span
              style={{
                display: "block",
                fontSize: 9.5,
                color: "var(--text-tertiary)",
                overflow: "hidden",
                textOverflow: "ellipsis",
              }}
            >
              on {friend.server}
            </span>
          )}
        </span>
        {!friend.server && (
          <span style={{ fontSize: 10, color: "var(--text-tertiary)" }}>
            {friend.online ? "up" : "down"}
          </span>
        )}
      </button>

      <button
        className="bare"
        onClick={onInvite}
        disabled={!canInvite}
        title={
          !world
            ? "You're not in a world others can join — open one to LAN first"
            : !friend.online
              ? `${friend.nick} is offline`
              : `Invite ${friend.nick} to ${world}`
        }
        aria-label={`Invite ${friend.nick} to your world`}
        style={{
          background: "transparent",
          border: "none",
          color: canInvite ? "var(--accent)" : "var(--text-tertiary)",
          cursor: canInvite ? "pointer" : "default",
          opacity: canInvite ? 1 : 0.4,
          padding: 4,
          display: "flex",
        }}
      >
        <Icon name="send" size={13} />
      </button>

      <button
        className="bare"
        onClick={onRemove}
        title={`Remove ${friend.nick} from your crew`}
        style={{
          background: "transparent",
          border: "none",
          color: "var(--text-tertiary)",
          cursor: "pointer",
          padding: 4,
          display: "flex",
        }}
      >
        <Icon name="close" size={13} />
      </button>
    </div>
  );
}

function Empty({ text }: { text: string }) {
  return (
    <div style={{ fontSize: 11.5, color: "var(--text-tertiary)", padding: "2px 10px 6px" }}>
      {text}
    </div>
  );
}

function ConversationRow({
  label,
  unread,
  active,
  onClick,
  onClose,
}: {
  label: string;
  unread: number;
  active: boolean;
  onClick: () => void;
  onClose: () => void;
}) {
  return (
    <div style={{ display: "flex", alignItems: "center" }}>
      <button className={`chat-entry ${active ? "active" : ""}`} onClick={onClick}>
        <span style={{ flex: 1, overflow: "hidden", textOverflow: "ellipsis" }}>{label}</span>
        {unread > 0 && (
          <span
            style={{
              background: "var(--accent)",
              color: "var(--accent-fg)",
              borderRadius: 0,
              fontSize: 10,
              fontWeight: 700,
              padding: "1px 6px",
            }}
          >
            {unread > 99 ? "99+" : unread}
          </span>
        )}
      </button>
      <button
        className="bare"
        onClick={onClose}
        title={`Close ${label}`}
        style={{
          background: "transparent",
          border: "none",
          color: "var(--text-tertiary)",
          cursor: "pointer",
          padding: 4,
          display: "flex",
        }}
      >
        <Icon name="close" size={13} />
      </button>
    </div>
  );
}

function PromptModal({
  title,
  label,
  hint,
  confirmLabel,
  withNote,
  noteLabel,
  notePlaceholder,
  onCancel,
  onConfirm,
}: {
  title: string;
  label: string;
  hint: string;
  confirmLabel: string;
  withNote?: boolean;
  noteLabel?: string;
  notePlaceholder?: string;
  onCancel: () => void;
  onConfirm: (value: string, note?: string) => Promise<void>;
}) {
  const [value, setValue] = useState("");
  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function go() {
    if (!value.trim()) return;
    setBusy(true);
    setError(null);
    try {
      await onConfirm(value.trim(), note.trim());
    } catch (e) {
      setError(String(e));
      setBusy(false);
    }
  }

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(0,10,16,0.6)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 200,
      }}
      onClick={onCancel}
    >
      <div className="glass-card" style={{ width: 360, padding: 20 }} onClick={(e) => e.stopPropagation()}>
        <h2 style={{ margin: "0 0 4px", fontSize: 16 }}>{title}</h2>
        <p style={{ margin: "0 0 14px", fontSize: 12, color: "var(--text-tertiary)", lineHeight: 1.5 }}>
          {hint}
        </p>

        <label style={{ fontSize: 12, color: "var(--text-secondary)" }}>{label}</label>
        <input
          autoFocus
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && go()}
          style={modalInput}
        />

        {withNote && (
          <>
            <label style={{ fontSize: 12, color: "var(--text-secondary)" }}>
              {noteLabel ?? "Note (optional)"}
            </label>
            <input
              value={note}
              onChange={(e) => setNote(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && go()}
              placeholder={notePlaceholder ?? "e.g. from the SMP"}
              style={modalInput}
            />
          </>
        )}

        {error && <div style={{ color: "var(--danger)", fontSize: 12, marginBottom: 8 }}>{error}</div>}

        <div style={{ display: "flex", gap: 8, marginTop: 6 }}>
          <button onClick={onCancel} style={{ flex: 1 }} disabled={busy}>
            Cancel
          </button>
          <button className="accent" style={{ flex: 1 }} onClick={go} disabled={!value.trim() || busy}>
            {busy ? "…" : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

const modalInput: CSSProperties = {
  width: "100%",
  marginBottom: 12,
  marginTop: 4,
  padding: "8px 10px",
  borderRadius: 0,
  border: "1px solid var(--glass-border)",
  background: "rgba(0,20,30,0.3)",
  color: "var(--text-primary)",
  fontSize: 13,
};
