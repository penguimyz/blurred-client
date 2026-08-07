import { useCallback, useEffect, useRef, useState } from "react";
import { GlassCard } from "../components/GlassCard";
import { Icon } from "../components/Icon";
import { PageHeader } from "../components/PageHeader";
import * as api from "../lib/tauri";
import { formatBytes, formatDate, formatDuration, formatRelativeDate } from "../lib/format";
import { useAccountStore } from "../store/accountStore";
import type { Loader } from "../types/instance";
import type {
  PlayerList,
  Server,
  ServerBackup,
  ServerPlayers,
  ServerStatus,
} from "../types/server";

/**
 * Host a Minecraft server from the launcher.
 *
 * Each server is a folder under the data dir with its own jar, world and
 * properties. Beyond starting and stopping one, the screen covers the things
 * you otherwise end up doing by hand: who's allowed in, what the world rules
 * are, and not losing the world.
 *
 * Two things it deliberately does not do for you:
 *
 *  - **The EULA is a click.** Writing `eula=true` on someone's behalf is
 *    agreeing to a licence for them.
 *  - **It does not touch your router.** Same-network play works out of the box
 *    via the LAN address; anything beyond that needs port forwarding, which a
 *    launcher should not be doing silently.
 */
export function Servers() {
  const [servers, setServers] = useState<Server[]>([]);
  const [statuses, setStatuses] = useState<Record<string, ServerStatus>>({});
  const [openId, setOpenId] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Console scrollback, per server, kept in memory for the session.
  const [logs, setLogs] = useState<Record<string, string[]>>({});

  const refresh = useCallback(async () => {
    try {
      setServers(await api.listServers());
      const st = await api.serverStatuses();
      setStatuses(Object.fromEntries(st.map((s) => [s.id, s])));
    } catch (e) {
      setError(String(e));
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  // Poll while anything is running. Uptime and the player list change without
  // any event to hang a refresh off, and a five-second tick is cheap next to a
  // running Minecraft server.
  const anyRunning = Object.values(statuses).some((s) => s.running);
  useEffect(() => {
    if (!anyRunning) return;
    const t = window.setInterval(() => {
      api
        .serverStatuses()
        .then((st) => setStatuses(Object.fromEntries(st.map((s) => [s.id, s]))))
        .catch(() => {});
    }, 5000);
    return () => window.clearInterval(t);
  }, [anyRunning]);

  // Log + lifecycle events.
  useEffect(() => {
    const unlisteners: Array<() => void> = [];
    let cancelled = false;
    const track = (p: Promise<() => void>) =>
      p.then((fn) => (cancelled ? fn() : unlisteners.push(fn)));

    track(
      api.onServerLog((id, line) =>
        setLogs((prev) => {
          const next = [...(prev[id] ?? []), line];
          // Cap scrollback: a busy server produces thousands of lines an hour
          // and the console is a live view, not an archive (the server writes
          // its own logs/ folder anyway).
          if (next.length > 500) next.splice(0, next.length - 500);
          return { ...prev, [id]: next };
        })
      )
    );
    track(api.onServerReady(() => refresh()));
    track(api.onServerStopped(() => refresh()));

    return () => {
      cancelled = true;
      unlisteners.forEach((fn) => fn());
    };
  }, [refresh]);

  const open = servers.find((s) => s.id === openId);

  if (open) {
    return (
      <ServerDetail
        server={open}
        status={statuses[open.id]}
        log={logs[open.id] ?? []}
        onBack={() => setOpenId(null)}
        onChanged={refresh}
      />
    );
  }

  return (
    <div style={{ padding: 28, height: "100%", overflowY: "auto" }}>
      <PageHeader
        page="servers"
        actions={
          <button className="accent" onClick={() => setShowCreate(true)}>
            New server
          </button>
        }
      />

      {error && (
        <GlassCard style={{ marginBottom: 16, borderColor: "var(--danger)" }}>
          <span style={{ color: "var(--danger)", fontSize: 13 }}>{error}</span>
        </GlassCard>
      )}

      {servers.length === 0 ? (
        <GlassCard style={{ textAlign: "center", padding: 44, maxWidth: 620 }}>
          <Icon name="server" size={30} style={{ color: "var(--text-tertiary)", marginBottom: 12 }} />
          <p style={{ color: "var(--text-secondary)", fontSize: 13, margin: "0 0 16px" }}>
            No servers yet. Create one and the launcher will fetch the server jar, write the
            config, and give you an address to hand to people on your network.
          </p>
          <button className="accent" onClick={() => setShowCreate(true)}>
            Create a server
          </button>
        </GlassCard>
      ) : (
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(250px, 1fr))",
            gap: 14,
          }}
        >
          {servers.map((s) => {
            const st = statuses[s.id];
            return (
              <GlassCard key={s.id} onClick={() => setOpenId(s.id)} style={{ padding: 16 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 7, marginBottom: 6 }}>
                  {st?.running && <span className="ping" title="Running" />}
                  <span style={{ fontWeight: 600, overflow: "hidden", textOverflow: "ellipsis" }}>
                    {s.name}
                  </span>
                </div>

                <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 10 }}>
                  <Tag>{s.mcVersion}</Tag>
                  <Tag>{s.loader}</Tag>
                  <Tag>:{s.port}</Tag>
                  {s.whiteList && <Tag>whitelist</Tag>}
                  {s.hardcore && <Tag>hardcore</Tag>}
                </div>

                <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginBottom: 12 }}>
                  {st?.running
                    ? st.ready
                      ? `${st.players.length}/${s.maxPlayers} online · up ${formatDuration(st.uptimeSeconds)}`
                      : "Starting…"
                    : s.lastStarted
                      ? `Last run ${formatRelativeDate(s.lastStarted)}`
                      : "Never started"}
                </div>

                {!s.eulaAccepted && (
                  <div style={{ fontSize: 11, color: "var(--warning)", marginBottom: 10 }}>
                    Needs EULA acceptance
                  </div>
                )}

                <button
                  className={st?.running ? undefined : "accent"}
                  onClick={(e) => {
                    e.stopPropagation();
                    const fn = st?.running ? api.stopServer : api.startServer;
                    fn(s.id).catch((err) => setError(String(err)));
                  }}
                  style={{
                    width: "100%",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    gap: 6,
                  }}
                  disabled={!s.eulaAccepted && !st?.running}
                >
                  <Icon name={st?.running ? "stop" : "play"} size={13} />
                  {st?.running ? "Stop" : "Start"}
                </button>
              </GlassCard>
            );
          })}
        </div>
      )}

      {showCreate && (
        <CreateServerModal
          onClose={() => setShowCreate(false)}
          onDone={async () => {
            setShowCreate(false);
            await refresh();
          }}
        />
      )}
    </div>
  );
}

