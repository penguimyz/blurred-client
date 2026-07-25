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

    if !matches!(instance.loader, Loader::Vanilla | Loader::Fabric) {
        return Err(format!(
            "{:?} loader installers aren't wired up yet -- Vanilla and Fabric only for now",
            instance.loader
        ));
    }

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

    // Loader step. Vanilla uses the client jar's main class as-is; Fabric adds
    // its own libraries to the classpath and swaps in KnotClient. The loader
    // version is resolved to latest-stable once, then persisted on the instance.
    let mut main_class = detail.main_class.clone();
    if instance.loader == Loader::Fabric {
        let loader_version = match instance.loader_version.clone() {
            Some(v) => v,
            None => {
                let v = crate::commands::fabric::latest_loader_version(&http, &instance.mc_version)
                    .await
                    .map_err(|e| e.to_string())?;
                instance.loader_version = Some(v.clone());
                v
            }
        };
        log("stdout", format!("[blurred] Fabric loader {loader_version}"));
        let profile =
            crate::commands::fabric::fetch_profile(&http, &instance.mc_version, &loader_version)
                .await
                .map_err(|e| e.to_string())?;
        for lib in &profile.libraries {
            let dest = libraries_dir.join(crate::commands::fabric::maven_path(&lib.name));
            let url = crate::commands::fabric::maven_url(lib.url.as_deref(), &lib.name);
            crate::commands::fabric::download_if_absent(&http, &url, &dest)
                .await
                .map_err(|e| e.to_string())?;
            classpath_jars.push(dest);
        }
        main_class = profile.main_class;
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

    let java_exe = java.executable_path.clone().ok_or_else(|| {
        "no Java executable configured -- run java detection and set one in Settings".to_string()
    })?;

    let mut jvm_args = vec![
        format!("-Xms{}M", java.min_memory_mb),
        format!("-Xmx{}M", java.max_memory_mb),
        "-cp".to_string(),
        classpath,
    ];
    if !java.jvm_args.trim().is_empty() {
        jvm_args.extend(java.jvm_args.split_whitespace().map(String::from));
    }

    // Offline identity: pick the most recently used account (there's no
    // per-instance account assignment UI yet, so "the active account" is just
    // whichever was last touched). Its username + derived offline UUID go
    // straight into the game args. Offline mode uses a dummy access token and
    // userType "legacy" -- there's no real session to authenticate against.
    let account = {
        let accounts = state.accounts.lock().unwrap();
        accounts
            .iter()
            .max_by_key(|a| a.last_used)
            .cloned()
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

    let game_args = vec![
        "--username".to_string(),
        mc_username,
        "--version".to_string(),
        instance.mc_version.clone(),
        "--gameDir".to_string(),
        dir.to_string_lossy().to_string(),
        "--assetsDir".to_string(),
        state.data_dir.join("assets").to_string_lossy().to_string(),
        "--assetIndex".to_string(),
        detail.asset_index.id.clone(),
        "--uuid".to_string(),
        mc_uuid,
        "--accessToken".to_string(),
        mc_access_token,
        "--userType".to_string(),
        user_type.to_string(),
    ];

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
    let (kill_tx, kill_rx) = tokio::sync::oneshot::channel();
    state.running.lock().unwrap().insert(instance_id.clone(), kill_tx);

    let launch_start = std::time::Instant::now();
    let result = launch_and_stream(
        app,
        instance_id.clone(),
        java_exe,
        jvm_args,
        main_class,
        game_args,
        dir.clone(),
        env_vars,
        kill_rx,
    )
    .await;

    state.running.lock().unwrap().remove(&instance_id);
    let exit_code = result.map_err(|e| e.to_string())?;

    let elapsed = launch_start.elapsed().as_secs();
    instance.total_playtime_seconds += elapsed;
    save_instance(&dir, &instance).map_err(|e| e.to_string())?;

    if exit_code != 0 {
        return Err(format!("minecraft exited with code {exit_code}"));
    }
    Ok(())
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
