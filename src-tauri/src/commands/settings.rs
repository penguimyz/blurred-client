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
    settings: GlobalSettings,
) -> Result<GlobalSettings, String> {
    {
        let mut guard = state.settings.lock().unwrap();
        *guard = settings;
    }
    state.persist_settings().map_err(|e| e.to_string())?;
    Ok(state.settings.lock().unwrap().clone())
}
