import { useEffect, useMemo, useState, type CSSProperties } from "react";
import { useInstanceStore } from "../store/instanceStore";
import { useAccountStore } from "../store/accountStore";
import { useChatStore } from "../store/chatStore";
import { GlassCard } from "../components/GlassCard";
import { Icon } from "../components/Icon";
import { Avatar } from "../components/Avatar";
import { CrashBanner } from "../components/CrashBanner";
import { blurredEssentials, installMods } from "../lib/tauri";
import { formatDuration, formatRelativeDate } from "../lib/format";
import { navLabel } from "../lib/nav";
import type { Instance, Loader } from "../types/instance";

/**
 * The launchpad.
 *
 * Shaped like Lunar's home screen because that layout is genuinely good: one
 * unmissable Play button for the thing you'll do 95% of the time, the instance
 * you last played already selected, and everything else arranged around it.
 * The differences are deliberate — no ad slot, no store rail, no promo strip.
 * The space Lunar gives to advertising goes to your crew and your own stats.
 */
export function Home({ onOpenInstance }: { onOpenInstance: (id: string) => void }) {
  const {
    instances,
    loading,
    error,
    running,
    refresh,
    refreshRunning,
    launch,
    quit,
    rename,
    duplicate,
    remove,
    openFolder,
  } = useInstanceStore();

  const accounts = useAccountStore((s) => s.accounts);
  const { connected, refreshFriends, wire, crew, incoming } = useChatStore();

  const [showCreate, setShowCreate] = useState(false);
  const [renamingId, setRenamingId] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState("");
  // Which instance the hero's Play button will launch. Null means "follow the
  // most recently played one", so the hero self-updates after each session
  // instead of pinning whatever was selected on first load.
  const [heroId, setHeroId] = useState<string | null>(null);

  useEffect(() => {
    refresh();
    refreshRunning();
    wire();
    refreshFriends().catch(() => {});
  }, [refresh, refreshRunning, wire, refreshFriends]);

  const activeAccount = useMemo(
    () => accounts.slice().sort((a, b) => (b.lastUsed ?? "").localeCompare(a.lastUsed ?? ""))[0],
    [accounts]
  );

  // Most recently played, falling back to the first instance for a fresh install.
  const mostRecent = useMemo(() => {
    const sorted = instances
      .slice()
      .sort((a, b) => (b.lastPlayed ?? "").localeCompare(a.lastPlayed ?? ""));
    return sorted[0];
  }, [instances]);

  const hero = useMemo(
    () => instances.find((i) => i.id === heroId) ?? mostRecent,
    [instances, heroId, mostRecent]
  );

  const totalPlaytime = useMemo(
    () => instances.reduce((sum, i) => sum + (i.totalPlaytimeSeconds ?? 0), 0),
    [instances]
  );
  const friends = crew();
  const requests = incoming().length;
  const onlineFriends = friends.filter((f) => f.online).length;

  async function commitRename(id: string) {
    const v = renameValue.trim();
    if (v) await rename(id, v).catch(() => {});
    setRenamingId(null);
  }

  return (
    <div style={{ display: "flex", height: "100%", overflow: "hidden" }}>
      {/* ---- Main column ---- */}
      <div style={{ flex: 1, overflowY: "auto", padding: "24px 28px", minWidth: 0 }}>
        {/* Greeting */}
        <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 18 }}>
          {activeAccount && <Avatar account={activeAccount} size={38} />}
          <div style={{ flex: 1, minWidth: 0 }}>
            {/* The tab is called Launchpad, so the screen says Launchpad —
                the greeting is a subtitle under it, not the title. */}
            <div style={{ fontSize: 10, letterSpacing: "0.1em", color: "var(--text-tertiary)" }}>
              {navLabel("home").toUpperCase()}
            </div>
            <div style={{ fontSize: 17, fontFamily: "var(--font-pixel)" }}>
              Welcome back{activeAccount ? `, ${activeAccount.username}` : ""}
            </div>
            <div style={{ fontSize: 12, color: "var(--text-tertiary)" }}>
              {instances.length} instance{instances.length === 1 ? "" : "s"} ·{" "}
              {formatDuration(totalPlaytime)} logged
            </div>
          </div>
          <button onClick={() => setShowCreate(true)} style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <Icon name="plus" size={15} />
            New Instance
          </button>
        </div>

        <CrashBanner onOpenInstance={onOpenInstance} />

        {error && (
          <GlassCard style={{ marginBottom: 16, borderColor: "var(--danger)" }}>
            <span style={{ color: "var(--danger)" }}>{error}</span>
          </GlassCard>
        )}

        {/* Hero launch panel */}
        {hero && (
          <HeroPanel
            instance={hero}
            instances={instances}
            isRunning={!!running[hero.id]}
            onSelect={setHeroId}
            onLaunch={() => launch(hero.id).catch(() => {})}
            onQuit={() => quit(hero.id).catch(() => {})}
            onConfigure={() => onOpenInstance(hero.id)}
          />
        )}

        {loading && instances.length === 0 && (
          <p style={{ color: "var(--text-secondary)" }}>Loading instances…</p>
        )}

        {!loading && instances.length === 0 && !showCreate && (
          <GlassCard style={{ textAlign: "center", padding: 48 }}>
            <Icon name="anchor" size={34} style={{ color: "var(--text-tertiary)", marginBottom: 10 }} />
            <p style={{ color: "var(--text-secondary)", marginBottom: 16 }}>
              Nothing in the fleet yet. Create an instance to get started.
            </p>
            <button className="accent" onClick={() => setShowCreate(true)}>
              Create your first instance
            </button>
          </GlassCard>
        )}

        {instances.length > 0 && (
          <>
            <h2 style={sectionTitle}>Your fleet</h2>
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fill, minmax(210px, 1fr))",
                gap: 14,
              }}
            >
              {instances.map((inst) => {
                const isRunning = !!running[inst.id];
                const isRenaming = renamingId === inst.id;
                return (
                  <GlassCard
                    key={inst.id}
                    onClick={() => !isRenaming && onOpenInstance(inst.id)}
                    style={{ padding: 16 }}
                  >
                    {isRenaming ? (
                      <input
                        autoFocus
                        value={renameValue}
                        onClick={(e) => e.stopPropagation()}
                        onChange={(e) => setRenameValue(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === "Enter") commitRename(inst.id);
                          if (e.key === "Escape") setRenamingId(null);
                        }}
                        onBlur={() => commitRename(inst.id)}
                        style={{
                          width: "100%",
                          marginBottom: 4,
                          padding: "4px 8px",
                          borderRadius: "var(--radius-sm)",
                          border: "1px solid var(--accent)",
                          background: "rgba(0,20,30,0.3)",
                          color: "var(--text-primary)",
                          fontSize: 14,
                          fontWeight: 600,
                        }}
                      />
                    ) : (
                      <div
                        style={{
                          fontWeight: 600,
                          marginBottom: 4,
                          display: "flex",
                          alignItems: "center",
                          gap: 7,
                        }}
                      >
                        {isRunning && <span className="ping" title="Running" />}
                        <span
                          style={{
                            overflow: "hidden",
                            textOverflow: "ellipsis",
                            whiteSpace: "nowrap",
                          }}
                        >
                          {inst.name}
                        </span>
                      </div>
                    )}

                    <div style={{ display: "flex", gap: 6, marginBottom: 10, flexWrap: "wrap" }}>
                      <Tag>{inst.mcVersion}</Tag>
                      <Tag>{inst.loader}</Tag>
                    </div>

                    <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginBottom: 12 }}>
                      {inst.lastPlayed
                        ? `Last dive ${formatRelativeDate(inst.lastPlayed)}`
                        : "Never launched"}
                      {inst.totalPlaytimeSeconds > 0 &&
                        ` · ${formatDuration(inst.totalPlaytimeSeconds)}`}
                    </div>

                    {isRunning ? (
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          quit(inst.id).catch(() => {});
                        }}
                        style={{
                          width: "100%",
                          background: "var(--danger)",
                          color: "#fff",
                          border: "none",
                          borderRadius: "var(--radius-sm)",
                          padding: "8px 16px",
                          fontWeight: 600,
                          cursor: "pointer",
                          display: "flex",
                          alignItems: "center",
                          justifyContent: "center",
                          gap: 6,
                        }}
                      >
                        <Icon name="stop" size={14} />
                        Quit
                      </button>
                    ) : (
                      <button
                        className="accent"
                        style={{
                          width: "100%",
                          display: "flex",
                          alignItems: "center",
                          justifyContent: "center",
                          gap: 6,
                        }}
                        onClick={(e) => {
                          e.stopPropagation();
                          launch(inst.id).catch(() => {});
                        }}
                      >
                        <Icon name="play" size={14} />
                        Play
                      </button>
                    )}

                    <div style={{ display: "flex", gap: 4, marginTop: 8 }}>
                      <CardAction
                        icon="pencil"
                        label="Rename"
                        onClick={() => {
                          setRenameValue(inst.name);
                          setRenamingId(inst.id);
                        }}
                      />
                      <CardAction
                        icon="copy"
                        label="Duplicate"
                        onClick={() => duplicate(inst.id).catch(() => {})}
                      />
                      <CardAction
                        icon="folder"
                        label="Folder"
                        onClick={() => openFolder(inst.id).catch(() => {})}
                      />
                      <CardAction
                        icon="trash"
                        label="Delete"
                        danger
                        onClick={() => {
                          if (
                            confirm(
                              `Delete instance "${inst.name}"? This removes all its files (mods, saves, configs).`
                            )
                          ) {
                            remove(inst.id).catch(() => {});
                          }
                        }}
                      />
                    </div>
                  </GlassCard>
                );
              })}
            </div>
          </>
        )}
      </div>

      {/* ---- Right dock: crew + stats. Where Lunar puts its ad. ---- */}
      <aside
        style={{
          width: 250,
          flexShrink: 0,
          borderLeft: "1px solid var(--glass-border)",
          padding: "24px 16px",
          overflowY: "auto",
        }}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            marginBottom: 10,
          }}
        >
          <h2 style={{ ...sectionTitle, margin: 0 }}>
            Crew{friends.length > 0 ? ` (${onlineFriends} up)` : ""}
          </h2>
          <span
            className={`presence ${connected ? "online" : ""}`}
            title={connected ? "Connected to chat" : "Not connected to chat"}
          />
        </div>

        {requests > 0 && (
          <div
            style={{
              padding: "8px 10px",
              marginBottom: 10,
              borderRadius: "var(--radius-sm)",
              border: "1px solid var(--accent)",
              background: "rgba(53,224,208,0.08)",
              fontSize: 12,
              lineHeight: 1.45,
            }}
          >
            {requests} friend request{requests === 1 ? "" : "s"} waiting in{" "}
            <strong>Sonar</strong>.
          </div>
        )}

        {friends.length === 0 ? (
          <p style={{ fontSize: 12, color: "var(--text-tertiary)", lineHeight: 1.55 }}>
            No crew yet. Open <strong>Sonar</strong> to send a friend request and
            see when people are online.
          </p>
        ) : (
          <div style={{ marginBottom: 20 }}>
            {friends.map((f) => (
              <div
                key={f.nick}
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 9,
                  padding: "7px 8px",
                  fontSize: 13,
                  color: f.online ? "var(--text-primary)" : "var(--text-tertiary)",
                }}
              >
                <span className={`presence ${f.online ? "online" : ""}`} />
                <span style={{ flex: 1, overflow: "hidden", textOverflow: "ellipsis" }}>
                  {f.nick}
                </span>
              </div>
            ))}
          </div>
        )}

        <h2 style={sectionTitle}>Depth gauge</h2>
        <Stat label="Total playtime" value={formatDuration(totalPlaytime)} />
        <Stat label="Instances" value={String(instances.length)} />
        <Stat
          label="Running now"
          value={String(Object.values(running).filter(Boolean).length)}
        />
        <Stat
          label="Last dive"
          value={mostRecent ? formatRelativeDate(mostRecent.lastPlayed) : "—"}
        />
      </aside>

      {showCreate && (
        <CreateInstanceModal onClose={() => setShowCreate(false)} onDone={() => setShowCreate(false)} />
      )}
    </div>
  );
}

