//! Hosting a Minecraft server from the launcher.
//!
//! Each server is a self-contained folder under `<data>/servers/<id>/` holding
//! its jar, world, `server.properties`, `eula.txt` and our own `server.json` —
//! the same one-folder-per-thing shape instances use, so a server can be
//! zipped, backed up or deleted on its own.
//!
//! # What this deliberately does not do
//!
//! It does not touch your router. Port forwarding needs UPnP or manual firewall
//! changes, and a launcher silently opening a port to the internet is not
//! something that should happen quietly. The UI reports the LAN address, which
//! covers same-network play, and says plainly what else would be required.
//!
//! The EULA is likewise an explicit click. Writing `eula=true` on the user's
//! behalf would be agreeing to a licence for them.

use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::process::Stdio;
use std::sync::Mutex;

use tauri::{AppHandle, Emitter, Manager, State};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::process::{Child, Command};
use tokio::sync::mpsc::{self, UnboundedSender};
use uuid::Uuid;

use crate::models::instance::Loader;
use crate::models::server::{Server, ServerStatus};
use crate::state::AppState;

/// A running server process and the pipe into its console.
pub struct RunningServer {
    pub stdin: UnboundedSender<String>,
    pub kill: tokio::sync::oneshot::Sender<()>,
    pub ready: bool,
}

#[derive(Default)]
pub struct ServerState {
    pub running: Mutex<HashMap<String, RunningServer>>,
}

fn servers_dir(state: &AppState) -> PathBuf {
    state.data_dir.join("servers")
}

fn server_dir(state: &AppState, id: &str) -> PathBuf {
    servers_dir(state).join(id)
}

fn manifest_path(dir: &Path) -> PathBuf {
    dir.join("server.json")
}

fn load_server(dir: &Path) -> Option<Server> {
    std::fs::read_to_string(manifest_path(dir))
        .ok()
        .and_then(|raw| serde_json::from_str(&raw).ok())
}

fn save_server(dir: &Path, server: &Server) -> Result<(), String> {
    std::fs::create_dir_all(dir).map_err(|e| e.to_string())?;
    let json = serde_json::to_string_pretty(server).map_err(|e| e.to_string())?;
    std::fs::write(manifest_path(dir), json).map_err(|e| e.to_string())
}

/// Reject ids that could escape the servers directory.
fn check_id(id: &str) -> Result<(), String> {
    if id.is_empty() || id.contains(['/', '\\', '.']) {
        return Err("invalid server id".to_string());
    }
    Ok(())
}

// ---------------------------------------------------------------------------
// CRUD
// ---------------------------------------------------------------------------

#[tauri::command]
pub async fn list_servers(state: State<'_, AppState>) -> Result<Vec<Server>, String> {
    let dir = servers_dir(&state);
    if !dir.exists() {
        return Ok(Vec::new());
    }

    let mut out: Vec<Server> = std::fs::read_dir(&dir)
        .map_err(|e| e.to_string())?
        .flatten()
        .filter(|e| e.path().is_dir())
        .filter_map(|e| load_server(&e.path()))
        .collect();
    out.sort_by(|a, b| a.name.cmp(&b.name));
    Ok(out)
}

#[tauri::command]
pub async fn create_server(
    state: State<'_, AppState>,
    name: String,
    mc_version: String,
    loader: Loader,
    port: u16,
) -> Result<Server, String> {
    let name = name.trim().to_string();
    if name.is_empty() {
        return Err("Give the server a name.".to_string());
    }
    if !matches!(loader, Loader::Vanilla | Loader::Fabric) {
        return Err("Only vanilla and Fabric servers are supported right now.".to_string());
    }

    let id = Uuid::new_v4().to_string();
    let dir = server_dir(&state, &id);
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;

    let server = Server {
        id,
        name,
        mc_version: mc_version.trim().to_string(),
        loader,
        port: if port == 0 { 25565 } else { port },
        max_memory_mb: 2048,
        created_at: chrono::Utc::now().to_rfc3339(),
        last_started: None,
        eula_accepted: false,
        motd: "A Blurred Client server".to_string(),
        max_players: 10,
        gamemode: "survival".to_string(),
        difficulty: "normal".to_string(),
        online_mode: true,
        pvp: true,
    };
    save_server(&dir, &server)?;
    Ok(server)
}

