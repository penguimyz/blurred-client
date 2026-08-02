import { useEffect, useState, type ReactNode } from "react";
import type {
  CustomCommands,
  DetectedJava,
  EnvVars,
  JavaSettings,
  Overridable,
} from "../types/instance";
import { listDetectedJava } from "../lib/tauri";
import { MonoField } from "./MonoField";

// The Phase 5 payoff: the three field forms below (Java / Env Vars / Custom
// Commands) are authored once and consumed by BOTH the global Settings screen
// and the per-instance Settings tab. The global screen renders them directly on
// the default values; the instance screen wraps each in <OverrideSection>,
// which adds the "override global default" toggle and greys the form out when
// the instance is inheriting. That override toggle IS the Overridable<T>.enabled
// flag from the Rust model -- global default vs. per-instance override, spec §7.

const labelStyle = { fontSize: 12, color: "var(--text-secondary)", display: "block", marginBottom: 4 };
const rowGap = { display: "flex", flexDirection: "column" as const, gap: 14 };

function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <div>
      <label style={labelStyle}>{label}</label>
      {children}
      {hint && (
        <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginTop: 4 }}>{hint}</div>
      )}
    </div>
  );
}

// ---- Java ----

export function JavaForm({
  value,
  onChange,
  disabled,
}: {
  value: JavaSettings;
  onChange: (v: JavaSettings) => void;
  disabled?: boolean;
}) {
  const [detected, setDetected] = useState<DetectedJava[]>([]);

  useEffect(() => {
    listDetectedJava().then(setDetected).catch(() => setDetected([]));
  }, []);

  const set = <K extends keyof JavaSettings>(k: K, v: JavaSettings[K]) =>
    onChange({ ...value, [k]: v });

  return (
    <fieldset disabled={disabled} style={{ border: "none", padding: 0, margin: 0, ...rowGap }}>
      <Field
        label="Java executable"
        hint={
          detected.length > 0
            ? `${detected.length} JVM${detected.length > 1 ? "s" : ""} auto-detected — pick one or type a path.`
            : "No JVMs auto-detected. Enter the full path to javaw.exe / java."
        }
      >
        <MonoField
          value={value.executablePath ?? ""}
          onChange={(v) => set("executablePath", v.trim() === "" ? null : v)}
          placeholder="(auto-detect / not set)"
        />
        {detected.length > 0 && (
          <select
            value=""
            onChange={(e) => e.target.value && set("executablePath", e.target.value)}
            style={{ ...selectStyle, marginTop: 6 }}
          >
            <option value="">Use a detected JVM…</option>
            {detected.map((j) => (
              <option key={j.path} value={j.path}>
                {j.majorVersion ? `Java ${j.majorVersion}` : j.version} — {j.path}
              </option>
            ))}
          </select>
        )}
      </Field>

      <div style={{ display: "flex", gap: 14 }}>
        <Field label="Min memory (MB)">
          <input
            type="number"
            min={256}
            step={256}
            value={value.minMemoryMb}
            onChange={(e) => set("minMemoryMb", Number(e.target.value))}
            style={numberStyle}
          />
        </Field>
        <Field label="Max memory (MB)">
          <input
            type="number"
            min={512}
            step={256}
            value={value.maxMemoryMb}
            onChange={(e) => set("maxMemoryMb", Number(e.target.value))}
            style={numberStyle}
          />
        </Field>
      </div>

      <Field label="Custom JVM arguments" hint="Space-separated, e.g. -XX:+UseG1GC -Dfoo=bar">
        <MonoField
          value={value.jvmArgs}
          onChange={(v) => set("jvmArgs", v)}
          multiline
          rows={2}
          copyable={false}
          placeholder="(none)"
        />
      </Field>
    </fieldset>
  );
}

// ---- Environment variables ----

