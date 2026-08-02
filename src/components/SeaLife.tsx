import { useEffect, useRef } from "react";

/**
 * Ambient sea life: shoals, jellyfish and the occasional shark drifting past
 * in the water *behind* the glass panels.
 *
 * Sits at z-index -1, painted after the Backdrop, so creatures swim between the
 * water gradient and the UI. That placement is the whole effect — seen through
 * a frosted panel they read as shapes in deep water, and only fully resolve in
 * the gaps between panels. Drawing them in front would make them stickers.
 *
 * # Casting
 *
 * Creatures spawn one at a time on a timer, from a weighted table, and are
 * retired once they leave the screen. There is never a crowd: at most
 * MAX_CREATURES, and the spawn gap is long enough that a shark is a genuine
 * event rather than scenery. The whole point is "occasional".
 *
 * Everything is a silhouette — a flat fill at low alpha, no detail — because
 * that's what a real animal at depth looks like, and because it means the drawn
 * cost per creature is a handful of path ops.
 *
 * Honours prefers-reduced-motion by not animating at all. Unlike the cursor
 * fish (which are explicitly opted into), this is on by default, so unrequested
 * motion here is exactly what that setting is asking us not to do.
 */

const MAX_CREATURES = 4;
/** Milliseconds between spawn attempts. Long on purpose — the brief is
 *  "occasional", and at the original 2.6s the screen read as an aquarium. */
const SPAWN_EVERY_MS = 11000;

type Kind = "shoal" | "jelly" | "shark" | "ray";

/** Relative spawn weights. Sharks are rare on purpose — that's what makes one
 *  crossing the screen feel like something rather than wallpaper. */
const WEIGHTS: Array<[Kind, number]> = [
  ["jelly", 46],
  ["shoal", 34],
  ["ray", 16],
  // ~4% of an 11s spawn tick: roughly one shark every few minutes. A shark
  // should be something you notice, not part of the furniture.
  ["shark", 4],
];

interface Creature {
  kind: Kind;
  x: number;
  y: number;
  vx: number;
  vy: number;
  scale: number;
  /** Animation phase, advanced per frame. */
  phase: number;
  /** 0..1 — how far back in the water. Drives size, speed, alpha and blur. */
  depth: number;
  /** Fish per shoal; unused by other kinds. */
  count: number;
  seed: number;
}

function pickKind(): Kind {
  const total = WEIGHTS.reduce((s, [, w]) => s + w, 0);
  let r = Math.random() * total;
  for (const [kind, w] of WEIGHTS) {
    if ((r -= w) <= 0) return kind;
  }
  return "shoal";
}

function spawn(kind: Kind): Creature {
  const h = window.innerHeight;
  const w = window.innerWidth;
  // Depth drives everything else, so that a "far" creature is consistently
  // smaller, slower, fainter and higher up the frame.
  const depth = Math.random();
  const leftToRight = Math.random() < 0.5;
  const dir = leftToRight ? 1 : -1;
  // Start fully off-screen; the widest creature is ~260px at scale 1.
  const x = leftToRight ? -300 : w + 300;

  const base: Creature = {
    kind,
    x,
    y: 0,
    vx: 0,
    vy: 0,
    scale: 1,
    phase: Math.random() * Math.PI * 2,
    depth,
    count: 0,
    seed: Math.random() * 1000,
  };

  switch (kind) {
    case "shark":
      return {
        ...base,
        y: h * (0.25 + Math.random() * 0.5),
        vx: dir * (0.5 + depth * 0.55),
        vy: 0,
        scale: 0.85 + depth * 0.7,
      };
    case "ray":
      return {
        ...base,
        y: h * (0.35 + Math.random() * 0.55),
        vx: dir * (0.32 + depth * 0.4),
        vy: 0,
        scale: 0.6 + depth * 0.5,
      };
    case "jelly":
      // Jellyfish drift upward rather than across, so they cross the other
      // traffic instead of joining it.
      return {
        ...base,
        x: Math.random() * w,
        y: h + 60,
        vx: (Math.random() - 0.5) * 0.14,
        vy: -(0.18 + depth * 0.3),
        scale: 0.5 + depth * 0.6,
      };
    case "shoal":
    default:
      return {
        ...base,
        y: h * (0.15 + Math.random() * 0.7),
        vx: dir * (0.6 + depth * 0.75),
        vy: 0,
        scale: 0.45 + depth * 0.45,
        count: 7 + Math.floor(Math.random() * 12),
      };
  }
}

/** Off-screen far enough that it can be retired. */
function isGone(c: Creature): boolean {
  const pad = 400;
  return (
    c.x < -pad ||
    c.x > window.innerWidth + pad ||
    c.y < -pad ||
    c.y > window.innerHeight + pad
  );
}

