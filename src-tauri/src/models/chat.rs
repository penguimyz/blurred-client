use serde::{Deserialize, Serialize};

/// Where a crew relationship stands.
///
/// IRC has no friend concept at all, so the handshake is ours: two Blurred
/// Clients exchange CTCP messages (see `commands::chat`, `CTCP_FRIEND`) to move
/// through these states. That makes requests *real* — the other person has to
/// accept — rather than a one-sided bookmark dressed up as a friendship.
///
/// The honest limit, which the UI states plainly: this only works if the other
/// person is also running Blurred Client and is online when you send it. Any
/// other IRC client will silently ignore the CTCP, so the request just stays
/// `PendingOut` forever. That's why `PendingOut` is resendable.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum FriendStatus {
    /// We sent a request and are waiting on them.
    PendingOut,
    /// They asked us; awaiting our accept/decline.
    PendingIn,
    /// Mutual. Only these get presence tracking.
    Accepted,
}

/// A crew member or a pending request. Persisted to `friends.json`.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Friend {
    pub nick: String,
    /// Optional user-set label, e.g. their Minecraft name if it differs from
    /// the nick they chat under. For an incoming request this holds the
    /// greeting they sent with it.
    #[serde(default)]
    pub note: String,
    pub added_at: String,
    /// Last known presence. Persisted only so the list doesn't flash "offline"
    /// for everyone on a cold start before MONITOR replies land; the live value
    /// always comes from the server.
    #[serde(default)]
    pub online: bool,
    /// Defaults to `Accepted` so friends saved before requests existed are
    /// treated as established rather than silently reverting to pending.
    #[serde(default = "default_status")]
    pub status: FriendStatus,
}

fn default_status() -> FriendStatus {
    FriendStatus::Accepted
}

impl Friend {
    pub fn is_accepted(&self) -> bool {
        self.status == FriendStatus::Accepted
    }
}
