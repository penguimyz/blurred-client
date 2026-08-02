//! Custom capes: storage, and sharing them with every other Blurred user.
//!
//! # Why this is peer-to-peer
//!
//! Cosmetics normally need a server: somewhere to upload your cape and a CDN to
//! serve it to everyone else. This launcher has no backend by design, so capes
//! travel the same way chat does — over IRC, between clients.
//!
//! A 64x32 cape PNG is around a kilobyte, ~1.4 KB base64. IRC lines cap out
//! near 400 usable bytes, so a cape is four or five messages. That's cheap
//! enough to send on demand, but far too expensive to broadcast unprompted, so
//! the protocol is announce-then-request:
//!
//!   1. On connect (and whenever you change cape) you announce a *hash* to the
//!      lobby: `HAVE <mcUsername> <hash>`. One short line.
//!   2. Anyone who sees a hash they don't have DMs you `REQ <hash>`.
//!   3. You reply with `DATA <hash> <seq> <total> <chunk>` messages.
//!
//! So the bytes only move when someone actually needs them, and a room full of
//! people who already have each other's capes costs one line each per join.
//!
//! Received capes live in memory only — they're other people's cosmetics, not
//! ours to persist. Your own capes are on disk under `<data>/capes/`.

use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::Mutex;

use serde::{Deserialize, Serialize};
use sha1::{Digest, Sha1};
use tauri::{AppHandle, State};

use crate::state::AppState;
use crate::util;

/// Max bytes of base64 per DATA line. IRC's hard limit is 512 bytes for the
/// whole line including `PRIVMSG <target> :` and the CTCP wrapper, so this
/// leaves comfortable headroom for a long nick and channel name.
const CHUNK: usize = 300;

/// Refuse anything larger than this once assembled. A cape is ~1.4 KB base64;
/// this is generous enough for a 64x64 sheet and small enough that a hostile
/// peer can't stream megabytes into memory.
const MAX_CAPE_B64: usize = 64 * 1024;

/// Give up on a half-received cape after this many chunks are outstanding, so
/// an abandoned transfer can't pin memory forever.
const MAX_PARTIAL_TRANSFERS: usize = 32;

// ---------------------------------------------------------------------------
// Model
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Cape {
    pub id: String,
    pub name: String,
    pub created_at: String,
    /// SHA-1 of the PNG bytes. Doubles as the wire identity when sharing.
    pub hash: String,
}

#[derive(Default)]
pub struct CapeState {
    /// Other people's capes: lowercased MC username -> base64 PNG.
    pub remote: Mutex<HashMap<String, String>>,
    /// In-flight transfers: hash -> chunks received so far.
    pub incoming: Mutex<HashMap<String, Partial>>,
    /// Our active cape, as (hash, base64), or None when we aren't wearing one.
    pub active: Mutex<Option<(String, String)>>,
    /// The library id of the active cape. Kept alongside the bytes so the
    /// in-game picker can show which row is worn without re-hashing files.
    pub active_id: Mutex<Option<String>>,
}

pub struct Partial {
    pub total: usize,
    pub chunks: Vec<Option<String>>,
    /// Who we're expecting the rest from, so a third party can't inject chunks.
    pub from: String,
}

// ---------------------------------------------------------------------------
// On-disk library
// ---------------------------------------------------------------------------

fn capes_dir(data_dir: &Path) -> PathBuf {
    data_dir.join("capes")
}

fn index_path(data_dir: &Path) -> PathBuf {
    capes_dir(data_dir).join("capes.json")
}

fn load_index(data_dir: &Path) -> Vec<Cape> {
    std::fs::read_to_string(index_path(data_dir))
        .ok()
        .and_then(|raw| serde_json::from_str(&raw).ok())
        .unwrap_or_default()
}

