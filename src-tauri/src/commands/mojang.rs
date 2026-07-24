// Talks to Mojang's public, unauthenticated metadata endpoints:
//   https://piston-meta.mojang.com/mc/game/version_manifest_v2.json
//   -> per-version JSON (client jar url, libraries, asset index, main class, java version)
//
// IMPORTANT: I have not been able to hit these endpoints from the sandbox this
// was written in (network is allowlisted to package registries only), so this
// is written carefully against the known-stable schema but has NOT been
// round-tripped against a live response. First thing to verify when you run
// this for real: `fetch_version_manifest` actually deserializes, and the
// downloaded client jar's sha1 matches `VersionDetail.downloads.client.sha1`.

use futures_util::StreamExt;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::Path;

const VERSION_MANIFEST_URL: &str =
    "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
const RESOURCES_BASE: &str = "https://resources.download.minecraft.net";

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VersionManifest {
    pub latest: LatestVersions,
    pub versions: Vec<VersionSummary>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LatestVersions {
    pub release: String,
    pub snapshot: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VersionSummary {
    pub id: String,
    #[serde(rename = "type")]
    pub version_type: String, // "release" | "snapshot" | "old_beta" | "old_alpha"
    pub url: String,
    pub time: String,
    #[serde(rename = "releaseTime")]
    pub release_time: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct VersionDetail {
    pub id: String,
    #[serde(rename = "mainClass")]
    pub main_class: String,
    pub downloads: VersionDownloads,
    pub libraries: Vec<Library>,
    #[serde(rename = "assetIndex")]
    pub asset_index: AssetIndexRef,
    #[serde(rename = "javaVersion", default)]
    pub java_version: Option<JavaVersionReq>,
    pub arguments: Option<Arguments>,
    // Older versions (pre-1.13) use a flat `minecraftArguments` string instead
    // of the structured `arguments` object. Both need handling before this is
    // launch-complete; only the modern path is implemented so far.
    #[serde(rename = "minecraftArguments")]
    pub minecraft_arguments_legacy: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct JavaVersionReq {
    #[serde(rename = "majorVersion")]
    pub major_version: u32,
}

#[derive(Debug, Clone, Deserialize)]
pub struct VersionDownloads {
    pub client: DownloadArtifact,
}

#[derive(Debug, Clone, Deserialize)]
pub struct DownloadArtifact {
    pub url: String,
    pub sha1: String,
    pub size: u64,
}

#[derive(Debug, Clone, Deserialize)]
pub struct AssetIndexRef {
    pub id: String,
    pub url: String,
    pub sha1: String,
    pub size: u64,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Library {
    pub name: String, // maven coordinate, e.g. "com.google.code.gson:gson:2.10.1"
    pub downloads: Option<LibraryDownloads>,
    pub rules: Option<Vec<Rule>>, // OS-conditional inclusion
}

#[derive(Debug, Clone, Deserialize)]
pub struct LibraryDownloads {
    pub artifact: Option<DownloadArtifact>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Rule {
    pub action: String, // "allow" | "disallow"
    pub os: Option<OsRule>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct OsRule {
    pub name: Option<String>, // "windows" | "osx" | "linux"
}

#[derive(Debug, Clone, Deserialize)]
pub struct Arguments {
    pub game: Vec<serde_json::Value>, // mixed string / conditional-object array
    pub jvm: Vec<serde_json::Value>,
}

/// Returns true if a library rule set allows the library on this OS.
/// No rules at all = always include.
pub fn library_applies_to_current_os(lib: &Library) -> bool {
    let Some(rules) = &lib.rules else { return true };

    let current_os = if cfg!(target_os = "windows") {
        "windows"
    } else if cfg!(target_os = "macos") {
        "osx"
    } else {
        "linux"
    };

    let mut allowed = false;
    for rule in rules {
        let os_matches = match &rule.os {
            Some(os) => os.name.as_deref() == Some(current_os),
            None => true,
        };
        if os_matches {
            allowed = rule.action == "allow";
        }
    }
    allowed
}

pub async fn fetch_version_manifest(client: &reqwest::Client) -> anyhow::Result<VersionManifest> {
    let resp = client.get(VERSION_MANIFEST_URL).send().await?;
    let manifest = resp.json::<VersionManifest>().await?;
    Ok(manifest)
}

pub async fn fetch_version_detail(
    client: &reqwest::Client,
    version_url: &str,
) -> anyhow::Result<VersionDetail> {
    let resp = client.get(version_url).send().await?;
    let detail = resp.json::<VersionDetail>().await?;
    Ok(detail)
}

/// The asset index maps game resource paths to content-addressed blobs. Modern
/// (1.7+) versions store objects by hash under `assets/objects/<h[0:2]>/<h>`;
/// this handles that standard layout. Legacy "virtual"/"map_to_resources"
/// indexes (pre-1.7) that need the files copied into named paths aren't handled
/// here — same pre-1.13 gap as the legacy launch args.
#[derive(Debug, Deserialize)]
struct AssetIndexFile {
    objects: HashMap<String, AssetObject>,
}

#[derive(Debug, Deserialize)]
struct AssetObject {
    hash: String,
    size: u64,
}

/// Download the asset index + every object it references into `assets_dir`,
/// skipping anything already present. Returns the object count. Downloads run
/// with bounded concurrency because an index is thousands of tiny files and one-
/// at-a-time would make first launch painfully slow.
pub async fn sync_assets(
    client: &reqwest::Client,
    assets_dir: &Path,
    index_id: &str,
    index_url: &str,
) -> anyhow::Result<usize> {
    // Persist the index JSON where the game expects it (assets/indexes/<id>.json),
    // and parse it from that same body.
    let body = client.get(index_url).send().await?.text().await?;
    let indexes_dir = assets_dir.join("indexes");
    std::fs::create_dir_all(&indexes_dir)?;
    std::fs::write(indexes_dir.join(format!("{index_id}.json")), &body)?;

    let index: AssetIndexFile = serde_json::from_str(&body)?;
    let objects_dir = assets_dir.join("objects");
    let count = index.objects.len();

    let downloads = index.objects.into_values().map(|obj| {
        let client = client.clone();
        let objects_dir = objects_dir.clone();
        async move {
            let sub = &obj.hash[0..2];
            let dest = objects_dir.join(sub).join(&obj.hash);
            let url = format!("{RESOURCES_BASE}/{sub}/{}", obj.hash);
            download_if_missing(&client, &url, &dest, obj.size).await
        }
    });

    let mut stream = futures_util::stream::iter(downloads).buffer_unordered(16);
    while let Some(result) = stream.next().await {
        result?;
    }
    Ok(count)
}

/// Downloads a file to `dest`, skipping if it already exists with the right
/// size (cheap check, not a full sha1 verify -- do that separately for
/// anything security-sensitive like the client jar).
pub async fn download_if_missing(
    client: &reqwest::Client,
    url: &str,
    dest: &Path,
    expected_size: u64,
) -> anyhow::Result<()> {
    if let Ok(meta) = std::fs::metadata(dest) {
        if meta.len() == expected_size {
            return Ok(());
        }
    }
    if let Some(parent) = dest.parent() {
        std::fs::create_dir_all(parent)?;
    }
    let bytes = client.get(url).send().await?.bytes().await?;
    std::fs::write(dest, &bytes)?;
    Ok(())
}
