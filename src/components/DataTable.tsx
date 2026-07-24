import type { CSSProperties, ReactNode } from "react";

// The Prism depth-layer table primitive, counterpart to GlassCard on the
// surface layer. Dense, structured, no hover-lift theatrics -- built for
// scanning exact values (mods, configs, worlds, detected JVMs). Reads the same
// glass tokens as everything else (via the .data-table class in theme.css) so
// it stays visually one product with the surface layer while being far denser.

export interface Column<T> {
  key: string;
  header: ReactNode;
  /** Cell renderer; defaults to String(row[key]). */
  render?: (row: T) => ReactNode;
  width?: number | string;
  /** Right-align + monospace, for exact/technical values. */
  mono?: boolean;
  align?: "left" | "right" | "center";
}

interface DataTableProps<T> {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  empty?: ReactNode;
  style?: CSSProperties;
}

export function DataTable<T>({ columns, rows, rowKey, empty, style }: DataTableProps<T>) {
  if (rows.length === 0 && empty) {
    return (
      <div
        className="data-table"
        style={{ padding: 24, textAlign: "center", color: "var(--text-secondary)", ...style }}
      >
        {empty}
      </div>
    );
  }

  return (
    <div style={{ overflowX: "auto", ...style }}>
      <table className="data-table" style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead>
          <tr>
            {columns.map((col) => (
              <th
                key={col.key}
                style={{
                  width: col.width,
                  textAlign: col.align ?? (col.mono ? "right" : "left"),
                  color: "var(--text-secondary)",
                  fontWeight: 600,
                  fontSize: 12,
                  whiteSpace: "nowrap",
                }}
              >
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={rowKey(row)}>
              {columns.map((col) => (
                <td
                  key={col.key}
                  style={{
                    textAlign: col.align ?? (col.mono ? "right" : "left"),
                    fontFamily: col.mono ? "var(--font-mono)" : undefined,
                    fontSize: col.mono ? 12.5 : undefined,
                    color: col.mono ? "var(--text-primary)" : undefined,
                  }}
                >
                  {col.render ? col.render(row) : String((row as Record<string, unknown>)[col.key])}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
