//! Getting a JVM without asking the user to install one.
//!
//! Requiring people to go and install Java 21 before they can play is a real
//! barrier, and an unnecessary one: Mojang publishes the exact JRE each
//! Minecraft version wants, for each platform, and the version JSON says which
//! one. Prism does this, the official launcher does this, and now so does this.
//!
//! # Order of preference
//!
//! 1. **A path the user set.** Explicitly chosen, so it wins outright — even if
//!    its major version looks wrong. It gets a warning in the log, not an
//!    override; someone who typed a path meant it.
//! 2. **A runtime we already downloaded.** Kept per component under
//!    `<data>/java/<component>/`, so it's shared by every instance that needs
//!    that major version instead of downloaded per instance.
//! 3. **A system JVM of the right major version.** No reason to download 200MB
//!    when a suitable JDK is already installed. An exact major match is
//!    preferred; a newer one is accepted.
//! 4. **Download it.**
//!
//! # What this cannot do
//!
//! Mojang publishes no runtime for 64-bit ARM Linux. On that platform the
//! download step fails with a message saying so, and the user does have to
//! install a JVM themselves — which is the honest answer, rather than
//! downloading an x86 build that won't run.

use std::collections::HashMap;
use std::path::{Path, PathBuf};

use serde::Deserialize;
use tauri::{AppHandle, Emitter};

use crate::commands::java::detect_installed_javas;

/// Mojang's index of every JRE they publish, per platform.
///
/// The hash in the path pins a specific revision of the index. It's the URL the
/// official launcher itself uses; Mojang publishes a new one when they add
/// runtimes, and the old one keeps working.
const RUNTIME_INDEX: &str =
    "https://piston-meta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

/// How many files to fetch at once. A JRE is around a thousand small files, so
/// this is the difference between seconds and minutes; much higher and we're
/// just being rude to Mojang's CDN.
const PARALLEL_DOWNLOADS: usize = 8;

// ---------------------------------------------------------------------------
// Manifest shapes
// ---------------------------------------------------------------------------

/// platform -> component -> available builds.
type RuntimeIndex = HashMap<String, HashMap<String, Vec<RuntimeBuild>>>;

#[derive(Debug, Clone, Deserialize)]
struct RuntimeBuild {
    manifest: DownloadRef,
    version: RuntimeVersion,
}

#[derive(Debug, Clone, Deserialize)]
struct RuntimeVersion {
    name: String,
}

#[derive(Debug, Clone, Deserialize)]
struct DownloadRef {
    url: String,
    #[serde(default)]
    sha1: String,
    #[serde(default)]
    size: u64,
}

#[derive(Debug, Deserialize)]
struct RuntimeManifest {
    files: HashMap<String, RuntimeEntry>,
}

#[derive(Debug, Deserialize)]
#[serde(tag = "type", rename_all = "lowercase")]
enum RuntimeEntry {
    Directory,
    File {
        downloads: FileDownloads,
        #[serde(default)]
        executable: bool,
    },
    Link {
        target: String,
    },
}

#[derive(Debug, Deserialize)]
struct FileDownloads {
    /// The uncompressed copy. Mojang also offers an `lzma` variant, which is
    /// roughly half the bytes and would need an LZMA decoder as a dependency —
    /// not worth it for a download that happens once.
    raw: DownloadRef,
}

// ---------------------------------------------------------------------------
// Platform + component mapping
// ---------------------------------------------------------------------------

/// Mojang's key for this machine, or None if they don't publish for it.
fn platform_key() -> Option<&'static str> {
    match (std::env::consts::OS, std::env::consts::ARCH) {
        ("windows", "x86_64") => Some("windows-x64"),
        ("windows", "x86") => Some("windows-x86"),
        ("windows", "aarch64") => Some("windows-arm64"),
        ("linux", "x86_64") => Some("linux"),
        ("linux", "x86") => Some("linux-i386"),
        ("macos", "x86_64") => Some("mac-os"),
        ("macos", "aarch64") => Some("mac-os-arm64"),
        // Notably 64-bit ARM Linux: Mojang publishes nothing for it.
        _ => None,
    }
}

/// Which runtime component covers a given Java major version.
///
/// Used when the version JSON doesn't name one — anything before 1.17 predates
/// the field entirely.
pub fn component_for_major(major: u32) -> &'static str {
    match major {
        0..=8 => "jre-legacy",
        9..=16 => "java-runtime-alpha",
        17..=20 => "java-runtime-gamma",
        _ => "java-runtime-delta",
    }
}

