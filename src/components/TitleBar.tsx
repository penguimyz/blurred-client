import { useEffect, useRef, useState } from "react";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { Logo } from "./Icon";
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

  return (
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
  );
}
