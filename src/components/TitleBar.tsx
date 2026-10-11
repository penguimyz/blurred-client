import { useEffect, useRef, useState } from "react";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { Logo } from "./Icon";
import { QuitConfirm, collectQuitTargets } from "./QuitConfirm";
import { useBubbleCount } from "../lib/bubbleCounter";

// Custom window chrome. The app runs with `decorations: false` +
// `transparent: true` (tauri.conf.json) for the frosted-glass look, so there is
// no native title bar — this draws our own minimize / maximize-restore / close
// controls and a draggable region. The buttons drive the Tauri window API;
// dragging uses the `data-tauri-drag-region` attribute (needs the
// core:window:allow-start-dragging permission, see capabilities/default.json).

const appWindow = getCurrentWindow();

function Icon({ kind }: { kind: "min" | "max" | "restore" | "close" }) {
  const common = { width: 10, height: 10, viewBox: "0 0 10 10", fill: "none", stroke: "currentColor", strokeWidth: 1 };
  switch (kind) {
    case "min":
      return (
        <svg {...common}>
          <line x1="1" y1="5" x2="9" y2="5" />
        </svg>
      );
    case "max":
      return (
        <svg {...common}>
          <rect x="0.5" y="0.5" width="9" height="9" />
        </svg>
      );
    case "restore":
      return (
        <svg {...common}>
          <rect x="0.5" y="2.5" width="6" height="6" />
          <path d="M2.5 2.5 V0.5 H9.5 V7.5 H6.5" />
        </svg>
      );
    case "close":
      return (
        <svg {...common}>
          <line x1="1" y1="1" x2="9" y2="9" />
          <line x1="9" y1="1" x2="1" y2="9" />
        </svg>
      );
  }
}

/**
 * Lifetime bubble-pop tally.
 *
 * Hidden until the first pop rather than showing a permanent "0": a counter
 * nobody has started is chrome, and the point of this is that it appears once
 * you've discovered the bubbles are clickable.
 *
 * The little ring is drawn rather than an emoji so it matches the pixel bubbles
 * on the canvas at any DPI.
 */
function BubbleTally() {
  const count = useBubbleCount();
  const [bumped, setBumped] = useState(false);
  const previous = useRef(count);

  // Flash on each pop. Keyed off a change in the value, so mounting with a
  // large stored count doesn't animate.
  useEffect(() => {
    if (count === previous.current) return;
    previous.current = count;
    setBumped(true);
    const t = window.setTimeout(() => setBumped(false), 220);
    return () => window.clearTimeout(t);
  }, [count]);

  if (count === 0) return null;

  return (
    <span
      className="titlebar-bubbles"
      data-tauri-drag-region
      title={`${count.toLocaleString()} bubble${count === 1 ? "" : "s"} popped`}
      style={{
        transform: bumped ? "scale(1.18)" : "scale(1)",
        color: bumped ? "var(--accent)" : undefined,
      }}
    >
      <svg width="9" height="9" viewBox="0 0 9 9" aria-hidden focusable="false">
        {/* A pixel ring: four edge blocks plus a highlight, same read as the
            bubbles on the canvas. */}
        <rect x="3" y="0" width="3" height="1.5" fill="currentColor" />
        <rect x="3" y="7.5" width="3" height="1.5" fill="currentColor" />
        <rect x="0" y="3" width="1.5" height="3" fill="currentColor" />
        <rect x="7.5" y="3" width="1.5" height="3" fill="currentColor" />
        <rect x="1.5" y="1.5" width="1.5" height="1.5" fill="currentColor" />
        <rect x="6" y="6" width="1.5" height="1.5" fill="currentColor" />
      </svg>
      {count.toLocaleString()}
    </span>
  );
}

export function TitleBar() {
  const [maximized, setMaximized] = useState(false);
  const [atRisk, setAtRisk] = useState<{ games: string[]; servers: string[] } | null>(null);

  useEffect(() => {
    let unlisten: (() => void) | undefined;
    appWindow.isMaximized().then(setMaximized).catch(() => {});
    appWindow
      .onResized(() => {
        appWindow.isMaximized().then(setMaximized).catch(() => {});
      })
      .then((fn) => {
        unlisten = fn;
      });
    return () => unlisten?.();
  }, []);

  // Intercept every close, not just the X. `close()` from our own button, Alt+F4
  // and a taskbar "Close window" all arrive here, so the confirmation can't be
  // routed around — which matters, because the hazard it warns about (see
  // QuitConfirm) doesn't care which one you used.
  //
  // The close is always cancelled first and re-issued as `destroy()` once
  // there's an answer, because deciding whether to ask means a round trip to
  // the backend and `onCloseRequested` cannot be awaited — by the time the
  // process table comes back, the window would already be gone.
  useEffect(() => {
    // Only the launcher itself. A popped-out log window closing takes nothing
    // down with it, so warning about losing a world there would be a lie — and
    // an annoying one, since closing a log window is a thing you do often.
    if (appWindow.label !== "main") return;

    let unlisten: (() => void) | undefined;
    let cancelled = false;
    appWindow
      .onCloseRequested((event) => {
        event.preventDefault();
        collectQuitTargets()
          .then((targets) => {
            if (targets.games.length === 0 && targets.servers.length === 0) {
              appWindow.destroy();
            } else {
              setAtRisk(targets);
            }
          })
          // If we can't tell what's running, don't hold the window hostage over
          // it — an unclosable launcher is a worse failure than an unasked
          // question.
          .catch(() => appWindow.destroy());
      })
      .then((fn) => (cancelled ? fn() : (unlisten = fn)));
    return () => {
      cancelled = true;
      unlisten?.();
    };
  }, []);

  return (
    <>
    <div className="titlebar" data-tauri-drag-region>
      <div className="titlebar-title" data-tauri-drag-region>
        <Logo size={15} />
        Blurred Client
        <BubbleTally />
      </div>
      <div className="titlebar-controls">
        <button className="tb-btn" onClick={() => appWindow.minimize()} title="Minimize" aria-label="Minimize">
          <Icon kind="min" />
        </button>
        <button
          className="tb-btn"
          onClick={() => appWindow.toggleMaximize()}
          title={maximized ? "Restore" : "Maximize"}
          aria-label={maximized ? "Restore" : "Maximize"}
        >
          <Icon kind={maximized ? "restore" : "max"} />
        </button>
        <button className="tb-btn tb-close" onClick={() => appWindow.close()} title="Close" aria-label="Close">
          <Icon kind="close" />
        </button>
      </div>
    </div>

    {/* Outside the bar, not inside it: `.titlebar` is `user-select: none` and a
        drag region, neither of which a dialog with body text wants to inherit. */}
    {atRisk && (
      <QuitConfirm
        games={atRisk.games}
        servers={atRisk.servers}
        onCancel={() => setAtRisk(null)}
        // `destroy`, not `close`: close would come straight back round to the
        // handler above and the window would never shut.
        onConfirm={() => appWindow.destroy()}
      />
    )}
    </>
  );
}
