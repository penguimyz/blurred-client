import { useEffect, useState } from "react";
import { Backdrop } from "../components/Backdrop";
import { TitleBar } from "../components/TitleBar";
import { GlobalLogs } from "./GlobalLogs";
import { LogsTab } from "./instance/LogsTab";
import { getInstance } from "../lib/tauri";
import type { Instance } from "../types/instance";

/**
 * The root of a popped-out log window.
 *
 * Deliberately thin. This is the same bundle as the launcher, so everything it
 * shows is the component the main window shows — what's stripped out is the
 * launcher *around* it: no nav rail, no chat wiring, no update banner, no
 * crash banner. A second window that quietly re-ran all of that would open a
 * second IRC connection and start its own update countdown, which is a lot of
 * behaviour to inherit for something whose job is to display text.
 *
 * Live lines still arrive: `instance-log` is an app-wide Tauri event, so every
 * window gets it without any extra plumbing.
 */
export function LogWindow({ instanceId }: { instanceId: string | null }) {
  const [instance, setInstance] = useState<Instance | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!instanceId) return;
    getInstance(instanceId)
      .then(setInstance)
      .catch((e) => setError(String(e)));
  }, [instanceId]);

  return (
    <>
      <Backdrop />
      <TitleBar />
      <div
        style={{
          height: "100vh",
          paddingTop: "var(--titlebar-height)",
          boxSizing: "border-box",
          overflow: "hidden",
        }}
      >
        {error && <div style={{ padding: 24, color: "var(--danger)", fontSize: 13 }}>{error}</div>}

        {!instanceId && (
          // No instance: the crash history. `onOpenInstance` is a no-op here —
          // this window has no instance view to navigate to, and pretending
          // otherwise would be a button that does nothing.
          <GlobalLogs onOpenInstance={() => {}} popped />
        )}

        {instanceId && instance && (
          <div style={{ height: "100%", overflowY: "auto", padding: 20 }}>
            <LogsTab instance={instance} setInstance={setInstance} />
          </div>
        )}

        {instanceId && !instance && !error && (
          <div style={{ padding: 24, color: "var(--text-secondary)", fontSize: 13 }}>Loading…</div>
        )}
      </div>
    </>
  );
}
