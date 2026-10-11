import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { GlassCard } from "../components/GlassCard";
import { Icon } from "../components/Icon";
import { Avatar } from "../components/Avatar";
import { PageHeader } from "../components/PageHeader";
import { useAccountStore } from "../store/accountStore";
import * as api from "../lib/tauri";
import { open } from "@tauri-apps/plugin-dialog";
import {
  CAPE_AREA,
  CAPE_FACES,
  CAPE_SHEET_H,
  CAPE_SHEET_W,
  type Cape,
  type CapeFace,
} from "../types/cape";
import * as raster from "../lib/capeRaster";
import { SkinEditor } from "./SkinEditor";
import { SkinViewer } from "../components/SkinViewer";
import type { SkinVariant } from "../types/skin";

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

/**
 * Cape work in progress, held outside React.
 *
 * The editor lives inside `CapesTab`, which lives inside the Cosmetics page,
 * which App unmounts the moment you click any other icon in the nav rail. So
 * every piece of editor state was component state that a stray click on Home
 * threw away — half an hour of pixel work gone with no warning and nothing to
 * undo, which is the worst kind of data loss because it doesn't look like an
 * error, it looks like the app forgot.
 *
 * A module-level draft is the smallest thing that fixes it. It survives
 * unmount, so navigating away and back reopens the editor exactly as it was;
 * it's cleared on save and on cancel, which are the two moments the user has
 * actually said they're finished. It deliberately does not persist to disk —
 * "I closed the launcher" is a clear enough statement of intent, and a draft
 * that outlives the process would need its own management UI.
 */
interface CapeDraft {
  capeId: string | null;
  name: string;
  /** The face currently on the canvas. */
  data: Uint8ClampedArray;
  faceIndex: number;
  scale: number;
  /** The whole sheet, so the other five faces survive a round trip. */
  sheet: ImageData | null;
}

let capeDraft: CapeDraft | null = null;

