import { useEffect, useState } from "react";
import type { Instance } from "../../types/instance";
import { getInstance } from "../../lib/tauri";
import { useInstanceStore } from "../../store/instanceStore";
import { OverviewTab } from "./OverviewTab";
import { ModsTab } from "./ModsTab";
import { ConfigsTab } from "./ConfigsTab";
import { WorldsTab } from "./WorldsTab";
import { ScreenshotsTab } from "./ScreenshotsTab";
import { InstanceSettingsTab } from "./InstanceSettingsTab";
import { LogsTab } from "./LogsTab";
import { NotesTab } from "./NotesTab";

// The per-instance detail view (spec §4.2): a secondary tab bar layered over
// the instance, visually distinct from the global sidebar so the two-tier nav
// reads clearly. This is the boundary between the Lunar surface layer (Overview
// is glossy) and the Prism depth layer (Mods/Configs/Settings/Logs go dense and
// technical) — same glass tokens throughout, shifting density, per spec §2.1.

export interface TabProps {
  instance: Instance;
  setInstance: (i: Instance) => void;
}

const TABS = [
  { key: "overview", label: "Overview" },
  { key: "mods", label: "Mods" },
  { key: "configs", label: "Configs" },
  { key: "worlds", label: "Worlds" },
  { key: "screenshots", label: "Screenshots" },
  { key: "settings", label: "Settings" },
  { key: "logs", label: "Logs" },
  { key: "notes", label: "Notes" },
];

export function InstanceDetail({ instanceId, onBack }: { instanceId: string; onBack: () => void }) {
  const [instance, setInstance] = useState<Instance | null>(null);
  const [tab, setTab] = useState("overview");
  const [error, setError] = useState<string | null>(null);
  const { running, launch, quit, refreshRunning } = useInstanceStore();
  const isRunning = !!running[instanceId];

  useEffect(() => {
    getInstance(instanceId).then(setInstance).catch((e) => setError(String(e)));
    refreshRunning();
  }, [instanceId, refreshRunning]);

  if (error) {
    return (
      <div style={{ padding: 32 }}>
        <button onClick={onBack} style={backBtn}>
          ← Back
        </button>
        <p style={{ color: "var(--danger)", marginTop: 16 }}>{error}</p>
      </div>
    );
  }

  if (!instance) {
    return <div style={{ padding: 32, color: "var(--text-secondary)" }}>Loading…</div>;
  }

  return (
    <div style={{ height: "100%", display: "flex", flexDirection: "column" }}>
      {/* Header: back, identity, quick-play. Stays glossy (surface layer). */}
      <div style={{ padding: "20px 32px 0", flexShrink: 0 }}>
        <button onClick={onBack} style={backBtn}>
          ← All instances
        </button>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginTop: 12 }}>
          <div>
            <h1 style={{ margin: 0, fontSize: 22, fontWeight: 600 }}>{instance.name}</h1>
            <div style={{ fontSize: 13, color: "var(--text-secondary)", marginTop: 4 }}>
              {instance.mcVersion} · {instance.loader}
            </div>
          </div>
          {isRunning ? (
            <button
              style={{ padding: "10px 28px", fontSize: 14, background: "var(--danger)", color: "#fff", border: "none", borderRadius: "var(--radius-sm)", fontWeight: 600, cursor: "pointer" }}
              onClick={() => quit(instance.id).catch((e) => setError(String(e)))}
            >
              ■ Quit
            </button>
          ) : (
            <button
              className="accent"
              style={{ padding: "10px 28px", fontSize: 14 }}
              onClick={() => launch(instance.id).catch((e) => setError(String(e)))}
            >
              ▶ Play
            </button>
          )}
        </div>

        {/* Secondary tab bar */}
        <div style={{ display: "flex", gap: 4, marginTop: 20, borderBottom: "1px solid var(--glass-border)", overflowX: "auto" }}>
          {TABS.map((t) => (
            <button
              key={t.key}
              onClick={() => setTab(t.key)}
              style={{
                background: "transparent",
                border: "none",
                borderBottom: tab === t.key ? "2px solid var(--accent)" : "2px solid transparent",
                color: tab === t.key ? "var(--text-primary)" : "var(--text-secondary)",
                padding: "8px 14px",
                fontSize: 13,
                fontWeight: tab === t.key ? 600 : 400,
                cursor: "pointer",
                whiteSpace: "nowrap",
              }}
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>

      {/* Tab body */}
      <div style={{ flex: 1, overflowY: "auto", padding: "24px 32px" }}>
        {tab === "overview" && <OverviewTab instance={instance} setInstance={setInstance} />}
        {tab === "mods" && <ModsTab instance={instance} setInstance={setInstance} />}
        {tab === "configs" && <ConfigsTab instance={instance} setInstance={setInstance} />}
        {tab === "worlds" && <WorldsTab instance={instance} setInstance={setInstance} />}
        {tab === "screenshots" && <ScreenshotsTab instance={instance} setInstance={setInstance} />}
        {tab === "settings" && <InstanceSettingsTab instance={instance} setInstance={setInstance} />}
        {tab === "logs" && <LogsTab instance={instance} setInstance={setInstance} />}
        {tab === "notes" && <NotesTab instance={instance} setInstance={setInstance} />}
      </div>
    </div>
  );
}

const backBtn = {
  background: "transparent",
  border: "none",
  color: "var(--text-secondary)",
  fontSize: 13,
  cursor: "pointer",
  padding: 0,
};
