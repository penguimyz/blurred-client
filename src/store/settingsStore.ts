import { create } from "zustand";
import type { GlobalSettings, Theme } from "../types/settings";
import * as api from "../lib/tauri";

// Theme + accent are applied to the document root the moment settings load and
// again on every save, so the glass tokens in theme.css (which read
// [data-theme] and --accent) re-tint live without a reload. main.tsx sets a
// dark default for the pre-settings flash; this takes over once real settings
// arrive.
export function applyTheme(theme: Theme) {
  const resolved =
    theme === "system"
      ? window.matchMedia("(prefers-color-scheme: light)").matches
        ? "light"
        : "dark"
      : theme;
  document.documentElement.setAttribute("data-theme", resolved);
}

export function applyAccent(hex: string) {
  document.documentElement.style.setProperty("--accent", hex);
}

interface SettingsStore {
  settings: GlobalSettings | null;
  loading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  save: (settings: GlobalSettings) => Promise<void>;
}

export const useSettingsStore = create<SettingsStore>((set) => ({
  settings: null,
  loading: false,
  error: null,

  refresh: async () => {
    set({ loading: true, error: null });
    try {
      const settings = await api.getSettings();
      applyTheme(settings.theme);
      applyAccent(settings.accentColor);
      set({ settings, loading: false });
    } catch (e) {
      set({ error: String(e), loading: false });
    }
  },

  save: async (settings) => {
    const saved = await api.updateSettings(settings);
    applyTheme(saved.theme);
    applyAccent(saved.accentColor);
    set({ settings: saved });
  },
}));
