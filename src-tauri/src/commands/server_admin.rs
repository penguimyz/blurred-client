//! Running a server, rather than just starting one.
//!
//! Two jobs that a launcher which hosts servers has to do and which otherwise
//! mean editing JSON by hand or memorising console commands:
//!
//!  - **Who's allowed in.** Operators, the whitelist and the ban list.
//!  - **Not losing the world.** Snapshots, listed and restorable.
//!
//! # Why the lists are edited both ways
//!
//! A running server holds `ops.json`, `whitelist.json` and
//! `banned-players.json` in memory and rewrites them on shutdown, so editing
//! the files underneath a live server does nothing and is then overwritten.
//! Sending the console command is the only thing that works while it's up; the
//! file is the only thing that works while it's down. So this does whichever
//! applies, which is the behaviour someone using the UI expects either way.

use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Manager, State};

use crate::commands::servers::{check_id, load_server, server_dir};
use crate::models::server::{ServerBackup, ServerPlayer, ServerPlayers};
use crate::state::AppState;

/// Which of the three lists an operation applies to.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum PlayerList {
    Ops,
    Whitelist,
    Banned,
}

impl PlayerList {
    fn file(self) -> &'static str {
        match self {
            PlayerList::Ops => "ops.json",
            PlayerList::Whitelist => "whitelist.json",
            PlayerList::Banned => "banned-players.json",
        }
    }

    /// (add, remove) console commands.
    fn commands(self) -> (&'static str, &'static str) {
        match self {
            PlayerList::Ops => ("op", "deop"),
            PlayerList::Whitelist => ("whitelist add", "whitelist remove"),
            PlayerList::Banned => ("ban", "pardon"),
        }
    }
}

// ---------------------------------------------------------------------------
// Reading the lists
// ---------------------------------------------------------------------------

/// Vanilla's on-disk shape for all three files: an array of objects that
/// always carry a name and a UUID, plus per-list extras.
#[derive(Debug, Deserialize, Serialize)]
struct RawEntry {
    #[serde(default)]
    uuid: String,
    #[serde(default)]
    name: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    level: Option<u8>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    reason: Option<String>,
    #[serde(
        default,
        rename = "bypassesPlayerLimit",
        skip_serializing_if = "Option::is_none"
    )]
    bypasses_player_limit: Option<bool>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    created: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    source: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    expires: Option<String>,
}

fn read_list(dir: &Path, list: PlayerList) -> Vec<RawEntry> {
    std::fs::read_to_string(dir.join(list.file()))
        .ok()
        .and_then(|raw| serde_json::from_str::<Vec<RawEntry>>(&raw).ok())
        .unwrap_or_default()
}

fn write_list(dir: &Path, list: PlayerList, entries: &[RawEntry]) -> Result<(), String> {
    let json = serde_json::to_string_pretty(entries).map_err(|e| e.to_string())?;
    std::fs::write(dir.join(list.file()), json).map_err(|e| e.to_string())
}

fn to_player(e: &RawEntry) -> ServerPlayer {
    ServerPlayer {
        name: e.name.clone(),
        uuid: e.uuid.clone(),
        level: e.level,
        reason: e.reason.clone(),
    }
}

#[tauri::command]
pub async fn list_server_players(
    state: State<'_, AppState>,
    id: String,
) -> Result<ServerPlayers, String> {
    check_id(&id)?;
    let dir = server_dir(&state, &id);
    if !dir.is_dir() {
        return Err("no such server".to_string());
    }

    Ok(ServerPlayers {
        ops: read_list(&dir, PlayerList::Ops).iter().map(to_player).collect(),
        whitelist: read_list(&dir, PlayerList::Whitelist).iter().map(to_player).collect(),
        banned: read_list(&dir, PlayerList::Banned).iter().map(to_player).collect(),
    })
}

// ---------------------------------------------------------------------------
// Editing the lists
// ---------------------------------------------------------------------------

