import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { GlassCard } from "../components/GlassCard";
import { Icon } from "../components/Icon";
import { Avatar } from "../components/Avatar";
import { PageHeader } from "../components/PageHeader";
import { useAccountStore } from "../store/accountStore";
import * as api from "../lib/tauri";
import { open } from "@tauri-apps/plugin-dialog";
import { CAPE_AREA, CAPE_SHEET_H, CAPE_SHEET_W, type Cape } from "../types/cape";

/**
 * Cosmetics: a cape maker and a skin changer.
 *
 * The two halves work very differently and the UI says so:
 *
 * - **Skins** go through Mojang. Changing one is a real API call against your
 *   Microsoft account, it affects every client you play on, and it needs an
 *   account that owns the game.
 * - **Capes** are ours. Mojang won't let anyone hand out capes, so these are
 *   peer-to-peer: your cape is announced to the Blurred lobby by hash and sent
 *   to anyone who asks (see commands/capes.rs). That means other Blurred users
 *   see it and nobody else does — which the page states plainly rather than
 *   implying you've got a real Mojang cape.
 */
export function Cosmetics() {
  const [tab, setTab] = useState<"capes" | "skins">("capes");

  return (
    <div style={{ padding: 28, height: "100%", overflowY: "auto" }}>
      <PageHeader page="cosmetics" />

      <div style={{ display: "flex", gap: 8, marginBottom: 20 }}>
        <TabButton active={tab === "capes"} onClick={() => setTab("capes")} icon="anchor" label="Capes" />
        <TabButton active={tab === "skins"} onClick={() => setTab("skins")} icon="diver" label="Skins" />
      </div>

      {tab === "capes" ? <CapesTab /> : <SkinsTab />}
    </div>
  );
}

function TabButton({
  active,
  onClick,
  icon,
  label,
}: {
  active: boolean;
  onClick: () => void;
  icon: "anchor" | "diver";
  label: string;
}) {
  return (
    <button
      onClick={onClick}
      style={{
        display: "flex",
        alignItems: "center",
        gap: 7,
        background: active ? "var(--accent)" : undefined,
        color: active ? "var(--accent-fg)" : undefined,
      }}
    >
      <Icon name={icon} size={15} />
      {label}
    </button>
  );
}

// ---------------------------------------------------------------------------
// Capes
// ---------------------------------------------------------------------------

/** Editor zoom: one cape pixel is this many screen pixels. */
const ZOOM = 14;

const PALETTE = [
  "#0b1e2a", "#123344", "#1b5566", "#2a8fa0", "#35e0d0", "#9df3ea",
  "#ffffff", "#c8d4dc", "#8b9aa5", "#4a5a66", "#22303a", "#000000",
  "#e6b422", "#e2683c", "#c1354e", "#8e3d8e", "#3d5fbf", "#3fa34d",
];

