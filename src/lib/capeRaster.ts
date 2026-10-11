import { CAPE_AREA } from "../types/cape";

/**
 * The paint engine behind the cape editor.
 *
 * # Why RGBA and not a list of hex strings
 *
 * The editor used to hold pixels as a `string[]` of CSS colours, which is fine
 * for hard single-pixel painting and useless for anything else: there is no
 * partial coverage, so there is no soft brush, no opacity, and no way for the
 * bucket to have a tolerance. Everything here works on a straight RGBA byte
 * buffer instead, which makes all three fall out naturally.
 *
 * # Why nothing here knows how big a cape is
 *
 * Every function takes a {@link Surface} — pixels plus the shape they're in —
 * rather than assuming the 10x16 of {@link CAPE_AREA}. That started as an
 * assumption baked into module constants, and it was wrong twice over:
 *
 *  - A cape is a *box*, so it has six faces to paint, and only one of them is
 *    10x16. The side edges are 1x16 and the caps are 10x1.
 *  - The sheet doesn't have to be 64x32. Cape UVs are fractions of the texture,
 *    so a 128x64 sheet is the same cape at twice the resolution — which is the
 *    only way to get a canvas you can actually draw detail on.
 *
 * Both fall out of the same fix: stop hardcoding the size, take it from the
 * surface being painted.
 */

/** The default cape face, at 1x. Kept for previews of a standard sheet. */
export const CAPE_W = CAPE_AREA.w;
export const CAPE_H = CAPE_AREA.h;

/**
 * A paintable rectangle: RGBA bytes, and the shape they're arranged in.
 *
 * `data` is row-major, four bytes per pixel, exactly `w * h * 4` long.
 */
export interface Surface {
  data: Uint8ClampedArray;
  w: number;
  h: number;
}

export type Rgb = readonly [number, number, number];

/** A fresh, fully transparent surface. */
export function blankSurface(w: number, h: number): Surface {
  return { data: new Uint8ClampedArray(w * h * 4), w, h };
}

/** A surface wrapping an existing buffer, without copying it. */
export function surfaceOf(data: Uint8ClampedArray, w: number, h: number): Surface {
  return { data, w, h };
}

/** An independent copy, so an edit can't be seen through the original. */
export function cloneSurface(s: Surface): Surface {
  return { data: new Uint8ClampedArray(s.data), w: s.w, h: s.h };
}

export function hexToRgb(hex: string): Rgb {
  const h = hex.replace("#", "").trim();
  const full =
    h.length === 3
      ? h
          .split("")
          .map((c) => c + c)
          .join("")
      : h.padEnd(6, "0").slice(0, 6);
  return [
    Number.parseInt(full.slice(0, 2), 16) || 0,
    Number.parseInt(full.slice(2, 4), 16) || 0,
    Number.parseInt(full.slice(4, 6), 16) || 0,
  ];
}

export function rgbToHex([r, g, b]: Rgb): string {
  return `#${[r, g, b].map((v) => Math.round(v).toString(16).padStart(2, "0")).join("")}`;
}

/**
 * Standard source-over composite of one colour onto one pixel.
 *
 * Stored un-premultiplied, because that's what `ImageData` and PNG both want;
 * the premultiply/divide round trip happens here so callers never see it.
 */
function blendPixel(buf: Uint8ClampedArray, index: number, [r, g, b]: Rgb, alpha: number): void {
  if (alpha <= 0) return;
  const o = index * 4;
  const dstA = buf[o + 3] / 255;
  const outA = alpha + dstA * (1 - alpha);
  if (outA <= 0) {
    buf[o] = buf[o + 1] = buf[o + 2] = buf[o + 3] = 0;
    return;
  }
  buf[o] = (r * alpha + buf[o] * dstA * (1 - alpha)) / outA;
  buf[o + 1] = (g * alpha + buf[o + 1] * dstA * (1 - alpha)) / outA;
  buf[o + 2] = (b * alpha + buf[o + 2] * dstA * (1 - alpha)) / outA;
  buf[o + 3] = outA * 255;
}