fn save_index(data_dir: &Path, capes: &[Cape]) -> Result<(), String> {
    std::fs::create_dir_all(capes_dir(data_dir)).map_err(|e| e.to_string())?;
    let json = serde_json::to_string_pretty(capes).map_err(|e| e.to_string())?;
    std::fs::write(index_path(data_dir), json).map_err(|e| e.to_string())
}

fn cape_png_path(data_dir: &Path, id: &str) -> PathBuf {
    capes_dir(data_dir).join(format!("{id}.png"))
}

pub fn hash_bytes(bytes: &[u8]) -> String {
    let mut h = Sha1::new();
    h.update(bytes);
    format!("{:x}", h.finalize())
}

// ---------------------------------------------------------------------------
// Commands
// ---------------------------------------------------------------------------

#[tauri::command]
pub async fn list_capes(state: State<'_, AppState>) -> Result<Vec<Cape>, String> {
    Ok(load_index(&state.data_dir))
}

/// Save a cape from the maker. `data` is a base64 PNG (no data-URL prefix).
#[tauri::command]
pub async fn save_cape(
    state: State<'_, AppState>,
    name: String,
    data: String,
) -> Result<Vec<Cape>, String> {
    let bytes = util::base64_decode(&data).map_err(|_| "that isn't valid base64 PNG data")?;
    if bytes.len() < 8 || &bytes[1..4] != b"PNG" {
        return Err("that isn't a PNG".to_string());
    }

    let id = uuid::Uuid::new_v4().to_string();
    let hash = hash_bytes(&bytes);

    std::fs::create_dir_all(capes_dir(&state.data_dir)).map_err(|e| e.to_string())?;
    std::fs::write(cape_png_path(&state.data_dir, &id), &bytes).map_err(|e| e.to_string())?;

    let mut capes = load_index(&state.data_dir);
    capes.push(Cape {
        id,
        name: name.trim().to_string(),
        created_at: chrono::Utc::now().to_rfc3339(),
        hash,
    });
    save_index(&state.data_dir, &capes)?;
    Ok(capes)
}

#[tauri::command]
pub async fn delete_cape(state: State<'_, AppState>, id: String) -> Result<Vec<Cape>, String> {
    if id.contains(['/', '\\', '.']) {
        return Err("invalid cape id".to_string());
    }
    let _ = std::fs::remove_file(cape_png_path(&state.data_dir, &id));

    let mut capes = load_index(&state.data_dir);
    capes.retain(|c| c.id != id);
    save_index(&state.data_dir, &capes)?;
    Ok(capes)
}

/// Read a cape's PNG back as base64, for the editor and the preview.
#[tauri::command]
pub async fn read_cape(state: State<'_, AppState>, id: String) -> Result<String, String> {
    if id.contains(['/', '\\', '.']) {
        return Err("invalid cape id".to_string());
    }
    let bytes = std::fs::read(cape_png_path(&state.data_dir, &id)).map_err(|e| e.to_string())?;
    Ok(util::base64_encode(&bytes))
}

/// Wear a cape (or `None` to take it off), then tell everyone.
#[tauri::command]
pub async fn set_active_cape(
    app: AppHandle,
    state: State<'_, AppState>,
    id: Option<String>,
) -> Result<(), String> {
    apply_active_cape(&app, &state, id)
}

/// The actual work behind `set_active_cape`, callable without a Tauri command
/// context so the in-game picker (via the mod bridge) goes through exactly the
/// same path — including the announcement to other players. Two
/// implementations of "wear a cape" would be two chances to forget to announce.
pub fn apply_active_cape(
    app: &AppHandle,
    state: &AppState,
    id: Option<String>,
) -> Result<(), String> {
    match id {
        None => {
            *state.capes.active.lock().unwrap() = None;
            *state.capes.active_id.lock().unwrap() = None;
            announce(state, None);
            push_own_cape_to_bridge(app, state, None);
        }
        Some(id) => {
            if id.contains(['/', '\\', '.']) {
                return Err("invalid cape id".to_string());
            }
            let bytes =
                std::fs::read(cape_png_path(&state.data_dir, &id)).map_err(|e| e.to_string())?;
            let hash = hash_bytes(&bytes);
            let b64 = util::base64_encode(&bytes);

            *state.capes.active.lock().unwrap() = Some((hash.clone(), b64.clone()));
            *state.capes.active_id.lock().unwrap() = Some(id);
            announce(state, Some(&hash));
            push_own_cape_to_bridge(app, state, Some(&b64));
        }
    }
    Ok(())
}