#[tauri::command]
pub async fn update_server(state: State<'_, AppState>, server: Server) -> Result<Server, String> {
    check_id(&server.id)?;
    let dir = server_dir(&state, &server.id);
    save_server(&dir, &server)?;
    write_properties(&dir, &server)?;
    Ok(server)
}

#[tauri::command]
pub async fn delete_server(state: State<'_, AppState>, id: String) -> Result<(), String> {
    check_id(&id)?;
    if state.servers.running.lock().unwrap().contains_key(&id) {
        return Err("Stop the server before deleting it.".to_string());
    }
    let dir = server_dir(&state, &id);
    if dir.exists() {
        std::fs::remove_dir_all(&dir).map_err(|e| e.to_string())?;
    }
    Ok(())
}

#[tauri::command]
pub async fn open_server_folder(state: State<'_, AppState>, id: String) -> Result<(), String> {
    check_id(&id)?;
    crate::commands::modpacks::reveal_path(server_dir(&state, &id).to_string_lossy().to_string())
        .await
}

/// Accept the Minecraft EULA for this server. Explicit, and recorded.
#[tauri::command]
pub async fn accept_eula(state: State<'_, AppState>, id: String) -> Result<Server, String> {
    check_id(&id)?;
    let dir = server_dir(&state, &id);
    let mut server = load_server(&dir).ok_or("no such server")?;

    std::fs::write(
        dir.join("eula.txt"),
        "# Accepted through Blurred Client.\n# https://aka.ms/MinecraftEULA\neula=true\n",
    )
    .map_err(|e| e.to_string())?;

    server.eula_accepted = true;
    save_server(&dir, &server)?;
    Ok(server)
}

// ---------------------------------------------------------------------------
// server.properties
// ---------------------------------------------------------------------------

/// Write the handful of properties we expose, preserving everything else.
///
/// Reads the existing file and replaces only the keys we own, so a
/// hand-edited `view-distance` or datapack setting survives a change made in
/// the UI. Anything we manage that isn't present yet gets appended.
fn write_properties(dir: &Path, server: &Server) -> Result<(), String> {
    let path = dir.join("server.properties");
    let existing = std::fs::read_to_string(&path).unwrap_or_default();

    let managed: Vec<(&str, String)> = vec![
        ("server-port", server.port.to_string()),
        ("motd", server.motd.clone()),
        ("max-players", server.max_players.to_string()),
        ("gamemode", server.gamemode.clone()),
        ("difficulty", server.difficulty.clone()),
        ("online-mode", server.online_mode.to_string()),
        ("pvp", server.pvp.to_string()),
        // Without this a fresh server is unreachable on the LAN.
        ("enable-status", "true".to_string()),
    ];

    let mut seen: Vec<&str> = Vec::new();
    let mut out = String::new();

    for line in existing.lines() {
        let trimmed = line.trim_start();
        if trimmed.starts_with('#') || !trimmed.contains('=') {
            out.push_str(line);
            out.push('\n');
            continue;
        }
        let key = trimmed.split('=').next().unwrap_or("").trim();
        match managed.iter().find(|(k, _)| *k == key) {
            Some((k, v)) => {
                out.push_str(&format!("{k}={v}\n"));
                seen.push(k);
            }
            None => {
                out.push_str(line);
                out.push('\n');
            }
        }
    }

    for (k, v) in &managed {
        if !seen.contains(k) {
            out.push_str(&format!("{k}={v}\n"));
        }
    }

    std::fs::write(&path, out).map_err(|e| e.to_string())
}

// ---------------------------------------------------------------------------
// Jar download
// ---------------------------------------------------------------------------

