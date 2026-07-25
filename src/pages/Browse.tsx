import { useEffect, useState } from "react";
import { GlassCard } from "../components/GlassCard";
import {
  installModrinthMod,
  installModrinthModpack,
  modrinthSearch,
  type ModrinthSearchResult,
} from "../lib/tauri";
import { useInstanceStore } from "../store/instanceStore";

// Mod browser (spec §5.3), Modrinth only — keyless public API. Search + install:
// picking a hit's Install opens an instance picker, then downloads the newest
// version matching that instance's MC version + loader (with required
// dependencies) into its mods/ folder. Install is offered for mods only, since
// the backend targets mods/ and filters by loader; resource packs / shaders live
// in different folders and would need their own install path.

type Hit = ModrinthSearchResult["hits"][number];

const PROJECT_TYPES = [
  { key: "mod", label: "Mods" },
  { key: "modpack", label: "Modpacks" },
  { key: "resourcepack", label: "Resource Packs" },
  { key: "shader", label: "Shaders" },
];

// A curated subset of Modrinth's mod categories, shown as filter chips.
const MOD_CATEGORIES = [
  "optimization",
  "utility",
  "game-mechanics",
  "library",
  "adventure",
  "decoration",
  "magic",
  "technology",
  "worldgen",
  "mobs",
  "storage",
  "food",
  "equipment",
  "social",
];

