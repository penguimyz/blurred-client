use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, Default)]
#[serde(rename_all = "lowercase")]
pub enum AccountType {
    /// Local, no secrets — offline/LAN identity (see commands/auth.rs).
    #[default]
    Offline,
    /// Real Microsoft/Xbox login. `accounts.json` holds only the public profile
    /// (uuid/name/skin); the long-lived MSA refresh token lives in the OS
    /// keychain keyed by `id` (see commands/online_auth.rs), and the short-lived
    /// Minecraft access token is re-derived at launch and never persisted.
    Microsoft,
}

/// Account info persisted to accounts.json. For offline accounts everything
/// here is non-secret and self-contained (`mc_uuid` is the deterministic offline
/// UUID derived from `username`). For Microsoft accounts this is still only the
/// non-secret profile — the refresh token is in the keychain, not here.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Account {
    pub id: Uuid,
    #[serde(default)]
    pub account_type: AccountType,
    pub mc_uuid: String,   // real Minecraft UUID (Microsoft) or derived offline UUID
    pub username: String,  // in-game name
    pub skin_url: Option<String>, // populated for Microsoft accounts, None for offline
    pub added_at: chrono::DateTime<chrono::Utc>,
    pub last_used: Option<chrono::DateTime<chrono::Utc>>,
}
