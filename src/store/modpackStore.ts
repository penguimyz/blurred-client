import { create } from "zustand";
import type { Modpack } from "../types/instance";
import * as api from "../lib/tauri";

interface ModpackStore {
  modpacks: Modpack[];
  loading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  createFromInstance: (instanceId: string, name: string, description: string) => Promise<void>;
  remove: (modpackId: string) => Promise<void>;
  importFrom: (sourcePath: string) => Promise<void>;
}

// Same pattern as the instance store: the filesystem (via Rust) is the source
// of truth, this is just a cache refreshed after every mutation.
export const useModpackStore = create<ModpackStore>((set, get) => ({
  modpacks: [],
  loading: false,
  error: null,

  refresh: async () => {
    set({ loading: true, error: null });
    try {
      const modpacks = await api.listModpacks();
      set({ modpacks, loading: false });
    } catch (e) {
      set({ error: String(e), loading: false });
    }
  },

  createFromInstance: async (instanceId, name, description) => {
    await api.createModpackFromInstance(instanceId, name, description);
    await get().refresh();
  },

  remove: async (modpackId) => {
    await api.deleteModpack(modpackId);
    await get().refresh();
  },

  importFrom: async (sourcePath) => {
    await api.importModpack(sourcePath);
    await get().refresh();
  },
}));