function CapesTab() {
  const [capes, setCapes] = useState<Cape[]>([]);
  // Reopen straight into whatever was being drawn when the page unmounted.
  const [editing, setEditing] = useState<{ id: string | null; name: string } | null>(() =>
    capeDraft ? { id: capeDraft.capeId, name: capeDraft.name } : null
  );
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
 *
 * # Shapes, and why the circle is the point of them
 *
 * A freehand pen on a 10x16 grid can draw anything in principle and almost
 * nothing in practice: the shapes people actually want on a cape are straight
 * lines, boxes, rings and dots, and every one of them comes out wonky when
 * placed a pixel at a time. The circle is the worst of the four — there is a
 * single correct 5-pixel circle and nobody derives it by eye.
 *
 * So they're dragged out and rasterised from their equations: line, box, and
 * circle/ellipse, solid or outlined at an adjustable edge width. A drag
 * previews live and commits on release, which makes the whole gesture one undo
 * step and lets you re-aim it while the button is down.
 *
 * Two things multiply what those are worth:
 *
 *  - **Mirroring.** Cape designs are overwhelmingly symmetric, and half of ten
 *    pixels is five. With mirroring on you draw one side.
 *  - **The gradient.** Drag a direction and fade between two colours,
 *    optionally only over pixels that already have paint — which is how you
 *    shade an emblem without flooding the transparent background behind it.
 */

/** Screen pixels per cape pixel at 1x. Halved for each step up in resolution. */
const CAPE_ZOOM = 30;

/** Sheet multipliers offered. 1 is the classic 64x32; 4 is 256x128. */
const CAPE_SCALES = [1, 2, 4] as const;

/** A blank sheet at the given multiplier. */
function blankSheet(scale: number): ImageData {
  return new ImageData(CAPE_SHEET_W * scale, CAPE_SHEET_H * scale);
}

/** Everything about the sheet that the painting code needs, at this scale. */
function faceRect(face: CapeFace, scale: number) {
  return { x: face.x * scale, y: face.y * scale, w: face.w * scale, h: face.h * scale };
}

/**
 * A canvas holding a sheet, for the get/put round trips below.
 *
 * `ImageData` has no way to copy a sub-rectangle in or out on its own, and
 * hand-rolling the row-by-row slicing would be a loop to get wrong for no gain.
 */
function sheetCanvas(sheet: ImageData): CanvasRenderingContext2D | null {
  const canvas = document.createElement("canvas");
  canvas.width = sheet.width;
  canvas.height = sheet.height;
  const ctx = canvas.getContext("2d");
  ctx?.putImageData(sheet, 0, 0);
  return ctx;
}

/** Lift one face out of the sheet, to be painted on its own. */
function readFace(sheet: ImageData, face: CapeFace, scale: number): Uint8ClampedArray {
  const r = faceRect(face, scale);
  const ctx = sheetCanvas(sheet);
  if (!ctx) return new Uint8ClampedArray(r.w * r.h * 4);
  return new Uint8ClampedArray(ctx.getImageData(r.x, r.y, r.w, r.h).data);
}

/**
 * Put a painted face back into the sheet.
 *
 * `putImageData` writes verbatim rather than compositing, which is what's
 * wanted: the face buffer is the whole truth for its rectangle, transparent
 * pixels included. Compositing would make erasing impossible.
 */
function writeFace(
  sheet: ImageData,
  face: CapeFace,
  scale: number,
  data: Uint8ClampedArray
): ImageData {
  const r = faceRect(face, scale);
  const ctx = sheetCanvas(sheet);
  if (!ctx) return sheet;
  ctx.putImageData(new ImageData(new Uint8ClampedArray(data), r.w, r.h), r.x, r.y);
  return ctx.getImageData(0, 0, sheet.width, sheet.height);
}

/**
 * Resample a whole sheet to a different multiplier.
 *
 * Nearest-neighbour, always. Going up, smoothing would blur a design that was
 * drawn as hard pixels into mush; going down it would average colours that were
 * chosen exactly. Neither is ever what someone re-scaling pixel art wants.
 */
function rescaleSheet(sheet: ImageData, fromScale: number, toScale: number): ImageData {
  if (fromScale === toScale) return sheet;
  const source = sheetCanvas(sheet);
  if (!source) return sheet;

  const canvas = document.createElement("canvas");
  canvas.width = CAPE_SHEET_W * toScale;
  canvas.height = CAPE_SHEET_H * toScale;
  const ctx = canvas.getContext("2d");
  if (!ctx) return sheet;

  ctx.imageSmoothingEnabled = false;
  ctx.drawImage(source.canvas, 0, 0, canvas.width, canvas.height);
  return ctx.getImageData(0, 0, canvas.width, canvas.height);
}

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
  // A draft for this same cape means we're returning to work already in
  // progress — pick it up rather than reloading from disk. See CapeDraft.
  const resumed = capeDraft && capeDraft.capeId === capeId ? capeDraft : null;

  const [name, setName] = useState(resumed?.name ?? initialName);
  const [color, setColor] = useState(PALETTE[4]);
  const [color2, setColor2] = useState(PALETTE[0]);
  const [recent, setRecent] = useState<string[]>([]);
  const [tool, setTool] = useState<Tool>("pen");
  const [mode, setMode] = useState<"pixel" | "smooth">("pixel");
  const [brushSize, setBrushSize] = useState(1);
  const [roundBrush, setRoundBrush] = useState(true);
  const [opacity, setOpacity] = useState(0.6);
  const [tolerance, setTolerance] = useState(0.12);
  const [contiguous, setContiguous] = useState(true);
  const [showGrid, setShowGrid] = useState(true);

  // Shape options.
  const [filled, setFilled] = useState(true);
  const [thickness, setThickness] = useState(1);
  const [regular, setRegular] = useState(false);
  const [symX, setSymX] = useState(false);
  const [symY, setSymY] = useState(false);
  const [maskGradient, setMaskGradient] = useState(false);

  /**
   * Which of the cape's six faces is on the canvas, and how big the sheet is.
   *
   * The editor only ever paints one face at a time; `sheetRef` holds the whole
   * thing and the face is lifted in and out of it. That keeps everything below
   * — brushes, shapes, fills, symmetry — working on one plain rectangle,
   * whether that's the 10x16 outside or a 1x16 edge, and whether the sheet is
   * 1x or 4x.
   */
  const [faceIndex, setFaceIndex] = useState(resumed?.faceIndex ?? 0);
  const [scale, setScale] = useState(resumed?.scale ?? 1);
  const face = CAPE_FACES[faceIndex];
  const faceW = face.w * scale;
  const faceH = face.h * scale;

  /**
   * Screen pixels per cape pixel.
   *
   * Divided by the resolution so the canvas stays about the same physical size
   * as you add detail — a 4x cape at the 1x zoom would be 1200px wide and off
   * the screen. The floor keeps a 4x sheet's pixels big enough to hit.
   *
   * The thin faces get more magnification, because a 1x16 strip drawn at the
   * same scale as the 10x16 back is a sliver you cannot aim at.
   */
  const zoom = Math.max(6, Math.round((CAPE_ZOOM / scale) * (face.w <= 2 ? 2 : 1)));

  const [data, setData] = useState<Uint8ClampedArray>(
    () => resumed?.data ?? new Uint8ClampedArray(CAPE_FACES[0].w * CAPE_FACES[0].h * 4)
  );

  /** Wrap a face buffer for the raster, without copying it. */
  const surfaceOf = useCallback(
    (buffer: Uint8ClampedArray) => raster.surfaceOf(buffer, faceW, faceH),
    [faceW, faceH]
  );
  /**
   * The shape currently being dragged, drawn over the committed buffer.
   *
   * Held separately rather than repeatedly rewriting `data` so that a drag is
   * one undo step, not one per mouse-move — and so that releasing outside the
   * canvas leaves the last previewed shape rather than a trail of them.
   */
  const [preview, setPreview] = useState<Uint8ClampedArray | null>(null);
  const [history, setHistory] = useState<Uint8ClampedArray[]>([]);
  const [future, setFuture] = useState<Uint8ClampedArray[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canvasRef = useRef<HTMLCanvasElement>(null);
  const painting = useRef(false);
  const lastPoint = useRef<{ x: number; y: number } | null>(null);
  /** Where a shape or gradient drag began, in cape coordinates. */
  const dragStart = useRef<{ x: number; y: number } | null>(null);

  /**
   * An image staged for import, with its placement.
   *
   * Staged rather than applied on pick, because nothing is natively
   * cape-shaped: the first result of dropping a square logo onto a 10x16 canvas
   * is almost never the one you want, and the fit and zoom controls are only
   * useful if you can see what they do before committing.
   */
  const [importing, setImporting] = useState<{
    image: HTMLImageElement;
    name: string;
  } | null>(null);
  const [importOpts, setImportOpts] = useState<raster.ImportOptions>(raster.DEFAULT_IMPORT);
  const fileInput = useRef<HTMLInputElement>(null);
  /**
   * The whole sheet for the cape being edited.
   *
   * All six faces live here; the canvas only ever shows one of them. Held so
   * that saving writes back a complete sheet — switching face, or editing a
   * cape someone imported, must not blank the parts you weren't looking at.
   */
  const sheetRef = useRef<ImageData | null>(resumed?.sheet ?? null);

  // Load an existing cape: the whole sheet into sheetRef, the current face onto
  // the canvas, and the resolution read off the sheet's own width.
  useEffect(() => {
    // Resuming a draft: what's in memory is newer than what's on disk.
    if (!capeId || resumed) return;
    let cancelled = false;

    api
      .readCape(capeId)
      .then((b64) => {
        const img = new Image();
        img.onload = () => {
          if (cancelled) return;

          // The sheet's width tells us its resolution: 64 is 1x, 128 is 2x. A
          // width that isn't a multiple of 64 isn't a cape sheet, so fall back
          // to 1x and let the visible result speak for itself rather than
          // refusing to open the file.
          const detected = Math.max(1, Math.round(img.naturalWidth / CAPE_SHEET_W)) || 1;
          const off = document.createElement("canvas");
          off.width = CAPE_SHEET_W * detected;
          off.height = CAPE_SHEET_H * detected;
          const octx = off.getContext("2d");
          if (!octx) return;
          octx.imageSmoothingEnabled = false;
          octx.drawImage(img, 0, 0, off.width, off.height);

          const sheet = octx.getImageData(0, 0, off.width, off.height);
          sheetRef.current = sheet;
          setScale(detected);
          setData(readFace(sheet, CAPE_FACES[0], detected));
          setFaceIndex(0);
        };
        img.src = `data:image/png;base64,${b64}`;
      })
      .catch(() => {});

    return () => {
      cancelled = true;
    };
    // `resumed` is read once on mount to decide whether to load at all; adding
    // it here would re-run the load the moment the draft is cleared on save.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [capeId]);

  // Keep the out-of-React draft current, so an unmount loses nothing. Cheap:
  // the buffer is 640 bytes and this only runs when something actually changed.
  useEffect(() => {
    capeDraft = { capeId, name, data, faceIndex, scale, sheet: sheetRef.current };
  }, [capeId, name, data, faceIndex, scale]);

  // Repaint whenever the buffer changes.
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    // Checkerboard, at cape-pixel scale, so transparency is legible.
    for (let y = 0; y < faceH; y++) {
      for (let x = 0; x < faceW; x++) {
        ctx.fillStyle = (x + y) % 2 === 0 ? "#0d2531" : "#0a1c26";
        ctx.fillRect(x * zoom, y * zoom, zoom, zoom);
      }
    }

    // Blit the buffer through a 1:1 offscreen canvas and scale it up with
    // smoothing off. Drawing 160 individual rects would also work, but this
    // keeps partial alpha exact instead of round-tripping it through
    // `fillStyle` strings.
    const off = document.createElement("canvas");
    off.width = faceW;
    off.height = faceH;
    const octx = off.getContext("2d");
    if (!octx) return;
    octx.putImageData(
      new ImageData(new Uint8ClampedArray(preview ?? data), faceW, faceH),
      0,
      0
    );

    ctx.imageSmoothingEnabled = false;
    ctx.drawImage(off, 0, 0, canvas.width, canvas.height);

    if (showGrid) {
      // Hairline grid. Off in smooth mode by default, where cell boundaries
      // are not what you're looking at.
      ctx.strokeStyle = "rgba(125,226,240,0.14)";
      ctx.lineWidth = 1;
      ctx.beginPath();
      for (let x = 1; x < faceW; x++) {
        ctx.moveTo(x * zoom + 0.5, 0);
        ctx.lineTo(x * zoom + 0.5, canvas.height);
      }
      for (let y = 1; y < faceH; y++) {
        ctx.moveTo(0, y * zoom + 0.5);
        ctx.lineTo(canvas.width, y * zoom + 0.5);
      }
      ctx.stroke();
    }

    // Frame the canvas so the cape's own edge is unambiguous against the card.
    ctx.strokeStyle = "#35e0d0";
    ctx.lineWidth = 2;
    ctx.strokeRect(1, 1, canvas.width - 2, canvas.height - 2);

    // Symmetry guides, drawn last so they sit over the art. Without them
    // mirrored painting is a surprise rather than a tool — you can't aim at an
    // axis you can't see.
    if (symX || symY) {
      ctx.strokeStyle = "rgba(53,224,208,0.45)";
      ctx.setLineDash([4, 4]);
      ctx.lineWidth = 1;
      ctx.beginPath();
      if (symX) {
        ctx.moveTo(canvas.width / 2 + 0.5, 0);
        ctx.lineTo(canvas.width / 2 + 0.5, canvas.height);
      }
      if (symY) {
        ctx.moveTo(0, canvas.height / 2 + 0.5);
        ctx.lineTo(canvas.width, canvas.height / 2 + 0.5);
      }
      ctx.stroke();
      ctx.setLineDash([]);
    }
    // `faceW`/`faceH`/`zoom` change together when the face or resolution does,
    // and the canvas has to be repainted at its new size when they do.
  }, [data, preview, showGrid, symX, symY, faceW, faceH, zoom]);

  const brush = useMemo<raster.Brush>(
    () => ({
      soft: mode === "smooth",
      size: brushSize,
      round: roundBrush,
      opacity,
      color: raster.hexToRgb(color),
      erase: tool === "eraser",
    }),
    [mode, brushSize, roundBrush, opacity, color, tool]
  );

  const symmetry = useMemo<raster.Symmetry>(() => ({ x: symX, y: symY }), [symX, symY]);

  const shapeOptions = useMemo<raster.ShapeOptions>(
    () => ({
      kind: tool === "circle" ? "ellipse" : tool === "rect" ? "rect" : "line",
      filled,
      thickness,
      regular,
      brush,
      symmetry,
    }),
    [tool, filled, thickness, regular, brush, symmetry]
  );

  /** Cape-pixel coordinates, fractional — the soft brush needs sub-pixel aim. */
  function pointAt(e: React.MouseEvent<HTMLCanvasElement>) {
    const rect = e.currentTarget.getBoundingClientRect();
    return {
      x: ((e.clientX - rect.left) / rect.width) * faceW,
      y: ((e.clientY - rect.top) / rect.height) * faceH,
    };
  }

  // Re-render the staged image whenever it or its placement changes. Shares the
  // `preview` buffer with shape drags — both are "what the canvas would look
  // like if you committed right now", and only one can be in flight at a time.
  useEffect(() => {
    if (!importing) return;
    setPreview(
      raster.drawImageOntoCape(
        surfaceOf(data),
        importing.image,
        importing.image.naturalWidth,
        importing.image.naturalHeight,
        importOpts
      ).data
    );
  }, [importing, importOpts, data]);

  /**
   * Load a picked file into an `Image`.
   *
   * A plain `<input type="file">` rather than the Tauri dialog the rest of this
   * page uses, because what's needed here is the *bytes* in the webview. The
   * dialog hands back a filesystem path, which would then need a backend
   * command and a permission to read — a round trip to fetch something the
   * browser will hand over directly.
   */
  function onPickImage(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    // Reset immediately so picking the same file twice still fires a change.
    e.target.value = "";
    if (!file) return;

    const url = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => {
      URL.revokeObjectURL(url);
      setImportOpts(raster.DEFAULT_IMPORT);
      setImporting({ image, name: file.name });
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      setError(`Could not read ${file.name} as an image.`);
    };
    image.src = url;
  }

  function applyImport() {
    if (!preview) return;
    checkpoint();
    setData(preview);
    setImporting(null);
    setPreview(null);
  }

  function cancelImport() {
    setImporting(null);
    setPreview(null);
  }

  /**
   * Fold whatever's on the canvas back into the sheet.
   *
   * Every path that leaves the current face — switching face, changing
   * resolution, saving — goes through here first. Miss it and the face you were
   * painting is simply discarded, which is the obvious bug in a
   * one-face-at-a-time editor and the reason it's a single named step.
   */
  function commitFace(sheetOverride?: ImageData): ImageData {
    const sheet = sheetOverride ?? sheetRef.current ?? blankSheet(scale);
    const next = writeFace(sheet, face, scale, data);
    sheetRef.current = next;
    return next;
  }

  function switchFace(index: number) {
    if (index === faceIndex) return;
    const sheet = commitFace();
    setFaceIndex(index);
    setData(readFace(sheet, CAPE_FACES[index], scale));
    // History is per-face: undoing across a switch would put pixels back onto
    // a canvas they never came from.
    setHistory([]);
    setFuture([]);
    setPreview(null);
    cancelImport();
  }

  /**
   * Change the sheet's resolution, resampling everything already drawn.
   *
   * Going up is the point of the control — 10x16 is not enough pixels to draw
   * anything detailed on — and it's non-destructive: each pixel becomes a block
   * of identical pixels, so the design is unchanged and now has room in it.
   * Going back down is destructive and says so at the call site.
   */
  function changeScale(next: number) {
    if (next === scale) return;
    const sheet = rescaleSheet(commitFace(), scale, next);
    sheetRef.current = sheet;
    setScale(next);
    setData(readFace(sheet, face, next));
    setHistory([]);
    setFuture([]);
    setPreview(null);
    cancelImport();
  }

  /** Snapshot the buffer for undo, and drop any redo branch. */
  function checkpoint(from: Uint8ClampedArray = data) {
    setHistory((h) => [...h.slice(-31), new Uint8ClampedArray(from)]);
    setFuture([]);
  }

  /** Remember a colour that got used, most recent first. */
  function noteColor(hex: string) {
    setRecent((r) => [hex, ...r.filter((c) => c !== hex)].slice(0, 12));
  }

  /** Render a drag's shape or gradient onto a copy of the committed buffer. */
  function renderDrag(from: { x: number; y: number }, to: { x: number; y: number }) {
    const next = new Uint8ClampedArray(data);
    if (tool === "gradient") {
      raster.gradient(surfaceOf(next), from.x, from.y, to.x, to.y, {
        from: raster.hexToRgb(color),
        to: raster.hexToRgb(color2),
        opacity: mode === "smooth" ? opacity : 1,
        maskToPainted: maskGradient,
        symmetry,
      });
    } else {
      raster.drawShape(surfaceOf(next), from.x, from.y, to.x, to.y, shapeOptions);
    }
    return next;
  }

  function onDown(e: React.MouseEvent<HTMLCanvasElement>) {
    // While an image is staged the canvas is showing a preview, not the cape.
    // Painting onto it would be painting onto something that's about to be
    // thrown away.
    if (importing) return;

    const p = pointAt(e);

    if (tool === "picker") {
      const hex = raster.pick(surfaceOf(data), Math.floor(p.x), Math.floor(p.y));
      if (hex) setColor(hex);
      // Snap back afterwards: an eyedropper you have to switch away from
      // manually is one extra click on every single use. Back to whatever
      // space interrupted, if that's how we got here — picking a colour
      // mid-stroke should hand you back the brush you were holding, not the
      // pen you might not have been using.
      setTool(beforeSpace.current ?? "pen");
      return;
    }

    if (tool === "fill") {
      checkpoint();
      noteColor(color);
      setData(
        raster.bucketFill(surfaceOf(data), Math.floor(p.x), Math.floor(p.y), raster.hexToRgb(color), {
          tolerance,
          contiguous,
          erase: false,
        }).data
      );
      return;
    }

    // Shapes and gradients are dragged out and only committed on release, so
    // the whole gesture is a single undo step and can be re-aimed while the
    // button is held.
    if (isDragTool(tool)) {
      dragStart.current = p;
      setPreview(renderDrag(p, p));
      return;
    }

    checkpoint();
    if (tool !== "eraser") noteColor(color);
    painting.current = true;
    lastPoint.current = p;
    const next = new Uint8ClampedArray(data);
    raster.stampSym(surfaceOf(next), p.x, p.y, brush, symmetry);
    setData(next);
  }

  function onMove(e: React.MouseEvent<HTMLCanvasElement>) {
    if (importing) return;
    const p = pointAt(e);

    if (dragStart.current) {
      setPreview(renderDrag(dragStart.current, p));
      return;
    }

    if (!painting.current) return;
    const from = lastPoint.current ?? p;
    lastPoint.current = p;

    setData((prev) => {
      const next = new Uint8ClampedArray(prev);
      raster.strokeSym(surfaceOf(next), from.x, from.y, p.x, p.y, brush, symmetry);
      return next;
    });
  }

  function endStroke() {
    if (dragStart.current) {
      dragStart.current = null;
      if (preview) {
        checkpoint();
        if (tool !== "eraser") noteColor(color);
        setData(preview);
      }
      setPreview(null);
    }
    painting.current = false;
    lastPoint.current = null;
  }

  // End the gesture on a mouse-up anywhere, not just over the canvas.
  //
  // The canvas used to end it on mouseleave as well, which was right for a
  // freehand stroke and wrong for everything added since: dragging a circle
  // slightly past the edge of a 10-pixel-wide cape is normal, and committing
  // the shape the moment the cursor crossed the border made the tool feel like
  // it was snatching at the drag. Now leaving the canvas does nothing — paint
  // outside the bounds is clipped by the raster anyway — and releasing the
  // button is what finishes it, wherever the pointer happens to be.
  const endStrokeRef = useRef(endStroke);
  endStrokeRef.current = endStroke;
  useEffect(() => {
    const up = () => endStrokeRef.current();
    window.addEventListener("mouseup", up);
    return () => window.removeEventListener("mouseup", up);
  }, []);

  function undo() {
    if (history.length === 0) return;
    setFuture((f) => [new Uint8ClampedArray(data), ...f.slice(0, 31)]);
    setData(history[history.length - 1]);
    setHistory((h) => h.slice(0, -1));
  }

  function redo() {
    if (future.length === 0) return;
    setHistory((h) => [...h.slice(-31), new Uint8ClampedArray(data)]);
    setData(future[0]);
    setFuture((f) => f.slice(1));
  }

  // Ctrl+Z / Ctrl+Y, plus Ctrl+Shift+Z for the redo binding the rest of the
  // world uses. Bound on the window rather than on the canvas because the
  // canvas can't hold focus — it isn't a focusable element, so a keydown
  // handler on it would only ever fire if you happened to tab to it, which you
  // can't.
  //
  // Refs, not the functions directly, so the listener is attached once instead
  // of being torn down and rebuilt on every stroke.
  const undoRef = useRef(undo);
  const redoRef = useRef(redo);
  undoRef.current = undo;
  redoRef.current = redo;

  /**
   * The tool to go back to when space is released.
   *
   * Holding space for the eyedropper is a spring-loaded mode: you're mid-stroke,
   * you want the colour under the cursor, and switching tool + switching back is
   * three clicks for a one-second detour. Null means space isn't held.
   */
  const beforeSpace = useRef<Tool | null>(null);

  useEffect(() => {
    function typing(e: KeyboardEvent): boolean {
      const target = e.target as HTMLElement | null;
      return !!target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA");
    }

    function onKey(e: KeyboardEvent) {
      // Never steal a keystroke from a text field — the name box is right
      // there, and both undo and the space bar belong to it while it has focus.
      if (typing(e)) return;

      if (e.code === "Space") {
        // Also stops the page scrolling, which is what space does otherwise.
        e.preventDefault();
        // Guard on the ref, not on the tool: key repeat fires this many times
        // per second, and without it the second event would record "picker" as
        // the tool to restore.
        if (beforeSpace.current === null) {
          setTool((current) => {
            beforeSpace.current = current;
            return "picker";
          });
        }
        return;
      }

      if (!e.ctrlKey && !e.metaKey) return;
      const key = e.key.toLowerCase();
      if (key === "z" && !e.shiftKey) {
        e.preventDefault();
        undoRef.current();
      } else if (key === "y" || (key === "z" && e.shiftKey)) {
        e.preventDefault();
        redoRef.current();
      }
    }

    function onKeyUp(e: KeyboardEvent) {
      if (e.code !== "Space" || typing(e)) return;
      const previous = beforeSpace.current;
      beforeSpace.current = null;
      // Only restore if we're still on the picker — picking a colour snaps back
      // to the pen on its own, and overriding that would undo the pick's own
      // sensible behaviour.
      if (previous !== null) setTool((current) => (current === "picker" ? previous : current));
    }

    window.addEventListener("keydown", onKey);
    window.addEventListener("keyup", onKeyUp);
    return () => {
      window.removeEventListener("keydown", onKey);
      window.removeEventListener("keyup", onKeyUp);
    };
  }, []);

  function clear() {
    checkpoint();
    setData(new Uint8ClampedArray(faceW * faceH * 4));
  }

  /** Fold the current face into the sheet, encode it, hand it to the backend. */
  async function save() {
    setBusy(true);
    setError(null);
    try {
      // The face on the canvas is the only part of the sheet that isn't
      // already in sheetRef, so committing it is the whole composition step.
      const sheet = commitFace();

      const off = document.createElement("canvas");
      off.width = sheet.width;
      off.height = sheet.height;
      const ctx = off.getContext("2d");
      if (!ctx) throw new Error("could not get a canvas context");
      ctx.putImageData(sheet, 0, 0);

      // Strip the `data:image/png;base64,` prefix — the backend wants raw base64.
      const b64 = off.toDataURL("image/png").split(",")[1];

      // Editing replaces rather than mutating: capes are content-addressed by
      // hash on the wire, so an edited cape is genuinely a different cape.
      if (capeId) await api.deleteCape(capeId);
      await api.saveCape(name.trim() || "Untitled", b64);
      // It's on disk now; the draft would only ever be a stale copy of it.
      capeDraft = null;
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
        {/* Cancel is the one click that's *meant* to discard, so it's also the
            one that clears the draft. Everything else preserves it. */}
        <button
          onClick={() => {
            capeDraft = null;
            onCancel();
          }}
          disabled={busy}
        >
          Cancel
        </button>
        <button onClick={undo} disabled={history.length === 0} title="Ctrl+Z">Undo</button>
        <button onClick={redo} disabled={future.length === 0} title="Ctrl+Y">Redo</button>
        <button onClick={clear}>Clear</button>
        <button onClick={() => fileInput.current?.click()} disabled={!!importing}>
          Import image…
        </button>
        <input
          ref={fileInput}
          type="file"
          accept="image/*"
          onChange={onPickImage}
          style={{ display: "none" }}
        />
      </div>

      {error && (
        <div style={{ color: "var(--danger)", fontSize: 12, marginBottom: 12 }}>{error}</div>
      )}

      <div style={{ display: "flex", gap: 10, alignItems: "center", marginBottom: 12, flexWrap: "wrap" }}>
        <span style={{ fontSize: 10, color: "var(--text-tertiary)", textTransform: "uppercase", letterSpacing: "0.05em" }}>
          Side
        </span>
        {CAPE_FACES.map((f, i) => (
          <button
            key={f.key}
            onClick={() => switchFace(i)}
            title={f.hint}
            style={{
              fontSize: 10.5,
              padding: "5px 10px",
              background: faceIndex === i ? "var(--accent)" : undefined,
              color: faceIndex === i ? "var(--accent-fg)" : undefined,
            }}
          >
            {f.label}
          </button>
        ))}

        <span style={{ width: 12 }} />
        <span style={{ fontSize: 10, color: "var(--text-tertiary)", textTransform: "uppercase", letterSpacing: "0.05em" }}>
          Detail
        </span>
        {CAPE_SCALES.map((sc) => (
          <button
            key={sc}
            onClick={() => {
              if (sc < scale && !confirm(
                `Going down to ${sc}x throws pixels away — detail drawn at ${scale}x can't be recovered. Continue?`
              )) {
                return;
              }
              changeScale(sc);
            }}
            title={`${CAPE_SHEET_W * sc}x${CAPE_SHEET_H * sc} sheet — the cape face becomes ${CAPE_AREA.w * sc}x${CAPE_AREA.h * sc}`}
            style={{
              fontSize: 10.5,
              padding: "5px 10px",
              background: scale === sc ? "var(--accent)" : undefined,
              color: scale === sc ? "var(--accent-fg)" : undefined,
            }}
          >
            {sc}x
          </button>
        ))}
      </div>

      <div style={{ display: "flex", gap: 16, alignItems: "flex-start", flexWrap: "wrap" }}>
        <GlassCard style={{ padding: 12 }}>
          <canvas
            ref={canvasRef}
            width={faceW * zoom}
            height={faceH * zoom}
            onMouseDown={onDown}
            onMouseMove={onMove}
            style={{
              imageRendering: "pixelated",
              cursor: tool === "picker" ? "copy" : isDragTool(tool) ? "cell" : "crosshair",
              display: "block",
              touchAction: "none",
            }}
          />
          <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginTop: 8, maxWidth: 300 }}>
            {face.hint} {faceW}×{faceH} pixels
            {scale > 1 ? ` at ${scale}x` : ""}. The elytra half of the sheet is left as it was.
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
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 4, marginBottom: 12 }}>
            {TOOLS.map((t) => (
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
                {TOOL_LABELS[t]}
              </button>
            ))}
          </div>

          <SidebarLabel>Mirror</SidebarLabel>
          <div style={{ display: "flex", gap: 5, marginBottom: 6 }}>
            <button
              onClick={() => setSymX((v) => !v)}
              title="Mirror everything you paint across the cape's vertical centre line"
              style={{
                flex: 1,
                fontSize: 10.5,
                padding: "5px 2px",
                background: symX ? "var(--accent)" : undefined,
                color: symX ? "var(--accent-fg)" : undefined,
              }}
            >
              Left ↔ right
            </button>
            <button
              onClick={() => setSymY((v) => !v)}
              title="Mirror everything you paint across the cape's horizontal centre line"
              style={{
                flex: 1,
                fontSize: 10.5,
                padding: "5px 2px",
                background: symY ? "var(--accent)" : undefined,
                color: symY ? "var(--accent-fg)" : undefined,
              }}
            >
              Top ↕ bottom
            </button>
          </div>
          <div style={{ fontSize: 10, color: "var(--text-tertiary)", marginBottom: 14, lineHeight: 1.5 }}>
            Paint one half and get the other free. Most cape designs are symmetric, and ten pixels
            across is not a lot of room to get a mirror right by hand.
          </div>

          {isShapeTool(tool) && (
            <>
              <SidebarLabel>{tool === "circle" ? "Circle" : "Rectangle"}</SidebarLabel>
              <div style={{ display: "flex", gap: 5, marginBottom: 10 }}>
                {([true, false] as const).map((f) => (
                  <button
                    key={String(f)}
                    onClick={() => setFilled(f)}
                    style={{
                      flex: 1,
                      fontSize: 10.5,
                      padding: "5px 2px",
                      background: filled === f ? "var(--accent)" : undefined,
                      color: filled === f ? "var(--accent-fg)" : undefined,
                    }}
                  >
                    {f ? "Solid" : "Outline"}
                  </button>
                ))}
              </div>

              {!filled && (
                <>
                  <SidebarLabel>Edge · {thickness}px</SidebarLabel>
                  <input
                    type="range"
                    min={1}
                    max={4}
                    step={1}
                    value={thickness}
                    onChange={(e) => setThickness(Number(e.target.value))}
                    style={sliderStyle}
                  />
                </>
              )}

              <label style={checkRow}>
                <input
                  type="checkbox"
                  checked={regular}
                  onChange={(e) => setRegular(e.target.checked)}
                />
                <span>
                  <div style={{ fontSize: 11 }}>
                    {tool === "circle" ? "Perfect circle" : "Perfect square"}
                  </div>
                  <div style={{ fontSize: 9.5, color: "var(--text-tertiary)", lineHeight: 1.4 }}>
                    Off, the drag box sets the width and height separately — an ellipse.
                  </div>
                </span>
              </label>
            </>
          )}

          {tool === "gradient" && (
            <>
              <SidebarLabel>Gradient</SidebarLabel>
              <div style={{ fontSize: 10, color: "var(--text-tertiary)", marginBottom: 10, lineHeight: 1.5 }}>
                Drag across the cape: the direction of the drag is the direction of the ramp, from
                the main colour to the second one below.
              </div>
              <label style={checkRow}>
                <input
                  type="checkbox"
                  checked={maskGradient}
                  onChange={(e) => setMaskGradient(e.target.checked)}
                />
                <span>
                  <div style={{ fontSize: 11 }}>Only where painted</div>
                  <div style={{ fontSize: 9.5, color: "var(--text-tertiary)", lineHeight: 1.4 }}>
                    Shade a shape you've already drawn without spilling onto the transparent
                    background around it.
                  </div>
                </span>
              </label>
            </>
          )}

          {(tool === "pen" || tool === "eraser" || tool === "line") && (
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

              {mode === "pixel" && (
                <>
                  <div style={{ display: "flex", gap: 5, marginBottom: 8, alignItems: "center" }}>
                    {([true, false] as const).map((r) => (
                      <button
                        key={String(r)}
                        onClick={() => setRoundBrush(r)}
                        style={{
                          flex: 1,
                          fontSize: 10.5,
                          padding: "5px 2px",
                          background: roundBrush === r ? "var(--accent)" : undefined,
                          color: roundBrush === r ? "var(--accent-fg)" : undefined,
                        }}
                      >
                        {r ? "Round" : "Square"}
                      </button>
                    ))}
                    <BrushFootprint size={brushSize} round={roundBrush} />
                  </div>
                  <div style={{ fontSize: 10, color: "var(--text-tertiary)", marginBottom: 12, lineHeight: 1.5 }}>
                    Round drops the corners, so a stroke covers fewer pixels and
                    all of them are near where you clicked. On a cape ten pixels
                    wide that's the difference between a line and a smear.
                  </div>
                </>
              )}
            </>
          )}

          {/* Flow applies to anything that lays paint down, not just the brush:
              in smooth mode a shape and a gradient are both composited at this
              opacity. */}
          {mode === "smooth" && tool !== "fill" && tool !== "picker" && (
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

          {tool === "gradient" && (
            <>
              <SidebarLabel>Fades to</SidebarLabel>
              <input
                type="color"
                value={color2}
                onChange={(e) => setColor2(e.target.value)}
                style={{
                  width: "100%",
                  height: 30,
                  background: "transparent",
                  border: "none",
                  cursor: "pointer",
                  marginBottom: 4,
                }}
              />
              <button
                onClick={() => {
                  setColor(color2);
                  setColor2(color);
                }}
                style={{ width: "100%", fontSize: 10, padding: "4px 2px", marginBottom: 10 }}
              >
                Swap ends
              </button>
            </>
          )}

          {/* Recently used, so a colour mixed by hand in the picker can be got
              back after wandering off to another one. The custom picker is
              where most cape colours come from, and it has no memory of its
              own. */}
          {recent.length > 0 && (
            <>
              <SidebarLabel>Recent</SidebarLabel>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(6, 1fr)", gap: 4 }}>
                {recent.map((c) => (
                  <button
                    key={c}
                    onClick={() => setColor(c)}
                    title={c}
                    className="bare"
                    style={{
                      width: "100%",
                      aspectRatio: "1",
                      background: c,
                      border:
                        color === c ? "2px solid var(--text-primary)" : "2px solid rgba(0,0,0,0.4)",
                      cursor: "pointer",
                      padding: 0,
                    }}
                  />
                ))}
              </div>
            </>
          )}
        </GlassCard>

        {importing && (
          <GlassCard style={{ padding: 12, width: 210 }}>
            <SidebarLabel>Placing image</SidebarLabel>
            <div
              style={{
                fontSize: 11,
                color: "var(--text-secondary)",
                marginBottom: 4,
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
              }}
              title={importing.name}
            >
              {importing.name}
            </div>
            <div style={{ fontSize: 10, color: "var(--text-tertiary)", marginBottom: 12 }}>
              {importing.image.naturalWidth}×{importing.image.naturalHeight} →{" "}
              {faceW}×{faceH}
            </div>

            <SidebarLabel>Fit</SidebarLabel>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 4, marginBottom: 6 }}>
              {(["contain", "cover", "stretch"] as const).map((f) => (
                <button
                  key={f}
                  onClick={() => setImportOpts((o) => ({ ...o, fit: f }))}
                  title={FIT_HINTS[f]}
                  style={{
                    fontSize: 9.5,
                    padding: "5px 1px",
                    background: importOpts.fit === f ? "var(--accent)" : undefined,
                    color: importOpts.fit === f ? "var(--accent-fg)" : undefined,
                  }}
                >
                  {f === "contain" ? "fit" : f === "cover" ? "fill" : "squash"}
                </button>
              ))}
            </div>
            <div style={{ fontSize: 10, color: "var(--text-tertiary)", marginBottom: 12, lineHeight: 1.5 }}>
              {FIT_HINTS[importOpts.fit]}
            </div>

            <SidebarLabel>Zoom · {Math.round(importOpts.zoom * 100)}%</SidebarLabel>
            <input
              type="range"
              min={10}
              max={400}
              step={5}
              value={Math.round(importOpts.zoom * 100)}
              onChange={(e) => setImportOpts((o) => ({ ...o, zoom: Number(e.target.value) / 100 }))}
              style={sliderStyle}
            />

            <SidebarLabel>Nudge across</SidebarLabel>
            <input
              type="range"
              min={-100}
              max={100}
              step={2}
              value={Math.round(importOpts.offsetX * 100)}
              onChange={(e) => setImportOpts((o) => ({ ...o, offsetX: Number(e.target.value) / 100 }))}
              style={sliderStyle}
            />
            <SidebarLabel>Nudge down</SidebarLabel>
            <input
              type="range"
              min={-100}
              max={100}
              step={2}
              value={Math.round(importOpts.offsetY * 100)}
              onChange={(e) => setImportOpts((o) => ({ ...o, offsetY: Number(e.target.value) / 100 }))}
              style={sliderStyle}
            />

            <SidebarLabel>Opacity · {Math.round(importOpts.opacity * 100)}%</SidebarLabel>
            <input
              type="range"
              min={5}
              max={100}
              step={5}
              value={Math.round(importOpts.opacity * 100)}
              onChange={(e) => setImportOpts((o) => ({ ...o, opacity: Number(e.target.value) / 100 }))}
              style={sliderStyle}
            />

            <label style={checkRow}>
              <input
                type="checkbox"
                checked={importOpts.pixelated}
                onChange={(e) => setImportOpts((o) => ({ ...o, pixelated: e.target.checked }))}
              />
              <span>
                <div style={{ fontSize: 11 }}>Keep it crisp</div>
                <div style={{ fontSize: 9.5, color: "var(--text-tertiary)", lineHeight: 1.4 }}>
                  Nearest-neighbour. Smooth scaling averages a detailed image
                  into ten pixels of grey; turn it off for a soft gradient.
                </div>
              </span>
            </label>

            <label style={checkRow}>
              <input
                type="checkbox"
                checked={importOpts.replace}
                onChange={(e) => setImportOpts((o) => ({ ...o, replace: e.target.checked }))}
              />
              <span>
                <div style={{ fontSize: 11 }}>Replace the cape</div>
                <div style={{ fontSize: 9.5, color: "var(--text-tertiary)", lineHeight: 1.4 }}>
                  Off, the image composites over what's already painted.
                </div>
              </span>
            </label>

            <div style={{ display: "flex", gap: 6 }}>
              <button onClick={cancelImport} style={{ flex: 1, fontSize: 11, padding: "5px 8px" }}>
                Cancel
              </button>
              <button
                className="accent"
                onClick={applyImport}
                style={{ flex: 1, fontSize: 11, padding: "5px 8px" }}
              >
                Place
              </button>
            </div>
          </GlassCard>
        )}
      </div>
    </div>
  );
}

