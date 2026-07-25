import { useEffect, useState } from "react";
import type { GlobalSettings, Theme } from "../types/settings";
import { useSettingsStore, applyTheme, applyAccent } from "../store/settingsStore";
import { GlassCard } from "../components/GlassCard";
import { MonoField } from "../components/MonoField";
import { CustomCommandsForm, EnvVarsForm, JavaForm } from "../components/OverrideSettingsForm";
import { checkLauncherUpdate, type UpdateStatus } from "../lib/tauri";

// Global launcher settings (spec §10). The Java/Env/Commands editors here are
// the very same components the per-instance Settings tab uses — this screen just
// edits the default values directly (no override toggle), the instance screen
// wraps them in OverrideSection. That shared form is the whole point of Phase 5.
//
// Theme + accent apply live as you change them (before saving) so the glass
// re-tints under your cursor; Save persists.

const THEMES: Theme[] = ["dark", "light", "system"];
const ACCENT_SWATCHES = ["#7c9cff", "#4c6fff", "#a78bfa", "#22d3ee", "#34d399", "#fb7185", "#fbbf24"];

export function Settings() {
  const { settings, refresh, save } = useSettingsStore();
  const [draft, setDraft] = useState<GlobalSettings | null>(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [updateStatus, setUpdateStatus] = useState<UpdateStatus | null>(null);
  const [checkingUpdate, setCheckingUpdate] = useState(false);

  useEffect(() => {
    if (!settings) refresh();
    else setDraft(settings);
  }, [settings, refresh]);

  if (!draft) {
    return <div style={{ padding: 32, color: "var(--text-secondary)" }}>Loading settings…</div>;
  }

  const set = <K extends keyof GlobalSettings>(k: K, v: GlobalSettings[K]) => {
    const next = { ...draft, [k]: v };
    setDraft(next);
    // Live-preview appearance changes without waiting for Save.
    if (k === "theme") applyTheme(v as Theme);
    if (k === "accentColor") applyAccent(v as string);
  };

  const dirty = settings ? JSON.stringify(draft) !== JSON.stringify(settings) : true;

  async function onSave() {
    if (!draft) return;
    setSaving(true);
    setError(null);
    try {
      await save(draft);
      setSaved(true);
      setTimeout(() => setSaved(false), 1500);
    } catch (e) {
      setError(String(e));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div style={{ padding: 32, height: "100%", overflowY: "auto" }}>
      <h1 style={{ margin: "0 0 24px", fontSize: 22, fontWeight: 600 }}>Settings</h1>

      <div style={{ maxWidth: 720, display: "flex", flexDirection: "column", gap: 16 }}>
        <GlassCard>
          <h3 style={{ margin: "0 0 16px", fontSize: 14 }}>Appearance</h3>
          <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            <div>
              <label style={labelStyle}>Theme</label>
              <div style={{ display: "flex", gap: 8 }}>
                {THEMES.map((t) => (
                  <button
                    key={t}
                    onClick={() => set("theme", t)}
                    style={{
                      ...pill,
                      background: draft.theme === t ? "var(--accent)" : "transparent",
                      color: draft.theme === t ? "var(--accent-fg)" : "var(--text-secondary)",
                      textTransform: "capitalize",
                    }}
                  >
                    {t}
                  </button>
                ))}
              </div>
            </div>
            <div>
              <label style={labelStyle}>Accent color</label>
              <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                {ACCENT_SWATCHES.map((c) => (
                  <button
                    key={c}
                    onClick={() => set("accentColor", c)}
                    title={c}
                    style={{
                      width: 28,
                      height: 28,
                      borderRadius: "50%",
                      background: c,
                      border: draft.accentColor.toLowerCase() === c ? "2px solid var(--text-primary)" : "2px solid transparent",
                      cursor: "pointer",
                    }}
                  />
                ))}
                <input
                  type="color"
                  value={draft.accentColor}
                  onChange={(e) => set("accentColor", e.target.value)}
                  style={{ width: 36, height: 28, background: "transparent", border: "none", cursor: "pointer" }}
                />
                <div style={{ width: 120 }}>
                  <MonoField value={draft.accentColor} onChange={(v) => set("accentColor", v)} copyable={false} />
                </div>
              </div>
            </div>
          </div>
        </GlassCard>

        <GlassCard>
          <h3 style={{ margin: "0 0 16px", fontSize: 14 }}>Default Java</h3>
          <JavaForm value={draft.defaultJava} onChange={(v) => set("defaultJava", v)} />
        </GlassCard>

        <GlassCard>
          <h3 style={{ margin: "0 0 16px", fontSize: 14 }}>Default environment variables</h3>
          <EnvVarsForm value={draft.defaultEnvVars} onChange={(v) => set("defaultEnvVars", v)} />
        </GlassCard>

        <GlassCard>
          <h3 style={{ margin: "0 0 16px", fontSize: 14 }}>Default custom commands</h3>
          <CustomCommandsForm value={draft.defaultCustomCommands} onChange={(v) => set("defaultCustomCommands", v)} />
        </GlassCard>

        <GlassCard>
          <h3 style={{ margin: "0 0 16px", fontSize: 14 }}>Storage & updates</h3>
          <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
            <div>
              <label style={labelStyle}>Instance storage folder</label>
              <MonoField value={draft.instanceStoragePath} copyable />
              <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginTop: 4 }}>
                Where instances live on disk. Read-only here — moving instances is a separate operation.
              </div>
            </div>
            <div style={{ maxWidth: 260 }}>
              <label style={labelStyle}>Update check frequency (minutes)</label>
              <input
                type="number"
                min={0}
                value={draft.updateCheckFrequencyMinutes}
                onChange={(e) => set("updateCheckFrequencyMinutes", Number(e.target.value))}
                style={inputStyle}
              />
            </div>
            <div>
              <label style={labelStyle}>Update source (GitHub owner/name)</label>
              <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                <input
                  value={draft.updateRepo}
                  onChange={(e) => set("updateRepo", e.target.value)}
                  placeholder="e.g. your-user/blurred-client (blank = disabled)"
                  style={{ ...inputStyle, flex: 1 }}
                />
                <button
                  onClick={async () => {
                    setCheckingUpdate(true);
                    setUpdateStatus(null);
                    try {
                      // Save first so the check uses the current repo value.
                      if (draft) await save(draft);
                      setUpdateStatus(await checkLauncherUpdate());
                    } catch (e) {
                      setError(String(e));
                    } finally {
                      setCheckingUpdate(false);
                    }
                  }}
                  style={{ ...pill, whiteSpace: "nowrap" }}
                  disabled={checkingUpdate}
                >
                  {checkingUpdate ? "Checking…" : "Check for updates"}
                </button>
              </div>
              {updateStatus && (
                <div style={{ fontSize: 12, marginTop: 8, color: "var(--text-secondary)" }}>
                  {!updateStatus.configured
                    ? `Current version ${updateStatus.currentVersion}. Set a GitHub repo above to enable update checks.`
                    : updateStatus.updateAvailable
                      ? <>
                          Update available: <strong>{updateStatus.latestVersion}</strong> (you have {updateStatus.currentVersion}).{" "}
                          {updateStatus.url && <a href={updateStatus.url} target="_blank" rel="noreferrer" style={{ color: "var(--accent)" }}>Release notes ↗</a>}
                        </>
                      : updateStatus.latestVersion
                        ? `Up to date (${updateStatus.currentVersion}).`
                        : updateStatus.notes ?? `Current version ${updateStatus.currentVersion}.`}
                </div>
              )}
              <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginTop: 4 }}>
                Checks GitHub releases for a newer version. Auto-install isn't wired (needs signed releases) — this notifies only.
              </div>
            </div>
          </div>
        </GlassCard>

        <GlassCard>
          <h3 style={{ margin: "0 0 16px", fontSize: 14 }}>Microsoft sign-in</h3>
          <label style={labelStyle}>Azure application (client) ID</label>
          <MonoField value={draft.msaClientId} onChange={(v) => set("msaClientId", v)} copyable={false} />
          <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginTop: 4 }}>
            The Azure app registration used for Microsoft/Xbox login. Public identifier, not a secret.
            Your Azure app must have "Allow public client flows" enabled. Changing this only affects new sign-ins.
          </div>
        </GlassCard>

        {error && <div style={{ color: "var(--danger)", fontSize: 13 }}>{error}</div>}

        <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
          <button className="accent" onClick={onSave} disabled={!dirty || saving} style={{ opacity: dirty && !saving ? 1 : 0.5 }}>
            {saving ? "Saving…" : "Save settings"}
          </button>
          {saved && <span style={{ fontSize: 12, color: "var(--success)" }}>Saved</span>}
        </div>
      </div>
    </div>
  );
}

const labelStyle = { fontSize: 12, color: "var(--text-secondary)", display: "block" as const, marginBottom: 6 };
const pill = {
  border: "1px solid var(--glass-border)",
  borderRadius: "var(--radius-sm)",
  padding: "8px 18px",
  fontSize: 13,
  cursor: "pointer",
};
const inputStyle = {
  width: "100%",
  padding: "8px 10px",
  borderRadius: "var(--radius-sm)",
  border: "1px solid var(--glass-border)",
  background: "rgba(0,0,0,0.2)",
  color: "var(--text-primary)",
  fontSize: 13,
};
