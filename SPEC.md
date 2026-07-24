# Blurred Client — Product & Build Specification

## 1. Concept Summary
Build **Blurred Client**, a third-party Minecraft launcher/client in the spirit of **Lunar Client** (polished, performance-focused, mod-integrated) but architected internally like **Prism Launcher** (open, instance-based, fully user-controlled). The defining visual identity is a **frosted-glass / iOS-style aesthetic** — translucent panels, background blur, soft depth, rounded corners, subtle motion — applied consistently across every screen.

Target platforms: Windows, macOS, Linux (desktop app — Electron, Tauri, or a native framework such as Qt/Flutter; pick one and justify the tradeoffs for performance, bundle size, and native OS integration).

---

## 2. Visual Design Direction ("Glass" Theme)

### 2.1 Design Philosophy — Lunar Polish, Prism Depth
The client should split its design language by **layer**, not blend the two styles into a compromise:

- **Surface layer (Lunar-style)** — the screens a user sees most often: Home, instance grid, mod browser, account switcher, first-run onboarding. These should feel like a **premium consumer product**: glossy, cinematic, big hero imagery/cover art, minimal visible text, generous whitespace inside the glass panels, large tappable cards instead of dense tables, and marketing-grade polish (smooth animated transitions, satisfying micro-interactions on hover/click, a strong "wow factor" on first launch). A new user should be able to install and launch modded Minecraft in a couple of clicks without ever seeing a raw config value.
- **Depth layer (Prism-style)** — anything a power user opens deliberately: instance Settings tabs, Java/Env Vars/Custom Commands, per-mod config editors, logs, version management, modpack export/import. These screens should **not** be dumbed down or hidden behind excessive simplification — they should expose the real underlying values (file paths, JVM args, exact version strings, raw config keys) in structured forms, the way Prism does. Prefer information-dense tables, monospace fields for technical values, visible tooltips explaining what each setting actually does, and direct access to "advanced"/raw views (e.g., an "Edit raw config file" toggle next to the friendly form editor).
- **The transition between layers should be intentional**: opening a "Settings" or "Logs" tab is the signal that the user has stepped from the polished consumer surface into the technical control layer. The glass visual theme (blur, translucency, rounded corners, accent color) stays consistent across both layers so the app never feels like two different products — but information density, terminology (exact/technical vs. friendly/simplified), and control count should shift noticeably deeper in.
- **Rule of thumb**: if a Lunar Client user would expect to see it on first launch, style it glossy and minimal. If a Prism Launcher user would expect to configure it, expose it fully and technically — don't hide it behind "simple mode" only.

### 2.2 Visual System Details
- **Glassmorphism system**: semi-transparent backgrounds (backdrop-blur), 1px soft borders with low-opacity white/light strokes, subtle drop shadows for elevation, rounded corners (12–20px radius scale).
- **Dynamic backdrop**: a blurred, softly animated or parallax background (user-selectable wallpaper/gradient) that panels sit on top of, similar to iOS Control Center / macOS Big Sur+ menus. Reserve the most elaborate/animated backdrops for the surface layer (Home); technical screens can use a calmer, more static version of the same backdrop to reduce visual noise while reading dense data.
- **Light & dark modes**, both built on the same glass system (frosted-white vs. frosted-black panels).
- **Accent color system**: a single user-selectable accent tint that colors buttons, toggles, progress bars, and highlights across all glass panels.
- **Typography**: clean, rounded system font stack (SF Pro–like on macOS, Segoe UI Variable–like on Windows) for surface-layer UI chrome; switch to a **monospace font** for technical values specifically (file paths, JVM args, version strings, log output, raw config values) so they read clearly as exact/copyable data, matching Prism's convention of monospacing anything technical.
- **Motion**: soft spring-based transitions for panel open/close, instance switching, and modal presentation on the surface layer — no jarring instant cuts. Depth-layer screens (logs, settings tables) can use lighter/faster transitions since users there prioritize speed and scanning over spectacle.
- **Consistency requirement**: every screen (home, instance view, mod browser, settings, account manager, logs) must use the same component library and the same glass theme tokens, so the Lunar-vs-Prism split is about density and tone, never about looking like a different app.

---

## 3. Core Architecture — Prism-Style Instance Model
Adopt Prism Launcher's core philosophy: **everything is scoped to an instance**, and instances are fully isolated and portable.

