import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Tauri-specific config per https://v2.tauri.app/start/frontend/vite/ --
// fixed port (Tauri's dev server watcher expects it), and we ignore
// src-tauri so Rust rebuilds don't trigger a frontend HMR loop.
export default defineConfig({
  plugins: [react()],
  clearScreen: false,
  server: {
    port: 1420,
    strictPort: true,
    watch: {
      ignored: ["**/src-tauri/**"],
    },
  },
});