const FIT_HINTS: Record<raster.FitMode, string> = {
  contain: "The whole image, letterboxed. Nothing gets cropped.",
  cover: "Fills the cape. The overhanging edges are cropped off.",
  stretch: "Squashed to the cape's shape, ignoring its own.",
};

type Tool = "pen" | "line" | "rect" | "circle" | "gradient" | "fill" | "eraser" | "picker";

/** In the order they appear in the palette grid: draw, then shape, then edit. */
const TOOLS: readonly Tool[] = [
  "pen",
  "line",
  "rect",
  "circle",
  "gradient",
  "fill",
  "eraser",
  "picker",
] as const;

const TOOL_LABELS: Record<Tool, string> = {
  pen: "pen",
  line: "line",
  rect: "box",
  circle: "circle",
  gradient: "fade",
  fill: "fill",
  eraser: "erase",
  picker: "pick",
};

const TOOL_HINTS: Record<Tool, string> = {
  pen: "Paint freehand",
  line: "Drag a straight line",
  rect: "Drag out a rectangle",
  circle: "Drag out a circle or ellipse — the shape that's near-impossible to get right by hand",
  gradient: "Drag to fade between two colours",
  fill: "Paint bucket",
  eraser: "Erase back to transparent",
  picker: "Pick a colour off the cape",
};

