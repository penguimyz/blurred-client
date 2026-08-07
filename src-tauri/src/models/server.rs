use serde::{Deserialize, Serialize};

use super::instance::Loader;

/// A Minecraft server hosted on this machine.
///
/// Stored as `server.json` inside its own folder under `<data>/servers/<id>/`,
/// mirroring how instances work — one self-contained directory you can zip up,
/// back up or delete without touching anything else.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Server {
    pub id: String,
    pub name: String,
    pub mc_version: String,
    /// Vanilla or Fabric. Forge/NeoForge servers need their installer pipeline,
    /// which isn't built (same gap as the client side).
    pub loader: Loader,
    pub port: u16,
    /// Megabytes handed to the server JVM.
    pub max_memory_mb: u32,
    pub created_at: String,
    pub last_started: Option<String>,
    /// Whether the EULA has been accepted. The server will not start without
    /// it, and it is deliberately an explicit user action — see
    /// `commands::servers::accept_eula`.
    #[serde(default)]
    pub eula_accepted: bool,
    /// Subset of server.properties we expose. Everything else stays in the
    /// file for hand-editing.
    #[serde(default)]
    pub motd: String,
    #[serde(default = "default_max_players")]
    pub max_players: u32,
    #[serde(default)]
    pub gamemode: String,
    #[serde(default)]
    pub difficulty: String,
    #[serde(default)]
    pub online_mode: bool,
    #[serde(default)]
    pub pvp: bool,

    // ---- World and performance ----
    // All `#[serde(default = ...)]` so a server.json written before these
    // existed loads with vanilla's own defaults rather than zeroes, which
    // would silently set view-distance=0 on every existing server.
    #[serde(default = "default_view_distance")]
    pub view_distance: u32,
    #[serde(default = "default_simulation_distance")]
    pub simulation_distance: u32,
    #[serde(default = "default_spawn_protection")]
    pub spawn_protection: u32,
    /// World folder name. Changing it starts a different world rather than
    /// renaming the existing one — which is how vanilla behaves, and is worth
    /// saying out loud in the UI.
    #[serde(default = "default_level_name")]
    pub level_name: String,
    /// Blank means "let the game pick one".
    #[serde(default)]
    pub level_seed: String,

    // ---- Rules ----
    #[serde(default)]
    pub hardcore: bool,
    #[serde(default = "default_true")]
    pub allow_nether: bool,
    #[serde(default)]
    pub allow_flight: bool,
    #[serde(default)]
    pub enable_command_block: bool,
    #[serde(default)]
    pub force_gamemode: bool,
    /// Only whitelisted players may join. The whitelist itself is managed on
    /// the Players tab.
    #[serde(default)]
    pub white_list: bool,

    // ---- Launcher behaviour ----
    /// Bring the server back up if it exits without being asked to stop.
    #[serde(default)]
    pub auto_restart: bool,
    /// Snapshot the world before each start. Cheap insurance against the
    /// classic "started it with the wrong version and it converted my world".
    #[serde(default)]
    pub backup_on_start: bool,
}

fn default_max_players() -> u32 {
    10
}

fn default_true() -> bool {
    true
}

/// Vanilla's own defaults, so an unset field means "as shipped".
fn default_view_distance() -> u32 {
    10
}

fn default_simulation_distance() -> u32 {
    10
}

fn default_spawn_protection() -> u32 {
    16
}

fn default_level_name() -> String {
    "world".to_string()
}

/// Live status, computed rather than stored.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerStatus {
    pub id: String,
    pub running: bool,
    /// True once the log has reported "Done", i.e. it's accepting players.
    pub ready: bool,
    /// LAN address to hand to people on the same network.
    pub lan_address: Option<String>,
    /// Who's connected right now, tracked from the console log.
    #[serde(default)]
    pub players: Vec<String>,
    /// Seconds since the process started.
    #[serde(default)]
    pub uptime_seconds: u64,
}

/// One name on the ops, whitelist or ban list.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerPlayer {
    pub name: String,
    /// Dashed Mojang UUID. Empty for an offline-mode entry we couldn't resolve.
    #[serde(default)]
    pub uuid: String,
    /// Operator level 1–4, ops list only.
    #[serde(default)]
    pub level: Option<u8>,
    /// Ban reason, ban list only.
    #[serde(default)]
    pub reason: Option<String>,
}

/// Everyone on every list, for the Players tab.
#[derive(Debug, Clone, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerPlayers {
    pub ops: Vec<ServerPlayer>,
    pub whitelist: Vec<ServerPlayer>,
    pub banned: Vec<ServerPlayer>,
}

/// A world snapshot on disk.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerBackup {
    /// Filename inside the server's `backups/` folder; also its identity.
    pub file: String,
    pub created_at: String,
    pub size_bytes: u64,
}
