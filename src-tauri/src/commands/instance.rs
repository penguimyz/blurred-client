use std::path::{Path, PathBuf};

use tauri::{AppHandle, Emitter, State};
use uuid::Uuid;

use crate::commands::java::{build_classpath, detect_installed_javas, launch_and_stream, DetectedJava};
use crate::commands::mojang::{
    download_if_missing, fetch_version_detail, fetch_version_manifest, library_applies_to_current_os,
};
use crate::models::account::AccountType;
use crate::models::instance::{Instance, Loader};
use crate::state::AppState;

fn instance_json_path(base: &Path) -> PathBuf {
    base.join("instance.json")
}

pub fn load_instance(dir: &Path) -> anyhow::Result<Instance> {
    let raw = std::fs::read_to_string(instance_json_path(dir))?;
    Ok(serde_json::from_str(&raw)?)
}

pub fn save_instance(dir: &Path, instance: &Instance) -> anyhow::Result<()> {
    std::fs::create_dir_all(dir)?;
    std::fs::write(instance_json_path(dir), serde_json::to_string_pretty(instance)?)?;
    Ok(())
}

/// Locate an instance's on-disk directory and its parsed model by id.
/// Every command that mutates one instance funnels through this instead of
/// re-walking `instances_dir` by hand, so the "folder name can drift from the
/// display name" lookup rule lives in exactly one place.
pub fn find_instance(state: &AppState, instance_id: &str) -> Result<(PathBuf, Instance), String> {
    let target = Uuid::parse_str(instance_id).map_err(|e| e.to_string())?;
    let entries = std::fs::read_dir(&state.instances_dir).map_err(|e| e.to_string())?;
    for entry in entries.flatten() {
        let dir = entry.path();
        if let Ok(inst) = load_instance(&dir) {
            if inst.id == target {
                return Ok((dir, inst));
            }
        }
    }
    Err(format!("instance {instance_id} not found"))
}

#[tauri::command]
pub async fn list_instances(state: State<'_, AppState>) -> Result<Vec<Instance>, String> {
    let mut out = Vec::new();
    let entries = std::fs::read_dir(&state.instances_dir).map_err(|e| e.to_string())?;
    for entry in entries.flatten() {
        let dir = entry.path();
        if dir.is_dir() {
            if let Ok(inst) = load_instance(&dir) {
                out.push(inst);
            }
        }
    }
    out.sort_by(|a, b| b.created_at.cmp(&a.created_at));
    Ok(out)
}

/// Loader is currently accepted but only "vanilla" actually launches --
/// Fabric/Forge/Quilt/NeoForge installer integration is Phase 3+ work per
/// the roadmap and isn't wired in yet. Non-vanilla instances will be
/// created (so you can build the UI against them) but will fail at launch
/// with a clear "not implemented" error rather than silently doing the
/// wrong thing.
#[tauri::command]
pub async fn create_instance(
    state: State<'_, AppState>,
    name: String,
    mc_version: String,
    loader: Loader,
) -> Result<Instance, String> {
    let instance = Instance::new(name, mc_version, loader);
    let dir = state.instances_dir.join(instance.folder_name());
    save_instance(&dir, &instance).map_err(|e| e.to_string())?;
    Ok(instance)
}

#[tauri::command]
pub async fn delete_instance(state: State<'_, AppState>, instance_id: String) -> Result<(), String> {
    let (dir, _) = find_instance(&state, &instance_id)?;
    std::fs::remove_dir_all(&dir).map_err(|e| e.to_string())?;
    Ok(())
}

#[tauri::command]
pub async fn get_instance(state: State<'_, AppState>, instance_id: String) -> Result<Instance, String> {
    let (_, inst) = find_instance(&state, &instance_id)?;
    Ok(inst)
}

/// Persist an edited instance model back to its `instance.json`. The frontend
/// sends the whole `Instance` (Notes tab, per-instance Settings overrides,
/// window size, loader version) and this writes it verbatim. The on-disk
/// folder is located by `id`, so renaming the instance never moves its
/// directory — the folder name is cosmetic once the instance exists.
#[tauri::command]
pub async fn update_instance(
    state: State<'_, AppState>,
    instance: Instance,
) -> Result<Instance, String> {
    let (dir, _) = find_instance(&state, &instance.id.to_string())?;
    save_instance(&dir, &instance).map_err(|e| e.to_string())?;
    Ok(instance)
}