/// Add someone to a list.
///
/// `level` is the operator level (1–4) and is ignored for the other two lists.
#[tauri::command]
pub async fn add_server_player(
    state: State<'_, AppState>,
    id: String,
    list: PlayerList,
    username: String,
    level: Option<u8>,
    reason: Option<String>,
) -> Result<ServerPlayers, String> {
    check_id(&id)?;
    let name = username.trim().to_string();
    if name.is_empty() {
        return Err("Enter a username.".to_string());
    }
    if !valid_username(&name) {
        return Err(format!("\"{name}\" isn't a valid Minecraft username."));
    }

    let dir = server_dir(&state, &id);
    let running = state.servers.running.lock().unwrap().contains_key(&id);

    if running {
        // The live server owns these files while it's up — go through the
        // console or the change will be reverted on shutdown.
        let (add, _) = list.commands();
        let cmd = match list {
            PlayerList::Banned => match reason.as_deref().map(str::trim).filter(|r| !r.is_empty()) {
                Some(r) => format!("{add} {name} {r}"),
                None => format!("{add} {name}"),
            },
            _ => format!("{add} {name}"),
        };
        send_console(&state, &id, &cmd)?;
        // The server rewrites the file itself; give it a moment, then re-read.
        drop(dir);
        tokio::time::sleep(std::time::Duration::from_millis(250)).await;
        return list_server_players(state, id).await;
    }

    // Offline: write the file ourselves. The UUID matters — vanilla matches
    // ops and bans by UUID, not by name, so an entry without one is ignored
    // the moment the server is in online mode.
    let uuid = resolve_uuid(&name).await.unwrap_or_default();
    if uuid.is_empty() {
        let server = load_server(&dir).ok_or("no such server")?;
        if server.online_mode {
            return Err(format!(
                "Couldn't look up a Mojang account for \"{name}\". \
                 Check the spelling — on an online-mode server an entry without a UUID is ignored."
            ));
        }
    }

    let mut entries = read_list(&dir, list);
    if entries.iter().any(|e| e.name.eq_ignore_ascii_case(&name)) {
        return Err(format!("{name} is already on that list."));
    }

    entries.push(RawEntry {
        uuid,
        name: name.clone(),
        level: (list == PlayerList::Ops).then(|| level.unwrap_or(4).clamp(1, 4)),
        reason: (list == PlayerList::Banned)
            .then(|| reason.unwrap_or_else(|| "Banned by an operator".to_string())),
        bypasses_player_limit: (list == PlayerList::Ops).then_some(false),
        created: (list == PlayerList::Banned)
            .then(|| chrono::Utc::now().format("%Y-%m-%d %H:%M:%S %z").to_string()),
        source: (list == PlayerList::Banned).then(|| "Blurred Client".to_string()),
        expires: (list == PlayerList::Banned).then(|| "forever".to_string()),
    });
    write_list(&dir, list, &entries)?;

    list_server_players(state, id).await
}

#[tauri::command]
pub async fn remove_server_player(
    state: State<'_, AppState>,
    id: String,
    list: PlayerList,
    username: String,
) -> Result<ServerPlayers, String> {
    check_id(&id)?;
    let name = username.trim().to_string();
    let dir = server_dir(&state, &id);
    let running = state.servers.running.lock().unwrap().contains_key(&id);

    if running {
        let (_, remove) = list.commands();
        send_console(&state, &id, &format!("{remove} {name}"))?;
        tokio::time::sleep(std::time::Duration::from_millis(250)).await;
        return list_server_players(state, id).await;
    }

    let mut entries = read_list(&dir, list);
    entries.retain(|e| !e.name.eq_ignore_ascii_case(&name));
    write_list(&dir, list, &entries)?;

    list_server_players(state, id).await
}

