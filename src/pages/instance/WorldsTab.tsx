import { useEffect, useState } from "react";
import type { TabProps } from "./InstanceDetail";
import type { WorldInfo } from "../../types/instance";
import { DataTable, type Column } from "../../components/DataTable";
import { deleteWorld, listWorlds } from "../../lib/tauri";
import { formatBytes, formatDate } from "../../lib/format";

// Worlds/Saves tab (spec §4.2). Lists the saves/ folder with size + last-
// modified and offers deletion. Backup/export would reuse the same modpack-style
// bundling; kept out for now to stay within the offline scope.

export function WorldsTab({ instance }: TabProps) {
  const [worlds, setWorlds] = useState<WorldInfo[]>([]);
  const [error, setError] = useState<string | null>(null);

  function refresh() {
    listWorlds(instance.id).then(setWorlds).catch((e) => setError(String(e)));
  }
  useEffect(refresh, [instance.id]);

  const columns: Column<WorldInfo>[] = [
    { key: "name", header: "World", render: (w) => <span style={{ fontWeight: 500 }}>{w.name}</span> },
    { key: "sizeBytes", header: "Size", mono: true, render: (w) => formatBytes(w.sizeBytes) },
    { key: "modified", header: "Last modified", render: (w) => formatDate(w.modified) },
    {
      key: "actions",
      header: "",
      align: "right",
      render: (w) => (
        <button
          onClick={async () => {
            if (confirm(`Delete world "${w.name}"? This permanently removes the save folder.`)) {
              try {
                await deleteWorld(instance.id, w.name);
                refresh();
              } catch (e) {
                setError(String(e));
              }
            }
          }}
          style={ghostBtn}
        >
          Delete
        </button>
      ),
    },
  ];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
      {error && <div style={{ color: "var(--danger)", fontSize: 13 }}>{error}</div>}
      <DataTable
        columns={columns}
        rows={worlds}
        rowKey={(w) => w.name}
        empty="No worlds yet. Singleplayer worlds you create appear here."
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
