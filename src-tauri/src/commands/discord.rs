//! "Blurred Client — In the launcher" on your Discord profile.
//!
//! # Why this exists separately from the mod's presence
//!
//! The in-game companion already publishes a status (`DiscordPresence.java`),
//! but only while Minecraft is running. Browsing instances, editing a cape or
//! setting up a server showed nothing at all — which reads as "not playing"
//! when you are very much in the middle of playing.
//!
//! # The handoff, and why it's necessary
//!
//! Discord shows **one activity per application per user**. The launcher and
//! the mod share an application id, so if both published at once they would
//! overwrite each other every few seconds and the status would flicker between
//! "In the launcher" and "On a server".
//!
//! So they take turns, and the launcher yields: it publishes only while no
//! instance is running, and clears its activity the moment one starts. The mod
//! owns the status from then on, because it knows things the launcher cannot —
//! main menu versus world versus server. When the game exits, the launcher
//! picks the status back up.
//!
//! Deciding it this way round means no coordination protocol between the two
//! processes: `state.running` is already the authority on whether a game is up,
//! and the launcher is the one holding it.
//!
//! # Transport
//!
//! Same hand-written IPC as the mod's Java side — a named pipe on Windows, a
//! Unix socket elsewhere, framed as little-endian opcode + length + JSON. It's
//! forty lines of socket code either way, which is a much better trade than a
//! dependency for something this peripheral.

use std::io::{Read, Write};
#[cfg(not(target_os = "windows"))]
use std::path::PathBuf;
use std::time::Duration;

use tauri::{AppHandle, Manager};

use crate::state::AppState;

/// Discord numbers its IPC sockets; several builds can run side by side.
const MAX_SOCKETS: u8 = 10;

const OP_HANDSHAKE: u32 = 0;
const OP_FRAME: u32 = 1;

/// Discord rate-limits activity updates to roughly one per 15 seconds.
const MIN_UPDATE_INTERVAL: Duration = Duration::from_secs(15);

/// How long to wait before looking for Discord again after it isn't there.
const RECONNECT_DELAY: Duration = Duration::from_secs(60);

/// Start the presence worker. Cheap and non-fatal: if Discord isn't running, or
/// no application id is configured, this thread parks and retries.
pub fn start(app: AppHandle) {
    std::thread::Builder::new()
        .name("blurred-discord".into())
        .spawn(move || run(app))
        .ok();
}

fn run(app: AppHandle) {
    // Session start, so Discord's "elapsed" counter measures the launcher
    // session rather than restarting on every reconnect.
    let started = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0);

    loop {
        let (app_id, enabled) = {
            let state = app.state::<AppState>();
            let settings = state.settings.lock().unwrap();
            (
                settings.discord_app_id.trim().to_string(),
                settings.discord_launcher_presence,
            )
        };

        if app_id.is_empty() || !enabled {
            std::thread::sleep(RECONNECT_DELAY);
            continue;
        }

        match Ipc::connect() {
            Some(mut ipc) => {
                if let Err(e) = pump(&app, &mut ipc, &app_id, started) {
                    tracing::debug!("discord presence dropped: {e}");
                }
            }
            // Discord isn't running, or isn't running as this user. Not worth
            // logging every minute — most people don't have it open.
            None => {}
        }

        std::thread::sleep(RECONNECT_DELAY);
    }
}

