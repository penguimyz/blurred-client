import { GlassCard } from "./GlassCard";
import { Logo } from "./Icon";
import { MicrosoftLoginButton } from "./MicrosoftLoginButton";

/**
 * The hard gate from spec Section 8.1: no dismiss, blocks everything until an
 * account exists. Microsoft/Xbox sign-in is the only way in.
 *
 * Offline mode is not offered here: offline accounts require a signed-in
 * Microsoft account that owns the game (proof of ownership) and inherit that
 * account's username, so they can only be created from the Accounts tab after
 * you've signed in. This is what keeps the launcher from being usable like
 * TLauncher.
 */
export function LoginGate({ onSuccess }: { onSuccess: () => void }) {
  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        // No background of its own: the animated water backdrop sits behind the
        // whole app, and covering it with a flat gradient here would make the
        // very first screen the only static one.
      }}
    >
      <GlassCard style={{ width: 420, textAlign: "center", padding: 40 }}>
        <Logo size={40} />
        <h1 style={{ fontSize: 20, marginBottom: 8, marginTop: 12 }}>Sign in to Blurred Client</h1>
        <p style={{ color: "var(--text-secondary)", fontSize: 13, marginBottom: 24 }}>
          Sign in with your Microsoft account to get started.
        </p>

        <MicrosoftLoginButton onSuccess={onSuccess} />

        <p style={{ fontSize: 11, color: "var(--text-tertiary)", marginTop: 16 }}>
          Offline accounts (for LAN &amp; singleplayer with your owned copy)
          unlock in the Accounts tab once you've signed in.
        </p>
      </GlassCard>
    </div>
  );
}
