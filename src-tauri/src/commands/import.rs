//! Importing from other launchers.
//!
//! Moving to a new launcher normally means re-doing every setting you ever
//! changed: your keybinds, your video options, your resource packs and your
//! whole server list. All of that is sitting in files the old launcher owns,
//! and copying them by hand means knowing where they live.
//!
//! So this finds the other launchers on the machine, lists what they have, and
//! copies across whichever pieces you tick. Nothing is guessed at and nothing
//! is moved — every import is a copy, and the source instance is never touched,
//! because "I'll try the new launcher" should not be a decision you can't back
//! out of.
//!
//! # What "an instance" means elsewhere
//!
//! Prism, MultiMC and PolyMC share a layout: a folder per instance holding
//! `instance.cfg` and `mmc-pack.json`, with the actual game directory nested
//! inside as `.minecraft`. The Modrinth app and CurseForge each use their own
//! JSON, and the official launcher has no instances at all — just one
//! `.minecraft` folder. They're normalised into [`ImportCandidate`] here so the
//! rest of the app only has to understand one shape.

use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};
use tauri::State;

use crate::commands::instance::save_instance;
use crate::models::instance::{Instance, Loader};
use crate::state::AppState;

// ---------------------------------------------------------------------------
// Model
// ---------------------------------------------------------------------------

/// One launcher found on this machine, with whatever it holds.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ImportSource {
    /// Display name, e.g. "Prism Launcher".
    pub launcher: String,
    /// The folder that was scanned.
    pub root: String,
    pub instances: Vec<ImportCandidate>,
}

/// One importable game directory, normalised across launchers.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ImportCandidate {
    /// The game directory, absolute. Doubles as the candidate's identity —
    /// it's unique by construction and the frontend hands it straight back.
    pub id: String,
    pub name: String,
    /// Empty when the launcher didn't record one and we couldn't infer it.
    pub mc_version: String,
    pub loader: Loader,
    pub loader_version: Option<String>,

    // What's actually in there, so the UI can grey out what isn't.
    pub has_options: bool,
    pub has_servers: bool,
    pub config_files: usize,
    pub resource_packs: usize,
    pub shader_packs: usize,
    pub mods: usize,
    pub worlds: usize,
}

/// Which pieces of a candidate to copy. Everything is opt-in.
#[derive(Debug, Clone, Copy, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ImportSelection {
    /// `options.txt` — video settings, keybinds, sound levels, chat settings.
    pub options: bool,
    /// `servers.dat` — the multiplayer server list.
    pub servers: bool,
    /// `resourcepacks/`.
    pub resource_packs: bool,
    /// `shaderpacks/`.
    pub shader_packs: bool,
    /// `config/` — per-mod configuration.
    pub config: bool,
    /// `mods/`.
    pub mods: bool,
    /// `saves/`. Can be very large, so it's off by default in the UI.
    pub worlds: bool,
}

/// What an import actually did, so the UI can be specific rather than saying
/// "done" and leaving you to go and look.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ImportReport {
    pub instance_id: String,
    pub name: String,
    /// Human lines: "42 resource packs", "options.txt", and so on.
    pub copied: Vec<String>,
    /// Things that were asked for but weren't there, or failed to copy.
    pub skipped: Vec<String>,
}

// ---------------------------------------------------------------------------
// Detection
// ---------------------------------------------------------------------------

fn home() -> Option<PathBuf> {
    std::env::var_os("USERPROFILE")
        .or_else(|| std::env::var_os("HOME"))
        .map(PathBuf::from)
}

fn appdata() -> Option<PathBuf> {
    std::env::var_os("APPDATA").map(PathBuf::from)
}

