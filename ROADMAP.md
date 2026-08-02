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
**Status: done — vanilla & Fabric launch end-to-end (verified live).** What's wired:
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
- [x] Legacy `minecraftArguments` (pre-1.13) argument format — `launch_instance`
      detects the flat `minecraftArguments` string and does `${token}`
      substitution (auth_player_name / game_directory / assets_root / etc.).
      (Very old versions also want a "virtual" assets layout, which the asset
      syncer still doesn't build — the remaining pre-1.7 gap.)
- [x] macOS/Linux Java detection — implemented: `JAVA_HOME`, `$PATH`, the
      distro/vendor roots (`/usr/lib/jvm`, `/usr/lib64/jvm`, `/usr/java`,
      `/opt`, macOS's `JavaVirtualMachines`) and the per-user JDK managers
      (`~/.sdkman`, `~/.jdks`, `~/.gradle/jdks`), deduped by resolved path so
      one JDK reachable under several symlinks lists once.

### Linux support

**Status: shipped and confirmed working** — v0.2.0 ran on Linux Mint (2015
laptop, Intel integrated graphics). Java auto-detection found the system JVM
unassisted, and Microsoft sign-in completed, so the Secret Service keychain path
works on a stock Mint install. Framerate is hardware-bound (~40 fps there); the
launcher isn't in the render path once the JVM is spawned.

What landed:

- [x] Java detection reworked for Linux layouts (above).
- [x] `WEBKIT_DISABLE_DMABUF_RENDERER=1` set before webview init (`main.rs`) —
      without it WebKitGTK 2.42+ renders a blank window on several driver
      stacks, Nvidia proprietary especially.
- [x] Keychain failures explain themselves (`online_auth.rs`): the Linux
      backend is the D-Bus Secret Service, which is absent on minimal desktops.
- [x] `bundle.linux` deb/rpm dependencies in `tauri.conf.json`, including
      `xdg-utils` — `open_in_file_manager` execs `xdg-open` directly.
- [x] Font stack falls back to the desktop's UI font instead of a serif face.
- [x] Build pipeline: `.github/workflows/build-linux.yml` (ubuntu-22.04 →
      deb/rpm/AppImage) and `scripts/build-linux.sh` for local/WSL builds.

Not done: the glass blur-behind (no portable Linux equivalent — see README).

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
**Status: done.**
- [x] Create a reusable modpack from any instance's mod set
      (`create_modpack_from_instance`, copies the jars in so packs are
      self-contained).
- [x] Modpacks library page (`pages/Modpacks.tsx`), apply-to-new-instance
      (`apply_modpack`, preserves enabled/disabled state).
- [x] Share/export & import as a single self-contained `.bpack` file
      (JSON + base64'd jars; export reveals it in the file manager, import is
      drag-drop). Local, dependency-free — no zip crate added.
- [x] Browsing/importing packs from Modrinth listings — `install_modrinth_modpack`
      (Browse → Install on a modpack) fetches the newest `.mrpack` and builds an
      instance; `import_mrpack` does the same from a drag-dropped local `.mrpack`
      (parses `modrinth.index.json`, downloads listed files, applies `overrides/`).
      Uses the `zip` crate.
- [x] Default "Blurred Essentials" pack — `BLURRED_ESSENTIALS` slug list +
      `install_mods`; the New Instance modal offers it (Fabric), installs on
      create. See Phase 3.

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
**Status: done (offline + Microsoft, multi-account, per-instance assignment, skins).**

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
- [x] Per-instance account assignment — the instance Settings tab has a "Launch
      as account" picker (`Instance.account_id`); `launch_instance` uses it when
      set, falling back to the global active account.
- [x] Skin management — the Accounts page changes a Microsoft account's skin from
      a **local PNG file** (native file picker, `set_account_skin_file` multipart)
      or a PNG URL, classic/slim, and can reset it, via the Minecraft Services
      API. (A saved skin library is the remaining nicety.)

**Offline-mode limitation:** offline accounts can only join `online-mode=false`
servers. Microsoft accounts can join online servers and Realms.

## Phase 7 — Logs viewer, playtime tracking, auto-update
**Status: done (silent launcher auto-install is the only infra piece left).**
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
- [x] Launcher self-update (check) — `check_launcher_update` queries the
      configured GitHub repo's latest release (`update_repo` setting) and reports
      whether a newer version is out, surfaced in Settings. Silent auto-install
      (updater plugin + signed releases + hosted feed) is the remaining infra
      piece — that needs published, signed releases to exist first.

## Phase 8 — Ocean theme, chat, crash reports
- [x] **Ocean/submarine visual identity.** `styles/theme.css` rebuilt around a
      water column: an abyssal blue-green dark theme and a sunlit-shallows light
      theme, bioluminescent cyan accent (`#35E0D0`), a wet-glass top edge on
      every panel, and a backdrop of drifting currents + god rays + marine snow.
      Existing installs still carrying the old default accent (`#7C9CFF`) are
      migrated on load; a genuinely custom accent is left alone.
- [x] **Icon set** — `components/Icon.tsx`, hand-drawn inline SVG (no icon
      package, keeping the dependency-free build). Periscope for Discover, sonar
      dish for chat, diving helmet for Accounts, depth gauge for Settings.
- [x] **Navigation rail** — the sidebar is now icons-only with hover tooltips
      and an unread pip, Lunar-shaped, which returns ~130px to the content area.
- [x] **Launchpad home** — hero panel with one large Play button, an instance
      switcher, and a right dock carrying your crew and playtime stats. The slot
      Lunar fills with an ad is deliberately the crew list here.
- [x] **Sonar (chat)** — `commands/chat.rs`. A real IRC client over TLS
      (Libera.Chat by default) written directly against the socket: channels,
      DMs, CTCP actions, nick-collision recovery, and **live friend presence via
      the server's MONITOR list** rather than polling. Chosen over a bespoke
      relay so the feature ships working with nothing to host and no second
      account; the UI is explicit that nicks are first-come and that "crew" is a
      one-sided bookmark list, because IRC has no friend handshake. Chat history
      is never written to disk — only the friends list is.
- [x] **Crash reports** — session output is now written to
      `<instance>/logs/session-<timestamp>.log` as it streams, and a non-zero
      exit saves a report under `<data dir>/crashes/` with a diagnosis, the log
      tail, and a copy-ready summary. Surfaced as a banner on the launchpad and
      a full history on the Logs page. Previously the only copy of a crash log
      lived in the frontend's memory, so restarting the launcher destroyed the
      one thing you needed to read. The diagnoser pattern-matches known failures
      (OOM, mixin conflicts, mod dependency errors, driver failures) and falls
      back to stating the exit code rather than inventing a cause.
- [x] **School of fish** — opt-in cursor-following canvas toy (Settings →
      Ambience, off by default), steering agents that chase the pointer and
      settle into an orbit when it holds still.
- [x] **Native control styling** — `color-scheme` is now set per theme, which is
      what actually fixes the white scrollbars and white `<select>` drop-down
      popups (those are OS-drawn, not DOM-drawn, so CSS alone never reached
      them). Selects get a custom chevron and the shared input styles were moved
      off the `background` shorthand, which was silently erasing it.

### Phase 8 follow-ups
- [x] **Friend requests are real.** IRC has no friend concept, so the handshake
      is ours: a `BLURREDFRIEND` CTCP carrying `REQ`/`ACCEPT`/`DECLINE` between
      two Blurred Clients, with `pendingIn`/`pendingOut`/`accepted` states in
      `friends.json`. CTCP is the right transport — servers relay it untouched
      and other IRC clients ignore verbs they don't know, so nobody else sees
      line noise. Presence (MONITOR) is only requested for *accepted* friends,
      so nobody leaks their online status to someone whose request they haven't
      answered. Honest limit, stated in the UI: the recipient must be running
      Blurred Client and be online, so `pendingOut` requests are resendable.
- [x] **Ambient sea life** — shoals, jellyfish, manta rays and the occasional
      shark cross the screen at z-index -1, i.e. *behind* the glass panels, which
      is what makes them read as animals at depth rather than stickers. On by
      default and honours `prefers-reduced-motion`.
- [x] **Fish fix.** The cursor school never appeared for anyone whose OS had
      animations turned down: the reduced-motion branch drew a single static
      frame with all seven fish stacked at screen centre. That branch is gone —
      the school is explicitly opted into, so honouring a "no unrequested
      motion" signal by silently doing nothing was the wrong call. They now also
      start spread around the orbit instead of converging from one point.
- [x] **Rail tooltips no longer get covered.** `backdrop-filter` on the rail
      makes it a stacking context, which trapped the tooltip's z-index inside it
      — no value on the tooltip could win, because that comparison never
      happened at page level. Fixed by lifting the whole rail instead.
- [x] **Rounding + section content.** Selects carry their own radius rather than
      depending on each call site; `.data-table` needed `border-collapse:
      separate` + `overflow: hidden` (a radius on a `<table>` doesn't clip its
      cells); `pre`/`code`/`img` rounded globally. Browse gained loading
      skeletons, a real empty state, version compatibility and clamped
      descriptions; Modpacks gained mod-name chips and an actionable empty
      state; Accounts gained summary tiles.

## Phase 9 — The companion mod
A real Fabric mod (`mod/`), built by Gradle, bundled into the launcher and
auto-installed into instances. Targets MC 1.21.1 / Java 21 — mappings are
version-locked, so moving Minecraft means moving `yarn_mappings` and
`fabric_version` in `mod/gradle.properties` together.

- [x] **Builds.** Gradle wrapper pinned to 9.5.1 (Loom 1.17.17 requires ≥9.5;
      the system Gradle 9.4 fails variant resolution). `mod/gradlew build`
      produces a remapped jar.
- [x] **HUD** — porthole watermark, FPS (smoothed), coordinates, facing, ping
      and CPS, drawn with primitives in the launcher's palette (`Theme.java`
      mirrors `theme.css`). Hides with F1 like the rest of the game's chrome.
- [x] **Launcher bridge** — `commands/bridge.rs` ↔ `LauncherBridge.java`.
      Newline-delimited JSON over a **loopback-only** socket, guarded by a
      per-run token the launcher passes as a JVM `-D` property, so only a
      process the launcher started can connect. The launcher keeps the single
      IRC connection and the game reads a mirror of it — one connection, one
      nick.
- [x] **In-game crew & chat** (`CrewScreen`, default key `O`) — crew with live
      presence, transcript, composer. Doesn't pause singleplayer.
- [x] **Join a friend** — the mod reports its current server on change, the
      launcher records it, and a joinable crew member can be clicked to connect.
- [x] **Auto-install** — the jar ships as a Tauri resource and is synced into
      every Fabric/Quilt instance on launch (and removed from instances whose
      loader can't use it). Byte-compared first, so launching doesn't rewrite it
      every time. CI rebuilds the jar so a release can't ship a stale one.
- [x] **Client-only, by design.** No world state, no movement, no packets —
      the mod is safe on servers that don't have it and can't be mistaken for a
      cheat, which is exactly what a client-branded mod poking at gameplay would
      risk.

**Still out:** pre-1.7 "virtual" assets, silent launcher auto-install (needs
signed release infra), Forge/NeoForge launch (selectable, installer pipeline not
built), and SASL/registered-nick support for chat.

## Phase 10 — Pixel-art restyle
The ocean palette is unchanged; what changed is the *grammar* around it.

- [x] **Silkscreen** (SIL OFL, vendored as woff2 — see `src/styles/fonts/`) for
      UI chrome only: headings, buttons, labels, nav, tags. Body copy, chat and
      log output stay on the sans/mono stacks on purpose — Silkscreen is a
      display face and chat is where reading speed matters most.
      `-webkit-font-smoothing: none` keeps the glyphs on their pixel grid;
      antialiasing a bitmap face just blurs it.
- [x] **Square everything.** `--radius-*` are all 0. Rounded corners are the
      loudest "modern web app" signal there is and no amount of pixel font
      survives them. The three vars are kept distinct so a future theme can
      reintroduce rounding without touching every call site.
- [x] **Hard offset shadows** (`--drop`, no blur) and **2px borders**
      (`--edge`, "one pixel at 2x"). Buttons carry the shadow at rest and on
      `:active` translate by exactly that offset while it collapses to zero — so
      the control physically sinks. Inputs invert it with an inset shadow, so
      fields read as carved in while buttons sit on top.
- [x] **Stepped motion.** Transitions use `steps()` rather than easing, so even
      hover moves in discrete jumps.
- [x] **Dither texture** on cards — a 2px checker at low alpha that lands on the
      pixel grid, standing in for the gradients pixel art can't use.
- [x] Round dots (presence, ping, swatches) became squares; glows became hard
      outlines. A soft halo is the one thing pixel art never has.
- [x] The blur stays. The product is called Blurred and it's what keeps the
      water visible behind a panel — framing it in hard edges is what lets the
      two languages sit together instead of fighting.

## Phase 11 — Cosmetics
- [x] **Capes render in game, for everyone.** `PlayerSkinTexturesMixin` hooks
      `AbstractClientPlayerEntity#getSkinTextures` rather than the cape renderer:
      `SkinTextures` is the single record every render path reads, so one
      injection covers the world, the inventory preview and the elytra slot.
      `CapeManager` decodes base64 PNGs off the bridge thread but defers the
      texture upload to the render thread, because calling the texture manager
      from anywhere else is a silent GL crash.
- [x] **Peer-to-peer cape sharing** (`commands/capes.rs`). Mojang won't hand out
      capes and this launcher has no backend, so capes travel over IRC between
      clients: announce a **hash** to the lobby (`HAVE`), and only send the
      ~1.4 KB of base64 when someone actually asks (`REQ` → `DATA` chunks of
      300 bytes, under IRC's 512-byte line cap). A room where everyone already
      has each other's capes costs one line per join. Received capes are
      verified against the announced hash before use — otherwise a peer could
      serve anything under someone else's identity — held in memory only, and
      dropped when the launcher connection goes away.
- [x] **Cape maker** — pixel editor on the 64x32 sheet with pen/fill/eraser,
      undo, palette, and the cape region outlined (the rest of the sheet is
      elytra texture, and painting there does nothing visible).
- [x] **Skin changer** — account picker, model, file upload or URL, reset. The
      UI is explicit that skins are a *real Mojang change* affecting every
      client, while capes are Blurred-only. Conflating the two would be the
      easiest way to mislead someone here.
- [x] **Pixel-art sea life.** Background creatures and the cursor school are now
      sprite sheets — `string[]` rows, one char per pixel — animated by frame
      swap rather than by maths. Legible and editable in source, and no blur
      filter: a blurred pixel sprite is just a smudge, so depth is carried by
      size and alpha alone.

## Phase 12 — Servers, pixel icons, water
- [x] **Servers tab** (`commands/servers.rs`). One folder per server under
      `<data>/servers/<id>/` with its own jar, world and properties. Downloads
      the vanilla jar via the Mojang manifest or Fabric's server launcher,
      writes `server.properties`, streams a live console you can type commands
      into, and reports the LAN address. Stop uses the console `stop` command
      rather than killing the process, because Minecraft flushes chunks on
      `stop` and killing it is how worlds get corrupted (a separate Force kill
      exists for a hung server).
      - `server.properties` writes **preserve unmanaged keys and comments** — a
        hand-edited `view-distance` survives a change made in the UI. Tested.
      - **The EULA is an explicit click** and **the router is never touched.**
        Agreeing to a licence for someone, or quietly opening a port to the
        internet, are not things a launcher should do on its own.
- [x] **Pixel-art icons.** `Icon.tsx` is now 12x12 character grids — `#` filled,
      `.` empty — compiled once into a single `<path>` of merged horizontal
      runs, so an icon is one DOM node rather than thirty rects.
      `shapeRendering: crispEdges` stops the browser antialiasing the grid away.
- [x] **Tab names can't drift.** `lib/nav.ts` is the one definition of every
      destination; the rail tooltip and the page heading both read from it via
      `PageHeader`, and `NavKey` is a union so an unknown tab fails to compile.
      This fixes the rail saying "Discover" over a page headed "Browse", and
      "Logs" opening a screen called "Crash log".
- [x] **Pixel water.** The backdrop is now hard-stopped colour bands plus a 4px
      dither over the seams, instead of a smooth gradient — stepped zones are
      how pixel art renders a water column. God rays lost their blur for the
      same reason. The drifting currents stay soft *behind* the bands, which are
      slightly transparent so the colour still comes through.
- [x] **Poppable bubbles.** Rise through the water and burst when clicked. The
      canvas is `pointer-events: none` and pops are hit-tested from a capture-
      phase window listener that never consumes the event — so bubbles are
      clickable without the layer swallowing every button press underneath.

## Phase 13 — Stonecutter multi-version builds
The mod now builds for **five Minecraft versions from one source tree** via
[Stonecutter](https://stonecutter.kikugie.dev/) 0.9.7: **1.21.1, 1.21.4, 1.21.8,
1.21.10, 1.21.11**. `mod/gradlew build` produces all five jars in one pass.

Layout: `settings.gradle` declares the version tree, `stonecutter.gradle` is the
controller (the root build script — `build.gradle` became the *per-version*
script), and `versions/<v>/gradle.properties` carries that version's yarn and
fabric-api coordinates, which cannot be derived because mappings are
version-locked.

**Two API boundaries, both measured rather than assumed** (`javap` against each
remapped jar) — that mattered, because a single guessed cutoff was wrong:

| API | 1.21.1 | 1.21.4 | 1.21.8 | 1.21.10 | 1.21.11 |
|---|---|---|---|---|---|
| `NativeImageBackedTexture` | image | image | **supplier** | supplier | supplier |
| `KeyBinding` category | String | String | String | **Category** | Category |
| `Screen.keyPressed` | ints | ints | ints | **KeyInput** | KeyInput |
| skin accessor | `getSkinTextures` | `getSkinTextures` | `getSkinTextures` | **`getSkin`** | `getSkin` |

So the guards are `>=1.21.8` for the texture constructor and `>=1.21.10` for the
other three. Both sit at *verified* endpoints; 1.21.5–1.21.7 and 1.21.9 are not
built and not tested, so adding one may need its guard adjusted — it will fail
loudly at compile time rather than silently, which is the right failure.

The launcher ships all five jars (`src-tauri/resources/mod/`) and
`companion.rs` installs the one matching the instance's Minecraft version, or
none at all if it's outside the set.

## Phase 14 — Minecraft's own menus, restyled
The mod now reskins the game's UI, not just its own screens.

- [x] **Ocean menu background** (`ScreenBackgroundMixin`) — stepped water bands,
      dither, hard-edged light shafts and rising bubbles, all primitives so
      there's no texture to ship and it scales with the GUI scale. Applied
      **only when no world is loaded**: in-game, `renderBackground` draws the
      translucent blur over your world, and covering that with opaque water
      would mean you couldn't see the game behind the pause menu.
- [x] **Ocean buttons** (`PressableWidgetMixin`) — targets `PressableWidget`
      rather than `ButtonWidget`, so one injection covers buttons, toggles and
      most other pressables instead of just plain ones. Cancels at HEAD so the
      vanilla nine-slice never draws.
- [x] **Title-screen branding** (`TitleScreenMixin`) — the porthole mark and
      wordmark, plus your name with a presence dot showing whether the launcher
      is up. The vanilla Minecraft logo is deliberately left alone: replacing it
      would be passing the game's own branding off as ours.
- [x] **Crew and Cosmetics buttons** on the title and pause menus, added through
      Fabric's `ScreenEvents.AFTER_INIT` rather than a mixin per screen — it
      fires after vanilla lays out its widgets, so ours don't fight other mods
      adding buttons to the same screens.
- [x] **In-game cosmetics screen** — pick a cape without alt-tabbing. Routed
      through `capes::apply_active_cape`, the *same* function the launcher UI
      calls, so wearing a cape in game announces it to other players identically.
      Two implementations of "wear a cape" would be two chances to forget the
      announcement.
- [x] All of the above is **one config switch** (`styleMenus`). Reskinning
      vanilla UI is the change most likely to collide with another mod doing the
      same, so turning it off is one obvious step.

### Fixed after the first live run (screenshots, 2026-08-02)
The first time any of this rendered, four things were wrong:

- **Bottom-row buttons overlapped.** Language and Accessibility are *icon*
  buttons (`TexturedButtonWidget`) whose `getMessage()` is the full accessible
  label — "Accessibility Settings…" — so styling them drew that entire string
  inside a 20px box, across its neighbours. Those are now skipped and left to
  vanilla, and every styled label is trimmed to its own button width so this
  class of bug can't recur.
- **The panorama never went away.** `TitleScreen` *overrides*
  `renderBackground` and never calls `super`, so the injection on `Screen` never
  fired there — every other screen had turned to water while the main menu
  hadn't. It now has its own mixin.
- **Title-screen branding removed** (mark + player name above the Minecraft
  logo). It crowded the game's own title.
- **In-game watermark removed.** The HUD led with a porthole and "BLURRED";
  that was branding occupying screen space during play. The stat rows are the
  whole HUD now, and `showWatermark` is gone from the config.

Also in this pass: a **submarine** drifting in the water behind the main menu
(sprite grid, bobbing on whole-pixel steps so it doesn't shimmer), **beveled
two-tone buttons** with corner rivets and hover bars, and **numeric ping in the
tab list** (`PlayerListPingMixin`) — the five-bar icon buckets 30ms and 140ms
identically, which is most of the range anyone cares about.

**Deliberately not done:** a cape thumbnail in the in-game list. `drawTexture`
is the least version-stable method in the client — its parameters and its
`RenderLayer` argument changed repeatedly across the five targets — so a 10x18
preview would have cost a Stonecutter branch per version. The launcher's
Cosmetics tab already previews capes properly.

**Skins are not editable in game**, on purpose: changing one is a real Mojang
API call against your account that affects every client you play on. That
belongs where the consequences can be spelled out, not behind a keybind.

### Minecraft version support (measured, 2026-08-02)
The mod targets **1.21.11** and is confirmed to compile for **1.21.10** from the
same source. Beyond that band it does not, and the reasons are hard ones:

- **1.21.8 and older: 22 compile errors.** Between those eras `SkinTextures`
  moved from `net.minecraft.client.util` to `net.minecraft.entity.player` and
  changed shape (`AssetInfo.TextureAsset` components, plus a `withOverride`
  API), `KeyBinding` categories became a type instead of a translation key,
  `Screen.keyPressed` started taking a `KeyInput` record, `NativeImageBackedTexture`
  gained a name supplier, and `GameProfile` became a record (`name()` not
  `getName()`).
- **26.1 and newer: no Yarn mappings exist at all.** Fabric's yarn line stops at
  1.21.11. Targeting the 26.x era means switching the build to Mojang official
  mappings, which renames essentially every symbol in the source.

Supporting a wide range therefore needs [Stonecutter](https://stonecutter.kikugie.dev/)
(a source preprocessor with per-version branches), not just different Gradle
properties — the properties are already overridable and that isn't the blocker.

`commands/companion.rs` now gates auto-install on `SUPPORTED_MC` and removes a
stale jar when an instance falls outside it. Installing the jar into a version
it wasn't built for doesn't degrade gracefully; it crashes the game on startup.

### Fixed
- **Avatar face crop.** Three compounding bugs: `box-sizing: border-box` plus a
  `border` meant the percentage background-size and the offset were computed
  against a box smaller than the element; `800% 800%` vertically stretched
  legacy 64x32 sheets; and the hat layer was never drawn, so skins with headwear
  rendered bald. Now sized in px with `auto` height (correct for both sheet
  sizes), ringed with a `box-shadow` that can't affect layout, and composited in
  two layers. The Cosmetics preview had hand-rolled the same maths and got the
  scale wrong (`1024%` for `800%`); it now uses the shared component.

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
