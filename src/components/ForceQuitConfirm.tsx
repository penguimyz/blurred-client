import { useEffect, useState, type ReactNode } from "react";
import { useInstanceStore } from "../store/instanceStore";

/**
 * The confirmation on an instance's Quit button.
 *
 * # Why this button is not "close the game"
 *
 * It kills the process. There is no polite version — the launcher has no way to
 * ask Minecraft to shut down cleanly, because the game exposes no such channel
 * to whatever started it. So Quit is `TerminateProcess` / `SIGKILL`, and the JVM
 * gets no chance to finish what it was doing.
 *
 * In a singleplayer world that is a genuine hazard rather than a theoretical
 * one. The game autosaves on a timer and writes chunks in the background, and a
 * kill lands wherever it lands: mid-write on a region file, or after `level.dat`
 * has been replaced but before its backup was. The outcomes are losing whatever
 * happened since the last autosave, or a world that won't load at all.
 *
 * On a server it's milder but still real — no disconnect packet, so the server
 * waits out your timeout before releasing the slot.
 *
 * # Why it asks every time
 *
 * A red button labelled Quit reads like the opposite of Play, and the cost of
 * that misreading is somebody's world. This is the one place in the launcher
 * where the honest thing is to slow the user down: save and quit from inside
 * the game, and this button is only for when the game has stopped responding.
 */
function ForceQuitConfirm({
  name,
  onCancel,
  onConfirm,
}: {
  name: string;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  // Escape backs out. No Enter binding on purpose — the destructive choice
  // should never be one keystroke away.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onCancel();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onCancel]);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="force-quit-title"
      onClick={onCancel}
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 1000,
        display: "grid",
        placeItems: "center",
        background: "rgba(0, 12, 20, 0.55)",
        backdropFilter: "blur(3px)",
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          width: 440,
          maxWidth: "calc(100vw - 48px)",
          padding: "18px 20px 16px",
          background: "var(--glass-bg-elevated)",
          border: "1px solid var(--warning, #ffb347)",
          backdropFilter: "blur(14px)",
        }}
      >
        <h2 id="force-quit-title" style={{ margin: "0 0 10px", fontSize: 14 }}>
          Force close {name}?
        </h2>

        <p
          style={{
            margin: "0 0 12px",
            fontSize: 11.5,
            lineHeight: 1.6,
            color: "var(--text-secondary)",
          }}
        >
          This kills Minecraft outright — it doesn't get to save first. A
          singleplayer world caught mid-save can lose everything since the last
          autosave, or be left corrupted and refuse to load.
        </p>

        <div
          style={{
            margin: "0 0 16px",
            padding: "9px 11px",
            fontSize: 11.5,
            lineHeight: 1.6,
            background: "rgba(255, 179, 71, 0.09)",
            border: "1px solid rgba(255, 179, 71, 0.35)",
          }}
        >
          Save and quit to title from inside the game instead. Use this only when
          the game has actually stopped responding.
        </div>

        <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
          <button
            onClick={onCancel}
            autoFocus
            style={{ ...button, borderColor: "var(--accent)", color: "var(--accent)" }}
          >
            Leave it running
          </button>
          <button onClick={onConfirm} style={button}>
            Force close
          </button>
        </div>
      </div>
    </div>
  );
}

const button: React.CSSProperties = {
  fontSize: 11.5,
  padding: "6px 14px",
  cursor: "pointer",
  border: "1px solid var(--glass-border)",
  background: "transparent",
  color: "var(--text-secondary)",
};

/**
 * Wire up a Quit button that confirms first.
 *
 * Returned as a hook rather than left for each caller to assemble, because
 * there are three Quit buttons — the launchpad hero, the instance grid and the
 * instance detail header — and a confirmation that only two of them route
 * through is a confirmation that can be walked around without knowing it.
 * Render `dialog` alongside the button and call `requestQuit` from its handler.
 */
export function useForceQuit(): {
  requestQuit: (instanceId: string, name: string) => void;
  dialog: ReactNode;
} {
  const quit = useInstanceStore((s) => s.quit);
  const [target, setTarget] = useState<{ id: string; name: string } | null>(null);

  return {
    requestQuit: (instanceId, name) => setTarget({ id: instanceId, name }),
    dialog: target ? (
      <ForceQuitConfirm
        name={target.name}
        onCancel={() => setTarget(null)}
        onConfirm={() => {
          quit(target.id).catch(() => {});
          setTarget(null);
        }}
      />
    ) : null,
  };
}