/// The Java major version a Minecraft version needs.
///
/// A lookup rather than a download: used where fetching and parsing the version
/// JSON just to read one integer isn't worth a network round trip — the server
/// launcher, mainly. The boundaries are Mojang's own: 1.20.5 moved to 21, 1.18
/// moved to 17, and everything older is happy on 8.
pub fn major_for_mc_version(version: &str) -> u32 {
    let parts: Vec<u32> = version
        .split('.')
        .map(|p| p.split('-').next().unwrap_or(""))
        .map(|p| p.parse().unwrap_or(0))
        .collect();

    let minor = parts.get(1).copied().unwrap_or(0);
    let patch = parts.get(2).copied().unwrap_or(0);

    if minor > 20 || (minor == 20 && patch >= 5) {
        21
    } else if minor >= 18 {
        17
    } else if minor >= 17 {
        16
    } else {
        8
    }
}

// ---------------------------------------------------------------------------
// Resolution
// ---------------------------------------------------------------------------

/// Where managed runtimes live.
pub fn runtimes_dir(data_dir: &Path) -> PathBuf {
    data_dir.join("java")
}

fn component_dir(data_dir: &Path, component: &str) -> PathBuf {
    runtimes_dir(data_dir).join(component)
}

/// The `java` binary inside an extracted runtime.
///
/// The layout differs by platform — macOS runtimes nest everything inside a
/// `jre.bundle`, and Windows has both `java.exe` and the console-less
/// `javaw.exe`. Checking known locations first keeps the common case to a few
/// `stat` calls; the bounded walk is the safety net for a layout we haven't
/// seen.
fn find_java_exe(root: &Path) -> Option<PathBuf> {
    let names: &[&str] = if cfg!(windows) {
        &["javaw.exe", "java.exe"]
    } else {
        &["java"]
    };

    let prefixes = [
        PathBuf::from("bin"),
        PathBuf::from("jre.bundle/Contents/Home/bin"),
        PathBuf::from("Contents/Home/bin"),
        PathBuf::new(),
    ];

    for prefix in &prefixes {
        for name in names {
            let candidate = root.join(prefix).join(name);
            if candidate.is_file() {
                return Some(candidate);
            }
        }
    }

    walk_for_java(root, names, 0)
}

fn walk_for_java(dir: &Path, names: &[&str], depth: usize) -> Option<PathBuf> {
    // A JRE tree is shallow; anything deeper than this is not the launcher
    // binary and we shouldn't be walking someone's whole disk.
    if depth > 5 {
        return None;
    }
    let entries = std::fs::read_dir(dir).ok()?;
    let mut subdirs = Vec::new();

    for entry in entries.flatten() {
        let path = entry.path();
        if path.is_dir() {
            subdirs.push(path);
        } else if let Some(name) = path.file_name().and_then(|n| n.to_str()) {
            if names.contains(&name) {
                return Some(path);
            }
        }
    }
    for sub in subdirs {
        if let Some(found) = walk_for_java(&sub, names, depth + 1) {
            return Some(found);
        }
    }
    None
}

/// An already-downloaded runtime for this component, if there is one.
pub fn installed_runtime(data_dir: &Path, component: &str) -> Option<PathBuf> {
    let dir = component_dir(data_dir, component);
    if !dir.is_dir() {
        return None;
    }
    find_java_exe(&dir)
}

/// A system JVM new enough to run this Minecraft version.
///
/// Prefers an exact major match, because that's what the version was tested
/// against, but accepts anything newer — refusing to launch on Java 22 when 21
/// was asked for would be pedantry, and the game runs fine.
fn system_java_for(required_major: u32) -> Option<String> {
    let installed = detect_installed_javas();

    if let Some(exact) = installed
        .iter()
        .find(|j| j.major_version == Some(required_major))
    {
        return Some(exact.path.clone());
    }
    installed
        .iter()
        .filter(|j| j.major_version.is_some_and(|m| m > required_major))
        // The lowest version that still qualifies: closest to what the game
        // expects.
        .min_by_key(|j| j.major_version.unwrap_or(u32::MAX))
        .map(|j| j.path.clone())
}

