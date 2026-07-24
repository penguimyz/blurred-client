import { create } from "zustand";
import type { Account } from "../types/account";
import * as api from "../lib/tauri";

interface AccountStore {
  accounts: Account[];
  loaded: boolean; // distinguishes "haven't checked yet" from "checked, zero accounts"
  refresh: () => Promise<void>;
  remove: (accountId: string) => Promise<void>;
  setActive: (accountId: string) => Promise<void>;
}

// `loaded` matters for the login gate: rendering the gate before the first
// listAccounts() call resolves would flash it even for a returning user
// who already has an account saved. Gate off `loaded`, not off
// `accounts.length` alone.
export const useAccountStore = create<AccountStore>((set, get) => ({
  accounts: [],
  loaded: false,

  refresh: async () => {
    const accounts = await api.listAccounts();
    set({ accounts, loaded: true });
  },

  remove: async (accountId) => {
    await api.removeAccount(accountId);
    await get().refresh();
  },

  setActive: async (accountId) => {
    const accounts = await api.setActiveAccount(accountId);
    set({ accounts, loaded: true });
  },
}));
