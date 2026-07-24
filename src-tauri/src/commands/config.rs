// Per-mod config editing (Phase 3). Mods drop their config files under
// `<instance>/config/`, in a mix of formats (`.toml` for Forge/NeoForge,
// `.json`/`.json5` for Fabric mods, `.properties` for a few). We enumerate
// them here and hand raw text back and forth; the "friendly form vs. raw
// editor" decision (spec 5.1) is made on the frontend from `format`, which is
// just sniffed from the extension. Anything we don't recognize still round-
// trips as raw text, so no config is ever un-editable.

use std::path::{Path, PathBuf};

use serde::Serialize;
use tauri::State;

use crate::commands::instance::find_instance;
use crate::state::AppState;

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ConfigFile {
    /// Path relative to the instance's `config/` dir, e.g. "sodium-options.json"
    /// or "forge/chunkloading.toml". Also the key for read/write.
    pub rel_path: String,
    pub format: String, // "json" | "toml" | "properties" | "text"
    pub size: u64,
}

fn config_dir(instance_dir: &Path) -> PathBuf {
    instance_dir.join("config")
}

fn sniff_format(path: &Path) -> String {
    match path.extension().and_then(|e| e.to_str()).map(|e| e.to_ascii_lowercase()).as_deref() {
        Some("json") | Some("json5") => "json",
        Some("toml") => "toml",
        Some("properties") | Some("cfg") | Some("conf") => "properties",
        _ => "text",
    }
    .to_string()
}

/// Walk `config/` recursively, collecting editable text files. Directories a
/// few levels deep are normal (some mods nest per-feature configs), so this
/// recurses rather than doing a flat read.
fn collect(dir: &Path, base: &Path, out: &mut Vec<ConfigFile>) {
    let Ok(entries) = std::fs::read_dir(dir) else { return };
    for entry in entries.flatten() {
        let path = entry.path();
        if path.is_dir() {
            collect(&path, base, out);
        } else if path.is_file() {
            let rel = path.strip_prefix(base).unwrap_or(&path);
            let size = std::fs::metadata(&path).map(|m| m.len()).unwrap_or(0);
            out.push(ConfigFile {
                rel_path: rel.to_string_lossy().replace('\\', "/"),
                format: sniff_format(&path),
                size,
            });
        }
    }
}

#[tauri::command]
pub async fn list_config_files(
    state: State<'_, AppState>,
    instance_id: String,
) -> Result<Vec<ConfigFile>, String> {
    let (dir, _) = find_instance(&state, &instance_id)?;
    let base = config_dir(&dir);
    let mut out = Vec::new();
    if base.is_dir() {
        collect(&base, &base, &mut out);
    }
    out.sort_by(|a, b| a.rel_path.to_lowercase().cmp(&b.rel_path.to_lowercase()));
    Ok(out)
}

/// Resolve a caller-supplied relative path against `config/`, refusing anything
/// that would escape the directory (`..`, absolute paths). The config editor is
/// the one place we take a path from the frontend, so this guard matters.
fn safe_config_path(instance_dir: &Path, rel_path: &str) -> Result<PathBuf, String> {
    let rel = Path::new(rel_path);
    if rel.is_absolute() || rel.components().any(|c| matches!(c, std::path::Component::ParentDir)) {
        return Err("invalid config path".to_string());
    }
    Ok(config_dir(instance_dir).join(rel))
}

#[tauri::command]
pub async fn read_config_file(
    state: State<'_, AppState>,
    instance_id: String,
    rel_path: String,
) -> Result<String, String> {
    let (dir, _) = find_instance(&state, &instance_id)?;
    let path = safe_config_path(&dir, &rel_path)?;
    std::fs::read_to_string(&path).map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn write_config_file(
    state: State<'_, AppState>,
    instance_id: String,
    rel_path: String,
    contents: String,
) -> Result<(), String> {
    let (dir, _) = find_instance(&state, &instance_id)?;
    let path = safe_config_path(&dir, &rel_path)?;
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
    }
    std::fs::write(&path, contents).map_err(|e| e.to_string())
}
