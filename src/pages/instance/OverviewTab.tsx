import { useEffect, useState } from "react";
import type { TabProps } from "./InstanceDetail";
import type { Instance } from "../../types/instance";
import { GlassCard } from "../../components/GlassCard";
import { listInstances } from "../../lib/tauri";
import { formatDuration, formatRelativeDate, formatDate } from "../../lib/format";

// Overview is the surface-layer face of the instance: glossy stat tiles and a
// playtime breakdown. The chart (Phase 7 / spec §9) is real, not decorative —
// it plots this instance's total playtime against every other instance so the
// "per-instance vs all-time" comparison the spec asks for is visible at a
// glance, with the current instance highlighted in the accent color.

function StatTile({ label, value }: { label: string; value: string }) {
  return (
    <GlassCard style={{ padding: 16 }}>
      <div style={{ fontSize: 11, color: "var(--text-tertiary)", textTransform: "uppercase", letterSpacing: 0.5 }}>
        {label}
      </div>
      <div style={{ fontSize: 22, fontWeight: 600, marginTop: 6 }}>{value}</div>
    </GlassCard>
  );
}

export function OverviewTab({ instance }: TabProps) {
  const [all, setAll] = useState<Instance[]>([]);

  useEffect(() => {
    listInstances().then(setAll).catch(() => setAll([]));
  }, []);

  const enabledMods = instance.mods.filter((m) => m.enabled).length;
  const allTime = all.reduce((sum, i) => sum + i.totalPlaytimeSeconds, 0);

  // Bars: every instance with any playtime, plus the current one even at 0,
  // sorted desc so the chart reads as a ranking.
  const withCurrent = all.some((i) => i.id === instance.id) ? all : [...all, instance];
  const bars = withCurrent
    .filter((i) => i.totalPlaytimeSeconds > 0 || i.id === instance.id)
    .sort((a, b) => b.totalPlaytimeSeconds - a.totalPlaytimeSeconds);
  const max = Math.max(1, ...bars.map((i) => i.totalPlaytimeSeconds));

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 24 }}>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 12 }}>
        <StatTile label="Playtime" value={formatDuration(instance.totalPlaytimeSeconds)} />
        <StatTile label="Last played" value={formatRelativeDate(instance.lastPlayed)} />
        <StatTile label="Mods" value={`${enabledMods}${instance.mods.length !== enabledMods ? ` / ${instance.mods.length}` : ""}`} />
        <StatTile label="All-time (all instances)" value={formatDuration(allTime)} />
      </div>

      <GlassCard>
        <h3 style={{ margin: "0 0 4px", fontSize: 14 }}>Playtime by instance</h3>
        <div style={{ fontSize: 12, color: "var(--text-secondary)", marginBottom: 16 }}>
          This instance highlighted; others shown for comparison.
        </div>
        {bars.length === 0 ? (
          <div style={{ color: "var(--text-tertiary)", fontSize: 13 }}>No playtime recorded yet.</div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            {bars.map((i) => {
              const isCurrent = i.id === instance.id;
              return (
                <div key={i.id} style={{ display: "flex", alignItems: "center", gap: 12 }}>
                  <div
                    style={{
                      width: 120,
                      fontSize: 12,
                      color: isCurrent ? "var(--text-primary)" : "var(--text-secondary)",
                      fontWeight: isCurrent ? 600 : 400,
                      whiteSpace: "nowrap",
                      overflow: "hidden",
                      textOverflow: "ellipsis",
                    }}
                    title={i.name}
                  >
                    {i.name}
                  </div>
                  <div style={{ flex: 1, background: "rgba(0,0,0,0.2)", borderRadius: 4, height: 18, overflow: "hidden" }}>
                    <div
                      style={{
                        width: `${(i.totalPlaytimeSeconds / max) * 100}%`,
                        minWidth: i.totalPlaytimeSeconds > 0 ? 2 : 0,
                        height: "100%",
                        background: isCurrent ? "var(--accent)" : "var(--text-tertiary)",
                        borderRadius: 4,
                        transition: "width 300ms ease",
                      }}
                    />
                  </div>
                  <div style={{ width: 64, textAlign: "right", fontSize: 12, fontFamily: "var(--font-mono)", color: "var(--text-secondary)" }}>
                    {formatDuration(i.totalPlaytimeSeconds)}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </GlassCard>

      <GlassCard>
        <h3 style={{ margin: "0 0 12px", fontSize: 14 }}>Details</h3>
        <dl style={{ margin: 0, display: "grid", gridTemplateColumns: "auto 1fr", gap: "8px 16px", fontSize: 13 }}>
          <dt style={{ color: "var(--text-secondary)" }}>Minecraft version</dt>
          <dd style={{ margin: 0, fontFamily: "var(--font-mono)" }}>{instance.mcVersion}</dd>
          <dt style={{ color: "var(--text-secondary)" }}>Loader</dt>
          <dd style={{ margin: 0 }}>{instance.loader}{instance.loaderVersion ? ` ${instance.loaderVersion}` : ""}</dd>
          <dt style={{ color: "var(--text-secondary)" }}>Window size</dt>
          <dd style={{ margin: 0, fontFamily: "var(--font-mono)" }}>{instance.windowWidth} × {instance.windowHeight}</dd>
          <dt style={{ color: "var(--text-secondary)" }}>Created</dt>
          <dd style={{ margin: 0 }}>{formatDate(instance.createdAt)}</dd>
        </dl>
      </GlassCard>
    </div>
  );
}
