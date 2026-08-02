mod commands;
mod models;
mod state;
mod util;

use tauri::Manager;

use crate::state::AppState;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tracing_subscriber::fmt::init();

    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_dialog::init())
        .setup(|app| {
            let state = AppState::init().expect("failed to initialize app state -- check disk permissions on the data dir");
            app.manage(state);

            // Loopback bridge the in-game Blurred mod talks to. Started here so
            // the port exists before any instance can launch — `launch_instance`
            // passes it to the game as a JVM property. A failure here is not
            // fatal: the launcher works fine, the mod just reports "launcher
            // offline" and falls back to HUD-only.
            {
                let handle = app.handle().clone();
                tauri::async_runtime::spawn(async move {
                    match crate::commands::bridge::start(handle.clone()).await {
                        Ok(port) => {
                            let state = handle.state::<AppState>();
                            state
                                .bridge
                                .port
                                .store(port, std::sync::atomic::Ordering::Relaxed);
                        }
                        Err(e) => tracing::warn!("mod bridge unavailable: {e}"),
                    }
                });
            }

            // OS-level blur-behind for the glass theme. Without it,
            // `transparent: true` in tauri.conf.json just gives you a
            // see-through window with no blur -- the CSS backdrop-filter only
            // blurs content within the webview, not whatever's behind the
            // window on the user's desktop.
            //
            // Windows-only on purpose: `window_vibrancy` has no Linux backend
            // (X11/Wayland expose no portable blur-behind; it's per-compositor,
            // e.g. KWin's blur or Hyprland's layer rules, and only the user can
            // enable it). On Linux the app is still translucent-capable but
            // paints the opaque `--backdrop-gradient` from theme.css instead,
            // which is why it looks correct rather than empty there.
            #[cfg(target_os = "windows")]
            {
                let window = app.get_webview_window("main").unwrap();
                if let Err(e) = window_vibrancy::apply_acrylic(&window, Some((18, 18, 22, 125))) {
                    tracing::warn!("acrylic effect failed, falling back to flat translucent bg: {e}");
                }
            }

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::instance::list_instances,
            commands::instance::create_instance,
            commands::instance::delete_instance,
            commands::instance::launch_instance,
            commands::instance::list_detected_java,
            commands::instance::get_instance,
            commands::instance::update_instance,
            commands::instance::kill_instance,
            commands::instance::list_running,
            commands::instance::rename_instance,
            commands::instance::duplicate_instance,
            commands::instance::open_instance_folder,
            commands::mods::set_mod_enabled,
            commands::mods::set_mod_pinned,
            commands::mods::remove_mod,
            commands::mods::add_local_mod,
            commands::mods::sync_instance_mods,
            commands::config::list_config_files,
            commands::config::read_config_file,
            commands::config::write_config_file,
            commands::content::list_worlds,
            commands::content::delete_world,
            commands::content::list_screenshots,
            commands::content::read_screenshot_data,
            commands::settings::get_settings,
            commands::settings::update_settings,
            commands::modpacks::list_modpacks,
            commands::modpacks::create_modpack_from_instance,
            commands::modpacks::delete_modpack,
            commands::modpacks::apply_modpack,
            commands::modpacks::export_modpack,
            commands::modpacks::import_modpack,
            commands::modpacks::import_mrpack,
            commands::modpacks::install_modrinth_modpack,
            commands::modpacks::reveal_path,
            commands::modrinth::modrinth_search,
            commands::modrinth::install_modrinth_mod,
            commands::modrinth::install_mods,
            commands::modrinth::blurred_essentials,
            commands::modrinth::check_mod_updates,
            commands::modrinth::update_mod,
            commands::modrinth::update_all_mods,
            commands::auth::create_offline_account,
            commands::auth::list_accounts,
            commands::auth::remove_account,
            commands::auth::set_active_account,
            commands::online_auth::begin_msa_login,
            commands::online_auth::complete_msa_login,
            commands::online_auth::set_account_skin,
            commands::online_auth::set_account_skin_file,
            commands::online_auth::reset_account_skin,
            commands::update::check_launcher_update,
            commands::chat::chat_status,
            commands::chat::chat_connect,
            commands::chat::chat_disconnect,
            commands::chat::chat_send,
            commands::chat::chat_join,
            commands::chat::chat_part,
            commands::chat::chat_names,
            commands::chat::list_friends,
            commands::chat::add_friend,
            commands::chat::accept_friend,
            commands::chat::decline_friend,
            commands::chat::remove_friend,
            commands::crashes::list_crash_reports,
            commands::crashes::delete_crash_report,
            commands::crashes::clear_crash_reports,
            commands::crashes::read_session_log,
            commands::bridge::bridge_info,
            commands::bridge::current_server,
            commands::capes::list_capes,
            commands::capes::save_cape,
            commands::capes::delete_cape,
            commands::capes::read_cape,
            commands::capes::set_active_cape,
            commands::servers::list_servers,
            commands::servers::create_server,
            commands::servers::update_server,
            commands::servers::delete_server,
            commands::servers::open_server_folder,
            commands::servers::accept_eula,
            commands::servers::start_server,
            commands::servers::stop_server,
            commands::servers::kill_server,
            commands::servers::server_command,
            commands::servers::server_statuses,
        ])
        .run(tauri::generate_context!())
        .expect("error while running blurred client");
}
