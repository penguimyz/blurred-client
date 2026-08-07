// Real Microsoft/Xbox sign-in — the MSA -> Xbox Live -> XSTS -> Minecraft
// Services chain (spec §8). Uses the OAuth 2.0 device-code flow (best fit for a
// desktop app with no browser redirect): we show the user a short code, they
// enter it at microsoft.com/link, and we poll until they've approved.
//
// SECURITY MODEL: the only long-lived secret is the MSA refresh token, and it
// goes in the OS keychain (via `keyring`), keyed by the account id — never into
// accounts.json. The short-lived Minecraft access token is re-derived from the
// refresh token at launch and kept in memory only, so no bearer token is ever
// written to disk.
//
// NOT LIVE-TESTED from this sandbox (no network to login.microsoftonline.com /
// xboxlive.com / minecraftservices.com) — written against the documented,
// long-stable endpoint contracts. Azure app must have "Allow public client
// flows" enabled for the device-code grant to work.

use std::time::{Duration, Instant};

use chrono::Utc;
use serde::{Deserialize, Serialize};
use tauri::State;
use uuid::Uuid;

use crate::models::account::{Account, AccountType};
use crate::state::AppState;

const DEVICE_CODE_URL: &str =
    "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
const TOKEN_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const XBL_URL: &str = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_URL: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";
const MC_LOGIN_URL: &str = "https://api.minecraftservices.com/authentication/login_with_xbox";
const MC_PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";
const SCOPE: &str = "XboxLive.signin offline_access";
/// Service name for the OS keychain entries holding Microsoft refresh tokens.
///
/// This deliberately still reads `.app` even though the bundle identifier is
/// now `dev.blurredclient.launcher`. It is not the bundle identifier — it just
/// happened to be spelled the same — and it is the key existing installs
/// already stored their tokens under. Changing it to match would orphan every
/// saved token and silently sign everyone out on upgrade, for no benefit.
const KEYRING_SERVICE: &str = "dev.blurredclient.app";

// ---- keychain ----

fn keyring_entry(account_id: &str) -> Result<keyring::Entry, String> {
    keyring::Entry::new(KEYRING_SERVICE, account_id).map_err(keychain_error)
}

/// Turn a keychain failure into something the user can act on. This matters
/// most on Linux, where the backend is the D-Bus Secret Service — which simply
/// isn't running on a minimal desktop or a fresh tiling-WM setup. The raw error
/// there is a D-Bus name-resolution failure that reads like a launcher bug
/// rather than "install a keyring daemon".
fn keychain_error(e: keyring::Error) -> String {
    #[cfg(target_os = "linux")]
    {
        return format!(
            "couldn't reach the system keyring ({e}). Blurred Client keeps your Microsoft \
             session in the D-Bus Secret Service — install and start a provider \
             (gnome-keyring, or KDE's kwallet with kwallet-pam) and sign in again."
        );
    }
    #[cfg(not(target_os = "linux"))]
    format!("couldn't reach the system keyring ({e})")
}

fn store_refresh_token(account_id: &str, token: &str) -> Result<(), String> {
    keyring_entry(account_id)?.set_password(token).map_err(keychain_error)
}

fn read_refresh_token(account_id: &str) -> Result<String, String> {
    keyring_entry(account_id)?.get_password().map_err(|e| match e {
        keyring::Error::NoEntry => "no stored Microsoft session — sign in again".to_string(),
        other => keychain_error(other),
    })
}

/// Best-effort keychain cleanup when an account is removed.
pub fn delete_refresh_token(account_id: &str) {
    if let Ok(entry) = keyring_entry(account_id) {
        let _ = entry.delete_password();
    }
}

// ---- device code flow ----

