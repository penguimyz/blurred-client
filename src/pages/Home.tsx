import { useEffect, useState, type CSSProperties } from "react";
import { useInstanceStore } from "../store/instanceStore";
import { GlassCard } from "../components/GlassCard";
import type { Loader } from "../types/instance";

export function Home({ onOpenInstance }: { onOpenInstance: (id: string) => void }) {
  const { instances, loading, error, running, refresh, refreshRunning, create, launch, quit, rename, duplicate, remove, openFolder } =
    useInstanceStore();
  const [showCreate, setShowCreate] = useState(false);
  const [renamingId, setRenamingId] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState("");

  useEffect(() => {
    refresh();
    refreshRunning();
  }, [refresh, refreshRunning]);

  async function commitRename(id: string) {
    const v = renameValue.trim();
    if (v) await rename(id, v).catch(() => {});
    setRenamingId(null);
  }

  return (
    <div style={{ padding: 32, height: "100%", overflowY: "auto" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
        <h1 style={{ margin: 0, fontSize: 22, fontWeight: 600 }}>Home</h1>
        <button className="accent" onClick={() => setShowCreate(true)}>
          + New Instance
        </button>
      </div>

      {error && (
        <GlassCard style={{ marginBottom: 16, borderColor: "var(--danger)" }}>
          <span style={{ color: "var(--danger)" }}>{error}</span>
        </GlassCard>
      )}

      {loading && instances.length === 0 && (
        <p style={{ color: "var(--text-secondary)" }}>Loading instances...</p>
      )}

      {!loading && instances.length === 0 && !showCreate && (
        <GlassCard style={{ textAlign: "center", padding: 48 }}>
          <p style={{ color: "var(--text-secondary)", marginBottom: 16 }}>
            No instances yet. Create one to get started.
          </p>
          <button className="accent" onClick={() => setShowCreate(true)}>
            Create your first instance
          </button>
        </GlassCard>
      )}

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))",
          gap: 16,
        }}
      >
        {instances.map((inst) => {
          const isRunning = !!running[inst.id];
          const isRenaming = renamingId === inst.id;
          return (
            <GlassCard key={inst.id} onClick={() => !isRenaming && onOpenInstance(inst.id)}>
              {isRenaming ? (
                <input
                  autoFocus
                  value={renameValue}
                  onClick={(e) => e.stopPropagation()}
                  onChange={(e) => setRenameValue(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") commitRename(inst.id);
                    if (e.key === "Escape") setRenamingId(null);
                  }}
                  onBlur={() => commitRename(inst.id)}
                  style={{
                    width: "100%",
                    marginBottom: 4,
                    padding: "4px 8px",
                    borderRadius: "var(--radius-sm)",
                    border: "1px solid var(--accent)",
                    background: "rgba(0,0,0,0.25)",
                    color: "var(--text-primary)",
                    fontSize: 14,
                    fontWeight: 600,
                  }}
                />
              ) : (
                <div style={{ fontWeight: 600, marginBottom: 4, display: "flex", alignItems: "center", gap: 6 }}>
                  {isRunning && (
                    <span title="Running" style={{ width: 8, height: 8, borderRadius: "50%", background: "var(--success)", flexShrink: 0 }} />
                  )}
                  <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{inst.name}</span>
                </div>
              )}
              <div style={{ fontSize: 12, color: "var(--text-secondary)", marginBottom: 12 }}>
                {inst.mcVersion} · {inst.loader}
              </div>
              <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginBottom: 12 }}>
                {inst.lastPlayed
                  ? `Last played ${new Date(inst.lastPlayed).toLocaleDateString()}`
                  : "Never played"}
              </div>

              {isRunning ? (
                <button
                  style={{ width: "100%", background: "var(--danger)", color: "#fff", border: "none", borderRadius: "var(--radius-sm)", padding: "8px 16px", fontWeight: 600, cursor: "pointer" }}
                  onClick={(e) => {
                    e.stopPropagation();
                    quit(inst.id).catch(() => {});
                  }}
                >
                  ■ Quit
                </button>
              ) : (
                <button
                  className="accent"
                  style={{ width: "100%" }}
                  onClick={(e) => {
                    e.stopPropagation();
                    launch(inst.id).catch(() => {});
                  }}
                >
                  ▶ Play
                </button>
              )}

              {/* Secondary actions */}
              <div style={{ display: "flex", gap: 4, marginTop: 8 }}>
                <CardAction
                  label="Rename"
                  onClick={() => {
                    setRenameValue(inst.name);
                    setRenamingId(inst.id);
                  }}
                />
                <CardAction label="Duplicate" onClick={() => duplicate(inst.id).catch(() => {})} />
                <CardAction label="Folder" onClick={() => openFolder(inst.id).catch(() => {})} />
                <CardAction
                  label="Delete"
                  danger
                  onClick={() => {
                    if (confirm(`Delete instance "${inst.name}"? This removes all its files (mods, saves, configs).`)) {
                      remove(inst.id).catch(() => {});
                    }
                  }}
                />
              </div>
            </GlassCard>
          );
        })}
      </div>

      {showCreate && (
        <CreateInstanceModal
          onClose={() => setShowCreate(false)}
          onCreate={async (name, version, loader) => {
            await create(name, version, loader);
            setShowCreate(false);
          }}
        />
      )}
    </div>
  );
}

