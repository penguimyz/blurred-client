use serde::{Deserialize, Serialize};
use uuid::Uuid;

/// The generic override pattern: global default -> per-instance override.
/// Used for Java, EnvVars, and CustomCommands per spec section 7.
/// Build once, reuse three times, instead of three bespoke copies.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Overridable<T> {
    pub enabled: bool, // false = inherit global default, true = use `value`
    pub value: T,
}

impl<T: Default> Default for Overridable<T> {
    fn default() -> Self {
        Self {
            enabled: false,
            value: T::default(),
        }
    }
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum Loader {
    Vanilla,
    Fabric,
    Forge,
    Quilt,
    NeoForge,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JavaSettings {
    pub executable_path: Option<String>, // None = auto-detect
    pub min_memory_mb: u32,
    pub max_memory_mb: u32,
    pub jvm_args: String,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct EnvVars {
    pub vars: Vec<(String, String)>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CustomCommands {
    pub pre_launch: Option<String>,
    pub wrapper: Option<String>,
    pub post_exit: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ModRef {
    pub id: String,          // Modrinth project id, or local hash if side-loaded
    pub filename: String,
    pub name: String,
    pub version: String,     // human version number (e.g. "0.5.11")
    pub enabled: bool,       // false = renamed to .disabled on disk
    pub source: ModSource,
    pub pinned: bool,        // exclude from auto-update
    // Modrinth version id of the installed file, used to detect updates
    // (latest version id != this one). None for local mods and for older
    // instance.json files written before this field existed.
    #[serde(default)]
    pub version_id: Option<String>,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum ModSource {
    Modrinth,
    Local,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Instance {
    pub id: Uuid,
    pub name: String,
    pub icon_path: Option<String>,
    pub mc_version: String,
    pub loader: Loader,
    pub loader_version: Option<String>,

    pub created_at: chrono::DateTime<chrono::Utc>,
    pub last_played: Option<chrono::DateTime<chrono::Utc>>,
    pub total_playtime_seconds: u64,

    pub java_override: Overridable<JavaSettings>,
    pub env_vars_override: Overridable<EnvVars>,
    pub custom_commands_override: Overridable<CustomCommands>,

    pub account_id: Option<Uuid>, // per-instance account assignment
    pub mods: Vec<ModRef>,
    pub notes: String,

    pub window_width: u32,
    pub window_height: u32,
}

impl Instance {
    pub fn new(name: String, mc_version: String, loader: Loader) -> Self {
        Self {
            id: Uuid::new_v4(),
            name,
            icon_path: None,
            mc_version,
            loader,
            loader_version: None,
            created_at: chrono::Utc::now(),
            last_played: None,
            total_playtime_seconds: 0,
            java_override: Overridable::default(),
            env_vars_override: Overridable::default(),
            custom_commands_override: Overridable::default(),
            account_id: None,
            mods: Vec::new(),
            notes: String::new(),
            window_width: 854,
            window_height: 480,
        }
    }

    /// Directory-safe slug, since instance names can contain spaces/unicode
    /// but we still want readable folder names, not just the UUID.
    pub fn folder_name(&self) -> String {
        let slug: String = self
            .name
            .chars()
            .map(|c| if c.is_alphanumeric() || c == '-' || c == '_' { c } else { '_' })
            .collect();
        format!("{}-{}", slug, &self.id.to_string()[..8])
    }
}
