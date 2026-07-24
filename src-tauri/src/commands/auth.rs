// Offline accounts. No Microsoft/Xbox/Minecraft-services chain, no OAuth, no
// keychain -- the user just types a username and we mint a local account for
// it. This is the "offline mode" every launcher offers for LAN / singleplayer
// / cracked-server use where the real Mojang session servers aren't involved.
//
// The only non-obvious bit is the UUID: we derive it the exact same way
// vanilla Minecraft does for offline players (see `offline_uuid`), so a given
// username always maps to the same identity across sessions -- and matches
// what an offline-mode server would independently compute for that name.

use chrono::Utc;
use md5::{Digest, Md5};
use tauri::State;
use uuid::Uuid;

use crate::models::account::{Account, AccountType};
use crate::state::AppState;

/// Vanilla Minecraft assigns offline players a UUID via
/// `UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(UTF_8))`, which
/// is a type-3 (MD5) UUID over that string with no namespace prefix. We
/// reproduce it byte-for-byte here so our offline identity is stable per
/// username and lines up with what offline-mode servers derive themselves.
fn offline_uuid(username: &str) -> Uuid {
    let mut hasher = Md5::new();
    hasher.update(format!("OfflinePlayer:{username}").as_bytes());
    let digest = hasher.finalize();

    let mut bytes = [0u8; 16];
    bytes.copy_from_slice(&digest);
    // Stamp version (3) and IETF variant bits, exactly like Java does.
    bytes[6] = (bytes[6] & 0x0f) | 0x30;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;

    Uuid::from_bytes(bytes)
}

/// Minecraft username rules: 3-16 chars, ASCII letters/digits/underscore.
fn validate_username(name: &str) -> Result<(), String> {
    if name.len() < 3 || name.len() > 16 {
        return Err("Username must be between 3 and 16 characters.".into());
    }
    if !name.chars().all(|c| c.is_ascii_alphanumeric() || c == '_') {
        return Err("Username can only contain letters, numbers, and underscores.".into());
    }
    Ok(())
}

#[tauri::command]
pub async fn create_offline_account(state: State<'_, AppState>) -> Result<Account, String> {
    // Hard gate, no bypass. Offline accounts:
    //  1. ANTI-PIRACY — require a signed-in Microsoft account that owns the game
    //     (proof of ownership) to exist first. No Microsoft account => no offline
    //     account, period. There is no override flag or env var.
    //  2. ANTI-IMPERSONATION — the offline identity is ALWAYS your Microsoft
    //     username (the active one), so offline mode can never be used to take
    //     another player's name. There is no free-text username to supply.
    // Net effect: an offline account is just the offline-playable form of your
    // owned Microsoft identity, for LAN/singleplayer.
    let username = {
        let accounts = state.accounts.lock().unwrap();
        accounts
            .iter()
            .filter(|a| a.account_type == AccountType::Microsoft)
            .max_by_key(|a| a.last_used)
            .map(|a| a.username.clone())
    }
    .ok_or_else(|| {
        "Sign in with a Microsoft account that owns Minecraft before adding an offline account. \
         Offline mode is for playing your owned copy on LAN/singleplayer — not for playing without owning the game."
            .to_string()
    })?;

    validate_username(&username)?;

    let account = Account {
        id: Uuid::new_v4(),
        account_type: crate::models::account::AccountType::Offline,
        mc_uuid: offline_uuid(&username).to_string(),
        username,
        skin_url: None,
        added_at: Utc::now(),
        last_used: Some(Utc::now()),
    };

    let mut accounts = state.accounts.lock().unwrap();
    // Re-adding the same name just refreshes the existing account instead of
    // stacking duplicates that would all resolve to the same offline UUID.
    accounts.retain(|a| !a.username.eq_ignore_ascii_case(&account.username));
    accounts.push(account.clone());
    let accounts_clone = accounts.clone();
    drop(accounts);

    crate::state::persist_accounts(&state.data_dir, &accounts_clone).map_err(|e| e.to_string())?;

    Ok(account)
}

#[tauri::command]
pub async fn remove_account(
    state: State<'_, AppState>,
    account_id: String,
) -> Result<(), String> {
    let mut accounts = state.accounts.lock().unwrap();
    accounts.retain(|a| a.id.to_string() != account_id);
    let accounts_clone = accounts.clone();
    drop(accounts);
    // Drop any Microsoft refresh token from the keychain too (no-op for offline).
    crate::commands::online_auth::delete_refresh_token(&account_id);
    crate::state::persist_accounts(&state.data_dir, &accounts_clone).map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn list_accounts(state: State<'_, AppState>) -> Result<Vec<Account>, String> {
    Ok(state.accounts.lock().unwrap().clone())
}

/// Make an account the active one by bumping its `last_used` — launch picks the
/// most recently used account, so this is how the account switcher works without
/// a separate "active" flag to keep in sync.
#[tauri::command]
pub async fn set_active_account(
    state: State<'_, AppState>,
    account_id: String,
) -> Result<Vec<Account>, String> {
    let mut accounts = state.accounts.lock().unwrap();
    let found = accounts.iter_mut().find(|a| a.id.to_string() == account_id);
    match found {
        Some(a) => a.last_used = Some(Utc::now()),
        None => return Err(format!("account {account_id} not found")),
    }
    let snapshot = accounts.clone();
    drop(accounts);
    crate::state::persist_accounts(&state.data_dir, &snapshot).map_err(|e| e.to_string())?;
    Ok(snapshot)
}
