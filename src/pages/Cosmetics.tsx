import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { GlassCard } from "../components/GlassCard";
import { Icon } from "../components/Icon";
import { Avatar } from "../components/Avatar";
import { PageHeader } from "../components/PageHeader";
import { useAccountStore } from "../store/accountStore";
import * as api from "../lib/tauri";
import { open } from "@tauri-apps/plugin-dialog";
import { CAPE_AREA, CAPE_SHEET_H, CAPE_SHEET_W, type Cape } from "../types/cape";
import * as raster from "../lib/capeRaster";

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
 * # It only shows the cape
 *
 * A cape sheet is 64x32, of which the cape is the 10x16 block at (1,1) — the
 * rest is the elytra texture and dead padding. The editor used to show the
 * whole sheet with that block outlined, which meant 95% of the canvas was
 * space where painting did nothing visible. Now the canvas *is* the cape,
 * blown up large enough to work on, and the sheet is assembled at save time.
 *
 * Editing an existing cape keeps whatever was in the rest of its sheet
 * untouched, so cropping the view never silently discards an elytra someone
 * imported.
 *
 * # Two painting modes
 *
 * Pixel art mode is hard whole-pixel painting, which is what a cape at this
 * resolution usually wants. Smooth mode is a soft, partially-transparent brush
 * that builds up — worth having for shading, gradients and glows, which are
 * miserable to place by hand one pixel at a time. See {@link raster.stamp}.
 */

