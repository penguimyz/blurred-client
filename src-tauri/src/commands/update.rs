// Launcher self-update — the "check" half (spec §10 / Phase 7). Full silent
// auto-install needs signed releases + the updater plugin + a hosted release
// feed (infrastructure that doesn't exist yet), so this implements the honest,
// useful part: query the configured GitHub repo's latest release and report
// whether a newer version is out, with a link. `update_repo` is a setting
// ("owner/name"); empty = update checks disabled.

use serde::{Deserialize, Serialize};
use tauri::State;

use crate::state::AppState;

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateStatus {
    pub configured: bool,
    pub current_version: String,
    pub latest_version: Option<String>,
    pub update_available: bool,
    pub url: Option<String>,
    pub notes: Option<String>,
}

#[derive(Debug, Deserialize)]
struct GhRelease {
    tag_name: String,
    html_url: String,
    #[serde(default)]
    body: String,
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
        });
    }

    let client = reqwest::Client::new();
    let resp = client
        .get(format!("https://api.github.com/repos/{repo}/releases/latest"))
        .header("User-Agent", "blurred-client")
        .header("Accept", "application/vnd.github+json")
        .send()
        .await
        .map_err(|e| e.to_string())?;

    if resp.status() == reqwest::StatusCode::NOT_FOUND {
        return Ok(UpdateStatus {
            configured: true,
            current_version: current,
            latest_version: None,
            update_available: false,
            url: None,
            notes: Some("No published releases for this repo yet.".to_string()),
        });
    }
    if !resp.status().is_success() {
        return Err(format!("GitHub returned {}", resp.status()));
    }

    let rel: GhRelease = resp.json().await.map_err(|e| e.to_string())?;
    let latest = rel.tag_name.trim_start_matches('v').to_string();
    let update_available = version_gt(&latest, &current);

    Ok(UpdateStatus {
        configured: true,
        current_version: current,
        latest_version: Some(latest),
        update_available,
        url: Some(rel.html_url),
        notes: if rel.body.trim().is_empty() { None } else { Some(rel.body) },
    })
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
