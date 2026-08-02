// Mirrors src-tauri/src/models/crash.rs.

export interface CrashReport {
  id: string;
  instanceId: string;
  instanceName: string;
  mcVersion: string;
  loader: string;
  exitCode: number;
  occurredAt: string;
  /** Absolute path to the full session log on disk. */
  logPath: string;
  /** Best-effort one-line explanation, or a plain statement of the exit code
   *  when nothing recognisable matched. Never a guess. */
  summary: string;
  /** Trailing lines of the session log, oldest first. */
  tail: string[];
}
