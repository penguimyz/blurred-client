//! Auto-installing the Blurred companion mod into instances.
//!
//! The mod jar is bundled with the launcher as a Tauri resource (see
//! `tauri.conf.json` → `bundle.resources`) rather than downloaded, so it is
//! always present, always matches the launcher version, and works with no
//! network at all.
//!
//! Sync runs on every launch rather than only at instance creation. That means
//! an instance created before the mod existed picks it up, and a launcher
//! update replaces an outdated copy — without the user ever managing it.

use std::path::{Path, PathBuf};

use tauri::{AppHandle, Manager};

use crate::models::instance::{Instance, Loader};

/// Filename used inside the instance's `mods/`. Fixed rather than versioned so
/// an update overwrites in place instead of leaving two loaders' worth of the
/// same mod, which Fabric refuses to start with.
const JAR_NAME: &str = "blurred.jar";

/// Directory of per-version jars, relative to the resource dir.
///
/// One jar per Minecraft version, because the mod is compiled against that
/// version's mappings and Minecraft's client API drifts hard across this range
/// — `SkinTextures` moved package and changed shape, `KeyBinding` categories
/// became a type, `Screen.keyPressed` took a record, `NativeImageBackedTexture`
/// gained a name supplier. The jars are produced by Stonecutter from one source
/// tree (see `mod/settings.gradle`).
const RESOURCE_DIR: &str = "resources/mod";

/// Minecraft versions with a bundled jar.
///
/// Must match the version list in `mod/settings.gradle`. Installing a jar into
/// a version it wasn't built for doesn't degrade gracefully — the game crashes
/// on startup — so an instance outside this list gets no companion at all.
const SUPPORTED_MC: &[&str] = &["1.21.1", "1.21.4", "1.21.8", "1.21.10", "1.21.11"];

/// Copy (or refresh) the companion mod inside an instance.
///
/// Returns `Ok(false)` when the instance isn't a loader the mod supports, which
/// is a normal outcome and not an error — a vanilla instance simply has no
/// `mods/` folder that means anything.
pub fn sync_companion(app: &AppHandle, instance: &Instance, dir: &Path) -> anyhow::Result<bool> {
    // It's a Fabric mod. Quilt loads Fabric mods, so both are fine; Forge and
    // NeoForge are a different loader entirely and would just error at startup.
    if !matches!(instance.loader, Loader::Fabric | Loader::Quilt) {
        remove_companion(dir);
        return Ok(false);
    }

    // Wrong Minecraft version is worse than no companion: the jar would load
    // and immediately crash the game. Remove any copy left behind by a version
    // change, then bail.
    if !SUPPORTED_MC.contains(&instance.mc_version.as_str()) {
        remove_companion(dir);
        tracing::info!(
            "skipping companion mod for {} — built for {:?}, instance is {}",
            instance.name,
            SUPPORTED_MC,
            instance.mc_version
        );
        return Ok(false);
    }

    let Some(source) = bundled_jar(app, &instance.mc_version) else {
        // Missing resource means a dev build that hasn't run `gradlew build`.
        // Not fatal — the game launches fine without the companion.
        tracing::warn!("companion mod jar not bundled; skipping auto-install");
        return Ok(false);
    };

    let mods_dir = dir.join("mods");
    std::fs::create_dir_all(&mods_dir)?;
    let target = mods_dir.join(JAR_NAME);

    // Skip the copy when the bytes already match, so launching doesn't rewrite
    // the file (and bump its mtime) every single time.
    if same_contents(&source, &target) {
        return Ok(true);
    }

    std::fs::copy(&source, &target)?;
    tracing::info!("installed companion mod into {}", mods_dir.display());
    Ok(true)
}

fn bundled_jar(app: &AppHandle, mc_version: &str) -> Option<PathBuf> {
    let path = app
        .path()
        .resource_dir()
        .ok()?
        .join(RESOURCE_DIR)
        .join(format!("blurred-{mc_version}.jar"));
    path.exists().then_some(path)
}

/// Cheap equality: compare length first, then bytes. The jar is small enough
/// that a full compare is far cheaper than the copy it avoids.
fn same_contents(a: &Path, b: &Path) -> bool {
    let (Ok(ma), Ok(mb)) = (std::fs::metadata(a), std::fs::metadata(b)) else {
        return false;
    };
    if ma.len() != mb.len() {
        return false;
    }
    match (std::fs::read(a), std::fs::read(b)) {
        (Ok(da), Ok(db)) => da == db,
        _ => false,
    }
}

/// Remove the companion from an instance — used when a loader change makes it
/// inapplicable, so a Fabric-to-Forge switch doesn't leave a jar that crashes
/// the game on startup.
fn remove_companion(dir: &Path) {
    let jar = dir.join("mods").join(JAR_NAME);
    if jar.exists() {
        let _ = std::fs::remove_file(jar);
    }
}