/** Take `alpha` worth of opacity away from a pixel. */
function erasePixel(buf: Uint8ClampedArray, index: number, alpha: number): void {
  if (alpha <= 0) return;
  const o = index * 4;
  const next = buf[o + 3] * (1 - alpha);
  buf[o + 3] = next;
  if (next < 1) {
    // Fully clear rather than leaving stale RGB behind a zero alpha: the PNG
    // encoder keeps those channels, and they show up again the moment someone
    // paints over the pixel with a partial alpha.
    buf[o] = buf[o + 1] = buf[o + 2] = 0;
  }
}

export interface Brush {
  /** Hard, whole-pixel edges, or a soft falloff. */
  soft: boolean;
  /** Diameter in cape pixels. Whole pixels in hard mode. */
  size: number;
  /**
   * Round footprint rather than a square block.
   *
   * Only affects hard painting — a soft brush is radial by construction. See
   * {@link stampHard} for why this is the better default.
   */
  round: boolean;
  /** 0..1. Only meaningful for a soft brush; hard painting is always opaque. */
  opacity: number;
  color: Rgb;
  erase: boolean;
}

/**
 * Which pixels a hard brush of this size covers, as offsets from its centre.
 *
 * Exported so the UI can draw the footprint rather than describe it: "3px
 * round" means nothing until you can see that it's five pixels in a plus, and
 * on a canvas ten pixels wide the difference between five and nine is half the
 * width of the cape.
 */
export function brushFootprint(size: number, round: boolean): Array<{ dx: number; dy: number }> {
  const n = Math.max(1, Math.round(size));
  const half = Math.floor((n - 1) / 2);
  // Centre of the block in pixel-centre coordinates. For an odd size this lands
  // on a pixel centre and the offsets below come out whole; for an even size it
  // lands on a boundary and they come out on halves. Both are what we want.
  const centre = half + n / 2;

  // Radius chosen so the shapes match what a pixel artist expects: 3 gives the
  // five-pixel plus, 5 gives the thirteen-pixel circle, 4 gives a 4x4 with the
  // corners off. Using n/2 for odd sizes instead would keep every corner and
  // give back the square.
  const r = Math.floor(n / 2);

  const cells: Array<{ dx: number; dy: number }> = [];
  for (let y = 0; y < n; y++) {
    for (let x = 0; x < n; x++) {
      if (round) {
        const ox = x + 0.5 - centre;
        const oy = y + 0.5 - centre;
        if (ox * ox + oy * oy > r * r) continue;
      }
      cells.push({ dx: x - half, dy: y - half });
    }
  }
  return cells;
}

/**
 * Lay one brush stamp down at a point, in cape-pixel coordinates.
 *
 * The two modes are genuinely different operations rather than one with a
 * parameter:
 *
 * - **Pixel art** paints whole, fully-opaque pixels — a disc by default, see
 *   {@link stampHard}. No coverage maths, so an edge drawn twice is identical
 *   to an edge drawn once and nothing ever ends up at 97% alpha where it should
 *   be flat.
 * - **Smooth** weights each pixel by how far it is from the brush centre and
 *   composites, so strokes build up and blend. On a canvas this small that
 *   reads as airbrushing rather than as blur.
 */
export function stamp(dst: Surface, cx: number, cy: number, brush: Brush): void {
  if (brush.soft) {
    stampSoft(dst, cx, cy, brush);
  } else {
    stampHard(dst, cx, cy, brush);
  }
}

/**
 * Paint whole pixels in the brush's footprint.
 *
 * A round footprint is the default and a square one is the option, which is the
 * opposite of how this started. A square block is what falls out of a naive
 * `for y, for x` and it is the wrong shape for a brush: at size 3 it puts down
 * nine pixels when a round brush puts down five, and on a cape ten pixels wide
 * those four corners are the difference between a stroke and a smear. The
 * corners are also the pixels you least meant to paint — they're the furthest
 * from where you clicked.
 *
 * Square stays available because a hard block genuinely is what you want for
 * filling a rectangular area by hand.
 */
function stampHard(dst: Surface, cx: number, cy: number, brush: Brush): void {
  // Anchor so the clicked pixel is in the footprint and odd sizes are centred.
  const ax = Math.floor(cx);
  const ay = Math.floor(cy);

  for (const cell of brushFootprint(brush.size, brush.round)) {
    const x = ax + cell.dx;
    const y = ay + cell.dy;
    if (x < 0 || y < 0 || x >= dst.w || y >= dst.h) continue;

    const i = y * dst.w + x;
    if (brush.erase) {
      erasePixel(dst.data, i, 1);
    } else {
      const o = i * 4;
      dst.data[o] = brush.color[0];
      dst.data[o + 1] = brush.color[1];
      dst.data[o + 2] = brush.color[2];
      dst.data[o + 3] = 255;
    }
  }
}

