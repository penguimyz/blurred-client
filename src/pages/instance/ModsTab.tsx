import { useEffect, useState } from "react";
import { getCurrentWebview } from "@tauri-apps/api/webview";
import type { TabProps } from "./InstanceDetail";
import type { ModRef, ModUpdate } from "../../types/instance";
import { DataTable, type Column } from "../../components/DataTable";
import {
  addLocalMod,
  checkModUpdates,
  removeMod,
  setModEnabled,
  setModPinned,
  syncInstanceMods,
  updateAllMods,
  updateMod,
} from "../../lib/tauri";
import { formatDuration } from "../../lib/format";

// Mods tab — Prism depth layer. Dense table of installed mods with the three
// Prism-style controls: enable/disable (renames to .disabled, no delete), pin
// (exclude from future auto-update), and remove. Local .jars are side-loaded by
// dragging them onto the drop zone — the webview's native drag-drop hands us
// real filesystem paths, so no file-dialog plugin is needed (spec §5.1).
//
// Installing from the Modrinth browser is the one path that needs the live API
// (downloading the jar), so that lives in the Browse tab, not here.

export function ModsTab({ instance, setInstance }: TabProps) {
  const [dragOver, setDragOver] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [updates, setUpdates] = useState<ModUpdate[]>([]);
  const [checking, setChecking] = useState(false);
  const [checkedOnce, setCheckedOnce] = useState(false);

  const updateFor = (filename: string) => updates.find((u) => u.filename === filename);
  const modrinthCount = instance.mods.filter((m) => m.source === "modrinth").length;

  // Reconcile the list against disk whenever the tab opens (catches jars the
  // user dropped in with a file manager, prunes ones deleted out of band).
  useEffect(() => {
    syncInstanceMods(instance.id).then(setInstance).catch((e) => setError(String(e)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [instance.id]);

  // Native drag-drop: only active while this tab is mounted.
  useEffect(() => {
    let unlisten: (() => void) | undefined;
    getCurrentWebview()
      .onDragDropEvent(async (event) => {
        if (event.payload.type === "over" || event.payload.type === "enter") {
          setDragOver(true);
        } else if (event.payload.type === "leave") {
          setDragOver(false);
        } else if (event.payload.type === "drop") {
          setDragOver(false);
          const jars = event.payload.paths.filter((p) => p.toLowerCase().endsWith(".jar"));
          if (jars.length === 0) return;
          setBusy(true);
          setError(null);
          try {
            let latest = instance;
            for (const p of jars) {
              latest = await addLocalMod(instance.id, p);
            }
            setInstance(latest);
          } catch (e) {
            setError(String(e));
          } finally {
            setBusy(false);
          }
        }
      })
      .then((fn) => {
        unlisten = fn;
      });
    return () => {
      unlisten?.();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [instance.id]);

  async function guard(fn: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }

  async function check() {
    setChecking(true);
    setError(null);
    try {
      setUpdates(await checkModUpdates(instance.id));
      setCheckedOnce(true);
    } catch (e) {
      setError(String(e));
    } finally {
      setChecking(false);
    }
  }

  async function updateOne(filename: string) {
    await guard(async () => {
      setInstance(await updateMod(instance.id, filename));
      setUpdates((prev) => prev.filter((u) => u.filename !== filename));
    });
  }

  async function updateAll() {
    await guard(async () => {
      setInstance(await updateAllMods(instance.id));
      setUpdates([]);
    });
  }

  const columns: Column<ModRef>[] = [
    {
      key: "enabled",
      header: "On",
      width: 40,
      render: (m) => (
        <input
          type="checkbox"
          checked={m.enabled}
          disabled={busy}
          onChange={(e) => guard(async () => setInstance(await setModEnabled(instance.id, m.filename, e.target.checked)))}
          title={m.enabled ? "Disable" : "Enable"}
        />
      ),
    },
    {
      key: "name",
      header: "Mod",
      render: (m) => (
        <div>
          <div style={{ fontWeight: 500, opacity: m.enabled ? 1 : 0.5 }}>{m.name}</div>
          <div style={{ fontSize: 11, color: "var(--text-tertiary)", fontFamily: "var(--font-mono)" }}>{m.filename}</div>
        </div>
      ),
    },
    {
      key: "version",
      header: "Version",
      mono: true,
      align: "left",
      render: (m) => {
        const up = updateFor(m.filename);
        return up ? (
          <span>
            {m.version}{" "}
            <span style={{ color: "var(--success)" }}>→ {up.latestVersion}</span>
          </span>
        ) : (
          m.version
        );
      },
    },
    {
      key: "source",
      header: "Source",
      render: (m) => (
        <span style={{ fontSize: 11, padding: "2px 8px", borderRadius: 0, background: "var(--glass-bg-elevated)", color: "var(--text-secondary)" }}>
          {m.source}
        </span>
      ),
    },
    {
      key: "pinned",
      header: "Pinned",
      width: 60,
      align: "center",
      render: (m) => (
        <input
          type="checkbox"
          checked={m.pinned}
          disabled={busy}
          onChange={(e) => guard(async () => setInstance(await setModPinned(instance.id, m.filename, e.target.checked)))}
          title="Pin to exclude from auto-update"
        />
      ),
    },
    {
      key: "actions",
      header: "",
      width: 140,
      align: "right",
      render: (m) => (
        <div style={{ display: "flex", gap: 6, justifyContent: "flex-end" }}>
          {updateFor(m.filename) && (
            <button disabled={busy} onClick={() => updateOne(m.filename)} style={{ ...ghostBtn, color: "var(--success)", borderColor: "var(--success)" }}>
              Update
            </button>
          )}
          <button
            disabled={busy}
            onClick={() => {
              if (confirm(`Remove ${m.name}? The .jar will be deleted from disk.`)) {
                guard(async () => setInstance(await removeMod(instance.id, m.filename)));
              }
            }}
            style={ghostBtn}
          >
            Remove
          </button>
        </div>
      ),
    },
  ];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <div
        style={{
          border: `1.5px dashed ${dragOver ? "var(--accent)" : "var(--glass-border)"}`,
          borderRadius: "var(--radius-md)",
          padding: 24,
          textAlign: "center",
          color: dragOver ? "var(--text-primary)" : "var(--text-secondary)",
          background: dragOver ? "var(--glass-bg-elevated)" : "transparent",
          transition: "all 150ms ease",
          fontSize: 13,
        }}
      >
        {busy ? "Working…" : "Drag & drop .jar files here to add mods"}
      </div>

      {error && <div style={{ color: "var(--danger)", fontSize: 13 }}>{error}</div>}

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
        <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
          {instance.mods.length} mod{instance.mods.length !== 1 ? "s" : ""}
          {instance.totalPlaytimeSeconds > 0 && ` · ${formatDuration(instance.totalPlaytimeSeconds)} played`}
          {checkedOnce && updates.length === 0 && !checking && (
            <span style={{ color: "var(--success)", marginLeft: 8 }}>All up to date</span>
          )}
          {updates.length > 0 && (
            <span style={{ color: "var(--success)", marginLeft: 8 }}>
              {updates.length} update{updates.length !== 1 ? "s" : ""} available
            </span>
          )}
        </div>
        {modrinthCount > 0 && (
          <div style={{ display: "flex", gap: 6 }}>
            <button disabled={checking || busy} onClick={check} style={ghostBtn}>
              {checking ? "Checking…" : "Check for updates"}
            </button>
            {updates.length > 0 && (
              <button className="accent" disabled={busy} onClick={updateAll} style={{ fontSize: 11, padding: "4px 12px" }}>
                Update all
              </button>
            )}
          </div>
        )}
      </div>

      <DataTable
        columns={columns}
        rows={instance.mods}
        rowKey={(m) => m.filename}
        empty="No mods installed yet. Drop a .jar above, or install from the Browse tab."
      />
    </div>
  );
}

const ghostBtn = {
  background: "transparent",
  border: "1px solid var(--glass-border)",
  borderRadius: "var(--radius-sm)",
  color: "var(--text-secondary)",
  padding: "4px 10px",
  fontSize: 11,
  cursor: "pointer",
};
