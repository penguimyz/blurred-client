import { useEffect, useRef, useState } from "react";
import {
  checkLauncherUpdate,
  installLauncherUpdate,
  onUpdateProgress,
  type UpdateProgress,
  type UpdateStatus,
} from "../lib/tauri";
import { useInstanceStore } from "../store/instanceStore";
import { useSettingsStore } from "../store/settingsStore";
import { formatBytes } from "../lib/format";

/**
 * Finds a newer release on startup and installs it.
 *
 * # What "auto" means here, and what it deliberately doesn't
 *
 * Updates install themselves: the launcher checks on startup, downloads the
 * release asset for this platform, runs the installer, and quits so the
 * installer can replace it. Nobody has to visit Settings, press Check, read a
 * version number and go and find the download page — which is what the previous
 * "notify only" behaviour asked of people, and therefore what nobody did.
 *
 * Two things hold it back, and both are about not destroying work:
 *
 *  - **A running game or server.** Installing quits the launcher, and the
 *    launcher is what's holding the game's pipes and the server's console. This
 *    is the same hazard the quit confirmation warns about; it would be absurd
 *    to warn about it and then do it unprompted. With anything running, the
 *    banner degrades to a notice.
 *  - **A grace period.** The install doesn't start the instant the check
 *    returns — there's a visible countdown with a Not now on it. An update that
 *    yanks the window away mid-click is worse than one that waits ten seconds.
 *
 * Declining is remembered for the session only. Next launch it asks again,
 * because "not right now" is not "never" — and the switch for "never" lives in
 * Settings, where a permanent choice belongs.
 */

/** How long the user has to say no before the install starts. */
const GRACE_SECONDS = 10;

/** Wait for the app to settle before doing network work on startup. */
const CHECK_DELAY_MS = 4000;

type Phase = "idle" | "offer" | "working" | "failed";

