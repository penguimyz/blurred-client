import { useEffect, useState } from "react";
import { useAccountStore } from "../store/accountStore";
import { GlassCard } from "../components/GlassCard";
import { MonoField } from "../components/MonoField";
import { open } from "@tauri-apps/plugin-dialog";
import { MicrosoftLoginButton } from "../components/MicrosoftLoginButton";
import { createOfflineAccount, resetAccountSkin, setAccountSkin, setAccountSkinFile } from "../lib/tauri";
import { formatRelativeDate } from "../lib/format";
import type { Account } from "../types/account";

// Full account manager (spec §8): Microsoft and offline accounts side by side,
// with add / switch-active / remove. The active account (the one launch uses) is
// whichever was used most recently — "Set active" just bumps that timestamp.
// Per-instance account assignment is still a later step; this is the global
// switcher.

function ActiveDot() {
  return (
    <span style={{ fontSize: 10, padding: "2px 8px", borderRadius: 10, background: "var(--accent)", color: "var(--accent-fg)" }}>
      Active
    </span>
  );
}

function SkinEditor({ account, onDone }: { account: Account; onDone: () => void }) {
  const [editing, setEditing] = useState(false);
  const [url, setUrl] = useState("");
  const [variant, setVariant] = useState("classic");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function run(fn: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await fn();
      setUrl("");
      setEditing(false);
      onDone();
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }

  async function pickFile() {
    const picked = await open({ multiple: false, filters: [{ name: "Skin PNG", extensions: ["png"] }] });
    if (typeof picked === "string") {
      await run(() => setAccountSkinFile(account.id, picked, variant));
    }
  }

  if (!editing) {
    return (
      <button onClick={() => setEditing(true)} style={skinBtn}>
        Change skin
      </button>
    );
  }

  return (
    <div style={{ marginTop: 10, display: "flex", flexDirection: "column", gap: 6 }}>
      <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
        <label style={{ fontSize: 11, color: "var(--text-secondary)" }}>Model</label>
        <select value={variant} onChange={(e) => setVariant(e.target.value)} style={{ padding: "6px 8px", borderRadius: "var(--radius-sm)", border: "1px solid var(--glass-border)", background: "rgba(0,0,0,0.2)", color: "var(--text-primary)", fontSize: 12 }}>
          <option value="classic">Classic</option>
          <option value="slim">Slim</option>
        </select>
        <button className="accent" disabled={busy} onClick={pickFile} style={{ fontSize: 12, padding: "6px 10px" }}>
          {busy ? "…" : "From file…"}
        </button>
        <button disabled={busy} onClick={() => run(() => resetAccountSkin(account.id))} style={skinBtn}>
          Reset
        </button>
        <button disabled={busy} onClick={() => setEditing(false)} style={skinBtn}>
          Cancel
        </button>
      </div>
      <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
        <input
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          placeholder="…or paste a public .png URL"
          spellCheck={false}
          style={{ flex: 1, padding: "6px 8px", borderRadius: "var(--radius-sm)", border: "1px solid var(--glass-border)", background: "rgba(0,0,0,0.2)", color: "var(--text-primary)", fontSize: 12 }}
        />
        <button className="accent" disabled={busy || !url.trim()} onClick={() => run(() => setAccountSkin(account.id, url.trim(), variant))} style={{ fontSize: 12, padding: "6px 10px" }}>
          {busy ? "…" : "Apply URL"}
        </button>
      </div>
      {error && <div style={{ color: "var(--danger)", fontSize: 11 }}>{error}</div>}
    </div>
  );
}

function AccountCard({ account, active, onActivate, onRemove, onDone }: { account: Account; active: boolean; onActivate: () => void; onRemove: () => void; onDone: () => void }) {
  const isMs = account.accountType === "microsoft";
  return (
    <GlassCard>
      <div style={{ display: "flex", gap: 12, alignItems: "flex-start" }}>
        {/* Skin face crop for Microsoft accounts (skinUrl is a full skin PNG;
            we just show it — a proper face crop would need canvas slicing). */}
        <div style={{ width: 44, height: 44, borderRadius: "var(--radius-sm)", background: "var(--glass-bg-elevated)", overflow: "hidden", flexShrink: 0, display: "flex", alignItems: "center", justifyContent: "center" }}>
          {account.skinUrl ? (
            <img src={account.skinUrl} alt="" style={{ width: "100%", height: "100%", objectFit: "cover", imageRendering: "pixelated" }} />
          ) : (
            <span style={{ fontSize: 18 }}>{isMs ? "🎮" : "👤"}</span>
          )}
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <span style={{ fontWeight: 600, fontSize: 15 }}>{account.username}</span>
            {active && <ActiveDot />}
          </div>
          <span style={{ fontSize: 11, color: "var(--text-tertiary)" }}>
            {isMs ? "Microsoft account" : "Offline account"} · used {formatRelativeDate(account.lastUsed)}
          </span>
        </div>
      </div>

      <div style={{ marginTop: 10 }}>
        <div style={{ fontSize: 10, color: "var(--text-tertiary)", marginBottom: 2 }}>UUID</div>
        <MonoField value={account.mcUuid} copyable />
      </div>

      <div style={{ display: "flex", gap: 6, marginTop: 12, alignItems: "center", flexWrap: "wrap" }}>
        {!active && (
          <button className="accent" onClick={onActivate} style={{ fontSize: 12, padding: "6px 12px" }}>
            Set active
          </button>
        )}
        <button onClick={onRemove} style={ghostBtn}>
          Remove
        </button>
        {isMs && <SkinEditor account={account} onDone={onDone} />}
      </div>
    </GlassCard>
  );
}

