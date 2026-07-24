import { useState } from "react";
import { createOfflineAccount } from "../lib/tauri";
import { useSettingsStore } from "../store/settingsStore";
import { GlassCard } from "./GlassCard";
import { MicrosoftLoginButton } from "./MicrosoftLoginButton";

/**
 * The hard gate from spec Section 8.1: no dismiss, blocks everything until an
 * account exists. Microsoft/Xbox sign-in is the way in (device-code flow — can
 * join online servers & Realms).
 *
 * Offline mode is gated behind ownership (see the backend anti-piracy check):
 * because this gate only shows when there are ZERO accounts, offline is
 * unavailable here unless the developer dev-override flag is set. Once you've
 * signed in with a Microsoft account that owns the game, you can add offline
 * accounts from the Accounts tab. This keeps the launcher from being usable
 * like TLauncher.
 */
export function LoginGate({ onSuccess }: { onSuccess: () => void }) {
  const [showOffline, setShowOffline] = useState(false);
  const [username, setUsername] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // No Microsoft account can exist yet (gate only shows at zero accounts), so
  // offline is only offered here under the dev override.
  const offlineAllowed = useSettingsStore((s) => s.settings?.allowOfflineWithoutMsa ?? false);

  const trimmed = username.trim();
  const valid = /^[A-Za-z0-9_]{3,16}$/.test(trimmed);

  async function submitOffline(e: React.FormEvent) {
    e.preventDefault();
    if (!valid || busy) return;
    setBusy(true);
    setError(null);
    try {
      await createOfflineAccount(trimmed);
      onSuccess();
    } catch (err) {
      setError(String(err));
      setBusy(false);
    }
  }

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background: "var(--backdrop-gradient)",
      }}
    >
      <GlassCard style={{ width: 420, textAlign: "center", padding: 40 }}>
        <h1 style={{ fontSize: 20, marginBottom: 8 }}>Sign in to Blurred Client</h1>
        <p style={{ color: "var(--text-secondary)", fontSize: 13, marginBottom: 24 }}>
          Sign in with your Microsoft account to play online, or continue offline
          for LAN and singleplayer.
        </p>

        <MicrosoftLoginButton onSuccess={onSuccess} />

        {offlineAllowed ? (
          <>
            <div style={{ display: "flex", alignItems: "center", gap: 12, margin: "20px 0" }}>
              <div style={{ flex: 1, height: 1, background: "var(--glass-border)" }} />
              <span style={{ fontSize: 11, color: "var(--text-tertiary)" }}>OR (DEV)</span>
              <div style={{ flex: 1, height: 1, background: "var(--glass-border)" }} />
            </div>

            {!showOffline ? (
              <button
                onClick={() => setShowOffline(true)}
                style={{
                  background: "transparent",
                  border: "1px solid var(--glass-border)",
                  borderRadius: "var(--radius-sm)",
                  color: "var(--text-secondary)",
                  padding: "8px 16px",
                  fontSize: 13,
                  cursor: "pointer",
                  width: "100%",
                }}
              >
                Continue in offline mode (dev override)
              </button>
            ) : (
              <form onSubmit={submitOffline}>
                <input
                  autoFocus
                  value={username}
                  onChange={(e) => {
                    setUsername(e.target.value);
                    if (error) setError(null);
                  }}
                  placeholder="Username"
                  spellCheck={false}
                  autoComplete="off"
                  style={{
                    width: "100%",
                    fontSize: 16,
                    padding: "12px 14px",
                    marginBottom: 12,
                    textAlign: "center",
                    letterSpacing: 1,
                  }}
                />

                {error && <p style={{ color: "var(--danger)", fontSize: 13, marginBottom: 12 }}>{error}</p>}

                <button
                  className="accent"
                  type="submit"
                  disabled={!valid || busy}
                  style={{ width: "100%", opacity: !valid || busy ? 0.5 : 1 }}
                >
                  {busy ? "Setting up…" : "Continue offline"}
                </button>
                <p style={{ fontSize: 11, color: "var(--text-tertiary)", marginTop: 12 }}>
                  3-16 characters. Letters, numbers and underscores only. Offline
                  accounts can't join online servers or Realms.
                </p>
              </form>
            )}
          </>
        ) : (
          <p style={{ fontSize: 11, color: "var(--text-tertiary)", marginTop: 16 }}>
            Offline mode unlocks after you sign in once with a Microsoft account
            that owns Minecraft — it's for playing your owned copy on LAN and
            singleplayer.
          </p>
        )}
      </GlassCard>
    </div>
  );
}
