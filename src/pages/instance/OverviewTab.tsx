import { useEffect, useState } from "react";
import type { TabProps } from "./InstanceDetail";
import type { Instance } from "../../types/instance";
import { GlassCard } from "../../components/GlassCard";
import { installJavaRuntime, listInstances, planJava, type JavaPlan } from "../../lib/tauri";
import { formatDuration, formatRelativeDate, formatDate } from "../../lib/format";

// Overview is the surface-layer face of the instance: glossy stat tiles and a
// playtime breakdown. The chart (Phase 7 / spec §9) is real, not decorative —
// it plots this instance's total playtime against every other instance so the
// "per-instance vs all-time" comparison the spec asks for is visible at a
// glance, with the current instance highlighted in the accent color.

/**
 * What this instance will launch with, and where that Java is coming from.
 *
 * Worth stating plainly rather than leaving to be discovered at launch: "Java
 * 21 will be downloaded on first launch" answers the question before it's
 * asked, and the pre-install button means the download can happen now instead
 * of in the two minutes between pressing Play and the game appearing.
 */
function JavaCard({ mcVersion }: { mcVersion: string }) {
  const [plan, setPlan] = useState<JavaPlan | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = () => {
    planJava(mcVersion).then(setPlan).catch(() => setPlan(null));
  };
  useEffect(refresh, [mcVersion]);

  if (!plan) return null;

  const tone =
    plan.source === "unsupported"
      ? "var(--danger)"
      : plan.source === "download"
        ? "var(--warning)"
        : "var(--success)";

  return (
    <GlassCard>
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 6 }}>
        <h3 style={{ margin: 0, fontSize: 14, flex: 1 }}>Java {plan.requiredMajor}</h3>
        <span style={{ fontSize: 10.5, color: tone, letterSpacing: "0.05em" }}>
          {plan.source.toUpperCase()}
        </span>
      </div>

      <div style={{ fontSize: 12.5, color: "var(--text-secondary)", lineHeight: 1.6 }}>
        {plan.detail}
      </div>

      {plan.path && (
        <div
          style={{
            fontSize: 11,
            fontFamily: "var(--font-mono)",
            color: "var(--text-tertiary)",
            marginTop: 6,
            overflow: "hidden",
            textOverflow: "ellipsis",
          }}
          title={plan.path}
        >
          {plan.path}
        </div>
      )}

      {plan.source === "download" && (
        <button
          onClick={async () => {
            setBusy(true);
            setError(null);
            try {
              await installJavaRuntime(plan.requiredMajor);
              refresh();
            } catch (e) {
              setError(String(e));
            } finally {
              setBusy(false);
            }
          }}
          disabled={busy}
          style={{ marginTop: 12, fontSize: 12 }}
        >
          {busy ? "Downloading…" : "Download it now"}
        </button>
      )}

      {error && (
        <div style={{ color: "var(--danger)", fontSize: 12, marginTop: 10 }}>{error}</div>
      )}
    </GlassCard>
  );
}

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

      <JavaCard mcVersion={instance.mcVersion} />

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
                  <div style={{ flex: 1, background: "rgba(0,0,0,0.2)", borderRadius: 0, height: 18, overflow: "hidden" }}>
                    <div
                      style={{
                        width: `${(i.totalPlaytimeSeconds / max) * 100}%`,
                        minWidth: i.totalPlaytimeSeconds > 0 ? 2 : 0,
                        height: "100%",
                        background: isCurrent ? "var(--accent)" : "var(--text-tertiary)",
                        borderRadius: 0,
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
