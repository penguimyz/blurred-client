// Mirrors src-tauri/src/models/chat.rs and the event structs in
// src-tauri/src/commands/chat.rs. Same manual-sync caveat as the other type
// mirrors -- no codegen wired up.

/** Where a crew relationship stands. See models/chat.rs for the handshake. */
export type FriendStatus = "pendingOut" | "pendingIn" | "accepted";

export interface Friend {
  nick: string;
  /** User's own label, or — for an incoming request — the sender's greeting. */
  note: string;
  addedAt: string;
  online: boolean;
  status: FriendStatus;
}

/** Emitted when an inbound message changed the friends list. */
export interface FriendsChangedEvent {
  friends: Friend[];
  kind: "request" | "accepted" | "declined";
  nick: string;
}

export type MessageKind = "message" | "action" | "notice" | "system";

export interface ChatMessage {
  /** Channel name for channel traffic, the other party's nick for a DM, or
   *  "*" for server notices that belong to no conversation. */
  conversation: string;
  from: string;
  text: string;
  kind: MessageKind;
  ts: string;
  mine: boolean;
}

export interface ChatStatus {
  connected: boolean;
  nick: string;
  channels: string[];
  server: string;
  defaultChannel: string;
}

export interface ChatStatusEvent {
  connected: boolean;
  nick: string;
  error: string | null;
}

export interface ChatPresenceEvent {
  nick: string;
  online: boolean;
}

export interface ChatNamesEvent {
  channel: string;
  users: string[];
}