/// Candidate roots, as (launcher name, path, layout).
///
/// Deliberately a flat table rather than per-OS functions: every entry is
/// "somewhere a launcher might have put its instances", the list is the whole
/// logic, and a table is the honest shape for that. Paths that don't exist are
/// dropped later, so listing a Windows path on Linux costs nothing.
fn known_roots() -> Vec<(&'static str, PathBuf, Layout)> {
    let mut out: Vec<(&'static str, PathBuf, Layout)> = Vec::new();

    let mut push = |name: &'static str, path: Option<PathBuf>, layout: Layout| {
        if let Some(p) = path {
            out.push((name, p, layout));
        }
    };

    let app = appdata();
    let h = home();

    // --- Windows ---
    push("Prism Launcher", app.as_ref().map(|p| p.join("PrismLauncher/instances")), Layout::MultiMc);
    push("PolyMC", app.as_ref().map(|p| p.join("PolyMC/instances")), Layout::MultiMc);
    push("MultiMC", app.as_ref().map(|p| p.join("MultiMC/instances")), Layout::MultiMc);
    push("ATLauncher", app.as_ref().map(|p| p.join("ATLauncher/instances")), Layout::GameDirPerFolder);
    push("GDLauncher", app.as_ref().map(|p| p.join("gdlauncher_next/instances")), Layout::GameDirPerFolder);
    push("Modrinth App", app.as_ref().map(|p| p.join("ModrinthApp/profiles")), Layout::GameDirPerFolder);
    push("Modrinth App", app.as_ref().map(|p| p.join("com.modrinth.theseus/profiles")), Layout::GameDirPerFolder);
    push("CurseForge", h.as_ref().map(|p| p.join("curseforge/minecraft/Instances")), Layout::GameDirPerFolder);
    push("Minecraft Launcher", app.as_ref().map(|p| p.join(".minecraft")), Layout::SingleGameDir);

    // --- Linux ---
    if let Some(h) = h.as_ref() {
        let share = h.join(".local/share");
        push("Prism Launcher", Some(share.join("PrismLauncher/instances")), Layout::MultiMc);
        push(
            "Prism Launcher",
            Some(h.join(".var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances")),
            Layout::MultiMc,
        );
        push("PolyMC", Some(share.join("PolyMC/instances")), Layout::MultiMc);
        push("MultiMC", Some(share.join("multimc/instances")), Layout::MultiMc);
        push("MultiMC", Some(h.join(".multimc/instances")), Layout::MultiMc);
        push("Modrinth App", Some(share.join("ModrinthApp/profiles")), Layout::GameDirPerFolder);
        push("ATLauncher", Some(h.join(".atlauncher/instances")), Layout::GameDirPerFolder);
        push("Minecraft Launcher", Some(h.join(".minecraft")), Layout::SingleGameDir);

        // --- macOS ---
        let support = h.join("Library/Application Support");
        push("Prism Launcher", Some(support.join("PrismLauncher/instances")), Layout::MultiMc);
        push("PolyMC", Some(support.join("PolyMC/instances")), Layout::MultiMc);
        push("MultiMC", Some(support.join("MultiMC/instances")), Layout::MultiMc);
        push("Modrinth App", Some(support.join("ModrinthApp/profiles")), Layout::GameDirPerFolder);
        push("Minecraft Launcher", Some(support.join("minecraft")), Layout::SingleGameDir);
    }

    out
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Layout {
    /// Prism/MultiMC/PolyMC: instance folder with the game dir nested inside.
    MultiMc,
    /// One folder per instance, and the folder *is* the game dir.
    GameDirPerFolder,
    /// The path itself is a single game directory.
    SingleGameDir,
}

/// Every other launcher we can find, with its instances.
///
/// Sources with nothing in them are dropped: an empty "Prism Launcher (0)" row
/// is a worse answer than not mentioning Prism.
#[tauri::command]
pub async fn detect_import_sources(state: State<'_, AppState>) -> Result<Vec<ImportSource>, String> {
    let ours = state.instances_dir.clone();
    let mut out: Vec<ImportSource> = Vec::new();

    for (launcher, root, layout) in known_roots() {
        if !root.is_dir() {
            continue;
        }
        // Never offer to import our own instances into ourselves.
        if root.starts_with(&ours) || ours.starts_with(&root) {
            continue;
        }
        // The same launcher can match two roots (a native install and a
        // Flatpak, say). Only the first one that exists is interesting.
        if out.iter().any(|s| s.launcher == launcher) {
            continue;
        }

        let instances = scan_root(&root, layout);
        if !instances.is_empty() {
            out.push(ImportSource {
                launcher: launcher.to_string(),
                root: root.to_string_lossy().to_string(),
                instances,
            });
        }
    }

    Ok(out)
}

/// Scan a folder the user picked by hand.
///
/// Tries all three layouts, because someone pointing at a folder knows they
/// have Minecraft data in there and shouldn't have to tell us which launcher
/// put it there. Returns an empty instance list rather than an error when
/// there's nothing to find — "we looked and there's nothing" is a result.
#[tauri::command]
pub async fn scan_import_folder(path: String) -> Result<ImportSource, String> {
    let root = PathBuf::from(&path);
    if !root.is_dir() {
        return Err(format!("{path} is not a folder"));
    }

    // A folder that is itself a game directory is the most likely thing
    // someone means when they pick one.
    let mut instances = scan_root(&root, Layout::SingleGameDir);
    if instances.is_empty() {
        instances = scan_root(&root, Layout::MultiMc);
    }
    if instances.is_empty() {
        instances = scan_root(&root, Layout::GameDirPerFolder);
    }

    Ok(ImportSource {
        launcher: "Chosen folder".to_string(),
        root: path,
        instances,
    })
}

fn scan_root(root: &Path, layout: Layout) -> Vec<ImportCandidate> {
    match layout {
        Layout::SingleGameDir => {
            let name = root
                .file_name()
                .map(|n| n.to_string_lossy().to_string())
                .unwrap_or_else(|| "Minecraft".to_string());
            // ".minecraft" is a folder name, not something to show a person.
            let name = if name.starts_with('.') { "Minecraft".to_string() } else { name };
            describe(root, name, String::new(), Loader::Vanilla, None)
                .into_iter()
                .collect()
        }
        Layout::MultiMc | Layout::GameDirPerFolder => {
            let mut out = Vec::new();
            let Ok(entries) = std::fs::read_dir(root) else {
                return out;
            };
            for entry in entries.flatten() {
                let dir = entry.path();
                if !dir.is_dir() {
                    continue;
                }
                let candidate = if layout == Layout::MultiMc {
                    read_multimc_instance(&dir)
                } else {
                    read_plain_instance(&dir)
                };
                if let Some(c) = candidate {
                    out.push(c);
                }
            }
            out.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
            out
        }
    }
}

/// Prism / MultiMC / PolyMC.
fn read_multimc_instance(dir: &Path) -> Option<ImportCandidate> {
    let cfg_path = dir.join("instance.cfg");
    if !cfg_path.is_file() {
        return None;
    }

    // `.minecraft` is the modern layout; very old MultiMC used `minecraft`.
    let game_dir = [".minecraft", "minecraft"]
        .iter()
        .map(|d| dir.join(d))
        .find(|p| p.is_dir())?;

    let cfg = std::fs::read_to_string(&cfg_path).unwrap_or_default();
    let name = ini_value(&cfg, "name")
        .unwrap_or_else(|| dir.file_name().unwrap_or_default().to_string_lossy().to_string());

    let (mc_version, loader, loader_version) = read_mmc_pack(&dir.join("mmc-pack.json"))
        // Pre-`mmc-pack.json` MultiMC put the version in instance.cfg.
        .unwrap_or_else(|| (ini_value(&cfg, "IntendedVersion").unwrap_or_default(), Loader::Vanilla, None));

    describe(&game_dir, name, mc_version, loader, loader_version)
}

/// Launchers whose instance folder is the game directory.
fn read_plain_instance(dir: &Path) -> Option<ImportCandidate> {
    // Must actually look like a game directory, or every stray folder in the
    // parent shows up as an importable instance.
    if !looks_like_game_dir(dir) {
        return None;
    }

    let name = dir.file_name()?.to_string_lossy().to_string();
    let (mc_version, loader) = read_sidecar_metadata(dir);
    describe(dir, name, mc_version, loader, None)
}

/// Is there enough here to be a Minecraft game directory?
fn looks_like_game_dir(dir: &Path) -> bool {
    dir.join("options.txt").is_file()
        || dir.join("saves").is_dir()
        || dir.join("mods").is_dir()
        || dir.join("resourcepacks").is_dir()
        || dir.join("servers.dat").is_file()
}

/// Version and loader out of whatever JSON the launcher left next to the game.
///
/// Best effort by design: getting this wrong only means the version box is
/// pre-filled with the wrong string, which the user can see and correct. It is
/// never worth failing an import over.
fn read_sidecar_metadata(dir: &Path) -> (String, Loader) {
    // Modrinth app profile, CurseForge instance, GDLauncher config.
    for file in ["profile.json", "minecraftinstance.json", "config.json"] {
        let Ok(raw) = std::fs::read_to_string(dir.join(file)) else {
            continue;
        };
        let Ok(json) = serde_json::from_str::<serde_json::Value>(&raw) else {
            continue;
        };

        let version = ["game_version", "gameVersion", "mcVersion"]
            .iter()
            .find_map(|k| find_string(&json, k))
            .unwrap_or_default();

        let loader_name = ["loader", "modLoader", "loaderType", "modloader"]
            .iter()
            .find_map(|k| find_string(&json, k))
            .unwrap_or_default();

        if !version.is_empty() || !loader_name.is_empty() {
            return (version, loader_from_str(&loader_name));
        }
    }
    (String::new(), Loader::Vanilla)
}

/// First string value for `key` anywhere in the tree.
///
/// These files nest their metadata differently per launcher and per version of
/// that launcher; searching by key is far more robust than encoding four
/// separate schemas that all drift.
fn find_string(value: &serde_json::Value, key: &str) -> Option<String> {
    match value {
        serde_json::Value::Object(map) => {
            if let Some(serde_json::Value::String(s)) = map.get(key) {
                if !s.is_empty() {
                    return Some(s.clone());
                }
            }
            map.values().find_map(|v| find_string(v, key))
        }
        serde_json::Value::Array(items) => items.iter().find_map(|v| find_string(v, key)),
        _ => None,
    }
}

fn loader_from_str(s: &str) -> Loader {
    let s = s.to_lowercase();
    if s.contains("neoforge") {
        Loader::NeoForge
    } else if s.contains("fabric") {
        Loader::Fabric
    } else if s.contains("quilt") {
        Loader::Quilt
    } else if s.contains("forge") {
        Loader::Forge
    } else {
        Loader::Vanilla
    }
}

/// `mmc-pack.json`: a component list, one entry per thing installed.
fn read_mmc_pack(path: &Path) -> Option<(String, Loader, Option<String>)> {
    #[derive(Deserialize)]
    struct Pack {
        components: Vec<Component>,
    }
    #[derive(Deserialize)]
    struct Component {
        uid: String,
        #[serde(default)]
        version: String,
    }

    let raw = std::fs::read_to_string(path).ok()?;
    let pack: Pack = serde_json::from_str(&raw).ok()?;

    let mut mc_version = String::new();
    let mut loader = Loader::Vanilla;
    let mut loader_version = None;

    for c in pack.components {
        match c.uid.as_str() {
            "net.minecraft" => mc_version = c.version,
            "net.fabricmc.fabric-loader" => {
                loader = Loader::Fabric;
                loader_version = Some(c.version);
            }
            "org.quiltmc.quilt-loader" => {
                loader = Loader::Quilt;
                loader_version = Some(c.version);
            }
            "net.minecraftforge" => {
                loader = Loader::Forge;
                loader_version = Some(c.version);
            }
            "net.neoforged" => {
                loader = Loader::NeoForge;
                loader_version = Some(c.version);
            }
            _ => {}
        }
    }

    Some((mc_version, loader, loader_version))
}

/// `key=value` out of an INI-ish file. MultiMC's `instance.cfg` has no
/// sections, so there's nothing more to parse than this.
fn ini_value(text: &str, key: &str) -> Option<String> {
    text.lines().find_map(|line| {
        let (k, v) = line.split_once('=')?;
        (k.trim() == key).then(|| v.trim().to_string())
    })
}

/// Count what's in a game directory and build the candidate.
fn describe(
    game_dir: &Path,
    name: String,
    mc_version: String,
    loader: Loader,
    loader_version: Option<String>,
) -> Option<ImportCandidate> {
    if !game_dir.is_dir() {
        return None;
    }

    Some(ImportCandidate {
        id: game_dir.to_string_lossy().to_string(),
        name,
        mc_version,
        loader,
        loader_version,
        has_options: game_dir.join("options.txt").is_file(),
        has_servers: game_dir.join("servers.dat").is_file(),
        config_files: count_entries(&game_dir.join("config")),
        resource_packs: count_entries(&game_dir.join("resourcepacks")),
        shader_packs: count_entries(&game_dir.join("shaderpacks")),
        mods: count_entries(&game_dir.join("mods")),
        worlds: count_entries(&game_dir.join("saves")),
    })
}

fn count_entries(dir: &Path) -> usize {
    std::fs::read_dir(dir).map(|d| d.flatten().count()).unwrap_or(0)
}

// ---------------------------------------------------------------------------
// Importing
// ---------------------------------------------------------------------------

/// Copy one other-launcher instance in as a new Blurred instance.
///
/// The source is only ever read. If something goes wrong halfway the new
/// instance is left in place with whatever did copy, and the report says what
/// didn't — which is more useful than deleting the lot and reporting one error.
#[tauri::command]
pub async fn import_instance(
    state: State<'_, AppState>,
    game_dir: String,
    name: String,
    mc_version: String,
    loader: Loader,
    loader_version: Option<String>,
    selection: ImportSelection,
) -> Result<ImportReport, String> {
    let source = PathBuf::from(&game_dir);
    if !source.is_dir() {
        return Err(format!("{game_dir} no longer exists"));
    }

    let name = if name.trim().is_empty() { "Imported".to_string() } else { name.trim().to_string() };
    let mut instance = Instance::new(name.clone(), mc_version, loader);
    instance.loader_version = loader_version;
    instance.notes = format!("Imported from {}", source.display());

    let dest = state.instances_dir.join(instance.folder_name());
    std::fs::create_dir_all(&dest).map_err(|e| e.to_string())?;

    let (copied, skipped) = copy_selection(&source, &dest, selection);

    save_instance(&dest, &instance).map_err(|e| e.to_string())?;

    Ok(ImportReport {
        instance_id: instance.id.to_string(),
        name,
        copied,
        skipped,
    })
}

/// Copy selected content into an instance that already exists.
///
/// The other half of the same job: someone who already built an instance here
/// and just wants their old options and server list in it, without a second
/// copy of the instance appearing.
#[tauri::command]
pub async fn import_into_instance(
    state: State<'_, AppState>,
    instance_id: String,
    game_dir: String,
    selection: ImportSelection,
) -> Result<ImportReport, String> {
    let (dest, instance) = crate::commands::instance::find_instance(&state, &instance_id)?;
    let source = PathBuf::from(&game_dir);
    if !source.is_dir() {
        return Err(format!("{game_dir} no longer exists"));
    }

    let (copied, skipped) = copy_selection(&source, &dest, selection);

    Ok(ImportReport {
        instance_id,
        name: instance.name,
        copied,
        skipped,
    })
}

/// The actual copying, shared by both entry points.
///
/// Returns (copied, skipped) as human-readable lines. Each piece is
/// independent: one failure never stops the others, because a resource pack
/// that won't copy is no reason to lose the server list too.
fn copy_selection(
    source: &Path,
    dest: &Path,
    selection: ImportSelection,
) -> (Vec<String>, Vec<String>) {
    let mut copied = Vec::new();
    let mut skipped = Vec::new();

    let mut file = |flag: bool, rel: &str, label: &str| {
        if !flag {
            return;
        }
        let from = source.join(rel);
        if !from.is_file() {
            skipped.push(format!("{label} — not in the source instance"));
            return;
        }
        match std::fs::copy(&from, dest.join(rel)) {
            Ok(_) => copied.push(label.to_string()),
            Err(e) => skipped.push(format!("{label} — {e}")),
        }
    };

    // options.txt carries video settings, keybinds, sound and chat options —
    // the single file that makes a new launcher feel like your old one.
    file(selection.options, "options.txt", "options.txt");
    file(selection.servers, "servers.dat", "server list");

    let mut folder = |flag: bool, rel: &str, label: &str| {
        if !flag {
            return;
        }
        let from = source.join(rel);
        if !from.is_dir() {
            skipped.push(format!("{label} — not in the source instance"));
            return;
        }
        match copy_dir(&from, &dest.join(rel)) {
            Ok(0) => skipped.push(format!("{label} — nothing to copy")),
            Ok(n) => copied.push(format!("{n} {label}")),
            Err(e) => skipped.push(format!("{label} — {e}")),
        }
    };

    folder(selection.resource_packs, "resourcepacks", "resource packs");
    folder(selection.shader_packs, "shaderpacks", "shader packs");
    folder(selection.config, "config", "config files");
    folder(selection.mods, "mods", "mods");
    folder(selection.worlds, "saves", "worlds");

    (copied, skipped)
}

/// Recursive copy, returning how many top-level entries were copied.
///
/// Counts top-level entries rather than files so the report reads "42 resource
/// packs" rather than "6,190 resource packs" — the number a person means.
/// Existing files at the destination are overwritten, which is what "import
/// this over the top" means.
fn copy_dir(from: &Path, to: &Path) -> std::io::Result<usize> {
    std::fs::create_dir_all(to)?;
    let mut top_level = 0;

    for entry in std::fs::read_dir(from)?.flatten() {
        let path = entry.path();
        let Some(file_name) = path.file_name() else {
            continue;
        };
        let target = to.join(file_name);

        if path.is_dir() {
            copy_dir_all(&path, &target)?;
        } else {
            std::fs::copy(&path, &target)?;
        }
        top_level += 1;
    }

    Ok(top_level)
}

fn copy_dir_all(from: &Path, to: &Path) -> std::io::Result<()> {
    std::fs::create_dir_all(to)?;
    for entry in std::fs::read_dir(from)?.flatten() {
        let path = entry.path();
        let Some(file_name) = path.file_name() else {
            continue;
        };
        let target = to.join(file_name);
        if path.is_dir() {
            copy_dir_all(&path, &target)?;
        } else {
            std::fs::copy(&path, &target)?;
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_a_key_out_of_an_instance_cfg() {
        let cfg = "InstanceType=OneSix\nname=My Pack\nOverrideMemory=true\n";
        assert_eq!(ini_value(cfg, "name").as_deref(), Some("My Pack"));
        assert_eq!(ini_value(cfg, "missing"), None);
    }

    #[test]
    fn ignores_whitespace_around_keys_and_values() {
        assert_eq!(ini_value("  name  =  Spaced  \n", "name").as_deref(), Some("Spaced"));
    }

    #[test]
    fn maps_loader_names_onto_our_enum() {
        // NeoForge before Forge: "neoforge" contains "forge", and getting that
        // precedence backwards silently mislabels every NeoForge instance.
        assert_eq!(loader_from_str("neoforge"), Loader::NeoForge);
        assert_eq!(loader_from_str("Forge"), Loader::Forge);
        assert_eq!(loader_from_str("fabric-loader"), Loader::Fabric);
        assert_eq!(loader_from_str("Quilt"), Loader::Quilt);
        assert_eq!(loader_from_str(""), Loader::Vanilla);
    }

    #[test]
    fn finds_a_nested_key_in_launcher_metadata() {
        let json: serde_json::Value = serde_json::from_str(
            r#"{"metadata":{"nested":{"game_version":"1.21.4"}},"other":1}"#,
        )
        .unwrap();
        assert_eq!(find_string(&json, "game_version").as_deref(), Some("1.21.4"));
        assert_eq!(find_string(&json, "nope"), None);
    }

    #[test]
    fn parses_a_prism_pack_file() {
        let dir = std::env::temp_dir().join(format!("blurred-import-{}", uuid::Uuid::new_v4()));
        std::fs::create_dir_all(&dir).unwrap();
        let path = dir.join("mmc-pack.json");
        std::fs::write(
            &path,
            r#"{"components":[
                {"uid":"net.minecraft","version":"1.21.1"},
                {"uid":"net.fabricmc.fabric-loader","version":"0.16.5"}
            ]}"#,
        )
        .unwrap();

        let (version, loader, loader_version) = read_mmc_pack(&path).unwrap();
        assert_eq!(version, "1.21.1");
        assert_eq!(loader, Loader::Fabric);
        assert_eq!(loader_version.as_deref(), Some("0.16.5"));

        std::fs::remove_dir_all(&dir).ok();
    }

    #[test]
    fn only_treats_folders_with_game_content_as_instances() {
        let base = std::env::temp_dir().join(format!("blurred-import-{}", uuid::Uuid::new_v4()));
        let empty = base.join("empty");
        let game = base.join("game");
        std::fs::create_dir_all(&empty).unwrap();
        std::fs::create_dir_all(game.join("saves")).unwrap();

        assert!(!looks_like_game_dir(&empty));
        assert!(looks_like_game_dir(&game));

        std::fs::remove_dir_all(&base).ok();
    }

    #[test]
    fn copies_only_what_was_asked_for_and_reports_the_rest() {
        let base = std::env::temp_dir().join(format!("blurred-import-{}", uuid::Uuid::new_v4()));
        let source = base.join("source");
        let dest = base.join("dest");
        std::fs::create_dir_all(source.join("resourcepacks/pack-a")).unwrap();
        std::fs::write(source.join("resourcepacks/pack-b.zip"), b"z").unwrap();
        std::fs::write(source.join("options.txt"), b"fov:70").unwrap();
        std::fs::create_dir_all(&dest).unwrap();

        let (copied, skipped) = copy_selection(
            &source,
            &dest,
            ImportSelection {
                options: true,
                servers: true,
                resource_packs: true,
                shader_packs: false,
                config: false,
                mods: false,
                worlds: false,
            },
        );

        assert!(dest.join("options.txt").is_file());
        assert!(dest.join("resourcepacks/pack-a").is_dir());
        assert!(dest.join("resourcepacks/pack-b.zip").is_file());
        // Two top-level entries, not "one folder and one file".
        assert!(copied.iter().any(|c| c == "2 resource packs"), "{copied:?}");
        assert!(copied.iter().any(|c| c == "options.txt"), "{copied:?}");
        // servers.dat wasn't there; that's reported, not an error.
        assert!(skipped.iter().any(|s| s.starts_with("server list")), "{skipped:?}");
        // Unticked things are neither copied nor complained about.
        assert!(!dest.join("mods").exists());
        assert!(!skipped.iter().any(|s| s.starts_with("mods")), "{skipped:?}");

        std::fs::remove_dir_all(&base).ok();
    }
}
