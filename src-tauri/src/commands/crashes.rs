//! Crash report storage and retrieval.
//!
//! Reports are one JSON file each under `<data dir>/crashes/`, written by
//! `launch_instance` when the game exits non-zero. Flat files rather than an
//! index: a crash is written once and read rarely, and one-file-per-report
//! means a corrupt report can never take the whole list down with it.

use std::path::PathBuf;

use tauri::State;

use crate::models::crash::CrashReport;
use crate::state::AppState;

/// Keep the most recent N reports; older ones are pruned on each new write so
/// a repeatedly-crashing instance can't fill the disk.
const MAX_REPORTS: usize = 40;

pub fn crashes_dir(state: &AppState) -> PathBuf {
    state.data_dir.join("crashes")
}

/// Persist a crash report and prune the backlog. Best-effort: a failure to
/// write a crash report must never turn into a second error on top of the crash
/// the user is already dealing with, so this logs and moves on.
pub fn save_report(state: &AppState, report: &CrashReport) {
    let dir = crashes_dir(state);
    if let Err(e) = std::fs::create_dir_all(&dir) {
        tracing::warn!("could not create crash dir: {e}");
        return;
    }

    let path = dir.join(format!("{}.json", report.id));
    match serde_json::to_string_pretty(report) {
        Ok(json) => {
            if let Err(e) = std::fs::write(&path, json) {
                tracing::warn!("could not write crash report: {e}");
                return;
            }
        }
        Err(e) => {
            tracing::warn!("could not serialize crash report: {e}");
            return;
        }
    }

    prune(&dir);
}

fn prune(dir: &PathBuf) {
    let mut entries: Vec<_> = match std::fs::read_dir(dir) {
        Ok(rd) => rd
            .flatten()
            .filter(|e| e.path().extension().is_some_and(|x| x == "json"))
            .collect(),
        Err(_) => return,
    };
    if entries.len() <= MAX_REPORTS {
        return;
    }

    // Oldest first by mtime, then drop the excess from the front.
    entries.sort_by_key(|e| {
        e.metadata()
            .and_then(|m| m.modified())
            .unwrap_or(std::time::SystemTime::UNIX_EPOCH)
    });
    for entry in entries.iter().take(entries.len() - MAX_REPORTS) {
        let _ = std::fs::remove_file(entry.path());
    }
}

/// All saved crash reports, newest first.
#[tauri::command]
pub async fn list_crash_reports(state: State<'_, AppState>) -> Result<Vec<CrashReport>, String> {
    let dir = crashes_dir(&state);
    if !dir.exists() {
        return Ok(Vec::new());
    }

    let mut reports: Vec<CrashReport> = std::fs::read_dir(&dir)
        .map_err(|e| e.to_string())?
        .flatten()
        .filter(|e| e.path().extension().is_some_and(|x| x == "json"))
        // A report that fails to parse is skipped rather than failing the whole
        // list — one bad file shouldn't hide every other crash.
        .filter_map(|e| std::fs::read_to_string(e.path()).ok())
        .filter_map(|raw| serde_json::from_str::<CrashReport>(&raw).ok())
        .collect();

    reports.sort_by(|a, b| b.occurred_at.cmp(&a.occurred_at));
    Ok(reports)
}

#[tauri::command]
pub async fn delete_crash_report(state: State<'_, AppState>, id: String) -> Result<(), String> {
    // Guard against a caller reaching outside the crashes dir with a crafted id.
    if id.contains(['/', '\\', '.']) {
        return Err("invalid report id".to_string());
    }
    let path = crashes_dir(&state).join(format!("{id}.json"));
    if path.exists() {
        std::fs::remove_file(&path).map_err(|e| e.to_string())?;
    }
    Ok(())
}

#[tauri::command]
pub async fn clear_crash_reports(state: State<'_, AppState>) -> Result<(), String> {
    let dir = crashes_dir(&state);
    if !dir.exists() {
        return Ok(());
    }
    for entry in std::fs::read_dir(&dir).map_err(|e| e.to_string())?.flatten() {
        if entry.path().extension().is_some_and(|x| x == "json") {
            let _ = std::fs::remove_file(entry.path());
        }
    }
    Ok(())
}

/// Read a full session log off disk. Used by "View full log" on a crash report;
/// the report itself only carries the tail.
#[tauri::command]
pub async fn read_session_log(path: String) -> Result<String, String> {
    let p = PathBuf::from(&path);
    if !p.exists() {
        return Err("That log file is gone — it may have been cleaned up.".to_string());
    }
    // Logs can reach tens of MB after a long session with a chatty modpack.
    // Return the tail rather than pushing all of it through the IPC bridge.
    const MAX_BYTES: usize = 2 * 1024 * 1024;
    let bytes = std::fs::read(&p).map_err(|e| e.to_string())?;
    if bytes.len() <= MAX_BYTES {
        return Ok(String::from_utf8_lossy(&bytes).to_string());
    }

    let start = bytes.len() - MAX_BYTES;
    let clipped = String::from_utf8_lossy(&bytes[start..]).to_string();
    Ok(format!(
        "[… {:.1} MB trimmed — open the file on disk for the whole log …]\n{}",
        start as f64 / 1_048_576.0,
        clipped
    ))
}
