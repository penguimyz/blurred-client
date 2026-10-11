import { useEffect, useState } from "react";
import { listMcVersions } from "../lib/tauri";

/**
 * A Minecraft version field that offers the real list.
 *
 * # Why it falls back to a text box
 *
 * The list comes from Mojang's manifest over the network, and the launcher has
 * to keep working without it — someone offline, or behind something that blocks
 * piston-meta, still needs to be able to say which version they want. So a
 * failed fetch degrades to the free-text input this replaced rather than
 * leaving an empty dropdown and no way forward.
 *
 * The same applies while it's loading: the field is usable immediately, and
 * becomes a picker when the list lands.
 *
 * # Snapshots are opt-in
 *
 * They're in the manifest and vastly outnumber releases, so an unfiltered list
 * puts `25w31a` above the version nearly everyone wants. The checkbox is there
 * for the people who do want one.
 */
export function McVersionPicker({
  value,
  onChange,
  disabled = false,
  style,
}: {
  value: string;
  onChange: (version: string) => void;
  disabled?: boolean;
  style?: React.CSSProperties;
}) {
  const [versions, setVersions] = useState<string[] | null>(null);
  const [snapshots, setSnapshots] = useState(false);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setFailed(false);
    listMcVersions(snapshots)
      .then((list) => {
        if (!cancelled) setVersions(list);
      })
      .catch(() => {
        if (!cancelled) {
          setVersions(null);
          setFailed(true);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [snapshots]);

  const field: React.CSSProperties = {
    width: "100%",
    padding: "7px 9px",
    fontSize: 12.5,
    ...style,
  };

  return (
    <div>
      {versions ? (
        <select
          value={value}
          onChange={(e) => onChange(e.target.value)}
          disabled={disabled}
          style={field}
        >
          {/* A version the list doesn't have — a snapshot while the filter is
              off, or one typed in before — must still be selectable, or opening
              this picker would silently change it. */}
          {!versions.includes(value) && value && <option value={value}>{value} (current)</option>}
          {versions.map((v) => (
            <option key={v} value={v}>
              {v}
            </option>
          ))}
        </select>
      ) : (
        <input
          value={value}
          onChange={(e) => onChange(e.target.value)}
          disabled={disabled}
          placeholder="e.g. 1.21.11"
          style={field}
        />
      )}

      <label
        style={{
          display: "flex",
          gap: 6,
          alignItems: "center",
          fontSize: 10.5,
          color: "var(--text-tertiary)",
          marginTop: 5,
          cursor: disabled ? "default" : "pointer",
        }}
      >
        <input
          type="checkbox"
          checked={snapshots}
          disabled={disabled}
          onChange={(e) => setSnapshots(e.target.checked)}
        />
        Include snapshots
        {failed && (
          <span style={{ color: "var(--warning)" }}>
            · couldn't reach Mojang's list — type a version instead
          </span>
        )}
      </label>
    </div>
  );
}