#[tauri::command]
pub fn list_detected_java() -> Vec<DetectedJava> {
    detect_installed_javas()
}

/// Stop a running instance's game process (the Quit button). Fires the kill
/// switch registered by `launch_instance`; a no-op error if it isn't running.
#[tauri::command]
pub async fn kill_instance(state: State<'_, AppState>, instance_id: String) -> Result<(), String> {
    let tx = state.running.lock().unwrap().remove(&instance_id);
    match tx {
        Some(tx) => {
            let _ = tx.send(());
            Ok(())
        }
        None => Err("instance is not running".to_string()),
    }
}

/// Instance ids that currently have a running game process. Lets the UI show
/// Quit vs. Play correctly even after a reload while a game is still open.
#[tauri::command]
pub async fn list_running(state: State<'_, AppState>) -> Result<Vec<String>, String> {
    Ok(state.running.lock().unwrap().keys().cloned().collect())
}

/// Rename an instance (display name only — the on-disk folder never moves, since
/// everything is keyed by id).
#[tauri::command]
pub async fn rename_instance(
    state: State<'_, AppState>,
    instance_id: String,
    name: String,
) -> Result<Instance, String> {
    let name = name.trim().to_string();
    if name.is_empty() {
        return Err("instance name can't be empty".to_string());
    }
    let (dir, mut instance) = find_instance(&state, &instance_id)?;
    instance.name = name;
    save_instance(&dir, &instance).map_err(|e| e.to_string())?;
    Ok(instance)
}

/// Duplicate an instance: deep-copy its folder (mods, configs, saves, downloaded
/// files) into a new instance with a fresh id, reset play stats, and a "(copy)"
/// name.
#[tauri::command]
pub async fn duplicate_instance(
    state: State<'_, AppState>,
    instance_id: String,
) -> Result<Instance, String> {
    let (src_dir, src) = find_instance(&state, &instance_id)?;

    let mut copy = src.clone();
    copy.id = Uuid::new_v4();
    copy.name = format!("{} (copy)", src.name);
    copy.created_at = chrono::Utc::now();
    copy.last_played = None;
    copy.total_playtime_seconds = 0;

    let dest_dir = state.instances_dir.join(copy.folder_name());
    copy_dir_recursive(&src_dir, &dest_dir).map_err(|e| e.to_string())?;
    // Overwrite the copied instance.json with the new identity.
    save_instance(&dest_dir, &copy).map_err(|e| e.to_string())?;
    Ok(copy)
}

/// Open an instance's folder in the OS file manager.
#[tauri::command]
pub async fn open_instance_folder(
    state: State<'_, AppState>,
    instance_id: String,
) -> Result<(), String> {
    let (dir, _) = find_instance(&state, &instance_id)?;
    crate::commands::modpacks::open_in_file_manager(&dir)
}

fn copy_dir_recursive(src: &Path, dest: &Path) -> std::io::Result<()> {
    std::fs::create_dir_all(dest)?;
    for entry in std::fs::read_dir(src)? {
        let entry = entry?;
        let path = entry.path();
        let target = dest.join(entry.file_name());
        if path.is_dir() {
            copy_dir_recursive(&path, &target)?;
        } else {
            std::fs::copy(&path, &target)?;
        }
    }
    Ok(())
}

