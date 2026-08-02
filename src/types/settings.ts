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
  updateRepo: string; // GitHub "owner/name" for launcher update checks ("" disables)

  // Chat (Sonar). IRC endpoint; empty values fall back to the backend defaults.
  chatServer: string;
  chatPort: number;
  chatChannel: string;
  chatAutoConnect: boolean;

  // The cursor-following school of fish. Off by default.
  fishEnabled: boolean;
  // Ambient sea life drifting behind the glass. On by default.
  seaLifeEnabled: boolean;
}
