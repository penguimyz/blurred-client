// Mirrors src-tauri/src/models/instance.rs. No codegen (e.g. ts-rs) wired
// up yet -- if you add/rename a field on the Rust side, update this file by
// hand or things will silently desync. Worth adding ts-rs before this
// grows much further.

export type Loader = "vanilla" | "fabric" | "forge" | "quilt" | "neoforge";

export interface Overridable<T> {
  enabled: boolean;
  value: T;
}

export interface JavaSettings {
  executablePath: string | null;
  minMemoryMb: number;
  maxMemoryMb: number;
  jvmArgs: string;
}

export interface EnvVars {
  vars: [string, string][];
}

export interface CustomCommands {
  preLaunch: string | null;
  wrapper: string | null;
  postExit: string | null;
}

export type ModSource = "modrinth" | "local";

export interface ModRef {
  id: string;
  filename: string;
  name: string;
  version: string;
  enabled: boolean;
  source: ModSource;
  pinned: boolean;
  versionId: string | null; // Modrinth version id of the installed file
}

export interface ModUpdate {
  filename: string;
  name: string;
  currentVersion: string;
  latestVersion: string;
  latestVersionId: string;
}

export interface Instance {
  id: string;
  name: string;
  iconPath: string | null;
  mcVersion: string;
  loader: Loader;
  loaderVersion: string | null;

  createdAt: string; // ISO 8601
  lastPlayed: string | null;
  totalPlaytimeSeconds: number;

  javaOverride: Overridable<JavaSettings>;
  envVarsOverride: Overridable<EnvVars>;
  customCommandsOverride: Overridable<CustomCommands>;

  accountId: string | null;
  mods: ModRef[];
  notes: string;

  windowWidth: number;
  windowHeight: number;
}

export interface DetectedJava {
  path: string;
  version: string;
  majorVersion: number | null;
}

// ---- per-instance content (mirrors commands/config.rs + content.rs) ----

export type ConfigFormat = "json" | "toml" | "properties" | "text";

export interface ConfigFile {
  relPath: string;
  format: ConfigFormat;
  size: number;
}

export interface WorldInfo {
  name: string;
  sizeBytes: number;
  modified: string | null; // ISO 8601
}

export interface ScreenshotInfo {
  name: string;
  sizeBytes: number;
  modified: string | null; // ISO 8601
}

// ---- modpacks (mirrors models/modpack.rs) ----

export interface Modpack {
  id: string;
  name: string;
  description: string;
  mcVersion: string;
  loader: Loader;
  mods: ModRef[];
  createdAt: string; // ISO 8601
}
