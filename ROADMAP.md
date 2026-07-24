# Blurred Client — Build Plan

Decisions locked in Phase 0, reasoning kept here so nobody re-litigates them
three months from now without remembering why:

- **Shell: Tauri**, not Electron. Bundle size, native OS integration
  (keychain for tokens), and a Rust backend fit for CPU/IO-bound work
  (Java process management) better than Electron+Node for this app.
  Tradeoff accepted: WebView rendering differs across OSes, so
  `backdrop-filter` behavior needs verifying per-platform. Windows uses
  WebView2 — real acrylic blur-behind needs `window-vibrancy`, not just
  CSS (wired in `src-tauri/src/lib.rs`).
- **UI: React + Zustand**, no Redux. Instance switching needs to feel
  instant; normalized, colocated state beats a heavy global store for that.
- **Backend split**: Rust owns instance filesystem ops, Java process
  spawn/monitor, credential storage, config parsing. Frontend owns UI
  state + Modrinth/CurseForge calls proxied through Rust (keeps the
  CurseForge key, once you have one, out of the webview).
- **The `Overridable<T>` pattern** (global default → per-instance override)
  is generic, used for Java/EnvVars/CustomCommands. Built once in
  `models/instance.rs`, not three separate implementations.
- **CurseForge was removed**, not just deferred. Modrinth is the only mod
  source. If CurseForge comes back later it's a from-scratch addition
  (own command module, own UI toggle), not an uncommenting job.

---

## Phase 0 — Foundational decisions
**Status: done.** Data models (`src-tauri/src/models/`), project structure,
tech stack locked as above.

## Phase 1 — Core loop, no styling
**Status: partially done.** What's actually wired:
- [x] Create instance (vanilla only) → writes `instance.json` to disk
- [x] Fetch Mojang version manifest + per-version detail
- [x] Download client jar + OS-filtered libraries
- [x] Java auto-detection (Windows: JAVA_HOME, common install paths, registry)
- [x] Spawn Java process, stream stdout/stderr back to frontend as events
- [x] **Asset download/sync** — implemented (`sync_assets` in
      `commands/mojang.rs`): fetches the asset index, downloads objects from
      `resources.download.minecraft.net` into `assets/objects/` with bounded
      concurrency, wired into `launch_instance` (streams progress to the Logs
      tab). Standard 1.7+ hashed layout only — legacy "virtual" assets not
      handled. Written against the documented schema, not yet run live.
- [ ] Legacy `minecraftArguments` (pre-1.13) argument format — only the
      modern structured `arguments` object is parsed so far.
- [x] macOS/Linux Java detection — implemented (`JAVA_HOME`, plus
      `/Library/Java/JavaVirtualMachines` on macOS and `/usr/lib/jvm` etc. on
      Linux). Not yet run against a real macOS/Linux box.

**This has not been run against live Mojang/Modrinth endpoints** — this
sandbox's network is allowlisted to package registries only, not
`piston-meta.mojang.com` or `api.modrinth.com`. First thing to do on your
machine: run `create_instance` → `launch_instance` for a vanilla instance
and see what breaks.

## Phase 2 — Glass UI shell + theme system
**Status: done.**
- [x] Theme tokens (`src/styles/theme.css`) — blur, radius scale, accent,
      light/dark, mono font for technical values
- [x] `GlassCard` component (surface layer primitive)
- [x] Home page (instance grid + create modal), wired to the real backend
- [x] `DataTable` / `MonoField` React components (depth layer primitives),
      used across Mods/Configs/Worlds/Settings/Logs
- [x] Backdrop parallax/animation (`components/Backdrop.tsx`, CSS drift blobs,
      honors `prefers-reduced-motion`)
- [x] Instance detail view — `pages/instance/InstanceDetail.tsx` with
      Overview / Mods / Configs / Worlds / Screenshots / Settings / Logs /
      Notes tabs. (Resource Packs/Shaders tab not split out yet — same pattern
      as Mods.)

## Phase 3 — Mods: browser, install, manage
**Status: local mod management done; live-API install still open.**
- [x] `modrinth_search` command (untested against live API)
- [x] Browse page UI wired to `modrinth_search`, with working install:
      `install_modrinth_mod` fetches the newest version matching the target
      instance's MC version + loader and downloads the jar into `mods/`. Mods
      only (resource packs/shaders live in other folders — separate path).
- CurseForge was removed entirely (see decision log above) -- Modrinth is
  the only source now. If it comes back later, it goes back in as its own
  command module + a real platform toggle in the UI, not before.
- [x] Per-instance Mods tab UI (`pages/instance/ModsTab.tsx`) — dense table,
      drag-drop local `.jar` side-loading, remove, pin.
- [x] Enable/disable (`.disabled` renaming) — `set_mod_enabled` +
      `sync_instance_mods` reconcile the list against disk.
- [x] Config editor (`ConfigsTab` + `lib/configFormat.ts`) — sniffs
      JSON/TOML/`.properties`, friendly key/value form with in-place line
      edits (comments/structure preserved), always-available raw fallback.
- [x] Dependency resolution — `install_modrinth_mod` walks required
      dependencies (recursive, cycle-guarded, skips already-installed) and
      pulls them in alongside the requested mod.

## Phase 4 — Modpacks
**Status: offline half done.**
- [x] Create a reusable modpack from any instance's mod set
      (`create_modpack_from_instance`, copies the jars in so packs are
      self-contained).
- [x] Modpacks library page (`pages/Modpacks.tsx`), apply-to-new-instance
      (`apply_modpack`, preserves enabled/disabled state).
