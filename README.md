# Blurred Client

Blurred Client is a **personal, non-commercial, open-source third-party
Minecraft: Java Edition launcher** — a desktop app for creating and managing
isolated game instances (Minecraft version, mod loader, mods, configs, saves)
and launching the copy of the game the signed-in player owns. It's built with
[Tauri](https://tauri.app) (Rust backend) + React, with a frosted-glass UI.

> **Not affiliated with, endorsed by, or associated with Mojang, Microsoft, or
> Xbox.** "Minecraft" is a trademark of Mojang Synergies AB. This is a hobby
> project in active development. See `SPEC.md` for the product spec and
> `ROADMAP.md` for what's built vs. still TODO.

## Authentication & compliance

Blurred Client signs players in with **their own Microsoft account** using the
**standard, documented OAuth 2.0 device-code flow**. It does **not** bypass,
disable, weaken, or work around any authentication, license-ownership, or safety
check, and it never handles or stores user passwords. Game ownership is enforced
by Minecraft Services — if the account doesn't own Minecraft: Java Edition, there
is no profile and the launch is refused.

Sign-in flow (`src-tauri/src/commands/online_auth.rs`):

1. **OAuth 2.0 device code** against `login.microsoftonline.com` (`consumers`
   tenant, scope `XboxLive.signin offline_access`) — the user approves at
   microsoft.com/link. Public client, **no client secret**.
2. **Xbox Live → XSTS →** `api.minecraftservices.com/authentication/login_with_xbox`.
3. `api.minecraftservices.com/minecraft/profile` to read the owned account's
   profile (uuid / name / skin).

Token handling:

- The long-lived **MSA refresh token is stored in the OS keychain** (via the
  `keyring` crate), keyed by account id — never in plaintext.
- The short-lived Minecraft access token is re-derived from the refresh token at
  launch and **never written to disk**.

The Azure **application (client) ID** used for sign-in is a configurable setting
(Settings → Microsoft sign-in); the Azure app has "Allow public client flows"
enabled for the device-code grant. The launcher respects the
[Minecraft EULA and Usage Guidelines](https://aka.ms/mcusageguidelines).

**Offline mode** is also offered for LAN / singleplayer: it mints a local account
with the same deterministic offline UUID vanilla uses
(`UUID.nameUUIDFromBytes("OfflinePlayer:<name>")` — see `offline_uuid` in
`src-tauri/src/commands/auth.rs`). Offline accounts can only join
`online-mode=false` servers; they do not and cannot reach online servers, Realms,
or any authenticated Mojang service.

Accounts of either type are managed (add / switch / remove) from the **Accounts**
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
- The sign-in gate offers Microsoft sign-in or offline mode, and saves the
  account to `accounts.json` (Microsoft refresh tokens go to the OS keychain,
  not this file).
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
  src/commands/      One file per command group: instance, java, mojang, modrinth,
                     mods, config, content, modpacks, settings, auth, online_auth
  src/models/        Instance / settings / account / modpack data structures
  src/state.rs       App-wide state (data dir, settings, accounts, instance cache)
  src/util.rs        Small shared helpers (base64)
src/                 React frontend
  components/        Shared UI: GlassCard, DataTable, MonoField, OverrideSettingsForm,
                     Backdrop, Sidebar, LoginGate, MicrosoftLoginButton
  pages/             Home, Browse, Modpacks, Accounts, Settings, GlobalLogs
  pages/instance/    Per-instance detail tabs (Overview/Mods/Configs/Worlds/…)
  store/             Zustand stores wrapping the Tauri commands
  lib/tauri.ts       Typed invoke() wrappers -- one function per Rust command
  types/             Hand-maintained mirrors of the Rust structs (no codegen yet)
  styles/theme.css   Glass design tokens
```
