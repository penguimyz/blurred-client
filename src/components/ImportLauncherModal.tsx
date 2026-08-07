import { useCallback, useEffect, useState } from "react";
import { open } from "@tauri-apps/plugin-dialog";
import { GlassCard } from "./GlassCard";
import { Icon } from "./Icon";
import * as api from "../lib/tauri";
import {
  DEFAULT_IMPORT_SELECTION,
  type ImportCandidate,
  type ImportReport,
  type ImportSelection,
  type ImportSource,
} from "../types/import";

/**
 * Bring an instance across from another launcher.
 *
 * Two steps, deliberately: pick what to import, then pick what to bring with
 * it. Splitting them is what makes this less work than copying folders by hand
 * — the first step is the part nobody can do themselves (knowing where Prism
 * keeps its instances), and the second is the part they'd otherwise get wrong
 * (which files actually carry their settings).
 *
 * Nothing here writes to the source launcher. The copy is one-way, so trying
 * Blurred out costs nothing and leaves the old setup working.
 */
export function ImportLauncherModal({
  onClose,
  onImported,
}: {
  onClose: () => void;
  onImported: () => void;
}) {
  const [sources, setSources] = useState<ImportSource[] | null>(null);
  const [picked, setPicked] = useState<ImportCandidate | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [report, setReport] = useState<ImportReport | null>(null);

  const scan = useCallback(async () => {
    setError(null);
    try {
      setSources(await api.detectImportSources());
    } catch (e) {
      setError(String(e));
      setSources([]);
    }
  }, []);

  useEffect(() => {
    scan();
  }, [scan]);

  async function pickFolder() {
    const chosen = await open({ directory: true, multiple: false });
    if (typeof chosen !== "string") return;
    setError(null);
    try {
      const source = await api.scanImportFolder(chosen);
      if (source.instances.length === 0) {
        setError(
          "No Minecraft data in that folder. Pick either a game directory (the one with options.txt and saves/) or a launcher's instances folder."
        );
        return;
      }
      // Put the hand-picked source at the top; it's what they just asked for.
      setSources((prev) => [source, ...(prev ?? [])]);
    } catch (e) {
      setError(String(e));
    }
  }

  return (
    <Shell onClose={onClose} wide={!picked && !report}>
      {report ? (
        <ReportView
          report={report}
          onDone={() => {
            onImported();
            onClose();
          }}
        />
      ) : picked ? (
        <ConfigureImport
          candidate={picked}
          onBack={() => setPicked(null)}
          onDone={setReport}
        />
      ) : (
        <>
          <h2 style={{ marginTop: 0, fontSize: 16 }}>Import from another launcher</h2>
          <p style={{ fontSize: 12, color: "var(--text-tertiary)", lineHeight: 1.6, marginTop: 0 }}>
            Copies your settings, resource packs and server list across. The other launcher isn't
            touched — everything here is a copy, so nothing you already have stops working.
          </p>

          {error && (
            <div style={{ color: "var(--danger)", fontSize: 12, marginBottom: 12 }}>{error}</div>
          )}

          {sources === null ? (
            <p style={{ fontSize: 13, color: "var(--text-secondary)" }}>Looking for launchers…</p>
          ) : sources.length === 0 ? (
            <GlassCard style={{ textAlign: "center", padding: 28 }}>
              <Icon
                name="folder"
                size={26}
                style={{ color: "var(--text-tertiary)", marginBottom: 10 }}
              />
              <p style={{ fontSize: 12.5, color: "var(--text-secondary)", margin: "0 0 4px" }}>
                No other launchers found in the usual places.
              </p>
              <p style={{ fontSize: 11, color: "var(--text-tertiary)", margin: 0 }}>
                If yours is installed somewhere else, point at it below.
              </p>
            </GlassCard>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 16, marginBottom: 16 }}>
              {sources.map((source) => (
                <SourceSection key={source.root} source={source} onPick={setPicked} />
              ))}
            </div>
          )}

          <div style={{ display: "flex", gap: 8, marginTop: 16 }}>
            <button onClick={pickFolder} style={{ display: "flex", alignItems: "center", gap: 6 }}>
              <Icon name="folder" size={13} />
              Choose a folder…
            </button>
            <button onClick={scan}>Rescan</button>
            <div style={{ flex: 1 }} />
            <button onClick={onClose}>Close</button>
          </div>
        </>
      )}
    </Shell>
  );
}