// Small secondary action button used on instance cards. Stops click propagation
// so it doesn't also open the instance detail view.
function CardAction({ label, onClick, danger }: { label: string; onClick: () => void; danger?: boolean }) {
  return (
    <button
      onClick={(e) => {
        e.stopPropagation();
        onClick();
      }}
      style={{
        flex: 1,
        background: "transparent",
        border: "1px solid var(--glass-border)",
        borderRadius: "var(--radius-sm)",
        color: danger ? "var(--danger)" : "var(--text-secondary)",
        padding: "5px 4px",
        fontSize: 11,
        cursor: "pointer",
      }}
    >
      {label}
    </button>
  );
}

function CreateInstanceModal({
  onClose,
  onCreate,
}: {
  onClose: () => void;
  onCreate: (name: string, mcVersion: string, loader: Loader) => Promise<void>;
}) {
  const [name, setName] = useState("");
  const [mcVersion, setMcVersion] = useState("1.21.1");
  const [loader, setLoader] = useState<Loader>("vanilla");
  const [submitting, setSubmitting] = useState(false);

  // NOTE: mc_version is a free-text field for now, not a dropdown sourced
  // from the real Mojang version manifest -- that's a follow-up wire-up,
  // not built yet. Type it exactly right (e.g. "1.21.1") or launch will
  // fail with "version not found in manifest".

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(0,0,0,0.5)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
      }}
      onClick={onClose}
    >
      <GlassCard style={{ width: 360 }} className="">
        <div onClick={(e) => e.stopPropagation()}>
          <h2 style={{ marginTop: 0, fontSize: 16 }}>New Instance</h2>

          <label style={{ fontSize: 12, color: "var(--text-secondary)" }}>Name</label>
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="My Instance"
            style={inputStyle}
          />

          <label style={{ fontSize: 12, color: "var(--text-secondary)" }}>MC Version</label>
          <input value={mcVersion} onChange={(e) => setMcVersion(e.target.value)} style={inputStyle} />

          <label style={{ fontSize: 12, color: "var(--text-secondary)" }}>Loader</label>
          <select value={loader} onChange={(e) => setLoader(e.target.value as Loader)} style={inputStyle}>
            <option value="vanilla">Vanilla</option>
            <option value="fabric">Fabric (not launchable yet)</option>
            <option value="forge">Forge (not launchable yet)</option>
            <option value="quilt">Quilt (not launchable yet)</option>
            <option value="neoforge">NeoForge (not launchable yet)</option>
          </select>

          <div style={{ display: "flex", gap: 8, marginTop: 16 }}>
            <button onClick={onClose} style={{ flex: 1 }}>
              Cancel
            </button>
            <button
              className="accent"
              style={{ flex: 1 }}
              disabled={!name.trim() || submitting}
              onClick={async () => {
                setSubmitting(true);
                await onCreate(name.trim(), mcVersion.trim(), loader);
                setSubmitting(false);
              }}
            >
              {submitting ? "Creating..." : "Create"}
            </button>
          </div>
        </div>
      </GlassCard>
    </div>
  );
}

const inputStyle: CSSProperties = {
  width: "100%",
  marginBottom: 12,
  marginTop: 4,
  padding: "8px 10px",
  borderRadius: 8,
  border: "1px solid var(--glass-border)",
  background: "rgba(0,0,0,0.2)",
  color: "var(--text-primary)",
  fontSize: 13,
};
