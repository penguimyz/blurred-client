const NAV_ITEMS = [
  { label: "Home", key: "home" },
  { label: "Browse", key: "browse" },
  { label: "Modpacks", key: "modpacks" },
  { label: "Accounts", key: "accounts" },
  { label: "Settings", key: "settings" },
  { label: "Logs", key: "logs" },
];

// Every global tab now routes to a real page (see App.tsx). Selecting one also
// clears any open instance-detail view so the sidebar always returns to the
// top level. Keep new global destinations registered here.
export function Sidebar({
  active,
  onSelect,
}: {
  active: string;
  onSelect: (key: string) => void;
}) {
  return (
    <div
      className="glass-card"
      style={{
        width: 200,
        height: "100%",
        borderRadius: 0,
        borderTop: "none",
        borderBottom: "none",
        borderLeft: "none",
        display: "flex",
        flexDirection: "column",
        padding: 16,
      }}
    >
      <div style={{ fontWeight: 700, fontSize: 15, marginBottom: 24, paddingLeft: 8 }}>
        Blurred Client
      </div>
      {NAV_ITEMS.map((item) => (
        <button
          key={item.key}
          onClick={() => onSelect(item.key)}
          style={{
            background: active === item.key ? "var(--glass-bg-elevated)" : "transparent",
            border: "none",
            color: active === item.key ? "var(--text-primary)" : "var(--text-secondary)",
            textAlign: "left",
            padding: "10px 12px",
            borderRadius: 8,
            marginBottom: 4,
            cursor: "pointer",
            fontSize: 13,
          }}
        >
          {item.label}
        </button>
      ))}
    </div>
  );
}