/// The cape library on disk, for the in-game picker.
pub fn library(data_dir: &Path) -> Vec<Cape> {
    load_index(data_dir)
}

/// Which cape is currently worn, by id.
pub fn active_cape_id(state: &AppState) -> Option<String> {
    state.capes.active_id.lock().unwrap().clone()
}

/// Show our own cape in-game immediately, without waiting to hear it back off
/// the network — you should see your own cape even alone on a server.
fn push_own_cape_to_bridge(app: &AppHandle, state: &AppState, b64: Option<&str>) {
    let username = {
        let accounts = state.accounts.lock().unwrap();
        accounts
            .iter()
            .max_by_key(|a| a.last_used)
            .map(|a| a.username.clone())
    };
    let Some(username) = username else { return };

    crate::commands::bridge::push(
        app,
        serde_json::json!({ "t": "cape", "username": username, "data": b64 }),
    );
}

// ---------------------------------------------------------------------------
// Wire protocol
// ---------------------------------------------------------------------------

/// Broadcast which cape we're wearing (by hash) to the lobby.
pub fn announce(state: &AppState, hash: Option<&str>) {
    let username = {
        let accounts = state.accounts.lock().unwrap();
        accounts
            .iter()
            .max_by_key(|a| a.last_used)
            .map(|a| a.username.clone())
    };
    let Some(username) = username else { return };

    let channel = {
        let s = state.settings.lock().unwrap();
        s.chat_channel.clone()
    };
    if channel.is_empty() {
        return;
    }

    let payload = match hash {
        Some(h) => format!("HAVE {username} {h}"),
        // An empty hash is how "I took my cape off" is expressed, so peers can
        // clear it rather than keeping the last one forever.
        None => format!("HAVE {username} -"),
    };

    let chat = state.chat.lock().unwrap();
    if chat.connected {
        chat.send_raw(crate::commands::chat::ctcp_line_public(
            &channel, "BLURREDCAPE", &payload,
        ));
    }
}