export function UpdateBanner() {
  const settings = useSettingsStore((s) => s.settings);
  const running = useInstanceStore((s) => s.running);
  const anyGameRunning = Object.keys(running).length > 0;

  const [status, setStatus] = useState<UpdateStatus | null>(null);
  const [phase, setPhase] = useState<Phase>("idle");
  const [countdown, setCountdown] = useState(GRACE_SECONDS);
  const [progress, setProgress] = useState<UpdateProgress | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [dismissed, setDismissed] = useState(false);

  // One check per app run. A settings save re-renders this component and must
  // not fire a second check, let alone a second install.
  const checked = useRef(false);

  useEffect(() => {
    if (checked.current || !settings) return;
    if (!settings.updateRepo.trim()) return;
    checked.current = true;

    const timer = window.setTimeout(() => {
      checkLauncherUpdate()
        .then((s) => {
          if (s.updateAvailable) {
            setStatus(s);
            setPhase("offer");
          }
        })
        // A failed check is not worth a banner: no network, or GitHub is having
        // a day. The Settings page still reports it when asked directly.
        .catch(() => {});
    }, CHECK_DELAY_MS);

    return () => window.clearTimeout(timer);
  }, [settings]);

  useEffect(() => {
    let unlisten: (() => void) | undefined;
    let cancelled = false;
    onUpdateProgress(setProgress).then((fn) => (cancelled ? fn() : (unlisten = fn)));
    return () => {
      cancelled = true;
      unlisten?.();
    };
  }, []);

  // The grace countdown. Only runs when installing is actually going to happen:
  // auto-install on, something to install, and nothing running that would be
  // hurt by the launcher going away.
  const willAutoInstall =
    phase === "offer" &&
    !dismissed &&
    !!status?.canInstall &&
    !!settings?.autoInstallUpdates &&
    !anyGameRunning;

  useEffect(() => {
    if (!willAutoInstall) return;
    if (countdown <= 0) {
      void install();
      return;
    }
    const t = window.setTimeout(() => setCountdown((c) => c - 1), 1000);
    return () => window.clearTimeout(t);
    // `install` is stable enough for this — it only reads state it re-reads on
    // call. Listing it would re-create the timer on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [willAutoInstall, countdown]);

  async function install() {
    setPhase("working");
    setError(null);
    try {
      await installLauncherUpdate();
      // On every platform but the .deb/.rpm handoff the process is gone before
      // this resolves. Reaching here means the system's installer took over and
      // the launcher is still up, so say so rather than sitting on a spinner.
      setPhase("idle");
      setStatus(null);
    } catch (e) {
      setError(String(e));
      setPhase("failed");
    }
  }

  if (!status || phase === "idle" || dismissed) return null;

  const version = status.latestVersion ?? "";
  const pct =
    progress && progress.total > 0 ? Math.round((progress.done / progress.total) * 100) : 0;

  return (
    <div
      role="status"
      style={{
        position: "fixed",
        top: "calc(var(--titlebar-height) + 10px)",
        left: "50%",
        transform: "translateX(-50%)",
        zIndex: 300,
        minWidth: 360,
        maxWidth: 500,
        padding: "11px 14px",
        background: "var(--glass-bg-elevated)",
        border: `1px solid ${phase === "failed" ? "var(--danger, #ff6b6b)" : "var(--accent)"}`,
        backdropFilter: "blur(12px)",
      }}
    >
      <div style={{ display: "flex", alignItems: "baseline", gap: 8, marginBottom: 6 }}>
        <span style={{ fontSize: 12.5, fontWeight: 600 }}>
          {phase === "failed"
            ? "Update failed"
            : phase === "working"
              ? progress?.phase === "install"
                ? `Installing ${version}`
                : `Downloading ${version}`
              : `Blurred Client ${version} is out`}
        </span>
        <span style={{ flex: 1 }} />
        {phase === "working" && progress && (
          <span
            style={{ fontSize: 10.5, color: "var(--text-tertiary)", fontFamily: "var(--font-mono)" }}
          >
            {pct}%
          </span>
        )}
      </div>

      {phase === "working" && (
        <div style={{ height: 4, background: "rgba(0,20,30,0.5)", overflow: "hidden" }}>
          <div
            style={{
              height: "100%",
              width: `${pct}%`,
              background: "var(--accent)",
              transition: "width 200ms linear",
            }}
          />
        </div>
      )}

      <div style={{ fontSize: 10.5, color: "var(--text-tertiary)", marginTop: 7, lineHeight: 1.55 }}>
        {phase === "failed" ? (
          <>
            {error}
            {status.url && (
              <>
                {" "}
                <a href={status.url} target="_blank" rel="noreferrer" style={{ color: "var(--accent)" }}>
                  Download it manually ↗
                </a>
              </>
            )}
          </>
        ) : phase === "working" ? (
          progress?.phase === "install"
            ? "Handing over to the installer. Blurred will close and come back on the new version."
            : `Fetching ${progress?.asset ?? status.assetName ?? "the installer"}${
                status.assetSize ? ` (${formatBytes(status.assetSize)})` : ""
              }.`
        ) : !status.canInstall ? (
          <>
            This platform has no installer in that release, so it can't update itself.{" "}
            {status.url && (
              <a href={status.url} target="_blank" rel="noreferrer" style={{ color: "var(--accent)" }}>
                Get it from the release page ↗
              </a>
            )}
          </>
        ) : anyGameRunning ? (
          "Installing closes the launcher, so it's waiting until you're out of the game — quitting now could corrupt a singleplayer world."
        ) : !settings?.autoInstallUpdates ? (
          "Automatic installs are switched off in Settings, so this one is up to you."
        ) : (
          `Installing in ${countdown}s. Blurred will close, update, and reopen.`
        )}
      </div>

      <div style={{ display: "flex", gap: 8, marginTop: 10, justifyContent: "flex-end" }}>
        {phase !== "working" && (
          <button onClick={() => setDismissed(true)} style={ghostButton}>
            Not now
          </button>
        )}
        {phase !== "working" && status.canInstall && (
          <button onClick={() => void install()} style={primaryButton}>
            {phase === "failed" ? "Try again" : "Install now"}
          </button>
        )}
      </div>
    </div>
  );
}

const baseButton: React.CSSProperties = {
  fontSize: 11,
  padding: "5px 12px",
  cursor: "pointer",
  border: "1px solid var(--glass-border)",
  background: "transparent",
  color: "var(--text-secondary)",
};

const ghostButton = baseButton;

const primaryButton: React.CSSProperties = {
  ...baseButton,
  borderColor: "var(--accent)",
  color: "var(--accent)",
};