- Each **instance** = its own Minecraft version, mod loader (Fabric/Forge/Quilt/NeoForge), mod list, resource packs, configs, saves, and settings overrides.
- Instance list view shows: icon/cover art, name, MC version, loader, last-played date, and quick-launch button.
- Per-instance detail view includes tabs for: **Mods**, **Settings**, **Logs**, **Screenshots/Saves**, **Notes**.
- Instances can be **duplicated, exported, imported, and shared** (zip/format compatible with common formats like `.mrpack` where feasible).
- **Global settings** act as defaults; any instance can override them (this override pattern applies specifically to Java, Environment Variables, and Custom Commands — see Section 7).

---

## 4. Navigation Structure (Tabs)

### 4.1 Global App Tabs (left sidebar or top bar, always visible post-login)
- **Home** — instance grid/list, "Play" quick-launch, recently played.
- **Browse** — the Modrinth/CurseForge mod, modpack, resource pack, and shader browser (Section 5.3).
- **Modpacks** — library of created/imported modpacks, separate from live instances.
- **Accounts** — account switcher, add/remove accounts, skin management.
- **Settings** — global launcher settings (Section 10): theme, default Java, default env vars, default custom commands, default platform source, update frequency, storage location.
- **Logs** — global/launcher-level log (distinct from per-instance logs), useful for launcher crashes or update errors.

### 4.2 Per-Instance Tabs (opened when a specific instance is selected)
- **Overview** — instance icon/name, MC version, loader, quick "Play" button, last-played and playtime stats.
- **Mods** — installed mods list, enable/disable, update indicators, add mods (Section 5.1).
- **Configs** — consolidated per-mod config editor view (Section 5.1), so users don't have to hunt through the Mods tab mod-by-mod.
- **Resource Packs / Shaders** — separate lightweight lists, same enable/disable + browse-to-add pattern as Mods.
- **Worlds/Saves** — manage local worlds, backup/export/delete.
- **Screenshots** — in-launcher gallery of the instance's screenshots folder.
- **Settings** — instance-specific overrides: version/loader, Java, environment variables, custom commands, memory allocation, window size (Sections 7–8).
- **Logs** — this instance's live and historical logs (Section 7).
- **Notes** — free-text notes field for the instance (Prism has this too — useful for "why did I install this mod" reminders).

This two-tier tab system (global app-level tabs + per-instance tabs) should be visually distinguished — e.g., global tabs live in a persistent glass sidebar, while instance tabs appear as a secondary tab bar at the top of the instance detail view once an instance is opened.

---

## 5. Mod Management

### 5.1 Installed Mods View (per instance)
- List of installed mods with icon, name, version, enabled/disabled toggle, and update-available indicator.
- One-click **enable/disable** without deleting the mod (Prism-style `.disabled` toggling).
- Drag-and-drop to add local `.jar` mod files.
- Per-mod **config editor** accessible directly from the launcher — auto-detects known config formats (JSON, TOML, `.properties`) and renders a friendly form UI instead of requiring the user to hand-edit files. Falls back to a raw text/code editor for unsupported formats.

### 5.2 Auto-Update Support
- Background check for mod updates (respecting the selected platform — Modrinth or CurseForge) with a batch "Update All" and per-mod "Update" action.
- Version-pinning option to exclude specific mods from auto-update.
- Update changelog preview before applying.

### 5.3 Mod Browser (Modrinth ⇄ CurseForge)
- Integrated in-launcher browser for discovering mods, resource packs, shaders, and modpacks.
- **Platform toggle switch** to swap the entire browsing source between Modrinth and CurseForge (search, categories, filters, download all route through whichever is selected).
- Filter by MC version, loader, category, and sort by relevance/downloads/updated.
- One-click "Install to [instance]" or "Add to new instance."
- Dependency resolution (auto-installs required libraries, e.g., Fabric API, Cloth Config).

### 5.4 Modpacks
- **Creatable modpacks**: users can save any instance's mod list as a reusable modpack (with metadata: name, icon, description, MC version, loader).
- Modpacks can be **shared/exported** and **browsed/imported** from Modrinth/CurseForge modpack listings.
- **Default built-in modpack** ("Blurred Essentials" or similar) pre-populated with:
  - Fabric API, Sodium, Cloth Config, EntityCulling, FerriteCore, Mod Menu, Lithium, ImmediatelyFast, YACL, AppleSkin, Reese's Sodium Options, ModernFix, Jade, Simple Voice Chat, Dynamic FPS, Zoomify, MoreCulling, Krypton, Controlling, Clumps, ShulkerBoxTooltip, MaLiLib, Combat Hitboxes, Consumable Optimizer, Complete Shield Fixes, MoreCullingExtra, Gamma Utils, JEI, Freelook, Essential.
  - This pack should be offered as the default option when creating a new instance, with the ability to opt out and start blank.