export function Accounts() {
  const { accounts, refresh, remove, setActive } = useAccountStore();
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const activeId = [...accounts].sort((a, b) => (b.lastUsed ?? "").localeCompare(a.lastUsed ?? ""))[0]?.id;
  // Offline accounts require a Microsoft account (proof of ownership) and always
  // inherit its username (anti-impersonation) — both enforced in the backend.
  // This is the Microsoft account an offline copy would derive from.
  const activeMsUsername =
    [...accounts]
      .filter((a) => a.accountType === "microsoft")
      .sort((a, b) => (b.lastUsed ?? "").localeCompare(a.lastUsed ?? ""))[0]?.username ?? null;

  async function addOffline() {
    setError(null);
    try {
      await createOfflineAccount();
      setAdding(false);
      await refresh();
    } catch (e) {
      setError(String(e));
    }
  }

  return (
    <div style={{ padding: 32, height: "100%", overflowY: "auto" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 4 }}>
        <h1 style={{ margin: 0, fontSize: 22, fontWeight: 600 }}>Accounts</h1>
        {!adding && (
          <button className="accent" onClick={() => setAdding(true)}>
            + Add account
          </button>
        )}
      </div>
      <div style={{ fontSize: 12, color: "var(--text-secondary)", marginBottom: 24 }}>
        Launch uses the active account. Microsoft accounts can join online servers &amp; Realms; offline accounts are LAN/singleplayer only.
      </div>

      {error && <div style={{ color: "var(--danger)", fontSize: 13, marginBottom: 16 }}>{error}</div>}

      {adding && (
        <GlassCard style={{ maxWidth: 420, marginBottom: 24 }}>
          <h3 style={{ marginTop: 0, fontSize: 14 }}>Add an account</h3>
          <MicrosoftLoginButton
            onSuccess={() => {
              setAdding(false);
              refresh();
            }}
          />
          <div style={{ display: "flex", alignItems: "center", gap: 12, margin: "16px 0" }}>
            <div style={{ flex: 1, height: 1, background: "var(--glass-border)" }} />
            <span style={{ fontSize: 11, color: "var(--text-tertiary)" }}>OR OFFLINE</span>
            <div style={{ flex: 1, height: 1, background: "var(--glass-border)" }} />
          </div>
          {activeMsUsername ? (
            <div>
              <p style={{ fontSize: 12, color: "var(--text-secondary)", margin: "0 0 10px" }}>
                Offline accounts use your Microsoft username — this makes an
                offline-playable copy of <strong>{activeMsUsername}</strong> for
                LAN/singleplayer. (You can't set it to another player's name.)
              </p>
              <button className="accent" onClick={addOffline}>
                Add offline copy of {activeMsUsername}
              </button>
            </div>
          ) : (
            <p style={{ fontSize: 11, color: "var(--text-tertiary)", margin: 0 }}>
              Add a Microsoft account that owns Minecraft first — then you can create
              offline accounts for LAN/singleplayer.
            </p>
          )}
          <button onClick={() => setAdding(false)} style={{ ...ghostBtn, marginTop: 12 }}>
            Cancel
          </button>
        </GlassCard>
      )}

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(300px, 1fr))", gap: 16, maxWidth: 720 }}>
        {accounts.map((a) => (
          <AccountCard
            key={a.id}
            account={a}
            active={a.id === activeId}
            onDone={refresh}
            onActivate={() => setActive(a.id).catch((e) => setError(String(e)))}
            onRemove={() => {
              if (confirm(`Remove ${a.username}? ${a.accountType === "microsoft" ? "You'll need to sign in again to re-add it." : ""}`)) {
                remove(a.id).catch((e) => setError(String(e)));
              }
            }}
          />
        ))}
      </div>
    </div>
  );
}

const ghostBtn = {
  background: "transparent",
  border: "1px solid var(--glass-border)",
  borderRadius: "var(--radius-sm)",
  color: "var(--text-secondary)",
  padding: "6px 12px",
  fontSize: 12,
  cursor: "pointer",
};

const skinBtn = {
  background: "transparent",
  border: "1px solid var(--glass-border)",
  borderRadius: "var(--radius-sm)",
  color: "var(--text-secondary)",
  padding: "6px 10px",
  fontSize: 12,
  cursor: "pointer",
};
