import { useEffect, useState } from "react";
import type { TabProps } from "./InstanceDetail";
import type { Instance, Loader } from "../../types/instance";
import { useAccountStore } from "../../store/accountStore";
import { GlassCard } from "../../components/GlassCard";
import {
  CustomCommandsForm,
  EnvVarsForm,
  JavaForm,
  OverrideSection,
} from "../../components/OverrideSettingsForm";
import { updateInstance } from "../../lib/tauri";

// Per-instance Settings tab (spec §7). Version/loader/window on top, then the
// three Prism-parity override sections — each reuses the exact same field form
// the global Settings screen uses, wrapped in OverrideSection to add the
// inherit-vs-override toggle. Save persists the whole Instance via update_instance.

const LOADERS: Loader[] = ["vanilla", "fabric", "forge", "quilt", "neoforge"];

export function InstanceSettingsTab({ instance, setInstance }: TabProps) {
  const { accounts, refresh: refreshAccounts } = useAccountStore();
  const [draft, setDraft] = useState<Instance>(instance);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    refreshAccounts();
  }, [refreshAccounts]);

  const dirty = JSON.stringify(draft) !== JSON.stringify(instance);
  const set = <K extends keyof Instance>(k: K, v: Instance[K]) => setDraft({ ...draft, [k]: v });

  async function save() {
    setSaving(true);
    setError(null);
    try {
      const updated = await updateInstance(draft);
      setInstance(updated);
      setDraft(updated);
      setSaved(true);
      setTimeout(() => setSaved(false), 1500);
    } catch (e) {
      setError(String(e));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16, maxWidth: 720 }}>
      <GlassCard>
        <h3 style={{ margin: "0 0 16px", fontSize: 14 }}>Instance</h3>
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          <div>
            <label style={labelStyle}>Name</label>
            <input value={draft.name} onChange={(e) => set("name", e.target.value)} style={inputStyle} />
          </div>
          <div style={{ display: "flex", gap: 14 }}>
            <div style={{ flex: 1 }}>
              <label style={labelStyle}>Minecraft version</label>
              <input value={draft.mcVersion} onChange={(e) => set("mcVersion", e.target.value)} style={inputStyle} />
            </div>
            <div style={{ flex: 1 }}>
              <label style={labelStyle}>Loader</label>
              <select value={draft.loader} onChange={(e) => set("loader", e.target.value as Loader)} style={inputStyle}>
                {LOADERS.map((l) => (
                  <option key={l} value={l}>
                    {l}
                  </option>
                ))}
              </select>
            </div>
            <div style={{ flex: 1 }}>
              <label style={labelStyle}>Loader version</label>
              <input
                value={draft.loaderVersion ?? ""}
                onChange={(e) => set("loaderVersion", e.target.value.trim() === "" ? null : e.target.value)}
                placeholder="(latest)"
                style={inputStyle}
              />
            </div>
          </div>
          <div style={{ display: "flex", gap: 14 }}>
            <div style={{ flex: 1 }}>
              <label style={labelStyle}>Window width</label>
              <input type="number" value={draft.windowWidth} onChange={(e) => set("windowWidth", Number(e.target.value))} style={inputStyle} />
            </div>
            <div style={{ flex: 1 }}>
              <label style={labelStyle}>Window height</label>
              <input type="number" value={draft.windowHeight} onChange={(e) => set("windowHeight", Number(e.target.value))} style={inputStyle} />
            </div>
          </div>
          <div>
            <label style={labelStyle}>Launch as account</label>
            <select
              value={draft.accountId ?? ""}
              onChange={(e) => set("accountId", e.target.value === "" ? null : e.target.value)}
              style={inputStyle}
            >
              <option value="">Default (active account)</option>
              {accounts.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.username} ({a.accountType})
                </option>
              ))}
            </select>
          </div>
        </div>
      </GlassCard>

      <GlassCard>
        <OverrideSection
          title="Java"
          override={draft.javaOverride}
          onChange={(o) => set("javaOverride", o)}
        >
          {(value, disabled, setValue) => <JavaForm value={value} onChange={setValue} disabled={disabled} />}
        </OverrideSection>

        <OverrideSection
          title="Environment variables"
          override={draft.envVarsOverride}
          onChange={(o) => set("envVarsOverride", o)}
        >
          {(value, disabled, setValue) => <EnvVarsForm value={value} onChange={setValue} disabled={disabled} />}
        </OverrideSection>

        <OverrideSection
          title="Custom commands"
          override={draft.customCommandsOverride}
          onChange={(o) => set("customCommandsOverride", o)}
        >
          {(value, disabled, setValue) => (
            <CustomCommandsForm value={value} onChange={setValue} disabled={disabled} />
          )}
        </OverrideSection>
      </GlassCard>

      {error && <div style={{ color: "var(--danger)", fontSize: 13 }}>{error}</div>}

      <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
        <button className="accent" onClick={save} disabled={!dirty || saving} style={{ opacity: dirty && !saving ? 1 : 0.5 }}>
          {saving ? "Saving…" : "Save changes"}
        </button>
        {dirty && (
          <button onClick={() => setDraft(instance)} style={ghostBtn}>
            Reset
          </button>
        )}
        {saved && <span style={{ fontSize: 12, color: "var(--success)" }}>Saved</span>}
      </div>
    </div>
  );
}

const labelStyle = { fontSize: 12, color: "var(--text-secondary)", display: "block" as const, marginBottom: 4 };
const inputStyle = {
  width: "100%",
  padding: "8px 10px",
  borderRadius: "var(--radius-sm)",
  border: "1px solid var(--glass-border)",
  background: "rgba(0,0,0,0.2)",
  color: "var(--text-primary)",
  fontSize: 13,
};
const ghostBtn = {
  background: "transparent",
  border: "1px solid var(--glass-border)",
  borderRadius: "var(--radius-sm)",
  color: "var(--text-secondary)",
  padding: "8px 16px",
  fontSize: 13,
  cursor: "pointer",
};
