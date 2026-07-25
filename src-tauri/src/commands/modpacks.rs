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

use std::collections::HashMap;
use std::io::Read;
use std::path::{Path, PathBuf};

use serde::Deserialize;
use tauri::State;
use uuid::Uuid;

use crate::commands::instance::{find_instance, save_instance};
use crate::models::instance::{Instance, Loader, ModRef, ModSource};
use crate::models::modpack::{BpackEntry, BpackFile, Modpack};
use crate::state::AppState;
use crate::util::{base64_decode, base64_encode};

const MODRINTH_UA: &str = "blurred-client/0.1.0 (dev build)";

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

// ---- Modrinth modpack import (.mrpack) ----
//
// An .mrpack is a zip: `modrinth.index.json` lists mod files by CDN download URL,
// plus `overrides/` (and `client-overrides/`) folders of loose files (configs,
// resource packs) to drop into the instance. Importing builds a real instance:
// correct MC version + loader, every listed file downloaded, overrides applied.

#[derive(Debug, Deserialize)]
struct MrIndex {
    name: Option<String>,
    #[serde(default)]
    dependencies: HashMap<String, String>,
    #[serde(default)]
    files: Vec<MrFile>,
}

#[derive(Debug, Deserialize)]
struct MrFile {
    path: String,
    #[serde(default)]
    downloads: Vec<String>,
    #[serde(default)]
    env: Option<MrEnv>,
}

#[derive(Debug, Deserialize)]
struct MrEnv {
    client: Option<String>,
}

fn detect_mrpack_loader(deps: &HashMap<String, String>) -> (Loader, Option<String>) {
    for (key, loader) in [
        ("fabric-loader", Loader::Fabric),
        ("quilt-loader", Loader::Quilt),
        ("neoforge", Loader::NeoForge),
        ("forge", Loader::Forge),
    ] {
        if let Some(v) = deps.get(key) {
            return (loader, Some(v.clone()));
        }
    }
    (Loader::Vanilla, None)
}

async fn import_mrpack_from_path(state: &AppState, path: &Path) -> Result<Instance, String> {
    let bytes = std::fs::read(path).map_err(|e| e.to_string())?;

    // Read the zip synchronously up front (the reader isn't Send, so we must not
    // hold it across the download awaits below): parse the index + buffer overrides.
    let (index, overrides): (MrIndex, Vec<(String, Vec<u8>)>) = {
        let mut archive =
            zip::ZipArchive::new(std::io::Cursor::new(&bytes)).map_err(|e| e.to_string())?;

        let index: MrIndex = {
            let mut f = archive
                .by_name("modrinth.index.json")
                .map_err(|_| "not a Modrinth .mrpack (no modrinth.index.json)".to_string())?;
            let mut s = String::new();
            f.read_to_string(&mut s).map_err(|e| e.to_string())?;
            serde_json::from_str(&s).map_err(|e| e.to_string())?
        };

        let mut overrides = Vec::new();
        for i in 0..archive.len() {
            let mut entry = archive.by_index(i).map_err(|e| e.to_string())?;
            if entry.is_dir() {
                continue;
            }
            let name = entry.name().to_string();
            let rel = name
                .strip_prefix("overrides/")
                .or_else(|| name.strip_prefix("client-overrides/"));
            if let Some(rel) = rel {
                if rel.is_empty() || rel.contains("..") {
                    continue;
                }
                let mut buf = Vec::new();
                entry.read_to_end(&mut buf).map_err(|e| e.to_string())?;
                overrides.push((rel.to_string(), buf));
            }
        }
        (index, overrides)
    };

    let mc_version = index
        .dependencies
        .get("minecraft")
        .cloned()
        .ok_or_else(|| "mrpack doesn't specify a Minecraft version".to_string())?;
    let (loader, loader_version) = detect_mrpack_loader(&index.dependencies);

    let name = index.name.clone().unwrap_or_else(|| "Imported Pack".to_string());
    let mut instance = Instance::new(name, mc_version, loader);
    instance.loader_version = loader_version;

    let inst_dir = state.instances_dir.join(instance.folder_name());
    std::fs::create_dir_all(&inst_dir).map_err(|e| e.to_string())?;

    // Apply overrides (configs, resource packs, etc.).
    for (rel, buf) in overrides {
        let dest = inst_dir.join(&rel);
        if let Some(parent) = dest.parent() {
            std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
        }
        std::fs::write(&dest, buf).map_err(|e| e.to_string())?;
    }

    // Download the listed files (mods, etc.) from the Modrinth CDN.
    let client = reqwest::Client::new();
    for f in &index.files {
        if let Some(env) = &f.env {
            if env.client.as_deref() == Some("unsupported") {
                continue; // server-only file
            }
        }
        if f.path.contains("..") || f.path.starts_with('/') {
            continue;
        }
        let Some(url) = f.downloads.first() else { continue };
        let dest = inst_dir.join(&f.path);
        if let Some(parent) = dest.parent() {
            std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
        }
        let data = client
            .get(url)
            .send()
            .await
            .map_err(|e| e.to_string())?
            .bytes()
            .await
            .map_err(|e| e.to_string())?;
        std::fs::write(&dest, &data).map_err(|e| e.to_string())?;

        // Track jars under mods/ so the Mods tab shows them (as adopted locals —
        // the mrpack index doesn't carry Modrinth project ids per file).
        if f.path.starts_with("mods/") && f.path.ends_with(".jar") {
            if let Some(fname) = Path::new(&f.path).file_name().and_then(|n| n.to_str()) {
                instance.mods.push(ModRef {
                    id: fname.to_string(),
                    filename: fname.to_string(),
                    name: fname.trim_end_matches(".jar").to_string(),
                    version: "modpack".to_string(),
                    enabled: true,
                    source: ModSource::Local,
                    pinned: false,
                    version_id: None,
                });
            }
        }
    }

    save_instance(&inst_dir, &instance).map_err(|e| e.to_string())?;
    Ok(instance)
}

