import { useEffect, useState } from "react";
import { onJavaProgress, type JavaProgress } from "../lib/tauri";

/**
 * A strip that appears while a Java runtime is downloading.
 *
 * Blurred fetches the JRE the game needs on first launch, which is the right
 * behaviour — nobody should have to install Java 21 by hand before they can
 * play — but it takes a minute or two and happens in the gap between pressing
 * Play and the game window appearing. Without this, that gap looks like the
 * launcher has hung.
 *
 * Mounted app-wide rather than on the Launchpad, because a server start on the
 * Servers tab can trigger the same download.
 */
export function JavaBanner() {
  const [progress, setProgress] = useState<JavaProgress | null>(null);

  useEffect(() => {
    let unlisten: (() => void) | undefined;
    let cancelled = false;
    let hideTimer: number | undefined;

    onJavaProgress((p) => {
      setProgress(p);
      // Clear once it's finished, after a beat so the completed bar is
      // actually seen rather than vanishing on the last file.
      if (p.done >= p.total) {
        window.clearTimeout(hideTimer);
        hideTimer = window.setTimeout(() => setProgress(null), 1600);
      }
    }).then((fn) => (cancelled ? fn() : (unlisten = fn)));

    return () => {
      cancelled = true;
      window.clearTimeout(hideTimer);
      unlisten?.();
    };
  }, []);

  if (!progress) return null;

  const pct = progress.total > 0 ? Math.round((progress.done / progress.total) * 100) : 0;
  const done = progress.done >= progress.total;

  return (
    <div
      role="status"
      style={{
        position: "fixed",
        top: "calc(var(--titlebar-height) + 10px)",
        left: "50%",
        transform: "translateX(-50%)",
        zIndex: 300,
        minWidth: 320,
        maxWidth: 460,
        padding: "10px 14px",
        background: "var(--glass-bg-elevated)",
        border: "1px solid var(--accent)",
        backdropFilter: "blur(12px)",
      }}
    >
      <div style={{ display: "flex", alignItems: "baseline", gap: 8, marginBottom: 7 }}>
        <span style={{ fontSize: 12.5, fontWeight: 600 }}>
          {done ? "Java ready" : "Setting up Java"}
        </span>
        <span style={{ fontSize: 10.5, color: "var(--text-tertiary)", flex: 1 }}>
          {progress.version ? `Java ${progress.version}` : progress.component}
        </span>
        <span style={{ fontSize: 10.5, color: "var(--text-tertiary)", fontFamily: "var(--font-mono)" }}>
          {pct}%
        </span>
      </div>

      <div style={{ height: 4, background: "rgba(0,20,30,0.5)", overflow: "hidden" }}>
        <div
          style={{
            height: "100%",
            width: `${pct}%`,
            background: "var(--accent)",
            transition: "width 200ms linear",
          }}
        />
      </div>

      <div style={{ fontSize: 10.5, color: "var(--text-tertiary)", marginTop: 7, lineHeight: 1.5 }}>
        {done
          ? "Downloaded once and reused by every instance that needs it."
          : "Your machine hasn't got the Java version this Minecraft needs, so Blurred is fetching it. This only happens once."}
      </div>
    </div>
  );
}