/// The Phase 1 core loop: resolve version manifest -> download client jar +
/// applicable libraries -> spawn java -> stream logs back to the frontend
/// via `instance-log` events.
///
/// NOTE ON WHAT'S MISSING: asset download/verification is not implemented
/// yet (game will likely fail to find sounds/textures on first real launch
/// until that lands), and only vanilla loader is handled. This gets you
/// far enough to prove the pipeline; asset syncing and mod loader
/// installers are next.
#[tauri::command]
pub async fn launch_instance(
    app: AppHandle,
    state: State<'_, AppState>,
    instance_id: String,
) -> Result<(), String> {
    let (dir, mut instance) = find_instance(&state, &instance_id)?;

    // Vanilla / Fabric / Quilt launch fully; Forge & NeoForge run their installer
    // pipeline (experimental — see commands::forge).

    let http = reqwest::Client::new();

    let manifest = fetch_version_manifest(&http).await.map_err(|e| e.to_string())?;
    let version_summary = manifest
        .versions
        .iter()
        .find(|v| v.id == instance.mc_version)
        .ok_or_else(|| format!("version {} not found in manifest", instance.mc_version))?;

    let detail = fetch_version_detail(&http, &version_summary.url)
        .await
        .map_err(|e| e.to_string())?;

    let versions_dir = dir.join("versions").join(&instance.mc_version);
    let client_jar = versions_dir.join(format!("{}.jar", instance.mc_version));
    download_if_missing(
        &http,
        &detail.downloads.client.url,
        &client_jar,
        detail.downloads.client.size,
    )
    .await
    .map_err(|e| e.to_string())?;

    let libraries_dir = state.data_dir.join("libraries");
    let mut classpath_jars = vec![client_jar];
    for lib in &detail.libraries {
        if !library_applies_to_current_os(lib) {
            continue;
        }
        let Some(downloads) = &lib.downloads else { continue };
        let Some(artifact) = &downloads.artifact else { continue };
        let jar_path = libraries_dir.join(maven_coord_to_path(&lib.name));
        download_if_missing(&http, &artifact.url, &jar_path, artifact.size)
            .await
            .map_err(|e| e.to_string())?;
        classpath_jars.push(jar_path);
    }

    // Progress logger -> the instance Logs tab (used by the Fabric + asset steps).
    let log = |stream: &str, line: String| {
        let _ = app.emit(
            "instance-log",
            serde_json::json!({ "instanceId": instance_id.clone(), "stream": stream, "line": line }),
        );
    };

    // Loader step. Vanilla uses the client jar's main class as-is. Fabric/Quilt
    // add their libraries to the classpath and swap in the loader's main class,
    // resolving the loader version once and persisting it. Forge/NeoForge need
    // their installer's processor pipeline, which isn't built yet.
    use crate::commands::fabric;
    let mut main_class = detail.main_class.clone();

    match instance.loader {
        Loader::Vanilla => {}
        Loader::Fabric | Loader::Quilt => {
            let (meta, maven) = if instance.loader == Loader::Quilt {
                (fabric::QUILT_META, fabric::QUILT_MAVEN)
            } else {
                (fabric::FABRIC_META, fabric::FABRIC_MAVEN)
            };
            let loader_version = match instance.loader_version.clone() {
                Some(v) => v,
                None => {
                    let v = fabric::latest_loader_version(&http, meta, &instance.mc_version)
                        .await
                        .map_err(|e| e.to_string())?;
                    instance.loader_version = Some(v.clone());
                    v
                }
            };
            log("stdout", format!("[blurred] {:?} loader {loader_version}", instance.loader));
            let profile = fabric::fetch_profile(&http, meta, &instance.mc_version, &loader_version)
                .await
                .map_err(|e| e.to_string())?;
            for lib in &profile.libraries {
                let dest = libraries_dir.join(fabric::maven_path(&lib.name));
                let url = fabric::maven_url(maven, lib.url.as_deref(), &lib.name);
                fabric::download_if_absent(&http, &url, &dest)
                    .await
                    .map_err(|e| e.to_string())?;
                classpath_jars.push(dest);
            }
            main_class = profile.main_class.client().to_string();
        }
        Loader::Forge | Loader::NeoForge => {
            return Err(format!(
                "{:?} launch isn't implemented yet — it needs the {:?} installer's \
                 processor pipeline (a large separate piece). Fabric and Quilt launch today; \
                 you can still create {:?} instances and manage their mods.",
                instance.loader, instance.loader, instance.loader
            ));
        }
    }

    let classpath = build_classpath(&classpath_jars);

    // Asset sync (sounds/textures). Streamed to the instance log so the user can
    // see progress instead of staring at a frozen launch. A failure here is
    // surfaced but not fatal — the game may still start (just without some
    // assets), and the error tells the user exactly what to retry.
    let assets_dir = state.data_dir.join("assets");
    log("stdout", format!("[blurred] Syncing assets (index {})…", detail.asset_index.id));
    match crate::commands::mojang::sync_assets(
        &http,
        &assets_dir,
        &detail.asset_index.id,
        &detail.asset_index.url,
    )
    .await
    {
        Ok(n) => log("stdout", format!("[blurred] Assets ready ({n} objects).")),
        Err(e) => log("stderr", format!("[blurred] Asset sync failed: {e}")),
    }

    let settings = state.settings.lock().unwrap().clone();
    let java = if instance.java_override.enabled {
        instance.java_override.value.clone()
    } else {
        settings.default_java.clone()
    };

    // Resolve a JVM, downloading Mojang's own if this machine hasn't got one.
    // Requiring people to go and install Java 21 before they can play is a
    // barrier with no purpose — the version JSON says exactly which runtime it
    // wants, so we can just fetch it. See commands::java_runtime.
    let (required_major, component) = match &detail.java_version {
        Some(req) => (req.major_version, req.component.clone()),
        // Pre-1.17 versions predate the field entirely.
        None => (
            crate::commands::java_runtime::major_for_mc_version(&instance.mc_version),
            None,
        ),
    };

    let java_exe = {
        // `log` borrows locals and isn't cloneable, so the progress sink gets
        // its own owned handle rather than trying to share that one.
        let app_log = app.clone();
        let id_log = instance_id.clone();
        crate::commands::java_runtime::ensure_java(
            &app,
            &state.data_dir,
            java.executable_path.as_deref(),
            required_major,
            component.as_deref(),
            &move |line| {
                let _ = app_log.emit(
                    "instance-log",
                    serde_json::json!({
                        "instanceId": id_log.clone(),
                        "stream": "stdout",
                        "line": line,
                    }),
                );
            },
        )
        .await?
        .to_string_lossy()
        .to_string()
    };

    let mut jvm_args = vec![
        format!("-Xms{}M", java.min_memory_mb),
        format!("-Xmx{}M", java.max_memory_mb),
    ];

    // Hand the in-game Blurred mod its bridge coordinates. System properties
    // rather than a file or a fixed port: they reach only this process, so the
    // token can't be read by anything the launcher didn't start (see
    // commands::bridge). The mod treats a missing port as "launcher offline"
    // and runs HUD-only, so this is safe to always pass.
    {
        let port = state.bridge.port.load(std::sync::atomic::Ordering::Relaxed);
        if port != 0 {
            jvm_args.push(format!("-Dblurred.bridge.port={port}"));
            jvm_args.push(format!("-Dblurred.bridge.token={}", state.bridge.token));
        }
    }

    jvm_args.push("-cp".to_string());
    jvm_args.push(classpath);

    if !java.jvm_args.trim().is_empty() {
        jvm_args.extend(java.jvm_args.split_whitespace().map(String::from));
    }

    // Offline identity: pick the most recently used account (there's no
    // per-instance account assignment UI yet, so "the active account" is just
    // whichever was last touched). Its username + derived offline UUID go
    // straight into the game args. Offline mode uses a dummy access token and
    // userType "legacy" -- there's no real session to authenticate against.
    // Per-instance account assignment: use the instance's assigned account if it
    // still exists, otherwise fall back to the globally-active (most recent) one.
    let account = {
        let accounts = state.accounts.lock().unwrap();
        instance
            .account_id
            .and_then(|id| accounts.iter().find(|a| a.id == id).cloned())
            .or_else(|| accounts.iter().max_by_key(|a| a.last_used).cloned())
            .ok_or_else(|| "no account configured -- add one from the sign-in screen first".to_string())?
    };

    // Resolve the launch identity. Offline accounts use dummy token/legacy
    // userType; Microsoft accounts re-derive a live Minecraft token from the
    // keychain-stored refresh token (network round-trip, so it's logged).
    let (mc_username, mc_uuid, mc_access_token, user_type) = match account.account_type {
        AccountType::Microsoft => {
            log("stdout", "[blurred] Authenticating with Microsoft…".to_string());
            let creds = crate::commands::online_auth::authenticate_for_launch(
                &settings.msa_client_id,
                &account,
            )
            .await
            .map_err(|e| {
                log("stderr", format!("[blurred] Microsoft auth failed: {e}"));
                e
            })?;
            log("stdout", format!("[blurred] Signed in as {}.", creds.username));
            (creds.username, creds.uuid, creds.access_token, "msa")
        }
        AccountType::Offline => (
            account.username.clone(),
            account.mc_uuid.replace('-', ""), // --uuid wants undashed 32-char hex
            "0".to_string(),
            "legacy",
        ),
    };

    let game_dir_str = dir.to_string_lossy().to_string();
    let assets_dir_str = assets_dir.to_string_lossy().to_string();
    let user_type_str = user_type.to_string();

    // Pre-1.13 versions ship a flat `minecraftArguments` string with `${token}`
    // placeholders; 1.13+ ship the structured `arguments` object (for which our
    // fixed modern arg set is equivalent for a vanilla client launch). Detect the
    // legacy form and substitute tokens; otherwise use the modern args.
    // (Note: very old versions also want a "virtual" assets layout, which the
    // asset syncer doesn't build yet — that's the remaining pre-1.7 gap.)
    let game_args = if let Some(legacy) = &detail.minecraft_arguments_legacy {
        let subs: Vec<(&str, &str)> = vec![
            ("auth_player_name", &mc_username),
            ("version_name", &instance.mc_version),
            ("game_directory", &game_dir_str),
            ("assets_root", &assets_dir_str),
            ("game_assets", &assets_dir_str),
            ("assets_index_name", &detail.asset_index.id),
            ("auth_uuid", &mc_uuid),
            ("auth_access_token", &mc_access_token),
            ("auth_session", &mc_access_token),
            ("user_type", &user_type_str),
            ("user_properties", "{}"),
            ("version_type", "release"),
            ("auth_xuid", ""),
            ("clientid", ""),
        ];
        legacy
            .split_whitespace()
            .map(|tok| {
                let mut s = tok.to_string();
                for (k, v) in &subs {
                    s = s.replace(&format!("${{{k}}}"), v);
                }
                s
            })
            .collect::<Vec<_>>()
    } else {
        vec![
            "--username".to_string(),
            mc_username.clone(),
            "--version".to_string(),
            instance.mc_version.clone(),
            "--gameDir".to_string(),
            game_dir_str.clone(),
            "--assetsDir".to_string(),
            assets_dir_str.clone(),
            "--assetIndex".to_string(),
            detail.asset_index.id.clone(),
            "--uuid".to_string(),
            mc_uuid.clone(),
            "--accessToken".to_string(),
            mc_access_token.clone(),
            "--userType".to_string(),
            user_type_str.clone(),
        ]
    };

    let env_vars = if instance.env_vars_override.enabled {
        instance.env_vars_override.value.vars.clone()
    } else {
        settings.default_env_vars.vars.clone()
    };

    instance.last_played = Some(chrono::Utc::now());
    save_instance(&dir, &instance).map_err(|e| e.to_string())?;

    // Mark this account as the most recently used so it stays "active".
    {
        let mut accounts = state.accounts.lock().unwrap();
        if let Some(a) = accounts.iter_mut().find(|a| a.id == account.id) {
            a.last_used = Some(chrono::Utc::now());
        }
        let accounts_clone = accounts.clone();
        drop(accounts);
        crate::state::persist_accounts(&state.data_dir, &accounts_clone).map_err(|e| e.to_string())?;
    }

    // Register a kill switch so the Quit button can stop this process, then
    // remove it once the process is gone (whether it exited on its own or was
    // killed). Its presence in `state.running` is also how the UI knows the
    // instance is running.
    // Keep the in-game companion mod current. Best-effort: a failure here must
    // not stop the game from launching, since the mod is an enhancement rather
    // than a requirement.
    if let Err(e) = crate::commands::companion::sync_companion(&app, &instance, &dir) {
        tracing::warn!("could not install companion mod: {e}");
    }

    let (kill_tx, kill_rx) = tokio::sync::oneshot::channel();
    state.running.lock().unwrap().insert(instance_id.clone(), kill_tx);

    // One log file per session, named by start time so they sort chronologically
    // in a file browser. Kept inside the instance dir so "open folder" leads
    // straight to it and deleting an instance takes its logs with it.
    let session_log = dir.join("logs").join(format!(
        "session-{}.log",
        chrono::Local::now().format("%Y-%m-%d_%H-%M-%S")
    ));
    prune_session_logs(&dir.join("logs"));

    let launch_start = std::time::Instant::now();
    let result = launch_and_stream(
        app.clone(),
        instance_id.clone(),
        java_exe,
        jvm_args,
        main_class,
        game_args,
        dir.clone(),
        env_vars,
        session_log,
        kill_rx,
    )
    .await;

    state.running.lock().unwrap().remove(&instance_id);
    let outcome = result.map_err(|e| e.to_string())?;

    let elapsed = launch_start.elapsed().as_secs();
    instance.total_playtime_seconds += elapsed;
    save_instance(&dir, &instance).map_err(|e| e.to_string())?;

    if outcome.exit_code != 0 {
        // A crash the user quit out of isn't worth a report — but we can't tell
        // those apart by exit code alone on every platform, so record it and let
        // the UI decide what to surface. The report is written before returning
        // so it exists even though the caller sees only an error string.
        let report = crate::models::crash::CrashReport {
            id: Uuid::new_v4().to_string(),
            instance_id: instance_id.clone(),
            instance_name: instance.name.clone(),
            mc_version: instance.mc_version.clone(),
            loader: format!("{:?}", instance.loader).to_lowercase(),
            exit_code: outcome.exit_code,
            occurred_at: chrono::Utc::now().to_rfc3339(),
            log_path: outcome.log_path.to_string_lossy().to_string(),
            summary: crate::models::crash::diagnose(outcome.exit_code, &outcome.tail),
            tail: outcome.tail,
        };
        crate::commands::crashes::save_report(&state, &report);
        let _ = app.emit("instance-crashed", report);

        return Err(format!("minecraft exited with code {}", outcome.exit_code));
    }
    Ok(())
}