type DetailTab = "console" | "settings" | "players" | "backups";

function ServerDetail({
  server,
  status,
  log,
  onBack,
  onChanged,
}: {
  server: Server;
  status?: ServerStatus;
  log: string[];
  onBack: () => void;
  onChanged: () => void;
}) {
  const [tab, setTab] = useState<DetailTab>("console");
  const [error, setError] = useState<string | null>(null);
  const running = !!status?.running;

  return (
    <div style={{ padding: 28, height: "100%", overflowY: "auto" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 16 }}>
        <button onClick={onBack}>Back</button>
        <h1 style={{ margin: 0, fontSize: 17, flex: 1 }}>{server.name}</h1>
        {running && <span className="ping" />}
        <button onClick={() => api.openServerFolder(server.id).catch(() => {})}>Folder</button>
      </div>

      {error && (
        <GlassCard style={{ marginBottom: 14, borderColor: "var(--danger)" }}>
          <span style={{ color: "var(--danger)", fontSize: 13 }}>{error}</span>
        </GlassCard>
      )}

      {!server.eulaAccepted && (
        <GlassCard style={{ marginBottom: 14, borderColor: "var(--warning)" }}>
          <div style={{ fontSize: 13, marginBottom: 10, lineHeight: 1.6 }}>
            Minecraft servers require you to accept the{" "}
            <a
              href="https://aka.ms/MinecraftEULA"
              target="_blank"
              rel="noreferrer"
              style={{ color: "var(--accent)" }}
            >
              Minecraft EULA
            </a>
            . The launcher won't do this for you — accepting a licence is your call, not
            software's.
          </div>
          <button
            className="accent"
            onClick={() =>
              api.acceptEula(server.id).then(onChanged).catch((e) => setError(String(e)))
            }
          >
            I accept the EULA
          </button>
        </GlassCard>
      )}

      <StatusCard server={server} status={status} onError={setError} onChanged={onChanged} />

      <div style={{ display: "flex", gap: 6, margin: "16px 0 14px", flexWrap: "wrap" }}>
        {(["console", "settings", "players", "backups"] as const).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            style={{
              textTransform: "capitalize",
              background: tab === t ? "var(--accent)" : undefined,
              color: tab === t ? "var(--accent-fg)" : undefined,
            }}
          >
            {t}
          </button>
        ))}
      </div>

      {tab === "console" && <ConsoleTab server={server} running={running} log={log} onError={setError} />}
      {tab === "settings" && (
        <SettingsTab server={server} running={running} onChanged={onChanged} onError={setError} />
      )}
      {tab === "players" && (
        <PlayersTab server={server} status={status} running={running} onError={setError} />
      )}
      {tab === "backups" && <BackupsTab server={server} running={running} onError={setError} />}
    </div>
  );
}

