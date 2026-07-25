// Modpacks (Phase 4), the offline half: create a reusable pack from an
// instance's mod set, keep a library of them, apply one to a fresh instance,
// and export/import a pack as a single self-contained `.bpack` file (metadata +
// the jars themselves, base64'd in — see models::modpack::BpackFile). Browsing
// and importing packs from Modrinth needs the live API and is deliberately not
// here.
//
// Layout: <data>/modpacks/<id>/modpack.json  +  <data>/modpacks/<id>/mods/*.jar
// Exports land in <data>/exports/<slug>.bpack (no file-dialog plugin needed;
// the frontend reveals the folder, and import comes back in via drag-drop).

use std::path::{Path, PathBuf};

use tauri::State;
use uuid::Uuid;

use crate::commands::instance::{find_instance, save_instance};
use crate::models::instance::Instance;
use crate::models::modpack::{BpackEntry, BpackFile, Modpack};
use crate::state::AppState;
use crate::util::{base64_decode, base64_encode};

fn modpacks_dir(state: &AppState) -> PathBuf {
    state.data_dir.join("modpacks")
}

fn modpack_dir(state: &AppState, id: &Uuid) -> PathBuf {
    modpacks_dir(state).join(id.to_string())
}

fn load_modpack(dir: &Path) -> anyhow::Result<Modpack> {
    let raw = std::fs::read_to_string(dir.join("modpack.json"))?;
    Ok(serde_json::from_str(&raw)?)
}

fn slugify(name: &str) -> String {
    let s: String = name
        .chars()
        .map(|c| if c.is_alphanumeric() { c } else { '-' })
        .collect();
    let s = s.trim_matches('-').to_lowercase();
    if s.is_empty() { "modpack".to_string() } else { s }
}

#[tauri::command]
pub async fn list_modpacks(state: State<'_, AppState>) -> Result<Vec<Modpack>, String> {
    let base = modpacks_dir(&state);
    let mut out = Vec::new();
    if base.is_dir() {
        for entry in std::fs::read_dir(&base).map_err(|e| e.to_string())?.flatten() {
            if entry.path().is_dir() {
                if let Ok(pack) = load_modpack(&entry.path()) {
                    out.push(pack);
                }
            }
        }
    }
    out.sort_by(|a, b| b.created_at.cmp(&a.created_at));
    Ok(out)
}

/// Snapshot an instance's mod set into a new library modpack, copying the jar
/// files in so the pack is self-contained even if the source instance later
/// changes or is deleted.
#[tauri::command]
pub async fn create_modpack_from_instance(
    state: State<'_, AppState>,
    instance_id: String,
    name: String,
    description: String,
) -> Result<Modpack, String> {
    let name = name.trim().to_string();
    if name.is_empty() {
        return Err("modpack name can't be empty".to_string());
    }
    let (inst_dir, instance) = find_instance(&state, &instance_id)?;

    let pack = Modpack {
        id: Uuid::new_v4(),
        name,
        description,
        mc_version: instance.mc_version.clone(),
        loader: instance.loader,
        mods: instance.mods.clone(),
        created_at: chrono::Utc::now(),
    };

    let dir = modpack_dir(&state, &pack.id);
    let pack_mods = dir.join("mods");
    std::fs::create_dir_all(&pack_mods).map_err(|e| e.to_string())?;

    // Copy each tracked jar, normalizing to the enabled filename regardless of
    // whether it's currently disabled on the source instance.
    let src_mods = inst_dir.join("mods");
    for m in &instance.mods {
        for candidate in [src_mods.join(&m.filename), src_mods.join(format!("{}.disabled", m.filename))] {
            if candidate.exists() {
                std::fs::copy(&candidate, pack_mods.join(&m.filename)).map_err(|e| e.to_string())?;
                break;
            }
        }
    }

    std::fs::write(dir.join("modpack.json"), serde_json::to_string_pretty(&pack).map_err(|e| e.to_string())?)
        .map_err(|e| e.to_string())?;
    Ok(pack)
}

#[tauri::command]
pub async fn delete_modpack(state: State<'_, AppState>, modpack_id: String) -> Result<(), String> {
    let id = Uuid::parse_str(&modpack_id).map_err(|e| e.to_string())?;
    let dir = modpack_dir(&state, &id);
    if dir.is_dir() {
        std::fs::remove_dir_all(&dir).map_err(|e| e.to_string())?;
    }
    Ok(())
}

