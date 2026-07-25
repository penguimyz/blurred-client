// Fabric-family loader support (Fabric + Quilt) via their public meta APIs
// (meta.fabricmc.net / meta.quiltmc.org, no key). Both expose the same shape:
// a list of loader versions, and a per-(game,loader) "profile" JSON giving the
// main class + extra libraries. Launch = vanilla client jar + vanilla libs +
// these libs on the classpath, with the loader's main class instead of vanilla's.
//
// Not live-tested against the meta endpoints from the sandbox — written to the
// documented, stable schema. (Fabric launch IS confirmed working on the user's
// machine; Quilt mirrors it.)

use serde::Deserialize;
use std::path::Path;

pub const FABRIC_META: &str = "https://meta.fabricmc.net/v2";
pub const FABRIC_MAVEN: &str = "https://maven.fabricmc.net/";
pub const QUILT_META: &str = "https://meta.quiltmc.org/v3";
pub const QUILT_MAVEN: &str = "https://maven.quiltmc.org/repository/release/";

#[derive(Debug, Deserialize)]
struct LoaderEntry {
    loader: LoaderInfo,
}
#[derive(Debug, Deserialize)]
struct LoaderInfo {
    version: String,
    stable: bool,
}

/// Newest *stable* loader version for a game version (falls back to newest of
/// any stability if none are marked stable — Quilt often has no "stable" flag set).
pub async fn latest_loader_version(
    client: &reqwest::Client,
    meta_base: &str,
    game_version: &str,
) -> anyhow::Result<String> {
    let url = format!("{meta_base}/versions/loader/{game_version}");
    let list: Vec<LoaderEntry> = client.get(url).send().await?.json().await?;
    let chosen = list
        .iter()
        .find(|e| e.loader.stable)
        .or_else(|| list.first())
        .ok_or_else(|| anyhow::anyhow!("no loader version available for Minecraft {game_version}"))?;
    Ok(chosen.loader.version.clone())
}

#[derive(Debug, Deserialize)]
pub struct LoaderProfile {
    #[serde(rename = "mainClass")]
    pub main_class: MainClass,
    pub libraries: Vec<ProfileLibrary>,
}

/// Fabric returns `mainClass` as a string; some Quilt profiles return an object
/// `{ "client": "...", "server": "..." }`. Accept either and pick the client.
#[derive(Debug, Deserialize)]
#[serde(untagged)]
pub enum MainClass {
    Plain(String),
    Sided { client: String },
}

impl MainClass {
    pub fn client(&self) -> &str {
        match self {
            MainClass::Plain(s) => s,
            MainClass::Sided { client } => client,
        }
    }
}

#[derive(Debug, Deserialize)]
pub struct ProfileLibrary {
    pub name: String,        // maven coordinate
    pub url: Option<String>, // maven repo base; defaults to the loader's maven
}

pub async fn fetch_profile(
    client: &reqwest::Client,
    meta_base: &str,
    game_version: &str,
    loader_version: &str,
) -> anyhow::Result<LoaderProfile> {
    let url = format!("{meta_base}/versions/loader/{game_version}/{loader_version}/profile/json");
    Ok(client.get(url).send().await?.json().await?)
}

/// "group:artifact:version[:classifier]" -> "group/path/artifact/version/file.jar"
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

pub fn maven_url(default_maven: &str, base: Option<&str>, coord: &str) -> String {
    let base = base.unwrap_or(default_maven);
    let sep = if base.ends_with('/') { "" } else { "/" };
    format!("{base}{sep}{}", maven_path(coord))
}

/// Download to `dest` unless it already exists (the coordinate pins the exact
/// version, so a present file is the right one).
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
