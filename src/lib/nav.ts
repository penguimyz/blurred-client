import type { IconName } from "../components/Icon";

/**
 * Every destination in the app, defined once.
 *
 * The rail's tooltip and the page's own heading both read from here, so a tab
 * can never be called one thing in the sidebar and something else once you're
 * on it. That drift is exactly what happened when the two were written
 * separately — the rail said "Discover" while the page said "Browse", and
 * "Logs" opened a screen headed "Crash log".
 *
 * Adding a destination means adding it here, listing its key in `PRIMARY` or
 * `UTILITY`, and rendering it in App.tsx. Nothing else needs a label.
 */

export type NavKey =
  | "home"
  | "browse"
  | "modpacks"
  | "servers"
  | "chat"
  | "cosmetics"
  | "logs"
  | "settings"
  | "accounts";

export interface NavItem {
  key: NavKey;
  /** The one true name for this destination. */
  label: string;
  /** One line under the heading, explaining what the screen is for. */
  blurb: string;
  icon: IconName;
}

export const NAV: Record<NavKey, NavItem> = {
  home: {
    key: "home",
    label: "Launchpad",
    blurb: "Your fleet of instances, and the one you're about to play.",
    icon: "home",
  },
  browse: {
    key: "browse",
    label: "Discover",
    blurb: "Search Modrinth and install mods, packs and shaders into an instance.",
    icon: "periscope",
  },
  modpacks: {
    key: "modpacks",
    label: "Modpacks",
    blurb: "Snapshot an instance's mod set, re-apply it, or share it as a file.",
    icon: "crate",
  },
  servers: {
    key: "servers",
    label: "Servers",
    blurb: "Host a Minecraft server on this machine and play on it with friends.",
    icon: "server",
  },
  chat: {
    key: "chat",
    label: "Sonar",
    blurb: "Chat and crew presence, without leaving the launcher.",
    icon: "sonar",
  },
  cosmetics: {
    key: "cosmetics",
    label: "Cosmetics",
    blurb: "Draw a cape, or change the skin on your Microsoft account.",
    icon: "anchor",
  },
  logs: {
    key: "logs",
    label: "Logs",
    blurb: "Sessions that ended badly, kept so they survive restarting the launcher.",
    icon: "log",
  },
  settings: {
    key: "settings",
    label: "Settings",
    blurb: "Java defaults, chat, updates and ambience.",
    icon: "gauge",
  },
  accounts: {
    key: "accounts",
    label: "Accounts",
    blurb: "Microsoft and offline accounts. Launch uses the active one.",
    icon: "diver",
  },
};

/** Top of the rail, in order. */
export const PRIMARY: NavKey[] = ["home", "browse", "modpacks", "servers", "chat", "cosmetics"];

/** Pinned above the account tile. */
export const UTILITY: NavKey[] = ["logs", "settings"];

export function navLabel(key: NavKey): string {
  return NAV[key].label;
}
