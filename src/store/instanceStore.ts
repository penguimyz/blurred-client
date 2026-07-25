import { create } from "zustand";
import type { Instance, Loader } from "../types/instance";
import * as api from "../lib/tauri";

interface InstanceStore {
  instances: Instance[];
  loading: boolean;
  error: string | null;
  // Instance ids with a running game process (mirrors the backend `running` map).
  running: Record<string, boolean>;

  refresh: () => Promise<void>;
  refreshRunning: () => Promise<void>;
  create: (name: string, mcVersion: string, loader: Loader) => Promise<Instance>;
  remove: (instanceId: string) => Promise<void>;
  launch: (instanceId: string) => Promise<void>;
  quit: (instanceId: string) => Promise<void>;
  rename: (instanceId: string, name: string) => Promise<void>;
  duplicate: (instanceId: string) => Promise<void>;
  openFolder: (instanceId: string) => Promise<void>;
}

// Deliberately not persisting instance list to localStorage/etc -- the
// filesystem (via Rust) is the source of truth. This store is just a
// cache of that, refreshed on mutation. Keeps "which state is authoritative"
// unambiguous as the app grows past this one store.
export const useInstanceStore = create<InstanceStore>((set, get) => ({
  instances: [],
  loading: false,
  error: null,
  running: {},

  refresh: async () => {
    set({ loading: true, error: null });
    try {
      const instances = await api.listInstances();
      set({ instances, loading: false });
    } catch (e) {
      set({ error: String(e), loading: false });
    }
  },

  // Sync the running set from the backend (e.g. on app load, so a game left
  // running across a UI reload still shows as running).
  refreshRunning: async () => {
    try {
      const ids = await api.listRunning();
      set({ running: Object.fromEntries(ids.map((id) => [id, true])) });
    } catch {
      /* non-fatal */
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
    // launch_instance stays pending for the whole game session, so we flip
    // `running` on immediately and clear it when the process exits.
    set((s) => ({ error: null, running: { ...s.running, [instanceId]: true } }));
    try {
      await api.launchInstance(instanceId);
    } catch (e) {
      set({ error: String(e) });
      throw e;
    } finally {
      set((s) => {
        const running = { ...s.running };
        delete running[instanceId];
        return { running };
      });
      await get().refresh(); // pick up updated lastPlayed / playtime
    }
  },

  quit: async (instanceId) => {
    await api.killInstance(instanceId);
  },

  rename: async (instanceId, name) => {
    await api.renameInstance(instanceId, name);
    await get().refresh();
  },

  duplicate: async (instanceId) => {
    await api.duplicateInstance(instanceId);
    await get().refresh();
  },

  openFolder: async (instanceId) => {
    await api.openInstanceFolder(instanceId);
  },
}));
