// Worlds/Saves and Screenshots tabs (spec 4.2). Both are just structured views
// over folders inside the instance: `saves/<world>/` and `screenshots/*.png`.
// No game data is parsed (level.dat is binary NBT — the folder name is the
// world name for our purposes); we surface names, sizes, and timestamps, plus
// on-demand base64 for rendering screenshot thumbnails in the webview without
// wiring up the asset:// protocol scope.

use std::path::Path;

use serde::Serialize;
use tauri::State;

use crate::commands::instance::find_instance;
use crate::state::AppState;

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct WorldInfo {
    pub name: String,      // folder name under saves/
    pub size_bytes: u64,   // total on-disk size, summed recursively
    pub modified: Option<String>, // ISO 8601, newest file in the folder
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ScreenshotInfo {
    pub name: String,
    pub size_bytes: u64,
    pub modified: Option<String>,
}

fn iso(t: std::time::SystemTime) -> String {
    chrono::DateTime::<chrono::Utc>::from(t).to_rfc3339()
}

fn dir_size(dir: &Path) -> u64 {
    let mut total = 0;
    if let Ok(entries) = std::fs::read_dir(dir) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                total += dir_size(&path);
            } else if let Ok(meta) = std::fs::metadata(&path) {
                total += meta.len();
            }
        }
    }
    total
}

#[tauri::command]
pub async fn list_worlds(
    state: State<'_, AppState>,
    instance_id: String,
) -> Result<Vec<WorldInfo>, String> {
    let (dir, _) = find_instance(&state, &instance_id)?;
    let saves = dir.join("saves");
    let mut out = Vec::new();
    if saves.is_dir() {
        for entry in std::fs::read_dir(&saves).map_err(|e| e.to_string())?.flatten() {
            let path = entry.path();
            if path.is_dir() {
                let modified = std::fs::metadata(&path).and_then(|m| m.modified()).ok().map(iso);
                out.push(WorldInfo {
                    name: entry.file_name().to_string_lossy().to_string(),
                    size_bytes: dir_size(&path),
                    modified,
                });
            }
        }
    }
    out.sort_by(|a, b| b.modified.cmp(&a.modified));
    Ok(out)
}

#[tauri::command]
pub async fn delete_world(
    state: State<'_, AppState>,
    instance_id: String,
    name: String,
) -> Result<(), String> {
    let (dir, _) = find_instance(&state, &instance_id)?;
    // Guard: no traversal, must be a direct child of saves/.
    if name.is_empty() || name.contains('/') || name.contains('\\') || name.contains("..") {
        return Err("invalid world name".to_string());
    }
    let target = dir.join("saves").join(&name);
    if target.is_dir() {
        std::fs::remove_dir_all(&target).map_err(|e| e.to_string())?;
    }
    Ok(())
}

const IMAGE_EXTS: &[&str] = &["png", "jpg", "jpeg", "webp", "gif"];

#[tauri::command]
pub async fn list_screenshots(
    state: State<'_, AppState>,
    instance_id: String,
) -> Result<Vec<ScreenshotInfo>, String> {
    let (dir, _) = find_instance(&state, &instance_id)?;
    let shots = dir.join("screenshots");
    let mut out = Vec::new();
    if shots.is_dir() {
        for entry in std::fs::read_dir(&shots).map_err(|e| e.to_string())?.flatten() {
            let path = entry.path();
            let is_image = path
                .extension()
                .and_then(|e| e.to_str())
                .map(|e| IMAGE_EXTS.contains(&e.to_ascii_lowercase().as_str()))
                .unwrap_or(false);
            if path.is_file() && is_image {
                let meta = std::fs::metadata(&path).map_err(|e| e.to_string())?;
                out.push(ScreenshotInfo {
                    name: entry.file_name().to_string_lossy().to_string(),
                    size_bytes: meta.len(),
                    modified: meta.modified().ok().map(iso),
                });
            }
        }
    }
    out.sort_by(|a, b| b.modified.cmp(&a.modified));
    Ok(out)
}

/// Return a screenshot as a `data:` URL so the webview `<img>` can render it
/// directly. Lazy per-image (the gallery only asks for what's on screen), which
/// keeps us from base64-ing an entire screenshots folder up front.
#[tauri::command]
pub async fn read_screenshot_data(
    state: State<'_, AppState>,
    instance_id: String,
    name: String,
) -> Result<String, String> {
    let (dir, _) = find_instance(&state, &instance_id)?;
    if name.is_empty() || name.contains('/') || name.contains('\\') || name.contains("..") {
        return Err("invalid screenshot name".to_string());
    }
    let path = dir.join("screenshots").join(&name);
    let bytes = std::fs::read(&path).map_err(|e| e.to_string())?;
    let mime = match path.extension().and_then(|e| e.to_str()).map(|e| e.to_ascii_lowercase()).as_deref() {
        Some("jpg") | Some("jpeg") => "image/jpeg",
        Some("webp") => "image/webp",
        Some("gif") => "image/gif",
        _ => "image/png",
    };
    Ok(format!("data:{mime};base64,{}", crate::util::base64_encode(&bytes)))
}
