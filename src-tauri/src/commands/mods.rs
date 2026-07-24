// Per-instance mod file management (Phase 3, the parts that don't need the
// Modrinth API): enable/disable via Prism-style `.disabled` renaming, drag-drop
// side-loading of local .jars, removal, and version-pin toggling.
//
// The instance's `mods` array in instance.json is the source of truth for the
// UI; the files under `<instance>/mods/` are the source of truth for the game.
// Every command here keeps the two in sync and returns the updated Instance so
// the frontend can refresh in one round-trip.

use std::path::{Path, PathBuf};

use sha1::{Digest, Sha1};
use tauri::State;

use crate::commands::instance::{find_instance, save_instance};
use crate::models::instance::{Instance, ModRef, ModSource};
use crate::state::AppState;

fn mods_dir(instance_dir: &Path) -> PathBuf {
    instance_dir.join("mods")
}

/// A disabled mod lives on disk as `<filename>.disabled` (Prism's convention),
/// so the game's loader skips it without us having to delete anything.
fn disabled_name(filename: &str) -> String {
    format!("{filename}.disabled")
}

#[tauri::command]
pub async fn set_mod_enabled(
    state: State<'_, AppState>,
    instance_id: String,
    filename: String,
    enabled: bool,
) -> Result<Instance, String> {
    let (dir, mut instance) = find_instance(&state, &instance_id)?;
    let mods = mods_dir(&dir);
    let active = mods.join(&filename);
    let disabled = mods.join(disabled_name(&filename));

    // Rename toward the desired state. If the file is already in the target
    // state (or missing entirely — e.g. added to the list but not yet on disk)
    // we don't error, we just fix up the metadata below.
    if enabled {
        if disabled.exists() && !active.exists() {
            std::fs::rename(&disabled, &active).map_err(|e| e.to_string())?;
        }
    } else if active.exists() && !disabled.exists() {
        std::fs::rename(&active, &disabled).map_err(|e| e.to_string())?;
    }

    let m = instance
        .mods
        .iter_mut()
        .find(|m| m.filename == filename)
        .ok_or_else(|| format!("mod {filename} not tracked on this instance"))?;
    m.enabled = enabled;

    save_instance(&dir, &instance).map_err(|e| e.to_string())?;
    Ok(instance)
}

#[tauri::command]
pub async fn set_mod_pinned(
    state: State<'_, AppState>,
    instance_id: String,
    filename: String,
    pinned: bool,
) -> Result<Instance, String> {
    let (dir, mut instance) = find_instance(&state, &instance_id)?;
    let m = instance
        .mods
        .iter_mut()
        .find(|m| m.filename == filename)
        .ok_or_else(|| format!("mod {filename} not tracked on this instance"))?;
    m.pinned = pinned;
    save_instance(&dir, &instance).map_err(|e| e.to_string())?;
    Ok(instance)
}

#[tauri::command]
pub async fn remove_mod(
    state: State<'_, AppState>,
    instance_id: String,
    filename: String,
) -> Result<Instance, String> {
    let (dir, mut instance) = find_instance(&state, &instance_id)?;
    let mods = mods_dir(&dir);
    // Remove whichever on-disk form exists; ignore "already gone".
    for candidate in [mods.join(&filename), mods.join(disabled_name(&filename))] {
        if candidate.exists() {
            std::fs::remove_file(&candidate).map_err(|e| e.to_string())?;
        }
    }
    instance.mods.retain(|m| m.filename != filename);
    save_instance(&dir, &instance).map_err(|e| e.to_string())?;
    Ok(instance)
}

/// Side-load a local `.jar` by copying it into the instance's `mods/` folder.
/// The frontend gets `source_path` from a Tauri drag-drop event (no file-dialog
/// plugin needed — the webview's drag-drop hands us a real filesystem path).
#[tauri::command]
pub async fn add_local_mod(
    state: State<'_, AppState>,
    instance_id: String,
    source_path: String,
) -> Result<Instance, String> {
    let src = PathBuf::from(&source_path);
    let ext = src
        .extension()
        .and_then(|e| e.to_str())
        .map(|e| e.to_ascii_lowercase());
    if ext.as_deref() != Some("jar") {
        return Err("only .jar files can be added as mods".to_string());
    }
    let filename = src
        .file_name()
        .and_then(|n| n.to_str())
        .ok_or_else(|| "could not read the file name".to_string())?
        .to_string();

    let (dir, mut instance) = find_instance(&state, &instance_id)?;
    let mods = mods_dir(&dir);
    std::fs::create_dir_all(&mods).map_err(|e| e.to_string())?;
    let dest = mods.join(&filename);

    let bytes = std::fs::read(&src).map_err(|e| e.to_string())?;
    std::fs::write(&dest, &bytes).map_err(|e| e.to_string())?;

    // Stable id from the file contents, so re-adding the same jar de-dupes
    // instead of stacking a second identical entry.
    let mut hasher = Sha1::new();
    hasher.update(&bytes);
    let id = format!("{:x}", hasher.finalize());

    // Human-ish name: strip the .jar extension for display.
    let name = filename.trim_end_matches(".jar").to_string();

    instance.mods.retain(|m| m.filename != filename);
    instance.mods.push(ModRef {
        id,
        filename,
        name,
        version: "local".to_string(),
        enabled: true,
        source: ModSource::Local,
        pinned: false,
        version_id: None,
    });

    save_instance(&dir, &instance).map_err(|e| e.to_string())?;
    Ok(instance)
}

/// Reconcile the tracked `mods` list against what's actually in `mods/` on
/// disk. Catches jars the user dropped in with a file manager (picked up as
/// Local mods) and prunes entries whose files were deleted out from under us.
/// Called when the Mods tab opens so the list never lies about disk state.
#[tauri::command]
pub async fn sync_instance_mods(
    state: State<'_, AppState>,
    instance_id: String,
) -> Result<Instance, String> {
    let (dir, mut instance) = find_instance(&state, &instance_id)?;
    let mods = mods_dir(&dir);
    std::fs::create_dir_all(&mods).map_err(|e| e.to_string())?;

    // What's on disk right now, keyed by the enabled-form base filename.
    let mut on_disk: Vec<(String, bool)> = Vec::new();
    for entry in std::fs::read_dir(&mods).map_err(|e| e.to_string())?.flatten() {
        let name = entry.file_name().to_string_lossy().to_string();
        if let Some(base) = name.strip_suffix(".disabled") {
            if base.ends_with(".jar") {
                on_disk.push((base.to_string(), false));
            }
        } else if name.ends_with(".jar") {
            on_disk.push((name, true));
        }
    }

    // Drop tracked mods whose files vanished.
    instance
        .mods
        .retain(|m| on_disk.iter().any(|(base, _)| base == &m.filename));

    // Update enabled flags + adopt untracked jars as Local mods.
    for (base, enabled) in on_disk {
        match instance.mods.iter_mut().find(|m| m.filename == base) {
            Some(m) => m.enabled = enabled,
            None => instance.mods.push(ModRef {
                id: base.clone(),
                name: base.trim_end_matches(".jar").to_string(),
                filename: base,
                version: "local".to_string(),
                enabled,
                source: ModSource::Local,
                pinned: false,
                version_id: None,
            }),
        }
    }

    instance.mods.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
    save_instance(&dir, &instance).map_err(|e| e.to_string())?;
    Ok(instance)
}