/// Resolve a JVM to launch with, downloading one if that's what it takes.
///
/// `configured` is the user's explicit path, if they set one. `required_major`
/// and `component` come from the Minecraft version JSON where available.
///
/// `log` receives human-readable progress; it's wired to the instance console
/// or the server console so a first launch that spends a minute downloading a
/// JRE says so rather than appearing to hang.
pub async fn ensure_java<F>(
    app: &AppHandle,
    data_dir: &Path,
    configured: Option<&str>,
    required_major: u32,
    component: Option<&str>,
    log: &F,
) -> Result<PathBuf, String>
where
    // Generic rather than `&dyn Fn`: a trait object here forces a
    // higher-ranked bound the compiler can't satisfy once the closure is held
    // across an await inside a future that itself has to be `Send`.
    F: Fn(String) + Send + Sync,
{
    // 1. The user's explicit choice always wins.
    if let Some(path) = configured.map(str::trim).filter(|p| !p.is_empty()) {
        let exe = PathBuf::from(path);
        if !exe.is_file() {
            return Err(format!(
                "The Java path in Settings doesn't exist: {path}\n\
                 Clear the field to let Blurred pick or download one for you."
            ));
        }
        return Ok(exe);
    }

    let component = component
        .filter(|c| !c.is_empty())
        .map(str::to_string)
        .unwrap_or_else(|| component_for_major(required_major).to_string());

    // 2. Something we downloaded earlier.
    if let Some(exe) = installed_runtime(data_dir, &component) {
        return Ok(exe);
    }

    // 3. A system JVM that fits.
    if let Some(path) = system_java_for(required_major) {
        log(format!("[blurred] Using system Java {required_major} at {path}"));
        return Ok(PathBuf::from(path));
    }

    // 4. Fetch one.
    log(format!(
        "[blurred] No Java {required_major} found. Downloading one — this happens once."
    ));
    let exe = download_runtime(app, data_dir, &component, log).await?;
    log(format!("[blurred] Java ready at {}", exe.display()));
    Ok(exe)
}

// ---------------------------------------------------------------------------
// Download
// ---------------------------------------------------------------------------

/// Fetch and unpack a Mojang runtime into `<data>/java/<component>/`.
pub async fn download_runtime<F>(
    app: &AppHandle,
    data_dir: &Path,
    component: &str,
    log: &F,
) -> Result<PathBuf, String>
where
    F: Fn(String) + Send + Sync,
{
    let platform = platform_key().ok_or_else(|| {
        format!(
            "Mojang doesn't publish a Java runtime for {} on {}, so Blurred can't download one. \
             Install a JDK (Temurin {} or newer) and point Settings → Default Java at it.",
            std::env::consts::ARCH,
            std::env::consts::OS,
            21
        )
    })?;

    let http = reqwest::Client::new();

    let index: RuntimeIndex = http
        .get(RUNTIME_INDEX)
        .send()
        .await
        .map_err(|e| format!("could not reach Mojang's Java index: {e}"))?
        .json()
        .await
        .map_err(|e| format!("Mojang's Java index didn't parse: {e}"))?;

    // Apple Silicon can run the Intel build under Rosetta, and Mojang relies on
    // that for the older components they never shipped natively.
    let fallbacks: &[&str] = if platform == "mac-os-arm64" {
        &["mac-os-arm64", "mac-os"]
    } else {
        &[]
    };
    let search: Vec<&str> = if fallbacks.is_empty() { vec![platform] } else { fallbacks.to_vec() };

    let build = search
        .iter()
        .find_map(|p| index.get(*p).and_then(|c| c.get(component)).and_then(|b| b.first()))
        .ok_or_else(|| {
            format!(
                "Mojang publishes no '{component}' runtime for {platform}. \
                 Install a JDK yourself and set it in Settings → Default Java."
            )
        })?;

    log(format!(
        "[blurred] Fetching Java {} ({component}) for {platform}…",
        build.version.name
    ));

    let manifest: RuntimeManifest = http
        .get(&build.manifest.url)
        .send()
        .await
        .map_err(|e| format!("could not fetch the runtime file list: {e}"))?
        .json()
        .await
        .map_err(|e| format!("the runtime file list didn't parse: {e}"))?;

    let root = component_dir(data_dir, component);
    // Unpack into a staging directory and move it into place at the end, so an
    // interrupted download can never leave a half-extracted JRE that looks
    // installed. `find_java_exe` would happily return a binary from a tree
    // missing half its libraries.
    let staging = runtimes_dir(data_dir).join(format!(".{component}.partial"));
    if staging.exists() {
        std::fs::remove_dir_all(&staging).map_err(|e| e.to_string())?;
    }
    std::fs::create_dir_all(&staging).map_err(|e| e.to_string())?;

    // Directories first, so file writes never race to create a parent.
    for (path, entry) in &manifest.files {
        if matches!(entry, RuntimeEntry::Directory) {
            std::fs::create_dir_all(staging.join(path)).map_err(|e| e.to_string())?;
        }
    }

    let files: Vec<(&String, &FileDownloads, bool)> = manifest
        .files
        .iter()
        .filter_map(|(path, entry)| match entry {
            RuntimeEntry::File { downloads, executable } => Some((path, downloads, *executable)),
            _ => None,
        })
        .collect();

    let total = files.len();
    let total_bytes: u64 = files.iter().map(|(_, d, _)| d.raw.size).sum();
    log(format!(
        "[blurred] {total} files, {:.0} MB.",
        total_bytes as f64 / 1_048_576.0
    ));

    // Fetched in fixed-size batches with `join_all`, rather than as a stream of
    // futures produced by a closure. The closure form reads better but doesn't
    // compile: a closure returning an `async move` block that borrows its
    // surroundings needs a higher-ranked `Fn` bound that can't be satisfied
    // once the whole future also has to be `Send`. A named async fn has
    // ordinary lifetimes and sidesteps it entirely.
    let mut completed = 0usize;
    for batch in files.chunks(PARALLEL_DOWNLOADS) {
        let mut pending = Vec::with_capacity(batch.len());
        for (path, downloads, executable) in batch {
            pending.push(fetch_file(&http, &staging, path, &downloads.raw, *executable));
        }

        for result in futures_util::future::join_all(pending).await {
            if let Err(e) = result {
                // Leave nothing half-installed behind.
                std::fs::remove_dir_all(&staging).ok();
                return Err(format!("Java download failed — {e}"));
            }
        }

        completed += batch.len();
        let _ = app.emit(
            "java-progress",
            serde_json::json!({
                "component": component,
                "version": build.version.name,
                "done": completed,
                "total": total,
            }),
        );
    }

    // Symlinks last: their targets have to exist first on the platforms that
    // care. Windows runtimes contain none, and creating one there needs
    // privileges we don't have, so it's a no-op.
    for (path, entry) in &manifest.files {
        if let RuntimeEntry::Link { target } = entry {
            let _ = make_link(&staging.join(path), target);
        }
    }

    if root.exists() {
        std::fs::remove_dir_all(&root).map_err(|e| e.to_string())?;
    }
    std::fs::rename(&staging, &root).map_err(|e| {
        format!("could not move the downloaded runtime into place: {e}")
    })?;

    let _ = app.emit(
        "java-progress",
        serde_json::json!({ "component": component, "done": total, "total": total }),
    );

    find_java_exe(&root).ok_or_else(|| {
        "the downloaded runtime has no java binary in it — this shouldn't happen".to_string()
    })
}

