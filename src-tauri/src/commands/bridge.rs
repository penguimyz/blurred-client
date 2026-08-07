//! The launcher ↔ mod bridge.
//!
//! The Blurred mod running inside Minecraft needs the things only the launcher
//! knows: who your crew is, who's online, and the chat stream. Rather than give
//! the mod its own IRC connection (two connections, two nicks, double the
//! traffic), the launcher keeps the single connection and relays over a local
//! socket. Same shape as Lunar: the game client is a view onto the launcher.
//!
//! # Security
//!
//! Two things keep this from being a hole:
//!
//! 1. **Loopback only.** The listener binds `127.0.0.1:0`, never `0.0.0.0`, so
//!    nothing off this machine can reach it. The `:0` picks a free ephemeral
//!    port, which also avoids fighting whatever else is on a fixed one.
//! 2. **A per-run token.** Any local process could still connect to a loopback
//!    port, so the first line a client sends must be a `hello` carrying a token
//!    generated fresh at launcher start. The launcher passes it to the game as
//!    a JVM `-D` property, so only a process the launcher started knows it.
//!    Wrong or missing token, and the connection is dropped before it can read
//!    anything.
//!
//! The protocol is newline-delimited JSON in both directions — trivially
//! parseable from Java without pulling in a dependency, and easy to eyeball.
//!
//! # Wire format
//!
//! Mod → launcher:
//!   `{"t":"hello","token":"…"}`            authenticate (must be first)
//!   `{"t":"send","conversation":"…","text":"…"}`
//!   `{"t":"playing","server":"…"}`         report the server you joined
//!   `{"t":"wearCape","id":"…"}`            cape picked in game (id absent = off)
//!   `{"t":"addFriend","nick":"…","note":"…"}`
//!   `{"t":"acceptFriend","nick":"…"}`
//!   `{"t":"declineFriend","nick":"…"}`     also withdraws one we sent
//!
//! Launcher → mod:
//!   `{"t":"welcome","nick":"…","connected":bool}`
//!   `{"t":"friends","friends":[{…,"server":"…"}]}`
//!   `{"t":"message","conversation":"…","from":"…","text":"…","mine":bool}`
//!   `{"t":"status","connected":bool,"nick":"…"}`
//!
//! Commands are fire-and-forget: there is no request id and no reply. Anything
//! the user needs to know comes back through the channels the mod already
//! renders — a roster push, or a system line in the transcript.

use std::collections::HashMap;
use std::sync::atomic::{AtomicU16, Ordering};
use std::sync::Mutex;

use serde::Serialize;
use tauri::{AppHandle, Emitter, Manager, State};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::TcpListener;
use tokio::sync::broadcast;

use crate::state::AppState;

/// How many pushed events a slow mod client can fall behind before it starts
/// dropping them. Chat is the bulk of the traffic and a client that far behind
/// has bigger problems than a missed line.
const BROADCAST_CAPACITY: usize = 256;

pub struct BridgeState {
    /// Ephemeral port the listener bound, or 0 before startup finishes.
    /// Atomic because `AppState` is shared immutably once Tauri manages it,
    /// and the real port isn't known until the listener binds during setup.
    pub port: AtomicU16,
    /// Per-run shared secret. Regenerated every launcher start, so a token
    /// captured from a previous run is useless.
    pub token: String,
    /// Fan-out to every connected mod client.
    pub tx: broadcast::Sender<String>,
    /// The Minecraft server the game most recently reported being on. Feeds
    /// "join friend" — see `chat::announce_world`.
    pub playing: Mutex<Option<String>>,
    /// Where each crew member says *they* are playing, keyed by lowercased
    /// nick. Populated from the `BLURREDWORLD` CTCP and never persisted: it's
    /// true only while they're there, and a saved copy would offer a one-click
    /// join to somewhere they left days ago.
    pub crew_servers: Mutex<HashMap<String, String>>,
}

