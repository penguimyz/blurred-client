import { useCallback, useEffect, useRef, useState } from "react";
import { GlassCard } from "../components/GlassCard";
import { Icon } from "../components/Icon";
import { PageHeader } from "../components/PageHeader";
import * as api from "../lib/tauri";
import { formatRelativeDate } from "../lib/format";
import type { Loader } from "../types/instance";
import type { Server, ServerStatus } from "../types/server";

/**
 * Host a Minecraft server from the launcher.
 *
 * Each server is a folder under the data dir with its own jar, world and
 * properties. The screen is honest about the two things it deliberately does
 * not do for you:
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
                </div>

                <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginBottom: 12 }}>
                  {st?.running
                    ? st.ready
                      ? "Running — accepting players"
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
  const [draft, setDraft] = useState<Server>(server);
  const [cmd, setCmd] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const consoleRef = useRef<HTMLPreElement>(null);

  useEffect(() => setDraft(server), [server]);

  // Stick the console to the bottom as lines arrive.
  useEffect(() => {
    const el = consoleRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [log]);

  const running = !!status?.running;
  const set = <K extends keyof Server>(k: K, v: Server[K]) =>
    setDraft((d) => ({ ...d, [k]: v }));

  async function save() {
    setError(null);
    try {
      await api.updateServer(draft);
      setSaved(true);
      setTimeout(() => setSaved(false), 1500);
      onChanged();
    } catch (e) {
      setError(String(e));
    }
  }

  async function send() {
    const c = cmd.trim();
    if (!c) return;
    setCmd("");
    await api.serverCommand(server.id, c).catch((e) => setError(String(e)));
  }

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

      {/* Address + controls */}
      <GlassCard style={{ marginBottom: 14 }}>
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center" }}>
          <button
            className={running ? undefined : "accent"}
            disabled={!server.eulaAccepted && !running}
            onClick={() =>
              (running ? api.stopServer : api.startServer)(server.id).catch((e) =>
                setError(String(e))
              )
            }
            style={{ display: "flex", alignItems: "center", gap: 6 }}
          >
            <Icon name={running ? "stop" : "play"} size={14} />
            {running ? "Stop" : "Start"}
          </button>
          {running && (
            <button
              onClick={() => api.killServer(server.id).catch((e) => setError(String(e)))}
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

      {/* Console */}
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
            height: 260,
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
            onKeyDown={(e) => e.key === "Enter" && send()}
            disabled={!running}
            placeholder={running ? "say hello   /   op someone   /   stop" : "Start the server to send commands"}
            style={{ flex: 1, padding: "7px 10px", fontSize: 12, fontFamily: "var(--font-mono)" }}
          />
          <button onClick={send} disabled={!running || !cmd.trim()}>
            Send
          </button>
        </div>
      </GlassCard>

      {/* Settings */}
      <GlassCard>
        <h3 style={{ margin: "0 0 14px", fontSize: 13 }}>Settings</h3>
        {running && (
          <div style={{ fontSize: 11.5, color: "var(--warning)", marginBottom: 12 }}>
            The server is running — changes apply the next time it starts.
          </div>
        )}
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 12 }}>
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

        <div style={{ display: "flex", gap: 16, marginTop: 14, flexWrap: "wrap" }}>
          <Check
            checked={draft.onlineMode}
            onChange={(v) => set("onlineMode", v)}
            label="Online mode"
            hint="Verify players against Mojang. Turning this off lets cracked clients join."
          />
          <Check checked={draft.pvp} onChange={(v) => set("pvp", v)} label="PvP" hint="Players can damage each other." />
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
                onBack();
                onChanged();
              } catch (e) {
                setError(String(e));
              }
            }}
          >
            Delete server
          </button>
        </div>
      </GlassCard>
    </div>
  );
}

function CreateServerModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const [name, setName] = useState("");
  const [mcVersion, setMcVersion] = useState("1.21.1");
  const [loader, setLoader] = useState<Loader>("vanilla");
  const [port, setPort] = useState(25565);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      await api.createServer(name.trim(), mcVersion.trim(), loader, port);
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
      <div className="glass-card" style={{ width: 380, padding: 20 }} onClick={(e) => e.stopPropagation()}>
        <h2 style={{ marginTop: 0, fontSize: 15 }}>New server</h2>

        <Field label="Name">
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="My SMP" style={inp} disabled={busy} />
        </Field>
        <Field label="Minecraft version">
          <input value={mcVersion} onChange={(e) => setMcVersion(e.target.value)} style={inp} disabled={busy} />
        </Field>
        <Field label="Loader">
          <select value={loader} onChange={(e) => setLoader(e.target.value as Loader)} style={inp} disabled={busy}>
            <option value="vanilla">Vanilla</option>
            <option value="fabric">Fabric</option>
          </select>
        </Field>
        <Field label="Port">
          <input type="number" value={port} onChange={(e) => setPort(Number(e.target.value))} style={inp} disabled={busy} />
        </Field>

        <div style={{ fontSize: 11, color: "var(--text-tertiary)", margin: "8px 0 12px", lineHeight: 1.55 }}>
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

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 10 }}>
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
    <label style={{ display: "flex", gap: 8, alignItems: "flex-start", cursor: "pointer", maxWidth: 280 }}>
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
