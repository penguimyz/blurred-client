use serde::{Deserialize, Serialize};

use super::instance::{CustomCommands, EnvVars, JavaSettings};

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum Theme {
    Light,
    Dark,
    System,
}

/// The launcher's look, matching the mod's menu styles: Ocean is the pixel-art
/// water theme, Modern is the mod's flat dark look with the blue accent.
#[derive(Debug, Clone, Copy, Default, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum UiStyle {
    #[default]
    Ocean,
    Modern,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GlobalSettings {
    pub theme: Theme,
    /// `#[serde(default)]` so settings.json files from before this field load as Ocean.
    #[serde(default)]
    pub ui_style: UiStyle,
    pub accent_color: String, // hex, e.g. "#7C9CFF"
    pub default_java: JavaSettings,
    pub default_env_vars: EnvVars,
    pub default_custom_commands: CustomCommands,
    // Modrinth-only for now -- CurseForge was pulled (no API key, and the
    // per-platform toggle from spec Section 5.3 isn't worth the complexity
    // until there's a second real source to switch to).
    pub instance_storage_path: String,
    pub update_check_frequency_minutes: u32,
    // Azure app (client) ID used for Microsoft/Xbox sign-in. Public identifier,
    // not a secret. Seeded with a default dev app; a user can point it at their
    // own Azure registration. `#[serde(default)]` so pre-existing settings.json
    // files (written before this field) still load.
    #[serde(default = "default_msa_client_id")]
    pub msa_client_id: String,
    // GitHub "owner/name" to check for launcher updates against; empty disables
    // the check. Defaults to the project's own repo so updates work out of the
    // box — a launcher that only updates if you first go and type its own
    // address into settings is a launcher that never updates.
    #[serde(default = "default_update_repo")]
    pub update_repo: String,
    /// Download and install a newer release on startup, rather than only
    /// mentioning it. Skipped while a game or server is running — see
    /// `UpdateBanner` on the frontend, which owns that decision.
    #[serde(default = "default_true")]
    pub auto_install_updates: bool,

    // ---- Chat (Sonar) ----
    // IRC endpoint. Empty values fall back to the constants in commands::chat,
    // so a settings.json written before these fields existed still connects to
    // the default network rather than to "":0.
    #[serde(default = "default_chat_server")]
    pub chat_server: String,
    #[serde(default = "default_chat_port")]
    pub chat_port: u16,
    #[serde(default = "default_chat_channel")]
    pub chat_channel: String,
    /// Connect to chat automatically once an account is available.
    #[serde(default)]
    pub chat_auto_connect: bool,

    // ---- Discord ----
    /// Discord application ID for the in-game rich presence ("Blurred Client —
    /// On a server"). Empty disables it.
    ///
    /// Defaults to the project's own registered application, so the status
    /// works with nothing to configure. It has to be *some* specific
    /// application rather than a generic identifier, because Discord takes the
    /// bold first line of the status from that application's name — which is
    /// also why pointing this at your own app renames the status to whatever
    /// you called it.
    ///
    /// Passed to every instance at launch as a JVM property, so it's set once
    /// here rather than per instance.
    #[serde(default = "default_discord_app_id")]
    pub discord_app_id: String,
    /// Show "In the launcher" on Discord while the launcher is open and no
    /// game is running. Yields to the in-game mod the moment one starts —
    /// Discord shows one activity per application, so they take turns rather
    /// than overwrite each other. See `commands::discord`.
    #[serde(default = "default_true")]
    pub discord_launcher_presence: bool,

    // ---- Cosmetic ----
    /// The cursor-following school of fish. Off by default — it's a toy, and an
    /// animation that tracks the pointer everywhere should be opt-in.
    #[serde(default)]
    pub fish_enabled: bool,
    /// Ambient sea life drifting past behind the glass. On by default: unlike
    /// the cursor fish it's background scenery rather than a pointer-tracking
    /// toy, and it's what makes the theme feel like water rather than a
    /// gradient. `default_true` (not `#[serde(default)]`) so an existing
    /// settings.json written before this field gets it switched on.
    #[serde(default = "default_true")]
    pub sea_life_enabled: bool,
}

fn default_true() -> bool {
    true
}

pub fn default_chat_server() -> String {
    "irc.libera.chat".to_string()
}

pub fn default_chat_port() -> u16 {
    6697
}

pub fn default_chat_channel() -> String {
    "#blurred-client".to_string()
}

/// Bioluminescent cyan — the ocean theme's accent. Must stay in sync with the
/// `--accent` fallback in styles/theme.css.
pub const DEFAULT_ACCENT: &str = "#35E0D0";

/// The accent shipped before the ocean theme. Anyone still carrying this value
/// never picked it (it was the old default), so it gets migrated on load —
/// otherwise every existing install would keep a purple accent that clashes
/// with the new palette. A genuinely chosen custom accent is left alone.
pub const LEGACY_DEFAULT_ACCENT: &str = "#7C9CFF";

pub fn default_msa_client_id() -> String {
    "c9c50f80-25c7-4dfd-ba2c-215636be66c4".to_string()
}

/// Where releases come from. Same repo the source lives in.
pub fn default_update_repo() -> String {
    "penguimyz/blurred-client".to_string()
}

/// The project's Discord application. Public identifier, not a secret — it's
/// broadcast to every Discord client that renders the status.
pub fn default_discord_app_id() -> String {
    "1535178618907664494".to_string()
}

impl Default for GlobalSettings {
    fn default() -> Self {
        Self {
            theme: Theme::Dark,
            ui_style: UiStyle::Ocean,
            accent_color: DEFAULT_ACCENT.to_string(),
            default_java: JavaSettings {
                executable_path: None,
                min_memory_mb: 1024,
                max_memory_mb: 4096,
                jvm_args: String::new(),
            },
            default_env_vars: EnvVars::default(),
            default_custom_commands: CustomCommands::default(),
            instance_storage_path: String::new(), // resolved at runtime via `directories`
            update_check_frequency_minutes: 60,
            msa_client_id: default_msa_client_id(),
            update_repo: default_update_repo(),
            auto_install_updates: true,
            chat_server: default_chat_server(),
            chat_port: default_chat_port(),
            chat_channel: default_chat_channel(),
            chat_auto_connect: false,
            discord_app_id: default_discord_app_id(),
            discord_launcher_presence: true,
            fish_enabled: false,
            sea_life_enabled: true,
        }
    }
}