/// Fetch the server jar if it isn't already there. Emits progress as log lines
/// so the console shows what's happening rather than sitting blank.
async fn ensure_jar(app: &AppHandle, dir: &Path, server: &Server) -> Result<PathBuf, String> {
    let jar = dir.join("server.jar");
    if jar.exists() {
        return Ok(jar);
    }

    let log = |line: &str| {
        let _ = app.emit(
            "server-log",
            serde_json::json!({ "serverId": server.id, "line": line }),
        );
    };

    let http = reqwest::Client::new();

    let url = match server.loader {
        Loader::Fabric => {
            log("Resolving the Fabric server launcher…");
            // Fabric publishes a self-installing server launcher per
            // (game, loader, installer) triple; asking for the latest stable of
            // each avoids pinning versions that go stale.
            let loader_version = fabric_latest_loader(&http).await?;
            let installer_version = fabric_latest_installer(&http).await?;
            format!(
                "https://meta.fabricmc.net/v2/versions/loader/{}/{}/{}/server/jar",
                server.mc_version, loader_version, installer_version
            )
        }
        _ => {
            log("Looking up the Mojang version manifest…");
            mojang_server_url(&http, &server.mc_version).await?
        }
    };

    log(&format!("Downloading {url}"));
    let resp = http.get(&url).send().await.map_err(|e| e.to_string())?;
    if !resp.status().is_success() {
        return Err(format!(
            "download failed ({}). Is {} a real Minecraft version?",
            resp.status(),
            server.mc_version
        ));
    }
    let bytes = resp.bytes().await.map_err(|e| e.to_string())?;
    std::fs::write(&jar, &bytes).map_err(|e| e.to_string())?;
    log(&format!("Server jar ready ({:.1} MB)", bytes.len() as f64 / 1_048_576.0));

    Ok(jar)
}

async fn mojang_server_url(http: &reqwest::Client, version: &str) -> Result<String, String> {
    let manifest: serde_json::Value = http
        .get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
        .send()
        .await
        .map_err(|e| e.to_string())?
        .json()
        .await
        .map_err(|e| e.to_string())?;

    let entry = manifest["versions"]
        .as_array()
        .ok_or("malformed version manifest")?
        .iter()
        .find(|v| v["id"].as_str() == Some(version))
        .ok_or_else(|| format!("Minecraft {version} isn't in the version manifest"))?;

    let detail_url = entry["url"].as_str().ok_or("version entry has no url")?;
    let detail: serde_json::Value = http
        .get(detail_url)
        .send()
        .await
        .map_err(|e| e.to_string())?
        .json()
        .await
        .map_err(|e| e.to_string())?;

    detail["downloads"]["server"]["url"]
        .as_str()
        .map(|s| s.to_string())
        .ok_or_else(|| format!("Minecraft {version} has no server download"))
}

async fn fabric_latest_loader(http: &reqwest::Client) -> Result<String, String> {
    let v: serde_json::Value = http
        .get("https://meta.fabricmc.net/v2/versions/loader")
        .send()
        .await
        .map_err(|e| e.to_string())?
        .json()
        .await
        .map_err(|e| e.to_string())?;
    v.as_array()
        .and_then(|a| a.iter().find(|e| e["stable"].as_bool() == Some(true)))
        .and_then(|e| e["version"].as_str())
        .map(|s| s.to_string())
        .ok_or_else(|| "could not resolve a stable Fabric loader".to_string())
}

async fn fabric_latest_installer(http: &reqwest::Client) -> Result<String, String> {
    let v: serde_json::Value = http
        .get("https://meta.fabricmc.net/v2/versions/installer")
        .send()
        .await
        .map_err(|e| e.to_string())?
        .json()
        .await
        .map_err(|e| e.to_string())?;
    v.as_array()
        .and_then(|a| a.iter().find(|e| e["stable"].as_bool() == Some(true)))
        .and_then(|e| e["version"].as_str())
        .map(|s| s.to_string())
        .ok_or_else(|| "could not resolve a stable Fabric installer".to_string())
}

// ---------------------------------------------------------------------------
// Run
// ---------------------------------------------------------------------------