export function EnvVarsForm({
  value,
  onChange,
  disabled,
}: {
  value: EnvVars;
  onChange: (v: EnvVars) => void;
  disabled?: boolean;
}) {
  const vars = value.vars;
  const setPair = (i: number, idx: 0 | 1, v: string) => {
    const next = vars.map((pair, j) => (j === i ? (idx === 0 ? [v, pair[1]] : [pair[0], v]) : pair)) as [
      string,
      string,
    ][];
    onChange({ vars: next });
  };
  const add = () => onChange({ vars: [...vars, ["", ""]] });
  const remove = (i: number) => onChange({ vars: vars.filter((_, j) => j !== i) });

  return (
    <fieldset disabled={disabled} style={{ border: "none", padding: 0, margin: 0, ...rowGap }}>
      {vars.length === 0 && (
        <div style={{ fontSize: 12, color: "var(--text-tertiary)" }}>No environment variables set.</div>
      )}
      {vars.map((pair, i) => (
        <div key={i} style={{ display: "flex", gap: 8, alignItems: "center" }}>
          <MonoField value={pair[0]} onChange={(v) => setPair(i, 0, v)} placeholder="KEY" copyable={false} />
          <span style={{ color: "var(--text-tertiary)" }}>=</span>
          <MonoField value={pair[1]} onChange={(v) => setPair(i, 1, v)} placeholder="value" copyable={false} />
          <button onClick={() => remove(i)} style={ghostBtn} title="Remove">
            ✕
          </button>
        </div>
      ))}
      <div>
        <button onClick={add} style={ghostBtn}>
          + Add variable
        </button>
      </div>
    </fieldset>
  );
}

// ---- Custom commands (Prism's three-hook system) ----

export function CustomCommandsForm({
  value,
  onChange,
  disabled,
}: {
  value: CustomCommands;
  onChange: (v: CustomCommands) => void;
  disabled?: boolean;
}) {
  const set = (k: keyof CustomCommands, v: string) =>
    onChange({ ...value, [k]: v.trim() === "" ? null : v });

  return (
    <fieldset disabled={disabled} style={{ border: "none", padding: 0, margin: 0, ...rowGap }}>
      <Field label="Pre-launch command" hint="Runs before Minecraft starts.">
        <MonoField value={value.preLaunch ?? ""} onChange={(v) => set("preLaunch", v)} copyable={false} placeholder="(none)" />
      </Field>
      <Field label="Wrapper command" hint="Wraps the launch, e.g. a prefix like `prime-run`.">
        <MonoField value={value.wrapper ?? ""} onChange={(v) => set("wrapper", v)} copyable={false} placeholder="(none)" />
      </Field>
      <Field label="Post-exit command" hint="Runs after Minecraft exits.">
        <MonoField value={value.postExit ?? ""} onChange={(v) => set("postExit", v)} copyable={false} placeholder="(none)" />
      </Field>
    </fieldset>
  );
}

// ---- Override wrapper (per-instance only) ----

/**
 * Wraps any of the forms above with the global-default vs. per-instance-override
 * control. When the toggle is off, the instance inherits the global default and
 * the inner form is shown greyed-out (still visible, so the user can see what
 * they'd be overriding — Prism does the same).
 */
export function OverrideSection<T>({
  title,
  override,
  onChange,
  children,
}: {
  title: string;
  override: Overridable<T>;
  onChange: (o: Overridable<T>) => void;
  /** Render prop receives the effective value + a disabled flag. */
  children: (value: T, disabled: boolean, setValue: (v: T) => void) => ReactNode;
}) {
  return (
    <div style={{ marginBottom: 20 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <h3 style={{ margin: 0, fontSize: 14 }}>{title}</h3>
        <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 12, color: "var(--text-secondary)", cursor: "pointer" }}>
          <input
            type="checkbox"
            checked={override.enabled}
            onChange={(e) => onChange({ ...override, enabled: e.target.checked })}
          />
          Override global default
        </label>
      </div>
      <div style={{ opacity: override.enabled ? 1 : 0.45, transition: "opacity 150ms ease" }}>
        {children(override.value, !override.enabled, (v) => onChange({ ...override, value: v }))}
      </div>
    </div>
  );
}

const numberStyle = {
  width: "100%",
  padding: "8px 10px",
  borderRadius: "var(--radius-sm)",
  border: "1px solid var(--glass-border)",
  backgroundColor: "rgba(0,20,30,0.3)",
  color: "var(--text-primary)",
  fontSize: 13,
};

const selectStyle = {
  width: "100%",
  padding: "8px 10px",
  borderRadius: "var(--radius-sm)",
  border: "1px solid var(--glass-border)",
  backgroundColor: "rgba(0,20,30,0.3)",
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
  cursor: "pointer",
};
