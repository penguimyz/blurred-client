import { useState, type CSSProperties } from "react";

// Monospace field for technical/exact values (file paths, JVM args, version
// strings, raw config values) -- spec §2.2 mandates monospacing these so they
// read as exact, copyable data. Two modes on one component: read-only display
// with a copy affordance, or an editable input/textarea. Both use the
// .mono-field token class from theme.css.

interface MonoFieldProps {
  value: string;
  onChange?: (value: string) => void; // omit for a read-only field
  placeholder?: string;
  multiline?: boolean;
  copyable?: boolean; // show a copy button (read-only fields default to true)
  rows?: number;
  style?: CSSProperties;
  spellCheck?: boolean;
}

export function MonoField({
  value,
  onChange,
  placeholder,
  multiline,
  copyable,
  rows = 4,
  style,
  spellCheck = false,
}: MonoFieldProps) {
  const [copied, setCopied] = useState(false);
  const readOnly = !onChange;
  const showCopy = copyable ?? readOnly;

  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 1200);
    } catch {
      /* clipboard blocked; nothing actionable to show */
    }
  }

  const field = multiline ? (
    <textarea
      className="mono-field"
      value={value}
      onChange={onChange ? (e) => onChange(e.target.value) : undefined}
      placeholder={placeholder}
      readOnly={readOnly}
      spellCheck={spellCheck}
      rows={rows}
      style={{ width: "100%", resize: "vertical", ...style }}
    />
  ) : (
    <input
      className="mono-field"
      value={value}
      onChange={onChange ? (e) => onChange(e.target.value) : undefined}
      placeholder={placeholder}
      readOnly={readOnly}
      spellCheck={spellCheck}
      style={{ width: "100%", ...style }}
    />
  );

  if (!showCopy) return field;

  return (
    <div style={{ display: "flex", gap: 6, alignItems: multiline ? "flex-start" : "center" }}>
      <div style={{ flex: 1, minWidth: 0 }}>{field}</div>
      <button
        onClick={copy}
        title="Copy"
        style={{
          background: "transparent",
          border: "1px solid var(--glass-border)",
          borderRadius: "var(--radius-sm)",
          color: "var(--text-secondary)",
          padding: "6px 10px",
          fontSize: 11,
          cursor: "pointer",
          whiteSpace: "nowrap",
        }}
      >
        {copied ? "Copied" : "Copy"}
      </button>
    </div>
  );
}
