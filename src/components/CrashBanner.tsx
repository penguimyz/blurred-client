import { useEffect, useState } from "react";
import { Icon } from "./Icon";
import * as api from "../lib/tauri";
import type { CrashReport } from "../types/crash";

/**
 * Surfaces the most recent crash on the launchpad, plus a detail view holding
 * the diagnosis and the tail of the log.
 *
 * Two sources feed this: a listener for `instance-crashed` (so a crash appears
 * the instant it happens, even mid-session) and a read of the saved reports on
 * mount (so a crash that happened before the launcher was restarted isn't
 * lost). The saved copy is the important half — the frontend's log buffer is
 * memory-only, so before crash reports existed, restarting the launcher threw
 * away the one thing you needed to read.
 *
 * Dismissing is per-session and does not delete the report; the Logs page keeps
 * the full list.
 */
export function CrashBanner({ onOpenInstance }: { onOpenInstance: (id: string) => void }) {
  const [report, setReport] = useState<CrashReport | null>(null);
  const [dismissed, setDismissed] = useState(false);
  const [expanded, setExpanded] = useState(false);

  useEffect(() => {
    let unlisten: (() => void) | undefined;
    let cancelled = false;

    api
      .listCrashReports()
      .then((reports) => {
        if (cancelled || reports.length === 0) return;
        // Only nag about something recent. An old crash you've already dealt
        // with shouldn't greet you every launch — it stays on the Logs page.
        const newest = reports[0];
        const ageMs = Date.now() - new Date(newest.occurredAt).getTime();
        if (ageMs < 24 * 60 * 60 * 1000) setReport(newest);
      })
      .catch(() => {});

    api
      .onInstanceCrashed((r) => {
        setReport(r);
        setDismissed(false);
        setExpanded(false);
      })
      .then((fn) => {
        if (cancelled) fn();
        else unlisten = fn;
      });

    return () => {
      cancelled = true;
      unlisten?.();
    };
  }, []);

  if (!report || dismissed) return null;

  return (
    <div
      className="glass-card"
      style={{
        marginBottom: 18,
        padding: 0,
        borderColor: "rgba(255,107,129,0.35)",
        overflow: "hidden",
      }}
    >
      <div style={{ display: "flex", alignItems: "flex-start", gap: 12, padding: "14px 16px" }}>
        <Icon name="anchor" size={20} style={{ color: "var(--danger)", marginTop: 2 }} />

        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 2 }}>
            {report.instanceName} crashed
          </div>
          <div style={{ fontSize: 12.5, color: "var(--text-secondary)", lineHeight: 1.5 }}>
            {report.summary}
          </div>
          <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginTop: 5 }}>
            {new Date(report.occurredAt).toLocaleString()} · exit code {report.exitCode} ·{" "}
            {report.mcVersion} {report.loader}
          </div>

          <div style={{ display: "flex", gap: 8, marginTop: 11, flexWrap: "wrap" }}>
            <button
              onClick={() => setExpanded((v) => !v)}
              style={{ fontSize: 12, padding: "5px 11px" }}
            >
              {expanded ? "Hide log" : "Show log"}
            </button>
            <button
              onClick={() => navigator.clipboard.writeText(crashToText(report)).catch(() => {})}
              style={{ fontSize: 12, padding: "5px 11px" }}
              title="Copy the diagnosis and log tail, ready to paste when asking for help"
            >
              Copy report
            </button>
            <button
              onClick={() => api.revealPath(report.logPath).catch(() => {})}
              style={{ fontSize: 12, padding: "5px 11px" }}
              title={report.logPath}
            >
              Open log file
            </button>
            <button
              onClick={() => onOpenInstance(report.instanceId)}
              style={{ fontSize: 12, padding: "5px 11px" }}
            >
              Open instance
            </button>
          </div>
        </div>

        <button
          className="bare"
          onClick={() => setDismissed(true)}
          title="Dismiss"
          aria-label="Dismiss"
          style={{
            background: "transparent",
            border: "none",
            color: "var(--text-tertiary)",
            cursor: "pointer",
            padding: 4,
            display: "flex",
          }}
        >
          <Icon name="close" size={15} />
        </button>
      </div>

      {expanded && (
        <pre
          style={{
            margin: 0,
            padding: "12px 16px",
            borderTop: "1px solid var(--glass-border)",
            background: "rgba(0,12,20,0.4)",
            fontFamily: "var(--font-mono)",
            fontSize: 11.5,
            lineHeight: 1.5,
            color: "var(--text-secondary)",
            maxHeight: 300,
            overflow: "auto",
            whiteSpace: "pre-wrap",
            wordBreak: "break-word",
          }}
        >
          {report.tail.join("\n") || "(no output captured)"}
        </pre>
      )}
    </div>
  );
}

/** Plain-text crash report, shaped for pasting into a help thread. */
export function crashToText(r: CrashReport): string {
  return [
    `Blurred Client crash report`,
    `Instance: ${r.instanceName} (${r.mcVersion}, ${r.loader})`,
    `When: ${new Date(r.occurredAt).toLocaleString()}`,
    `Exit code: ${r.exitCode}`,
    `Diagnosis: ${r.summary}`,
    ``,
    `--- last ${r.tail.length} lines ---`,
    r.tail.join("\n"),
  ].join("\n");
}