function SourceSection({
  source,
  onPick,
}: {
  source: ImportSource;
  onPick: (c: ImportCandidate) => void;
}) {
  return (
    <div>
      <div style={{ display: "flex", alignItems: "baseline", gap: 8, marginBottom: 8 }}>
        <span style={{ fontSize: 13, fontWeight: 600 }}>{source.launcher}</span>
        <span
          style={{
            fontSize: 10.5,
            color: "var(--text-tertiary)",
            fontFamily: "var(--font-mono)",
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
          }}
          title={source.root}
        >
          {source.root}
        </span>
      </div>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fill, minmax(230px, 1fr))",
          gap: 10,
        }}
      >
        {source.instances.map((c) => (
          <GlassCard key={c.id} onClick={() => onPick(c)} style={{ padding: 12 }}>
            <div
              style={{
                fontWeight: 600,
                fontSize: 12.5,
                marginBottom: 6,
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
              }}
              title={c.name}
            >
              {c.name}
            </div>
            <div style={{ display: "flex", gap: 5, flexWrap: "wrap", marginBottom: 8 }}>
              {c.mcVersion && <Chip>{c.mcVersion}</Chip>}
              <Chip>{c.loader}</Chip>
            </div>
            <div style={{ fontSize: 10.5, color: "var(--text-tertiary)", lineHeight: 1.5 }}>
              {summarise(c)}
            </div>
          </GlassCard>
        ))}
      </div>
    </div>
  );
}

/** One line describing what's actually in a candidate. */
function summarise(c: ImportCandidate): string {
  const bits: string[] = [];
  if (c.hasOptions) bits.push("settings");
  if (c.hasServers) bits.push("servers");
  if (c.resourcePacks) bits.push(`${c.resourcePacks} resource pack${c.resourcePacks === 1 ? "" : "s"}`);
  if (c.shaderPacks) bits.push(`${c.shaderPacks} shader pack${c.shaderPacks === 1 ? "" : "s"}`);
  if (c.mods) bits.push(`${c.mods} mod${c.mods === 1 ? "" : "s"}`);
  if (c.worlds) bits.push(`${c.worlds} world${c.worlds === 1 ? "" : "s"}`);
  return bits.length ? bits.join(" · ") : "Nothing importable in here";
}

function ConfigureImport({
  candidate,
  onBack,
  onDone,
}: {
  candidate: ImportCandidate;
  onBack: () => void;
  onDone: (r: ImportReport) => void;
}) {
  const [name, setName] = useState(candidate.name);
  const [mcVersion, setMcVersion] = useState(candidate.mcVersion);
  const [selection, setSelection] = useState<ImportSelection>(DEFAULT_IMPORT_SELECTION);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const set = (k: keyof ImportSelection, v: boolean) =>
    setSelection((s) => ({ ...s, [k]: v }));

  async function run() {
    setBusy(true);
    setError(null);
    try {
      onDone(await api.importInstance(candidate, name, mcVersion, selection));
    } catch (e) {
      setError(String(e));
      setBusy(false);
    }
  }

  const nothingChosen = !Object.values(selection).some(Boolean);

  return (
    <>
      <h2 style={{ marginTop: 0, fontSize: 16 }}>Import "{candidate.name}"</h2>

      <div style={{ display: "flex", gap: 10, marginBottom: 4 }}>
        <Field label="Name here">
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            style={inp}
            disabled={busy}
          />
        </Field>
        <Field label="Minecraft version">
          <input
            value={mcVersion}
            onChange={(e) => setMcVersion(e.target.value)}
            placeholder="e.g. 1.21.4"
            style={inp}
            disabled={busy}
          />
        </Field>
      </div>
      {!candidate.mcVersion && (
        <div style={{ fontSize: 11, color: "var(--warning)", marginBottom: 10, lineHeight: 1.5 }}>
          The source launcher didn't record a version, so this one is blank. Fill it in — the
          instance won't launch without it.
        </div>
      )}

      <div
        style={{
          fontSize: 10,
          color: "var(--text-tertiary)",
          textTransform: "uppercase",
          letterSpacing: "0.05em",
          margin: "12px 0 8px",
        }}
      >
        Bring across
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
        <Pick
          checked={selection.options}
          onChange={(v) => set("options", v)}
          available={candidate.hasOptions}
          label="Game settings (options.txt)"
          hint="Video options, keybinds, sound levels, chat settings. The one file that makes a new launcher feel like your old one."
        />
        <Pick
          checked={selection.servers}
          onChange={(v) => set("servers", v)}
          available={candidate.hasServers}
          label="Server list (servers.dat)"
          hint="Every server saved in your multiplayer menu."
        />
        <Pick
          checked={selection.resourcePacks}
          onChange={(v) => set("resourcePacks", v)}
          available={candidate.resourcePacks > 0}
          label={`Resource packs${candidate.resourcePacks ? ` (${candidate.resourcePacks})` : ""}`}
          hint="Texture packs, in the order options.txt already has them enabled."
        />
        <Pick
          checked={selection.shaderPacks}
          onChange={(v) => set("shaderPacks", v)}
          available={candidate.shaderPacks > 0}
          label={`Shader packs${candidate.shaderPacks ? ` (${candidate.shaderPacks})` : ""}`}
          hint="Needs Iris or OptiFine in the new instance to do anything."
        />
        <Pick
          checked={selection.config}
          onChange={(v) => set("config", v)}
          available={candidate.configFiles > 0}
          label={`Mod config${candidate.configFiles ? ` (${candidate.configFiles} files)` : ""}`}
          hint="Per-mod settings. Only useful alongside the mods themselves."
        />
        <Pick
          checked={selection.mods}
          onChange={(v) => set("mods", v)}
          available={candidate.mods > 0}
          label={`Mods${candidate.mods ? ` (${candidate.mods})` : ""}`}
          hint="Copied as files. They'll only load if this instance ends up on the same loader and version."
        />
        <Pick
          checked={selection.worlds}
          onChange={(v) => set("worlds", v)}
          available={candidate.worlds > 0}
          label={`Worlds${candidate.worlds ? ` (${candidate.worlds})` : ""}`}
          hint="A copy, not a move — the originals stay where they are. Can be large."
        />
      </div>

      {error && <div style={{ color: "var(--danger)", fontSize: 12, marginTop: 10 }}>{error}</div>}

      <div style={{ display: "flex", gap: 8, marginTop: 16 }}>
        <button onClick={onBack} disabled={busy}>
          Back
        </button>
        <div style={{ flex: 1 }} />
        <button className="accent" onClick={run} disabled={busy || nothingChosen}>
          {busy ? "Copying…" : "Import"}
        </button>
      </div>
    </>
  );
}

