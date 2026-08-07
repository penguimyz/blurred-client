import { useEffect, useState } from "react";
import type { GlobalSettings, Theme } from "../types/settings";
import { useSettingsStore, applyTheme, applyAccent } from "../store/settingsStore";
import { GlassCard } from "../components/GlassCard";
import { PageHeader } from "../components/PageHeader";
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
// Reordered for the ocean palette: the bioluminescent cyan default first, then
// the rest of the reef. The old purple stays available, just not first.
const ACCENT_SWATCHES = ["#35e0d0", "#22d3ee", "#38bdf8", "#34d399", "#a78bfa", "#fb7185", "#fbbf24"];

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
      <PageHeader page="settings" />

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
                      borderRadius: 0,
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
          <h3 style={{ margin: "0 0 6px", fontSize: 14 }}>Connections</h3>
          <p style={{ margin: "0 0 16px", fontSize: 11.5, color: "var(--text-tertiary)", lineHeight: 1.55 }}>
            Where sign-in and chat actually connect to. These are fixed, and shown here so you can
            see them rather than edit them — pointing sign-in at a different Azure app hands your
            Microsoft login flow to whoever owns it, and pointing chat somewhere else sends your
            username and messages there. Neither has a good reason to be a text box.
          </p>
          <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
            <div>
              <label style={labelStyle}>
                Azure application (client) ID <Locked />
              </label>
              <MonoField value={draft.msaClientId} copyable />
            </div>
            <div style={{ display: "flex", gap: 10 }}>
              <div style={{ flex: 1 }}>
                <label style={labelStyle}>
                  Chat server <Locked />
                </label>
                <MonoField value={draft.chatServer} copyable={false} />
              </div>
              <div style={{ width: 110 }}>
                <label style={labelStyle}>Port (TLS)</label>
                <MonoField value={String(draft.chatPort)} copyable={false} />
              </div>
            </div>
            <div>
              <label style={labelStyle}>
                Default channel <Locked />
              </label>
              <MonoField value={draft.chatChannel} copyable={false} />
            </div>
          </div>
        </GlassCard>

        <GlassCard>
          <h3 style={{ margin: "0 0 6px", fontSize: 14 }}>Chat (Sonar)</h3>
          <p style={{ margin: "0 0 16px", fontSize: 11.5, color: "var(--text-tertiary)", lineHeight: 1.55 }}>
            Sonar connects to a public IRC network, so there's no extra account and nothing to
            host. You chat under your Minecraft username. Nicks are first-come rather than
            reserved, so someone else may already be using yours — the launcher adds a suffix if
            so.
          </p>
          <Toggle
            checked={draft.chatAutoConnect}
            onChange={(v) => set("chatAutoConnect", v)}
            title="Connect automatically"
            hint="Join chat as soon as the launcher opens."
          />
        </GlassCard>

        <GlassCard>
          <h3 style={{ margin: "0 0 6px", fontSize: 14 }}>Ambience</h3>
          <p style={{ margin: "0 0 16px", fontSize: 11.5, color: "var(--text-tertiary)", lineHeight: 1.55 }}>
            Purely cosmetic. Sea life is background scenery and is on by default;
            the cursor school is a toy and is off until you ask for it.
          </p>
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            <Toggle
              checked={draft.seaLifeEnabled}
              onChange={(v) => set("seaLifeEnabled", v)}
              title="Sea life"
              hint="Shoals, jellyfish and the occasional shark drift past in the water behind the panels."
            />
            <Toggle
              checked={draft.fishEnabled}
              onChange={(v) => set("fishEnabled", v)}
              title="School of fish (follows your cursor)"
              hint="A few fish chase your cursor and circle it when you hold still. Applies as soon as you save."
            />
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

/** Marks a field the backend will refuse to change. */
function Locked() {
  return (
    <span
      title="Fixed by the launcher — the backend ignores changes to this."
      style={{
        marginLeft: 6,
        fontSize: 9,
        letterSpacing: "0.08em",
        padding: "1px 5px",
        border: "1px solid var(--glass-border)",
        color: "var(--text-tertiary)",
        verticalAlign: "middle",
      }}
    >
      LOCKED
    </span>
  );
}

/** A labelled checkbox row. The whole card is the hit target, not just the box. */
function Toggle({
  checked,
  onChange,
  title,
  hint,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  title: string;
  hint: string;
}) {
  return (
    <label
      style={{
        display: "flex",
        gap: 10,
        alignItems: "flex-start",
        padding: 10,
        borderRadius: "var(--radius-sm)",
        border: `1px solid ${checked ? "var(--accent)" : "var(--glass-border)"}`,
        background: checked ? "var(--glass-bg-elevated)" : "transparent",
        cursor: "pointer",
      }}
    >
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        style={{ marginTop: 2 }}
      />
      <span>
        <div style={{ fontSize: 13, fontWeight: 600 }}>{title}</div>
        <div style={{ fontSize: 11, color: "var(--text-tertiary)", lineHeight: 1.5 }}>{hint}</div>
      </span>
    </label>
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
  backgroundColor: "rgba(0,20,30,0.3)",
  color: "var(--text-primary)",
  fontSize: 13,
};