function stampSoft(dst: Surface, cx: number, cy: number, brush: Brush): void {
  // At size 1 there is no room for a falloff: the brush is one pixel wide, so
  // snap to that pixel's centre and let it take the full coverage. Left
  // un-snapped, a click near a pixel boundary splits its weight across two or
  // four neighbours and the "1px" brush lays down a faint smudge instead of a
  // pixel — which is not what the smallest possible brush should do.
  if (brush.size <= 1) {
    cx = Math.floor(cx) + 0.5;
    cy = Math.floor(cy) + 0.5;
  }

  const radius = Math.max(0.5, brush.size / 2);
  const x0 = Math.floor(cx - radius - 1);
  const x1 = Math.ceil(cx + radius + 1);
  const y0 = Math.floor(cy - radius - 1);
  const y1 = Math.ceil(cy + radius + 1);

  for (let y = y0; y <= y1; y++) {
    if (y < 0 || y >= dst.h) continue;
    for (let x = x0; x <= x1; x++) {
      if (x < 0 || x >= dst.w) continue;

      // Distance from the brush centre to the *centre* of this pixel.
      const dx = x + 0.5 - cx;
      const dy = y + 0.5 - cy;
      const d = Math.hypot(dx, dy);

      let t = 1 - d / radius;
      if (t <= 0) continue;
      if (t > 1) t = 1;
      // Smoothstep, so the edge of the brush rolls off instead of ending in a
      // visible cone.
      const coverage = t * t * (3 - 2 * t) * brush.opacity;

      const i = y * dst.w + x;
      if (brush.erase) {
        erasePixel(dst.data, i, coverage);
      } else {
        blendPixel(dst.data, i, brush.color, coverage);
      }
    }
  }
}

/**
 * Stamp along a segment, so a fast drag doesn't leave a dotted line.
 *
 * Steps at half a pixel, which is dense enough that a soft brush accumulates
 * evenly and cheap enough not to matter on a 10x16 canvas.
 */
export function stroke(
  dst: Surface,
  fromX: number,
  fromY: number,
  toX: number,
  toY: number,
  brush: Brush
): void {
  const steps = Math.max(1, Math.ceil(Math.hypot(toX - fromX, toY - fromY) * 2));
  for (let s = 1; s <= steps; s++) {
    const t = s / steps;
    stamp(dst, fromX + (toX - fromX) * t, fromY + (toY - fromY) * t, brush);
  }
}

// ---------------------------------------------------------------------------
// Importing a picture
// ---------------------------------------------------------------------------

/**
 * How to get an image of some other shape onto a 10x16 cape.
 *
 * Almost nothing is natively cape-shaped — a logo is square, a screenshot is
 * 16:9, a cape is a tall 5:8 — so "just scale it" always means choosing what to
 * do about the mismatch, and the three answers here are genuinely different
 * pictures rather than settings on one.
 */
export type FitMode =
  /** Whole image visible, letterboxed. Nothing is lost, nothing is cropped. */
  | "contain"
  /** Fill the cape, overflow cropped. No bars, but the edges go. */
  | "cover"
  /** Ignore the aspect ratio entirely and squash to fit. */
  | "stretch";

export interface ImportOptions {
  fit: FitMode;
  /**
   * Nearest-neighbour rather than smooth downscaling.
   *
   * On by default and it matters more here than almost anywhere: a cape is ten
   * pixels wide, so bilinear downscaling of a detailed image averages huge
   * areas into each pixel and hands back a grey rectangle. Nearest keeps a
   * pixel-art source crisp and, for a photo, at least gives you colours you can
   * recognise. Off is worth trying for a smooth gradient or a soft logo.
   */
  pixelated: boolean;
  /** 0..1 scale within the fitted box, for pulling a design in from the edges. */
  zoom: number;
  /** -1..1 nudge within the cape, as a fraction of the cape's size. */
  offsetX: number;
  offsetY: number;
  /** Replace what's there, or composite the image over it. */
  replace: boolean;
  /** 0..1. */
  opacity: number;
}

