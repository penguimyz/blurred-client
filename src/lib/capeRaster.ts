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
 * The buffer is exactly the cape — {@link CAPE_AREA} — not the whole 64x32
 * sheet. Painting the rest of the sheet does nothing you can see on a cape, so
 * the editor no longer offers it.
 */

export const CAPE_W = CAPE_AREA.w;
export const CAPE_H = CAPE_AREA.h;
export const CAPE_PIXELS = CAPE_W * CAPE_H;

export type Rgb = readonly [number, number, number];

/** A fresh, fully transparent cape. */
export function emptyCape(): Uint8ClampedArray {
  return new Uint8ClampedArray(CAPE_PIXELS * 4);
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
  /** 0..1. Only meaningful for a soft brush; hard painting is always opaque. */
  opacity: number;
  color: Rgb;
  erase: boolean;
}

/**
 * Lay one brush stamp down at a point, in cape-pixel coordinates.
 *
 * The two modes are genuinely different operations rather than one with a
 * parameter:
 *
 * - **Pixel art** paints an exact NxN block of fully-opaque pixels. No
 *   coverage maths, so an edge drawn twice is identical to an edge drawn once
 *   and nothing ever ends up at 97% alpha where it should be flat.
 * - **Smooth** weights each pixel by how far it is from the brush centre and
 *   composites, so strokes build up and blend. On a canvas this small that
 *   reads as airbrushing rather than as blur.
 */
export function stamp(buf: Uint8ClampedArray, cx: number, cy: number, brush: Brush): void {
  if (brush.soft) {
    stampSoft(buf, cx, cy, brush);
  } else {
    stampHard(buf, cx, cy, brush);
  }
}

function stampHard(buf: Uint8ClampedArray, cx: number, cy: number, brush: Brush): void {
  const size = Math.max(1, Math.round(brush.size));
  // Anchor so the clicked pixel is in the block and odd sizes are centred.
  const half = Math.floor((size - 1) / 2);
  const x0 = Math.floor(cx) - half;
  const y0 = Math.floor(cy) - half;

  for (let y = y0; y < y0 + size; y++) {
    if (y < 0 || y >= CAPE_H) continue;
    for (let x = x0; x < x0 + size; x++) {
      if (x < 0 || x >= CAPE_W) continue;
      const i = y * CAPE_W + x;
      if (brush.erase) {
        erasePixel(buf, i, 1);
      } else {
        const o = i * 4;
        buf[o] = brush.color[0];
        buf[o + 1] = brush.color[1];
        buf[o + 2] = brush.color[2];
        buf[o + 3] = 255;
      }
    }
  }
}

function stampSoft(buf: Uint8ClampedArray, cx: number, cy: number, brush: Brush): void {
  const radius = Math.max(0.5, brush.size / 2);
  const x0 = Math.floor(cx - radius - 1);
  const x1 = Math.ceil(cx + radius + 1);
  const y0 = Math.floor(cy - radius - 1);
  const y1 = Math.ceil(cy + radius + 1);

  for (let y = y0; y <= y1; y++) {
    if (y < 0 || y >= CAPE_H) continue;
    for (let x = x0; x <= x1; x++) {
      if (x < 0 || x >= CAPE_W) continue;

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

      const i = y * CAPE_W + x;
      if (brush.erase) {
        erasePixel(buf, i, coverage);
      } else {
        blendPixel(buf, i, brush.color, coverage);
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
  buf: Uint8ClampedArray,
  fromX: number,
  fromY: number,
  toX: number,
  toY: number,
  brush: Brush
): void {
  const steps = Math.max(1, Math.ceil(Math.hypot(toX - fromX, toY - fromY) * 2));
  for (let s = 1; s <= steps; s++) {
    const t = s / steps;
    stamp(buf, fromX + (toX - fromX) * t, fromY + (toY - fromY) * t, brush);
  }
}

/** The colour under a point, or null where the cape is transparent. */
export function pick(buf: Uint8ClampedArray, x: number, y: number): string | null {
  if (x < 0 || y < 0 || x >= CAPE_W || y >= CAPE_H) return null;
  const o = (y * CAPE_W + x) * 4;
  if (buf[o + 3] < 8) return null;
  return rgbToHex([buf[o], buf[o + 1], buf[o + 2]]);
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
  buf: Uint8ClampedArray,
  startX: number,
  startY: number,
  color: Rgb,
  { tolerance, contiguous, erase }: FillOptions
): Uint8ClampedArray {
  if (startX < 0 || startY < 0 || startX >= CAPE_W || startY >= CAPE_H) return buf;

  const start = startY * CAPE_W + startX;
  const target = buf.slice(start * 4, start * 4 + 4);
  // 510 is the largest possible distance, so this maps the slider onto the
  // full useful range with 1.0 meaning "everything".
  const limit = tolerance * 510;

  const next = new Uint8ClampedArray(buf);

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
    for (let i = 0; i < CAPE_PIXELS; i++) {
      if (distance(buf, i, target) <= limit) apply(i);
    }
    return next;
  }

  // Iterative flood, because even 160 cells is no reason to risk the stack —
  // and `visited` is what stops a tolerant fill from revisiting pixels it has
  // already recoloured (whose distance to the target has since changed).
  const visited = new Uint8Array(CAPE_PIXELS);
  const stack = [start];
  visited[start] = 1;

  while (stack.length) {
    const i = stack.pop()!;
    if (distance(buf, i, target) > limit) continue;
    apply(i);

    const x = i % CAPE_W;
    const y = (i - x) / CAPE_W;
    if (x > 0 && !visited[i - 1]) (visited[i - 1] = 1), stack.push(i - 1);
    if (x < CAPE_W - 1 && !visited[i + 1]) (visited[i + 1] = 1), stack.push(i + 1);
    if (y > 0 && !visited[i - CAPE_W]) (visited[i - CAPE_W] = 1), stack.push(i - CAPE_W);
    if (y < CAPE_H - 1 && !visited[i + CAPE_W]) (visited[i + CAPE_W] = 1), stack.push(i + CAPE_W);
  }

  return next;
}
