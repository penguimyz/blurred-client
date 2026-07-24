use serde::{Deserialize, Serialize};
use uuid::Uuid;

use super::instance::{Loader, ModRef};

/// A saved, reusable mod set (spec §5.4). Modpacks are a local library here —
/// created from any instance, applied to new instances, and shared as a single
/// self-contained `.bpack` file (metadata + the mod jars themselves, base64'd
/// in). Importing a pack from a Modrinth/CurseForge listing would need the
/// live API and is out of scope; this is the offline, user-authored half.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Modpack {
    pub id: Uuid,
    pub name: String,
    pub description: String,
    pub mc_version: String,
    pub loader: Loader,
    pub mods: Vec<ModRef>, // snapshot of the source instance's mod list
    pub created_at: chrono::DateTime<chrono::Utc>,
}

/// On-the-wire shape of an exported `.bpack` file: the pack metadata plus every
/// mod jar inlined as base64, so a single file is fully self-contained and can
/// be handed to someone else without any network round-trip on import.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BpackFile {
    pub format: String, // "blurred-modpack/1"
    pub modpack: Modpack,
    pub files: Vec<BpackEntry>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BpackEntry {
    pub filename: String,
    pub data_b64: String,
}
