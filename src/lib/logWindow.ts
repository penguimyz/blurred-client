import { WebviewWindow } from "@tauri-apps/api/webviewWindow";

/**
 * Logs in a window of their own.
 *
 * # Why a real window and not a modal
 *
 * The point of popping logs out is to watch them *while doing something else*
 * — reading a stack trace next to the mod list, or keeping a crash open while
 * you change a setting. A modal is the opposite of that: it covers the launcher
 * and blocks it. A second OS window can sit on your other monitor, stay on top
 * of the game, and be resized to whatever a wall of Java stack frames needs.
 *
 * # How it renders
 *
 * The same bundle, with `?view=logs` in the URL. `main.tsx` reads that and
 * mounts a log-only root instead of the launcher, so there's one build, one set
 * of components, and no second entry point to keep in step. The window carries
 * the same frameless chrome as the main one, so it looks like part of the app
 * rather than a browser popup.
 */
export async function openLogWindow(options?: {
  /** Show one instance's live log; omitted means the crash history. */
  instanceId?: string;
  /** Window title — the instance name reads better than "Logs". */
  title?: string;
}): Promise<void> {
  const params = new URLSearchParams({ view: "logs" });
  if (options?.instanceId) params.set("instance", options.instanceId);

  // One window per target rather than one per click: pressing the button twice
  // should bring the existing window forward, not stack a second identical one
  // behind it.
  const label = options?.instanceId ? `logs-${sanitize(options.instanceId)}` : "logs";

  const existing = await WebviewWindow.getByLabel(label);
  if (existing) {
    await existing.unminimize().catch(() => {});
    await existing.setFocus().catch(() => {});
    return;
  }

  const win = new WebviewWindow(label, {
    url: `index.html?${params.toString()}`,
    title: options?.title ?? "Blurred Client — Logs",
    width: 900,
    height: 620,
    minWidth: 480,
    minHeight: 320,
    resizable: true,
    // Matches the main window: the app draws its own title bar, so a native
    // one here would give this window two.
    decorations: false,
    transparent: true,
  });

  // A failure here is worth reporting rather than swallowing — it almost always
  // means the capability is missing, which is a wiring bug, not a user problem.
  await new Promise<void>((resolve, reject) => {
    win.once("tauri://created", () => resolve());
    win.once("tauri://error", (e) => reject(new Error(String(e.payload))));
  });
}

/** Window labels allow only alphanumerics, `-`, `/`, `:` and `_`. */
function sanitize(value: string): string {
  return value.replace(/[^a-zA-Z0-9\-_]/g, "");
}
