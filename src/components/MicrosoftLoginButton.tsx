import { useState } from "react";
import { open } from "@tauri-apps/plugin-shell";
import type { DeviceCodeInfo } from "../types/account";
import { beginMsaLogin, completeMsaLogin } from "../lib/tauri";

// Reusable Microsoft/Xbox sign-in (device-code flow), shared by the login gate
// and the Accounts page. Flow: begin -> show the short code + open
// microsoft.com/link in the browser -> block on complete_msa_login until the
// user approves. The whole chain (Xbox/XSTS/Minecraft, keychain storage) runs
// backend-side; this just drives the UX.

export function MicrosoftLoginButton({
  onSuccess,
  label = "Sign in with Microsoft",
}: {
  onSuccess: () => void;
  label?: string;
}) {
  const [info, setInfo] = useState<DeviceCodeInfo | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  async function start() {
    setBusy(true);
    setError(null);
    try {
      const dc = await beginMsaLogin();
      setInfo(dc);
      // Copy the code and open the verification page to smooth the hand-off.
      try {
        await navigator.clipboard.writeText(dc.userCode);
        setCopied(true);
      } catch {
        /* clipboard optional */
      }
      open(dc.verificationUri).catch(() => {});
      // Blocks until the user approves in the browser (or the code expires).
      await completeMsaLogin(dc.deviceCode, dc.interval, dc.expiresIn);
      setInfo(null);
      onSuccess();
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }

  if (info && busy) {
    return (
      <div style={{ textAlign: "center" }}>
        <div style={{ fontSize: 13, color: "var(--text-secondary)", marginBottom: 8 }}>
          Enter this code at{" "}
          <a
            href="#"
            onClick={(e) => {
              e.preventDefault();
              open(info.verificationUri).catch(() => {});
            }}
            style={{ color: "var(--accent)" }}
          >
            {info.verificationUri.replace(/^https?:\/\//, "")}
          </a>
          :
        </div>
        <div
          style={{
            fontFamily: "var(--font-mono)",
            fontSize: 28,
            letterSpacing: 4,
            fontWeight: 700,
            padding: "12px 16px",
            borderRadius: "var(--radius-md)",
            background: "var(--glass-bg-elevated)",
            display: "inline-block",
          }}
        >
          {info.userCode}
        </div>
        <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginTop: 8 }}>
          {copied ? "Copied to clipboard. " : ""}Waiting for you to approve in the browser…
        </div>
      </div>
    );
  }

  return (
    <div style={{ textAlign: "center" }}>
      <button className="accent" onClick={start} disabled={busy} style={{ width: "100%" }}>
        {busy ? "Starting…" : label}
      </button>
      {error && <div style={{ color: "var(--danger)", fontSize: 12, marginTop: 8 }}>{error}</div>}
    </div>
  );
}
