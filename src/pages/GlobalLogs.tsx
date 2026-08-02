import { useCallback, useEffect, useState } from "react";
import { GlassCard } from "../components/GlassCard";
import { Icon } from "../components/Icon";
import { crashToText } from "../components/CrashBanner";
import { PageHeader } from "../components/PageHeader";
import * as api from "../lib/tauri";
import type { CrashReport } from "../types/crash";

/**
 * Crash history (spec §4.1's launcher-level log tab).
 *
 * This used to be a signpost because there was nothing launcher-level to show.
 * Now that crashed sessions are saved to disk, this is where they live: every
 * crash, its diagnosis, the tail inline, and the full session log on demand.
 * Per-instance *live* logs still stream in each instance's own Logs tab — this
 * page is the after-the-fact record.
 */
export function GlobalLogs({ onOpenInstance }: { onOpenInstance: (id: string) => void }) {
  const [reports, setReports] = useState<CrashReport[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [fullLog, setFullLog] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setReports(await api.listCrashReports());
    } catch {
      setReports([]);
    }
    setLoading(false);
  }, []);

  useEffect(() => {
    load();
    // Refresh live, so a crash while this page is open appears without a
    // manual reload.
    let unlisten: (() => void) | undefined;
    let cancelled = false;
    api.onInstanceCrashed(() => load()).then((fn) => {
      if (cancelled) fn();
      else unlisten = fn;
    });
    return () => {
      cancelled = true;
      unlisten?.();
    };
  }, [load]);

  async function toggle(report: CrashReport) {
    if (expandedId === report.id) {
      setExpandedId(null);
      setFullLog(null);
      return;
    }
    setExpandedId(report.id);
    setFullLog(null);
  }

  async function loadFullLog(report: CrashReport) {
    try {
      setFullLog(await api.readSessionLog(report.logPath));
    } catch (e) {
      setFullLog(String(e));
    }
  }

  return (
    <div style={{ padding: 28, height: "100%", overflowY: "auto" }}>
      <PageHeader
        page="logs"
        actions={
          reports.length > 0 ? (
            <button
              onClick={async () => {
                if (confirm("Clear all saved crash reports? The log files on disk are kept.")) {
                  await api.clearCrashReports().catch(() => {});
                  load();
                }
              }}
            >
              Clear all
            </button>
          ) : undefined
        }
      />

      {loading && <p style={{ color: "var(--text-secondary)", fontSize: 13 }}>Loading…</p>}

      {!loading && reports.length === 0 && (
        <GlassCard style={{ maxWidth: 620, textAlign: "center", padding: 40 }}>
          <Icon name="check" size={28} style={{ color: "var(--success)", marginBottom: 8 }} />
          <p style={{ color: "var(--text-secondary)", fontSize: 13, margin: 0 }}>
            No crashes recorded. Smooth sailing.
          </p>
        </GlassCard>
      )}

      <div style={{ display: "flex", flexDirection: "column", gap: 12, maxWidth: 860 }}>
        {reports.map((r) => {
          const open = expandedId === r.id;
          return (
            <GlassCard key={r.id} style={{ padding: 0, overflow: "hidden" }}>
              <div style={{ display: "flex", alignItems: "flex-start", gap: 12, padding: 16 }}>
                <Icon name="anchor" size={18} style={{ color: "var(--danger)", marginTop: 2 }} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontWeight: 600, fontSize: 14 }}>{r.instanceName}</div>
                  <div
                    style={{
                      fontSize: 12.5,
                      color: "var(--text-secondary)",
                      lineHeight: 1.5,
                      marginTop: 2,
                    }}
                  >
                    {r.summary}
                  </div>
                  <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginTop: 5 }}>
                    {new Date(r.occurredAt).toLocaleString()} · exit code {r.exitCode} ·{" "}
                    {r.mcVersion} {r.loader}
                  </div>

                  <div style={{ display: "flex", gap: 8, marginTop: 11, flexWrap: "wrap" }}>
                    <button onClick={() => toggle(r)} style={smallBtn}>
                      {open ? "Hide" : "Details"}
                    </button>
                    <button
                      onClick={() => navigator.clipboard.writeText(crashToText(r)).catch(() => {})}
                      style={smallBtn}
                    >
                      Copy report
                    </button>
                    <button
                      onClick={() => api.revealPath(r.logPath).catch(() => {})}
                      style={smallBtn}
                      title={r.logPath}
                    >
                      Open log file
                    </button>
                    <button onClick={() => onOpenInstance(r.instanceId)} style={smallBtn}>
                      Open instance
                    </button>
                    <button
                      onClick={async () => {
                        await api.deleteCrashReport(r.id).catch(() => {});
                        load();
                      }}
                      style={{ ...smallBtn, color: "var(--danger)" }}
                    >
                      Delete
                    </button>
                  </div>
                </div>
              </div>

              {open && (
                <div style={{ borderTop: "1px solid var(--glass-border)" }}>
                  <div
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                      padding: "8px 16px",
                      fontSize: 11,
                      color: "var(--text-tertiary)",
                    }}
                  >
                    <span>{fullLog ? "Full session log" : `Last ${r.tail.length} lines`}</span>
                    {!fullLog && (
                      <button onClick={() => loadFullLog(r)} style={smallBtn}>
                        Load full log
                      </button>
                    )}
                  </div>
                  <pre style={logStyle}>
                    {fullLog ?? (r.tail.join("\n") || "(no output captured)")}
                  </pre>
                </div>
              )}
            </GlassCard>
          );
        })}
      </div>
    </div>
  );
}

const smallBtn = { fontSize: 12, padding: "5px 11px" } as const;

const logStyle = {
  margin: 0,
  padding: "12px 16px",
  background: "rgba(0,12,20,0.4)",
  fontFamily: "var(--font-mono)",
  fontSize: 11.5,
  lineHeight: 1.5,
  color: "var(--text-secondary)",
  maxHeight: 380,
  overflow: "auto",
  whiteSpace: "pre-wrap",
  wordBreak: "break-word",
} as const;