/// Kick someone who is currently connected. Only meaningful while running.
#[tauri::command]
pub async fn kick_server_player(
    state: State<'_, AppState>,
    id: String,
    username: String,
    reason: Option<String>,
) -> Result<(), String> {
    check_id(&id)?;
    let name = username.trim();
    if !valid_username(name) {
        return Err("That isn't a valid username.".to_string());
    }
    let reason = reason.unwrap_or_default();
    let cmd = if reason.trim().is_empty() {
        format!("kick {name}")
    } else {
        format!("kick {name} {}", reason.trim())
    };
    send_console(&state, &id, &cmd)
}

fn send_console(state: &AppState, id: &str, command: &str) -> Result<(), String> {
    let running = state.servers.running.lock().unwrap();
    let Some(r) = running.get(id) else {
        return Err("That server isn't running.".to_string());
    };
    r.stdin
        .send(command.to_string())
        .map_err(|_| "console is closed".to_string())
}

fn valid_username(s: &str) -> bool {
    (3..=16).contains(&s.len()) && s.chars().all(|c| c.is_ascii_alphanumeric() || c == '_')
}

/// Look up a Mojang UUID for a username, dashed.
///
/// Returns `None` on any failure — a missing account, a rate limit, or no
/// network. Callers decide whether that's fatal, because it only actually
/// matters for an online-mode server.
async fn resolve_uuid(name: &str) -> Option<String> {
    let url = format!("https://api.mojang.com/users/profiles/minecraft/{name}");
    let resp = reqwest::Client::new().get(url).send().await.ok()?;
    if !resp.status().is_success() {
        return None;
    }
    let json: serde_json::Value = resp.json().await.ok()?;
    let raw = json["id"].as_str()?;
    if raw.len() != 32 {
        return None;
    }
    // Mojang returns it undashed; the server's JSON files want it dashed.
    Some(format!(
        "{}-{}-{}-{}-{}",
        &raw[0..8],
        &raw[8..12],
        &raw[12..16],
        &raw[16..20],
        &raw[20..32]
    ))
}

// ---------------------------------------------------------------------------
// Backups
// ---------------------------------------------------------------------------

fn backups_dir(dir: &Path) -> PathBuf {
    dir.join("backups")
}

#[tauri::command]
pub async fn list_server_backups(
    state: State<'_, AppState>,
    id: String,
) -> Result<Vec<ServerBackup>, String> {
    check_id(&id)?;
    let dir = backups_dir(&server_dir(&state, &id));
    if !dir.is_dir() {
        return Ok(Vec::new());
    }

    let mut out: Vec<ServerBackup> = std::fs::read_dir(&dir)
        .map_err(|e| e.to_string())?
        .flatten()
        .filter(|e| e.path().extension().is_some_and(|x| x == "zip"))
        .filter_map(|e| {
            let meta = e.metadata().ok()?;
            Some(ServerBackup {
                file: e.file_name().to_string_lossy().to_string(),
                created_at: meta
                    .modified()
                    .ok()
                    .map(|t| chrono::DateTime::<chrono::Utc>::from(t).to_rfc3339())
                    .unwrap_or_default(),
                size_bytes: meta.len(),
            })
        })
        .collect();

    // Newest first — the one you want is nearly always the last one taken.
    out.sort_by(|a, b| b.file.cmp(&a.file));
    Ok(out)
}

#[tauri::command]
pub async fn create_server_backup(
    state: State<'_, AppState>,
    id: String,
) -> Result<ServerBackup, String> {
    check_id(&id)?;
    let dir = server_dir(&state, &id);
    let server = load_server(&dir).ok_or("no such server")?;

    // Ask a running server to flush first. Region files are memory-mapped and
    // written lazily, so a snapshot taken mid-play without this can be minutes
    // behind — or, worse, half-written.
    //
    // Bound to a local: a guard created in an `if` condition lives until the
    // end of the statement, which here would mean holding the servers lock
    // across the sleep below.
    let running = state.servers.running.lock().unwrap().contains_key(&id);
    if running {
        send_console(&state, &id, "save-all flush")?;
        tokio::time::sleep(std::time::Duration::from_secs(2)).await;
    }

    if !dir.join(&server.level_name).is_dir() {
        return Err("There's no world to back up yet — start the server once first.".to_string());
    }

    snapshot_world(&dir, &server.level_name)
}