#[tauri::command]
pub async fn start_server(
    app: AppHandle,
    state: State<'_, AppState>,
    id: String,
) -> Result<(), String> {
    check_id(&id)?;
    if state.servers.running.lock().unwrap().contains_key(&id) {
        return Err("That server is already running.".to_string());
    }

    let dir = server_dir(&state, &id);
    let mut server = load_server(&dir).ok_or("no such server")?;

    if !server.eula_accepted {
        return Err("Accept the Minecraft EULA for this server first.".to_string());
    }

    let java = {
        let s = state.settings.lock().unwrap();
        s.default_java.executable_path.clone()
    }
    .ok_or("No Java configured — set one in Settings first.")?;

    write_properties(&dir, &server)?;
    let jar = ensure_jar(&app, &dir, &server).await?;

    server.last_started = Some(chrono::Utc::now().to_rfc3339());
    save_server(&dir, &server)?;

    let mut child: Child = Command::new(&java)
        .arg(format!("-Xms{}M", (server.max_memory_mb / 2).max(512)))
        .arg(format!("-Xmx{}M", server.max_memory_mb))
        .arg("-jar")
        .arg(&jar)
        .arg("nogui")
        .current_dir(&dir)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .map_err(|e| format!("could not start the server process: {e}"))?;

    let mut stdin = child.stdin.take().ok_or("server stdin was not piped")?;
    let stdout = child.stdout.take().ok_or("server stdout was not piped")?;
    let stderr = child.stderr.take().ok_or("server stderr was not piped")?;

    // Console input: commands typed in the UI are written to the process.
    let (in_tx, mut in_rx) = mpsc::unbounded_channel::<String>();
    tauri::async_runtime::spawn(async move {
        while let Some(line) = in_rx.recv().await {
            if stdin.write_all(format!("{line}\n").as_bytes()).await.is_err() {
                break;
            }
            let _ = stdin.flush().await;
        }
    });

    let (kill_tx, kill_rx) = tokio::sync::oneshot::channel();
    state.servers.running.lock().unwrap().insert(
        id.clone(),
        RunningServer { stdin: in_tx, kill: kill_tx, ready: false },
    );

    // Console output, both streams into the same event.
    for (stream, is_stdout) in [
        (
            Box::new(stdout) as Box<dyn tokio::io::AsyncRead + Unpin + Send>,
            true,
        ),
        (Box::new(stderr) as Box<dyn tokio::io::AsyncRead + Unpin + Send>, false),
    ] {
        let app_log = app.clone();
        let id_log = id.clone();
        tauri::async_runtime::spawn(async move {
            let mut lines = BufReader::new(stream).lines();
            while let Ok(Some(line)) = lines.next_line().await {
                // Vanilla prints `Done (1.234s)! For help, type "help"` once
                // it's accepting connections — the only reliable "ready" signal
                // the server gives, so it's what the UI waits on.
                if is_stdout && line.contains("Done (") && line.contains("For help") {
                    let st = app_log.state::<AppState>();
                    if let Some(r) = st.servers.running.lock().unwrap().get_mut(&id_log) {
                        r.ready = true;
                    }
                    let _ = app_log.emit("server-ready", id_log.clone());
                }
                let _ = app_log.emit(
                    "server-log",
                    serde_json::json!({ "serverId": id_log, "line": line }),
                );
            }
        });
    }

    // Supervisor: wait for exit or a stop request.
    let app_wait = app.clone();
    let id_wait = id.clone();
    tauri::async_runtime::spawn(async move {
        let status = tokio::select! {
            res = child.wait() => res.ok(),
            _ = kill_rx => {
                // `stop` already sent the console command; this is the fallback
                // for a server that ignored it.
                let _ = child.start_kill();
                child.wait().await.ok()
            }
        };

        let st = app_wait.state::<AppState>();
        st.servers.running.lock().unwrap().remove(&id_wait);
        let code = status.and_then(|s| s.code()).unwrap_or(-1);
        let _ = app_wait.emit(
            "server-stopped",
            serde_json::json!({ "serverId": id_wait, "exitCode": code }),
        );
    });

    Ok(())
}

