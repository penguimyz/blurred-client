//! Launcher self-update: check, download, install, restart.
//!
//! # Where the update comes from
//!
//! GitHub Releases on the repo named by the `update_repo` setting
//! ("owner/name"; empty disables the whole thing). The latest release's tag is
//! compared against `CARGO_PKG_VERSION` — which is why the version in
//! Cargo.toml and the release tag have to move together, or a build reports an
//! update to itself.
//!
//! # Why this installs by running the installer rather than patching in place
//!
//! The earlier version of this file only *reported* that an update existed,
//! with a comment saying auto-install needed signed releases and Tauri's
//! updater plugin. That's true of the plugin's flow, which verifies a
//! minisign signature over an update bundle — infrastructure that doesn't
//! exist here. It is not true of installing at all.
//!
//! What CI already publishes on every tag is exactly what a person would
//! download and run by hand: an NSIS setup .exe on Windows, a .deb/.rpm/
//! .AppImage on Linux. So that is what this does — fetch the asset for the
//! current platform over TLS from GitHub's CDN, then hand it to the platform's
//! own installer, silently where the installer supports it. The trust anchor is
//! HTTPS to api.github.com and objects.githubusercontent.com, the same one the
//! browser download has; nothing here weakens it, and no unsigned payload is
//! executed that the user wasn't already going to run.
//!
//! # The handoff
//!
//! An installer cannot overwrite a running .exe, so installing is necessarily
//! "spawn something detached, then quit". Each platform's spawn waits a couple
//! of seconds before it starts, which is the window the launcher uses to shut
//! down cleanly. See [`launch_installer`].

use std::path::{Path, PathBuf};

use futures_util::StreamExt;
use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter, State};

use crate::state::AppState;

/// Progress and lifecycle events for an install, emitted to the frontend.
pub const PROGRESS_EVENT: &str = "update-progress";

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateStatus {
    pub configured: bool,
    pub current_version: String,
    pub latest_version: Option<String>,
    pub update_available: bool,
    pub url: Option<String>,
    pub notes: Option<String>,
    /// The release asset that would be installed on this platform, if there is
    /// one. Shown so "Install update" can say what it's about to download.
    pub asset_name: Option<String>,
    pub asset_size: Option<u64>,
    /// Whether [`install_launcher_update`] can actually do the install here, or
    /// whether this platform only gets a link to the release page.
    pub can_install: bool,
}

#[derive(Debug, Deserialize)]
struct GhRelease {
    tag_name: String,
    html_url: String,
    #[serde(default)]
    body: String,
    #[serde(default)]
    assets: Vec<GhAsset>,
}

#[derive(Debug, Clone, Deserialize)]
struct GhAsset {
    name: String,
    browser_download_url: String,
    #[serde(default)]
    size: u64,
}

/// How the picked asset gets installed. Kept separate from the file extension
/// because .exe and .msi are both "Windows" but take completely different
/// command lines, and .AppImage is an update-in-place rather than an install.
///
/// Every variant exists on every platform even though only one platform's
/// variants can ever be constructed there — hence the allow. Splitting the enum
/// per target would mean cfg-ing the struct that holds it, the match that
/// dispatches on it and the tests, to remove three lines of dead data.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[allow(dead_code)]
enum InstallKind {
    /// Tauri's NSIS setup.exe. Supports `/S` for a silent install.
    WindowsSetup,
    /// Windows Installer package. `msiexec /i … /passive`.
    WindowsMsi,
    /// Replace the running AppImage with the new one and re-exec it.
    AppImage,
    /// A .deb or .rpm. Handed to the desktop's package installer — installing
    /// system packages needs root, and silently escalating for it is not
    /// something a game launcher gets to do.
    SystemPackage,
}

impl InstallKind {
    /// Does installing this end the current process?
    ///
    /// Everything except the distro package handoff does: the installer needs
    /// our files unlocked. A .deb opened in the software centre is a window the
    /// user then drives themselves, so quitting under them would be rude and
    /// would also lose the download.
    fn restarts(self) -> bool {
        self != InstallKind::SystemPackage
    }
}

