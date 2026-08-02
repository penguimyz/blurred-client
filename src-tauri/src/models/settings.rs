use serde::{Deserialize, Serialize};

use super::instance::{CustomCommands, EnvVars, JavaSettings};

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum Theme {
    Light,
    Dark,
    System,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GlobalSettings {
    pub theme: Theme,
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
    // the check. Auto-install isn't wired (needs signed releases) — this powers
    // the "check for updates" notice only.
    #[serde(default)]
    pub update_repo: String,

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

impl Default for GlobalSettings {
    fn default() -> Self {
        Self {
            theme: Theme::Dark,
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
            update_repo: String::new(),
            chat_server: default_chat_server(),
            chat_port: default_chat_port(),
            chat_channel: default_chat_channel(),
            chat_auto_connect: false,
            fish_enabled: false,
            sea_life_enabled: true,
        }
    }
}