/// Spin up a brand-new instance from a pack: correct MC version + loader, and
/// every jar copied in with the pack's enabled/disabled state preserved.
#[tauri::command]
pub async fn apply_modpack(
    state: State<'_, AppState>,
    modpack_id: String,
    instance_name: String,
) -> Result<Instance, String> {
    let id = Uuid::parse_str(&modpack_id).map_err(|e| e.to_string())?;
    let pack = load_modpack(&modpack_dir(&state, &id)).map_err(|e| e.to_string())?;

    let name = instance_name.trim();
    let name = if name.is_empty() { pack.name.clone() } else { name.to_string() };

    let mut instance = Instance::new(name, pack.mc_version.clone(), pack.loader);
    instance.mods = pack.mods.clone();

    let inst_dir = state.instances_dir.join(instance.folder_name());
    let inst_mods = inst_dir.join("mods");
    std::fs::create_dir_all(&inst_mods).map_err(|e| e.to_string())?;

    let pack_mods = modpack_dir(&state, &id).join("mods");
    for m in &pack.mods {
        let src = pack_mods.join(&m.filename);
        if src.exists() {
            let dest_name = if m.enabled { m.filename.clone() } else { format!("{}.disabled", m.filename) };
            std::fs::copy(&src, inst_mods.join(dest_name)).map_err(|e| e.to_string())?;
        }
    }

    save_instance(&inst_dir, &instance).map_err(|e| e.to_string())?;
    Ok(instance)
}

/// Bundle a library pack into a single portable `.bpack` file under
/// <data>/exports/. Returns the path written so the UI can reveal it.
#[tauri::command]
pub async fn export_modpack(state: State<'_, AppState>, modpack_id: String) -> Result<String, String> {
    let id = Uuid::parse_str(&modpack_id).map_err(|e| e.to_string())?;
    let dir = modpack_dir(&state, &id);
    let pack = load_modpack(&dir).map_err(|e| e.to_string())?;

    let mut files = Vec::new();
    let pack_mods = dir.join("mods");
    for m in &pack.mods {
        let path = pack_mods.join(&m.filename);
        if let Ok(bytes) = std::fs::read(&path) {
            files.push(BpackEntry {
                filename: m.filename.clone(),
                data_b64: base64_encode(&bytes),
            });
        }
    }

    let bundle = BpackFile { format: "blurred-modpack/1".to_string(), modpack: pack.clone(), files };

    let exports = state.data_dir.join("exports");
    std::fs::create_dir_all(&exports).map_err(|e| e.to_string())?;
    let dest = exports.join(format!("{}.bpack", slugify(&pack.name)));
    std::fs::write(&dest, serde_json::to_string(&bundle).map_err(|e| e.to_string())?)
        .map_err(|e| e.to_string())?;
    Ok(dest.to_string_lossy().to_string())
}

/// Read a `.bpack` back into the library, giving it a fresh id (so importing a
/// pack you also authored doesn't collide) and extracting the embedded jars.
#[tauri::command]
pub async fn import_modpack(state: State<'_, AppState>, source_path: String) -> Result<Modpack, String> {
    let raw = std::fs::read_to_string(&source_path).map_err(|e| e.to_string())?;
    let bundle: BpackFile = serde_json::from_str(&raw)
        .map_err(|_| "not a valid .bpack file".to_string())?;

    let mut pack = bundle.modpack;
    pack.id = Uuid::new_v4();
    pack.created_at = chrono::Utc::now();

    let dir = modpack_dir(&state, &pack.id);
    let pack_mods = dir.join("mods");
    std::fs::create_dir_all(&pack_mods).map_err(|e| e.to_string())?;

    for entry in &bundle.files {
        // Guard: filenames come from an untrusted file — no path separators.
        if entry.filename.contains('/') || entry.filename.contains('\\') || entry.filename.contains("..") {
            continue;
        }
        let bytes = base64_decode(&entry.data_b64)?;
        std::fs::write(pack_mods.join(&entry.filename), bytes).map_err(|e| e.to_string())?;
    }

    std::fs::write(dir.join("modpack.json"), serde_json::to_string_pretty(&pack).map_err(|e| e.to_string())?)
        .map_err(|e| e.to_string())?;
    Ok(pack)
}

/// Open the given path's containing folder in the OS file manager. Small helper
/// so "Export" can point the user straight at the produced .bpack.
#[tauri::command]
pub async fn reveal_path(path: String) -> Result<(), String> {
    let p = PathBuf::from(&path);
    let target = if p.is_file() { p.parent().map(|x| x.to_path_buf()).unwrap_or(p.clone()) } else { p };
    open_in_file_manager(&target)
}

#[cfg(target_os = "windows")]
pub fn open_in_file_manager(dir: &Path) -> Result<(), String> {
    std::process::Command::new("explorer")
        .arg(dir)
        .spawn()
        .map(|_| ())
        .map_err(|e| e.to_string())
}

#[cfg(target_os = "macos")]
pub fn open_in_file_manager(dir: &Path) -> Result<(), String> {
    std::process::Command::new("open").arg(dir).spawn().map(|_| ()).map_err(|e| e.to_string())
}

#[cfg(all(not(target_os = "windows"), not(target_os = "macos")))]
pub fn open_in_file_manager(dir: &Path) -> Result<(), String> {
    std::process::Command::new("xdg-open").arg(dir).spawn().map(|_| ()).map_err(|e| e.to_string())
}