function CapesTab() {
  const [capes, setCapes] = useState<Cape[]>([]);
  const [editing, setEditing] = useState<{ id: string | null; name: string } | null>(null);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setCapes(await api.listCapes());
    } catch (e) {
      setError(String(e));
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  async function wear(id: string | null) {
    setError(null);
    try {
      await api.setActiveCape(id);
      setActiveId(id);
    } catch (e) {
      setError(String(e));
    }
  }

  if (editing) {
    return (
      <CapeEditor
        capeId={editing.id}
        initialName={editing.name}
        onCancel={() => setEditing(null)}
        onSaved={async () => {
          setEditing(null);
          await refresh();
        }}
      />
    );
  }

  return (
    <div style={{ maxWidth: 900 }}>
      <GlassCard style={{ marginBottom: 16 }}>
        <div style={{ fontSize: 12.5, color: "var(--text-secondary)", lineHeight: 1.6 }}>
          Capes are shared <strong>between Blurred Client users</strong>, not through Mojang —
          Mojang doesn't let anyone hand out capes. Yours is announced to the Blurred lobby and
          sent to anyone who asks for it, so other Blurred players see it in game. Players on a
          vanilla client won't.
        </div>
      </GlassCard>

      {error && (
        <GlassCard style={{ marginBottom: 16, borderColor: "var(--danger)" }}>
          <span style={{ color: "var(--danger)", fontSize: 13 }}>{error}</span>
        </GlassCard>
      )}

      <div style={{ display: "flex", gap: 8, marginBottom: 16, flexWrap: "wrap" }}>
        <button className="accent" onClick={() => setEditing({ id: null, name: "" })}>
          New cape
        </button>
        {activeId && <button onClick={() => wear(null)}>Take cape off</button>}
      </div>

      {capes.length === 0 ? (
        <GlassCard style={{ textAlign: "center", padding: 40 }}>
          <Icon name="anchor" size={30} style={{ color: "var(--text-tertiary)", marginBottom: 10 }} />
          <p style={{ color: "var(--text-secondary)", fontSize: 13, margin: 0 }}>
            No capes yet. Draw one and it'll show up on your back in game.
          </p>
        </GlassCard>
      ) : (
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(180px, 1fr))",
            gap: 14,
          }}
        >
          {capes.map((c) => (
            <CapeCard
              key={c.id}
              cape={c}
              active={activeId === c.id}
              onWear={() => wear(c.id)}
              onEdit={() => setEditing({ id: c.id, name: c.name })}
              onDelete={async () => {
                if (!confirm(`Delete cape "${c.name}"?`)) return;
                setCapes(await api.deleteCape(c.id));
                if (activeId === c.id) setActiveId(null);
              }}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function CapeCard({
  cape,
  active,
  onWear,
  onEdit,
  onDelete,
}: {
  cape: Cape;
  active: boolean;
  onWear: () => void;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const [png, setPng] = useState<string | null>(null);

  useEffect(() => {
    api.readCape(cape.id).then(setPng).catch(() => {});
  }, [cape.id]);

  return (
    <GlassCard style={{ padding: 12 }}>
      <div
        style={{
          display: "flex",
          justifyContent: "center",
          padding: 10,
          background: "rgba(0,20,30,0.35)",
          border: "2px solid var(--glass-border)",
          marginBottom: 10,
        }}
      >
        {png ? <CapePreview base64={png} scale={5} /> : <div style={{ height: 80 }} />}
      </div>

      <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 8 }}>
        {cape.name || "Untitled"}
        {active && (
          <span style={{ marginLeft: 6, fontSize: 10, color: "var(--accent)" }}>· WORN</span>
        )}
      </div>

      <div style={{ display: "flex", gap: 5 }}>
        <button
          className={active ? undefined : "accent"}
          onClick={onWear}
          disabled={active}
          style={{ flex: 1, fontSize: 11, padding: "5px 6px" }}
        >
          {active ? "Worn" : "Wear"}
        </button>
        <button onClick={onEdit} style={{ fontSize: 11, padding: "5px 8px" }}>
          Edit
        </button>
        <button onClick={onDelete} style={{ fontSize: 11, padding: "5px 8px", color: "var(--danger)" }}>
          <Icon name="trash" size={12} />
        </button>
      </div>
    </GlassCard>
  );
}

/** Renders just the cape region of a sheet, scaled up with hard pixels. */
function CapePreview({ base64, scale }: { base64: string; scale: number }) {
  const ref = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = ref.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const img = new Image();
    img.onload = () => {
      ctx.imageSmoothingEnabled = false;
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      // Crop the cape block out of the sheet rather than drawing the whole
      // thing — the rest of a cape sheet is elytra texture and padding.
      ctx.drawImage(
        img,
        CAPE_AREA.x, CAPE_AREA.y, CAPE_AREA.w, CAPE_AREA.h,
        0, 0, CAPE_AREA.w * scale, CAPE_AREA.h * scale
      );
    };
    img.src = `data:image/png;base64,${base64}`;
  }, [base64, scale]);

  return (
    <canvas
      ref={ref}
      width={CAPE_AREA.w * scale}
      height={CAPE_AREA.h * scale}
      style={{ imageRendering: "pixelated" }}
    />
  );
}

/**
 * The cape editor.
 *
 * Pixels live in a flat `string[]` of CSS colours (empty = transparent), which
 * is simple to mutate and simple to serialise. The canvas is only a view: every
 * edit updates the array and repaints, so undo is a matter of keeping previous
 * arrays rather than reading pixels back off the canvas.
 */
function CapeEditor({
  capeId,
  initialName,
  onCancel,
  onSaved,
}: {
  capeId: string | null;
  initialName: string;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [name, setName] = useState(initialName);
  const [color, setColor] = useState(PALETTE[4]);
  const [tool, setTool] = useState<"pen" | "eraser" | "fill">("pen");
  const [pixels, setPixels] = useState<string[]>(() =>
    new Array(CAPE_SHEET_W * CAPE_SHEET_H).fill("")
  );
  const [history, setHistory] = useState<string[][]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const painting = useRef(false);

  // Load an existing cape into the grid.
  useEffect(() => {
    if (!capeId) return;
    api.readCape(capeId).then((b64) => {
      const img = new Image();
      img.onload = () => {
        const off = document.createElement("canvas");
        off.width = CAPE_SHEET_W;
        off.height = CAPE_SHEET_H;
        const octx = off.getContext("2d");
        if (!octx) return;
        octx.drawImage(img, 0, 0);
        const data = octx.getImageData(0, 0, CAPE_SHEET_W, CAPE_SHEET_H).data;
        const next = new Array(CAPE_SHEET_W * CAPE_SHEET_H).fill("");
        for (let i = 0; i < next.length; i++) {
          const a = data[i * 4 + 3];
          if (a > 8) {
            next[i] = `#${[0, 1, 2]
              .map((k) => data[i * 4 + k].toString(16).padStart(2, "0"))
              .join("")}`;
          }
        }
        setPixels(next);
      };
      img.src = `data:image/png;base64,${b64}`;
    }).catch(() => {});
  }, [capeId]);

  // Repaint whenever the grid changes.
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // Checkerboard so transparent pixels are visibly transparent.
    for (let y = 0; y < CAPE_SHEET_H; y++) {
      for (let x = 0; x < CAPE_SHEET_W; x++) {
        ctx.fillStyle = (x + y) % 2 === 0 ? "#0d2531" : "#0a1c26";
        ctx.fillRect(x * ZOOM, y * ZOOM, ZOOM, ZOOM);
      }
    }

    for (let i = 0; i < pixels.length; i++) {
      const c = pixels[i];
      if (!c) continue;
      const x = i % CAPE_SHEET_W;
      const y = Math.floor(i / CAPE_SHEET_W);
      ctx.fillStyle = c;
      ctx.fillRect(x * ZOOM, y * ZOOM, ZOOM, ZOOM);
    }

    // Outline the region that actually shows on a cape.
    ctx.strokeStyle = "#35e0d0";
    ctx.lineWidth = 2;
    ctx.strokeRect(
      CAPE_AREA.x * ZOOM, CAPE_AREA.y * ZOOM,
      CAPE_AREA.w * ZOOM, CAPE_AREA.h * ZOOM
    );
  }, [pixels]);

  const pushHistory = useCallback(() => {
    setHistory((h) => [...h.slice(-24), pixels]);
  }, [pixels]);

  function pointToIndex(e: React.MouseEvent<HTMLCanvasElement>): number | null {
    const rect = e.currentTarget.getBoundingClientRect();
    const x = Math.floor((e.clientX - rect.left) / ZOOM);
    const y = Math.floor((e.clientY - rect.top) / ZOOM);
    if (x < 0 || y < 0 || x >= CAPE_SHEET_W || y >= CAPE_SHEET_H) return null;
    return y * CAPE_SHEET_W + x;
  }

  function paintAt(idx: number) {
    setPixels((prev) => {
      if (tool === "fill") return floodFill(prev, idx, color);
      const next = [...prev];
      next[idx] = tool === "eraser" ? "" : color;
      return next;
    });
  }

  function onDown(e: React.MouseEvent<HTMLCanvasElement>) {
    const idx = pointToIndex(e);
    if (idx === null) return;
    pushHistory();
    painting.current = true;
    paintAt(idx);
  }

  function onMove(e: React.MouseEvent<HTMLCanvasElement>) {
    if (!painting.current || tool === "fill") return;
    const idx = pointToIndex(e);
    if (idx !== null) paintAt(idx);
  }

  function undo() {
    setHistory((h) => {
      if (h.length === 0) return h;
      setPixels(h[h.length - 1]);
      return h.slice(0, -1);
    });
  }

  /** Rasterise the grid to a PNG and hand the base64 to the backend. */
  async function save() {
    setBusy(true);
    setError(null);
    try {
      const off = document.createElement("canvas");
      off.width = CAPE_SHEET_W;
      off.height = CAPE_SHEET_H;
      const ctx = off.getContext("2d");
      if (!ctx) throw new Error("could not get a canvas context");

      for (let i = 0; i < pixels.length; i++) {
        const c = pixels[i];
        if (!c) continue;
        ctx.fillStyle = c;
        ctx.fillRect(i % CAPE_SHEET_W, Math.floor(i / CAPE_SHEET_W), 1, 1);
      }

      // Strip the `data:image/png;base64,` prefix — the backend wants raw base64.
      const b64 = off.toDataURL("image/png").split(",")[1];

      // Editing replaces rather than mutating: capes are content-addressed by
      // hash on the wire, so an edited cape is genuinely a different cape.
      if (capeId) await api.deleteCape(capeId);
      await api.saveCape(name.trim() || "Untitled", b64);
      onSaved();
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ maxWidth: 1000 }}>
      <div style={{ display: "flex", gap: 10, alignItems: "center", marginBottom: 14, flexWrap: "wrap" }}>
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Cape name"
          style={{ padding: "8px 10px", fontSize: 13, width: 200 }}
        />
        <button className="accent" onClick={save} disabled={busy}>
          {busy ? "Saving…" : "Save cape"}
        </button>
        <button onClick={onCancel} disabled={busy}>Cancel</button>
        <button onClick={undo} disabled={history.length === 0}>Undo</button>
        <button onClick={() => { pushHistory(); setPixels(new Array(CAPE_SHEET_W * CAPE_SHEET_H).fill("")); }}>
          Clear
        </button>
      </div>

      {error && (
        <div style={{ color: "var(--danger)", fontSize: 12, marginBottom: 12 }}>{error}</div>
      )}

      <div style={{ display: "flex", gap: 16, alignItems: "flex-start", flexWrap: "wrap" }}>
        <GlassCard style={{ padding: 12 }}>
          <canvas
            ref={canvasRef}
            width={CAPE_SHEET_W * ZOOM}
            height={CAPE_SHEET_H * ZOOM}
            onMouseDown={onDown}
            onMouseMove={onMove}
            onMouseUp={() => (painting.current = false)}
            onMouseLeave={() => (painting.current = false)}
            style={{ imageRendering: "pixelated", cursor: "crosshair", display: "block" }}
          />
          <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginTop: 8 }}>
            The outlined block is the part that shows on your back. The rest of the sheet is the
            elytra texture.
          </div>
        </GlassCard>

        <GlassCard style={{ padding: 12, width: 190 }}>
          <div style={{ fontSize: 10, color: "var(--text-tertiary)", marginBottom: 8, textTransform: "uppercase" }}>
            Tool
          </div>
          <div style={{ display: "flex", gap: 5, marginBottom: 14 }}>
            {(["pen", "fill", "eraser"] as const).map((t) => (
              <button
                key={t}
                onClick={() => setTool(t)}
                style={{
                  flex: 1,
                  fontSize: 10,
                  padding: "5px 2px",
                  background: tool === t ? "var(--accent)" : undefined,
                  color: tool === t ? "var(--accent-fg)" : undefined,
                }}
              >
                {t}
              </button>
            ))}
          </div>

          <div style={{ fontSize: 10, color: "var(--text-tertiary)", marginBottom: 8, textTransform: "uppercase" }}>
            Colour
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(6, 1fr)", gap: 4, marginBottom: 10 }}>
            {PALETTE.map((c) => (
              <button
                key={c}
                onClick={() => setColor(c)}
                title={c}
                className="bare"
                style={{
                  width: "100%",
                  aspectRatio: "1",
                  background: c,
                  border: color === c ? "2px solid var(--text-primary)" : "2px solid rgba(0,0,0,0.4)",
                  cursor: "pointer",
                  padding: 0,
                }}
              />
            ))}
          </div>
          <input
            type="color"
            value={color}
            onChange={(e) => setColor(e.target.value)}
            style={{ width: "100%", height: 30, background: "transparent", border: "none", cursor: "pointer" }}
          />
        </GlassCard>
      </div>
    </div>
  );
}