export function SeaLife() {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

    function resize() {
      if (!canvas || !ctx) return;
      const dpr = window.devicePixelRatio || 1;
      canvas.width = window.innerWidth * dpr;
      canvas.height = window.innerHeight * dpr;
      canvas.style.width = `${window.innerWidth}px`;
      canvas.style.height = `${window.innerHeight}px`;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }
    resize();
    window.addEventListener("resize", resize);

    const creatures: Creature[] = [];
    // Seed a couple already mid-crossing, so opening the launcher doesn't start
    // with an empty tank for the first few seconds.
    for (let i = 0; i < 3; i++) {
      const c = spawn(pickKind());
      c.x = Math.random() * window.innerWidth;
      creatures.push(c);
    }

    let lastSpawn = performance.now();
    let raf = 0;

    function frame(now: number) {
      if (!ctx) return;
      ctx.clearRect(0, 0, window.innerWidth, window.innerHeight);

      if (now - lastSpawn > SPAWN_EVERY_MS) {
        lastSpawn = now;
        if (creatures.length < MAX_CREATURES) creatures.push(spawn(pickKind()));
      }

      for (let i = creatures.length - 1; i >= 0; i--) {
        const c = creatures[i];
        c.x += c.vx;
        c.y += c.vy;
        c.phase += 0.03 + c.depth * 0.02;

        if (isGone(c)) {
          creatures.splice(i, 1);
          continue;
        }
        draw(ctx, c);
      }

      raf = requestAnimationFrame(frame);
    }

    raf = requestAnimationFrame(frame);

    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener("resize", resize);
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      aria-hidden
      style={{
        position: "fixed",
        inset: 0,
        // Same layer as the backdrop, painted after it (DOM order) so creatures
        // sit in front of the water gradient but behind every panel.
        zIndex: -1,
        pointerEvents: "none",
      }}
    />
  );
}

/**
 * Sprite renderer.
 *
 * Pixel-art pass: creatures are drawn as blocks on a grid rather than as smooth
 * bezier outlines. Each shape is a string array — one character per pixel — so
 * the art is legible in the source and editable without a drawing tool, which
 * is the whole reason for this representation over a path.
 *
 * The canvas transform does the scaling, so a "pixel" here is `PIXEL * scale`
 * screen pixels and every edge lands on the grid. No blur filter: a blurred
 * pixel sprite is just a smudge, so depth is carried entirely by size and
 * alpha now.
 */
const PIXEL = 3;

/** Paint a sprite defined as rows of characters. Space = transparent. */
function blit(
  ctx: CanvasRenderingContext2D,
  rows: string[],
  colors: Record<string, string>,
  offsetY = 0
) {
  const h = rows.length;
  const w = Math.max(...rows.map((r) => r.length));
  // Centre the sprite on its origin so `scale` and flipping behave.
  const ox = -Math.floor(w / 2) * PIXEL;
  const oy = -Math.floor(h / 2) * PIXEL + offsetY;

  for (let y = 0; y < h; y++) {
    const row = rows[y];
    for (let x = 0; x < row.length; x++) {
      const ch = row[x];
      if (ch === " ") continue;
      const color = colors[ch];
      if (!color) continue;
      ctx.fillStyle = color;
      // +1 on the size closes the hairline seams that appear between adjacent
      // rects once the canvas transform involves a fractional scale.
      ctx.fillRect(ox + x * PIXEL, oy + y * PIXEL, PIXEL + 1, PIXEL + 1);
    }
  }
}

function draw(ctx: CanvasRenderingContext2D, c: Creature) {
  ctx.save();
  ctx.translate(Math.round(c.x), Math.round(c.y));
  // Nearer creatures are bigger and brighter; far ones fade into the water.
  ctx.scale(c.vx < 0 ? -c.scale : c.scale, c.scale);
  ctx.globalAlpha = 0.12 + c.depth * 0.26;

  switch (c.kind) {
    case "shark":
      drawShark(ctx, c);
      break;
    case "jelly":
      drawJelly(ctx, c);
      break;
    case "ray":
      drawRay(ctx, c);
      break;
    case "shoal":
      drawShoal(ctx, c);
      break;
  }

  ctx.restore();
}

/*
  Sprite sheets. Read them as pictures — that's the point of this format.
    B = body, D = darker underside/shading, E = eye, T = translucent (fins)
  Facing right; the canvas flips them for creatures swimming left.
*/

const SHARK_COLORS: Record<string, string> = {
  B: "#bdf3ff",
  D: "#7fc4d8",
  E: "#0a2430",
};

/** Two frames, tail up and tail down — animation by frame swap, not by maths. */
const SHARK_FRAMES: string[][] = [
  [
    "        BB              ",
    "       BBBB             ",
    "      BBBBBB            ",
    "  BB  BBBBBBB           ",
    " BBBB BBBBBBBBB         ",
    "BBBBBBBBBBBBBBBBBB      ",
    "BBBBBBBBBBBBBBBBBBBBBE  ",
    "DDBBBBBBBBBBBBBBBBBBBBBB",
    " DDDDBBBBBBBBBBBBBBBBBB ",
    "   DDDDDDDDDDDDDDDDDD   ",
    "      DD  DDDDDDD       ",
    "     DDDD               ",
    "      DD                ",
  ],
  [
    "      DD                ",
    "     DDDD               ",
    "      DD  DDDDDDD       ",
    "   DDDDDDDDDDDDDDDDDD   ",
    " DDDDBBBBBBBBBBBBBBBBBB ",
    "DDBBBBBBBBBBBBBBBBBBBBBB",
    "BBBBBBBBBBBBBBBBBBBBBE  ",
    "BBBBBBBBBBBBBBBBBB      ",
    " BBBB BBBBBBBBB         ",
    "  BB  BBBBBBB           ",
    "      BBBBBB            ",
    "       BBBB             ",
    "        BB              ",
  ],
];