/// Import a local `.mrpack` file (drag-drop) into a new instance.
#[tauri::command]
pub async fn import_mrpack(state: State<'_, AppState>, source_path: String) -> Result<Instance, String> {
    import_mrpack_from_path(&state, Path::new(&source_path)).await
}

/// Install a Modrinth modpack by project id/slug: fetch its newest version's
/// `.mrpack` file, download it, and import it into a new instance.
#[tauri::command]
pub async fn install_modrinth_modpack(
    state: State<'_, AppState>,
    project_id: String,
) -> Result<Instance, String> {
    let client = reqwest::Client::new();
    let versions: serde_json::Value = client
        .get(format!("https://api.modrinth.com/v2/project/{project_id}/version"))
        .header("User-Agent", MODRINTH_UA)
        .send()
        .await
        .map_err(|e| e.to_string())?
        .json()
        .await
        .map_err(|e| e.to_string())?;

    let arr = versions.as_array().ok_or_else(|| "unexpected Modrinth response".to_string())?;
    let version = arr.first().ok_or_else(|| "this modpack has no versions".to_string())?;
    let files = version["files"].as_array().ok_or_else(|| "modpack version has no files".to_string())?;
    let file = files
        .iter()
        .find(|f| f["filename"].as_str().map(|s| s.ends_with(".mrpack")).unwrap_or(false))
        .or_else(|| files.iter().find(|f| f["primary"].as_bool().unwrap_or(false)))
        .or_else(|| files.first())
        .ok_or_else(|| "no downloadable modpack file".to_string())?;
    let url = file["url"].as_str().ok_or_else(|| "modpack file has no url".to_string())?;

    let data = client.get(url).send().await.map_err(|e| e.to_string())?.bytes().await.map_err(|e| e.to_string())?;
    let tmp = state.data_dir.join("cache");
    std::fs::create_dir_all(&tmp).map_err(|e| e.to_string())?;
    let tmp_file = tmp.join("import.mrpack");
    std::fs::write(&tmp_file, &data).map_err(|e| e.to_string())?;

    import_mrpack_from_path(&state, &tmp_file).await
}