/** Controls, address and live numbers. Always visible above the tabs. */
function StatusCard({
  server,
  status,
  onError,
  onChanged,
}: {
  server: Server;
  status?: ServerStatus;
  onError: (e: string) => void;
  onChanged: () => void;
}) {
  const running = !!status?.running;

  return (
    <GlassCard>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center" }}>
        <button
          className={running ? undefined : "accent"}
          disabled={!server.eulaAccepted && !running}
          onClick={() =>
            (running ? api.stopServer : api.startServer)(server.id)
              .then(onChanged)
              .catch((e) => onError(String(e)))
          }
          style={{ display: "flex", alignItems: "center", gap: 6 }}
        >
          <Icon name={running ? "stop" : "play"} size={14} />
          {running ? "Stop" : "Start"}
        </button>
        {running && (
          <button
            onClick={() => api.killServer(server.id).catch((e) => onError(String(e)))}
            title="Force kill — skips saving the world"
            style={{ color: "var(--danger)" }}
          >
            Force kill
          </button>
        )}
        <div style={{ flex: 1 }} />
        <span style={{ fontSize: 12, color: "var(--text-tertiary)" }}>
          {running ? (status?.ready ? "Accepting players" : "Starting…") : "Stopped"}
        </span>
      </div>

      {running && (
        <div style={{ display: "flex", gap: 22, marginTop: 14, flexWrap: "wrap" }}>
          <Metric label="Players" value={`${status?.players.length ?? 0} / ${server.maxPlayers}`} />
          <Metric label="Uptime" value={formatDuration(status?.uptimeSeconds ?? 0)} />
          <Metric label="Memory" value={`${server.maxMemoryMb} MB`} />
          <Metric label="World" value={server.levelName} />
        </div>
      )}

      <div style={{ marginTop: 14, fontSize: 12.5, lineHeight: 1.7 }}>
        <div>
          <strong>On this machine:</strong>{" "}
          <code style={{ color: "var(--accent)" }}>localhost:{server.port}</code>
        </div>
        <div>
          <strong>Same network:</strong>{" "}
          {status?.lanAddress ? (
            <code style={{ color: "var(--accent)" }}>
              {status.lanAddress}:{server.port}
            </code>
          ) : (
            <span style={{ color: "var(--text-tertiary)" }}>start the server to see it</span>
          )}
        </div>
        <div style={{ color: "var(--text-tertiary)", fontSize: 11.5, marginTop: 6 }}>
          Playing with people outside your network needs port {server.port} forwarded on your
          router and allowed through your firewall. The launcher deliberately doesn't change
          either — opening a port to the internet shouldn't happen quietly.
        </div>
      </div>
    </GlassCard>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div style={{ fontSize: 9.5, color: "var(--text-tertiary)", letterSpacing: "0.06em" }}>
        {label.toUpperCase()}
      </div>
      <div style={{ fontSize: 14, fontFamily: "var(--font-pixel)" }}>{value}</div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Console
// ---------------------------------------------------------------------------

function ConsoleTab({
  server,
  running,
  log,
  onError,
}: {
  server: Server;
  running: boolean;
  log: string[];
  onError: (e: string) => void;
}) {
  const [cmd, setCmd] = useState("");
  /** Command history, newest last. Walked with the arrow keys. */
  const history = useRef<string[]>([]);
  const historyPos = useRef<number | null>(null);
  const consoleRef = useRef<HTMLPreElement>(null);

  // Stick the console to the bottom as lines arrive.
  useEffect(() => {
    const el = consoleRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [log]);

  async function send() {
    const c = cmd.trim();
    if (!c) return;
    history.current.push(c);
    historyPos.current = null;
    setCmd("");
    await api.serverCommand(server.id, c).catch((e) => onError(String(e)));
  }

  /** Up/down walk previously sent commands, like any other console. */
  function onKey(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter") {
      send();
      return;
    }
    if (e.key !== "ArrowUp" && e.key !== "ArrowDown") return;
    if (history.current.length === 0) return;
    e.preventDefault();

    const last = history.current.length - 1;
    const current = historyPos.current;
    const next =
      e.key === "ArrowUp"
        ? current === null
          ? last
          : Math.max(0, current - 1)
        : current === null
          ? null
          : current >= last
            ? null
            : current + 1;

    historyPos.current = next;
    setCmd(next === null ? "" : history.current[next]);
  }

  return (
    <>
      <GlassCard style={{ marginBottom: 14, padding: 0, overflow: "hidden" }}>
        <div
          style={{
            padding: "8px 14px",
            borderBottom: "2px solid var(--glass-border)",
            fontSize: 10,
            textTransform: "uppercase",
            color: "var(--text-tertiary)",
          }}
        >
          Console
        </div>
        <pre
          ref={consoleRef}
          style={{
            margin: 0,
            padding: "10px 14px",
            height: 320,
            overflow: "auto",
            fontFamily: "var(--font-mono)",
            fontSize: 11.5,
            lineHeight: 1.5,
            color: "var(--text-secondary)",
            whiteSpace: "pre-wrap",
            wordBreak: "break-word",
            background: "rgba(0,12,20,0.4)",
          }}
        >
          {log.length ? log.join("\n") : "Not running."}
        </pre>
        <div style={{ display: "flex", gap: 8, padding: 10, borderTop: "2px solid var(--glass-border)" }}>
          <input
            value={cmd}
            onChange={(e) => setCmd(e.target.value)}
            onKeyDown={onKey}
            disabled={!running}
            placeholder={
              running
                ? "say hello   /   op someone   /   stop        (↑ for history)"
                : "Start the server to send commands"
            }
            style={{ flex: 1, padding: "7px 10px", fontSize: 12, fontFamily: "var(--font-mono)" }}
          />
          <button onClick={send} disabled={!running || !cmd.trim()}>
            Send
          </button>
        </div>
      </GlassCard>

      {running && (
        <GlassCard>
          <div style={{ fontSize: 10, color: "var(--text-tertiary)", marginBottom: 8, textTransform: "uppercase" }}>
            Quick commands
          </div>
          <div style={{ display: "flex", gap: 7, flexWrap: "wrap" }}>
            {QUICK_COMMANDS.map(([label, command]) => (
              <button
                key={command}
                title={command}
                onClick={() => api.serverCommand(server.id, command).catch((e) => onError(String(e)))}
                style={{ fontSize: 11.5 }}
              >
                {label}
              </button>
            ))}
          </div>
        </GlassCard>
      )}
    </>
  );
}

/** The console commands worth a button. Everything else is one line of typing. */
const QUICK_COMMANDS: Array<[string, string]> = [
  ["Save world", "save-all"],
  ["Day", "time set day"],
  ["Night", "time set night"],
  ["Clear weather", "weather clear"],
  ["Rain", "weather rain"],
  ["List players", "list"],
];

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

function SettingsTab({
  server,
  running,
  onChanged,
  onError,
}: {
  server: Server;
  running: boolean;
  onChanged: () => void;
  onError: (e: string) => void;
}) {
  const [draft, setDraft] = useState<Server>(server);
  const [saved, setSaved] = useState(false);

  useEffect(() => setDraft(server), [server]);

  const set = <K extends keyof Server>(k: K, v: Server[K]) =>
    setDraft((d) => ({ ...d, [k]: v }));

  async function save() {
    try {
      await api.updateServer(draft);
      setSaved(true);
      setTimeout(() => setSaved(false), 1500);
      onChanged();
    } catch (e) {
      onError(String(e));
    }
  }

  return (
    <>
      {running && (
        <div style={{ fontSize: 11.5, color: "var(--warning)", marginBottom: 12 }}>
          The server is running — changes apply the next time it starts.
        </div>
      )}

      <GlassCard style={{ marginBottom: 14 }}>
        <h3 style={{ margin: "0 0 14px", fontSize: 13 }}>Basics</h3>
        <div style={grid}>
          <Field label="Name">
            <input value={draft.name} onChange={(e) => set("name", e.target.value)} style={inp} />
          </Field>
          <Field label="Port">
            <input
              type="number"
              value={draft.port}
              onChange={(e) => set("port", Number(e.target.value))}
              style={inp}
            />
          </Field>
          <Field label="Max memory (MB)">
            <input
              type="number"
              step={512}
              value={draft.maxMemoryMb}
              onChange={(e) => set("maxMemoryMb", Number(e.target.value))}
              style={inp}
            />
          </Field>
          <Field label="Max players">
            <input
              type="number"
              value={draft.maxPlayers}
              onChange={(e) => set("maxPlayers", Number(e.target.value))}
              style={inp}
            />
          </Field>
          <Field label="Gamemode">
            <select value={draft.gamemode} onChange={(e) => set("gamemode", e.target.value)} style={inp}>
              <option value="survival">Survival</option>
              <option value="creative">Creative</option>
              <option value="adventure">Adventure</option>
              <option value="spectator">Spectator</option>
            </select>
          </Field>
          <Field label="Difficulty">
            <select value={draft.difficulty} onChange={(e) => set("difficulty", e.target.value)} style={inp}>
              <option value="peaceful">Peaceful</option>
              <option value="easy">Easy</option>
              <option value="normal">Normal</option>
              <option value="hard">Hard</option>
            </select>
          </Field>
        </div>

        <div style={{ marginTop: 12 }}>
          <Field label="MOTD (shown in the server list)">
            <input value={draft.motd} onChange={(e) => set("motd", e.target.value)} style={inp} />
          </Field>
        </div>
      </GlassCard>

      <GlassCard style={{ marginBottom: 14 }}>
        <h3 style={{ margin: "0 0 14px", fontSize: 13 }}>World</h3>
        <div style={grid}>
          <Field label="World folder">
            <input
              value={draft.levelName}
              onChange={(e) => set("levelName", e.target.value)}
              style={inp}
            />
          </Field>
          <Field label="Seed (blank = random)">
            <input
              value={draft.levelSeed}
              onChange={(e) => set("levelSeed", e.target.value)}
              placeholder="e.g. -428876591"
              style={inp}
            />
          </Field>
          <Field label="View distance (chunks)">
            <input
              type="number"
              min={3}
              max={32}
              value={draft.viewDistance}
              onChange={(e) => set("viewDistance", Number(e.target.value))}
              style={inp}
            />
          </Field>
          <Field label="Simulation distance">
            <input
              type="number"
              min={3}
              max={32}
              value={draft.simulationDistance}
              onChange={(e) => set("simulationDistance", Number(e.target.value))}
              style={inp}
            />
          </Field>
          <Field label="Spawn protection (blocks)">
            <input
              type="number"
              min={0}
              value={draft.spawnProtection}
              onChange={(e) => set("spawnProtection", Number(e.target.value))}
              style={inp}
            />
          </Field>
        </div>
        <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginTop: 4, lineHeight: 1.55 }}>
          Changing the world folder or seed starts a <em>different</em> world rather than altering
          the current one — the old folder stays on disk untouched. View distance is the single
          biggest lever on server CPU and bandwidth; 6–8 is plenty for a handful of friends.
        </div>
      </GlassCard>

      <GlassCard style={{ marginBottom: 14 }}>
        <h3 style={{ margin: "0 0 14px", fontSize: 13 }}>Rules</h3>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))", gap: 12 }}>
          <Check
            checked={draft.onlineMode}
            onChange={(v) => set("onlineMode", v)}
            label="Online mode"
            hint="Verify players against Mojang. Turning this off lets cracked clients join — and lets anyone claim any username."
          />
          <Check checked={draft.pvp} onChange={(v) => set("pvp", v)} label="PvP" hint="Players can damage each other." />
          <Check
            checked={draft.whiteList}
            onChange={(v) => set("whiteList", v)}
            label="Whitelist only"
            hint="Only names on the Players tab can join."
          />
          <Check
            checked={draft.hardcore}
            onChange={(v) => set("hardcore", v)}
            label="Hardcore"
            hint="Death is permanent and difficulty is locked to hard."
          />
          <Check
            checked={draft.allowNether}
            onChange={(v) => set("allowNether", v)}
            label="Nether"
            hint="Allow travel to the Nether."
          />
          <Check
            checked={draft.allowFlight}
            onChange={(v) => set("allowFlight", v)}
            label="Allow flight"
            hint="Needed for flight mods; without it the server kicks players who hover."
          />
          <Check
            checked={draft.enableCommandBlock}
            onChange={(v) => set("enableCommandBlock", v)}
            label="Command blocks"
            hint="Let command blocks run. Off by default in vanilla."
          />
          <Check
            checked={draft.forceGamemode}
            onChange={(v) => set("forceGamemode", v)}
            label="Force gamemode"
            hint="Put players back into the default gamemode each time they join."
          />
        </div>
      </GlassCard>

      <GlassCard>
        <h3 style={{ margin: "0 0 14px", fontSize: 13 }}>Hosting</h3>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))", gap: 12 }}>
          <Check
            checked={draft.autoRestart}
            onChange={(v) => set("autoRestart", v)}
            label="Restart after a crash"
            hint="Brings the server back up 5s after an exit nobody asked for. Pressing Stop still stops it."
          />
          <Check
            checked={draft.backupOnStart}
            onChange={(v) => set("backupOnStart", v)}
            label="Back up before each start"
            hint="Snapshots the world into backups/ before launching. Cheap insurance against a bad version change."
          />
        </div>

        <div style={{ display: "flex", gap: 10, marginTop: 16, alignItems: "center" }}>
          <button className="accent" onClick={save}>Save</button>
          {saved && <span style={{ fontSize: 12, color: "var(--success)" }}>Saved</span>}
          <div style={{ flex: 1 }} />
          <button
            style={{ color: "var(--danger)" }}
            onClick={async () => {
              if (!confirm(`Delete "${server.name}"? This removes its world and all its files.`)) return;
              try {
                await api.deleteServer(server.id);
                onChanged();
              } catch (e) {
                onError(String(e));
              }
            }}
          >
            Delete server
          </button>
        </div>
      </GlassCard>
    </>
  );
}

