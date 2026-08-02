import { useEffect, useMemo, useState } from "react";
import type { TabProps } from "./InstanceDetail";
import type { ConfigFile } from "../../types/instance";
import { listConfigFiles, readConfigFile, writeConfigFile } from "../../lib/tauri";
import { MonoField } from "../../components/MonoField";
import { applyEdit, parseConfig } from "../../lib/configFormat";
import { formatBytes } from "../../lib/format";

// Consolidated per-mod config editor (spec §4.2 Configs / §5.1). Sniffs the
// format server-side and, for JSON/TOML/.properties, offers a friendly key/value
// form; everything falls back to a raw text editor that is always available and
// is the source of truth on save. The friendly form for line-based formats edits
// values IN PLACE in the raw text, so comments and structure survive round-trips.

export function ConfigsTab({ instance }: TabProps) {
  const [files, setFiles] = useState<ConfigFile[]>([]);
  const [selected, setSelected] = useState<ConfigFile | null>(null);
  const [text, setText] = useState("");
  const [original, setOriginal] = useState("");
  const [mode, setMode] = useState<"form" | "raw">("form");
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    listConfigFiles(instance.id).then(setFiles).catch((e) => setError(String(e)));
  }, [instance.id]);

  async function open(file: ConfigFile) {
    setError(null);
    setSaved(false);
    try {
      const contents = await readConfigFile(instance.id, file.relPath);
      setSelected(file);
      setText(contents);
      setOriginal(contents);
      const parsed = parseConfig(contents, file.format);
      setMode(parsed.supported ? "form" : "raw");
    } catch (e) {
      setError(String(e));
    }
  }

  const parsed = useMemo(
    () => (selected ? parseConfig(text, selected.format) : { entries: [], supported: false }),
    [text, selected]
  );
  const dirty = text !== original;

  async function save() {
    if (!selected) return;
    try {
      await writeConfigFile(instance.id, selected.relPath, text);
      setOriginal(text);
      setSaved(true);
      setTimeout(() => setSaved(false), 1500);
    } catch (e) {
      setError(String(e));
    }
  }

  return (
    <div style={{ display: "flex", gap: 16, height: "100%", minHeight: 400 }}>
      {/* File list */}
      <div style={{ width: 240, flexShrink: 0, overflowY: "auto" }}>
        <div style={{ fontSize: 12, color: "var(--text-secondary)", marginBottom: 8 }}>
          {files.length} config file{files.length !== 1 ? "s" : ""}
        </div>
        {files.length === 0 && (
          <div style={{ fontSize: 12, color: "var(--text-tertiary)" }}>
            No config files yet. They appear here after mods generate them on first launch.
          </div>
        )}
        {files.map((f) => (
          <button
            key={f.relPath}
            onClick={() => open(f)}
            style={{
              display: "block",
              width: "100%",
              textAlign: "left",
              background: selected?.relPath === f.relPath ? "var(--glass-bg-elevated)" : "transparent",
              border: "none",
              borderRadius: "var(--radius-sm)",
              padding: "8px 10px",
              marginBottom: 2,
              cursor: "pointer",
              color: "var(--text-primary)",
            }}
          >
            <div style={{ fontSize: 12.5, fontFamily: "var(--font-mono)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
              {f.relPath}
            </div>
            <div style={{ fontSize: 10, color: "var(--text-tertiary)" }}>
              {f.format} · {formatBytes(f.size)}
            </div>
          </button>
        ))}
      </div>

      {/* Editor */}
      <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column" }}>
        {error && <div style={{ color: "var(--danger)", fontSize: 13, marginBottom: 8 }}>{error}</div>}
        {!selected ? (
          <div style={{ color: "var(--text-tertiary)", fontSize: 13 }}>Select a config file to edit.</div>
        ) : (
          <>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
              <div style={{ display: "flex", gap: 4 }}>
                <button
                  onClick={() => setMode("form")}
                  disabled={!parsed.supported}
                  style={toggleBtn(mode === "form", !parsed.supported)}
                  title={parsed.supported ? "" : "No friendly form for this format — raw only."}
                >
                  Form
                </button>
                <button onClick={() => setMode("raw")} style={toggleBtn(mode === "raw", false)}>
                  Raw
                </button>
              </div>
              <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                {saved && <span style={{ fontSize: 12, color: "var(--success)" }}>Saved</span>}
                <button className="accent" onClick={save} disabled={!dirty} style={{ opacity: dirty ? 1 : 0.5 }}>
                  Save
                </button>
              </div>
            </div>

            {mode === "form" && parsed.supported ? (
              <div style={{ display: "flex", flexDirection: "column", gap: 10, overflowY: "auto" }}>
                {parsed.entries.map((entry) => (
                  <div key={`${entry.section ?? ""}.${entry.key}.${entry.line ?? ""}`} style={{ display: "grid", gridTemplateColumns: "200px 1fr", gap: 12, alignItems: "center" }}>
                    <label style={{ fontSize: 12.5, fontFamily: "var(--font-mono)", color: "var(--text-secondary)", overflow: "hidden", textOverflow: "ellipsis" }} title={entry.key}>
                      {entry.section ? `${entry.section}.` : ""}
                      {entry.key}
                    </label>
                    {entry.value === "true" || entry.value === "false" ? (
                      <select
                        value={entry.value}
                        onChange={(e) => setText(applyEdit(text, selected.format, entry, e.target.value))}
                        style={selectStyle}
                      >
                        <option value="true">true</option>
                        <option value="false">false</option>
                      </select>
                    ) : (
                      <MonoField
                        value={entry.value}
                        onChange={(v) => setText(applyEdit(text, selected.format, entry, v))}
                        copyable={false}
                      />
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <MonoField value={text} onChange={setText} multiline rows={20} copyable={false} spellCheck={false} />
            )}
          </>
        )}
      </div>
    </div>
  );
}

function toggleBtn(active: boolean, disabled: boolean) {
  return {
    background: active ? "var(--glass-bg-elevated)" : "transparent",
    border: "1px solid var(--glass-border)",
    borderRadius: "var(--radius-sm)",
    color: disabled ? "var(--text-tertiary)" : active ? "var(--text-primary)" : "var(--text-secondary)",
    padding: "6px 14px",
    fontSize: 12,
    cursor: disabled ? "not-allowed" : "pointer",
  };
}

const selectStyle = {
  width: "100%",
  padding: "6px 10px",
  borderRadius: "var(--radius-sm)",
  border: "1px solid var(--glass-border)",
  backgroundColor: "rgba(0,20,30,0.3)",
  color: "var(--text-primary)",
  fontSize: 13,
};