/// Publish state changes until the connection breaks.
///
/// Every write is followed by a read of Discord's reply. That's flow control,
/// not interest in the contents: Discord answers every frame, and a client that
/// never drains eventually fills the socket buffer and wedges.
fn pump(app: &AppHandle, ipc: &mut Ipc, app_id: &str, started: u64) -> std::io::Result<()> {
    ipc.send(
        OP_HANDSHAKE,
        &format!(r#"{{"v":1,"client_id":"{}"}}"#, escape(app_id)),
    )?;
    ipc.receive()?;
    tracing::info!("discord launcher presence connected");

    let mut published: Option<bool> = None;

    loop {
        // The mod owns the status while a game is up — see the module note.
        let in_game = {
            let state = app.state::<AppState>();
            let running = state.running.lock().unwrap();
            !running.is_empty()
        };

        if published != Some(in_game) {
            let payload = if in_game {
                // Clear ours so the mod's is the only one Discord sees.
                clear_frame()
            } else {
                activity_frame(started)
            };
            ipc.send(OP_FRAME, &payload)?;
            ipc.receive()?;
            published = Some(in_game);
        }

        // Coarse: a status is not something anyone watches for latency, and
        // Discord won't take updates faster than this anyway.
        std::thread::sleep(MIN_UPDATE_INTERVAL);
    }
}

fn activity_frame(started: u64) -> String {
    format!(
        r#"{{"cmd":"SET_ACTIVITY","args":{{"pid":{pid},"activity":{{"details":"In the launcher","timestamps":{{"start":{started}}},"assets":{{"large_image":"logo","large_text":"Blurred Client"}}}}}},"nonce":"{nonce}"}}"#,
        pid = std::process::id(),
        started = started,
        nonce = nonce(),
    )
}

/// A SET_ACTIVITY with no activity clears it.
fn clear_frame() -> String {
    format!(
        r#"{{"cmd":"SET_ACTIVITY","args":{{"pid":{pid}}},"nonce":"{nonce}"}}"#,
        pid = std::process::id(),
        nonce = nonce(),
    )
}

fn nonce() -> String {
    uuid::Uuid::new_v4().to_string()
}

/// Minimal JSON string escaping. The only value interpolated from settings is
/// the application id, which is digits in practice — but it's user-editable,
/// so it doesn't get to break out of the string.
fn escape(value: &str) -> String {
    value.replace('\\', "\\\\").replace('"', "\\\"")
}

// ---------------------------------------------------------------------------
// Transport
// ---------------------------------------------------------------------------

struct Ipc {
    stream: Box<dyn ReadWrite>,
}

trait ReadWrite: Read + Write + Send {}
impl<T: Read + Write + Send> ReadWrite for T {}

impl Ipc {
    fn connect() -> Option<Self> {
        for i in 0..MAX_SOCKETS {
            if let Some(ipc) = Self::open(i) {
                return Some(ipc);
            }
        }
        None
    }

    #[cfg(target_os = "windows")]
    fn open(index: u8) -> Option<Self> {
        // A named pipe opened read/write behaves as a bidirectional stream.
        // Absent pipe -> NotFound, which is just "Discord isn't running".
        let path = format!(r"\\.\pipe\discord-ipc-{index}");
        let file = std::fs::OpenOptions::new()
            .read(true)
            .write(true)
            .open(path)
            .ok()?;
        Some(Self { stream: Box::new(file) })
    }

    #[cfg(not(target_os = "windows"))]
    fn open(index: u8) -> Option<Self> {
        for dir in unix_socket_dirs() {
            let path = dir.join(format!("discord-ipc-{index}"));
            if let Ok(stream) = std::os::unix::net::UnixStream::connect(&path) {
                return Some(Self { stream: Box::new(stream) });
            }
        }
        None
    }

    fn send(&mut self, opcode: u32, json: &str) -> std::io::Result<()> {
        let body = json.as_bytes();
        let mut header = [0u8; 8];
        header[..4].copy_from_slice(&opcode.to_le_bytes());
        header[4..].copy_from_slice(&(body.len() as u32).to_le_bytes());
        self.stream.write_all(&header)?;
        self.stream.write_all(body)?;
        self.stream.flush()
    }

    /// Read one frame and discard it. See [`pump`].
    fn receive(&mut self) -> std::io::Result<()> {
        let mut header = [0u8; 8];
        self.stream.read_exact(&mut header)?;
        let length = u32::from_le_bytes([header[4], header[5], header[6], header[7]]) as usize;
        if length > 0 {
            // Bounded: Discord's replies are small, and a hostile length here
            // would otherwise be an allocation of whatever it says.
            let mut body = vec![0u8; length.min(64 * 1024)];
            self.stream.read_exact(&mut body)?;
        }
        Ok(())
    }
}

/// Where Discord puts its socket on Linux and macOS.
///
/// The base directory is the first of `XDG_RUNTIME_DIR`, `TMPDIR`, `TMP`,
/// `TEMP`, `/tmp` that is set — the order Discord itself uses. The
/// subdirectories cover Flatpak and Snap, which re-root the socket inside their
/// own trees; a Flatpak Discord is a very normal way to have Discord on Linux.
#[cfg(not(target_os = "windows"))]
fn unix_socket_dirs() -> Vec<PathBuf> {
    let base = std::env::var("XDG_RUNTIME_DIR")
        .or_else(|_| std::env::var("TMPDIR"))
        .or_else(|_| std::env::var("TMP"))
        .or_else(|_| std::env::var("TEMP"))
        .unwrap_or_else(|_| "/tmp".to_string());
    let root = PathBuf::from(base);
    vec![
        root.clone(),
        root.join("app/com.discordapp.Discord"),
        root.join("app/com.discordapp.DiscordCanary"),
        root.join("snap.discord"),
    ]
}