/** Tools that are dragged out and committed on release, not painted live. */
function isDragTool(tool: Tool): boolean {
  return tool === "line" || tool === "rect" || tool === "circle" || tool === "gradient";
}

/** The two that have a fill/outline choice. A line has neither. */
function isShapeTool(tool: Tool): boolean {
  return tool === "rect" || tool === "circle";
}

/**
 * The brush's actual footprint, drawn at 1:1 next to the shape toggle.
 *
 * "3px round" is an abstraction; five squares in a plus is the thing itself.
 * Worth the twenty lines because the whole point of the round brush is that it
 * covers *fewer* pixels than the number on the slider suggests, and no label
 * conveys that as fast as showing it.
 */
function BrushFootprint({ size, round }: { size: number; round: boolean }) {
  const cells = raster.brushFootprint(size, round);
  const n = Math.max(1, Math.round(size));
  const half = Math.floor((n - 1) / 2);
  const dot = 3;
  const box = n * dot;

  return (
    <svg
      width={box}
      height={box}
      viewBox={`0 0 ${box} ${box}`}
      aria-label={`${cells.length} pixels`}
      role="img"
      style={{ flexShrink: 0, opacity: 0.9 }}
    >
      <title>{`${cells.length} pixel${cells.length === 1 ? "" : "s"}`}</title>
      {cells.map((c) => (
        <rect
          key={`${c.dx},${c.dy}`}
          x={(c.dx + half) * dot}
          y={(c.dy + half) * dot}
          width={dot}
          height={dot}
          fill="var(--accent)"
        />
      ))}
    </svg>
  );
}

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

