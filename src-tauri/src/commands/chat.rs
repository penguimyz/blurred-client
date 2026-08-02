//! Sonar — the in-launcher chat.
//!
//! # Why IRC
//!
//! A chat needs a server. Rather than make the user host one (which would mean
//! the feature ships dead), Sonar speaks IRC over TLS to a public network —
//! Libera.Chat by default. That buys a real, working chat on first launch with
//! zero infrastructure: real presence, real DMs, real channels, no account
//! system of our own to run, and nothing that can be turned into an ad surface.
//!
//! The tradeoff is IRC's model, and the UI is honest about it: nicks are
//! first-come-first-served rather than owned, and "friends" is a one-sided
//! bookmark list (see `models::chat::Friend`) because IRC has no friend
//! request. Presence is genuine, though — it comes from the server's MONITOR
//! list, not from polling.
//!
//! # Shape
//!
//! One background task owns the socket. It splits the TLS stream in two:
//!   - a writer task draining an mpsc channel, so any command can send a line
//!     without holding a lock on the socket;
//!   - a reader loop parsing lines and emitting Tauri events to the frontend.
//!
//! Nothing is buffered on the Rust side: every line becomes an event the moment
//! it arrives, and the frontend store is the only place messages accumulate.
//! That keeps scrollback out of the backend entirely (and off disk — chat
//! history is never persisted, only the friends list is).
//!
//! `generation` is the guard against a stale socket. Every connect bumps it; a
//! reader loop whose generation no longer matches the state exits quietly
//! without touching shared state, so a fast disconnect/reconnect can't have the
//! old task overwrite the new one's status.

use std::collections::HashSet;
use std::path::{Path, PathBuf};

use serde::Serialize;
use tauri::{AppHandle, Emitter, State};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::TcpStream;
use tokio::sync::mpsc::{self, UnboundedSender};

use crate::models::chat::{Friend, FriendStatus};
use crate::state::AppState;

const DEFAULT_SERVER: &str = "irc.libera.chat";
const DEFAULT_PORT: u16 = 6697;
/// The lobby every client joins by default. `#blurred-client` on Libera.
const DEFAULT_CHANNEL: &str = "#blurred-client";

/// CTCP verb carrying the friend-request handshake between two Blurred Clients.
///
/// CTCP (a payload wrapped in \x01 inside a normal PRIVMSG) is the right
/// transport for this: it's the standard way to send client-to-client data over
/// IRC, every server relays it untouched, and other IRC clients ignore verbs
/// they don't recognise instead of showing the user line noise.
///
/// Subcommands: `REQ <greeting>`, `ACCEPT`, `DECLINE`.
const CTCP_FRIEND: &str = "BLURREDFRIEND";

/// CTCP verb carrying cape announcements and transfers. See
/// `commands::capes` for the protocol.
const CTCP_CAPE: &str = "BLURREDCAPE";

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

#[derive(Default)]
pub struct ChatState {
    /// Outbound line sink. `None` whenever we're not connected.
    pub tx: Option<UnboundedSender<String>>,
    pub connected: bool,
    /// The nick the server actually gave us, which may differ from the one we
    /// asked for if it was taken (see the 433 handler).
    pub nick: String,
    pub channels: Vec<String>,
    pub generation: u64,
}

impl ChatState {
    /// Queue a raw IRC line. Silently drops when disconnected — every caller
    /// here is best-effort, and a dropped line while offline is not an error
    /// worth surfacing.
    ///
    /// `pub(crate)` because `commands::capes` builds its own CTCP traffic and
    /// needs the same outbound path; it still goes through `ctcp_line_public`
    /// for the wire format, so there's one encoder rather than two.
    pub(crate) fn send_raw(&self, line: String) {
        if let Some(tx) = &self.tx {
            let _ = tx.send(line);
        }
    }
}

