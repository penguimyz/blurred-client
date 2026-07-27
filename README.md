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

**Offline mode is gated behind ownership, and can't impersonate anyone.** Two
rules, both enforced in the backend (`create_offline_account`), with **no bypass
of any kind** — no setting, no flag, no environment variable:

1. An offline account can only be created once a Microsoft account that owns
   Minecraft has signed in — so offline mode means "play the copy you own without
   a live connection," never "play without owning the game."
2. The offline identity is **always your Microsoft username** — there is no
   free-text username field, so an offline account can never be set to another
   player's name.

An offline account is simply the offline-playable form of your owned Microsoft
identity. This is the deliberate design difference from launchers like TLauncher.

Accounts of either type are managed (add / switch / remove) from the **Accounts**
tab.

## Setup

Windows and Linux are both supported. macOS is not: the code paths are written
and compile, but nothing has been built or run there.

Common to both: **Rust** via [rustup.rs](https://rustup.rs) (stable), and
**Node** v18+ (`node --version`). Full native-prerequisite checklist:
https://v2.tauri.app/start/prerequisites/

### Windows

- **Microsoft C++ Build Tools**: install via
  [Visual Studio Build Tools](https://visualstudio.microsoft.com/visual-cpp-build-tools/),
  select "Desktop development with C++" during install.
- **WebView2**: pre-installed on Windows 11 and most up-to-date Windows 10.
  If missing, Tauri's docs link the evergreen installer.

### Linux

Install the system libraries (`./scripts/build-linux.sh --deps` does this for
apt/dnf/pacman, or by hand):

```
sudo apt install libwebkit2gtk-4.1-dev libgtk-3-dev libayatana-appindicator3-dev \
                 librsvg2-dev libdbus-1-dev libssl-dev patchelf build-essential file
```

`webkit2gtk-4.1` is the one Tauri v2 needs — 4.0 is the v1 dependency and won't
satisfy it, which puts the floor at roughly Ubuntu 22.04 / Debian 12 / Fedora 36.

Two Linux runtime notes:

- **The glass blur is Windows-only.** `window_vibrancy` has no Linux backend
  because X11/Wayland expose no portable blur-behind — it's per-compositor. The
  UI paints its own opaque backdrop there instead, so it looks correct, just
  without the desktop showing through.
- **Microsoft sign-in needs a keyring.** The MSA refresh token goes to the D-Bus
  Secret Service, so a provider has to be running — `gnome-keyring` or KDE's
  `kwallet` (with `kwallet-pam`). Without one, sign-in reports that it couldn't
  reach the keyring. Offline accounts are unaffected.

### Run it

From the repo root:

```
npm install
npm run tauri dev
```

(or `cargo tauri dev` from `src-tauri/` if you have `tauri-cli` installed
globally instead of via npm)

## Building

`npm run tauri build` produces installers for whatever platform you're on —
`.msi`/`.exe` on Windows, `.deb`/`.rpm`/`.AppImage` on Linux. There is no
cross-compiling: a Linux bundle has to be built on Linux, because it links
against that machine's GTK/WebKit stack.

- **On Linux (or WSL):** `./scripts/build-linux.sh` installs the system
  dependencies and runs the build.
- **From anywhere:** push the branch and let
  `.github/workflows/build-linux.yml` do it — it builds on `ubuntu-22.04` and
  uploads the three bundles as workflow artifacts. Pushing a `v*` tag also
  attaches them to a GitHub release, which is what the in-app update check
  reads.

Build on the oldest distro you intend to support: bundles link against the
builder's glibc, so a build from a current distro fails to start on older ones
with a `GLIBC_x.y not found` error. That's why CI pins 22.04 rather than
`ubuntu-latest`.

## What happens on first run

- A data directory gets created (via the `directories` crate — on Windows
  that's `%APPDATA%\blurredclient\BlurredClient\`, on Linux
  `~/.local/share/BlurredClient/`), containing `settings.json` and an
  `instances/` folder.
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

JVM detection (`src-tauri/src/commands/java.rs`) looks in:

- **Windows** — `JAVA_HOME`, common install roots (`C:\Program Files\Java`,
  Eclipse Adoptium/Temurin, Microsoft, Zulu), and the `JavaSoft` registry keys.
- **Linux / macOS** — `JAVA_HOME`, everything on `$PATH`, the distro roots
  (`/usr/lib/jvm`, `/usr/lib64/jvm`, `/usr/java`, `/opt`, and macOS's
  `JavaVirtualMachines`), and the per-user JDK managers (`~/.sdkman`,
  `~/.jdks`, `~/.gradle/jdks`). Installs reachable under several names — the
  usual `/usr/bin/java` → `/usr/lib/jvm/default-java` → real JDK chain — are
  resolved and listed once.

If your Java is somewhere none of that covers, open the generated
`settings.json` and set `defaultJava.executablePath` by hand (`javaw.exe` on
Windows, `bin/java` elsewhere).

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
