// Global launcher settings (spec §10). The GlobalSettings struct already
// existed and is read at launch time to supply Java/EnvVars/CustomCommands
// defaults; these two commands just let the Settings screen read and write it.
// The per-instance override screen reuses the same form component on the
// frontend but persists through `update_instance` instead — see the frontend
// OverrideSettingsForm.

use tauri::State;

use crate::models::settings::GlobalSettings;
use crate::state::AppState;

#[tauri::command]
pub async fn get_settings(state: State<'_, AppState>) -> Result<GlobalSettings, String> {
    Ok(state.settings.lock().unwrap().clone())
}

#[tauri::command]
pub async fn update_settings(
    state: State<'_, AppState>,
    mut settings: GlobalSettings,
) -> Result<GlobalSettings, String> {
    {
        let mut guard = state.settings.lock().unwrap();
        // Identity endpoints are fixed — see `pin_locked_fields`. Enforced here
        // rather than only in the UI, because the frontend is not a trust
        // boundary: `update_settings` is reachable from anything that can talk
        // to the webview.
        pin_locked_fields(&mut settings, &guard);
        *guard = settings;
    }
    state.persist_settings().map_err(|e| e.to_string())?;
    Ok(state.settings.lock().unwrap().clone())
}

/// Overwrite the fields a user is not allowed to change with what's on disk.
///
/// The Azure application ID and the chat endpoint decide *who you are talking
/// to* when you sign in and when you chat. Pointing sign-in at someone else's
/// app registration hands them your OAuth flow, and pointing chat at another
/// server sends your username and messages there instead — both are
/// credential-adjacent, neither has a legitimate reason to be edited in a
/// settings box, and a wrong value just breaks login with an error that looks
/// like our bug.
///
/// A blank stored value still falls back to the default, so a settings.json
/// written before these fields existed heals rather than pinning "".
fn pin_locked_fields(incoming: &mut GlobalSettings, current: &GlobalSettings) {
    incoming.msa_client_id = non_empty(&current.msa_client_id, settings_defaults::msa_client_id);
    incoming.chat_server = non_empty(&current.chat_server, settings_defaults::chat_server);
    incoming.chat_channel = non_empty(&current.chat_channel, settings_defaults::chat_channel);
    incoming.chat_port = if current.chat_port == 0 {
        settings_defaults::chat_port()
    } else {
        current.chat_port
    };
}

fn non_empty(current: &str, fallback: fn() -> String) -> String {
    if current.trim().is_empty() {
        fallback()
    } else {
        current.to_string()
    }
}

mod settings_defaults {
    pub use crate::models::settings::{
        default_chat_channel as chat_channel, default_chat_port as chat_port,
        default_chat_server as chat_server, default_msa_client_id as msa_client_id,
    };
}

#[cfg(test)]
mod tests {
    use super::*;

    fn stored() -> GlobalSettings {
        GlobalSettings {
            msa_client_id: "the-real-app-id".to_string(),
            chat_server: "irc.example.net".to_string(),
            chat_port: 6697,
            chat_channel: "#blurred-client".to_string(),
            ..Default::default()
        }
    }

    #[test]
    fn rejects_edits_to_the_sign_in_and_chat_endpoints() {
        let current = stored();
        let mut incoming = GlobalSettings {
            msa_client_id: "attacker-app-id".to_string(),
            chat_server: "irc.attacker.example".to_string(),
            chat_port: 1234,
            chat_channel: "#somewhere-else".to_string(),
            ..Default::default()
        };

        pin_locked_fields(&mut incoming, &current);

        assert_eq!(incoming.msa_client_id, "the-real-app-id");
        assert_eq!(incoming.chat_server, "irc.example.net");
        assert_eq!(incoming.chat_port, 6697);
        assert_eq!(incoming.chat_channel, "#blurred-client");
    }

    #[test]
    fn still_accepts_edits_to_everything_else() {
        let current = stored();
        let mut incoming = GlobalSettings {
            accent_color: "#ff0000".to_string(),
            chat_auto_connect: true,
            ..stored()
        };

        pin_locked_fields(&mut incoming, &current);

        assert_eq!(incoming.accent_color, "#ff0000");
        assert!(incoming.chat_auto_connect);
    }

    #[test]
    fn heals_a_blank_stored_value_to_the_default() {
        let current = GlobalSettings {
            msa_client_id: String::new(),
            chat_server: "   ".to_string(),
            chat_port: 0,
            chat_channel: String::new(),
            ..Default::default()
        };
        let mut incoming = GlobalSettings::default();

        pin_locked_fields(&mut incoming, &current);

        assert_eq!(incoming.msa_client_id, settings_defaults::msa_client_id());
        assert_eq!(incoming.chat_server, settings_defaults::chat_server());
        assert_eq!(incoming.chat_port, settings_defaults::chat_port());
        assert_eq!(incoming.chat_channel, settings_defaults::chat_channel());
    }
}
