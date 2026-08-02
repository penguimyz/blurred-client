import { useEffect, useState, type CSSProperties } from "react";
import { getCurrentWebview } from "@tauri-apps/api/webview";
import type { Modpack } from "../types/instance";
import { useModpackStore } from "../store/modpackStore";
import { useInstanceStore } from "../store/instanceStore";
import { GlassCard } from "../components/GlassCard";
import { Icon } from "../components/Icon";
import { PageHeader } from "../components/PageHeader";
import { applyModpack, exportModpack, importMrpack, revealPath } from "../lib/tauri";
import { formatDate } from "../lib/format";

// Modpacks library (Phase 4, offline half — spec §5.4). Create a reusable pack
// from any instance's mod set, apply one to a fresh instance, and share via a
// single self-contained .bpack file (export writes it and reveals it; import is
// drag-drop). Browsing/importing packs from Modrinth needs the live API and is
// intentionally not here.

export function Modpacks({ onOpenInstance }: { onOpenInstance: (id: string) => void }) {
  const { modpacks, loading, error, refresh, remove, importFrom } = useModpackStore();
  const { instances, refresh: refreshInstances } = useInstanceStore();
  const [showCreate, setShowCreate] = useState(false);
  const [applying, setApplying] = useState<Modpack | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [dropHint, setDropHint] = useState(false);

  useEffect(() => {
    refresh();
    refreshInstances();
  }, [refresh, refreshInstances]);

  // Drag-drop .bpack import (same native mechanism the Mods tab uses).
  useEffect(() => {
    let unlisten: (() => void) | undefined;
    getCurrentWebview()
      .onDragDropEvent(async (event) => {
        if (event.payload.type === "over" || event.payload.type === "enter") setDropHint(true);
        else if (event.payload.type === "leave") setDropHint(false);
        else if (event.payload.type === "drop") {
          setDropHint(false);
          for (const p of event.payload.paths) {
            const lower = p.toLowerCase();
            try {
              if (lower.endsWith(".bpack")) {
                await importFrom(p);
                setNotice("Imported modpack into your library.");
              } else if (lower.endsWith(".mrpack")) {
                const inst = await importMrpack(p);
                setNotice(`Imported "${inst.name}" as a new instance.`);
                await refreshInstances();
                onOpenInstance(inst.id);
              }
            } catch (e) {
              setNotice(String(e));
            }
          }
        }
      })
      .then((fn) => (unlisten = fn));
    return () => unlisten?.();
  }, [importFrom]);

  async function onExport(pack: Modpack) {
    try {
      const path = await exportModpack(pack.id);
      setNotice(`Exported to ${path}`);
      await revealPath(path);
    } catch (e) {
      setNotice(String(e));
    }
  }

  return (
    <div style={{ padding: 32, height: "100%", overflowY: "auto" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
        <PageHeader
          page="modpacks"
          actions={
            <button className="accent" onClick={() => setShowCreate(true)} disabled={instances.length === 0}>
              Create from instance
            </button>
          }
        />
      </div>
      <div style={{ fontSize: 12, color: dropHint ? "var(--accent)" : "var(--text-tertiary)", marginBottom: 20 }}>
        {dropHint
          ? "Drop to import"
          : "Drag a .bpack (into your library) or a Modrinth .mrpack (into a new instance) anywhere here."}
      </div>

      {notice && (
        <GlassCard style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 13, display: "flex", justifyContent: "space-between", gap: 12 }}>
            <span style={{ fontFamily: "var(--font-mono)", wordBreak: "break-all" }}>{notice}</span>
            <button onClick={() => setNotice(null)} style={ghostBtn}>
              Dismiss
            </button>
          </div>
        </GlassCard>
      )}
      {error && <div style={{ color: "var(--danger)", fontSize: 13, marginBottom: 16 }}>{error}</div>}

      {!loading && modpacks.length === 0 ? (
        <GlassCard style={{ textAlign: "center", padding: 48, maxWidth: 620 }}>
          <Icon name="crate" size={32} style={{ color: "var(--text-tertiary)", marginBottom: 12 }} />
          <p style={{ color: "var(--text-secondary)", margin: "0 0 18px" }}>
            No modpacks yet. A modpack is a snapshot of one instance's mod set that
            you can re-apply or hand to someone else.
          </p>
          <div style={{ display: "flex", gap: 10, justifyContent: "center", flexWrap: "wrap" }}>
            <button
              className="accent"
              onClick={() => setShowCreate(true)}
              disabled={instances.length === 0}
              title={instances.length === 0 ? "Create an instance first" : undefined}
            >
              Snapshot an instance
            </button>
          </div>
          <div style={{ fontSize: 11.5, color: "var(--text-tertiary)", marginTop: 16, lineHeight: 1.6 }}>
            You can also drop a <strong>.bpack</strong> here to add it to your library,
            or a Modrinth <strong>.mrpack</strong> to install it straight into a new
            instance. Browse → Modpacks installs them from Modrinth directly.
          </div>
        </GlassCard>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))", gap: 16 }}>
          {modpacks.map((p) => (
            <GlassCard key={p.id}>
              <div style={{ fontWeight: 600, marginBottom: 4 }}>{p.name}</div>
              <div style={{ fontSize: 12, color: "var(--text-secondary)", marginBottom: 8 }}>
                {p.mcVersion} · {p.loader} · {p.mods.length} mod{p.mods.length !== 1 ? "s" : ""}
              </div>
              {p.description && (
                <div style={{ fontSize: 12, color: "var(--text-tertiary)", marginBottom: 8 }}>{p.description}</div>
              )}
              {/* First few mod names, so a pack is identifiable without opening
                  it — "23 mods" alone tells you nothing about which pack it is. */}
              {p.mods.length > 0 && (
                <div style={{ display: "flex", gap: 4, flexWrap: "wrap", marginBottom: 10 }}>
                  {p.mods.slice(0, 4).map((m) => (
                    <span
                      key={m.filename}
                      title={m.filename}
                      style={{
                        fontSize: 10,
                        padding: "2px 7px",
                        borderRadius: 0,
                        background: "rgba(125,226,240,0.1)",
                        border: "1px solid var(--glass-border)",
                        color: "var(--text-secondary)",
                        maxWidth: 110,
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        whiteSpace: "nowrap",
                      }}
                    >
                      {m.name}
                    </span>
                  ))}
                  {p.mods.length > 4 && (
                    <span style={{ fontSize: 10, color: "var(--text-tertiary)", padding: "2px 4px" }}>
                      +{p.mods.length - 4} more
                    </span>
                  )}
                </div>
              )}
              <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginBottom: 12 }}>
                Created {formatDate(p.createdAt)}
              </div>
              <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                <button className="accent" onClick={() => setApplying(p)} style={{ fontSize: 12, padding: "6px 12px" }}>
                  New instance
                </button>
                <button onClick={() => onExport(p)} style={ghostBtn}>
                  Export
                </button>
                <button
                  onClick={() => confirm(`Delete modpack "${p.name}"?`) && remove(p.id)}
                  style={ghostBtn}
                >
                  Delete
                </button>
              </div>
            </GlassCard>
          ))}
        </div>
      )}

      {showCreate && (
        <CreateModpackModal onClose={() => setShowCreate(false)} onDone={() => setShowCreate(false)} />
      )}
      {applying && (
        <ApplyModpackModal
          pack={applying}
          onClose={() => setApplying(null)}
          onApplied={async (id) => {
            setApplying(null);
            await refreshInstances();
            onOpenInstance(id);
          }}
        />
      )}
    </div>
  );
}

function CreateModpackModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const { instances } = useInstanceStore();
  const { createFromInstance } = useModpackStore();
  const [instanceId, setInstanceId] = useState(instances[0]?.id ?? "");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  return (
    <Modal onClose={onClose} title="Create modpack from instance">
      <label style={labelStyle}>Source instance</label>
      <select value={instanceId} onChange={(e) => setInstanceId(e.target.value)} style={inputStyle}>
        {instances.map((i) => (
          <option key={i.id} value={i.id}>
            {i.name} ({i.mcVersion} · {i.loader}, {i.mods.length} mods)
          </option>
        ))}
      </select>
      <label style={labelStyle}>Name</label>
      <input value={name} onChange={(e) => setName(e.target.value)} placeholder="My Pack" style={inputStyle} />
      <label style={labelStyle}>Description</label>
      <input value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Optional" style={inputStyle} />
      {error && <div style={{ color: "var(--danger)", fontSize: 12, marginBottom: 8 }}>{error}</div>}
      <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
        <button onClick={onClose} style={{ ...ghostBtn, flex: 1 }}>
          Cancel
        </button>
        <button
          className="accent"
          style={{ flex: 1 }}
          disabled={!name.trim() || !instanceId || busy}
          onClick={async () => {
            setBusy(true);
            setError(null);
            try {
              await createFromInstance(instanceId, name.trim(), description.trim());
              onDone();
            } catch (e) {
              setError(String(e));
              setBusy(false);
            }
          }}
        >
          {busy ? "Creating…" : "Create"}
        </button>
      </div>
    </Modal>
  );
}

