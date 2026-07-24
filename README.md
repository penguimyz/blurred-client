# Blurred Client

See `SPEC.md` for the full product spec and `ROADMAP.md` for what's
actually built vs. what's still TODO — read the roadmap before assuming
any given feature works, this is an early scaffold, not a finished app.

## Sign-in: Microsoft or offline

The login gate offers two paths:

**Microsoft/Xbox sign-in** (`src-tauri/src/commands/online_auth.rs`) — the
OAuth 2.0 device-code flow: the app shows a short code, opens
microsoft.com/link, and completes the MSA → Xbox Live → XSTS → Minecraft
Services chain once you approve. These accounts can join online servers and
Realms. The long-lived refresh token is stored in the **OS keychain** (via the
`keyring` crate); the short-lived Minecraft access token is re-derived at launch
and never written to disk.

- Needs an **Azure app registration** (client ID). One is seeded by default in
  Settings → Microsoft sign-in; point it at your own Azure app if you like. The
  app must have **"Allow public client flows"** enabled (Authentication →
  Advanced settings) for the device-code grant.
- Caveat: Microsoft has at times gated the final `login_with_xbox` step to
  approved Azure apps. A 401/403 there with an otherwise-valid token means the
  app needs Microsoft/Mojang launcher-auth approval.
- This chain is written against the documented endpoints but has **not** been
  live-tested from the machine it was built on (no network to the auth hosts).

**Offline mode** — type an in-game username and it mints a local account. The
username maps to a stable "offline UUID" the same way vanilla does
(`UUID.nameUUIDFromBytes("OfflinePlayer:<name>")` — see `offline_uuid` in
`src-tauri/src/commands/auth.rs`). Offline accounts can only join
`online-mode=false` servers — fine for testing, singleplayer, and LAN.

Manage, switch, add, and remove accounts of either type from the **Accounts**
tab.

## Windows setup (this is the target platform for now)

1. **Rust**: install via [rustup.rs](https://rustup.rs). Default stable
   toolchain is fine.
2. **Node**: v18+ (v22 was used to write this). `node --version` to check.
3. **Tauri's native prerequisites** — you need the MSVC build tools and
   WebView2:
   - Microsoft C++ Build Tools: install via
     [Visual Studio Build Tools](https://visualstudio.microsoft.com/visual-cpp-build-tools/),
     select "Desktop development with C++" during install.
   - WebView2: pre-installed on Windows 11 and most up-to-date Windows 10.
     If missing, Tauri's docs link the evergreen installer.
   - Full checklist: https://v2.tauri.app/start/prerequisites/ (verify
     this URL still matches current Tauri docs — v2 was fairly new when
     this was written and the docs structure may have moved).
4. From the repo root:
   ```
   npm install
   npm run tauri dev
   ```
   (or `cargo tauri dev` from `src-tauri/` if you have `tauri-cli`
   installed globally instead of via npm)

## What happens on first run

- A data directory gets created (via the `directories` crate — on Windows
  that's `%APPDATA%\blurredclient\BlurredClient\`), containing
  `settings.json` and an `instances/` folder.
- The sign-in gate asks for a username (offline mode) and creates a local
  account in `accounts.json`.
- The Home screen loads (empty instance grid) and lets you create a
  vanilla instance.
- Hitting "Play" attempts the full Phase 1 pipeline: fetch Mojang's
  version manifest, download the client jar + libraries, find a Java
  executable, launch, stream logs. **This has not been tested against the
  live Mojang API** — the sandbox this was built in doesn't have network
  access to `piston-meta.mojang.com`. Expect the first real run to surface
  something that needs fixing; `ROADMAP.md` lists the known gaps (asset
  sync being the biggest one).

## If `list_detected_java` finds nothing

The Windows JVM detection checks `JAVA_HOME`, common install roots
(`C:\Program Files\Java`, Eclipse Adoptium/Temurin paths, etc.), and the
registry. If you've got Java installed somewhere nonstandard, it won't be
found yet — for now, open the generated `settings.json` and set
`defaultJava.executablePath` to your `javaw.exe` path manually so you're
not blocked on the detection logic while everything else gets built out.

## Known gap: no icons

`src-tauri/icons/` is empty but `tauri.conf.json` references icon files in
it. `npm run tauri dev` won't care. `tauri build` (producing an actual
installer) will fail until you add them. Once you have a source PNG:
```
npx tauri icon path/to/source.png
```
generates the full platform icon set automatically.

## Project layout

```
src-tauri/          Rust backend
  src/commands/      One file per command group (instance, java, mojang, modrinth, curseforge)
  src/models/        Instance + settings data structures
  src/state.rs        App-wide state (data dir, settings, instance cache)
src/                 React frontend
  components/        GlassCard (surface layer), Sidebar
  pages/              Home (only real page right now)
  store/              Zustand store wrapping the Tauri commands
  lib/tauri.ts        Typed invoke() wrappers -- one function per Rust command
  types/instance.ts   Hand-maintained mirror of the Rust structs (no codegen yet)
  styles/theme.css     Glass design tokens -- read ROADMAP.md Phase 2 before adding new components
```
