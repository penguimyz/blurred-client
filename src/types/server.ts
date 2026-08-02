// Mirrors src-tauri/src/models/server.rs.

import type { Loader } from "./instance";

export interface Server {
  id: string;
  name: string;
  mcVersion: string;
  loader: Loader;
  port: number;
  maxMemoryMb: number;
  createdAt: string;
  lastStarted: string | null;
  /** The server refuses to start until this is true. */
  eulaAccepted: boolean;

  // The subset of server.properties the UI exposes. Anything else stays in the
  // file for hand-editing and is preserved on save.
  motd: string;
  maxPlayers: number;
  gamemode: string;
  difficulty: string;
  onlineMode: boolean;
  pvp: boolean;
}

export interface ServerStatus {
  id: string;
  running: boolean;
  /** True once the log reports "Done" — i.e. it's accepting players. */
  ready: boolean;
  lanAddress: string | null;
}
