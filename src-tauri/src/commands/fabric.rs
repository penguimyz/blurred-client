// Fabric loader support via the public Fabric meta API (meta.fabricmc.net, no
// key). Fabric is the simplest loader to launch: take the vanilla client jar +
// vanilla libraries, add Fabric's own libraries (loader, intermediary mappings,
// asm, etc.) to the classpath, and swap the main class to Fabric's KnotClient.
// The loader then discovers mods in <gameDir>/mods on its own.
//
// Not live-tested from the sandbox (no network to meta.fabricmc.net) — written
// against the documented, stable v2 schema.

use serde::Deserialize;
use std::path::Path;

const FABRIC_META: &str = "https://meta.fabricmc.net/v2";
const FABRIC_MAVEN: &str = "https://maven.fabricmc.net/";

#[derive(Debug, Deserialize)]
struct LoaderEntry {
    loader: LoaderInfo,
}
#[derive(Debug, Deserialize)]
struct LoaderInfo {
    version: String,
    stable: bool,
}

/// Newest *stable* Fabric loader version for a game version (falls back to the
/// newest of any stability if none are marked stable).
pub async fn latest_loader_version(
    client: &reqwest::Client,
    game_version: &str,
) -> anyhow::Result<String> {
    let url = format!("{FABRIC_META}/versions/loader/{game_version}");
    let list: Vec<LoaderEntry> = client.get(url).send().await?.json().await?;
    let chosen = list
        .iter()
        .find(|e| e.loader.stable)
        .or_else(|| list.first())
        .ok_or_else(|| anyhow::anyhow!("no Fabric loader available for Minecraft {game_version}"))?;
    Ok(chosen.loader.version.clone())
}

#[derive(Debug, Deserialize)]
pub struct FabricProfile {
    #[serde(rename = "mainClass")]
    pub main_class: String,
    pub libraries: Vec<FabricLibrary>,
}

#[derive(Debug, Deserialize)]
pub struct FabricLibrary {
    pub name: String,        // maven coordinate
    pub url: Option<String>, // maven repo base; defaults to Fabric's maven
}

/// The merged launch profile for a specific game+loader pair: Fabric's main
/// class and the extra libraries to put on the classpath.
pub async fn fetch_profile(
    client: &reqwest::Client,
    game_version: &str,
    loader_version: &str,
) -> anyhow::Result<FabricProfile> {
    let url = format!("{FABRIC_META}/versions/loader/{game_version}/{loader_version}/profile/json");
    Ok(client.get(url).send().await?.json().await?)
}

/// "group:artifact:version[:classifier]" -> "group/path/artifact/version/file.jar"
/// (forward slashes; usable both as a URL suffix and, via join, a filesystem path).
pub fn maven_path(coord: &str) -> String {
    let parts: Vec<&str> = coord.split(':').collect();
    if parts.len() < 3 {
        return coord.replace(':', "_");
    }
    let (group, artifact, version) = (parts[0], parts[1], parts[2]);
    let group_path = group.replace('.', "/");
    let filename = match parts.get(3) {
        Some(classifier) => format!("{artifact}-{version}-{classifier}.jar"),
        None => format!("{artifact}-{version}.jar"),
    };
    format!("{group_path}/{artifact}/{version}/{filename}")
}

pub fn maven_url(base: Option<&str>, coord: &str) -> String {
    let base = base.unwrap_or(FABRIC_MAVEN);
    let sep = if base.ends_with('/') { "" } else { "/" };
    format!("{base}{sep}{}", maven_path(coord))
}

/// Download to `dest` unless it already exists. Fabric meta doesn't publish
/// per-library sizes/hashes, so "already present" is the skip condition (the
/// coordinate includes the exact version, so a present file is the right file).
pub async fn download_if_absent(
    client: &reqwest::Client,
    url: &str,
    dest: &Path,
) -> anyhow::Result<()> {
    if dest.exists() {
        return Ok(());
    }
    if let Some(parent) = dest.parent() {
        std::fs::create_dir_all(parent)?;
    }
    let bytes = client.get(url).send().await?.bytes().await?;
    std::fs::write(dest, &bytes)?;
    Ok(())
}