#[tauri::command]
pub async fn check_launcher_update(state: State<'_, AppState>) -> Result<UpdateStatus, String> {
    let current = env!("CARGO_PKG_VERSION").to_string();
    let repo = state.settings.lock().unwrap().update_repo.trim().to_string();

    if repo.is_empty() {
        return Ok(UpdateStatus {
            configured: false,
            current_version: current,
            latest_version: None,
            update_available: false,
            url: None,
            notes: None,
            asset_name: None,
            asset_size: None,
            can_install: false,
        });
    }

    let rel = match fetch_latest(&repo).await? {
        Some(rel) => rel,
        None => {
            return Ok(UpdateStatus {
                configured: true,
                current_version: current,
                latest_version: None,
                update_available: false,
                url: None,
                notes: Some("No published releases for this repo yet.".to_string()),
                asset_name: None,
                asset_size: None,
                can_install: false,
            })
        }
    };

    let latest = rel.tag_name.trim_start_matches('v').to_string();
    let update_available = version_gt(&latest, &current);
    let picked = pick_asset(&rel.assets);

    Ok(UpdateStatus {
        configured: true,
        current_version: current,
        latest_version: Some(latest),
        update_available,
        url: Some(rel.html_url),
        notes: if rel.body.trim().is_empty() { None } else { Some(rel.body) },
        asset_name: picked.as_ref().map(|(a, _)| a.name.clone()),
        asset_size: picked.as_ref().map(|(a, _)| a.size),
        can_install: update_available && picked.is_some(),
    })
}

/// Download the latest release's installer and run it.
///
/// On success this does not return in the usual sense: the process exits so the
/// installer can replace it. The one exception is the .deb/.rpm handoff, which
/// leaves the launcher running while the system's installer takes over.
#[tauri::command]
pub async fn install_launcher_update(
    app: AppHandle,
    state: State<'_, AppState>,
) -> Result<(), String> {
    let current = env!("CARGO_PKG_VERSION").to_string();
    let repo = state.settings.lock().unwrap().update_repo.trim().to_string();
    if repo.is_empty() {
        return Err("No update source configured.".to_string());
    }

    let rel = fetch_latest(&repo)
        .await?
        .ok_or_else(|| "That repo has no published releases.".to_string())?;
    let latest = rel.tag_name.trim_start_matches('v').to_string();
    if !version_gt(&latest, &current) {
        return Err(format!("Already on the latest version ({current})."));
    }

    let (asset, kind) = pick_asset(&rel.assets).ok_or_else(|| {
        format!(
            "Release {} has nothing installable on this platform. Download it from the release page instead.",
            rel.tag_name
        )
    })?;

    emit(&app, "download", 0, asset.size, Some(&asset.name));
    let file = download(&app, &asset).await?;

    emit(&app, "install", asset.size, asset.size, Some(&asset.name));
    launch_installer(&file, kind)?;

    if kind.restarts() {
        // The installer is waiting on us to let go of our own files. Nothing
        // after this point runs.
        app.exit(0);
    }
    Ok(())
}

fn emit(app: &AppHandle, phase: &str, done: u64, total: u64, asset: Option<&str>) {
    let _ = app.emit(
        PROGRESS_EVENT,
        serde_json::json!({ "phase": phase, "done": done, "total": total, "asset": asset }),
    );
}

async fn fetch_latest(repo: &str) -> Result<Option<GhRelease>, String> {
    let client = reqwest::Client::new();
    let resp = client
        .get(format!("https://api.github.com/repos/{repo}/releases/latest"))
        .header("User-Agent", "blurred-client")
        .header("Accept", "application/vnd.github+json")
        .send()
        .await
        .map_err(|e| e.to_string())?;

    if resp.status() == reqwest::StatusCode::NOT_FOUND {
        return Ok(None);
    }
    if !resp.status().is_success() {
        return Err(format!("GitHub returned {}", resp.status()));
    }
    resp.json::<GhRelease>().await.map(Some).map_err(|e| e.to_string())
}

