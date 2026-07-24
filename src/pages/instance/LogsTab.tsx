import { useEffect, useRef, useState } from "react";
import type { UnlistenFn } from "@tauri-apps/api/event";
import type { TabProps } from "./InstanceDetail";
import { onInstanceLog } from "../../lib/tauri";

// Live log viewer (Phase 7 / spec §6). Hangs off the existing `instance-log`
// event pipe — the same stream playtime tracking uses — so it just renders,
// it doesn't re-plumb anything. Colorized levels, a search filter, and copy-to-
// clipboard. Lines only arrive while the instance is running; before a launch
// this is empty by design (historical on-disk log browsing would be a separate
// read of logs/latest.log).

interface LogLine {
  n: number;
  stream: "stdout" | "stderr";
  line: string;
}

function levelColor(line: string, stream: string): string {
  if (/\bERROR\b|\bFATAL\b|Exception/.test(line) || stream === "stderr") return "var(--danger)";
  if (/\bWARN(ING)?\b/.test(line)) return "var(--warning)";
  if (/\bDEBUG\b|\bTRACE\b/.test(line)) return "var(--text-tertiary)";
  return "var(--text-secondary)";
}

export function LogsTab({ instance }: TabProps) {
  const [lines, setLines] = useState<LogLine[]>([]);
  const [filter, setFilter] = useState("");
  const [autoScroll, setAutoScroll] = useState(true);
  const counter = useRef(0);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let unlisten: UnlistenFn | undefined;
    onInstanceLog(instance.id, (line, stream) => {
      counter.current += 1;
      const entry = { n: counter.current, stream, line };
      // Cap the buffer so a long session doesn't grow the DOM unbounded.
      setLines((prev) => (prev.length > 5000 ? [...prev.slice(-4000), entry] : [...prev, entry]));
    }).then((fn) => {
      unlisten = fn;
    });
    return () => {
      unlisten?.();
    };
  }, [instance.id]);

  const shown = filter ? lines.filter((l) => l.line.toLowerCase().includes(filter.toLowerCase())) : lines;

  useEffect(() => {
    if (autoScroll) bottomRef.current?.scrollIntoView({ block: "end" });
  }, [shown.length, autoScroll]);

  async function copyAll() {
    try {
      await navigator.clipboard.writeText(shown.map((l) => l.line).join("\n"));
    } catch {
      /* clipboard blocked */
    }
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12, height: "100%", minHeight: 400 }}>
      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
        <input
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          placeholder="Filter log…"
          style={{
            flex: 1,
            minWidth: 160,
            padding: "6px 10px",
            borderRadius: "var(--radius-sm)",
            border: "1px solid var(--glass-border)",
            background: "rgba(0,0,0,0.2)",
            color: "var(--text-primary)",
            fontSize: 13,
          }}
        />
        <label style={{ fontSize: 12, color: "var(--text-secondary)", display: "flex", gap: 6, alignItems: "center" }}>
          <input type="checkbox" checked={autoScroll} onChange={(e) => setAutoScroll(e.target.checked)} />
          Auto-scroll
        </label>
        <button onClick={copyAll} style={ghostBtn}>
          Copy
        </button>
        <button onClick={() => setLines([])} style={ghostBtn}>
          Clear
        </button>
      </div>

      <div
        className="mono-field"
        style={{
          flex: 1,
          overflowY: "auto",
          padding: 12,
          fontSize: 12,
          lineHeight: 1.5,
          whiteSpace: "pre-wrap",
          wordBreak: "break-word",
        }}
      >
        {shown.length === 0 ? (
          <span style={{ color: "var(--text-tertiary)" }}>
            {lines.length === 0 ? "No log output yet. Hit Play to stream the game's console here." : "No lines match the filter."}
          </span>
        ) : (
          shown.map((l) => (
            <div key={l.n} style={{ color: levelColor(l.line, l.stream) }}>
              {l.line}
            </div>
          ))
        )}
        <div ref={bottomRef} />
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
