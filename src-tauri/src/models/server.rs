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
}

fn default_max_players() -> u32 {
    10
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
}