export const DEFAULT_IMPORT: ImportOptions = {
  fit: "contain",
  pixelated: true,
  zoom: 1,
  offsetX: 0,
  offsetY: 0,
  replace: true,
  opacity: 1,
};

/**
 * Draw an already-loaded image onto the cape.
 *
 * Scaling is done by the canvas rather than by hand: it is the one piece of
 * image resampling in this file that isn't ours, and reimplementing a box
 * filter to avoid a `drawImage` would be pure vanity. The buffer comes back out
 * through `getImageData`, so everything downstream still sees plain RGBA.
 *
 * @returns a new buffer; `buf` is left alone
 */
export function drawImageOntoCape(
  dst: Surface,
  image: CanvasImageSource,
  sourceW: number,
  sourceH: number,
  opts: ImportOptions
): Surface {
  const canvas = document.createElement("canvas");
  canvas.width = dst.w;
  canvas.height = dst.h;
  const ctx = canvas.getContext("2d");
  if (!ctx || sourceW <= 0 || sourceH <= 0) return dst;

  // Start from what's already painted unless we're replacing it, so "over"
  // composites against the real cape rather than against nothing.
  if (!opts.replace) {
    ctx.putImageData(new ImageData(new Uint8ClampedArray(dst.data), dst.w, dst.h), 0, 0);
  }

  ctx.imageSmoothingEnabled = !opts.pixelated;
  ctx.imageSmoothingQuality = "high";
  ctx.globalAlpha = Math.max(0, Math.min(1, opts.opacity));

  let w: number;
  let h: number;
  if (opts.fit === "stretch") {
    w = dst.w;
    h = dst.h;
  } else {
    // contain takes the smaller ratio so everything fits; cover takes the
    // larger so nothing is left blank. That one comparison is the whole
    // difference between the two modes.
    const ratio =
      opts.fit === "cover"
        ? Math.max(dst.w / sourceW, dst.h / sourceH)
        : Math.min(dst.w / sourceW, dst.h / sourceH);
    w = sourceW * ratio;
    h = sourceH * ratio;
  }

  const zoom = Math.max(0.05, opts.zoom);
  w *= zoom;
  h *= zoom;

  const x = (dst.w - w) / 2 + opts.offsetX * dst.w;
  const y = (dst.h - h) / 2 + opts.offsetY * dst.h;

  ctx.drawImage(image, x, y, w, h);

  return {
    data: new Uint8ClampedArray(ctx.getImageData(0, 0, dst.w, dst.h).data),
    w: dst.w,
    h: dst.h,
  };
}

// ---------------------------------------------------------------------------
// Symmetry
// ---------------------------------------------------------------------------

/**
 * Mirror painting across the cape's centre lines.
 *
 * Worth having as a first-class thing rather than as a filter you apply
 * afterwards: most capes are symmetric — a crest, an emblem, a stripe — and
 * hand-mirroring a 10-pixel-wide design is both tedious and easy to get one
 * pixel wrong on. With this on, half the work draws the whole cape.
 */
export interface Symmetry {
  x: boolean;
  y: boolean;
}

export const NO_SYMMETRY: Symmetry = { x: false, y: false };

/**
 * Every point a paint operation should hit, given the symmetry setting.
 *
 * Coordinates here are continuous, not pixel indices, so the reflection is
 * `w - x` rather than `w - 1 - x`: the centre of pixel 0 is at 0.5, and its
 * mirror is the centre of the last pixel at `w - 0.5`. Using the index form
 * would shift every mirrored stroke half a pixel off, which on a canvas this
 * narrow is a visibly crooked cape.
 */
export function mirrored(
  dst: Surface,
  x: number,
  y: number,
  sym: Symmetry
): Array<{ x: number; y: number }> {
  const points = [{ x, y }];
  if (sym.x) points.push({ x: dst.w - x, y });
  if (sym.y) points.push({ x, y: dst.h - y });
  if (sym.x && sym.y) points.push({ x: dst.w - x, y: dst.h - y });
  return points;
}

export function stampSym(
  dst: Surface,
  x: number,
  y: number,
  brush: Brush,
  sym: Symmetry
): void {
  for (const p of mirrored(dst, x, y, sym)) stamp(dst, p.x, p.y, brush);
}