/// Keep the last 10 session logs per instance. Without this, a modpack that
/// logs heavily leaves hundreds of megabytes behind over a few weeks of play.
fn prune_session_logs(logs_dir: &std::path::Path) {
    const KEEP: usize = 10;

    let Ok(read) = std::fs::read_dir(logs_dir) else { return };
    let mut sessions: Vec<_> = read
        .flatten()
        .filter(|e| {
            e.file_name()
                .to_string_lossy()
                .starts_with("session-")
        })
        .collect();

    if sessions.len() <= KEEP {
        return;
    }
    sessions.sort_by_key(|e| {
        e.metadata()
            .and_then(|m| m.modified())
            .unwrap_or(std::time::SystemTime::UNIX_EPOCH)
    });
    for entry in sessions.iter().take(sessions.len() - KEEP) {
        let _ = std::fs::remove_file(entry.path());
    }
}

/// Maven coordinate -> repo-relative jar path, including any classifier:
///   "com.google.code.gson:gson:2.10.1"          -> "com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"
///   "org.lwjgl:lwjgl:3.3.3:natives-windows"      -> "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar"
///
/// The classifier (4th `:`-separated part) MUST be kept: modern Minecraft ships
/// LWJGL/OpenAL natives as classifier'd entries, and dropping it makes the
/// natives jar collide with the core jar on disk — which clobbers LWJGL and
/// crashes the game at startup with NoClassDefFoundError on org.lwjgl.* .
fn maven_coord_to_path(coord: &str) -> PathBuf {
    let parts: Vec<&str> = coord.split(':').collect();
    if parts.len() < 3 {
        return PathBuf::from(coord.replace(':', "_"));
    }
    let (group, artifact, version) = (parts[0], parts[1], parts[2]);
    let group_path = group.replace('.', "/");
    let filename = match parts.get(3) {
        Some(classifier) => format!("{artifact}-{version}-{classifier}.jar"),
        None => format!("{artifact}-{version}.jar"),
    };
    PathBuf::from(format!("{group_path}/{artifact}/{version}/{filename}"))
}