- [x] Share/export & import as a single self-contained `.bpack` file
      (JSON + base64'd jars; export reveals it in the file manager, import is
      drag-drop). Local, dependency-free — no zip crate added.
- [ ] Browsing/importing packs from Modrinth listings — needs the live API.
- [ ] Default "Blurred Essentials" pack — needs the download pipeline to
      populate real mods.

## Phase 5 — Java / Env Vars / Custom Commands (Prism parity)
**Status: done.**
- [x] `Overridable<T>` generic pattern, `JavaSettings`/`EnvVars`/`CustomCommands` structs
- [x] Windows Java detection backend (`list_detected_java` command)
- [x] Settings screens (global + per-instance) reuse one form component.
      `components/OverrideSettingsForm.tsx` exports `JavaForm` / `EnvVarsForm` /
      `CustomCommandsForm` (the actual fields) plus `OverrideSection` (the
      inherit-vs-override toggle). Global `Settings` renders the forms on the
      defaults; the per-instance Settings tab wraps each in `OverrideSection`
      bound to the instance's `Overridable<T>`. Backend: `get_settings` /
      `update_settings` / `update_instance`.

## Phase 6 — Accounts & the login gate
**Status: offline + Microsoft sign-in, done and wired into launch.**

Both account types are supported now. Microsoft/Xbox sign-in was re-added
(`commands/online_auth.rs`) as the full device-code -> Xbox Live -> XSTS ->
Minecraft Services chain, living **alongside** the offline flow, not replacing
it. Like the Modrinth/Mojang code it's written against the documented endpoint
contracts but NOT live-tested from the sandbox.

- [x] `create_offline_account(username)` — validates the name (3-16 chars,
      `[A-Za-z0-9_]`), derives the deterministic offline UUID the same way
      vanilla does (`UUID.nameUUIDFromBytes("OfflinePlayer:<name>")`).
- [x] **Microsoft sign-in** — device-code flow (`begin_msa_login` +
      `complete_msa_login`). Refresh token stored in the **OS keychain** via the
      `keyring` crate (keyed by account id); the short-lived Minecraft access
      token is re-derived at launch and never persisted. Azure client ID is a
      configurable setting (`msa_client_id`, seeded with a default dev app);
      the Azure app needs "Allow public client flows" enabled. XSTS `XErr`
      cases (no Xbox profile / child account / region) are mapped to messages.
      ⚠️ Microsoft may gate `login_with_xbox` to approved Azure apps — a 401/403
      there with a valid XSTS token means the app needs Mojang/Microsoft
      launcher-auth approval.
- [x] Account persistence to `accounts.json` (non-secret profile only; secrets
      in the keychain).
- [x] Frontend `LoginGate` — hard gate (spec 8.1), dual: Microsoft device-code
      (shows the code, opens microsoft.com/link) + offline username fallback.
- [x] `App.tsx` wired to check accounts on load and show the gate.
- [x] **Launch wired to both account types.** `launch_instance` branches: offline
      → `--accessToken 0 --userType legacy`; Microsoft → live MC token +
      `--userType msa` (refreshed from the keychain, logged to the Logs tab).
- [x] **Multi-account switching** — `pages/Accounts.tsx` is a full manager: add
      (Microsoft or offline), switch active (`set_active_account` bumps
      `last_used`, which is how launch picks), remove (also clears the keychain
      entry). Skins shown from the Microsoft profile.
- [ ] Per-instance account assignment (`Instance.account_id` exists in the model
      but launch still uses the global active account).
- [ ] Skin *management* (upload/change/library) — skins are displayed, not yet
      editable.

**Offline-mode limitation:** offline accounts can only join `online-mode=false`
servers. Microsoft accounts can join online servers and Realms.

## Phase 7 — Logs viewer, playtime tracking, auto-update
**Status: logs + playtime UI done; auto-update still open.**
- [x] Log event pipe (`instance-log` Tauri event, `onInstanceLog` in
      `src/lib/tauri.ts`)
- [x] Playtime accumulation (`total_playtime_seconds` updated on process exit)
- [x] Logs tab UI (`pages/instance/LogsTab.tsx`) — live tail off the pipe,
      colorized levels, search filter, copy-to-clipboard, buffer cap.
- [x] Playtime chart — Overview tab plots this instance vs. all others
      (`pages/instance/OverviewTab.tsx`) plus per-instance/all-time stat tiles.
- [x] Mod auto-update — `check_mod_updates` (read-only) flags non-pinned
      Modrinth mods with a newer version for the instance's MC version +
      loader; `update_mod` / `update_all_mods` swap the jar and update the
      ModRef, preserving enabled/pinned state. Surfaced in the Mods tab
      ("Check for updates" / per-row "Update" / "Update all"), respecting pins.
- [ ] Launcher self-update — needs a release feed / network.

---

## What to do first, in order, once this is running locally
1. `npm install`, then `cargo tauri dev` — see if it compiles at all.
   Rust/Tauri version drift is the most likely first failure.
2. Get through the login gate: type a username (offline mode). No Microsoft
   account, no Azure setup — it just mints a local account. This is wired
   into launch, so whatever username you pick is what the game runs as.
3. Create a vanilla 1.21.1 instance, hit Play, watch it either work or
   tell you exactly what's missing (asset sync is the most likely gap --
   see Phase 1 notes above).
4. Set a Java path manually in `default_java` inside the generated
   `settings.json` if `list_detected_java` doesn't find anything on your
   machine — the detection logic hasn't been run against a real Windows
   install yet either.
5. Once vanilla launches clean, the next natural steps are Phase 1's asset
   sync (biggest gap for a clean first launch), then Phase 3 (mod install)
   and Phase 2 (instance detail tabs).