function ReportView({ report, onDone }: { report: ImportReport; onDone: () => void }) {
  return (
    <>
      <h2 style={{ marginTop: 0, fontSize: 16 }}>Imported "{report.name}"</h2>

      {report.copied.length > 0 && (
        <>
          <div style={{ fontSize: 12, color: "var(--success)", marginBottom: 6 }}>Copied</div>
          <ul style={{ margin: "0 0 14px", paddingLeft: 18, fontSize: 12, lineHeight: 1.7 }}>
            {report.copied.map((line) => (
              <li key={line}>{line}</li>
            ))}
          </ul>
        </>
      )}

      {report.skipped.length > 0 && (
        <>
          <div style={{ fontSize: 12, color: "var(--warning)", marginBottom: 6 }}>Skipped</div>
          <ul
            style={{
              margin: "0 0 14px",
              paddingLeft: 18,
              fontSize: 12,
              lineHeight: 1.7,
              color: "var(--text-secondary)",
            }}
          >
            {report.skipped.map((line) => (
              <li key={line}>{line}</li>
            ))}
          </ul>
        </>
      )}

      {report.copied.length === 0 && (
        <p style={{ fontSize: 12.5, color: "var(--text-secondary)" }}>
          The instance was created, but nothing was copied into it — none of the things you ticked
          were in the source.
        </p>
      )}

      <button className="accent" onClick={onDone} style={{ marginTop: 6 }}>
        Done
      </button>
    </>
  );
}

// ---------------------------------------------------------------------------

function Shell({
  children,
  onClose,
  wide,
}: {
  children: React.ReactNode;
  onClose: () => void;
  wide: boolean;
}) {
  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(0,10,16,0.6)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 200,
        padding: 24,
      }}
      onClick={onClose}
    >
      <div
        className="glass-card"
        style={{
          width: wide ? 760 : 520,
          maxWidth: "100%",
          maxHeight: "100%",
          overflowY: "auto",
          padding: 22,
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {children}
      </div>
    </div>
  );
}

function Pick({
  checked,
  onChange,
  available,
  label,
  hint,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  available: boolean;
  label: string;
  hint: string;
}) {
  return (
    <label
      style={{
        display: "flex",
        gap: 9,
        alignItems: "flex-start",
        padding: "8px 10px",
        border: `1px solid ${checked && available ? "var(--accent)" : "var(--glass-border)"}`,
        background: checked && available ? "var(--glass-bg-elevated)" : "transparent",
        // Not hidden when absent: knowing the source has no shader packs is
        // information, and a row that vanishes reads as a missing feature.
        opacity: available ? 1 : 0.42,
        cursor: available ? "pointer" : "not-allowed",
      }}
    >
      <input
        type="checkbox"
        checked={checked && available}
        disabled={!available}
        onChange={(e) => onChange(e.target.checked)}
        style={{ marginTop: 2 }}
      />
      <span>
        <div style={{ fontSize: 12.5 }}>
          {label}
          {!available && (
            <span style={{ color: "var(--text-tertiary)", fontSize: 10.5 }}> · not present</span>
          )}
        </div>
        <div style={{ fontSize: 10.5, color: "var(--text-tertiary)", lineHeight: 1.45 }}>{hint}</div>
      </span>
    </label>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ flex: 1, marginBottom: 10 }}>
      <label
        style={{
          fontSize: 11,
          color: "var(--text-secondary)",
          display: "block",
          marginBottom: 4,
        }}
      >
        {label}
      </label>
      {children}
    </div>
  );
}

function Chip({ children }: { children: React.ReactNode }) {
  return (
    <span
      style={{
        fontSize: 9.5,
        padding: "2px 6px",
        background: "rgba(125,226,240,0.1)",
        border: "1px solid var(--glass-border)",
        color: "var(--text-secondary)",
        textTransform: "capitalize",
      }}
    >
      {children}
    </span>
  );
}

const inp: React.CSSProperties = { width: "100%", padding: "7px 9px", fontSize: 12.5 };