export function strokeSym(
  dst: Surface,
  fromX: number,
  fromY: number,
  toX: number,
  toY: number,
  brush: Brush,
  sym: Symmetry
): void {
  for (const s of mirroredSegments(dst, fromX, fromY, toX, toY, sym)) {
    stroke(dst, s.ax, s.ay, s.bx, s.by, brush);
  }
}

/**
 * Each mirrored copy of a dragged segment, ends kept together.
 *
 * `mirrored` is applied to both endpoints and the results zipped, which matters:
 * the reflections come back in a fixed order, so index *i* of one end always
 * belongs with index *i* of the other. Reflecting the two ends independently
 * and pairing them wrongly is how a mirrored diagonal turns into an X.
 */
function mirroredSegments(
  dst: Surface,
  x0: number,
  y0: number,
  x1: number,
  y1: number,
  sym: Symmetry
): Array<{ ax: number; ay: number; bx: number; by: number }> {
  const to = mirrored(dst, x1, y1, sym);
  return mirrored(dst, x0, y0, sym).map((from, i) => ({
    ax: from.x,
    ay: from.y,
    bx: to[i].x,
    by: to[i].y,
  }));
}

// ---------------------------------------------------------------------------
// Shapes
// ---------------------------------------------------------------------------

/**
 * The shapes you can drag out.
 *
 * `ellipse` is the one that earns its place hardest. A circle is the single
 * hardest thing to draw freehand on a pixel grid — every attempt comes out
 * lopsided, and the correct 5-pixel circle is not something anyone derives by
 * eye — yet round emblems, rings and dots are most of what people put on a
 * cape. Rasterising it from the equation means the shape is right every time
 * and stays right at any size.
 */
export type ShapeKind = "line" | "rect" | "ellipse";

export interface ShapeOptions {
  kind: ShapeKind;
  /** Solid, or just the border. */
  filled: boolean;
  /** Border thickness in pixels, for the unfilled forms. */
  thickness: number;
  /** Force equal radii / equal sides, from the drag's shorter axis. */
  regular: boolean;
  brush: Brush;
  symmetry: Symmetry;
}

/**
 * Draw a shape between two dragged points, in continuous cape coordinates.
 *
 * Shapes composite at the brush's opacity but always with hard pixel edges,
 * including in smooth mode. Anti-aliasing a 10-pixel-wide circle doesn't make
 * it look rounder, it makes it look grubby — there aren't enough pixels for the
 * eye to reconstruct a smooth curve from a blurred one.
 */
export function drawShape(
  dst: Surface,
  x0: number,
  y0: number,
  x1: number,
  y1: number,
  opts: ShapeOptions
): void {
  if (opts.kind === "line") {
    // A line is a stroke, which already handles brush size and softness.
    strokeSym(dst, x0, y0, x1, y1, opts.brush, opts.symmetry);
    // strokeSym starts one step in, so stamp the origin too — otherwise a click
    // with no drag leaves nothing behind.
    stampSym(dst, x0, y0, opts.brush, opts.symmetry);
    return;
  }

  const alpha = opts.brush.soft ? opts.brush.opacity : 1;
  for (const s of mirroredSegments(dst, x0, y0, x1, y1, opts.symmetry)) {
    box(dst, s.ax, s.ay, s.bx, s.by, opts, alpha);
  }
}