/// Download one file of a runtime, verify it, and write it into place.
async fn fetch_file(
    http: &reqwest::Client,
    staging: &Path,
    rel_path: &str,
    download: &DownloadRef,
    executable: bool,
) -> Result<(), String> {
    let target = staging.join(rel_path);
    if let Some(parent) = target.parent() {
        std::fs::create_dir_all(parent).map_err(|e| format!("{rel_path}: {e}"))?;
    }

    let bytes = http
        .get(&download.url)
        .send()
        .await
        .map_err(|e| format!("{rel_path}: {e}"))?
        .bytes()
        .await
        .map_err(|e| format!("{rel_path}: {e}"))?;

    // Verify before writing. A truncated or corrupted file inside a JRE
    // produces a launch failure that looks like anything except a bad
    // download, and costs far more to diagnose than to prevent.
    if !download.sha1.is_empty() {
        let actual = crate::util::sha1_hex(&bytes);
        if !actual.eq_ignore_ascii_case(&download.sha1) {
            return Err(format!("{rel_path}: checksum mismatch"));
        }
    }

    std::fs::write(&target, &bytes).map_err(|e| format!("{rel_path}: {e}"))?;

    if executable {
        set_executable(&target)?;
    }
    Ok(())
}

#[cfg(unix)]
fn set_executable(path: &Path) -> Result<(), String> {
    use std::os::unix::fs::PermissionsExt;
    let mut perms = std::fs::metadata(path).map_err(|e| e.to_string())?.permissions();
    perms.set_mode(perms.mode() | 0o755);
    std::fs::set_permissions(path, perms).map_err(|e| e.to_string())
}

#[cfg(not(unix))]
fn set_executable(_path: &Path) -> Result<(), String> {
    // Windows has no executable bit; the extension is what matters.
    Ok(())
}

#[cfg(unix)]
fn make_link(path: &Path, target: &str) -> Result<(), String> {
    if path.exists() {
        std::fs::remove_file(path).ok();
    }
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
    }
    std::os::unix::fs::symlink(target, path).map_err(|e| e.to_string())
}