function ApplyModpackModal({
  pack,
  onClose,
  onApplied,
}: {
  pack: Modpack;
  onClose: () => void;
  onApplied: (instanceId: string) => void;
}) {
  const [name, setName] = useState(pack.name);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  return (
    <Modal onClose={onClose} title={`New instance from "${pack.name}"`}>
      <label style={labelStyle}>Instance name</label>
      <input value={name} onChange={(e) => setName(e.target.value)} style={inputStyle} />
      <div style={{ fontSize: 12, color: "var(--text-tertiary)", marginBottom: 8 }}>
        Creates a {pack.mcVersion} · {pack.loader} instance with {pack.mods.length} mod
        {pack.mods.length !== 1 ? "s" : ""} copied in.
      </div>
      {error && <div style={{ color: "var(--danger)", fontSize: 12, marginBottom: 8 }}>{error}</div>}
      <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
        <button onClick={onClose} style={{ ...ghostBtn, flex: 1 }}>
          Cancel
        </button>
        <button
          className="accent"
          style={{ flex: 1 }}
          disabled={!name.trim() || busy}
          onClick={async () => {
            setBusy(true);
            setError(null);
            try {
              const inst = await applyModpack(pack.id, name.trim());
              onApplied(inst.id);
            } catch (e) {
              setError(String(e));
              setBusy(false);
            }
          }}
        >
          {busy ? "Creating…" : "Create instance"}
        </button>
      </div>
    </Modal>
  );
}

function Modal({ title, children, onClose }: { title: string; children: React.ReactNode; onClose: () => void }) {
  return (
    <div
      style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}
      onClick={onClose}
    >
      <GlassCard style={{ width: 400 }}>
        <div onClick={(e) => e.stopPropagation()}>
          <h2 style={{ marginTop: 0, fontSize: 16 }}>{title}</h2>
          {children}
        </div>
      </GlassCard>
    </div>
  );
}

const labelStyle: CSSProperties = { fontSize: 12, color: "var(--text-secondary)", display: "block", marginBottom: 4 };
const inputStyle: CSSProperties = {
  width: "100%",
  marginBottom: 12,
  padding: "8px 10px",
  borderRadius: "var(--radius-sm)",
  border: "1px solid var(--glass-border)",
  backgroundColor: "rgba(0,20,30,0.3)",
  color: "var(--text-primary)",
  fontSize: 13,
};
const ghostBtn: CSSProperties = {
  background: "transparent",
  border: "1px solid var(--glass-border)",
  borderRadius: "var(--radius-sm)",
  color: "var(--text-secondary)",
  padding: "6px 12px",
  fontSize: 12,
  cursor: "pointer",
};