// ---------------------------------------------------------------------------
// Players
// ---------------------------------------------------------------------------

const LIST_LABELS: Record<PlayerList, { title: string; blurb: string; verb: string }> = {
  ops: {
    title: "Operators",
    blurb: "Full command access. Level 4 is what a single-player host expects.",
    verb: "Op",
  },
  whitelist: {
    title: "Whitelist",
    blurb: "Only used while \"Whitelist only\" is on in Settings.",
    verb: "Add",
  },
  banned: {
    title: "Banned",
    blurb: "Kicked on sight, with the reason shown to them.",
    verb: "Ban",
  },
};

function PlayersTab({
  server,
  status,
  running,
  onError,
}: {
  server: Server;
  status?: ServerStatus;
  running: boolean;
  onError: (e: string) => void;
}) {
  const [players, setPlayers] = useState<ServerPlayers | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(() => {
    api.listServerPlayers(server.id).then(setPlayers).catch((e) => onError(String(e)));
  }, [server.id, onError]);

  useEffect(refresh, [refresh]);

  async function run(fn: () => Promise<ServerPlayers>) {
    setBusy(true);
    try {
      setPlayers(await fn());
    } catch (e) {
      onError(String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <GlassCard style={{ marginBottom: 14 }}>
        <h3 style={{ margin: "0 0 4px", fontSize: 13 }}>Online now</h3>
        <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginBottom: 10 }}>
          Read from the server console, so it's accurate without RCON or a query port.
        </div>
        {!running ? (
          <div style={{ fontSize: 12.5, color: "var(--text-tertiary)" }}>The server isn't running.</div>
        ) : status?.players.length ? (
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            {status.players.map((name) => (
              <div
                key={name}
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 8,
                  padding: "5px 6px 5px 10px",
                  border: "1px solid var(--glass-border)",
                  background: "rgba(125,226,240,0.06)",
                }}
              >
                <span className="ping" />
                <span style={{ fontSize: 12.5 }}>{name}</span>
                <button
                  onClick={() => api.kickServerPlayer(server.id, name).catch((e) => onError(String(e)))}
                  title={`Kick ${name}`}
                  style={{ fontSize: 10, padding: "3px 6px" }}
                >
                  Kick
                </button>
                <button
                  onClick={() => run(() => api.addServerPlayer(server.id, "banned", name))}
                  title={`Ban ${name}`}
                  style={{ fontSize: 10, padding: "3px 6px", color: "var(--danger)" }}
                >
                  Ban
                </button>
              </div>
            ))}
          </div>
        ) : (
          <div style={{ fontSize: 12.5, color: "var(--text-tertiary)" }}>Nobody's connected.</div>
        )}
      </GlassCard>

      {(["ops", "whitelist", "banned"] as const).map((list) => (
        <PlayerListCard
          key={list}
          list={list}
          entries={players?.[list] ?? []}
          busy={busy}
          onAdd={(name, level, reason) =>
            run(() => api.addServerPlayer(server.id, list, name, { level, reason }))
          }
          onRemove={(name) => run(() => api.removeServerPlayer(server.id, list, name))}
        />
      ))}

      <div style={{ fontSize: 11, color: "var(--text-tertiary)", lineHeight: 1.6, maxWidth: 620 }}>
        {running
          ? "The server is running, so changes go through its console — the same as typing the command yourself."
          : "The server is stopped, so these are written straight to ops.json, whitelist.json and banned-players.json. Usernames are looked up against Mojang for their UUID, which is what an online-mode server matches on."}
      </div>
    </>
  );
}