#[derive(Debug, Deserialize)]
struct DeviceCodeResp {
    user_code: String,
    device_code: String,
    verification_uri: String,
    expires_in: u64,
    interval: u64,
    message: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DeviceCodeInfo {
    pub user_code: String,
    pub device_code: String,
    pub verification_uri: String,
    pub expires_in: u64,
    pub interval: u64,
    pub message: String,
}

#[derive(Debug, Deserialize)]
struct TokenResp {
    access_token: String,
    refresh_token: Option<String>,
}

#[derive(Debug, Deserialize)]
struct TokenErr {
    error: String,
}

fn client_id(state: &AppState) -> String {
    state.settings.lock().unwrap().msa_client_id.clone()
}

/// Kick off device-code login: returns the code + URL to show the user. They
/// approve out-of-band; the frontend then calls `complete_msa_login`.
#[tauri::command]
pub async fn begin_msa_login(state: State<'_, AppState>) -> Result<DeviceCodeInfo, String> {
    let cid = client_id(&state);
    let client = reqwest::Client::new();
    let resp = client
        .post(DEVICE_CODE_URL)
        .form(&[("client_id", cid.as_str()), ("scope", SCOPE)])
        .send()
        .await
        .map_err(|e| e.to_string())?;
    if !resp.status().is_success() {
        let body = resp.text().await.unwrap_or_default();
        return Err(format!("device code request failed: {body}"));
    }
    let d: DeviceCodeResp = resp.json().await.map_err(|e| e.to_string())?;
    Ok(DeviceCodeInfo {
        user_code: d.user_code,
        device_code: d.device_code,
        verification_uri: d.verification_uri,
        expires_in: d.expires_in,
        interval: d.interval,
        message: d.message,
    })
}

/// Poll for approval of a device code, then run the full auth chain and persist
/// the resulting account. Blocks (server-side polling) until the user approves,
/// the code expires, or they decline — bounded by `expires_in`.
#[tauri::command]
pub async fn complete_msa_login(
    state: State<'_, AppState>,
    device_code: String,
    interval: u64,
    expires_in: u64,
) -> Result<Account, String> {
    let cid = client_id(&state);
    let client = reqwest::Client::new();

    let deadline = Instant::now() + Duration::from_secs(expires_in.min(900));
    let poll_every = Duration::from_secs(interval.max(1));

    let (msa_access, msa_refresh) = loop {
        if Instant::now() >= deadline {
            return Err("sign-in timed out — the code expired".to_string());
        }
        tokio::time::sleep(poll_every).await;

        let resp = client
            .post(TOKEN_URL)
            .form(&[
                ("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                ("client_id", cid.as_str()),
                ("device_code", device_code.as_str()),
            ])
            .send()
            .await
            .map_err(|e| e.to_string())?;

        if resp.status().is_success() {
            let t: TokenResp = resp.json().await.map_err(|e| e.to_string())?;
            let refresh = t
                .refresh_token
                .ok_or_else(|| "Microsoft returned no refresh token (is offline_access scoped?)".to_string())?;
            break (t.access_token, refresh);
        }

        // 400 with authorization_pending / slow_down means "keep waiting".
        let err: TokenErr = resp.json().await.map_err(|e| e.to_string())?;
        match err.error.as_str() {
            "authorization_pending" => continue,
            "slow_down" => {
                tokio::time::sleep(poll_every).await;
                continue;
            }
            "authorization_declined" => return Err("sign-in was declined".to_string()),
            "expired_token" => return Err("the sign-in code expired".to_string()),
            other => return Err(format!("Microsoft sign-in error: {other}")),
        }
    };

    let (profile, _mc_token) = run_chain(&client, &msa_access).await?;

    // De-dupe by Minecraft UUID: signing in again as the same player refreshes
    // the existing account rather than stacking a duplicate.
    let account = {
        let mut accounts = state.accounts.lock().unwrap();
        let id = accounts
            .iter()
            .find(|a| a.mc_uuid == profile.id && a.account_type == AccountType::Microsoft)
            .map(|a| a.id)
            .unwrap_or_else(Uuid::new_v4);

        let account = Account {
            id,
            account_type: AccountType::Microsoft,
            mc_uuid: profile.id.clone(),
            username: profile.name.clone(),
            skin_url: profile.skin_url.clone(),
            added_at: Utc::now(),
            last_used: Some(Utc::now()),
        };
        accounts.retain(|a| a.id != id);
        accounts.push(account.clone());
        let snapshot = accounts.clone();
        drop(accounts);
        crate::state::persist_accounts(&state.data_dir, &snapshot).map_err(|e| e.to_string())?;
        account
    };

    // Keychain write happens after the account id is settled.
    store_refresh_token(&account.id.to_string(), &msa_refresh)?;
    Ok(account)
}

// ---- the Xbox/XSTS/Minecraft chain ----

struct McProfile {
    id: String, // Minecraft UUID (undashed, as Mojang returns it)
    name: String,
    skin_url: Option<String>,
}

/// Full chain from an MSA access token to a live Minecraft token + profile.
/// Returns both so callers that need the token (launch) don't re-walk the chain.
async fn run_chain(client: &reqwest::Client, msa_access: &str) -> Result<(McProfile, String), String> {
    let (xbl_token, uhs) = xbox_authenticate(client, msa_access).await?;
    let xsts_token = xsts_authorize(client, &xbl_token).await?;
    let mc_token = minecraft_login(client, &uhs, &xsts_token).await?;
    let profile = fetch_profile(client, &mc_token).await?;
    Ok((profile, mc_token))
}

#[derive(Debug, Deserialize)]
struct XboxResp {
    #[serde(rename = "Token")]
    token: String,
    #[serde(rename = "DisplayClaims")]
    display_claims: DisplayClaims,
}
#[derive(Debug, Deserialize)]
struct DisplayClaims {
    xui: Vec<Xui>,
}
#[derive(Debug, Deserialize)]
struct Xui {
    uhs: String,
}

async fn xbox_authenticate(client: &reqwest::Client, msa_access: &str) -> Result<(String, String), String> {
    let body = serde_json::json!({
        "Properties": {
            "AuthMethod": "RPS",
            "SiteName": "user.auth.xboxlive.com",
            "RpsTicket": format!("d={msa_access}")
        },
        "RelyingParty": "http://auth.xboxlive.com",
        "TokenType": "JWT"
    });
    let resp = client
        .post(XBL_URL)
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .json(&body)
        .send()
        .await
        .map_err(|e| e.to_string())?;
    if !resp.status().is_success() {
        return Err(format!("Xbox Live auth failed ({})", resp.status()));
    }
    let x: XboxResp = resp.json().await.map_err(|e| e.to_string())?;
    let uhs = x
        .display_claims
        .xui
        .first()
        .map(|u| u.uhs.clone())
        .ok_or_else(|| "Xbox Live returned no user hash".to_string())?;
    Ok((x.token, uhs))
}

#[derive(Debug, Deserialize)]
struct XstsErr {
    #[serde(rename = "XErr")]
    xerr: i64,
}

async fn xsts_authorize(client: &reqwest::Client, xbl_token: &str) -> Result<String, String> {
    let body = serde_json::json!({
        "Properties": { "SandboxId": "RETAIL", "UserTokens": [xbl_token] },
        "RelyingParty": "rp://api.minecraftservices.com/",
        "TokenType": "JWT"
    });
    let resp = client
        .post(XSTS_URL)
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .json(&body)
        .send()
        .await
        .map_err(|e| e.to_string())?;

    if resp.status() == reqwest::StatusCode::UNAUTHORIZED {
        // XSTS uses XErr codes to explain why a valid Xbox token can't be elevated.
        if let Ok(err) = resp.json::<XstsErr>().await {
            let msg = match err.xerr {
                2148916233 => "This Microsoft account has no Xbox profile — create one at xbox.com first.",
                2148916235 => "Xbox Live isn't available in this account's region.",
                2148916236 | 2148916237 => "This account needs adult verification.",
                2148916238 => "This is a child account — it must be added to a Family group first.",
                _ => "Xbox security token (XSTS) authorization was denied.",
            };
            return Err(msg.to_string());
        }
        return Err("Xbox security token (XSTS) authorization was denied.".to_string());
    }
    if !resp.status().is_success() {
        return Err(format!("XSTS auth failed ({})", resp.status()));
    }
    let x: XboxResp = resp.json().await.map_err(|e| e.to_string())?;
    Ok(x.token)
}

#[derive(Debug, Deserialize)]
struct McAuthResp {
    access_token: String,
}

async fn minecraft_login(client: &reqwest::Client, uhs: &str, xsts_token: &str) -> Result<String, String> {
    let body = serde_json::json!({ "identityToken": format!("XBL3.0 x={uhs};{xsts_token}") });
    let resp = client
        .post(MC_LOGIN_URL)
        .json(&body)
        .send()
        .await
        .map_err(|e| e.to_string())?;
    if resp.status() == reqwest::StatusCode::UNAUTHORIZED || resp.status() == reqwest::StatusCode::FORBIDDEN {
        return Err(
            "Minecraft rejected the Xbox token. If your XSTS token is valid, your Azure app may not be \
             approved for the Minecraft Services API — check Microsoft/Mojang's launcher-auth approval process."
                .to_string(),
        );
    }
    if !resp.status().is_success() {
        return Err(format!("Minecraft login failed ({})", resp.status()));
    }
    let m: McAuthResp = resp.json().await.map_err(|e| e.to_string())?;
    Ok(m.access_token)
}

#[derive(Debug, Deserialize)]
struct ProfileResp {
    id: String,
    name: String,
    #[serde(default)]
    skins: Vec<ProfileSkin>,
}
#[derive(Debug, Deserialize)]
struct ProfileSkin {
    url: String,
    state: String,
}

async fn fetch_profile(client: &reqwest::Client, mc_token: &str) -> Result<McProfile, String> {
    let resp = client
        .get(MC_PROFILE_URL)
        .bearer_auth(mc_token)
        .send()
        .await
        .map_err(|e| e.to_string())?;
    if resp.status() == reqwest::StatusCode::NOT_FOUND {
        return Err("This account doesn't own Minecraft: Java Edition.".to_string());
    }
    if !resp.status().is_success() {
        return Err(format!("fetching Minecraft profile failed ({})", resp.status()));
    }
    let p: ProfileResp = resp.json().await.map_err(|e| e.to_string())?;
    let skin_url = p
        .skins
        .iter()
        .find(|s| s.state.eq_ignore_ascii_case("ACTIVE"))
        .or_else(|| p.skins.first())
        .map(|s| s.url.clone());
    Ok(McProfile { id: p.id, name: p.name, skin_url })
}

// ---- launch integration ----

pub struct LaunchCreds {
    pub username: String,
    pub uuid: String, // undashed
    pub access_token: String,
}

// ---- skin management (spec §8) ----

const MC_SKINS_URL: &str = "https://api.minecraftservices.com/minecraft/profile/skins";
const MC_ACTIVE_SKIN_URL: &str = "https://api.minecraftservices.com/minecraft/profile/skins/active";

fn find_microsoft_account(state: &AppState, account_id: &str) -> Result<Account, String> {
    state
        .accounts
        .lock()
        .unwrap()
        .iter()
        .find(|a| a.id.to_string() == account_id && a.account_type == AccountType::Microsoft)
        .cloned()
        .ok_or_else(|| "Microsoft account not found (skins only apply to Microsoft accounts)".to_string())
}

fn store_skin_url(state: &AppState, id: Uuid, skin_url: Option<String>) -> Result<Account, String> {
    let mut accounts = state.accounts.lock().unwrap();
    let acc = accounts
        .iter_mut()
        .find(|a| a.id == id)
        .ok_or_else(|| "account no longer exists".to_string())?;
    acc.skin_url = skin_url;
    let updated = acc.clone();
    let snapshot = accounts.clone();
    drop(accounts);
    crate::state::persist_accounts(&state.data_dir, &snapshot).map_err(|e| e.to_string())?;
    Ok(updated)
}

/// Change the account's skin to the PNG at `url` (`variant` is "classic" or
/// "slim"). Uses a fresh Minecraft token derived from the keychain refresh token.
#[tauri::command]
pub async fn set_account_skin(
    state: State<'_, AppState>,
    account_id: String,
    url: String,
    variant: String,
) -> Result<Account, String> {
    let client_id = client_id(&state);
    let account = find_microsoft_account(&state, &account_id)?;
    let token = authenticate_for_launch(&client_id, &account).await?.access_token;

    let client = reqwest::Client::new();
    let resp = client
        .post(MC_SKINS_URL)
        .bearer_auth(&token)
        .json(&serde_json::json!({ "variant": variant, "url": url }))
        .send()
        .await
        .map_err(|e| e.to_string())?;
    if !resp.status().is_success() {
        return Err(format!("skin change was rejected ({}) — is the URL a public .png?", resp.status()));
    }

    let profile = fetch_profile(&client, &token).await?;
    store_skin_url(&state, account.id, profile.skin_url)
}

/// Change the account's skin from a local PNG file (multipart upload).
#[tauri::command]
pub async fn set_account_skin_file(
    state: State<'_, AppState>,
    account_id: String,
    file_path: String,
    variant: String,
) -> Result<Account, String> {
    let client_id = client_id(&state);
    let account = find_microsoft_account(&state, &account_id)?;
    let token = authenticate_for_launch(&client_id, &account).await?.access_token;

    let bytes = std::fs::read(&file_path).map_err(|e| e.to_string())?;
    let part = reqwest::multipart::Part::bytes(bytes)
        .file_name("skin.png")
        .mime_str("image/png")
        .map_err(|e| e.to_string())?;
    let form = reqwest::multipart::Form::new()
        .text("variant", variant)
        .part("file", part);

    let client = reqwest::Client::new();
    let resp = client
        .post(MC_SKINS_URL)
        .bearer_auth(&token)
        .multipart(form)
        .send()
        .await
        .map_err(|e| e.to_string())?;
    if !resp.status().is_success() {
        return Err(format!("skin upload was rejected ({}) — needs a valid 64x64 (or 64x32) PNG", resp.status()));
    }

    let profile = fetch_profile(&client, &token).await?;
    store_skin_url(&state, account.id, profile.skin_url)
}

/// Reset to the default (Steve/Alex) skin.
#[tauri::command]
pub async fn reset_account_skin(
    state: State<'_, AppState>,
    account_id: String,
) -> Result<Account, String> {
    let client_id = client_id(&state);
    let account = find_microsoft_account(&state, &account_id)?;
    let token = authenticate_for_launch(&client_id, &account).await?.access_token;

    let client = reqwest::Client::new();
    let resp = client
        .delete(MC_ACTIVE_SKIN_URL)
        .bearer_auth(&token)
        .send()
        .await
        .map_err(|e| e.to_string())?;
    if !resp.status().is_success() {
        return Err(format!("skin reset failed ({})", resp.status()));
    }

    let profile = fetch_profile(&client, &token).await?;
    store_skin_url(&state, account.id, profile.skin_url)
}

/// Produce fresh launch credentials for a Microsoft account: refresh the MSA
/// token from the keychain, re-run the chain, and return the live Minecraft
/// access token + profile. Rotates and re-stores the refresh token if Microsoft
/// hands back a new one. Called from `launch_instance`.
pub async fn authenticate_for_launch(client_id: &str, account: &Account) -> Result<LaunchCreds, String> {
    let account_id = account.id.to_string();
    let refresh = read_refresh_token(&account_id)?;

    let client = reqwest::Client::new();
    let resp = client
        .post(TOKEN_URL)
        .form(&[
            ("grant_type", "refresh_token"),
            ("client_id", client_id),
            ("refresh_token", refresh.as_str()),
            ("scope", SCOPE),
        ])
        .send()
        .await
        .map_err(|e| e.to_string())?;
    if !resp.status().is_success() {
        return Err("Microsoft session expired — sign in again from the Accounts screen.".to_string());
    }
    let t: TokenResp = resp.json().await.map_err(|e| e.to_string())?;
    if let Some(new_refresh) = &t.refresh_token {
        // Refresh tokens can rotate; persist the new one so we don't get locked out.
        let _ = store_refresh_token(&account_id, new_refresh);
    }

    let (profile, mc_token) = run_chain(&client, &t.access_token).await?;

    Ok(LaunchCreds {
        username: profile.name,
        uuid: profile.id.replace('-', ""),
        access_token: mc_token,
    })
}
