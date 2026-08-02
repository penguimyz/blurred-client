import { create } from "zustand";
import type { ChatMessage, Friend } from "../types/chat";
import * as api from "../lib/tauri";

/**
 * Chat state. Messages live here and only here — the backend streams events and
 * keeps nothing, so this store is the single scrollback buffer for the session.
 * That's deliberate: chat history is never written to disk (only the friends
 * list is), so closing the launcher genuinely forgets it.
 *
 * Conversations are keyed by channel name for channel traffic and by the other
 * party's nick for DMs, exactly as the backend keys them. Keys are compared
 * case-insensitively via `convKey` because IRC treats nicks and channels that
 * way, and a server that echoes "#Blurred-Client" must not open a second tab
 * next to "#blurred-client".
 */

const MAX_MESSAGES_PER_CONVERSATION = 500;

/** IRC is case-insensitive; normalize so casing never forks a conversation. */
function convKey(name: string): string {
  return name.toLowerCase();
}

interface Conversation {
  /** Display name, in the casing it was first seen in. */
  name: string;
  messages: ChatMessage[];
  unread: number;
  /** Channel member roster; empty for DMs. */
  users: string[];
  isChannel: boolean;
}

interface ChatStore {
  connected: boolean;
  connecting: boolean;
  nick: string;
  error: string | null;
  friends: Friend[];
  conversations: Record<string, Conversation>;
  /** convKey of the conversation currently on screen; drives unread clearing. */
  activeKey: string | null;
  /** True once the event listeners are attached, so we only wire them once. */
  wired: boolean;

  wire: () => Promise<void>;
  connect: (nick: string) => Promise<void>;
  disconnect: () => Promise<void>;
  send: (conversation: string, text: string) => Promise<void>;
  openConversation: (name: string) => void;
  closeConversation: (name: string) => void;
  setActive: (name: string | null) => void;
  joinChannel: (channel: string) => Promise<void>;
  refreshFriends: () => Promise<void>;
  addFriend: (nick: string, note: string) => Promise<void>;
  acceptFriend: (nick: string) => Promise<void>;
  declineFriend: (nick: string) => Promise<void>;
  removeFriend: (nick: string) => Promise<void>;

  /** Mutual friends only — the ones with live presence. */
  crew: () => Friend[];
  /** Requests waiting on us to answer. */
  incoming: () => Friend[];
  /** Requests we've sent that haven't been answered. */
  outgoing: () => Friend[];
  /** Unread messages plus unanswered requests — what the rail pip counts. */
  totalUnread: () => number;
}

function emptyConversation(name: string): Conversation {
  return {
    name,
    messages: [],
    unread: 0,
    users: [],
    isChannel: name.startsWith("#"),
  };
}