function PlayerListCard({
  list,
  entries,
  busy,
  onAdd,
  onRemove,
}: {
  list: PlayerList;
  entries: ServerPlayers[PlayerList];
  busy: boolean;
  onAdd: (name: string, level?: number, reason?: string) => void;
  onRemove: (name: string) => void;
}) {
  const [name, setName] = useState("");
  const [level, setLevel] = useState(4);
  const [reason, setReason] = useState("");
  const meta = LIST_LABELS[list];

  function submit() {
    const n = name.trim();
    if (!n) return;
    onAdd(n, list === "ops" ? level : undefined, list === "banned" ? reason.trim() : undefined);
    setName("");
    setReason("");
  }

  return (
    <GlassCard style={{ marginBottom: 14 }}>
      <h3 style={{ margin: "0 0 2px", fontSize: 13 }}>
        {meta.title}
        <span style={{ color: "var(--text-tertiary)", fontWeight: 400 }}> · {entries.length}</span>
      </h3>
      <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginBottom: 10 }}>{meta.blurb}</div>

      {entries.length > 0 && (
        <div style={{ display: "flex", flexDirection: "column", gap: 4, marginBottom: 12 }}>
          {entries.map((p) => (
            <div
              key={p.name}
              style={{
                display: "flex",
                alignItems: "center",
                gap: 10,
                padding: "6px 10px",
                border: "1px solid var(--glass-border)",
              }}
            >
              <span style={{ fontSize: 12.5, minWidth: 120 }}>{p.name}</span>
              {p.level !== null && (
                <span style={{ fontSize: 10.5, color: "var(--text-tertiary)" }}>level {p.level}</span>
              )}
              {p.reason && (
                <span
                  style={{
                    fontSize: 10.5,
                    color: "var(--text-tertiary)",
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                  }}
                >
                  {p.reason}
                </span>
              )}
              {!p.uuid && (
                <span style={{ fontSize: 10, color: "var(--warning)" }} title="No Mojang UUID — ignored by an online-mode server">
                  no UUID
                </span>
              )}
              <div style={{ flex: 1 }} />
              <button
                onClick={() => onRemove(p.name)}
                disabled={busy}
                style={{ fontSize: 10.5, padding: "3px 8px" }}
              >
                Remove
              </button>
            </div>
          ))}
        </div>
      )}

      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && submit()}
          placeholder="Minecraft username"
          style={{ ...inp, width: 190 }}
          disabled={busy}
        />
        {list === "ops" && (
          <select value={level} onChange={(e) => setLevel(Number(e.target.value))} style={{ ...inp, width: 130 }}>
            <option value={1}>Level 1 — bypass spawn</option>
            <option value={2}>Level 2 — cheats</option>
            <option value={3}>Level 3 — kick/ban</option>
            <option value={4}>Level 4 — everything</option>
          </select>
        )}
        {list === "banned" && (
          <input
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && submit()}
            placeholder="Reason (optional)"
            style={{ ...inp, width: 200 }}
            disabled={busy}
          />
        )}
        <button onClick={submit} disabled={busy || !name.trim()}>
          {meta.verb}
        </button>
      </div>
    </GlassCard>
  );
}