export function Browse({ onOpenInstance }: { onOpenInstance: (id: string) => void }) {
  const [query, setQuery] = useState("");
  const [projectType, setProjectType] = useState("mod");
  const [mcVersion, setMcVersion] = useState("");
  const [loader, setLoader] = useState("");
  const [results, setResults] = useState<Hit[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [searched, setSearched] = useState(false);
  const [installFor, setInstallFor] = useState<Hit | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [sortIndex, setSortIndex] = useState("downloads");
  const [categories, setCategories] = useState<string[]>([]);

  async function search() {
    setLoading(true);
    setError(null);
    setSearched(true);
    try {
      const res = await modrinthSearch(query, {
        mcVersion: mcVersion || undefined,
        loader: loader || undefined,
        projectType,
        categories: categories.length ? categories : undefined,
        index: sortIndex,
      });
      setResults(res.hits);
      setTotal(res.total_hits);
    } catch (e) {
      setError(String(e));
      setResults([]);
    } finally {
      setLoading(false);
    }
  }

  // Auto-run when the type / sort / category filters change (and once on mount,
  // which loads the most-downloaded projects — i.e. "popular"). Query changes
  // still wait for Enter/the Search button so we don't fire on every keystroke.
  useEffect(() => {
    search();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectType, sortIndex, categories]);

  const toggleCategory = (c: string) =>
    setCategories((prev) => (prev.includes(c) ? prev.filter((x) => x !== c) : [...prev, c]));

  const [installingId, setInstallingId] = useState<string | null>(null);
  async function installPack(hit: Hit) {
    setInstallingId(hit.project_id);
    setNotice(null);
    try {
      const inst = await installModrinthModpack(hit.project_id);
      setNotice(`Installed "${hit.title}" as a new instance.`);
      onOpenInstance(inst.id);
    } catch (e) {
      setNotice(String(e));
    } finally {
      setInstallingId(null);
    }
  }

  return (
    <div style={{ padding: 32, height: "100%", overflowY: "auto" }}>
      <h1 style={{ margin: "0 0 4px", fontSize: 22, fontWeight: 600 }}>Browse</h1>
      <div style={{ fontSize: 12, color: "var(--text-secondary)", marginBottom: 20 }}>
        Search Modrinth and install mods (with dependencies) straight into an instance.
      </div>

      {notice && (
        <GlassCard style={{ marginBottom: 16 }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 12, fontSize: 13 }}>
            <span>{notice}</span>
            <button onClick={() => setNotice(null)} style={ghostBtn}>
              Dismiss
            </button>
          </div>
        </GlassCard>
      )}

      <GlassCard style={{ marginBottom: 20 }}>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "flex-end" }}>
          <div style={{ flex: 2, minWidth: 200 }}>
            <label style={labelStyle}>Search</label>
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && search()}
              placeholder="sodium, jei, …"
              style={inputStyle}
            />
          </div>
          <div style={{ flex: 1, minWidth: 120 }}>
            <label style={labelStyle}>Type</label>
            <select value={projectType} onChange={(e) => setProjectType(e.target.value)} style={inputStyle}>
              {PROJECT_TYPES.map((t) => (
                <option key={t.key} value={t.key}>
                  {t.label}
                </option>
              ))}
            </select>
          </div>
          <div style={{ flex: 1, minWidth: 110 }}>
            <label style={labelStyle}>MC version</label>
            <input value={mcVersion} onChange={(e) => setMcVersion(e.target.value)} placeholder="any" style={inputStyle} />
          </div>
          <div style={{ flex: 1, minWidth: 110 }}>
            <label style={labelStyle}>Loader</label>
            <input value={loader} onChange={(e) => setLoader(e.target.value)} placeholder="any" style={inputStyle} />
          </div>
          <div style={{ flex: 1, minWidth: 120 }}>
            <label style={labelStyle}>Sort</label>
            <select value={sortIndex} onChange={(e) => setSortIndex(e.target.value)} style={inputStyle}>
              <option value="downloads">Popular</option>
              <option value="relevance">Relevance</option>
              <option value="follows">Followers</option>
              <option value="newest">Newest</option>
              <option value="updated">Recently updated</option>
            </select>
          </div>
          <button className="accent" onClick={search} disabled={loading} style={{ height: 36 }}>
            {loading ? "Searching…" : "Search"}
          </button>
        </div>

        {projectType === "mod" && (
          <div style={{ display: "flex", flexWrap: "wrap", gap: 6, marginTop: 12 }}>
            {MOD_CATEGORIES.map((c) => {
              const on = categories.includes(c);
              return (
                <button
                  key={c}
                  onClick={() => toggleCategory(c)}
                  style={{
                    fontSize: 11,
                    padding: "4px 10px",
                    borderRadius: 12,
                    cursor: "pointer",
                    border: "1px solid var(--glass-border)",
                    background: on ? "var(--accent)" : "transparent",
                    color: on ? "var(--accent-fg)" : "var(--text-secondary)",
                  }}
                >
                  {c.replace(/-/g, " ")}
                </button>
              );
            })}
          </div>
        )}
      </GlassCard>

      {error && (
        <GlassCard style={{ marginBottom: 16, borderColor: "var(--danger)" }}>
          <div style={{ color: "var(--danger)", fontSize: 13 }}>{error}</div>
          <div style={{ fontSize: 12, color: "var(--text-tertiary)", marginTop: 6 }}>
            Modrinth may be unreachable from here — search hits the live api.modrinth.com.
          </div>
        </GlassCard>
      )}

      {searched && !loading && !error && (
        <div style={{ fontSize: 12, color: "var(--text-secondary)", marginBottom: 12 }}>
          {total.toLocaleString()} result{total !== 1 ? "s" : ""}
        </div>
      )}

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(300px, 1fr))", gap: 12 }}>
        {results.map((hit) => (
          <GlassCard key={hit.project_id}>
            <div style={{ display: "flex", gap: 12 }}>
              {hit.icon_url ? (
                <img
                  src={hit.icon_url}
                  alt=""
                  style={{ width: 48, height: 48, borderRadius: "var(--radius-sm)", objectFit: "cover", flexShrink: 0 }}
                />
              ) : (
                <div style={{ width: 48, height: 48, borderRadius: "var(--radius-sm)", background: "var(--glass-bg-elevated)", flexShrink: 0 }} />
              )}
              <div style={{ minWidth: 0 }}>
                <div style={{ fontWeight: 600, fontSize: 14 }}>{hit.title}</div>
                <div style={{ fontSize: 11, color: "var(--text-tertiary)" }}>
                  {hit.downloads.toLocaleString()} downloads
                </div>
              </div>
            </div>
            <div style={{ fontSize: 12, color: "var(--text-secondary)", margin: "10px 0", lineHeight: 1.5 }}>
              {hit.description}
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div style={{ display: "flex", gap: 4, flexWrap: "wrap" }}>
                {hit.categories.slice(0, 3).map((c) => (
                  <span key={c} style={{ fontSize: 10, padding: "2px 6px", borderRadius: 8, background: "var(--glass-bg-elevated)", color: "var(--text-tertiary)" }}>
                    {c}
                  </span>
                ))}
              </div>
              {projectType === "mod" ? (
                <button onClick={() => setInstallFor(hit)} style={{ ...ghostBtn, cursor: "pointer" }}>
                  Install
                </button>
              ) : projectType === "modpack" ? (
                <button
                  onClick={() => installPack(hit)}
                  disabled={installingId === hit.project_id}
                  style={{ ...ghostBtn, cursor: "pointer" }}
                >
                  {installingId === hit.project_id ? "Installing…" : "Install"}
                </button>
              ) : (
                <button disabled title="Install supports mods and modpacks" style={{ ...ghostBtn, cursor: "not-allowed", opacity: 0.5 }}>
                  Install
                </button>
              )}
            </div>
          </GlassCard>
        ))}
      </div>

      {installFor && (
        <InstallModal
          hit={installFor}
          onClose={() => setInstallFor(null)}
          onDone={(msg) => {
            setInstallFor(null);
            setNotice(msg);
          }}
        />
      )}
    </div>
  );
}