export const useChatStore = create<ChatStore>((set, get) => ({
  connected: false,
  connecting: false,
  nick: "",
  error: null,
  friends: [],
  conversations: {},
  activeKey: null,
  wired: false,

  wire: async () => {
    if (get().wired) return;
    // Set immediately, not after the awaits: two components mounting in the
    // same tick would otherwise both pass the guard and double-subscribe,
    // which shows every message twice.
    set({ wired: true });

    await api.onChatMessage((m) => {
      const key = convKey(m.conversation);
      set((s) => {
        // Server-wide notices have no conversation of their own; show them in
        // whatever is on screen so they aren't silently dropped.
        const targetKey = m.conversation === "*" ? s.activeKey : key;
        if (!targetKey) return s;

        const existing = s.conversations[targetKey] ?? emptyConversation(m.conversation);
        const messages = [...existing.messages, m];
        // Trim from the front so a long-running lobby can't grow without bound.
        if (messages.length > MAX_MESSAGES_PER_CONVERSATION) {
          messages.splice(0, messages.length - MAX_MESSAGES_PER_CONVERSATION);
        }

        // Our own echoes and system lines never count as unread.
        const isUnread =
          targetKey !== s.activeKey && !m.mine && m.kind !== "system";

        return {
          conversations: {
            ...s.conversations,
            [targetKey]: {
              ...existing,
              messages,
              unread: existing.unread + (isUnread ? 1 : 0),
            },
          },
        };
      });
    });

    await api.onChatStatus((st) => {
      set({
        connected: st.connected,
        connecting: false,
        nick: st.nick,
        error: st.error,
      });
    });

    await api.onChatPresence((p) => {
      set((s) => ({
        friends: s.friends.map((f) =>
          f.nick.toLowerCase() === p.nick.toLowerCase() ? { ...f, online: p.online } : f
        ),
      }));
    });

    // An inbound request / accept / decline rewrites the list on the Rust side
    // and hands us the new one, so this replaces rather than merges.
    await api.onFriendsChanged((e) => {
      set({ friends: e.friends });
    });

    await api.onChatNames((n) => {
      const key = convKey(n.channel);
      set((s) => ({
        conversations: {
          ...s.conversations,
          [key]: {
            ...(s.conversations[key] ?? emptyConversation(n.channel)),
            users: n.users,
          },
        },
      }));
    });
  },

  connect: async (nick) => {
    set({ connecting: true, error: null });
    try {
      await api.chatConnect(nick);
      // `connected` is not set here — it flips when the server's welcome
      // arrives as a chat-status event. Claiming it early would show a
      // connected UI while registration is still in flight.
      const status = await api.chatStatus();
      // Make sure the default lobby has a tab even before the first message.
      get().openConversation(status.defaultChannel);
    } catch (e) {
      set({ connecting: false, error: String(e) });
    }
  },

  disconnect: async () => {
    await api.chatDisconnect().catch(() => {});
    set({ connected: false, connecting: false });
  },

  send: async (conversation, text) => {
    await api.chatSend(conversation, text);
  },

  openConversation: (name) => {
    const key = convKey(name);
    set((s) =>
      s.conversations[key]
        ? { activeKey: key }
        : {
            activeKey: key,
            conversations: { ...s.conversations, [key]: emptyConversation(name) },
          }
    );
    // Opening a conversation clears its unread count.
    set((s) => ({
      conversations: {
        ...s.conversations,
        [key]: { ...s.conversations[key], unread: 0 },
      },
    }));
  },

  closeConversation: (name) => {
    const key = convKey(name);
    const conv = get().conversations[key];
    if (conv?.isChannel) {
      api.chatPart(conv.name).catch(() => {});
    }
    set((s) => {
      const next = { ...s.conversations };
      delete next[key];
      const remaining = Object.keys(next);
      return {
        conversations: next,
        activeKey: s.activeKey === key ? (remaining[0] ?? null) : s.activeKey,
      };
    });
  },

  setActive: (name) => {
    if (name === null) {
      set({ activeKey: null });
      return;
    }
    get().openConversation(name);
  },

  joinChannel: async (channel) => {
    await api.chatJoin(channel);
    const withHash = channel.startsWith("#") ? channel : `#${channel}`;
    get().openConversation(withHash);
    // The roster arrives asynchronously; ask for it explicitly so a channel
    // opened this way populates its member list without waiting for traffic.
    api.chatNames(withHash).catch(() => {});
  },

  refreshFriends: async () => {
    const friends = await api.listFriends();
    set({ friends });
  },

  addFriend: async (nick, note) => {
    const friends = await api.addFriend(nick, note);
    set({ friends });
  },

  acceptFriend: async (nick) => {
    const friends = await api.acceptFriend(nick);
    set({ friends });
  },

  declineFriend: async (nick) => {
    const friends = await api.declineFriend(nick);
    set({ friends });
  },

  removeFriend: async (nick) => {
    const friends = await api.removeFriend(nick);
    set({ friends });
  },

  crew: () => get().friends.filter((f) => f.status === "accepted"),
  incoming: () => get().friends.filter((f) => f.status === "pendingIn"),
  outgoing: () => get().friends.filter((f) => f.status === "pendingOut"),

  totalUnread: () =>
    Object.values(get().conversations).reduce((sum, c) => sum + c.unread, 0) +
    get().friends.filter((f) => f.status === "pendingIn").length,
}));