/** One shape instance, already reflected into place. */
function box(
  dst: Surface,
  ax: number,
  ay: number,
  bx: number,
  by: number,
  opts: ShapeOptions,
  alpha: number
): void {
  let left = Math.floor(Math.min(ax, bx));
  let top = Math.floor(Math.min(ay, by));
  let right = Math.floor(Math.max(ax, bx));
  let bottom = Math.floor(Math.max(ay, by));

  if (opts.regular) {
    // Square off from the anchor corner, using the shorter drag so the shape
    // stays inside the gesture rather than shooting past the cursor.
    const side = Math.min(right - left, bottom - top);
    if (ax <= bx) right = left + side;
    else left = right - side;
    if (ay <= by) bottom = top + side;
    else top = bottom - side;
  }

  const w = right - left + 1;
  const h = bottom - top + 1;
  const t = Math.max(1, Math.round(opts.thickness));

  const paint = (x: number, y: number) => {
    if (x < 0 || y < 0 || x >= dst.w || y >= dst.h) return;
    const i = y * dst.w + x;
    if (opts.brush.erase) {
      erasePixel(dst.data, i, alpha);
    } else if (alpha >= 1) {
      // Opaque paint replaces rather than blends, so a shape drawn over a
      // half-transparent one doesn't inherit its ghost.
      const o = i * 4;
      dst.data[o] = opts.brush.color[0];
      dst.data[o + 1] = opts.brush.color[1];
      dst.data[o + 2] = opts.brush.color[2];
      dst.data[o + 3] = 255;
    } else {
      blendPixel(dst.data, i, opts.brush.color, alpha);
    }
  };

  if (opts.kind === "rect") {
    for (let y = top; y <= bottom; y++) {
      for (let x = left; x <= right; x++) {
        const border =
          x < left + t || x > right - t || y < top + t || y > bottom - t;
        if (opts.filled || border) paint(x, y);
      }
    }
    return;
  }

  // Ellipse, from the equation. Testing each pixel's *centre* against the
  // normalised radius is what makes a 3px circle come out as the plus-shape
  // everyone draws by hand, and a 9px one come out actually round.
  const cx = left + w / 2;
  const cy = top + h / 2;
  const rx = w / 2;
  const ry = h / 2;
  // The hole left when this is an outline. Guarded at zero: on a shape thinner
  // than the border, "outline" and "filled" are the same thing.
  const ix = Math.max(0, rx - t);
  const iy = Math.max(0, ry - t);

  for (let y = top; y <= bottom; y++) {
    for (let x = left; x <= right; x++) {
      const dx = x + 0.5 - cx;
      const dy = y + 0.5 - cy;
      if ((dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) > 1) continue;
      if (!opts.filled && ix > 0 && iy > 0 && (dx * dx) / (ix * ix) + (dy * dy) / (iy * iy) <= 1) {
        continue;
      }
      paint(x, y);
    }
  }
}

// ---------------------------------------------------------------------------
// Gradient
// ---------------------------------------------------------------------------

export interface GradientOptions {
  from: Rgb;
  to: Rgb;
  /** 0..1 opacity of the whole ramp. */
  opacity: number;
  /**
   * Restrict the ramp to pixels that already have paint on them.
   *
   * This is the mode that makes a gradient useful on a cape rather than just
   * pretty: it shades an emblem you've already drawn without spilling the ramp
   * across the transparent background around it.
   */
  maskToPainted: boolean;
  symmetry: Symmetry;
}

/**
 * Lay a linear colour ramp along a dragged vector.
 *
 * Each pixel is projected onto the drag; the projection's position along it,
 * clamped to the ends, is the mix. That's the standard formulation and it means
 * the *direction* of the drag sets the gradient's angle — dragging top-to-bottom
 * gives a horizon, corner-to-corner gives a diagonal sheen.
 */
export function gradient(
  dst: Surface,
  x0: number,
  y0: number,
  x1: number,
  y1: number,
  opts: GradientOptions
): void {
  for (const s of mirroredSegments(dst, x0, y0, x1, y1, opts.symmetry)) {
    const dx = s.bx - s.ax;
    const dy = s.by - s.ay;
    const lengthSq = dx * dx + dy * dy;

    for (let y = 0; y < dst.h; y++) {
      for (let x = 0; x < dst.w; x++) {
        const i = y * dst.w + x;
        if (opts.maskToPainted && dst.data[i * 4 + 3] < 8) continue;

        // A zero-length drag is a single click: treat the whole area as the
        // start colour rather than dividing by zero.
        const t =
          lengthSq === 0
            ? 0
            : Math.min(
                1,
                Math.max(0, ((x + 0.5 - s.ax) * dx + (y + 0.5 - s.ay) * dy) / lengthSq)
              );

        blendPixel(
          dst.data,
          i,
          [
            opts.from[0] + (opts.to[0] - opts.from[0]) * t,
            opts.from[1] + (opts.to[1] - opts.from[1]) * t,
            opts.from[2] + (opts.to[2] - opts.from[2]) * t,
          ],
          opts.opacity
        );
      }
    }
  }
}