/** Editor zoom for the cropped view: one cape pixel is this many screen px. */
const CAPE_ZOOM = 30;

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
  const [tool, setTool] = useState<"pen" | "eraser" | "fill" | "picker">("pen");
  const [mode, setMode] = useState<"pixel" | "smooth">("pixel");
  const [brushSize, setBrushSize] = useState(1);
  const [opacity, setOpacity] = useState(0.6);
  const [tolerance, setTolerance] = useState(0.12);
  const [contiguous, setContiguous] = useState(true);
  const [showGrid, setShowGrid] = useState(true);

  const [data, setData] = useState<Uint8ClampedArray>(() => raster.emptyCape());
  const [history, setHistory] = useState<Uint8ClampedArray[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canvasRef = useRef<HTMLCanvasElement>(null);
  const painting = useRef(false);
  const lastPoint = useRef<{ x: number; y: number } | null>(null);
  /**
   * The rest of the sheet for the cape being edited — everything outside the
   * cape block. Held so saving can put the cape back into its original sheet
   * rather than onto a blank one.
   */
  const sheetRef = useRef<ImageData | null>(null);

  // Load an existing cape: the cape block into the editable buffer, the whole
  // sheet into sheetRef so the elytra survives a round trip.
  useEffect(() => {
    if (!capeId) return;
    let cancelled = false;

    api
      .readCape(capeId)
      .then((b64) => {
        const img = new Image();
        img.onload = () => {
          if (cancelled) return;
          const off = document.createElement("canvas");
          off.width = CAPE_SHEET_W;
          off.height = CAPE_SHEET_H;
          const octx = off.getContext("2d");
          if (!octx) return;
          octx.drawImage(img, 0, 0);
          sheetRef.current = octx.getImageData(0, 0, CAPE_SHEET_W, CAPE_SHEET_H);
          setData(
            new Uint8ClampedArray(
              octx.getImageData(CAPE_AREA.x, CAPE_AREA.y, CAPE_AREA.w, CAPE_AREA.h).data
            )
          );
        };
        img.src = `data:image/png;base64,${b64}`;
      })
      .catch(() => {});

    return () => {
      cancelled = true;
    };
  }, [capeId]);

  // Repaint whenever the buffer changes.
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    // Checkerboard, at cape-pixel scale, so transparency is legible.
    for (let y = 0; y < raster.CAPE_H; y++) {
      for (let x = 0; x < raster.CAPE_W; x++) {
        ctx.fillStyle = (x + y) % 2 === 0 ? "#0d2531" : "#0a1c26";
        ctx.fillRect(x * CAPE_ZOOM, y * CAPE_ZOOM, CAPE_ZOOM, CAPE_ZOOM);
      }
    }

    // Blit the buffer through a 1:1 offscreen canvas and scale it up with
    // smoothing off. Drawing 160 individual rects would also work, but this
    // keeps partial alpha exact instead of round-tripping it through
    // `fillStyle` strings.
    const off = document.createElement("canvas");
    off.width = raster.CAPE_W;
    off.height = raster.CAPE_H;
    const octx = off.getContext("2d");
    if (!octx) return;
    octx.putImageData(new ImageData(new Uint8ClampedArray(data), raster.CAPE_W, raster.CAPE_H), 0, 0);

    ctx.imageSmoothingEnabled = false;
    ctx.drawImage(off, 0, 0, canvas.width, canvas.height);

    if (showGrid) {
      // Hairline grid. Off in smooth mode by default, where cell boundaries
      // are not what you're looking at.
      ctx.strokeStyle = "rgba(125,226,240,0.14)";
      ctx.lineWidth = 1;
      ctx.beginPath();
      for (let x = 1; x < raster.CAPE_W; x++) {
        ctx.moveTo(x * CAPE_ZOOM + 0.5, 0);
        ctx.lineTo(x * CAPE_ZOOM + 0.5, canvas.height);
      }
      for (let y = 1; y < raster.CAPE_H; y++) {
        ctx.moveTo(0, y * CAPE_ZOOM + 0.5);
        ctx.lineTo(canvas.width, y * CAPE_ZOOM + 0.5);
      }
      ctx.stroke();
    }

    // Frame the canvas so the cape's own edge is unambiguous against the card.
    ctx.strokeStyle = "#35e0d0";
    ctx.lineWidth = 2;
    ctx.strokeRect(1, 1, canvas.width - 2, canvas.height - 2);
  }, [data, showGrid]);

  const brush = useMemo<raster.Brush>(
    () => ({
      soft: mode === "smooth",
      size: brushSize,
      opacity,
      color: raster.hexToRgb(color),
      erase: tool === "eraser",
    }),
    [mode, brushSize, opacity, color, tool]
  );

  /** Cape-pixel coordinates, fractional — the soft brush needs sub-pixel aim. */
  function pointAt(e: React.MouseEvent<HTMLCanvasElement>) {
    const rect = e.currentTarget.getBoundingClientRect();
    return {
      x: ((e.clientX - rect.left) / rect.width) * raster.CAPE_W,
      y: ((e.clientY - rect.top) / rect.height) * raster.CAPE_H,
    };
  }

  function onDown(e: React.MouseEvent<HTMLCanvasElement>) {
    const p = pointAt(e);

    if (tool === "picker") {
      const hex = raster.pick(data, Math.floor(p.x), Math.floor(p.y));
      if (hex) setColor(hex);
      // Snap back to the pen: an eyedropper you have to switch away from
      // manually is one extra click on every single use.
      setTool("pen");
      return;
    }

    setHistory((h) => [...h.slice(-31), new Uint8ClampedArray(data)]);

    if (tool === "fill") {
      setData(
        raster.bucketFill(data, Math.floor(p.x), Math.floor(p.y), raster.hexToRgb(color), {
          tolerance,
          contiguous,
          erase: false,
        })
      );
      return;
    }

    painting.current = true;
    lastPoint.current = p;
    const next = new Uint8ClampedArray(data);
    raster.stamp(next, p.x, p.y, brush);
    setData(next);
  }

  function onMove(e: React.MouseEvent<HTMLCanvasElement>) {
    if (!painting.current) return;
    const p = pointAt(e);
    const from = lastPoint.current ?? p;
    lastPoint.current = p;

    setData((prev) => {
      const next = new Uint8ClampedArray(prev);
      raster.stroke(next, from.x, from.y, p.x, p.y, brush);
      return next;
    });
  }

  function endStroke() {
    painting.current = false;
    lastPoint.current = null;
  }

  function undo() {
    setHistory((h) => {
      if (h.length === 0) return h;
      setData(h[h.length - 1]);
      return h.slice(0, -1);
    });
  }

  function clear() {
    setHistory((h) => [...h.slice(-31), new Uint8ClampedArray(data)]);
    setData(raster.emptyCape());
  }

  /** Compose the cape back into a full sheet, encode it, hand it to the backend. */
  async function save() {
    setBusy(true);
    setError(null);
    try {
      const off = document.createElement("canvas");
      off.width = CAPE_SHEET_W;
      off.height = CAPE_SHEET_H;
      const ctx = off.getContext("2d");
      if (!ctx) throw new Error("could not get a canvas context");

      // Start from the original sheet when editing, so the elytra half and
      // anything else outside the cape block is preserved.
      if (sheetRef.current) ctx.putImageData(sheetRef.current, 0, 0);

      // putImageData ignores compositing and writes the block verbatim, which
      // is what's wanted: the editor buffer is the whole truth for this region,
      // including its transparent pixels.
      ctx.putImageData(
        new ImageData(new Uint8ClampedArray(data), raster.CAPE_W, raster.CAPE_H),
        CAPE_AREA.x,
        CAPE_AREA.y
      );

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
        <button onClick={clear}>Clear</button>
      </div>

      {error && (
        <div style={{ color: "var(--danger)", fontSize: 12, marginBottom: 12 }}>{error}</div>
      )}

      <div style={{ display: "flex", gap: 16, alignItems: "flex-start", flexWrap: "wrap" }}>
        <GlassCard style={{ padding: 12 }}>
          <canvas
            ref={canvasRef}
            width={raster.CAPE_W * CAPE_ZOOM}
            height={raster.CAPE_H * CAPE_ZOOM}
            onMouseDown={onDown}
            onMouseMove={onMove}
            onMouseUp={endStroke}
            onMouseLeave={endStroke}
            style={{
              imageRendering: "pixelated",
              cursor: tool === "picker" ? "copy" : "crosshair",
              display: "block",
              touchAction: "none",
            }}
          />
          <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginTop: 8, maxWidth: 300 }}>
            This is the whole cape — {raster.CAPE_W}×{raster.CAPE_H} pixels, exactly what shows on
            your back. The elytra half of the sheet is left as it was.
          </div>
        </GlassCard>

        <GlassCard style={{ padding: 12, width: 210 }}>
          <SidebarLabel>Mode</SidebarLabel>
          <div style={{ display: "flex", gap: 5, marginBottom: 6 }}>
            {(["pixel", "smooth"] as const).map((m) => (
              <button
                key={m}
                onClick={() => {
                  setMode(m);
                  // The grid is orientation for placing hard pixels and just
                  // clutter over a gradient.
                  setShowGrid(m === "pixel");
                }}
                style={{
                  flex: 1,
                  fontSize: 10.5,
                  padding: "5px 2px",
                  textTransform: "capitalize",
                  background: mode === m ? "var(--accent)" : undefined,
                  color: mode === m ? "var(--accent-fg)" : undefined,
                }}
              >
                {m === "pixel" ? "Pixel art" : "Smooth"}
              </button>
            ))}
          </div>
          <div style={{ fontSize: 10, color: "var(--text-tertiary)", marginBottom: 14, lineHeight: 1.5 }}>
            {mode === "pixel"
              ? "Hard whole pixels. Painting the same spot twice changes nothing."
              : "A soft brush that builds up as you go, for shading and glows."}
          </div>

          <SidebarLabel>Tool</SidebarLabel>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 4, marginBottom: 14 }}>
            {(["pen", "fill", "eraser", "picker"] as const).map((t) => (
              <button
                key={t}
                onClick={() => setTool(t)}
                title={TOOL_HINTS[t]}
                style={{
                  fontSize: 9.5,
                  padding: "5px 1px",
                  background: tool === t ? "var(--accent)" : undefined,
                  color: tool === t ? "var(--accent-fg)" : undefined,
                }}
              >
                {t === "picker" ? "pick" : t}
              </button>
            ))}
          </div>

          {tool !== "fill" && tool !== "picker" && (
            <>
              <SidebarLabel>Brush · {brushSize}px</SidebarLabel>
              <input
                type="range"
                min={1}
                max={8}
                step={1}
                value={brushSize}
                onChange={(e) => setBrushSize(Number(e.target.value))}
                style={sliderStyle}
              />
              {mode === "smooth" && (
                <>
                  <SidebarLabel>Flow · {Math.round(opacity * 100)}%</SidebarLabel>
                  <input
                    type="range"
                    min={5}
                    max={100}
                    step={5}
                    value={Math.round(opacity * 100)}
                    onChange={(e) => setOpacity(Number(e.target.value) / 100)}
                    style={sliderStyle}
                  />
                </>
              )}
            </>
          )}

          {tool === "fill" && (
            <>
              <SidebarLabel>Tolerance · {Math.round(tolerance * 100)}%</SidebarLabel>
              <input
                type="range"
                min={0}
                max={100}
                step={1}
                value={Math.round(tolerance * 100)}
                onChange={(e) => setTolerance(Number(e.target.value) / 100)}
                style={sliderStyle}
              />
              <div style={{ fontSize: 10, color: "var(--text-tertiary)", marginBottom: 10, lineHeight: 1.5 }}>
                How different a neighbouring pixel can be and still be filled. Raise it to swallow
                the soft edges a smooth brush leaves.
              </div>
              <label style={checkRow}>
                <input
                  type="checkbox"
                  checked={contiguous}
                  onChange={(e) => setContiguous(e.target.checked)}
                />
                <span>
                  <div style={{ fontSize: 11 }}>Connected only</div>
                  <div style={{ fontSize: 9.5, color: "var(--text-tertiary)", lineHeight: 1.4 }}>
                    Off, it recolours every matching pixel on the cape.
                  </div>
                </span>
              </label>
            </>
          )}

          <label style={{ ...checkRow, marginBottom: 12 }}>
            <input type="checkbox" checked={showGrid} onChange={(e) => setShowGrid(e.target.checked)} />
            <span style={{ fontSize: 11 }}>Show grid</span>
          </label>

          <SidebarLabel>Colour</SidebarLabel>
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

const TOOL_HINTS: Record<"pen" | "fill" | "eraser" | "picker", string> = {
  pen: "Paint",
  fill: "Paint bucket",
  eraser: "Erase back to transparent",
  picker: "Pick a colour off the cape",
};

function SidebarLabel({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        fontSize: 10,
        color: "var(--text-tertiary)",
        marginBottom: 6,
        textTransform: "uppercase",
        letterSpacing: "0.05em",
      }}
    >
      {children}
    </div>
  );
}

const sliderStyle: React.CSSProperties = { width: "100%", marginBottom: 12, accentColor: "var(--accent)" };
const checkRow: React.CSSProperties = {
  display: "flex",
  gap: 7,
  alignItems: "flex-start",
  cursor: "pointer",
  marginBottom: 14,
};

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
