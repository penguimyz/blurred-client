use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::Mutex;

use directories::ProjectDirs;
use tokio::sync::oneshot;

use crate::commands::bridge::BridgeState;
use crate::commands::capes::CapeState;
use crate::commands::servers::ServerState;
use crate::commands::chat::ChatState;
use crate::models::account::Account;
use crate::models::settings::GlobalSettings;

/// Root app state, held by Tauri and accessed from commands via `State<AppState>`.
pub struct AppState {
    pub data_dir: PathBuf,     // .../BlurredClient/  (settings.json, accounts.json, cache)
    pub instances_dir: PathBuf, // .../BlurredClient/instances/<folder_name>/
    pub settings: Mutex<GlobalSettings>,
    pub accounts: Mutex<Vec<Account>>,
    /// Instance id -> a one-shot kill switch for its running game process.
    /// `launch_instance` inserts one before spawning and removes it on exit;
    /// `kill_instance` takes it out and fires it to stop the game. Presence in
    /// this map is also how the frontend-facing "is it running" question is
    /// answered (see `list_running`).
    pub running: Mutex<HashMap<String, oneshot::Sender<()>>>,
    /// Live IRC chat connection (see `commands::chat`). Empty until the user
    /// connects; the socket itself lives in a background task, and this holds
    /// only the outbound channel plus enough bookkeeping to answer "are we
    /// connected, and as whom".
    pub chat: Mutex<ChatState>,
    /// Loopback socket the in-game Blurred mod connects to (see
    /// `commands::bridge`). Not behind a Mutex: the port and token are fixed
    /// for the process lifetime and the broadcast sender is already shareable.
    pub bridge: BridgeState,
    /// Custom capes: our library's active pick, plus everyone else's capes
    /// received over IRC (see `commands::capes`).
    pub capes: CapeState,
    /// Minecraft servers hosted from the launcher (see `commands::servers`).
    pub servers: ServerState,
}

impl AppState {
    pub fn init() -> anyhow::Result<Self> {
        let proj = ProjectDirs::from("dev", "blurredclient", "BlurredClient")
            .ok_or_else(|| anyhow::anyhow!("could not resolve a platform data directory"))?;

        let data_dir = proj.data_dir().to_path_buf();
        let instances_dir = data_dir.join("instances");
        std::fs::create_dir_all(&instances_dir)?;

        let settings_path = data_dir.join("settings.json");
        let settings = if settings_path.exists() {
            let raw = std::fs::read_to_string(&settings_path)?;
            let mut s: GlobalSettings = serde_json::from_str(&raw).unwrap_or_default();
            // One-time accent migration onto the ocean palette. See
            // LEGACY_DEFAULT_ACCENT for why this is safe to do silently.
            if s.accent_color.eq_ignore_ascii_case(crate::models::settings::LEGACY_DEFAULT_ACCENT) {
                s.accent_color = crate::models::settings::DEFAULT_ACCENT.to_string();
            }
            s
        } else {
            let s = GlobalSettings {
                instance_storage_path: instances_dir.to_string_lossy().to_string(),
                ..Default::default()
            };
            std::fs::write(&settings_path, serde_json::to_string_pretty(&s)?)?;
            s
        };

        let accounts_path = data_dir.join("accounts.json");
        let accounts: Vec<Account> = if accounts_path.exists() {
            let raw = std::fs::read_to_string(&accounts_path)?;
            serde_json::from_str(&raw).unwrap_or_default()
        } else {
            Vec::new()
        };

        Ok(Self {
            data_dir,
            instances_dir,
            settings: Mutex::new(settings),
            accounts: Mutex::new(accounts),
            running: Mutex::new(HashMap::new()),
            chat: Mutex::new(ChatState::default()),
            bridge: BridgeState::default(),
            capes: CapeState::default(),
            servers: ServerState::default(),
        })
    }

    pub fn settings_path(&self) -> PathBuf {
        self.data_dir.join("settings.json")
    }

    pub fn persist_settings(&self) -> anyhow::Result<()> {
        let s = self.settings.lock().unwrap();
        std::fs::write(self.settings_path(), serde_json::to_string_pretty(&*s)?)?;
        Ok(())
    }
}

pub fn persist_accounts(data_dir: &PathBuf, accounts: &[Account]) -> anyhow::Result<()> {
    let path = data_dir.join("accounts.json");
    std::fs::write(path, serde_json::to_string_pretty(accounts)?)?;
    Ok(())
}

