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

  // World and performance.
  viewDistance: number;
  simulationDistance: number;
  spawnProtection: number;
  /** World folder name. Changing it starts a different world, not a rename. */
  levelName: string;
  /** Blank means "let the game pick one". */
  levelSeed: string;

  // Rules.
  hardcore: boolean;
  allowNether: boolean;
  allowFlight: boolean;
  enableCommandBlock: boolean;
  forceGamemode: boolean;
  /** Only whitelisted players may join; the list lives on the Players tab. */
  whiteList: boolean;

  // Launcher behaviour.
  autoRestart: boolean;
  backupOnStart: boolean;
}

export interface ServerStatus {
  id: string;
  running: boolean;
  /** True once the log reports "Done" — i.e. it's accepting players. */
  ready: boolean;
  lanAddress: string | null;
  /** Who's connected right now, tracked from the console log. */
  players: string[];
  uptimeSeconds: number;
}

/** Which of the three access lists an operation applies to. */
export type PlayerList = "ops" | "whitelist" | "banned";

export interface ServerPlayer {
  name: string;
  /** Dashed Mojang UUID; empty if it couldn't be resolved. */
  uuid: string;
  /** Operator level 1–4, ops list only. */
  level: number | null;
  /** Ban reason, ban list only. */
  reason: string | null;
}

export interface ServerPlayers {
  ops: ServerPlayer[];
  whitelist: ServerPlayer[];
  banned: ServerPlayer[];
}

export interface ServerBackup {
  /** Filename inside the server's backups/ folder; also its identity. */
  file: string;
  createdAt: string;
  sizeBytes: number;
}
