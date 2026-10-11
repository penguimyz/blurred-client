//! A local skin library: make one, keep it, wear it.
//!
//! # How this differs from capes
//!
//! Capes are ours end to end — Mojang won't hand them out, so `commands::capes`
//! ships the bytes between players itself. Skins are the opposite: Mojang
//! already has an API for them, every client renders them, and everyone sees
//! them. So nothing here goes near the network. This is storage, and the last
//! step of "wear it" is the existing
//! [`crate::commands::online_auth::set_account_skin_file`], which uploads the
//! file to Mojang against the signed-in account.
//!
//! That split is why a saved skin has a **path** as well as an id: the upload
//! command takes a filesystem path, so the frontend needs to be able to name
//! the file it just saved rather than round-tripping the bytes back out.
//!
//! # The variant is part of the skin
//!
//! Classic and slim models differ by an arm that's four pixels wide instead of
//! three, and the texture is drawn to match. A slim skin worn on a classic
//! model has a one-pixel stripe of arm that samples the wrong column, so the
//! variant travels with the artwork rather than being asked for at upload time.

use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};
use tauri::State;

use crate::state::AppState;
use crate::util;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Skin {
    pub id: String,
    pub name: String,
    pub created_at: String,
    /// "classic" (4px arms) or "slim" (3px arms).
    pub variant: String,
    /// Absolute path to the PNG, so `set_account_skin_file` can take it
    /// straight from here.
    pub path: String,
}

fn skins_dir(data_dir: &Path) -> PathBuf {
    data_dir.join("skins")
}

fn index_path(data_dir: &Path) -> PathBuf {
    skins_dir(data_dir).join("skins.json")
}

fn skin_png_path(data_dir: &Path, id: &str) -> PathBuf {
    skins_dir(data_dir).join(format!("{id}.png"))
}

/// The index as stored: everything except `path`, which is derived on read.
///
/// Keeping the path out of the file matters — it's absolute, and the data
/// directory moves between machines and between a portable install and an
/// installed one. Storing it would bake in a location that stops being true.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct StoredSkin {
    id: String,
    name: String,
    created_at: String,
    #[serde(default = "default_variant")]
    variant: String,
}

fn default_variant() -> String {
    "classic".to_string()
}

fn load_index(data_dir: &Path) -> Vec<StoredSkin> {
    std::fs::read_to_string(index_path(data_dir))
        .ok()
        .and_then(|raw| serde_json::from_str(&raw).ok())
        .unwrap_or_default()
}

fn save_index(data_dir: &Path, skins: &[StoredSkin]) -> Result<(), String> {
    std::fs::create_dir_all(skins_dir(data_dir)).map_err(|e| e.to_string())?;
    let raw = serde_json::to_string_pretty(skins).map_err(|e| e.to_string())?;
    std::fs::write(index_path(data_dir), raw).map_err(|e| e.to_string())
}

fn hydrate(data_dir: &Path, stored: Vec<StoredSkin>) -> Vec<Skin> {
    stored
        .into_iter()
        .map(|s| Skin {
            path: skin_png_path(data_dir, &s.id).to_string_lossy().to_string(),
            id: s.id,
            name: s.name,
            created_at: s.created_at,
            variant: s.variant,
        })
        .collect()
}

#[tauri::command]
pub async fn list_skins(state: State<'_, AppState>) -> Result<Vec<Skin>, String> {
    Ok(hydrate(&state.data_dir, load_index(&state.data_dir)))
}

#[tauri::command]
pub async fn save_skin(
    state: State<'_, AppState>,
    name: String,
    variant: String,
    data: String,
) -> Result<Vec<Skin>, String> {
    let bytes = util::base64_decode(&data).map_err(|_| "that isn't valid base64 PNG data")?;
    // Same shallow check `save_cape` does: enough to catch a wrong file, and
    // the real validation is Mojang's when it's uploaded.
    if bytes.len() < 8 || &bytes[1..4] != b"PNG" {
        return Err("that isn't a PNG".to_string());
    }

    let id = uuid::Uuid::new_v4().to_string();
    std::fs::create_dir_all(skins_dir(&state.data_dir)).map_err(|e| e.to_string())?;
    std::fs::write(skin_png_path(&state.data_dir, &id), &bytes).map_err(|e| e.to_string())?;

    let mut skins = load_index(&state.data_dir);
    skins.push(StoredSkin {
        id,
        name: name.trim().to_string(),
        created_at: chrono::Utc::now().to_rfc3339(),
        variant: if variant == "slim" { "slim" } else { "classic" }.to_string(),
    });
    save_index(&state.data_dir, &skins)?;
    Ok(hydrate(&state.data_dir, skins))
}

#[tauri::command]
pub async fn delete_skin(state: State<'_, AppState>, id: String) -> Result<Vec<Skin>, String> {
    // Same guard as `delete_cape`: the id becomes a filename, so anything that
    // could climb out of the directory is refused rather than sanitised.
    if id.contains(['/', '\\', '.']) {
        return Err("invalid skin id".to_string());
    }
    let _ = std::fs::remove_file(skin_png_path(&state.data_dir, &id));

    let mut skins = load_index(&state.data_dir);
    skins.retain(|s| s.id != id);
    save_index(&state.data_dir, &skins)?;
    Ok(hydrate(&state.data_dir, skins))
}

/// The PNG as base64, for the editor and the 3D preview.
#[tauri::command]
pub async fn read_skin(state: State<'_, AppState>, id: String) -> Result<String, String> {
    if id.contains(['/', '\\', '.']) {
        return Err("invalid skin id".to_string());
    }
    let bytes = std::fs::read(skin_png_path(&state.data_dir, &id))
        .map_err(|e| format!("could not read that skin: {e}"))?;
    Ok(util::base64_encode(&bytes))
}

/// Rename in place, without rewriting the PNG.
///
/// Unlike a cape — which is content-addressed on the wire, so editing one makes
/// a genuinely different cape — a skin's identity is local and its name is just
/// a label. Renaming shouldn't churn the file or lose the id an account might
/// be pointing at.
#[tauri::command]
pub async fn rename_skin(
    state: State<'_, AppState>,
    id: String,
    name: String,
) -> Result<Vec<Skin>, String> {
    let mut skins = load_index(&state.data_dir);
    let Some(skin) = skins.iter_mut().find(|s| s.id == id) else {
        return Err("no such skin".to_string());
    };
    skin.name = name.trim().to_string();
    save_index(&state.data_dir, &skins)?;
    Ok(hydrate(&state.data_dir, skins))
}

/// Replace a skin's pixels, keeping its id, name and place in the library.
#[tauri::command]
pub async fn update_skin(
    state: State<'_, AppState>,
    id: String,
    variant: String,
    data: String,
) -> Result<Vec<Skin>, String> {
    if id.contains(['/', '\\', '.']) {
        return Err("invalid skin id".to_string());
    }
    let bytes = util::base64_decode(&data).map_err(|_| "that isn't valid base64 PNG data")?;
    if bytes.len() < 8 || &bytes[1..4] != b"PNG" {
        return Err("that isn't a PNG".to_string());
    }

    let mut skins = load_index(&state.data_dir);
    let Some(skin) = skins.iter_mut().find(|s| s.id == id) else {
        return Err("no such skin".to_string());
    };
    skin.variant = if variant == "slim" { "slim" } else { "classic" }.to_string();

    std::fs::write(skin_png_path(&state.data_dir, &id), &bytes).map_err(|e| e.to_string())?;
    save_index(&state.data_dir, &skins)?;
    Ok(hydrate(&state.data_dir, skins))
}