/**
 * Flood fill, iterative rather than recursive — a 64x32 sheet is 2048 cells and
 * a recursive fill on a fully-empty sheet would blow the JS stack.
 */
function floodFill(pixels: string[], start: number, color: string): string[] {
  const target = pixels[start];
  if (target === color) return pixels;

  const next = [...pixels];
  const stack = [start];
  while (stack.length) {
    const i = stack.pop()!;
    if (next[i] !== target) continue;
    next[i] = color;

    const x = i % CAPE_SHEET_W;
    const y = Math.floor(i / CAPE_SHEET_W);
    if (x > 0) stack.push(i - 1);
    if (x < CAPE_SHEET_W - 1) stack.push(i + 1);
    if (y > 0) stack.push(i - CAPE_SHEET_W);
    if (y < CAPE_SHEET_H - 1) stack.push(i + CAPE_SHEET_W);
  }
  return next;
}

// ---------------------------------------------------------------------------
// Skins
// ---------------------------------------------------------------------------

function SkinsTab() {
  const accounts = useAccountStore((s) => s.accounts);
  const refreshAccounts = useAccountStore((s) => s.refresh);
  const [variant, setVariant] = useState("classic");
  const [url, setUrl] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const msAccounts = useMemo(
    () => accounts.filter((a) => a.accountType === "microsoft"),
    [accounts]
  );
  const [accountId, setAccountId] = useState("");
  useEffect(() => {
    if (!accountId && msAccounts[0]) setAccountId(msAccounts[0].id);
  }, [accountId, msAccounts]);

  async function run(fn: () => Promise<unknown>, ok: string) {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      await fn();
      setNotice(ok);
      setUrl("");
      await refreshAccounts();
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }

  async function pickFile() {
    const picked = await open({
      multiple: false,
      filters: [{ name: "Skin PNG", extensions: ["png"] }],
    });
    if (typeof picked === "string") {
      await run(() => api.setAccountSkinFile(accountId, picked, variant), "Skin applied.");
    }
  }

  if (msAccounts.length === 0) {
    return (
      <GlassCard style={{ maxWidth: 620, padding: 36, textAlign: "center" }}>
        <Icon name="diver" size={28} style={{ color: "var(--text-tertiary)", marginBottom: 10 }} />
        <p style={{ color: "var(--text-secondary)", fontSize: 13, margin: 0 }}>
          Changing a skin goes through Mojang, so it needs a Microsoft account. Add one from the
          Accounts screen first.
        </p>
      </GlassCard>
    );
  }

  const current = msAccounts.find((a) => a.id === accountId);

  return (
    <div style={{ maxWidth: 720, display: "flex", flexDirection: "column", gap: 16 }}>
      <GlassCard>
        <div style={{ fontSize: 12.5, color: "var(--text-secondary)", lineHeight: 1.6 }}>
          Unlike capes, this is a <strong>real Mojang skin change</strong>. It applies to your
          account everywhere, not just in Blurred Client.
        </div>
      </GlassCard>

      <GlassCard>
        <div style={{ display: "flex", gap: 16, alignItems: "flex-start", flexWrap: "wrap" }}>
          {/* Same crop as everywhere else — this used to hand-roll the maths
              and got the scale wrong (1024% where 800% was correct). */}
          {current && <Avatar account={current} size={128} />}

          <div style={{ flex: 1, minWidth: 260, display: "flex", flexDirection: "column", gap: 12 }}>
            <div>
              <label style={{ fontSize: 12, color: "var(--text-secondary)", display: "block", marginBottom: 5 }}>
                Account
              </label>
              <select
                value={accountId}
                onChange={(e) => setAccountId(e.target.value)}
                style={{ width: "100%", padding: "8px 10px" }}
              >
                {msAccounts.map((a) => (
                  <option key={a.id} value={a.id}>{a.username}</option>
                ))}
              </select>
            </div>

            <div>
              <label style={{ fontSize: 12, color: "var(--text-secondary)", display: "block", marginBottom: 5 }}>
                Model
              </label>
              <select
                value={variant}
                onChange={(e) => setVariant(e.target.value)}
                style={{ width: "100%", padding: "8px 10px" }}
              >
                <option value="classic">Classic (4px arms)</option>
                <option value="slim">Slim (3px arms)</option>
              </select>
            </div>

            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              <button className="accent" onClick={pickFile} disabled={busy}>
                {busy ? "Working…" : "Upload a PNG"}
              </button>
              <button
                onClick={() => run(() => api.resetAccountSkin(accountId), "Skin reset to default.")}
                disabled={busy}
              >
                Reset to default
              </button>
            </div>

            <div>
              <label style={{ fontSize: 12, color: "var(--text-secondary)", display: "block", marginBottom: 5 }}>
                …or apply from a public URL
              </label>
              <div style={{ display: "flex", gap: 8 }}>
                <input
                  value={url}
                  onChange={(e) => setUrl(e.target.value)}
                  placeholder="https://…/skin.png"
                  spellCheck={false}
                  style={{ flex: 1, padding: "8px 10px", fontSize: 12 }}
                />
                <button
                  className="accent"
                  disabled={busy || !url.trim()}
                  onClick={() => run(() => api.setAccountSkin(accountId, url.trim(), variant), "Skin applied.")}
                >
                  Apply
                </button>
              </div>
            </div>
          </div>
        </div>

        {notice && <div style={{ color: "var(--success)", fontSize: 12, marginTop: 12 }}>{notice}</div>}
        {error && <div style={{ color: "var(--danger)", fontSize: 12, marginTop: 12 }}>{error}</div>}
      </GlassCard>
    </div>
  );
}
