import { useEffect, useState } from "react";
import { getCurrentWindow } from "@tauri-apps/api/window";

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
        Blurred Client
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
