import { useEffect } from "react";
import { listInstances, listRunning, listServers, serverStatuses } from "../lib/tauri";

/**
 * The confirmation on the title bar's close button.
 *
 * # Why closing the launcher is not a free action
 *
 * The launcher isn't a window sitting next to the game — it's the game's parent
 * process. It spawned the JVM, it holds the pipes the log is read through, and
 * on Windows a child process whose parent disappears can be taken down with it.
 * Minecraft killed mid-write does not save; it leaves a half-written region
 * file or a level.dat with no matching backup, and the world either rolls back
 * to the last autosave or fails to load at all. Hosted servers are worse: the
 * console pipe is how `stop` gets sent, so killing the launcher skips the clean
 * shutdown entirely.
 *
 * None of that is obvious from a window with an X in the corner, and the cost
 * of finding out is somebody's world. So the X asks first.
 *
 * # It only appears when something is actually running
 *
 * An earlier version asked on every close, on the theory that a dialog which
 * appears only sometimes trains people to click through it. That's true of
 * dialogs people see often — and closing the launcher with nothing running is
 * the overwhelmingly common case, so the theory backfired: it would have been
 * *mostly* a pointless dialog, which is exactly how people learn to dismiss one
 * without reading. Now it only interrupts when there is genuinely something to
 * lose, and {@link useQuitTargets} names it.
 *
 * Not to be confused with {@link ForceQuitConfirm}, which guards the Quit
 * button on an instance. That one kills the game outright and is the more
 * dangerous of the two.
 */

interface Props {
  /** Instance names with a live game process. */
  games: string[];
  /** Server names currently up. */
  servers: string[];
  onCancel: () => void;
  onConfirm: () => void;
}

export function QuitConfirm({ games, servers, onCancel, onConfirm }: Props) {
  // Escape cancels. Deliberately no Enter-confirms binding: the dangerous
  // choice should never be one keystroke away from someone who is typing.
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
      aria-labelledby="quit-title"
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
        <h2 id="quit-title" style={{ margin: "0 0 10px", fontSize: 14 }}>
          Quit while Minecraft is running?
        </h2>

        <p style={{ margin: "0 0 10px", fontSize: 11.5, lineHeight: 1.6, color: "var(--text-secondary)" }}>
          Blurred launched the game, so closing the launcher can take the game
          down with it. A world that gets killed mid-save can lose progress or
          be left corrupted, and a server won't get its clean shutdown.
        </p>

        <div
          style={{
            margin: "0 0 12px",
            padding: "9px 11px",
            fontSize: 11.5,
            lineHeight: 1.6,
            background: "rgba(255, 179, 71, 0.09)",
            border: "1px solid rgba(255, 179, 71, 0.35)",
          }}
        >
          <strong style={{ display: "block", marginBottom: 4 }}>Running right now:</strong>
          <ul style={{ margin: 0, paddingLeft: 18, color: "var(--text-secondary)" }}>
            {games.map((name) => (
              <li key={`g-${name}`}>{name} (game)</li>
            ))}
            {servers.map((name) => (
              <li key={`s-${name}`}>{name} (server)</li>
            ))}
          </ul>
        </div>

        <p style={{ margin: "0 0 16px", fontSize: 11, lineHeight: 1.6, color: "var(--text-tertiary)" }}>
          Save and quit to the title screen first, then stop the server from the
          Servers tab. Blurred will still be here.
        </p>

        <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
          <button onClick={onCancel} style={{ ...button, borderColor: "var(--accent)", color: "var(--accent)" }} autoFocus>
            Stay open
          </button>
          <button onClick={onConfirm} style={button}>
            Quit anyway
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
 * What's running right now, named.
 *
 * Asks the backend rather than reading the instance store: the store's `running`
 * map is maintained by whichever component launched the game, and a UI reload
 * leaves it empty while the process is very much alive. The backend's map is
 * the process table.
 *
 * A plain async function, not a hook, because the caller has to know the answer
 * *before* it decides whether to show the dialog at all — a hook keyed on "is
 * the dialog open" can only fill it in afterwards.
 */
export async function collectQuitTargets(): Promise<{ games: string[]; servers: string[] }> {
  const [ids, instances, statuses, serverList] = await Promise.all([
    listRunning().catch(() => [] as string[]),
    listInstances().catch(() => []),
    serverStatuses().catch(() => []),
    listServers().catch(() => []),
  ]);

  return {
    // Fall back to the id when the instance has since been deleted — better a
    // uuid in the list than a silently missing entry.
    games: ids.map((id) => instances.find((i) => i.id === id)?.name ?? id),
    servers: statuses
      .filter((s) => s.running)
      .map((s) => serverList.find((sv) => sv.id === s.id)?.name ?? s.id),
  };
}
