import { create } from "zustand";
import type { Instance, Loader } from "../types/instance";
import * as api from "../lib/tauri";

interface InstanceStore {
  instances: Instance[];
  loading: boolean;
  error: string | null;

  refresh: () => Promise<void>;
  create: (name: string, mcVersion: string, loader: Loader) => Promise<Instance>;
  remove: (instanceId: string) => Promise<void>;
  launch: (instanceId: string) => Promise<void>;
}

// Deliberately not persisting instance list to localStorage/etc -- the
// filesystem (via Rust) is the source of truth. This store is just a
// cache of that, refreshed on mutation. Keeps "which state is authoritative"
// unambiguous as the app grows past this one store.
export const useInstanceStore = create<InstanceStore>((set, get) => ({
  instances: [],
  loading: false,
  error: null,

  refresh: async () => {
    set({ loading: true, error: null });
    try {
      const instances = await api.listInstances();
      set({ instances, loading: false });
    } catch (e) {
      set({ error: String(e), loading: false });
    }
  },

  create: async (name, mcVersion, loader) => {
    const instance = await api.createInstance(name, mcVersion, loader);
    await get().refresh();
    return instance;
  },

  remove: async (instanceId) => {
    await api.deleteInstance(instanceId);
    await get().refresh();
  },

  launch: async (instanceId) => {
    set({ error: null });
    try {
      await api.launchInstance(instanceId);
    } catch (e) {
      set({ error: String(e) });
      throw e;
    }
  },
}));