// ---------------------------------------------------------------------------
// Backups
// ---------------------------------------------------------------------------

function BackupsTab({
  server,
  running,
  onError,
}: {
  server: Server;
  running: boolean;
  onError: (e: string) => void;
}) {
  const [backups, setBackups] = useState<ServerBackup[]>([]);
  const [busy, setBusy] = useState<string | null>(null);

  const refresh = useCallback(() => {
    api.listServerBackups(server.id).then(setBackups).catch((e) => onError(String(e)));
  }, [server.id, onError]);

  useEffect(refresh, [refresh]);

  async function run(label: string, fn: () => Promise<unknown>) {
    setBusy(label);
    try {
      await fn();
      refresh();
    } catch (e) {
      onError(String(e));
    } finally {
      setBusy(null);
    }
  }

  return (
    <GlassCard>
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
        <h3 style={{ margin: 0, fontSize: 13, flex: 1 }}>World backups</h3>
        <button
          className="accent"
          disabled={busy !== null}
          onClick={() => run("create", () => api.createServerBackup(server.id))}
        >
          {busy === "create" ? "Backing up…" : "Back up now"}
        </button>
      </div>
      <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginBottom: 14, lineHeight: 1.6, maxWidth: 620 }}>
        A zip of the <code>{server.levelName}</code> folder, kept in the server's{" "}
        <code>backups/</code> directory.{" "}
        {running
          ? "The server is running, so it'll be asked to flush chunks to disk first."
          : "Restoring replaces the live world — and takes a snapshot of it first, so an accidental restore is undoable."}
      </div>

      {backups.length === 0 ? (
        <div style={{ fontSize: 12.5, color: "var(--text-tertiary)" }}>
          No backups yet.
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
          {backups.map((b) => (
            <div
              key={b.file}
              style={{
                display: "flex",
                alignItems: "center",
                gap: 12,
                padding: "7px 10px",
                border: "1px solid var(--glass-border)",
              }}
            >
              <span style={{ fontSize: 12, fontFamily: "var(--font-mono)", flex: 1, minWidth: 0, overflow: "hidden", textOverflow: "ellipsis" }}>
                {b.file}
              </span>
              <span style={{ fontSize: 10.5, color: "var(--text-tertiary)", whiteSpace: "nowrap" }}>
                {formatBytes(b.sizeBytes)}
              </span>
              <span style={{ fontSize: 10.5, color: "var(--text-tertiary)", whiteSpace: "nowrap" }}>
                {formatDate(b.createdAt)}
              </span>
              <button
                disabled={running || busy !== null}
                title={running ? "Stop the server before restoring" : "Replace the live world with this backup"}
                onClick={() => {
                  if (!confirm(`Replace the current world with "${b.file}"?\n\nThe world as it is now will be backed up first.`)) return;
                  run(b.file, () => api.restoreServerBackup(server.id, b.file));
                }}
                style={{ fontSize: 10.5, padding: "3px 8px" }}
              >
                {busy === b.file ? "Restoring…" : "Restore"}
              </button>
              <button
                disabled={busy !== null}
                onClick={() => {
                  if (!confirm(`Delete backup "${b.file}"?`)) return;
                  run(b.file, () => api.deleteServerBackup(server.id, b.file));
                }}
                style={{ fontSize: 10.5, padding: "3px 8px", color: "var(--danger)" }}
              >
                <Icon name="trash" size={11} />
              </button>
            </div>
          ))}
        </div>
      )}
    </GlassCard>
  );
}

