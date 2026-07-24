// Mirrors src-tauri/src/models/account.rs. Same manual-sync caveat as
// types/instance.ts -- no codegen wired up.

export type AccountType = "offline" | "microsoft";

export interface Account {
  id: string;
  accountType: AccountType;
  mcUuid: string; // real MC UUID (Microsoft) or derived offline UUID
  username: string;
  skinUrl: string | null; // populated for Microsoft accounts, null for offline
  addedAt: string;
  lastUsed: string | null;
}

// Mirrors online_auth::DeviceCodeInfo — the device-code prompt shown mid-login.
export interface DeviceCodeInfo {
  userCode: string;
  deviceCode: string;
  verificationUri: string;
  expiresIn: number;
  interval: number;
  message: string;
}
