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
}

pub fn default_msa_client_id() -> String {
    "c9c50f80-25c7-4dfd-ba2c-215636be66c4".to_string()
}

impl Default for GlobalSettings {
    fn default() -> Self {
        Self {
            theme: Theme::Dark,
            accent_color: "#7C9CFF".to_string(),
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
        }
    }
}

