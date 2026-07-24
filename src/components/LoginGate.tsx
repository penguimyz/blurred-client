import { useState } from "react";
import { createOfflineAccount } from "../lib/tauri";
import { GlassCard } from "./GlassCard";
import { MicrosoftLoginButton } from "./MicrosoftLoginButton";

/**
 * The hard gate from spec Section 8.1: no dismiss, blocks everything until an
 * account exists. Now dual-mode: sign in with a real Microsoft/Xbox account
 * (device-code flow — can join online servers & Realms), or continue in offline
 * mode with just a username (LAN / offline-mode servers). Microsoft is the
 * primary path; offline is a deliberate opt-in below it.
 */
export function LoginGate({ onSuccess }: { onSuccess: () => void }) {
  const [showOffline, setShowOffline] = useState(false);
  const [username, setUsername] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

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

        <div style={{ display: "flex", alignItems: "center", gap: 12, margin: "20px 0" }}>
          <div style={{ flex: 1, height: 1, background: "var(--glass-border)" }} />
          <span style={{ fontSize: 11, color: "var(--text-tertiary)" }}>OR</span>
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
            Continue in offline mode
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
      </GlassCard>
    </div>
  );
}
