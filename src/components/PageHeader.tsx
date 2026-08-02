import type { ReactNode } from "react";
import { Icon } from "./Icon";
import { NAV, type NavKey } from "../lib/nav";

/**
 * Every page's heading.
 *
 * Takes a nav key rather than a title string, so the name shown here is
 * literally the same value the rail's tooltip uses (see `lib/nav.ts`). A page
 * cannot be headed something different from the tab that opened it, because
 * neither one owns a label of its own.
 */
export function PageHeader({
  page,
  actions,
}: {
  page: NavKey;
  /** Buttons for the right-hand side of the header row. */
  actions?: ReactNode;
}) {
  const { label, blurb, icon } = NAV[page];

  return (
    <div style={{ marginBottom: 20 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
        <Icon name={icon} size={18} style={{ color: "var(--accent)" }} />
        <h1 style={{ margin: 0, fontSize: 17, flex: 1 }}>{label}</h1>
        {actions}
      </div>
      <div
        style={{
          fontSize: 12,
          color: "var(--text-secondary)",
          marginTop: 6,
          lineHeight: 1.5,
        }}
      >
        {blurb}
      </div>
    </div>
  );
}
