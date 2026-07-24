// Mirrors src-tauri/src/models/settings.rs. Same manual-sync caveat as the
// other type mirrors -- no codegen wired up.

import type { CustomCommands, EnvVars, JavaSettings } from "./instance";

export type Theme = "light" | "dark" | "system";

export interface GlobalSettings {
  theme: Theme;
  accentColor: string; // hex, e.g. "#7C9CFF"
  defaultJava: JavaSettings;
  defaultEnvVars: EnvVars;
  defaultCustomCommands: CustomCommands;
  instanceStoragePath: string;
  updateCheckFrequencyMinutes: number;
  msaClientId: string; // Azure app (client) ID for Microsoft sign-in
  // Dev-only escape hatch for the offline anti-piracy gate. Not shown in the
  // Settings UI (edited by hand in settings.json); default false. See the Rust
  // GlobalSettings field of the same name.
  allowOfflineWithoutMsa: boolean;
}