---

## 6. Version & Instance Maintenance
- **Easy version upgrading/downgrading**: a dropdown/selector on each instance to change MC version and/or loader version, with automatic warning if installed mods aren't compatible with the target version and an option to auto-search for compatible replacements.
- **Easy logs**: a dedicated log viewer per instance (live tail during play, plus historical log browsing), with search/filter, colorized log levels, and one-click "copy to clipboard" or "upload to paste service."

---

## 7. Instance-Level Advanced Settings (Prism Launcher parity — required)
These three features must work **exactly like Prism Launcher's implementation**, including the global-default + per-instance-override pattern:

- **Java Management**: auto-detect installed JVMs, allow manual path entry, per-instance Java executable selection, memory allocation (min/max RAM) sliders, and custom JVM arguments field. Global default Java settings apply unless an instance overrides them.
- **Environment Variables**: per-instance key/value environment variable editor, with a global default set that instances inherit unless overridden.
- **Custom Commands**: per-instance "pre-launch," "wrapper," and "post-exit" command fields (matching Prism's three-hook command system), with global defaults and per-instance override toggle.

---

## 8. Account & Identity Management

### 8.1 Mandatory Login Gate
- The launcher **requires a signed-in Microsoft/Mojang account before any part of the app can be used** — no guest/offline browsing of the UI. On first launch (and on any launch with zero saved accounts), the user is shown a full-screen login prompt (styled with the glass theme) and cannot dismiss it or reach the home screen/instances/mod browser until authentication succeeds.
- If the saved auth token expires or is revoked, the launcher locks back to the login screen until re-authentication, preserving local instance data so nothing is lost.
- This login screen is the one exception to the "instant, chrome-free" navigation goal — it's a deliberate hard gate.

- **Multi-account support**, with the ability to launch different instances under different accounts (per-instance account assignment, not just global).
- **Quick account switching** from the home screen (avatar-based account switcher).
- **Skin management**: quick skin change, local skin saving/library, and preview before applying.
- Secure token storage (OS keychain / credential manager integration) — never store raw credentials in plaintext.

---

## 9. Playtime Tracking
- **Last played** (session duration for the most recent launch).
- **All-time total playtime** (aggregate across all instances).
- **Per-instance total playtime**.
- Display these stats on both the instance card (compact) and instance detail view (full breakdown, ideally with a simple chart).

---

## 10. Settings & Configuration Hub
- Centralized launcher-wide settings screen: theme/appearance controls, default Java, default environment variables, default custom commands, default download source (Modrinth/CurseForge), update check frequency, storage/instance folder location.
- Every mod's config should be reachable either from the instance's Mods tab or a consolidated "All Configs" view.

---

## 11. Non-Functional Requirements
- Fast cold-start time; instance switching should feel instant (data-driven UI, not full reloads).
- Offline-tolerant: cached mod metadata and instance data usable without network access (only live features like browsing/updating require connectivity).
- Clear error handling for failed downloads, incompatible mods, and corrupted instances, with actionable recovery steps.
- Respect Mojang/Microsoft auth flow requirements and Modrinth/CurseForge API terms of service.

---

## 12. Suggested Tech Stack (open for revision)
- **Shell**: Tauri (lighter weight, Rust backend) or Electron (broader ecosystem) — evaluate based on desired binary size and native performance needs.
- **UI**: React/Svelte with a custom component library implementing the glass design system (CSS `backdrop-filter` + custom shadow/border tokens).
- **Backend logic**: Rust or Node for instance/version/mod management, Java process launching, and file system operations.
- **APIs**: Modrinth API v2, CurseForge API (requires API key), Microsoft/Mojang auth (MSA device code or OAuth flow).

---

## 13. Deliverable Priorities (suggested build order)
1. Instance creation/launch core loop (create instance → pick version/loader → launch vanilla).
2. Glass UI shell + theme system.
3. Mod browser + install (Modrinth first, CurseForge toggle second).
4. Per-instance mods tab with enable/disable + config editor.
5. Default modpack + custom modpack creation/export/import.
6. Java/Env Vars/Custom Commands (Prism-parity settings).
7. Accounts, skins, multi-account per instance.
8. Logs viewer, playtime tracking, auto-update.