// ---------------------------------------------------------------------------
// Events
// ---------------------------------------------------------------------------

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct ChatMessageEvent {
    /// Channel name for channel traffic, or the other party's nick for a DM.
    /// `"*"` for server-wide notices that belong to no conversation.
    conversation: String,
    from: String,
    text: String,
    /// "message" | "action" | "notice" | "system"
    kind: String,
    ts: String,
    /// True when we sent it, so the UI can style it without knowing our nick.
    mine: bool,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct ChatStatusEvent {
    connected: bool,
    nick: String,
    /// Present when the connection dropped or failed to come up.
    error: Option<String>,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct ChatPresenceEvent {
    nick: String,
    online: bool,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct ChatNamesEvent {
    channel: String,
    users: Vec<String>,
}

/// Fired whenever the friends list changes as a result of an *incoming*
/// message, so the UI can re-read it without polling.
#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct FriendsChangedEvent {
    friends: Vec<Friend>,
    /// What just happened, for a toast: "request" | "accepted" | "declined".
    kind: String,
    nick: String,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ChatStatus {
    pub connected: bool,
    pub nick: String,
    pub channels: Vec<String>,
    pub server: String,
    pub default_channel: String,
}

fn now() -> String {
    chrono::Utc::now().to_rfc3339()
}

/// Emit to the launcher UI *and* mirror to any in-game mod client. Every chat
/// line goes through here, so the two views can't drift apart.
fn emit_message(app: &AppHandle, ev: ChatMessageEvent) {
    crate::commands::bridge::push(
        app,
        serde_json::json!({
            "t": "message",
            "conversation": ev.conversation,
            "from": ev.from,
            "text": ev.text,
            "kind": ev.kind,
            "mine": ev.mine,
        }),
    );
    let _ = app.emit("chat-message", ev);
}

/// Same idea for the roster: push it to the game whenever it changes.
fn push_friends_to_bridge(app: &AppHandle, friends: &[Friend]) {
    crate::commands::bridge::push(
        app,
        serde_json::json!({ "t": "friends", "friends": friends }),
    );
}

fn emit_system(app: &AppHandle, conversation: &str, text: impl Into<String>) {
    emit_message(
        app,
        ChatMessageEvent {
            conversation: conversation.to_string(),
            from: "*".to_string(),
            text: text.into(),
            kind: "system".to_string(),
            ts: now(),
            mine: false,
        },
    );
}

// ---------------------------------------------------------------------------
// IRC line parsing
// ---------------------------------------------------------------------------

/// A parsed IRC protocol line: `[@tags] [:prefix] COMMAND [params...] [:trailing]`.
struct IrcLine {
    /// Sender prefix with the `!user@host` tail stripped — i.e. just the nick.
    prefix_nick: String,
    command: String,
    /// Middle params plus the trailing param as the final element.
    params: Vec<String>,
}

fn parse_line(raw: &str) -> Option<IrcLine> {
    let mut rest = raw.trim_end_matches(['\r', '\n']);
    if rest.is_empty() {
        return None;
    }

    // IRCv3 message tags. We request no capabilities, so nothing here is load
    // bearing, but a server may still send them — skip rather than choke.
    if let Some(stripped) = rest.strip_prefix('@') {
        let (_tags, after) = stripped.split_once(' ')?;
        rest = after.trim_start();
    }

    let mut prefix_nick = String::new();
    if let Some(stripped) = rest.strip_prefix(':') {
        let (prefix, after) = stripped.split_once(' ')?;
        // "nick!user@host" -> "nick"; a bare server name has neither separator
        // and passes through whole, which is what we want for server notices.
        prefix_nick = prefix
            .split(['!', '@'])
            .next()
            .unwrap_or(prefix)
            .to_string();
        rest = after.trim_start();
    }

    // Trailing param: everything after the first " :" is one value, spaces and
    // all. Split it off before tokenizing the middles.
    let (head, trailing) = match rest.find(" :") {
        Some(i) => (&rest[..i], Some(rest[i + 2..].to_string())),
        None => (rest, None),
    };

    let mut tokens = head.split_whitespace();
    let command = tokens.next()?.to_uppercase();
    let mut params: Vec<String> = tokens.map(|s| s.to_string()).collect();
    if let Some(t) = trailing {
        params.push(t);
    }

    Some(IrcLine {
        prefix_nick,
        command,
        params,
    })
}

/// Strip anything that could inject a second protocol command, plus the control
/// bytes IRC assigns meaning to. Applied to every user-supplied string that
/// reaches the wire — without this, a message containing a newline would let
/// the sender run arbitrary IRC commands as themselves.
fn sanitize(input: &str) -> String {
    input
        .chars()
        .filter(|c| !matches!(c, '\r' | '\n' | '\0'))
        .collect::<String>()
        .trim()
        .to_string()
}

/// Coerce a Minecraft username into a legal IRC nick.
///
/// MC names are `[A-Za-z0-9_]{3,16}`, which is almost a subset of IRC's nick
/// grammar — the one gap is that IRC forbids a leading digit, so those get a
/// `_` prefix. Anything else unexpected is dropped rather than substituted, and
/// an empty result falls back to a generic nick so we always send something
/// legal.
fn sanitize_nick(name: &str) -> String {
    let mut out: String = name
        .chars()
        .filter(|c| c.is_ascii_alphanumeric() || matches!(c, '_' | '-' | '[' | ']' | '\\' | '`' | '^' | '{' | '}'))
        .take(16)
        .collect();

    if out.chars().next().map(|c| c.is_ascii_digit()).unwrap_or(true) {
        out.insert(0, '_');
    }
    if out.trim_matches('_').is_empty() {
        return "diver".to_string();
    }
    out
}

/// CTCP ACTION (`/me`) arrives wrapped in \x01. Unwrap it, returning the inner
/// text and whether it was an action.
fn unwrap_ctcp(text: &str) -> (String, bool) {
    let trimmed = text.trim_matches('\u{1}');
    match trimmed.strip_prefix("ACTION ") {
        Some(inner) => (inner.to_string(), true),
        None => (text.trim_matches('\u{1}').to_string(), false),
    }
}

/// If `body` is a CTCP message for `verb`, return everything after the verb.
/// Returns `Some("")` for a bare verb with no arguments, and `None` when this
/// isn't that CTCP at all — so the caller can distinguish "no payload" from
/// "not for me".
fn extract_ctcp(body: &str, verb: &str) -> Option<String> {
    let inner = body.strip_prefix('\u{1}')?.trim_end_matches('\u{1}');
    let rest = inner.strip_prefix(verb)?;
    match rest.strip_prefix(' ') {
        Some(args) => Some(args.to_string()),
        // Guard against matching a longer verb that merely starts with ours.
        None if rest.is_empty() => Some(String::new()),
        None => None,
    }
}

/// `ctcp_line` for other modules (capes), which build their own CTCP traffic
/// but must not duplicate the wire format.
pub fn ctcp_line_public(target: &str, verb: &str, args: &str) -> String {
    ctcp_line(target, verb, args)
}

/// Wrap a payload as a CTCP PRIVMSG line aimed at `target`.
fn ctcp_line(target: &str, verb: &str, args: &str) -> String {
    if args.is_empty() {
        format!("PRIVMSG {target} :\u{1}{verb}\u{1}")
    } else {
        format!("PRIVMSG {target} :\u{1}{verb} {args}\u{1}")
    }
}

/// Route an inbound cape CTCP into `commands::capes`, then send whatever
/// replies it asked for and push any newly-received cape to the in-game mod.
fn handle_cape_ctcp(
    app: &AppHandle,
    from: &str,
    payload: &str,
    tx: &UnboundedSender<String>,
) {
    use tauri::Manager;

    let state = app.state::<AppState>();

    // Capes arrive here rather than being pushed from `capes.rs` directly so
    // that module never needs the socket — it returns lines and a callback.
    let mut pushes: Vec<(String, Option<String>)> = Vec::new();
    let replies = crate::commands::capes::handle_ctcp(&state, from, payload, &mut |user, data| {
        pushes.push((user, data));
    });

    for line in replies {
        let _ = tx.send(line);
    }
    for (username, data) in pushes {
        crate::commands::bridge::push(
            app,
            serde_json::json!({ "t": "cape", "username": username, "data": data }),
        );
    }
}

/// Apply an inbound friend-request CTCP to the saved list.
///
/// Runs on the read loop, so it does its own load/mutate/save rather than going
/// through the commands below. Every branch is idempotent: a duplicate REQ from
/// someone already accepted is ignored rather than knocking them back to
/// pending, which matters because a client with no reply path (or a user who
/// clicks twice) will resend.
fn handle_friend_ctcp(
    app: &AppHandle,
    from: &str,
    payload: &str,
    tx: &UnboundedSender<String>,
) {
    use tauri::Manager;

    let from = sanitize_nick(from);
    if from.is_empty() {
        return;
    }

    let state = app.state::<AppState>();
    let data_dir = state.data_dir.clone();
    let mut friends = load_friends(&data_dir);

    let (verb, args) = match payload.split_once(' ') {
        Some((v, a)) => (v.to_uppercase(), a.trim().to_string()),
        None => (payload.trim().to_uppercase(), String::new()),
    };

    let existing = friends
        .iter()
        .position(|f| f.nick.eq_ignore_ascii_case(&from));

    let kind = match verb.as_str() {
        "REQ" => {
            match existing {
                // Already mutual — nothing to do.
                Some(i) if friends[i].status == FriendStatus::Accepted => return,
                // They requested us while we had a request out to them. Both
                // sides want this, so settle it immediately and tell them.
                Some(i) if friends[i].status == FriendStatus::PendingOut => {
                    friends[i].status = FriendStatus::Accepted;
                    let _ = tx.send(ctcp_line(&from, CTCP_FRIEND, "ACCEPT"));
                    // One lock, one scope. Taking the mutex twice in an
                    // `if cond { body }` is a deadlock waiting to happen if the
                    // condition's guard ever outlives the condition.
                    {
                        let chat = state.chat.lock().unwrap();
                        if chat.connected {
                            chat.send_raw(format!("MONITOR + {from}"));
                        }
                    }
                    "accepted"
                }
                // Duplicate incoming request; refresh the greeting only.
                Some(i) => {
                    friends[i].note = sanitize(&args);
                    "request"
                }
                None => {
                    friends.push(Friend {
                        nick: from.clone(),
                        note: sanitize(&args),
                        added_at: now(),
                        online: true, // they just messaged us, so they're up
                        status: FriendStatus::PendingIn,
                    });
                    "request"
                }
            }
        }
        "ACCEPT" => match existing {
            Some(i) if friends[i].status == FriendStatus::PendingOut => {
                friends[i].status = FriendStatus::Accepted;
                friends[i].online = true;
                let chat = state.chat.lock().unwrap();
                if chat.connected {
                    chat.send_raw(format!("MONITOR + {from}"));
                }
                "accepted"
            }
            // An ACCEPT we never asked for is either a stale reply or a spoof.
            _ => return,
        },
        "DECLINE" => match existing {
            Some(i) if friends[i].status == FriendStatus::PendingOut => {
                friends.remove(i);
                "declined"
            }
            _ => return,
        },
        _ => return,
    };

    if save_friends(&data_dir, &friends).is_err() {
        return;
    }
    push_friends_to_bridge(app, &friends);
    let _ = app.emit(
        "friends-changed",
        FriendsChangedEvent { friends, kind: kind.to_string(), nick: from },
    );
}

// ---------------------------------------------------------------------------
// Friends persistence
// ---------------------------------------------------------------------------

fn friends_path(data_dir: &Path) -> PathBuf {
    data_dir.join("friends.json")
}

fn load_friends(data_dir: &Path) -> Vec<Friend> {
    let path = friends_path(data_dir);
    if !path.exists() {
        return Vec::new();
    }
    std::fs::read_to_string(&path)
        .ok()
        .and_then(|raw| serde_json::from_str(&raw).ok())
        .unwrap_or_default()
}

fn save_friends(data_dir: &Path, friends: &[Friend]) -> Result<(), String> {
    let json = serde_json::to_string_pretty(friends).map_err(|e| e.to_string())?;
    std::fs::write(friends_path(data_dir), json).map_err(|e| e.to_string())
}

/// `load_friends` for the mod bridge, which lives in a different module and
/// needs the roster to greet a newly-connected game client.
pub fn load_friends_public(data_dir: &Path) -> Vec<Friend> {
    load_friends(data_dir)
}

/// Send a chat message on behalf of the in-game mod.
///
/// Mirrors `chat_send`, but synchronous and taking an `&AppState` rather than a
/// `State<'_, _>` guard, because the bridge's connection task can't hold a Tauri
/// state guard across an await. Same sanitising and the same local echo, so a
/// message typed in-game shows up in the launcher's transcript too.
pub fn send_from_bridge(
    app: &AppHandle,
    state: &AppState,
    conversation: String,
    text: String,
) -> Result<(), String> {
    let target = sanitize(&conversation);
    let body = sanitize(&text);
    if target.is_empty() || body.is_empty() {
        return Ok(());
    }

    let nick = {
        let chat = state.chat.lock().unwrap();
        if !chat.connected {
            return Err("Not connected.".to_string());
        }
        chat.send_raw(format!("PRIVMSG {target} :{body}"));
        chat.nick.clone()
    };

    emit_message(
        app,
        ChatMessageEvent {
            conversation: target,
            from: nick,
            text: body,
            kind: "message".to_string(),
            ts: now(),
            mine: true,
        },
    );
    Ok(())
}

// ---------------------------------------------------------------------------
// Commands
// ---------------------------------------------------------------------------

#[tauri::command]
pub async fn chat_status(state: State<'_, AppState>) -> Result<ChatStatus, String> {
    let (server, channel) = {
        let s = state.settings.lock().unwrap();
        (s.chat_server.clone(), s.chat_channel.clone())
    };
    let chat = state.chat.lock().unwrap();
    Ok(ChatStatus {
        connected: chat.connected,
        nick: chat.nick.clone(),
        channels: chat.channels.clone(),
        server: if server.is_empty() { DEFAULT_SERVER.to_string() } else { server },
        default_channel: if channel.is_empty() { DEFAULT_CHANNEL.to_string() } else { channel },
    })
}

/// Open the connection. `nick` is normally the active account's Minecraft
/// username, passed in by the frontend so the backend doesn't have to duplicate
/// the "which account is active" rule.
#[tauri::command]
pub async fn chat_connect(
    app: AppHandle,
    state: State<'_, AppState>,
    nick: String,
) -> Result<(), String> {
    let (server, port, channel) = {
        let s = state.settings.lock().unwrap();
        (
            if s.chat_server.is_empty() { DEFAULT_SERVER.to_string() } else { s.chat_server.clone() },
            if s.chat_port == 0 { DEFAULT_PORT } else { s.chat_port },
            if s.chat_channel.is_empty() { DEFAULT_CHANNEL.to_string() } else { s.chat_channel.clone() },
        )
    };

    let nick = sanitize_nick(&nick);
    let friends = load_friends(&state.data_dir);

    // Bump the generation and drop any previous connection before starting a
    // new one, so a reconnect can't leave two readers racing on the same state.
    let generation = {
        let mut chat = state.chat.lock().unwrap();
        chat.generation += 1;
        chat.tx = None;
        chat.connected = false;
        chat.nick = nick.clone();
        chat.channels.clear();
        chat.generation
    };

    let (tx, mut rx) = mpsc::unbounded_channel::<String>();
    {
        let mut chat = state.chat.lock().unwrap();
        chat.tx = Some(tx.clone());
    }

    let app_task = app.clone();
    // The connection task outlives this command, so it can't borrow `State`.
    // Clone out everything it needs up front.
    let data_dir = state.data_dir.clone();

    tauri::async_runtime::spawn(async move {
        let result = async {
            let tcp = TcpStream::connect((server.as_str(), port))
                .await
                .map_err(|e| format!("could not reach {server}:{port} — {e}"))?;
            // Nagle off: IRC is small interactive writes, and batching them
            // adds latency to every message for no bandwidth win.
            let _ = tcp.set_nodelay(true);

            let connector = native_tls::TlsConnector::new()
                .map_err(|e| format!("TLS setup failed: {e}"))?;
            let connector = tokio_native_tls::TlsConnector::from(connector);
            let stream = connector
                .connect(server.as_str(), tcp)
                .await
                .map_err(|e| format!("TLS handshake with {server} failed: {e}"))?;

            Ok::<_, String>(stream)
        }
        .await;

        let stream = match result {
            Ok(s) => s,
            Err(e) => {
                let _ = app_task.emit(
                    "chat-status",
                    ChatStatusEvent { connected: false, nick: String::new(), error: Some(e) },
                );
                return;
            }
        };

        let (reader, mut writer) = tokio::io::split(stream);

        // Writer task: the only thing that touches the socket's write half.
        tauri::async_runtime::spawn(async move {
            while let Some(line) = rx.recv().await {
                if writer.write_all(line.as_bytes()).await.is_err() {
                    break;
                }
                if writer.write_all(b"\r\n").await.is_err() {
                    break;
                }
                let _ = writer.flush().await;
            }
            // Channel closed or socket died — a half-open write side is useless.
            let _ = writer.shutdown().await;
        });

        // Registration. No CAP negotiation: we use no IRCv3 capabilities, and
        // MONITOR is an ISUPPORT feature rather than a capability, so a plain
        // NICK/USER handshake is enough.
        let _ = tx.send(format!("NICK {nick}"));
        let _ = tx.send(format!("USER {nick} 0 * :Blurred Client"));

        run_reader(
            app_task,
            reader,
            tx,
            nick,
            channel,
            friends,
            data_dir,
            generation,
        )
        .await;
    });

    Ok(())
}

/// The read loop. Owns nick/channel bookkeeping for the life of the connection
/// and writes back into `AppState.chat` through the app handle's state.
#[allow(clippy::too_many_arguments)]
async fn run_reader<R>(
    app: AppHandle,
    reader: R,
    tx: UnboundedSender<String>,
    mut nick: String,
    channel: String,
    friends: Vec<Friend>,
    _data_dir: PathBuf,
    generation: u64,
) where
    R: tokio::io::AsyncRead + Unpin,
{
    use tauri::Manager;

    let mut lines = BufReader::new(reader).lines();
    // Nicks we've asked MONITOR about, so a presence line for someone we no
    // longer track doesn't produce a stray event.
    let monitored: HashSet<String> = friends
        .iter()
        .filter(|f| f.is_accepted())
        .map(|f| f.nick.to_lowercase())
        .collect();
    // Accumulates 353 RPL_NAMREPLY fragments until 366 says the list is done.
    let mut pending_names: Vec<String> = Vec::new();

    /// Bail out if another connect() superseded us while we were awaiting.
    macro_rules! stale {
        ($app:expr) => {{
            let st = $app.state::<AppState>();
            let chat = st.chat.lock().unwrap();
            chat.generation != generation
        }};
    }

    // The loop yields the reason it ended, so there's exactly one place the
    // disconnect reason is produced and no "assigned but never read" initial.
    let disconnect_reason: Option<String> = loop {
        let line = match lines.next_line().await {
            Ok(Some(l)) => l,
            Ok(None) => break Some("the server closed the connection".to_string()),
            Err(e) => break Some(format!("connection lost: {e}")),
        };

        if stale!(app) {
            return;
        }

        let Some(msg) = parse_line(&line) else { continue };
        let p = &msg.params;

        match msg.command.as_str() {
            // Keepalive. Must be answered or the server drops us after ~4min.
            "PING" => {
                let token = p.last().cloned().unwrap_or_default();
                let _ = tx.send(format!("PONG :{token}"));
            }

            // RPL_WELCOME — registration finished, we now have a usable session.
            "001" => {
                // The server's idea of our nick wins; it may have truncated it.
                if let Some(given) = p.first() {
                    nick = given.clone();
                }
                {
                    let st = app.state::<AppState>();
                    let mut chat = st.chat.lock().unwrap();
                    chat.connected = true;
                    chat.nick = nick.clone();
                }
                let _ = app.emit(
                    "chat-status",
                    ChatStatusEvent { connected: true, nick: nick.clone(), error: None },
                );

                let _ = tx.send(format!("JOIN {channel}"));

                // Ask the server to push presence for the crew. One MONITOR +
                // with a comma-separated target list; the server replies
                // immediately with 730/731 for the current state, so no separate
                // ISON seeding is needed.
                //
                // Accepted friends only. Tracking someone who merely has a
                // request outstanding would leak their online status to us
                // before they have agreed to anything.
                let targets: Vec<String> = friends
                    .iter()
                    .filter(|f| f.is_accepted())
                    .map(|f| f.nick.clone())
                    .collect();
                for chunk in targets.chunks(20) {
                    let _ = tx.send(format!("MONITOR + {}", chunk.join(",")));
                }

                // Announce our cape to the lobby so everyone already there can
                // ask for it. Deferred slightly: the JOIN above hasn't been
                // acknowledged yet, and a channel message before that would
                // bounce with "no such channel".
                {
                    let app_announce = app.clone();
                    tauri::async_runtime::spawn(async move {
                        tokio::time::sleep(std::time::Duration::from_millis(1500)).await;
                        let st = app_announce.state::<AppState>();
                        let hash = st.capes.active.lock().unwrap().as_ref().map(|(h, _)| h.clone());
                        crate::commands::capes::announce(&st, hash.as_deref());
                    });
                }
            }

            // ERR_NICKNAMEINUSE. Nicks are first-come on IRC, so fall back to a
            // suffixed variant rather than failing the whole connection.
            "433" => {
                if nick.len() < 16 {
                    nick.push('_');
                } else {
                    nick = format!("{}_", &nick[..15]);
                }
                let _ = tx.send(format!("NICK {nick}"));
                emit_system(&app, "*", format!("Nick was taken — using {nick} instead."));
            }

            // RPL_MONONLINE / RPL_MONOFFLINE. Targets are comma-separated;
            // online entries are full nick!user@host masks, offline are bare.
            "730" | "731" => {
                let online = msg.command == "730";
                if let Some(list) = p.last() {
                    for target in list.split(',') {
                        let n = target.split(['!', '@']).next().unwrap_or(target).trim();
                        if n.is_empty() || !monitored.contains(&n.to_lowercase()) {
                            continue;
                        }
                        crate::commands::bridge::push(
                            &app,
                            serde_json::json!({ "t": "presence", "nick": n, "online": online }),
                        );
                        let _ = app.emit(
                            "chat-presence",
                            ChatPresenceEvent { nick: n.to_string(), online },
                        );
                    }
                }
            }

            // ERR_MONLISTFULL — more friends than the server will track.
            "734" => {
                emit_system(
                    &app,
                    "*",
                    "The server's presence list is full — some friends won't show live status.",
                );
            }

            // RPL_NAMREPLY: "<me> <sym> <channel> :nick nick nick"
            "353" => {
                if let (Some(chan), Some(names)) = (p.get(2), p.last()) {
                    if pending_names.is_empty() {
                        pending_names.push(chan.clone());
                    }
                    for n in names.split_whitespace() {
                        // Strip channel-status prefixes (@ op, + voice, etc).
                        pending_names.push(n.trim_start_matches(['@', '+', '~', '&', '%']).to_string());
                    }
                }
            }

            // RPL_ENDOFNAMES — flush the accumulated roster.
            "366" => {
                if !pending_names.is_empty() {
                    let chan = pending_names.remove(0);
                    let _ = app.emit(
                        "chat-names",
                        ChatNamesEvent { channel: chan, users: std::mem::take(&mut pending_names) },
                    );
                }
            }

            "PRIVMSG" | "NOTICE" => {
                let target = p.first().cloned().unwrap_or_default();
                let body = p.last().cloned().unwrap_or_default();

                // Friend-request handshake. Handled before anything else and
                // never rendered as chat — a raw CTCP payload in the transcript
                // would just be noise.
                if let Some(payload) = extract_ctcp(&body, CTCP_FRIEND) {
                    handle_friend_ctcp(&app, &msg.prefix_nick, &payload, &tx);
                    continue;
                }

                // Cape exchange. Also never rendered as chat — a DATA chunk in
                // the transcript would be a wall of base64.
                if let Some(payload) = extract_ctcp(&body, CTCP_CAPE) {
                    handle_cape_ctcp(&app, &msg.prefix_nick, &payload, &tx);
                    continue;
                }

                let (text, is_action) = unwrap_ctcp(&body);

                // A message addressed to our nick is a DM, and its conversation
                // is keyed by the *sender*; anything else is channel traffic.
                let conversation = if target.eq_ignore_ascii_case(&nick) {
                    msg.prefix_nick.clone()
                } else {
                    target.clone()
                };

                let kind = if msg.command == "NOTICE" {
                    "notice"
                } else if is_action {
                    "action"
                } else {
                    "message"
                };

                emit_message(
                    &app,
                    ChatMessageEvent {
                        conversation,
                        from: msg.prefix_nick.clone(),
                        text,
                        kind: kind.to_string(),
                        ts: now(),
                        mine: false,
                    },
                );
            }

            "JOIN" => {
                let chan = p.first().cloned().unwrap_or_default();
                if msg.prefix_nick.eq_ignore_ascii_case(&nick) {
                    let st = app.state::<AppState>();
                    let mut chat = st.chat.lock().unwrap();
                    if !chat.channels.iter().any(|c| c.eq_ignore_ascii_case(&chan)) {
                        chat.channels.push(chan.clone());
                    }
                } else {
                    emit_system(&app, &chan, format!("{} surfaced.", msg.prefix_nick));
                }
            }

            "PART" => {
                let chan = p.first().cloned().unwrap_or_default();
                if msg.prefix_nick.eq_ignore_ascii_case(&nick) {
                    let st = app.state::<AppState>();
                    let mut chat = st.chat.lock().unwrap();
                    chat.channels.retain(|c| !c.eq_ignore_ascii_case(&chan));
                } else {
                    emit_system(&app, &chan, format!("{} submerged.", msg.prefix_nick));
                }
            }

            // A QUIT names no channel, so it can't be attributed to one
            // conversation. Presence for friends is covered by MONITOR, and the
            // roster refreshes on the next NAMES — so this is intentionally
            // dropped rather than spammed into every open channel.
            "QUIT" => {}

            "NICK" => {
                let new = p.last().cloned().unwrap_or_default();
                if msg.prefix_nick.eq_ignore_ascii_case(&nick) {
                    nick = new.clone();
                    let st = app.state::<AppState>();
                    let mut chat = st.chat.lock().unwrap();
                    chat.nick = new.clone();
                    drop(chat);
                    let _ = app.emit(
                        "chat-status",
                        ChatStatusEvent { connected: true, nick: new, error: None },
                    );
                }
            }

            // ERR_NOSUCHNICK — usually a DM to someone who just went offline.
            "401" => {
                if let Some(who) = p.get(1) {
                    emit_system(&app, who, format!("{who} isn't online right now."));
                }
            }

            "ERROR" => {
                break Some(p.last().cloned().unwrap_or_else(|| "server error".into()));
            }

            _ => {}
        }
    };

    // Only tear down shared state if we're still the live connection.
    if stale!(app) {
        return;
    }
    {
        let st = app.state::<AppState>();
        let mut chat = st.chat.lock().unwrap();
        chat.connected = false;
        chat.tx = None;
        chat.channels.clear();
    }
    let _ = app.emit(
        "chat-status",
        ChatStatusEvent { connected: false, nick: String::new(), error: disconnect_reason },
    );
}

#[tauri::command]
pub async fn chat_disconnect(app: AppHandle, state: State<'_, AppState>) -> Result<(), String> {
    {
        let mut chat = state.chat.lock().unwrap();
        // Bumping the generation orphans the reader task; sending QUIT lets the
        // server close cleanly rather than time us out.
        chat.generation += 1;
        chat.send_raw("QUIT :diving".to_string());
        chat.tx = None;
        chat.connected = false;
        chat.channels.clear();
    }
    let _ = app.emit(
        "chat-status",
        ChatStatusEvent { connected: false, nick: String::new(), error: None },
    );
    Ok(())
}

/// Send a message to a channel or a nick. Echoes locally, because IRC does not
/// reflect your own PRIVMSG back to you.
#[tauri::command]
pub async fn chat_send(
    app: AppHandle,
    state: State<'_, AppState>,
    conversation: String,
    text: String,
) -> Result<(), String> {
    let target = sanitize(&conversation);
    let body = sanitize(&text);
    if target.is_empty() || body.is_empty() {
        return Ok(());
    }

    let nick = {
        let chat = state.chat.lock().unwrap();
        if !chat.connected {
            return Err("Not connected.".to_string());
        }
        chat.send_raw(format!("PRIVMSG {target} :{body}"));
        chat.nick.clone()
    };

    emit_message(
        &app,
        ChatMessageEvent {
            conversation: target,
            from: nick,
            text: body,
            kind: "message".to_string(),
            ts: now(),
            mine: true,
        },
    );
    Ok(())
}

#[tauri::command]
pub async fn chat_join(state: State<'_, AppState>, channel: String) -> Result<(), String> {
    let mut chan = sanitize(&channel);
    if chan.is_empty() {
        return Ok(());
    }
    if !chan.starts_with('#') {
        chan.insert(0, '#');
    }
    let chat = state.chat.lock().unwrap();
    if !chat.connected {
        return Err("Not connected.".to_string());
    }
    chat.send_raw(format!("JOIN {chan}"));
    Ok(())
}

#[tauri::command]
pub async fn chat_part(state: State<'_, AppState>, channel: String) -> Result<(), String> {
    let chan = sanitize(&channel);
    if chan.is_empty() {
        return Ok(());
    }
    let chat = state.chat.lock().unwrap();
    chat.send_raw(format!("PART {chan}"));
    Ok(())
}

/// Ask the server to re-send the member roster for a channel.
#[tauri::command]
pub async fn chat_names(state: State<'_, AppState>, channel: String) -> Result<(), String> {
    let chan = sanitize(&channel);
    if chan.is_empty() {
        return Ok(());
    }
    let chat = state.chat.lock().unwrap();
    chat.send_raw(format!("NAMES {chan}"));
    Ok(())
}

// ---------------------------------------------------------------------------
// Friends
// ---------------------------------------------------------------------------

#[tauri::command]
pub async fn list_friends(state: State<'_, AppState>) -> Result<Vec<Friend>, String> {
    Ok(load_friends(&state.data_dir))
}

/// Send a friend request. Requires a live connection, because the request *is*
/// a message — there's nothing to queue it in if we're offline.
#[tauri::command]
pub async fn add_friend(
    state: State<'_, AppState>,
    nick: String,
    note: String,
) -> Result<Vec<Friend>, String> {
    let nick = sanitize_nick(&nick);
    if nick.is_empty() {
        return Err("Enter a nick.".to_string());
    }

    {
        let chat = state.chat.lock().unwrap();
        if !chat.connected {
            return Err("Connect to chat first — a request is sent to them directly.".to_string());
        }
        if nick.eq_ignore_ascii_case(&chat.nick) {
            return Err("That's you.".to_string());
        }
    }

    let mut friends = load_friends(&state.data_dir);
    let greeting = sanitize(&note);

    // Resolve the index into a local first. A `match` extends its scrutinee's
    // temporaries across every arm, so matching on `friends.iter().position(..)`
    // directly would hold a borrow of `friends` while the arms try to mutate it.
    let existing = friends
        .iter()
        .position(|f| f.nick.eq_ignore_ascii_case(&nick));

    match existing {
        Some(i) if friends[i].status == FriendStatus::Accepted => {
            return Err(format!("{nick} is already on your crew."));
        }
        // They already asked us — treat "add" as accepting, which is what the
        // user means when they add someone whose request is sitting in the list.
        Some(i) if friends[i].status == FriendStatus::PendingIn => {
            drop(friends);
            return accept_friend(state, nick).await;
        }
        // Re-sending an outstanding request is allowed: the other side may have
        // been offline, or not running Blurred Client, when we first asked.
        Some(i) => {
            friends[i].note = greeting.clone();
        }
        None => friends.push(Friend {
            nick: nick.clone(),
            note: greeting.clone(),
            added_at: now(),
            online: false,
            status: FriendStatus::PendingOut,
        }),
    }

    save_friends(&state.data_dir, &friends)?;

    let chat = state.chat.lock().unwrap();
    chat.send_raw(ctcp_line(&nick, CTCP_FRIEND, &greeting_or_default(&greeting)));

    Ok(friends)
}

/// The greeting is sent as the CTCP argument, and a bare `REQ` with no argument
/// is ambiguous with a malformed one — so an empty greeting becomes a marker.
fn greeting_or_default(greeting: &str) -> String {
    if greeting.is_empty() {
        "REQ -".to_string()
    } else {
        format!("REQ {greeting}")
    }
}

/// Accept an incoming request, and tell the other side so their copy flips too.
#[tauri::command]
pub async fn accept_friend(
    state: State<'_, AppState>,
    nick: String,
) -> Result<Vec<Friend>, String> {
    let nick = sanitize_nick(&nick);
    let mut friends = load_friends(&state.data_dir);

    let Some(i) = friends
        .iter()
        .position(|f| f.nick.eq_ignore_ascii_case(&nick))
    else {
        return Err(format!("No request from {nick}."));
    };

    friends[i].status = FriendStatus::Accepted;
    save_friends(&state.data_dir, &friends)?;

    let chat = state.chat.lock().unwrap();
    if chat.connected {
        chat.send_raw(ctcp_line(&nick, CTCP_FRIEND, "ACCEPT"));
        chat.send_raw(format!("MONITOR + {nick}"));
    }

    Ok(friends)
}

/// Decline an incoming request. Tells the sender so their pending entry clears
/// rather than hanging forever.
#[tauri::command]
pub async fn decline_friend(
    state: State<'_, AppState>,
    nick: String,
) -> Result<Vec<Friend>, String> {
    let safe = sanitize_nick(&nick);
    let mut friends = load_friends(&state.data_dir);
    friends.retain(|f| !f.nick.eq_ignore_ascii_case(&nick));
    save_friends(&state.data_dir, &friends)?;

    let chat = state.chat.lock().unwrap();
    if chat.connected {
        chat.send_raw(ctcp_line(&safe, CTCP_FRIEND, "DECLINE"));
    }

    Ok(friends)
}

#[tauri::command]
pub async fn remove_friend(state: State<'_, AppState>, nick: String) -> Result<Vec<Friend>, String> {
    let mut friends = load_friends(&state.data_dir);
    friends.retain(|f| !f.nick.eq_ignore_ascii_case(&nick));
    save_friends(&state.data_dir, &friends)?;

    let safe = sanitize_nick(&nick);
    let chat = state.chat.lock().unwrap();
    if chat.connected {
        chat.send_raw(format!("MONITOR - {safe}"));
    }

    Ok(friends)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_a_channel_message() {
        let l = parse_line(":reef!~reef@example.net PRIVMSG #blurred-client :hey there").unwrap();
        assert_eq!(l.prefix_nick, "reef");
        assert_eq!(l.command, "PRIVMSG");
        assert_eq!(l.params, vec!["#blurred-client", "hey there"]);
    }

    #[test]
    fn keeps_colons_inside_the_trailing_param() {
        let l = parse_line(":a!b@c PRIVMSG #x :see http://y/z :3").unwrap();
        assert_eq!(l.params.last().unwrap(), "see http://y/z :3");
    }

    #[test]
    fn parses_a_prefixless_ping() {
        let l = parse_line("PING :1234567").unwrap();
        assert!(l.prefix_nick.is_empty());
        assert_eq!(l.command, "PING");
        assert_eq!(l.params, vec!["1234567"]);
    }

    #[test]
    fn skips_ircv3_tags() {
        let l = parse_line("@time=2026-01-01T00:00:00Z :n!u@h PRIVMSG #c :hi").unwrap();
        assert_eq!(l.prefix_nick, "n");
        assert_eq!(l.command, "PRIVMSG");
    }

    #[test]
    fn unwraps_a_ctcp_action() {
        let (text, is_action) = unwrap_ctcp("\u{1}ACTION waves\u{1}");
        assert_eq!(text, "waves");
        assert!(is_action);
    }

    #[test]
    fn strips_newlines_so_a_message_cannot_inject_a_command() {
        assert_eq!(sanitize("hi\r\nQUIT :owned"), "hiQUIT :owned");
    }

    #[test]
    fn prefixes_nicks_that_start_with_a_digit() {
        assert_eq!(sanitize_nick("123abc"), "_123abc");
        assert_eq!(sanitize_nick("penguimyz"), "penguimyz");
    }

    #[test]
    fn falls_back_when_a_name_has_no_usable_characters() {
        assert_eq!(sanitize_nick("!!!"), "diver");
    }

    #[test]
    fn extracts_a_friend_ctcp_payload() {
        let body = "\u{1}BLURREDFRIEND REQ hey it's me\u{1}";
        assert_eq!(
            extract_ctcp(body, CTCP_FRIEND).as_deref(),
            Some("REQ hey it's me")
        );
    }

    #[test]
    fn extracts_a_bare_friend_ctcp_verb() {
        assert_eq!(
            extract_ctcp("\u{1}BLURREDFRIEND\u{1}", CTCP_FRIEND).as_deref(),
            Some("")
        );
    }

    #[test]
    fn ignores_other_ctcps_and_plain_messages() {
        assert!(extract_ctcp("\u{1}ACTION waves\u{1}", CTCP_FRIEND).is_none());
        assert!(extract_ctcp("just a normal message", CTCP_FRIEND).is_none());
    }

    #[test]
    fn does_not_match_a_verb_that_merely_starts_with_ours() {
        // Without the explicit space/empty check this would match on the prefix
        // and be handled as a friend request.
        assert!(extract_ctcp("\u{1}BLURREDFRIENDLY REQ x\u{1}", CTCP_FRIEND).is_none());
    }

    #[test]
    fn builds_ctcp_lines_with_and_without_arguments() {
        assert_eq!(
            ctcp_line("reef", CTCP_FRIEND, "ACCEPT"),
            "PRIVMSG reef :\u{1}BLURREDFRIEND ACCEPT\u{1}"
        );
        assert_eq!(
            ctcp_line("reef", CTCP_FRIEND, ""),
            "PRIVMSG reef :\u{1}BLURREDFRIEND\u{1}"
        );
    }
}