/** The colour under a point, or null where the cape is transparent. */
export function pick(dst: Surface, x: number, y: number): string | null {
  if (x < 0 || y < 0 || x >= dst.w || y >= dst.h) return null;
  const o = (y * dst.w + x) * 4;
  if (dst.data[o + 3] < 8) return null;
  return rgbToHex([dst.data[o], dst.data[o + 1], dst.data[o + 2]]);
}

/**
 * How different two pixels are, 0..510.
 *
 * Compares premultiplied colour plus alpha, so two transparent pixels match
 * regardless of the RGB bytes sitting unused underneath them — otherwise
 * filling a blank cape behaves differently depending on what used to be
 * painted there, which is baffling to use.
 */
function distance(buf: Uint8ClampedArray, index: number, target: Uint8ClampedArray): number {
  const o = index * 4;
  const a = buf[o + 3] / 255;
  const ta = target[3] / 255;
  const dr = buf[o] * a - target[0] * ta;
  const dg = buf[o + 1] * a - target[1] * ta;
  const db = buf[o + 2] * a - target[2] * ta;
  const da = buf[o + 3] - target[3];
  return Math.sqrt(dr * dr + dg * dg + db * db + da * da);
}

export interface FillOptions {
  /** 0..1. 0 fills only exactly-matching pixels; 1 fills the whole cape. */
  tolerance: number;
  /** Fill the connected region only, or every matching pixel on the cape. */
  contiguous: boolean;
  erase: boolean;
}

/**
 * The paint bucket.
 *
 * Three things the old one didn't do, all of which are the difference between
 * a bucket that works on real art and one that only works on flat blocks:
 *
 *  - **Tolerance.** Anti-aliased or soft-brushed edges are never two identical
 *    colours, so an exact-match fill stops dead at the first shaded pixel and
 *    leaves a fringe.
 *  - **Global fill.** Recolouring every pixel of one shade across the whole
 *    cape, not just the blob you happened to click.
 *  - **Erasing.** Bucket-clearing a region instead of scrubbing it out by hand.
 */
export function bucketFill(
  dst: Surface,
  startX: number,
  startY: number,
  color: Rgb,
  { tolerance, contiguous, erase }: FillOptions
): Surface {
  if (startX < 0 || startY < 0 || startX >= dst.w || startY >= dst.h) return dst;

  const start = startY * dst.w + startX;
  const target = dst.data.slice(start * 4, start * 4 + 4);
  // 510 is the largest possible distance, so this maps the slider onto the
  // full useful range with 1.0 meaning "everything".
  const limit = tolerance * 510;
  const pixelCount = dst.w * dst.h;

  const next = new Uint8ClampedArray(dst.data);

  const apply = (i: number) => {
    const o = i * 4;
    if (erase) {
      next[o] = next[o + 1] = next[o + 2] = next[o + 3] = 0;
    } else {
      next[o] = color[0];
      next[o + 1] = color[1];
      next[o + 2] = color[2];
      next[o + 3] = 255;
    }
  };

  if (!contiguous) {
    for (let i = 0; i < pixelCount; i++) {
      if (distance(dst.data, i, target) <= limit) apply(i);
    }
    return { data: next, w: dst.w, h: dst.h };
  }

  // Iterative flood, because even 160 cells is no reason to risk the stack —
  // and `visited` is what stops a tolerant fill from revisiting pixels it has
  // already recoloured (whose distance to the target has since changed).
  const visited = new Uint8Array(pixelCount);
  const stack = [start];
  visited[start] = 1;

  while (stack.length) {
    const i = stack.pop()!;
    if (distance(dst.data, i, target) > limit) continue;
    apply(i);

    const x = i % dst.w;
    const y = (i - x) / dst.w;
    if (x > 0 && !visited[i - 1]) (visited[i - 1] = 1), stack.push(i - 1);
    if (x < dst.w - 1 && !visited[i + 1]) (visited[i + 1] = 1), stack.push(i + 1);
    if (y > 0 && !visited[i - dst.w]) (visited[i - dst.w] = 1), stack.push(i - dst.w);
    if (y < dst.h - 1 && !visited[i + dst.w]) (visited[i + dst.w] = 1), stack.push(i + dst.w);
  }

  return { data: next, w: dst.w, h: dst.h };
}