const RAY_COLORS: Record<string, string> = { B: "#bdf3ff", D: "#84c9dc" };

/** Three flap frames: wings down, level, up. */
const RAY_FRAMES: string[][] = [
  [
    "    BB    ",
    "   BBBB   ",
    "  BBBBBB  ",
    " BBBBBBBB ",
    "BBBBBBBBBB",
    "DDDBBBBDDD",
    "DD  DD  DD",
    "     D    ",
    "     D    ",
  ],
  [
    "          ",
    "    BB    ",
    "  BBBBBB  ",
    "BBBBBBBBBB",
    "BBBBBBBBBB",
    " DBBBBBBD ",
    "   DDDD   ",
    "     D    ",
    "     D    ",
  ],
  [
    "DD      DD",
    " DD    DD ",
    "  BBBBBB  ",
    "  BBBBBB  ",
    " BBBBBBBB ",
    "  DBBBBD  ",
    "   DDDD   ",
    "     D    ",
    "     D    ",
  ],
];

const JELLY_COLORS: Record<string, string> = { B: "#cdf6ff", D: "#8fd4e6" };

/** Two pulse frames: bell contracted and expanded. */
const JELLY_FRAMES: string[][] = [
  [
    "  BBBB  ",
    " BBBBBB ",
    "BBBBBBBB",
    "BBBBBBBB",
    "BBBBBBBB",
    " DDDDDD ",
    " D D D D",
    "D  D D  ",
    " D  D  D",
    "D  D  D ",
    "   D D  ",
  ],
  [
    "   BB   ",
    " BBBBBB ",
    "BBBBBBBB",
    "BBBBBBBB",
    " BBBBBB ",
    "  DDDD  ",
    " D D D  ",
    " D  D D ",
    "  D  D  ",
    " D  D  D",
    "  D  D  ",
  ],
];

const FISH_COLORS: Record<string, string> = { B: "#c8f5ff", D: "#8ecfe0" };

const FISH_FRAMES: string[][] = [
  [
    "D  BBB ",
    "DD BBBB",
    "DDDBBBB",
    "DD BBBB",
    "D  BBB ",
  ],
  [
    "  DBBB ",
    " DDBBBB",
    "DDDBBBB",
    " DDBBBB",
    "  DBBB ",
  ],
];

/** Pick an animation frame from a creature's phase. */
function frameOf(count: number, phase: number, speed = 1): number {
  return Math.floor(phase * speed) % count;
}

function drawShark(ctx: CanvasRenderingContext2D, c: Creature) {
  blit(ctx, SHARK_FRAMES[frameOf(SHARK_FRAMES.length, c.phase, 0.6)], SHARK_COLORS);
}

function drawRay(ctx: CanvasRenderingContext2D, c: Creature) {
  blit(ctx, RAY_FRAMES[frameOf(RAY_FRAMES.length, c.phase, 0.8)], RAY_COLORS);
}

function drawJelly(ctx: CanvasRenderingContext2D, c: Creature) {
  blit(ctx, JELLY_FRAMES[frameOf(JELLY_FRAMES.length, c.phase, 0.5)], JELLY_COLORS);
}

/**
 * A shoal: many small fish in a loose cloud. Positions are derived from the
 * creature's seed and index rather than stored, so a 20-fish shoal costs one
 * object instead of twenty — and stays identical frame to frame because the
 * pseudo-random offsets are a pure function of (seed, index).
 *
 * Offsets are snapped to the pixel grid, or fish would sit at fractional
 * positions and the sprite edges would blur back out.
 */
function drawShoal(ctx: CanvasRenderingContext2D, c: Creature) {
  for (let i = 0; i < c.count; i++) {
    // Cheap deterministic hash -> two offsets in [0,1).
    const n = Math.sin(c.seed + i * 12.9898) * 43758.5453;
    const rx = n - Math.floor(n);
    const m = Math.sin(c.seed + i * 78.233) * 43758.5453;
    const ry = m - Math.floor(m);

    const fx = Math.round(((rx - 0.5) * 130) / PIXEL) * PIXEL;
    // Each fish bobs on its own phase so the cloud shimmers rather than sliding.
    const bob = Math.round(Math.sin(c.phase + i * 0.9) * 1.2) * PIXEL;
    const fy = Math.round(((ry - 0.5) * 56) / PIXEL) * PIXEL + bob;

    ctx.save();
    ctx.translate(fx, fy);
    blit(ctx, FISH_FRAMES[frameOf(FISH_FRAMES.length, c.phase + i, 0.7)], FISH_COLORS);
    ctx.restore();
  }
}
