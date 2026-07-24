import type { ReactNode, CSSProperties } from "react";

interface GlassCardProps {
  children: ReactNode;
  onClick?: () => void;
  style?: CSSProperties;
  className?: string;
}

/**
 * The Lunar-style surface-layer primitive: translucent, blurred, rounded,
 * lifts slightly on hover. Use this for Home, instance grid, onboarding --
 * anywhere the spec calls for "premium consumer product" polish.
 *
 * Do NOT reach for this on Settings/Logs/Configs screens -- those are the
 * Prism depth layer and should use .data-table / .mono-field instead
 * (see theme.css). Mixing the two on the same screen is the thing the
 * spec explicitly warns against ("never blend the two styles into a
 * compromise").
 */
export function GlassCard({ children, onClick, style, className }: GlassCardProps) {
  return (
    <div
      className={`glass-card ${className ?? ""}`}
      onClick={onClick}
      style={{
        padding: 20,
        cursor: onClick ? "pointer" : "default",
        ...style,
      }}
    >
      {children}
    </div>
  );
}