/**
 * One saved skin: a turnable 3D preview and the three things you'd do with it.
 *
 * A rotating model rather than a flat sheet or a front-on avatar, because a
 * sheet is unreadable and a front view hides the half of the skin people
 * actually put work into. Idle spin is off — a grid of eight spinning models is
 * a fairground, not a library — so each one turns only when you drag it.
 */
function SkinCard({
  skin,
  busy,
  onWear,
  onEdit,
  onDelete,
}: {
  skin: api.Skin;
  busy: boolean;
  onWear: () => void;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const [src, setSrc] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    api
      .readSkin(skin.id)
      .then((b64) => {
        if (!cancelled) setSrc(`data:image/png;base64,${b64}`);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [skin.id]);

  return (
    <div style={{ border: "1px solid var(--glass-border)", padding: 8 }}>
      <SkinViewer
        src={src}
        variant={skin.variant === "slim" ? "slim" : "classic"}
        unit={3}
        spin={false}
      />
      <div
        style={{
          fontSize: 12,
          fontWeight: 600,
          marginTop: 4,
          overflow: "hidden",
          textOverflow: "ellipsis",
          whiteSpace: "nowrap",
        }}
        title={skin.name}
      >
        {skin.name || "Untitled"}
      </div>
      <div style={{ fontSize: 10, color: "var(--text-tertiary)", marginBottom: 7 }}>
        {skin.variant === "slim" ? "Slim" : "Classic"}
      </div>
      <div style={{ display: "flex", gap: 4 }}>
        <button className="accent" onClick={onWear} disabled={busy} style={{ flex: 1, fontSize: 10.5, padding: "4px 6px" }}>
          Wear
        </button>
        <button onClick={onEdit} style={{ fontSize: 10.5, padding: "4px 8px" }}>
          Edit
        </button>
        <button onClick={onDelete} title="Delete" style={{ fontSize: 10.5, padding: "4px 8px" }}>
          ×
        </button>
      </div>
    </div>
  );
}

function SkinsTab() {
  const accounts = useAccountStore((s) => s.accounts);
  const refreshAccounts = useAccountStore((s) => s.refresh);
  const [variant, setVariant] = useState("classic");
  const [skins, setSkins] = useState<api.Skin[]>([]);
  const [editing, setEditing] = useState<{
    id: string | null;
    name: string;
    variant: SkinVariant;
  } | null>(null);
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

  const refreshSkins = useCallback(async () => {
    try {
      setSkins(await api.listSkins());
    } catch {
      /* an empty library is a fine failure mode for a cosmetic list */
    }
  }, []);

  useEffect(() => {
    refreshSkins();
  }, [refreshSkins]);

  if (editing) {
    return (
      <SkinEditor
        skinId={editing.id}
        initialName={editing.name}
        initialVariant={editing.variant}
        onCancel={() => setEditing(null)}
        onSaved={async () => {
          setEditing(null);
          await refreshSkins();
        }}
      />
    );
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
    <div style={{ maxWidth: 860, display: "flex", flexDirection: "column", gap: 16 }}>
      <GlassCard>
        <div style={{ fontSize: 12.5, color: "var(--text-secondary)", lineHeight: 1.6 }}>
          Unlike capes, this is a <strong>real Mojang skin change</strong>. It applies to your
          account everywhere, not just in Blurred Client.
        </div>
      </GlassCard>

      {/* The local library. Skins made here live on disk as ordinary PNGs;
          "Wear" is the same Mojang upload as picking a file by hand, pointed at
          one of ours. */}
      <GlassCard>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 12 }}>
          <h3 style={{ margin: 0, fontSize: 14 }}>Your skins</h3>
          <button className="accent" onClick={() => setEditing({ id: null, name: "", variant: "classic" })}>
            Make a skin
          </button>
        </div>

        {skins.length === 0 ? (
          <div style={{ fontSize: 12, color: "var(--text-tertiary)", lineHeight: 1.6 }}>
            Nothing saved yet. Make one and it'll show up here, ready to wear —
            or drag an existing PNG in with "Upload a PNG" below.
          </div>
        ) : (
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fill, minmax(150px, 1fr))",
              gap: 12,
            }}
          >
            {skins.map((skin) => (
              <SkinCard
                key={skin.id}
                skin={skin}
                busy={busy}
                onWear={() =>
                  run(
                    () => api.setAccountSkinFile(accountId, skin.path, skin.variant),
                    `Wearing "${skin.name}".`
                  )
                }
                onEdit={() =>
                  setEditing({
                    id: skin.id,
                    name: skin.name,
                    variant: skin.variant === "slim" ? "slim" : "classic",
                  })
                }
                onDelete={async () => {
                  if (!confirm(`Delete skin "${skin.name}"?`)) return;
                  setSkins(await api.deleteSkin(skin.id));
                }}
              />
            ))}
          </div>
        )}
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