/// Stream the asset to a temp file, reporting progress as it goes.
///
/// Streamed rather than `bytes()` in one go for the progress bar: these are
/// 5–90MB downloads and a dialog that sits at 0% until it finishes reads as a
/// hang. The file is written next to the OS temp dir under our own name, so a
/// failed install leaves one obvious file rather than a scattering.
async fn download(app: &AppHandle, asset: &GhAsset) -> Result<PathBuf, String> {
    let dir = std::env::temp_dir().join("blurred-client-update");
    std::fs::create_dir_all(&dir).map_err(|e| format!("could not create {}: {e}", dir.display()))?;
    let target = dir.join(&asset.name);

    let resp = reqwest::Client::new()
        .get(&asset.browser_download_url)
        .header("User-Agent", "blurred-client")
        .send()
        .await
        .map_err(|e| format!("download failed: {e}"))?
        .error_for_status()
        .map_err(|e| format!("download failed: {e}"))?;

    // Content-Length is the authority here; the release metadata's `size` is a
    // fine estimate but the redirect to the CDN is what actually serves bytes.
    let total = resp.content_length().unwrap_or(asset.size);

    let mut file = std::fs::File::create(&target)
        .map_err(|e| format!("could not write {}: {e}", target.display()))?;
    let mut stream = resp.bytes_stream();
    let mut done: u64 = 0;
    // Only emit on a whole percent of movement — a chunk-by-chunk event storm
    // makes the webview do more work repainting the bar than we do downloading.
    let mut last_emit = 0u64;

    while let Some(chunk) = stream.next().await {
        let chunk = chunk.map_err(|e| format!("download interrupted: {e}"))?;
        use std::io::Write;
        file.write_all(&chunk).map_err(|e| format!("could not write update: {e}"))?;
        done += chunk.len() as u64;

        if done - last_emit >= (total / 100).max(64 * 1024) {
            last_emit = done;
            emit(app, "download", done, total, Some(&asset.name));
        }
    }
    file.sync_all().ok();
    drop(file);

    if total > 0 && done < total {
        std::fs::remove_file(&target).ok();
        return Err(format!("download ended early ({done} of {total} bytes)"));
    }

    Ok(target)
}

/// Which release asset applies to the machine we're running on.
///
/// Matching is on the file extension rather than on the full name, so the
/// version number and the bundler's naming (`_x64-setup.exe`, `-1.x86_64.rpm`)
/// can change without breaking updates.
fn pick_asset(assets: &[GhAsset]) -> Option<(GhAsset, InstallKind)> {
    let find = |suffix: &str| {
        assets
            .iter()
            .find(|a| a.name.to_ascii_lowercase().ends_with(suffix))
            .cloned()
    };

    #[cfg(target_os = "windows")]
    {
        // NSIS first: it installs silently with `/S`, keeps the existing
        // install location, and is the artifact CI publishes for Windows. The
        // MSI is the fallback for a release that only has one.
        if let Some(a) = find("-setup.exe") {
            return Some((a, InstallKind::WindowsSetup));
        }
        if let Some(a) = find(".msi") {
            return Some((a, InstallKind::WindowsMsi));
        }
        None
    }

    #[cfg(target_os = "linux")]
    {
        // Running from an AppImage means the update is a file swap we can do
        // ourselves, with no root and no package manager. Prefer it whenever
        // that's how we were started, whatever the distro is.
        if std::env::var_os("APPIMAGE").is_some() {
            if let Some(a) = find(".appimage") {
                return Some((a, InstallKind::AppImage));
            }
        }
        if Path::new("/etc/debian_version").exists() {
            if let Some(a) = find(".deb") {
                return Some((a, InstallKind::SystemPackage));
            }
        }
        if Path::new("/etc/redhat-release").exists() || Path::new("/etc/fedora-release").exists() {
            if let Some(a) = find(".rpm") {
                return Some((a, InstallKind::SystemPackage));
            }
        }
        // Unknown distro: the AppImage runs anywhere, so it's the safe default
        // even when we weren't launched from one.
        find(".appimage").map(|a| (a, InstallKind::AppImage))
    }

    #[cfg(not(any(target_os = "windows", target_os = "linux")))]
    {
        let _ = find;
        None
    }
}

/// Spawn the installer detached and return immediately.
///
/// Every branch delays a few seconds before doing anything. That delay is the
/// whole mechanism: the caller exits as soon as this returns, and the installer
/// needs our executable unlocked before it starts copying over it. Spawning and
/// exiting with no delay is the classic way this fails on Windows, with the
/// installer reporting that the application is in use.
fn launch_installer(file: &Path, kind: InstallKind) -> Result<(), String> {
    let path = file.display().to_string();

    match kind {
        #[cfg(target_os = "windows")]
        InstallKind::WindowsSetup | InstallKind::WindowsMsi => {
            // PowerShell rather than a bare spawn so the sleep, the detach and
            // the relaunch are one command that outlives us. Single quotes are
            // doubled because that's PowerShell's escape inside a literal
            // string — a path with an apostrophe in the username otherwise
            // ends the argument early.
            let quoted = path.replace('\'', "''");
            let script = if kind == InstallKind::WindowsSetup {
                // `/S` is NSIS silent mode. Tauri's installer template closes a
                // running instance itself, but we're already gone by then.
                format!("Start-Sleep -Seconds 3; Start-Process -FilePath '{quoted}' -ArgumentList '/S'")
            } else {
                format!(
                    "Start-Sleep -Seconds 3; Start-Process -FilePath 'msiexec.exe' -ArgumentList '/i','\"{quoted}\"','/passive'"
                )
            };
            spawn_detached("powershell", &["-NoProfile", "-WindowStyle", "Hidden", "-Command", &script])
        }

        #[cfg(target_os = "linux")]
        InstallKind::AppImage => {
            // Swap the new image over the one we were launched from, then start
            // it. `mv` within the same directory is atomic, so a crash mid-way
            // leaves either the old or the new image, never half of one.
            let current = std::env::var("APPIMAGE")
                .map_err(|_| "not running from an AppImage — install it manually".to_string())?;
            let script = format!(
                "sleep 3; chmod +x '{new}' && mv -f '{new}' '{cur}' && exec '{cur}'",
                new = path.replace('\'', "'\\''"),
                cur = current.replace('\'', "'\\''"),
            );
            spawn_detached("sh", &["-c", &script])
        }

        #[cfg(target_os = "linux")]
        InstallKind::SystemPackage => {
            // Hand it to whatever the desktop uses for packages. Installing
            // needs root; prompting for a password out of a game launcher is
            // the system's job to present, not ours to collect.
            spawn_detached("xdg-open", &[&path])
        }

        #[allow(unreachable_patterns)]
        _ => Err("This platform can't install updates automatically.".to_string()),
    }
}