/// Ask the server to shut down cleanly, so the world is saved.
#[tauri::command]
pub async fn stop_server(state: State<'_, AppState>, id: String) -> Result<(), String> {
    check_id(&id)?;
    let running = state.servers.running.lock().unwrap();
    let Some(r) = running.get(&id) else {
        return Err("That server isn't running.".to_string());
    };
    // `stop` rather than killing the process: Minecraft flushes chunks on this
    // command, and killing it instead is how worlds get corrupted.
    let _ = r.stdin.send("stop".to_string());
    Ok(())
}

/// Force-kill a server that won't respond to `stop`.
#[tauri::command]
pub async fn kill_server(state: State<'_, AppState>, id: String) -> Result<(), String> {
    check_id(&id)?;
    let mut running = state.servers.running.lock().unwrap();
    let Some(r) = running.remove(&id) else {
        return Err("That server isn't running.".to_string());
    };
    let _ = r.kill.send(());
    Ok(())
}

/// Send a console command (without a leading slash).
#[tauri::command]
pub async fn server_command(
    state: State<'_, AppState>,
    id: String,
    command: String,
) -> Result<(), String> {
    check_id(&id)?;
    let cmd = command.trim().trim_start_matches('/').to_string();
    if cmd.is_empty() {
        return Ok(());
    }
    let running = state.servers.running.lock().unwrap();
    let Some(r) = running.get(&id) else {
        return Err("That server isn't running.".to_string());
    };
    r.stdin.send(cmd).map_err(|_| "console is closed".to_string())
}

#[tauri::command]
pub async fn server_statuses(state: State<'_, AppState>) -> Result<Vec<ServerStatus>, String> {
    let lan = local_ip();
    let running = state.servers.running.lock().unwrap();
    Ok(running
        .iter()
        .map(|(id, r)| ServerStatus {
            id: id.clone(),
            running: true,
            ready: r.ready,
            lan_address: lan.clone(),
        })
        .collect())
}

/// Best-effort LAN IP.
///
/// Opens a UDP socket "to" a public address and reads back which local
/// interface the OS picked. No packet is ever sent — UDP connect only sets the
/// default peer — so this needs no network access and just asks the routing
/// table which interface would be used.
fn local_ip() -> Option<String> {
    let sock = std::net::UdpSocket::bind("0.0.0.0:0").ok()?;
    sock.connect("8.8.8.8:80").ok()?;
    sock.local_addr().ok().map(|a| a.ip().to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample() -> Server {
        Server {
            id: "x".into(),
            name: "Test".into(),
            mc_version: "1.21.1".into(),
            loader: Loader::Vanilla,
            port: 25577,
            max_memory_mb: 2048,
            created_at: "now".into(),
            last_started: None,
            eula_accepted: true,
            motd: "hi".into(),
            max_players: 8,
            gamemode: "creative".into(),
            difficulty: "peaceful".into(),
            online_mode: false,
            pvp: false,
        }
    }

    #[test]
    fn properties_preserve_unmanaged_keys_and_comments() {
        let dir = std::env::temp_dir().join(format!("blurred-props-{}", Uuid::new_v4()));
        std::fs::create_dir_all(&dir).unwrap();
        std::fs::write(
            dir.join("server.properties"),
            "#a comment\nview-distance=16\nmotd=old\n",
        )
        .unwrap();

        write_properties(&dir, &sample()).unwrap();
        let out = std::fs::read_to_string(dir.join("server.properties")).unwrap();

        assert!(out.contains("#a comment"), "comments must survive");
        assert!(out.contains("view-distance=16"), "unmanaged keys must survive");
        assert!(out.contains("motd=hi"), "managed keys must be replaced");
        assert!(!out.contains("motd=old"));
        assert!(out.contains("server-port=25577"), "missing keys appended");

        std::fs::remove_dir_all(&dir).ok();
    }

    #[test]
    fn rejects_ids_that_escape_the_servers_directory() {
        assert!(check_id("../etc").is_err());
        assert!(check_id("a/b").is_err());
        assert!(check_id("").is_err());
        assert!(check_id("2f8a1c").is_ok());
    }
}