/// Zip a world folder into `backups/<level>-<timestamp>.zip`.
///
/// Synchronous and shared with the start path, which is why it isn't a command
/// itself. Stored uncompressed-by-default is tempting for speed, but worlds are
/// mostly already-compressed region data with a lot of padding, and deflate
/// still halves them — worth the seconds.
pub fn snapshot_world(dir: &Path, level_name: &str) -> Result<ServerBackup, String> {
    use std::io::Write;
    use zip::write::SimpleFileOptions;

    let world = dir.join(level_name);
    if !world.is_dir() {
        return Err(format!("no world folder named \"{level_name}\""));
    }

    let out_dir = backups_dir(dir);
    std::fs::create_dir_all(&out_dir).map_err(|e| e.to_string())?;

    // Sortable timestamp, so listing by filename is listing by age.
    let stamp = chrono::Local::now().format("%Y%m%d-%H%M%S");
    let file_name = format!("{level_name}-{stamp}.zip");
    let path = out_dir.join(&file_name);

    let file = std::fs::File::create(&path).map_err(|e| e.to_string())?;
    let mut zip = zip::ZipWriter::new(file);
    let options = SimpleFileOptions::default().compression_method(zip::CompressionMethod::Deflated);

    let mut stack = vec![world.clone()];
    while let Some(current) = stack.pop() {
        for entry in std::fs::read_dir(&current).map_err(|e| e.to_string())?.flatten() {
            let p = entry.path();
            let rel = p
                .strip_prefix(&world)
                .map_err(|e| e.to_string())?
                .to_string_lossy()
                .replace('\\', "/");

            if p.is_dir() {
                zip.add_directory(format!("{rel}/"), options).map_err(|e| e.to_string())?;
                stack.push(p);
            } else {
                // `session.lock` is held open by a running server and is
                // meaningless in a backup anyway.
                if rel.ends_with("session.lock") {
                    continue;
                }
                let bytes = match std::fs::read(&p) {
                    Ok(b) => b,
                    // Skip anything the OS won't let us read rather than
                    // failing the whole snapshot over one locked file.
                    Err(_) => continue,
                };
                zip.start_file(rel, options).map_err(|e| e.to_string())?;
                zip.write_all(&bytes).map_err(|e| e.to_string())?;
            }
        }
    }

    zip.finish().map_err(|e| e.to_string())?;

    let size = std::fs::metadata(&path).map(|m| m.len()).unwrap_or(0);
    Ok(ServerBackup {
        file: file_name,
        created_at: chrono::Utc::now().to_rfc3339(),
        size_bytes: size,
    })
}

/// Replace the live world with a backup.
///
/// Refuses while the server is running, and takes a snapshot of the current
/// world first — restoring the wrong backup should not be the end of the story.
#[tauri::command]
pub async fn restore_server_backup(
    state: State<'_, AppState>,
    id: String,
    file: String,
) -> Result<(), String> {
    check_id(&id)?;
    check_backup_name(&file)?;

    if state.servers.running.lock().unwrap().contains_key(&id) {
        return Err("Stop the server before restoring a backup.".to_string());
    }

    let dir = server_dir(&state, &id);
    let server = load_server(&dir).ok_or("no such server")?;
    let archive = backups_dir(&dir).join(&file);
    if !archive.is_file() {
        return Err("That backup no longer exists.".to_string());
    }

    let world = dir.join(&server.level_name);
    if world.is_dir() {
        snapshot_world(&dir, &server.level_name)?;
        std::fs::remove_dir_all(&world).map_err(|e| e.to_string())?;
    }
    std::fs::create_dir_all(&world).map_err(|e| e.to_string())?;

    let reader = std::fs::File::open(&archive).map_err(|e| e.to_string())?;
    let mut zip = zip::ZipArchive::new(reader).map_err(|e| e.to_string())?;

    for i in 0..zip.len() {
        let mut entry = zip.by_index(i).map_err(|e| e.to_string())?;
        // `enclosed_name` is the zip-slip guard: it returns None for any path
        // that would escape the destination once joined.
        let Some(rel) = entry.enclosed_name() else {
            continue;
        };
        let target = world.join(rel);

        if entry.is_dir() {
            std::fs::create_dir_all(&target).map_err(|e| e.to_string())?;
            continue;
        }
        if let Some(parent) = target.parent() {
            std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
        }
        let mut out = std::fs::File::create(&target).map_err(|e| e.to_string())?;
        std::io::copy(&mut entry, &mut out).map_err(|e| e.to_string())?;
    }

    Ok(())
}