fn spawn_detached(program: &str, args: &[&str]) -> Result<(), String> {
    let mut cmd = std::process::Command::new(program);
    cmd.args(args);

    // Don't let the helper inherit our console window on Windows — the flash of
    // a terminal while the launcher closes looks like a crash.
    #[cfg(target_os = "windows")]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        const DETACHED_PROCESS: u32 = 0x0000_0008;
        cmd.creation_flags(CREATE_NO_WINDOW | DETACHED_PROCESS);
    }

    cmd.spawn()
        .map(|_| ())
        .map_err(|e| format!("could not start the installer ({program}): {e}"))
}

/// Naive dotted-numeric version compare (ignores any `-suffix`). Good enough to
/// tell "is the release newer than what's running".
fn version_gt(a: &str, b: &str) -> bool {
    fn parts(v: &str) -> Vec<u32> {
        v.split('.')
            .map(|seg| seg.split('-').next().unwrap_or("").parse::<u32>().unwrap_or(0))
            .collect()
    }
    let (pa, pb) = (parts(a), parts(b));
    for i in 0..pa.len().max(pb.len()) {
        let (x, y) = (pa.get(i).copied().unwrap_or(0), pb.get(i).copied().unwrap_or(0));
        if x != y {
            return x > y;
        }
    }
    false
}

#[cfg(test)]
mod tests {
    use super::*;

    fn asset(name: &str) -> GhAsset {
        GhAsset {
            name: name.to_string(),
            browser_download_url: format!("https://example.invalid/{name}"),
            size: 1,
        }
    }

    /// The names CI actually publishes, as of v0.3.2.
    fn release_assets() -> Vec<GhAsset> {
        vec![
            asset("Blurred.Client-0.3.2-1.x86_64.rpm"),
            asset("Blurred.Client_0.3.2_amd64.AppImage"),
            asset("Blurred.Client_0.3.2_amd64.deb"),
            asset("Blurred.Client_0.3.2_x64-setup.exe"),
            asset("Blurred.Client_0.3.2_x64_en-US.msi"),
        ]
    }

    #[test]
    fn picks_something_installable_from_a_real_release() {
        let picked = pick_asset(&release_assets());
        // Every platform this ships on has an artifact in that list.
        assert!(picked.is_some());
        if cfg!(target_os = "windows") {
            let (a, kind) = picked.unwrap();
            // The NSIS installer wins over the MSI: it can install silently.
            assert_eq!(a.name, "Blurred.Client_0.3.2_x64-setup.exe");
            assert_eq!(kind, InstallKind::WindowsSetup);
        }
    }

    #[test]
    fn no_asset_for_this_platform_is_not_a_crash() {
        assert!(pick_asset(&[asset("source-code.tar.gz")]).is_none());
        assert!(pick_asset(&[]).is_none());
    }

    #[test]
    fn compares_versions_numerically_not_lexically() {
        assert!(version_gt("0.3.10", "0.3.9"));
        assert!(version_gt("0.4.0", "0.3.99"));
        assert!(!version_gt("0.3.2", "0.3.2"));
        assert!(!version_gt("0.3.1", "0.3.2"));
        // A tag with fewer segments than the running version.
        assert!(version_gt("1", "0.9.9"));
        assert!(!version_gt("0.3.2-beta", "0.3.2"));
    }
}
