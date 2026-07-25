use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::Mutex;

use directories::ProjectDirs;
use tokio::sync::oneshot;

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
            serde_json::from_str(&raw).unwrap_or_default()
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

