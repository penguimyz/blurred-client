import { useEffect, useMemo, useRef, useState } from "react";
import { GlassCard } from "../components/GlassCard";
import { SkinViewer } from "../components/SkinViewer";
import * as api from "../lib/tauri";
import * as raster from "../lib/capeRaster";
import {
  SKIN_H,
  SKIN_W,
  skinParts,
  type SkinFace,
  type SkinPart,
  type SkinVariant,
} from "../types/skin";

/**
 * Draw a Minecraft skin, one face of one body part at a time, next to a 3D
 * preview of the whole thing.
 *
 * # Why the same paint engine as capes
 *
 * `lib/capeRaster` was generalised to work on a {@link raster.Surface} — pixels
 * plus dimensions — rather than on a fixed 10x16 cape. A skin face is another
 * rectangle, so brushes, shapes, the bucket, symmetry and the eyedropper all
 * apply here unchanged. Writing a second paint engine for the second thing that
 * needs painting would have been the obvious mistake.
 *
 * # Why one face at a time, with a 3D view beside it
 *
 * A skin sheet is a jigsaw: the back of the head is nowhere near the front, and
 * an arm's four sides are a strip in the wrong order. Editing the raw 64x64
 * atlas means constantly translating between where a pixel is and what it is —
 * which is exactly the part that makes skin editing feel like a puzzle rather
 * than like drawing.
 *
 * So the canvas is always one named face, blown up large, and the model beside
 * it says what that means. The 3D view is the feedback loop: paint the front of
 * the head, see a face appear.
 *
 * # What it is not
 *
 * The preview is a viewer, not a painting surface — you can't yet draw directly
 * onto the model. That needs a ray cast from the cursor into the box geometry
 * to find which face and texel were hit, which is a real piece of work and a
 * separate one from this.
 */

/** Screen pixels per skin pixel, before fitting to the face's size. */
const BASE_ZOOM = 26;

type Tool = "pen" | "eraser" | "fill" | "picker" | "line" | "rect" | "circle";

const TOOLS: readonly Tool[] = ["pen", "line", "rect", "circle", "fill", "eraser", "picker"];
const TOOL_LABELS: Record<Tool, string> = {
  pen: "pen",
  line: "line",
  rect: "box",
  circle: "circle",
  fill: "fill",
  eraser: "erase",
  picker: "pick",
};

const PALETTE = [
  "#000000", "#3b2a1e", "#6b4b32", "#a9784f", "#d8b08a", "#f0d3b0",
  "#ffffff", "#c8d4dc", "#8b9aa5", "#4a5a66", "#22303a", "#0b1e2a",
  "#e6b422", "#e2683c", "#c1354e", "#8e3d8e", "#3d5fbf", "#3fa34d",
];

/** A blank 64x64 sheet. */
function blankSkin(): ImageData {
  return new ImageData(SKIN_W, SKIN_H);
}

function sheetCtx(sheet: ImageData): CanvasRenderingContext2D | null {
  const canvas = document.createElement("canvas");
  canvas.width = sheet.width;
  canvas.height = sheet.height;
  const ctx = canvas.getContext("2d");
  ctx?.putImageData(sheet, 0, 0);
  return ctx;
}

function readFace(sheet: ImageData, face: SkinFace): Uint8ClampedArray {
  const ctx = sheetCtx(sheet);
  if (!ctx) return new Uint8ClampedArray(face.w * face.h * 4);
  return new Uint8ClampedArray(ctx.getImageData(face.x, face.y, face.w, face.h).data);
}

function writeFace(sheet: ImageData, face: SkinFace, data: Uint8ClampedArray): ImageData {
  const ctx = sheetCtx(sheet);
  if (!ctx) return sheet;
  ctx.putImageData(new ImageData(new Uint8ClampedArray(data), face.w, face.h), face.x, face.y);
  return ctx.getImageData(0, 0, sheet.width, sheet.height);
}

function sheetToUrl(sheet: ImageData): string {
  const ctx = sheetCtx(sheet);
  return ctx ? ctx.canvas.toDataURL("image/png") : "";
}

