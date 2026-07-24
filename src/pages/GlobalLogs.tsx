import { GlassCard } from "../components/GlassCard";

// Launcher-level log tab (spec §4.1). The current backend streams per-instance
// game logs (see each instance's Logs tab) but doesn't yet capture a separate
// launcher-level log stream, so this stays a signpost rather than a fake empty
// console. When launcher self-diagnostics land they render here.
export function GlobalLogs() {
  return (
    <div style={{ padding: 32 }}>
      <h1 style={{ margin: "0 0 4px", fontSize: 22, fontWeight: 600 }}>Logs</h1>
      <div style={{ fontSize: 12, color: "var(--text-secondary)", marginBottom: 24 }}>
        Launcher-level log. Per-instance game logs live in each instance's Logs tab.
      </div>
      <GlassCard style={{ maxWidth: 640 }}>
        <p style={{ color: "var(--text-secondary)", fontSize: 13, margin: 0 }}>
          Nothing to report. Open an instance and hit Play to watch its live game log stream in
          the instance's <strong>Logs</strong> tab.
        </p>
      </GlassCard>
    </div>
  );
}