impl Default for BridgeState {
    fn default() -> Self {
        let (tx, _) = broadcast::channel(BROADCAST_CAPACITY);
        Self {
            port: AtomicU16::new(0),
            token: uuid::Uuid::new_v4().to_string(),
            tx,
            playing: Mutex::new(None),
            crew_servers: Mutex::new(HashMap::new()),
        }
    }
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BridgeInfo {
    pub port: u16,
    pub token: String,
}

/// Start the loopback listener. Called once from `lib.rs` setup; the returned
/// port is stored back into state so `launch_instance` can pass it to the game.
pub async fn start(app: AppHandle) -> std::io::Result<u16> {
    let listener = TcpListener::bind("127.0.0.1:0").await?;
    let port = listener.local_addr()?.port();
    tracing::info!("mod bridge listening on 127.0.0.1:{port}");

    tauri::async_runtime::spawn(async move {
        loop {
            let Ok((stream, _addr)) = listener.accept().await else {
                continue;
            };
            let app = app.clone();
            tauri::async_runtime::spawn(async move {
                if let Err(e) = serve(app, stream).await {
                    tracing::debug!("bridge client ended: {e}");
                }
            });
        }
    });

    Ok(port)
}

/// One connected mod client.
async fn serve(app: AppHandle, stream: tokio::net::TcpStream) -> std::io::Result<()> {
    let (expected_token, mut rx) = {
        let state = app.state::<AppState>();
        let bridge = &state.bridge;
        (bridge.token.clone(), bridge.tx.subscribe())
    };

    let (reader, mut writer) = stream.into_split();
    let mut lines = BufReader::new(reader).lines();

    // Authenticate before anything else is read or written. A client that
    // doesn't lead with a valid hello gets nothing at all.
    let first = match lines.next_line().await? {
        Some(l) => l,
        None => return Ok(()),
    };
    let hello: serde_json::Value = serde_json::from_str(&first).unwrap_or_default();
    if hello.get("t").and_then(|v| v.as_str()) != Some("hello")
        || hello.get("token").and_then(|v| v.as_str()) != Some(expected_token.as_str())
    {
        tracing::warn!("bridge rejected a client with a bad or missing token");
        return Ok(());
    }

    // Greet with the current state so the mod starts populated rather than
    // waiting for the next event to arrive.
    let (nick, connected) = {
        let state = app.state::<AppState>();
        let chat = state.chat.lock().unwrap();
        (chat.nick.clone(), chat.connected)
    };
    let friends = {
        let state = app.state::<AppState>();
        crate::commands::chat::load_friends_public(&state.data_dir)
    };

    let welcome = serde_json::json!({
        "t": "welcome", "nick": nick, "connected": connected,
    });
    writer.write_all(format!("{welcome}\n").as_bytes()).await?;
    let roster = crate::commands::chat::friends_payload(&app, &friends);
    writer.write_all(format!("{roster}\n").as_bytes()).await?;

    // Every cape we know about, in one message. Includes our own so the player
    // sees their own cape immediately rather than only after someone else
    // announces it back.
    {
        let state = app.state::<AppState>();
        let mut all: serde_json::Map<String, serde_json::Value> = state
            .capes
            .remote
            .lock()
            .unwrap()
            .iter()
            .map(|(k, v)| (k.clone(), serde_json::Value::String(v.clone())))
            .collect();

        if let Some((_, own)) = state.capes.active.lock().unwrap().as_ref() {
            let accounts = state.accounts.lock().unwrap();
            if let Some(me) = accounts.iter().max_by_key(|a| a.last_used) {
                all.insert(me.username.clone(), serde_json::Value::String(own.clone()));
            }
        }

        if !all.is_empty() {
            let capes = serde_json::json!({ "t": "capes", "capes": all });
            writer.write_all(format!("{capes}\n").as_bytes()).await?;
        }

        // The library listing for the in-game picker, separate from the pixel
        // data above.
        let library = crate::commands::capes::library(&state.data_dir);
        let worn = crate::commands::capes::active_cape_id(&state).unwrap_or_default();
        let list = serde_json::json!({
            "t": "capeList",
            "worn": worn,
            "capes": library.iter()
                .map(|c| serde_json::json!({ "id": c.id, "name": c.name }))
                .collect::<Vec<_>>(),
        });
        writer.write_all(format!("{list}\n").as_bytes()).await?;
    }

    // Two halves: push broadcast events out, and read commands in.
    let app_read = app.clone();
    let reader_task = tauri::async_runtime::spawn(async move {
        while let Ok(Some(line)) = lines.next_line().await {
            handle_command(&app_read, &line).await;
        }
    });

    loop {
        match rx.recv().await {
            Ok(line) => {
                if writer.write_all(format!("{line}\n").as_bytes()).await.is_err() {
                    break;
                }
            }
            // Lagged: the client fell behind and lost events. Keep serving
            // rather than dropping it — chat gaps beat a dead connection.
            Err(broadcast::error::RecvError::Lagged(n)) => {
                tracing::debug!("bridge client lagged {n} events");
            }
            Err(broadcast::error::RecvError::Closed) => break,
        }
    }

    reader_task.abort();
    Ok(())
}

/// A command sent up from the mod.
async fn handle_command(app: &AppHandle, line: &str) {
    let Ok(v) = serde_json::from_str::<serde_json::Value>(line) else {
        return;
    };

    match v.get("t").and_then(|t| t.as_str()) {
        // Chat sent from inside the game.
        Some("send") => {
            let conversation = v.get("conversation").and_then(|c| c.as_str()).unwrap_or("");
            let text = v.get("text").and_then(|c| c.as_str()).unwrap_or("");
            if conversation.is_empty() || text.is_empty() {
                return;
            }
            let state = app.state::<AppState>();
            let _ = crate::commands::chat::send_from_bridge(
                app,
                &state,
                conversation.to_string(),
                text.to_string(),
            );
        }

        // Cape picked from the in-game cosmetics screen. Routed through the
        // same command the launcher UI uses, so wearing a cape in game
        // announces it to other players exactly as wearing it in the launcher
        // would — one code path, no second implementation to drift.
        Some("wearCape") => {
            let id = v
                .get("id")
                .and_then(|c| c.as_str())
                .filter(|s| !s.is_empty())
                .map(|s| s.to_string());
            let state = app.state::<AppState>();
            if let Err(e) = crate::commands::capes::apply_active_cape(app, &state, id) {
                tracing::warn!("in-game cape change failed: {e}");
            }
            push_cape_list(app);
        }

        // The game joined (or left) a server. Recorded so crew can join you,
        // then handed straight on to them — the record is only useful if the
        // people it's for are told about it.
        Some("playing") => {
            let server = v
                .get("server")
                .and_then(|c| c.as_str())
                .filter(|s| !s.is_empty())
                .map(|s| s.to_string());
            {
                let state = app.state::<AppState>();
                *state.bridge.playing.lock().unwrap() = server.clone();
            }
            crate::commands::chat::announce_world(app, None);
            let _ = app.emit("playing-changed", server);
        }

        // Crew management from the in-game screen. Each one routes through the
        // same core the launcher's own buttons call, so there is one
        // implementation of the handshake rather than two that can drift.
        //
        // The result is reported back as a system line in the transcript rather
        // than returned: the bridge is a one-way command channel, and the mod
        // is already showing that transcript next to the button that was
        // pressed. Success needs no line — the roster push says it.
        Some("addFriend") => {
            let nick = nick_of(&v);
            if nick.is_empty() {
                return;
            }
            let note = v.get("note").and_then(|c| c.as_str()).unwrap_or("");
            let state = app.state::<AppState>();
            match crate::commands::chat::add_friend_core(app, &state, nick.clone(), note.to_string())
            {
                Ok(_) => crate::commands::chat::notice(
                    app,
                    format!("Crew request sent to {nick}."),
                ),
                Err(e) => crate::commands::chat::notice(app, e),
            }
        }

        Some("acceptFriend") => {
            let nick = nick_of(&v);
            if nick.is_empty() {
                return;
            }
            let state = app.state::<AppState>();
            if let Err(e) = crate::commands::chat::accept_friend_core(app, &state, nick) {
                crate::commands::chat::notice(app, e);
            }
        }

        // Declining an incoming request and withdrawing one of ours are the
        // same operation on this side; the mod offers one button for both.
        Some("declineFriend") => {
            let nick = nick_of(&v);
            if nick.is_empty() {
                return;
            }
            let state = app.state::<AppState>();
            if let Err(e) = crate::commands::chat::decline_friend_core(app, &state, nick) {
                crate::commands::chat::notice(app, e);
            }
        }

        _ => {}
    }
}

/// The `nick` field of a mod command, trimmed. Empty means "don't act".
fn nick_of(v: &serde_json::Value) -> String {
    v.get("nick")
        .and_then(|c| c.as_str())
        .unwrap_or("")
        .trim()
        .to_string()
}

/// Push a JSON event to every connected mod client. Cheap no-op when nothing
/// is connected, which is the common case.
pub fn push(app: &AppHandle, value: serde_json::Value) {
    let state = app.state::<AppState>();
    let _ = state.bridge.tx.send(value.to_string());
}

/// Send the cape library (names only) and which one is worn, for the in-game
/// picker. No image data: the game already has the worn cape's texture, and
/// shipping every cape's pixels to draw a list would be pure waste.
pub fn push_cape_list(app: &AppHandle) {
    let state = app.state::<AppState>();
    let capes = crate::commands::capes::library(&state.data_dir);
    let worn = crate::commands::capes::active_cape_id(&state);

    push(
        app,
        serde_json::json!({
            "t": "capeList",
            "worn": worn.unwrap_or_default(),
            "capes": capes.iter()
                .map(|c| serde_json::json!({ "id": c.id, "name": c.name }))
                .collect::<Vec<_>>(),
        }),
    );
}

/// Port + token, so `launch_instance` can hand them to the game as JVM args.
#[tauri::command]
pub async fn bridge_info(state: State<'_, AppState>) -> Result<BridgeInfo, String> {
    Ok(BridgeInfo {
        port: state.bridge.port.load(Ordering::Relaxed),
        token: state.bridge.token.clone(),
    })
}

/// The Minecraft server the game is currently on, if the mod has reported one.
#[tauri::command]
pub async fn current_server(state: State<'_, AppState>) -> Result<Option<String>, String> {
    Ok(state.bridge.playing.lock().unwrap().clone())
}
