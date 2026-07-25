// Modrinth's API needs no key, which is why it's the only mod source now
// (CurseForge was removed -- see ROADMAP.md decision log). Not tested
// against the live endpoint from this sandbox for the same network-access
// reason noted in mojang.rs -- verify the shape of a real response before
// trusting this blind.
//
// This module owns the whole Modrinth surface: search (below), plus resolving a
// project's versions, downloading + installing a mod into an instance (with
// recursive required-dependency resolution), and checking/applying updates. All
// keyless, all public API.

use std::collections::HashSet;

use serde::{Deserialize, Serialize};
use tauri::State;

use crate::commands::instance::{find_instance, save_instance};
use crate::commands::mojang::download_if_missing;
use crate::models::instance::{Instance, Loader, ModRef, ModSource};
use crate::state::AppState;

const MODRINTH_API_BASE: &str = "https://api.modrinth.com/v2";
const USER_AGENT: &str = "blurred-client/0.1.0 (dev build)";

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SearchResult {
    pub hits: Vec<ProjectHit>,
    pub total_hits: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct ProjectHit {
    pub project_id: String,
    pub slug: String,
    pub title: String,
    pub description: String,
    pub icon_url: Option<String>,
    pub downloads: u64,
    pub categories: Vec<String>,
    pub versions: Vec<String>, // supported MC versions
    pub project_type: String,  // "mod" | "modpack" | "resourcepack" | "shader"
}

/// Search Modrinth. An empty `query` combined with `index = "downloads"` is how
/// the Browse page shows "popular" — Modrinth returns the most-downloaded
/// projects for the given facets. `categories` are ANDed together (each its own
/// facet group), same as the loader/version facets.
#[tauri::command]
pub async fn modrinth_search(
    query: String,
    mc_version: Option<String>,
    loader: Option<String>,
    project_type: Option<String>,
    categories: Option<Vec<String>>,
    index: Option<String>, // relevance | downloads | follows | newest | updated
) -> Result<SearchResult, String> {
    let client = reqwest::Client::new();

    let mut facets: Vec<Vec<String>> = Vec::new();
    if let Some(v) = &mc_version {
        facets.push(vec![format!("versions:{v}")]);
    }
    if let Some(l) = &loader {
        facets.push(vec![format!("categories:{l}")]);
    }
    if let Some(t) = &project_type {
        facets.push(vec![format!("project_type:{t}")]);
    }
    for c in categories.unwrap_or_default() {
        facets.push(vec![format!("categories:{c}")]);
    }

    let facets_json = serde_json::to_string(&facets).map_err(|e| e.to_string())?;

    let mut query_params: Vec<(&str, String)> = vec![
        ("query", query),
        ("facets", facets_json),
        ("limit", "40".to_string()),
    ];
    // Default to "downloads" so the page leads with popular projects.
    query_params.push(("index", index.unwrap_or_else(|| "downloads".to_string())));

    let resp = client
        .get(format!("{MODRINTH_API_BASE}/search"))
        .query(&query_params)
        .header("User-Agent", USER_AGENT)
        .send()
        .await
        .map_err(|e| e.to_string())?;

    if !resp.status().is_success() {
        return Err(format!("Modrinth API returned {}", resp.status()));
    }

    resp.json::<SearchResult>().await.map_err(|e| e.to_string())
}

// ---- version resolution + install + updates ----

#[derive(Debug, Clone, Deserialize)]
pub struct ModrinthVersion {
    pub id: String,
    /// The real project id — the canonical identity used for de-duplication, so a
    /// project requested by slug and again as a dependency (by id) is one install.
    #[serde(default)]
    pub project_id: String,
    pub name: String,
    pub version_number: String,
    #[serde(default)]
    pub date_published: String,
    pub files: Vec<ModrinthFile>,
    #[serde(default)]
    pub dependencies: Vec<ModrinthDependency>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ModrinthFile {
    pub url: String,
    pub filename: String,
    #[serde(default)]
    pub primary: bool,
    #[serde(default)]
    pub size: u64,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ModrinthDependency {
    pub project_id: Option<String>,
    pub version_id: Option<String>,
    pub dependency_type: String, // "required" | "optional" | "incompatible" | "embedded"
}

/// Modrinth loader slug for an instance's loader. Vanilla has no loader, so mod
/// version queries for it aren't loader-filtered (they'll generally come back
/// empty for actual mods, which surfaces as a clear "no compatible version").
fn loader_slug(loader: Loader) -> Option<&'static str> {
    match loader {
        Loader::Vanilla => None,
        Loader::Fabric => Some("fabric"),
        Loader::Forge => Some("forge"),
        Loader::Quilt => Some("quilt"),
        Loader::NeoForge => Some("neoforge"),
    }
}

/// Fetch a project's versions filtered to this MC version (+ loader when the
/// instance has one), newest first. `project` may be a project id or slug.
async fn fetch_project_versions(
    client: &reqwest::Client,
    project: &str,
    mc_version: &str,
    loader: Option<&str>,
) -> Result<Vec<ModrinthVersion>, String> {
    let game_versions = serde_json::to_string(&[mc_version]).map_err(|e| e.to_string())?;
    let mut query: Vec<(&str, String)> = vec![("game_versions", game_versions)];
    if let Some(l) = loader {
        query.push(("loaders", serde_json::to_string(&[l]).map_err(|e| e.to_string())?));
    }

    let resp = client
        .get(format!("{MODRINTH_API_BASE}/project/{project}/version"))
        .query(&query)
        .header("User-Agent", USER_AGENT)
        .send()
        .await
        .map_err(|e| e.to_string())?;
    if !resp.status().is_success() {
        return Err(format!("Modrinth API returned {} for project {project}", resp.status()));
    }
    let mut versions: Vec<ModrinthVersion> = resp.json().await.map_err(|e| e.to_string())?;
    // Newest first. Modrinth usually returns this order already, but the field
    // is RFC3339 so a lexical sort is a safe, cheap guarantee.
    versions.sort_by(|a, b| b.date_published.cmp(&a.date_published));
    Ok(versions)
}

fn pick_file(v: &ModrinthVersion) -> Option<&ModrinthFile> {
    v.files.iter().find(|f| f.primary).or_else(|| v.files.first())
}

/// Core installer: from `start_project` (id or slug), download the newest
/// version matching this instance's MC version + loader and record a ModRef;
/// when `with_dependencies`, walk the required-dependency graph too. `visited`
/// is shared across calls so a bulk install doesn't re-fetch shared deps.
async fn resolve_and_download(
    client: &reqwest::Client,
    instance: &mut Instance,
    mods_dir: &std::path::Path,
    mc: &str,
    loader: Option<&str>,
    start_project: String,
    start_version: Option<String>,
    with_dependencies: bool,
    visited: &mut HashSet<String>,
) -> Result<(), String> {
    // `fetched` guards against re-fetching the same alias (slug/id) within this
    // walk; `visited` (shared across calls) tracks canonical project ids that are
    // already installed, so nothing is installed twice under two names.
    let mut fetched: HashSet<String> = HashSet::new();
    let mut queue: Vec<(String, Option<String>)> = vec![(start_project, start_version)];

    while let Some((pid, vid)) = queue.pop() {
        if !fetched.insert(pid.clone()) {
            continue;
        }

        let versions = fetch_project_versions(client, &pid, mc, loader).await?;
        let version = match vid {
            Some(ref want) => versions.into_iter().find(|v| &v.id == want),
            None => versions.into_iter().next(),
        }
        .ok_or_else(|| format!("no {mc} / {loader:?} version available for project {pid}"))?;

        // Canonical identity, regardless of whether we were asked by slug or id.
        let canonical = if version.project_id.is_empty() {
            pid.clone()
        } else {
            version.project_id.clone()
        };
        // Already installed under some name (seed or an earlier alias)? Skip —
        // this is what stops the duplicate-mod crash (e.g. ukulib via slug + dep).
        if !visited.insert(canonical.clone()) {
            continue;
        }

        let file = pick_file(&version)
            .ok_or_else(|| format!("project {pid} version has no downloadable file"))?
            .clone();

        // Drop any prior tracked copy of this project (and its old jar) first.
        if let Some(old) = instance.mods.iter().find(|m| m.id == canonical).map(|m| m.filename.clone()) {
            if old != file.filename {
                let _ = std::fs::remove_file(mods_dir.join(&old));
                let _ = std::fs::remove_file(mods_dir.join(format!("{old}.disabled")));
            }
        }
        instance.mods.retain(|m| m.id != canonical);

        download_if_missing(client, &file.url, &mods_dir.join(&file.filename), file.size)
            .await
            .map_err(|e| e.to_string())?;

        instance.mods.push(ModRef {
            id: canonical.clone(),
            filename: file.filename.clone(),
            name: version.name.clone(),
            version: version.version_number.clone(),
            enabled: true,
            source: ModSource::Modrinth,
            pinned: false,
            version_id: Some(version.id.clone()),
        });

        if with_dependencies {
            for dep in &version.dependencies {
                if dep.dependency_type == "required" {
                    if let Some(dpid) = &dep.project_id {
                        if !visited.contains(dpid) {
                            queue.push((dpid.clone(), dep.version_id.clone()));
                        }
                    }
                }
            }
        }
    }
    Ok(())
}

fn tracked_modrinth_ids(instance: &Instance) -> HashSet<String> {
    instance
        .mods
        .iter()
        .filter(|m| m.source == ModSource::Modrinth)
        .map(|m| m.id.clone())
        .collect()
}

/// Install a single Modrinth project into an instance (Browse → Install). Picks
/// the newest matching version, resolves required dependencies, and refreshes the
/// project even if it's already installed.
#[tauri::command]
pub async fn install_modrinth_mod(
    state: State<'_, AppState>,
    instance_id: String,
    project_id: String,
    version_id: Option<String>,
    with_dependencies: bool,
) -> Result<Instance, String> {
    let (dir, mut instance) = find_instance(&state, &instance_id)?;
    let mods_dir = dir.join("mods");
    std::fs::create_dir_all(&mods_dir).map_err(|e| e.to_string())?;

    let client = reqwest::Client::new();
    let loader = loader_slug(instance.loader);
    let mc = instance.mc_version.clone();

    let mut visited = tracked_modrinth_ids(&instance);
    visited.remove(&project_id); // force refresh of the explicitly-requested project
    resolve_and_download(&client, &mut instance, &mods_dir, &mc, loader, project_id, version_id, with_dependencies, &mut visited).await?;

    instance.mods.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
    save_instance(&dir, &instance).map_err(|e| e.to_string())?;
    Ok(instance)
}

/// Install many projects (ids or slugs) at once — used for the default modpack.
/// Failures are per-project (a mod with no build for this MC version is skipped)
/// so one bad slug never sinks the batch; the count of failures is returned via
/// the log-free `Result` only when *nothing* installed.
#[tauri::command]
pub async fn install_mods(
    state: State<'_, AppState>,
    instance_id: String,
    projects: Vec<String>,
    with_dependencies: bool,
) -> Result<Instance, String> {
    let (dir, mut instance) = find_instance(&state, &instance_id)?;
    let mods_dir = dir.join("mods");
    std::fs::create_dir_all(&mods_dir).map_err(|e| e.to_string())?;

    let client = reqwest::Client::new();
    let loader = loader_slug(instance.loader);
    let mc = instance.mc_version.clone();

    let mut visited = tracked_modrinth_ids(&instance);
    let mut installed_any = false;
    let mut last_err: Option<String> = None;
    for p in projects {
        match resolve_and_download(&client, &mut instance, &mods_dir, &mc, loader, p, None, with_dependencies, &mut visited).await {
            Ok(()) => installed_any = true,
            Err(e) => last_err = Some(e),
        }
    }

    instance.mods.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
    save_instance(&dir, &instance).map_err(|e| e.to_string())?;

    if !installed_any {
        if let Some(e) = last_err {
            return Err(format!("no mods could be installed — is this a Fabric instance on a supported version? ({e})"));
        }
    }
    Ok(instance)
}

/// The "Blurred Essentials" default modpack (spec §5.4): a Fabric performance +
/// QoL set, by Modrinth slug. Offered when creating a new instance.
pub const BLURRED_ESSENTIALS: &[&str] = &[
    "fabric-api",
    "fabric-language-kotlin",
    "sodium",
    "lithium",
    "ferrite-core",
    "modernfix",
    "immediatelyfast",
    "moreculling",
    "morecullingextra",
    "entityculling",
    "krypton",
    "dynamic-fps",
    "cloth-config",
    "yacl",
    "modmenu",
    "jade",
    "appleskin",
    "reeses-sodium-options",
    "zoomify",
    "controlling",
    "clumps",
    "shulkerboxtooltip",
    "malilib",
    "simple-voice-chat",
    "jei",
    "searchables",
    "combat-hitboxes",
    "consumableoptimizer",
    "complete-shield-fixes",
    "gamma-utils",
    "walksylib",
    "ukulib",
    "ukus-armor-hud",
    "horse-statistics",
];

#[tauri::command]
pub fn blurred_essentials() -> Vec<String> {
    BLURRED_ESSENTIALS.iter().map(|s| s.to_string()).collect()
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ModUpdate {
    pub filename: String,
    pub name: String,
    pub current_version: String,
    pub latest_version: String,
    pub latest_version_id: String,
}

/// Check every non-pinned Modrinth mod for a newer version matching the
/// instance's MC version + loader. Read-only — downloads nothing.
#[tauri::command]
pub async fn check_mod_updates(
    state: State<'_, AppState>,
    instance_id: String,
) -> Result<Vec<ModUpdate>, String> {
    let (_, instance) = find_instance(&state, &instance_id)?;
    let client = reqwest::Client::new();
    let loader = loader_slug(instance.loader);
    let mc = instance.mc_version.clone();

    let mut out = Vec::new();
    for m in &instance.mods {
        if m.source != ModSource::Modrinth || m.pinned {
            continue;
        }
        // A failed lookup for one mod shouldn't sink the whole check.
        let Ok(versions) = fetch_project_versions(&client, &m.id, &mc, loader).await else {
            continue;
        };
        if let Some(latest) = versions.into_iter().next() {
            let is_newer = match &m.version_id {
                Some(vid) => latest.id != *vid,
                None => latest.version_number != m.version,
            };
            if is_newer {
                out.push(ModUpdate {
                    filename: m.filename.clone(),
                    name: m.name.clone(),
                    current_version: m.version.clone(),
                    latest_version: latest.version_number,
                    latest_version_id: latest.id,
                });
            }
        }
    }
    Ok(out)
}

/// Download the newest matching version of one tracked Modrinth mod, swap it in
/// (removing the old jar), and update the ModRef. Preserves enabled/disabled and
/// pinned state.
async fn upgrade_one(
    client: &reqwest::Client,
    mods_dir: &std::path::Path,
    instance: &mut Instance,
    idx: usize,
    mc: &str,
    loader: Option<&str>,
) -> Result<bool, String> {
    let (project, old_filename, was_enabled, cur_vid, cur_ver) = {
        let m = &instance.mods[idx];
        (m.id.clone(), m.filename.clone(), m.enabled, m.version_id.clone(), m.version.clone())
    };

    let versions = fetch_project_versions(client, &project, mc, loader).await?;
    let Some(latest) = versions.into_iter().next() else {
        return Ok(false);
    };
    let is_newer = match &cur_vid {
        Some(v) => latest.id != *v,
        None => latest.version_number != cur_ver,
    };
    if !is_newer {
        return Ok(false);
    }
    let Some(file) = pick_file(&latest).cloned() else {
        return Ok(false);
    };

    // Remove the old jar (either enabled or disabled form), then download fresh.
    for c in [mods_dir.join(&old_filename), mods_dir.join(format!("{old_filename}.disabled"))] {
        if c.exists() {
            let _ = std::fs::remove_file(&c);
        }
    }
    let disk_name = if was_enabled { file.filename.clone() } else { format!("{}.disabled", file.filename) };
    download_if_missing(client, &file.url, &mods_dir.join(&disk_name), file.size)
        .await
        .map_err(|e| e.to_string())?;

    let m = &mut instance.mods[idx];
    m.filename = file.filename.clone();
    m.name = latest.name.clone();
    m.version = latest.version_number.clone();
    m.version_id = Some(latest.id.clone());
    Ok(true)
}

#[tauri::command]
pub async fn update_mod(
    state: State<'_, AppState>,
    instance_id: String,
    filename: String,
) -> Result<Instance, String> {
    let (dir, mut instance) = find_instance(&state, &instance_id)?;
    let idx = instance
        .mods
        .iter()
        .position(|m| m.filename == filename)
        .ok_or_else(|| format!("mod {filename} not tracked on this instance"))?;
    if instance.mods[idx].source != ModSource::Modrinth {
        return Err("only Modrinth mods can be auto-updated".to_string());
    }
    let client = reqwest::Client::new();
    let loader = loader_slug(instance.loader);
    let mc = instance.mc_version.clone();
    upgrade_one(&client, &dir.join("mods"), &mut instance, idx, &mc, loader).await?;
    save_instance(&dir, &instance).map_err(|e| e.to_string())?;
    Ok(instance)
}

#[tauri::command]
pub async fn update_all_mods(
    state: State<'_, AppState>,
    instance_id: String,
) -> Result<Instance, String> {
    let (dir, mut instance) = find_instance(&state, &instance_id)?;
    let client = reqwest::Client::new();
    let loader = loader_slug(instance.loader);
    let mc = instance.mc_version.clone();
    let mods_dir = dir.join("mods");

    let targets: Vec<usize> = instance
        .mods
        .iter()
        .enumerate()
        .filter(|(_, m)| m.source == ModSource::Modrinth && !m.pinned)
        .map(|(i, _)| i)
        .collect();

    for i in targets {
        // Skip failures per-mod so one unreachable project doesn't abort the batch.
        let _ = upgrade_one(&client, &mods_dir, &mut instance, i, &mc, loader).await;
    }
    save_instance(&dir, &instance).map_err(|e| e.to_string())?;
    Ok(instance)
}