export function SkinEditor({
  skinId,
  initialName,
  initialVariant,
  onCancel,
  onSaved,
}: {
  skinId: string | null;
  initialName: string;
  initialVariant: SkinVariant;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [name, setName] = useState(initialName);
  const [variant, setVariant] = useState<SkinVariant>(initialVariant);
  const [sheet, setSheet] = useState<ImageData>(() => blankSkin());

  const parts = useMemo(() => skinParts(variant), [variant]);
  const [partKey, setPartKey] = useState("head");
  const [faceKey, setFaceKey] = useState("front");

  const part: SkinPart = parts.find((p) => p.key === partKey) ?? parts[0];
  const face: SkinFace = part.faces.find((f) => f.key === faceKey) ?? part.faces[0];

  const [data, setData] = useState<Uint8ClampedArray>(() => new Uint8ClampedArray(8 * 8 * 4));
  const [preview, setPreview] = useState<Uint8ClampedArray | null>(null);
  const [history, setHistory] = useState<Uint8ClampedArray[]>([]);
  const [future, setFuture] = useState<Uint8ClampedArray[]>([]);

  const [tool, setTool] = useState<Tool>("pen");
  const [color, setColor] = useState(PALETTE[3]);
  const [brushSize, setBrushSize] = useState(1);
  const [roundBrush, setRoundBrush] = useState(true);
  const [tolerance, setTolerance] = useState(0.1);
  const [showGrid, setShowGrid] = useState(true);
  const [showOverlay, setShowOverlay] = useState(true);
  const [filled, setFilled] = useState(true);

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canvasRef = useRef<HTMLCanvasElement>(null);
  const painting = useRef(false);
  const lastPoint = useRef<{ x: number; y: number } | null>(null);
  const dragStart = useRef<{ x: number; y: number } | null>(null);

  const zoom = Math.max(6, Math.round(BASE_ZOOM / Math.max(face.w, face.h) * 8));

  // Load an existing skin.
  useEffect(() => {
    if (!skinId) return;
    let cancelled = false;
    api
      .readSkin(skinId)
      .then((b64) => {
        const img = new Image();
        img.onload = () => {
          if (cancelled) return;
          const canvas = document.createElement("canvas");
          canvas.width = SKIN_W;
          canvas.height = SKIN_H;
          const ctx = canvas.getContext("2d");
          if (!ctx) return;
          ctx.imageSmoothingEnabled = false;
          // Old 64x32 skins are the top half of a 64x64 sheet; drawing at the
          // source's own size puts them where they belong and leaves the
          // second-layer rows transparent, which is exactly right.
          ctx.drawImage(img, 0, 0, img.naturalWidth, img.naturalHeight);
          setSheet(ctx.getImageData(0, 0, SKIN_W, SKIN_H));
        };
        img.src = `data:image/png;base64,${b64}`;
      })
      .catch((e) => setError(String(e)));
    return () => {
      cancelled = true;
    };
  }, [skinId]);

  // Pull the selected face out of the sheet whenever the selection changes.
  // Keyed on the face's rectangle rather than on the keys, so switching variant
  // (which moves the arm UVs) reloads too.
  useEffect(() => {
    setData(readFace(sheet, face));
    setHistory([]);
    setFuture([]);
    setPreview(null);
    // `sheet` deliberately absent: it changes on every commit, and reacting to
    // that would overwrite the face being painted with its own committed copy.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [face.x, face.y, face.w, face.h]);

  /** The sheet with the in-progress face folded in, for the 3D preview. */
  const liveSheet = useMemo(
    () => writeFace(sheet, face, preview ?? data),
    [sheet, face, data, preview]
  );
  const previewUrl = useMemo(() => sheetToUrl(liveSheet), [liveSheet]);

  const brush = useMemo<raster.Brush>(
    () => ({
      soft: false,
      size: brushSize,
      round: roundBrush,
      opacity: 1,
      color: raster.hexToRgb(color),
      erase: tool === "eraser",
    }),
    [brushSize, roundBrush, color, tool]
  );

  const surfaceOf = (buffer: Uint8ClampedArray) => raster.surfaceOf(buffer, face.w, face.h);

  // Repaint the editing canvas.
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    for (let y = 0; y < face.h; y++) {
      for (let x = 0; x < face.w; x++) {
        ctx.fillStyle = (x + y) % 2 === 0 ? "#0d2531" : "#0a1c26";
        ctx.fillRect(x * zoom, y * zoom, zoom, zoom);
      }
    }

    const off = document.createElement("canvas");
    off.width = face.w;
    off.height = face.h;
    const octx = off.getContext("2d");
    if (!octx) return;
    octx.putImageData(
      new ImageData(new Uint8ClampedArray(preview ?? data), face.w, face.h),
      0,
      0
    );
    ctx.imageSmoothingEnabled = false;
    ctx.drawImage(off, 0, 0, canvas.width, canvas.height);

    if (showGrid) {
      ctx.strokeStyle = "rgba(125,226,240,0.14)";
      ctx.lineWidth = 1;
      ctx.beginPath();
      for (let x = 1; x < face.w; x++) {
        ctx.moveTo(x * zoom + 0.5, 0);
        ctx.lineTo(x * zoom + 0.5, canvas.height);
      }
      for (let y = 1; y < face.h; y++) {
        ctx.moveTo(0, y * zoom + 0.5);
        ctx.lineTo(canvas.width, y * zoom + 0.5);
      }
      ctx.stroke();
    }

    ctx.strokeStyle = "#35e0d0";
    ctx.lineWidth = 2;
    ctx.strokeRect(1, 1, canvas.width - 2, canvas.height - 2);
  }, [data, preview, showGrid, face.w, face.h, zoom]);

  function checkpoint() {
    setHistory((h) => [...h.slice(-31), new Uint8ClampedArray(data)]);
    setFuture([]);
  }

  /** Fold the painted face back into the sheet. Every exit goes through here. */
  function commitFace(): ImageData {
    const next = writeFace(sheet, face, data);
    setSheet(next);
    return next;
  }

  function pointAt(e: React.MouseEvent<HTMLCanvasElement>) {
    const rect = e.currentTarget.getBoundingClientRect();
    return {
      x: ((e.clientX - rect.left) / rect.width) * face.w,
      y: ((e.clientY - rect.top) / rect.height) * face.h,
    };
  }

  function renderDrag(from: { x: number; y: number }, to: { x: number; y: number }) {
    const next = new Uint8ClampedArray(data);
    raster.drawShape(surfaceOf(next), from.x, from.y, to.x, to.y, {
      kind: tool === "circle" ? "ellipse" : tool === "rect" ? "rect" : "line",
      filled,
      thickness: 1,
      regular: false,
      brush,
      symmetry: raster.NO_SYMMETRY,
    });
    return next;
  }

  function onDown(e: React.MouseEvent<HTMLCanvasElement>) {
    const p = pointAt(e);

    if (tool === "picker") {
      const hex = raster.pick(surfaceOf(data), Math.floor(p.x), Math.floor(p.y));
      if (hex) setColor(hex);
      setTool("pen");
      return;
    }

    if (tool === "fill") {
      checkpoint();
      setData(
        raster.bucketFill(surfaceOf(data), Math.floor(p.x), Math.floor(p.y), raster.hexToRgb(color), {
          tolerance,
          contiguous: true,
          erase: false,
        }).data
      );
      return;
    }

    if (tool === "line" || tool === "rect" || tool === "circle") {
      dragStart.current = p;
      setPreview(renderDrag(p, p));
      return;
    }

    checkpoint();
    painting.current = true;
    lastPoint.current = p;
    const next = new Uint8ClampedArray(data);
    raster.stamp(surfaceOf(next), p.x, p.y, brush);
    setData(next);
  }

  function onMove(e: React.MouseEvent<HTMLCanvasElement>) {
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
      raster.stroke(surfaceOf(next), from.x, from.y, p.x, p.y, brush);
      return next;
    });
  }

  function endStroke() {
    if (dragStart.current) {
      dragStart.current = null;
      if (preview) {
        checkpoint();
        setData(preview);
      }
      setPreview(null);
    }
    painting.current = false;
    lastPoint.current = null;
  }

  const endRef = useRef(endStroke);
  endRef.current = endStroke;
  useEffect(() => {
    const up = () => endRef.current();
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

  const undoRef = useRef(undo);
  const redoRef = useRef(redo);
  undoRef.current = undo;
  redoRef.current = redo;
  const beforeSpace = useRef<Tool | null>(null);

  useEffect(() => {
    function typing(e: KeyboardEvent) {
      const t = e.target as HTMLElement | null;
      return !!t && (t.tagName === "INPUT" || t.tagName === "TEXTAREA");
    }
    function onKey(e: KeyboardEvent) {
      if (typing(e)) return;
      if (e.code === "Space") {
        e.preventDefault();
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
      if (previous !== null) setTool((c) => (c === "picker" ? previous : c));
    }
    window.addEventListener("keydown", onKey);
    window.addEventListener("keyup", onKeyUp);
    return () => {
      window.removeEventListener("keydown", onKey);
      window.removeEventListener("keyup", onKeyUp);
    };
  }, []);

  async function save() {
    setBusy(true);
    setError(null);
    try {
      const finished = commitFace();
      const ctx = sheetCtx(finished);
      if (!ctx) throw new Error("could not get a canvas context");
      const b64 = ctx.canvas.toDataURL("image/png").split(",")[1];

      // Editing keeps the id, so an account already pointed at this skin's file
      // keeps working and the library doesn't grow a copy on every save.
      if (skinId) await api.updateSkin(skinId, variant, b64);
      else await api.saveSkin(name.trim() || "Untitled", variant, b64);
      onSaved();
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ maxWidth: 1100 }}>
      <div style={{ display: "flex", gap: 10, alignItems: "center", marginBottom: 14, flexWrap: "wrap" }}>
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Skin name"
          style={{ padding: "8px 10px", fontSize: 13, width: 200 }}
        />
        <button className="accent" onClick={save} disabled={busy}>
          {busy ? "Saving…" : "Save skin"}
        </button>
        <button onClick={onCancel} disabled={busy}>Cancel</button>
        <button onClick={undo} disabled={history.length === 0} title="Ctrl+Z">Undo</button>
        <button onClick={redo} disabled={future.length === 0} title="Ctrl+Y">Redo</button>

        <span style={{ width: 10 }} />
        {(["classic", "slim"] as const).map((v) => (
          <button
            key={v}
            onClick={() => {
              // Commit first: switching variant moves the arm rectangles, and
              // the face on the canvas belongs to the old ones.
              commitFace();
              setVariant(v);
            }}
            title={v === "classic" ? "4px arms" : "3px arms"}
            style={{
              fontSize: 11,
              padding: "5px 12px",
              background: variant === v ? "var(--accent)" : undefined,
              color: variant === v ? "var(--accent-fg)" : undefined,
            }}
          >
            {v === "classic" ? "Classic" : "Slim"}
          </button>
        ))}
      </div>

      {error && <div style={{ color: "var(--danger)", fontSize: 12, marginBottom: 12 }}>{error}</div>}

      {/* Part, then face within it. Two rows rather than seventy-two buttons. */}
      <div style={{ display: "flex", gap: 5, marginBottom: 8, flexWrap: "wrap" }}>
        {parts.map((p) => (
          <button
            key={p.key}
            onClick={() => {
              commitFace();
              setPartKey(p.key);
            }}
            style={{
              fontSize: 10.5,
              padding: "5px 10px",
              background: partKey === p.key ? "var(--accent)" : undefined,
              color: partKey === p.key ? "var(--accent-fg)" : undefined,
            }}
          >
            {p.label}
          </button>
        ))}
      </div>
      <div style={{ display: "flex", gap: 5, marginBottom: 14, flexWrap: "wrap" }}>
        {part.faces.map((f) => (
          <button
            key={f.key}
            onClick={() => {
              commitFace();
              setFaceKey(f.key);
            }}
            style={{
              fontSize: 10.5,
              padding: "4px 10px",
              background: faceKey === f.key ? "var(--accent)" : undefined,
              color: faceKey === f.key ? "var(--accent-fg)" : undefined,
            }}
          >
            {f.label}
          </button>
        ))}
        <span style={{ fontSize: 10.5, color: "var(--text-tertiary)", alignSelf: "center", marginLeft: 6 }}>
          {part.label} · {face.label} · {face.w}×{face.h}
          {part.overlay ? " · overlay layer" : ""}
        </span>
      </div>

      <div style={{ display: "flex", gap: 16, alignItems: "flex-start", flexWrap: "wrap" }}>
        <GlassCard style={{ padding: 12 }}>
          <canvas
            ref={canvasRef}
            width={face.w * zoom}
            height={face.h * zoom}
            onMouseDown={onDown}
            onMouseMove={onMove}
            style={{
              imageRendering: "pixelated",
              cursor: tool === "picker" ? "copy" : "crosshair",
              display: "block",
              touchAction: "none",
            }}
          />
          <div style={{ fontSize: 10.5, color: "var(--text-tertiary)", marginTop: 8, maxWidth: 320, lineHeight: 1.5 }}>
            {part.overlay
              ? "The outer layer. Leave a pixel transparent and the layer underneath shows through — that's how hair and jackets work."
              : "The base layer. Every pixel here is solid on the model."}
          </div>
        </GlassCard>

        <GlassCard style={{ padding: 12, width: 240 }}>
          <Label>Preview</Label>
          <SkinViewer src={previewUrl} variant={variant} unit={5} showOverlay={showOverlay} />
          <label style={checkRow}>
            <input
              type="checkbox"
              checked={showOverlay}
              onChange={(e) => setShowOverlay(e.target.checked)}
            />
            <span style={{ fontSize: 11 }}>Show outer layer</span>
          </label>
          <div style={{ fontSize: 10, color: "var(--text-tertiary)", lineHeight: 1.5 }}>
            Drag the model to turn it.
          </div>
        </GlassCard>

        <GlassCard style={{ padding: 12, width: 200 }}>
          <Label>Tool</Label>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 4, marginBottom: 12 }}>
            {TOOLS.map((t) => (
              <button
                key={t}
                onClick={() => setTool(t)}
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

          {(tool === "pen" || tool === "eraser" || tool === "line") && (
            <>
              <Label>Brush · {brushSize}px</Label>
              <input
                type="range"
                min={1}
                max={6}
                step={1}
                value={brushSize}
                onChange={(e) => setBrushSize(Number(e.target.value))}
                style={slider}
              />
              <div style={{ display: "flex", gap: 5, marginBottom: 12 }}>
                {([true, false] as const).map((r) => (
                  <button
                    key={String(r)}
                    onClick={() => setRoundBrush(r)}
                    style={{
                      flex: 1,
                      fontSize: 10.5,
                      padding: "4px 2px",
                      background: roundBrush === r ? "var(--accent)" : undefined,
                      color: roundBrush === r ? "var(--accent-fg)" : undefined,
                    }}
                  >
                    {r ? "Round" : "Square"}
                  </button>
                ))}
              </div>
            </>
          )}

          {(tool === "rect" || tool === "circle") && (
            <div style={{ display: "flex", gap: 5, marginBottom: 12 }}>
              {([true, false] as const).map((f) => (
                <button
                  key={String(f)}
                  onClick={() => setFilled(f)}
                  style={{
                    flex: 1,
                    fontSize: 10.5,
                    padding: "4px 2px",
                    background: filled === f ? "var(--accent)" : undefined,
                    color: filled === f ? "var(--accent-fg)" : undefined,
                  }}
                >
                  {f ? "Solid" : "Outline"}
                </button>
              ))}
            </div>
          )}

          {tool === "fill" && (
            <>
              <Label>Tolerance · {Math.round(tolerance * 100)}%</Label>
              <input
                type="range"
                min={0}
                max={100}
                step={1}
                value={Math.round(tolerance * 100)}
                onChange={(e) => setTolerance(Number(e.target.value) / 100)}
                style={slider}
              />
            </>
          )}

          <label style={checkRow}>
            <input type="checkbox" checked={showGrid} onChange={(e) => setShowGrid(e.target.checked)} />
            <span style={{ fontSize: 11 }}>Show grid</span>
          </label>

          <Label>Colour</Label>
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
          <div style={{ fontSize: 10, color: "var(--text-tertiary)", marginTop: 8, lineHeight: 1.5 }}>
            Hold space for the eyedropper.
          </div>
        </GlassCard>
      </div>
    </div>
  );
}

function Label({ children }: { children: React.ReactNode }) {
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

const slider: React.CSSProperties = { width: "100%", marginBottom: 12, accentColor: "var(--accent)" };
const checkRow: React.CSSProperties = {
  display: "flex",
  gap: 7,
  alignItems: "center",
  cursor: "pointer",
  marginBottom: 12,
};