function InstallModal({ hit, onClose, onDone }: { hit: Hit; onClose: () => void; onDone: (msg: string) => void }) {
  const { instances, refresh } = useInstanceStore();
  const [instanceId, setInstanceId] = useState("");
  const [withDeps, setWithDeps] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (instances.length === 0) refresh();
  }, [instances.length, refresh]);
  useEffect(() => {
    if (!instanceId && instances[0]) setInstanceId(instances[0].id);
  }, [instances, instanceId]);

  const target = instances.find((i) => i.id === instanceId);

  return (
    <div
      style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}
      onClick={onClose}
    >
      <GlassCard style={{ width: 400 }}>
        <div onClick={(e) => e.stopPropagation()}>
          <h2 style={{ marginTop: 0, fontSize: 16 }}>Install {hit.title}</h2>

          {instances.length === 0 ? (
            <div style={{ fontSize: 13, color: "var(--text-secondary)" }}>
              No instances yet — create one first, then install into it.
            </div>
          ) : (
            <>
              <label style={labelStyle}>Install into</label>
              <select value={instanceId} onChange={(e) => setInstanceId(e.target.value)} style={inputStyle}>
                {instances.map((i) => (
                  <option key={i.id} value={i.id}>
                    {i.name} ({i.mcVersion} · {i.loader})
                  </option>
                ))}
              </select>
              {target && target.loader === "vanilla" && (
                <div style={{ fontSize: 11, color: "var(--warning)", marginBottom: 8 }}>
                  This is a vanilla instance — most mods need a loader (Fabric/Forge/…) and won't have a compatible version.
                </div>
              )}
              <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, color: "var(--text-secondary)", marginBottom: 8, cursor: "pointer" }}>
                <input type="checkbox" checked={withDeps} onChange={(e) => setWithDeps(e.target.checked)} />
                Also install required dependencies
              </label>
              {error && <div style={{ color: "var(--danger)", fontSize: 12, marginBottom: 8 }}>{error}</div>}
              <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
                <button onClick={onClose} style={{ ...ghostBtn, flex: 1 }}>
                  Cancel
                </button>
                <button
                  className="accent"
                  style={{ flex: 1 }}
                  disabled={!instanceId || busy}
                  onClick={async () => {
                    setBusy(true);
                    setError(null);
                    try {
                      const updated = await installModrinthMod(instanceId, hit.project_id, null, withDeps);
                      onDone(`Installed ${hit.title} into ${target?.name ?? "instance"} (${updated.mods.length} mods total).`);
                    } catch (e) {
                      setError(String(e));
                      setBusy(false);
                    }
                  }}
                >
                  {busy ? "Installing…" : "Install"}
                </button>
              </div>
            </>
          )}
        </div>
      </GlassCard>
    </div>
  );
}

const labelStyle = { fontSize: 12, color: "var(--text-secondary)", display: "block" as const, marginBottom: 4 };
const inputStyle = {
  width: "100%",
  padding: "8px 10px",
  borderRadius: "var(--radius-sm)",
  border: "1px solid var(--glass-border)",
  background: "rgba(0,0,0,0.2)",
  color: "var(--text-primary)",
  fontSize: 13,
};
const ghostBtn = {
  background: "transparent",
  border: "1px solid var(--glass-border)",
  borderRadius: "var(--radius-sm)",
  color: "var(--text-secondary)",
  padding: "6px 12px",
  fontSize: 12,
};