#[tauri::command]
pub async fn delete_server_backup(
    state: State<'_, AppState>,
    id: String,
    file: String,
) -> Result<(), String> {
    check_id(&id)?;
    check_backup_name(&file)?;
    let path = backups_dir(&server_dir(&state, &id)).join(&file);
    if path.is_file() {
        std::fs::remove_file(&path).map_err(|e| e.to_string())?;
    }
    Ok(())
}

/// A backup name must be a plain filename inside `backups/`.
fn check_backup_name(file: &str) -> Result<(), String> {
    if file.is_empty()
        || file.contains(['/', '\\'])
        || file.contains("..")
        || !file.ends_with(".zip")
    {
        return Err("invalid backup name".to_string());
    }
    Ok(())
}

/// Does this server want to come back up after a crash?
///
/// Read off disk rather than from a cached model: the supervisor runs long
/// after `start_server` returned, and the user may have toggled the setting in
/// the meantime.
pub fn wants_auto_restart(app: &AppHandle, id: &str) -> bool {
    let state = app.state::<AppState>();
    load_server(&server_dir(&state, id)).is_some_and(|s| s.auto_restart)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_backup_names_that_escape_the_folder() {
        assert!(check_backup_name("../../evil.zip").is_err());
        assert!(check_backup_name("sub/dir.zip").is_err());
        assert!(check_backup_name("world.txt").is_err());
        assert!(check_backup_name("").is_err());
        assert!(check_backup_name("world-20260101-120000.zip").is_ok());
    }

    #[test]
    fn validates_minecraft_usernames() {
        assert!(valid_username("Steve"));
        assert!(valid_username("a_b_c"));
        assert!(!valid_username("ab"));
        assert!(!valid_username("this_name_is_far_too_long"));
        // A space would let a caller smuggle a second console command in.
        assert!(!valid_username("Steve op Mallory"));
    }

    #[test]
    fn round_trips_a_world_through_a_snapshot() {
        let base = std::env::temp_dir().join(format!("blurred-backup-{}", uuid::Uuid::new_v4()));
        let world = base.join("world");
        std::fs::create_dir_all(world.join("region")).unwrap();
        std::fs::write(world.join("level.dat"), b"leveldata").unwrap();
        std::fs::write(world.join("region/r.0.0.mca"), b"chunks").unwrap();
        // Must be excluded: a live server holds this open.
        std::fs::write(world.join("session.lock"), b"lock").unwrap();

        let backup = snapshot_world(&base, "world").unwrap();
        assert!(backup.file.starts_with("world-"));
        assert!(backup.size_bytes > 0);

        let archive = base.join("backups").join(&backup.file);
        let mut zip = zip::ZipArchive::new(std::fs::File::open(&archive).unwrap()).unwrap();
        let names: Vec<String> = (0..zip.len())
            .map(|i| zip.by_index(i).unwrap().name().to_string())
            .collect();

        assert!(names.iter().any(|n| n == "level.dat"), "{names:?}");
        assert!(names.iter().any(|n| n == "region/r.0.0.mca"), "{names:?}");
        assert!(!names.iter().any(|n| n.ends_with("session.lock")), "{names:?}");

        std::fs::remove_dir_all(&base).ok();
    }
}