// ---------------------------------------------------------------------------
// Create
// ---------------------------------------------------------------------------

/**
 * Starting points, so a new server is playable without a tour of the Settings
 * tab first. Each is just a set of defaults — everything stays editable.
 */
const PRESETS = {
  survival: {
    label: "Survival SMP",
    hint: "Normal difficulty, PvP on, whitelisted. The usual friends-server setup.",
    apply: (s: Server): Server => ({
      ...s,
      gamemode: "survival",
      difficulty: "normal",
      pvp: true,
      whiteList: true,
      backupOnStart: true,
    }),
  },
  creative: {
    label: "Creative build",
    hint: "Creative, peaceful, flight allowed, no spawn protection.",
    apply: (s: Server): Server => ({
      ...s,
      gamemode: "creative",
      difficulty: "peaceful",
      pvp: false,
      allowFlight: true,
      spawnProtection: 0,
      enableCommandBlock: true,
    }),
  },
  hardcore: {
    label: "Hardcore",
    hint: "One life each. Hard difficulty, locked.",
    apply: (s: Server): Server => ({
      ...s,
      gamemode: "survival",
      difficulty: "hard",
      hardcore: true,
      pvp: true,
      backupOnStart: true,
    }),
  },
} as const;

type PresetKey = keyof typeof PRESETS;

function CreateServerModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const accounts = useAccountStore((s) => s.accounts);
  // Named after whoever is signed in rather than a stock string — it's their
  // server, and "My SMP" is one more field to clear. Most recently used
  // account, which is the same rule the launchpad uses to pick a default.
  const owner =
    accounts
      .slice()
      .sort((a, b) => (b.lastUsed ?? "").localeCompare(a.lastUsed ?? ""))[0]?.username ?? "";

  const [name, setName] = useState(owner ? `${owner}'s SMP` : "");
  const [mcVersion, setMcVersion] = useState("1.21.1");
  const [loader, setLoader] = useState<Loader>("vanilla");
  const [port, setPort] = useState(25565);
  const [preset, setPreset] = useState<PresetKey>("survival");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      // Create first, then apply the preset as an update. `create_server` owns
      // the defaults and the id; layering the preset on top afterwards means
      // there's one place that knows how to build a server.
      const created = await api.createServer(name.trim(), mcVersion.trim(), loader, port);
      await api.updateServer(PRESETS[preset].apply(created));
      onDone();
    } catch (e) {
      setError(String(e));
      setBusy(false);
    }
  }

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(0,10,16,0.6)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 200,
      }}
      onClick={busy ? undefined : onClose}
    >
      <div className="glass-card" style={{ width: 420, padding: 20 }} onClick={(e) => e.stopPropagation()}>
        <h2 style={{ marginTop: 0, fontSize: 15 }}>New server</h2>

        <Field label="Name">
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Server name" style={inp} disabled={busy} />
        </Field>
        <div style={{ display: "flex", gap: 10 }}>
          <Field label="Minecraft version">
            <input value={mcVersion} onChange={(e) => setMcVersion(e.target.value)} style={inp} disabled={busy} />
          </Field>
          <Field label="Port">
            <input type="number" value={port} onChange={(e) => setPort(Number(e.target.value))} style={inp} disabled={busy} />
          </Field>
        </div>
        <Field label="Loader">
          <select value={loader} onChange={(e) => setLoader(e.target.value as Loader)} style={inp} disabled={busy}>
            <option value="vanilla">Vanilla</option>
            <option value="fabric">Fabric</option>
          </select>
        </Field>

        <div style={{ fontSize: 11, color: "var(--text-secondary)", marginBottom: 6 }}>Start from</div>
        <div style={{ display: "flex", flexDirection: "column", gap: 4, marginBottom: 12 }}>
          {(Object.keys(PRESETS) as PresetKey[]).map((key) => (
            <label
              key={key}
              style={{
                display: "flex",
                gap: 9,
                alignItems: "flex-start",
                padding: "7px 9px",
                border: `1px solid ${preset === key ? "var(--accent)" : "var(--glass-border)"}`,
                background: preset === key ? "var(--glass-bg-elevated)" : "transparent",
                cursor: "pointer",
              }}
            >
              <input
                type="radio"
                checked={preset === key}
                onChange={() => setPreset(key)}
                style={{ marginTop: 2 }}
                disabled={busy}
              />
              <span>
                <div style={{ fontSize: 12.5 }}>{PRESETS[key].label}</div>
                <div style={{ fontSize: 10.5, color: "var(--text-tertiary)", lineHeight: 1.45 }}>
                  {PRESETS[key].hint}
                </div>
              </span>
            </label>
          ))}
        </div>

        <div style={{ fontSize: 11, color: "var(--text-tertiary)", margin: "0 0 12px", lineHeight: 1.55 }}>
          The server jar downloads the first time you start it. You'll need to accept the
          Minecraft EULA before it will run.
        </div>

        {error && <div style={{ color: "var(--danger)", fontSize: 12, marginBottom: 10 }}>{error}</div>}

        <div style={{ display: "flex", gap: 8 }}>
          <button onClick={onClose} style={{ flex: 1 }} disabled={busy}>Cancel</button>
          <button className="accent" style={{ flex: 1 }} disabled={!name.trim() || busy} onClick={submit}>
            {busy ? "Creating…" : "Create"}
          </button>
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 10, flex: 1 }}>
      <label style={{ fontSize: 11, color: "var(--text-secondary)", display: "block", marginBottom: 4 }}>
        {label}
      </label>
      {children}
    </div>
  );
}

function Check({
  checked,
  onChange,
  label,
  hint,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  label: string;
  hint: string;
}) {
  return (
    <label style={{ display: "flex", gap: 8, alignItems: "flex-start", cursor: "pointer" }}>
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} style={{ marginTop: 2 }} />
      <span>
        <div style={{ fontSize: 12.5 }}>{label}</div>
        <div style={{ fontSize: 10.5, color: "var(--text-tertiary)", lineHeight: 1.45 }}>{hint}</div>
      </span>
    </label>
  );
}

function Tag({ children }: { children: React.ReactNode }) {
  return (
    <span
      style={{
        fontSize: 10,
        padding: "2px 7px",
        background: "rgba(125,226,240,0.1)",
        border: "1px solid var(--glass-border)",
        color: "var(--text-secondary)",
      }}
    >
      {children}
    </span>
  );
}

const inp: React.CSSProperties = { width: "100%", padding: "7px 9px", fontSize: 12.5 };
const grid: React.CSSProperties = {
  display: "grid",
  gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))",
  gap: 12,
};