#[cfg(not(unix))]
fn make_link(_path: &Path, _target: &str) -> Result<(), String> {
    Ok(())
}

// ---------------------------------------------------------------------------
// Commands
// ---------------------------------------------------------------------------

/// What Blurred would use to launch, without launching.
///
/// Lets Settings show "Java 21 will be downloaded on first launch" rather than
/// leaving the user to guess.
#[derive(Debug, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct JavaPlan {
    /// "configured" | "managed" | "system" | "download" | "unsupported"
    pub source: String,
    pub path: Option<String>,
    pub required_major: u32,
    pub component: String,
    pub detail: String,
}

#[tauri::command]
pub async fn plan_java(
    state: tauri::State<'_, crate::state::AppState>,
    mc_version: String,
) -> Result<JavaPlan, String> {
    let major = major_for_mc_version(&mc_version);
    let component = component_for_major(major).to_string();

    let configured = {
        let s = state.settings.lock().unwrap();
        s.default_java.executable_path.clone()
    };

    if let Some(path) = configured.filter(|p| !p.trim().is_empty()) {
        let exists = Path::new(&path).is_file();
        return Ok(JavaPlan {
            source: "configured".into(),
            detail: if exists {
                "Using the Java you set in Settings.".into()
            } else {
                "The Java path in Settings doesn't exist — launching will fail until it's fixed or cleared.".into()
            },
            path: Some(path),
            required_major: major,
            component,
        });
    }

    if let Some(exe) = installed_runtime(&state.data_dir, &component) {
        return Ok(JavaPlan {
            source: "managed".into(),
            detail: format!("Java {major} is already downloaded and managed by Blurred."),
            path: Some(exe.to_string_lossy().to_string()),
            required_major: major,
            component,
        });
    }

    if let Some(path) = system_java_for(major) {
        return Ok(JavaPlan {
            source: "system".into(),
            detail: format!("Found Java {major} or newer installed on this machine."),
            path: Some(path),
            required_major: major,
            component,
        });
    }

    if platform_key().is_none() {
        return Ok(JavaPlan {
            source: "unsupported".into(),
            detail: format!(
                "No Java {major} installed, and Mojang publishes no runtime for {} on {}. \
                 Install a JDK and set it in Settings.",
                std::env::consts::ARCH,
                std::env::consts::OS
            ),
            path: None,
            required_major: major,
            component,
        });
    }

    Ok(JavaPlan {
        source: "download".into(),
        detail: format!("Java {major} will be downloaded automatically on first launch."),
        path: None,
        required_major: major,
        component,
    })
}

/// Download a runtime now, rather than at first launch.
#[tauri::command]
pub async fn install_java_runtime(
    app: AppHandle,
    state: tauri::State<'_, crate::state::AppState>,
    major: u32,
) -> Result<String, String> {
    let component = component_for_major(major).to_string();
    let data_dir = state.data_dir.clone();
    let exe = download_runtime(&app, &data_dir, &component, &(|_: String| {})).await?;
    Ok(exe.to_string_lossy().to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maps_minecraft_versions_onto_java_majors() {
        // The two boundaries Mojang actually moved on.
        assert_eq!(major_for_mc_version("1.20.4"), 17);
        assert_eq!(major_for_mc_version("1.20.5"), 21);
        assert_eq!(major_for_mc_version("1.21"), 21);
        assert_eq!(major_for_mc_version("1.21.11"), 21);
        assert_eq!(major_for_mc_version("1.18"), 17);
        assert_eq!(major_for_mc_version("1.17.1"), 16);
        assert_eq!(major_for_mc_version("1.16.5"), 8);
        assert_eq!(major_for_mc_version("1.8.9"), 8);
    }

    #[test]
    fn handles_snapshot_and_junk_version_strings() {
        // A snapshot like "1.21.2-pre1" must not parse as major 0 and get
        // handed a Java 8 runtime.
        assert_eq!(major_for_mc_version("1.21.2-pre1"), 21);
        assert_eq!(major_for_mc_version("1.20.5-rc1"), 21);
        // Total nonsense falls back to the oldest, which fails loudly at
        // launch rather than silently mis-picking a modern runtime.
        assert_eq!(major_for_mc_version(""), 8);
    }

    #[test]
    fn maps_majors_onto_mojang_components() {
        assert_eq!(component_for_major(8), "jre-legacy");
        assert_eq!(component_for_major(16), "java-runtime-alpha");
        assert_eq!(component_for_major(17), "java-runtime-gamma");
        assert_eq!(component_for_major(21), "java-runtime-delta");
        assert_eq!(component_for_major(22), "java-runtime-delta");
    }
}