/// Handle an inbound `BLURREDCAPE` CTCP. Called from the chat read loop.
///
/// Returns a list of raw IRC lines to send in reply, rather than sending them
/// directly, so the caller keeps sole ownership of the socket.
pub fn handle_ctcp(
    state: &AppState,
    from: &str,
    payload: &str,
    on_cape: &mut dyn FnMut(String, Option<String>),
) -> Vec<String> {
    let mut out = Vec::new();
    let mut parts = payload.splitn(2, ' ');
    let verb = parts.next().unwrap_or("").to_uppercase();
    let rest = parts.next().unwrap_or("").trim();

    match verb.as_str() {
        // "HAVE <mcUsername> <hash|->"
        "HAVE" => {
            let mut it = rest.split_whitespace();
            let (Some(username), Some(hash)) = (it.next(), it.next()) else {
                return out;
            };

            if hash == "-" {
                state
                    .capes
                    .remote
                    .lock()
                    .unwrap()
                    .remove(&username.to_lowercase());
                on_cape(username.to_string(), None);
                return out;
            }

            // Already have it? Nothing to do — this is the common case in a
            // busy channel and the whole reason we announce hashes not bytes.
            if state
                .capes
                .remote
                .lock()
                .unwrap()
                .contains_key(&username.to_lowercase())
            {
                return out;
            }

            out.push(crate::commands::chat::ctcp_line_public(
                from,
                "BLURREDCAPE",
                &format!("REQ {hash}"),
            ));
        }

        // "REQ <hash>" — someone wants our cape.
        "REQ" => {
            let wanted = rest;
            let active = state.capes.active.lock().unwrap().clone();
            let Some((hash, b64)) = active else { return out };
            if hash != wanted {
                return out;
            }

            let chunks: Vec<&str> = split_chunks(&b64);
            let total = chunks.len();
            for (i, chunk) in chunks.iter().enumerate() {
                out.push(crate::commands::chat::ctcp_line_public(
                    from,
                    "BLURREDCAPE",
                    &format!("DATA {hash} {i} {total} {chunk}"),
                ));
            }
        }

        // "DATA <hash> <seq> <total> <chunk>"
        "DATA" => {
            let mut it = rest.splitn(4, ' ');
            let (Some(hash), Some(seq), Some(total), Some(chunk)) =
                (it.next(), it.next(), it.next(), it.next())
            else {
                return out;
            };
            let (Ok(seq), Ok(total)) = (seq.parse::<usize>(), total.parse::<usize>()) else {
                return out;
            };
            if total == 0 || seq >= total || total * CHUNK > MAX_CAPE_B64 {
                return out;
            }

            let mut incoming = state.capes.incoming.lock().unwrap();
            if incoming.len() > MAX_PARTIAL_TRANSFERS && !incoming.contains_key(hash) {
                return out;
            }

            let entry = incoming.entry(hash.to_string()).or_insert_with(|| Partial {
                total,
                chunks: vec![None; total],
                from: from.to_string(),
            });

            // Only the peer who started the transfer may continue it.
            if !entry.from.eq_ignore_ascii_case(from) || entry.total != total {
                return out;
            }
            entry.chunks[seq] = Some(chunk.to_string());

            if entry.chunks.iter().all(|c| c.is_some()) {
                let assembled: String = entry
                    .chunks
                    .iter()
                    .map(|c| c.as_deref().unwrap_or(""))
                    .collect();
                let sender = entry.from.clone();
                incoming.remove(hash);
                drop(incoming);

                // Verify before trusting: the bytes must actually hash to what
                // was advertised, or a peer could serve anything under someone
                // else's announced hash.
                match util::base64_decode(&assembled) {
                    Ok(bytes) if hash_bytes(&bytes) == hash => {
                        state
                            .capes
                            .remote
                            .lock()
                            .unwrap()
                            .insert(sender.to_lowercase(), assembled.clone());
                        on_cape(sender, Some(assembled));
                    }
                    _ => {
                        tracing::warn!("discarded a cape from {sender}: hash mismatch");
                    }
                }
            }
        }

        _ => {}
    }

    out
}

/// Split base64 into wire-sized pieces on character boundaries.
fn split_chunks(b64: &str) -> Vec<&str> {
    let mut out = Vec::new();
    let bytes = b64.as_bytes();
    let mut i = 0;
    while i < bytes.len() {
        let end = (i + CHUNK).min(bytes.len());
        out.push(&b64[i..end]);
        i = end;
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn splits_into_wire_sized_chunks() {
        let s = "a".repeat(750);
        let chunks = split_chunks(&s);
        assert_eq!(chunks.len(), 3);
        assert_eq!(chunks[0].len(), CHUNK);
        assert_eq!(chunks[2].len(), 150);
        assert_eq!(chunks.concat(), s);
    }

    #[test]
    fn a_short_payload_is_a_single_chunk() {
        let chunks = split_chunks("abc");
        assert_eq!(chunks, vec!["abc"]);
    }

    #[test]
    fn hashing_is_stable() {
        assert_eq!(hash_bytes(b"blurred"), hash_bytes(b"blurred"));
        assert_ne!(hash_bytes(b"blurred"), hash_bytes(b"blurrec"));
    }
}
