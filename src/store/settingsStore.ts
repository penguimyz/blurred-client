import { create } from "zustand";
import type { GlobalSettings, Theme, UiStyle } from "../types/settings";
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

// The launcher's look. theme.css keys the Modern overrides off [data-style].
// Remembered locally too, so main.tsx can apply it before settings load and a
// Modern launcher doesn't flash the ocean on startup.
export function applyStyle(style: UiStyle) {
  const resolved = style ?? "ocean";
  document.documentElement.setAttribute("data-style", resolved);
  try {
    localStorage.setItem("blurred.uiStyle", resolved);
  } catch {
    // Storage unavailable: only costs the startup flash.
  }
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
      applyStyle(settings.uiStyle);
      applyAccent(settings.accentColor);
      set({ settings, loading: false });
    } catch (e) {
      set({ error: String(e), loading: false });
    }
  },

  save: async (settings) => {
    const saved = await api.updateSettings(settings);
    applyTheme(saved.theme);
    applyStyle(saved.uiStyle);
    applyAccent(saved.accentColor);
    set({ settings: saved });
  },
}));
