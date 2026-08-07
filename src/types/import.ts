// Mirrors src-tauri/src/commands/import.rs.

import type { Loader } from "./instance";

/** One other launcher found on this machine. */
export interface ImportSource {
  launcher: string;
  root: string;
  instances: ImportCandidate[];
}

/** One importable game directory, normalised across launchers. */
export interface ImportCandidate {
  /** Absolute path to the game directory; also its identity. */
  id: string;
  name: string;
  /** Empty when the source launcher didn't record one. */
  mcVersion: string;
  loader: Loader;
  loaderVersion: string | null;

  hasOptions: boolean;
  hasServers: boolean;
  configFiles: number;
  resourcePacks: number;
  shaderPacks: number;
  mods: number;
  worlds: number;
}

/** Which pieces of a candidate to copy. Everything is opt-in. */
export interface ImportSelection {
  options: boolean;
  servers: boolean;
  resourcePacks: boolean;
  shaderPacks: boolean;
  config: boolean;
  mods: boolean;
  worlds: boolean;
}

export interface ImportReport {
  instanceId: string;
  name: string;
  copied: string[];
  skipped: string[];
}

/**
 * The default tick-boxes.
 *
 * Options, servers and resource packs are on because they're what "bring my
 * setup across" means and they're all small. Worlds are off: they can be tens
 * of gigabytes, and duplicating someone's survival save without being asked is
 * not a default. Mods are off because they're tied to a loader version that
 * may not match what the new instance ends up on.
 */
export const DEFAULT_IMPORT_SELECTION: ImportSelection = {
  options: true,
  servers: true,
  resourcePacks: true,
  shaderPacks: false,
  config: false,
  mods: false,
  worlds: false,
};