/**
 * The big launch panel. One instance is "loaded" at a time; the dropdown
 * switches which, and the Play button is the largest thing on the screen by a
 * wide margin — that's the whole point of the layout.
 */
function HeroPanel({
  instance,
  instances,
  isRunning,
  onSelect,
  onLaunch,
  onQuit,
  onConfigure,
}: {
  instance: Instance;
  instances: Instance[];
  isRunning: boolean;
  onSelect: (id: string) => void;
  onLaunch: () => void;
  onQuit: () => void;
  onConfigure: () => void;
}) {
  return (
    <div
      className="glass-card"
      style={{
        position: "relative",
        padding: 0,
        marginBottom: 26,
        overflow: "hidden",
        background: "var(--hero-gradient)",
      }}
    >
      {/* Depth wash behind the button, so the panel reads as a window into
          deeper water rather than a flat card. */}
      <div
        aria-hidden
        style={{
          position: "absolute",
          inset: 0,
          background:
            "radial-gradient(ellipse at 50% 120%, var(--accent-glow), transparent 62%)",
          pointerEvents: "none",
        }}
      />

      <div
        style={{
          position: "relative",
          padding: "34px 28px 26px",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          gap: 16,
          minHeight: 210,
          justifyContent: "center",
        }}
      >
        <div style={{ textAlign: "center" }}>
          <div
            style={{
              fontSize: 11,
              letterSpacing: "0.14em",
              textTransform: "uppercase",
              color: "var(--text-tertiary)",
              marginBottom: 4,
            }}
          >
            {isRunning ? "Currently diving" : "Ready to dive"}
          </div>
          <div style={{ fontSize: 24, fontWeight: 700 }}>{instance.name}</div>
        </div>

        {isRunning ? (
          <button
            onClick={onQuit}
            style={{
              background: "var(--danger)",
              color: "#fff",
              border: "none",
              borderRadius: "var(--radius-md)",
              padding: "16px 52px",
              fontSize: 17,
              fontWeight: 700,
              letterSpacing: "0.02em",
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              gap: 10,
            }}
          >
            <Icon name="stop" size={19} />
            STOP GAME
          </button>
        ) : (
          <button
            className="accent"
            onClick={onLaunch}
            style={{
              borderRadius: "var(--radius-md)",
              padding: "16px 52px",
              fontSize: 17,
              fontWeight: 700,
              letterSpacing: "0.02em",
              display: "flex",
              alignItems: "center",
              gap: 10,
              boxShadow: "0 6px 28px var(--accent-glow)",
            }}
          >
            <Icon name="play" size={19} />
            LAUNCH GAME
          </button>
        )}

        {/* Instance switcher + settings shortcut. */}
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <div style={{ position: "relative", display: "flex", alignItems: "center" }}>
            <select
              value={instance.id}
              onChange={(e) => onSelect(e.target.value)}
              aria-label="Instance to launch"
              style={{
                appearance: "none",
                WebkitAppearance: "none",
                background: "rgba(0,20,30,0.35)",
                border: "1px solid var(--glass-border)",
                borderRadius: 0,
                color: "var(--text-secondary)",
                fontSize: 12.5,
                padding: "7px 30px 7px 14px",
                cursor: "pointer",
              }}
            >
              {instances.map((i) => (
                <option key={i.id} value={i.id}>
                  {i.name} — {i.mcVersion} {i.loader}
                </option>
              ))}
            </select>
            <Icon
              name="chevronDown"
              size={14}
              style={{
                position: "absolute",
                right: 10,
                pointerEvents: "none",
                color: "var(--text-tertiary)",
              }}
            />
          </div>

          <button
            onClick={onConfigure}
            title="Instance settings"
            aria-label="Instance settings"
            style={{
              borderRadius: 0,
              width: 34,
              height: 34,
              padding: 0,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <Icon name="gauge" size={16} />
          </button>
        </div>
      </div>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div
      style={{
        display: "flex",
        justifyContent: "space-between",
        alignItems: "baseline",
        padding: "7px 0",
        borderBottom: "1px solid var(--glass-border)",
        fontSize: 12.5,
      }}
    >
      <span style={{ color: "var(--text-tertiary)" }}>{label}</span>
      <span style={{ fontWeight: 600 }}>{value}</span>
    </div>
  );
}

function Tag({ children }: { children: React.ReactNode }) {
  return (
    <span
      style={{
        fontSize: 10.5,
        padding: "2px 7px",
        borderRadius: 0,
        background: "rgba(125,226,240,0.1)",
        border: "1px solid var(--glass-border)",
        color: "var(--text-secondary)",
      }}
    >
      {children}
    </span>
  );
}

const sectionTitle: CSSProperties = {
  fontSize: 11,
  fontWeight: 700,
  letterSpacing: "0.09em",
  textTransform: "uppercase",
  color: "var(--text-tertiary)",
  margin: "0 0 12px",
};

// Small secondary action on an instance card. Stops click propagation so it
// doesn't also open the instance detail view.
function CardAction({
  icon,
  label,
  onClick,
  danger,
}: {
  icon: "pencil" | "copy" | "folder" | "trash";
  label: string;
  onClick: () => void;
  danger?: boolean;
}) {
  return (
    <button
      className="bare"
      onClick={(e) => {
        e.stopPropagation();
        onClick();
      }}
      title={label}
      aria-label={label}
      style={{
        flex: 1,
        background: "transparent",
        border: "1px solid var(--glass-border)",
        borderRadius: "var(--radius-sm)",
        color: danger ? "var(--danger)" : "var(--text-tertiary)",
        padding: "6px 0",
        cursor: "pointer",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <Icon name={icon} size={14} />
    </button>
  );
}

function CreateInstanceModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const { create } = useInstanceStore();
  const [name, setName] = useState("");
  const [mcVersion, setMcVersion] = useState("1.21.1");
  const [loader, setLoader] = useState<Loader>("fabric");
  const [installDefaults, setInstallDefaults] = useState(true);
  const [phase, setPhase] = useState<"idle" | "creating" | "installing">("idle");
  const [error, setError] = useState<string | null>(null);

  // Blurred Essentials is a Fabric pack, so choosing it pins the loader to Fabric.
  const effectiveLoader: Loader = installDefaults ? "fabric" : loader;
  const busy = phase !== "idle";

  // NOTE: mc_version is a free-text field for now, not a dropdown sourced from
  // the real Mojang manifest. Type it exactly right (e.g. "1.21.1").

  async function submit() {
    setError(null);
    try {
      setPhase("creating");
      const inst = await create(name.trim(), mcVersion.trim(), effectiveLoader);
      if (installDefaults) {
        setPhase("installing");
        const slugs = await blurredEssentials();
        await installMods(inst.id, slugs, true);
      }
      onDone();
    } catch (e) {
      setError(String(e));
      setPhase("idle");
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
        <h2 style={{ marginTop: 0, fontSize: 16 }}>New Instance</h2>

        <label style={{ fontSize: 12, color: "var(--text-secondary)" }}>Name</label>
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="My Instance"
          style={inputStyle}
          disabled={busy}
        />

        <label style={{ fontSize: 12, color: "var(--text-secondary)" }}>MC Version</label>
        <input
          value={mcVersion}
          onChange={(e) => setMcVersion(e.target.value)}
          style={inputStyle}
          disabled={busy}
        />

        {/* Default modpack option */}
        <label
          style={{
            display: "flex",
            gap: 10,
            alignItems: "flex-start",
            padding: 10,
            borderRadius: "var(--radius-sm)",
            border: `1px solid ${installDefaults ? "var(--accent)" : "var(--glass-border)"}`,
            background: installDefaults ? "var(--glass-bg-elevated)" : "transparent",
            cursor: busy ? "default" : "pointer",
            marginBottom: 12,
          }}
        >
          <input
            type="checkbox"
            checked={installDefaults}
            disabled={busy}
            onChange={(e) => setInstallDefaults(e.target.checked)}
            style={{ marginTop: 2 }}
          />
          <span>
            <div style={{ fontSize: 13, fontWeight: 600 }}>Install Blurred Essentials</div>
            <div style={{ fontSize: 11, color: "var(--text-tertiary)" }}>
              Fabric performance + QoL pack (Sodium, Lithium, JEI, Jade…). Uncheck to start blank.
            </div>
          </span>
        </label>

        <label style={{ fontSize: 12, color: "var(--text-secondary)" }}>Loader</label>
        <select
          value={effectiveLoader}
          onChange={(e) => setLoader(e.target.value as Loader)}
          style={inputStyle}
          disabled={busy || installDefaults}
        >
          <option value="vanilla">Vanilla</option>
          <option value="fabric">Fabric</option>
          <option value="quilt">Quilt</option>
          <option value="forge">Forge (experimental — launch WIP)</option>
          <option value="neoforge">NeoForge (experimental — launch WIP)</option>
        </select>
        {installDefaults && (
          <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginTop: -4, marginBottom: 8 }}>
            Locked to Fabric for the Essentials pack.
          </div>
        )}

        {error && <div style={{ color: "var(--danger)", fontSize: 12, marginBottom: 8 }}>{error}</div>}

        <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
          <button onClick={onClose} style={{ flex: 1 }} disabled={busy}>
            Cancel
          </button>
          <button className="accent" style={{ flex: 1 }} disabled={!name.trim() || busy} onClick={submit}>
            {phase === "creating" ? "Creating…" : phase === "installing" ? "Installing mods…" : "Create"}
          </button>
        </div>
        {phase === "installing" && (
          <div
            style={{
              fontSize: 11,
              color: "var(--text-tertiary)",
              marginTop: 8,
              textAlign: "center",
            }}
          >
            Downloading the Essentials pack — this can take a minute.
          </div>
        )}
      </div>
    </div>
  );
}

const inputStyle: CSSProperties = {
  width: "100%",
  marginBottom: 12,
  marginTop: 4,
  padding: "8px 10px",
  borderRadius: 0,
  border: "1px solid var(--glass-border)",
  background: "rgba(0,20,30,0.3)",
  color: "var(--text-primary)",
  fontSize: 13,
};
