import { useEffect, useRef } from "react";

/**
 * A small school of fish that follows the cursor, and circles it once the
 * cursor holds still.
 *
 * Opt-in (Settings -> Ambience). Off by default: a thing that tracks the
 * pointer across every screen is a toy, and toys should be chosen, not
 * inflicted.
 *
 * # How it moves
 *
 * Each fish is a steering agent with a position, a velocity, and a personal
 * orbit phase. Every frame it computes a *target* and steers toward it rather
 * than snapping — which is what makes the motion read as swimming instead of
 * dragging:
 *
 *   - ACTIVE (cursor moved recently): the target is a point offset from the
 *     cursor, per fish, so they fan out instead of stacking.
 *   - IDLE (cursor still for ~900ms): the target walks around a circle centred
 *     on the cursor, each fish at its own angle, so the school settles into a
 *     slow orbit.
 *
 * Steering is a lerp toward the target, capped by MAX_SPEED, plus light drag.
 * The lerp constant sets how eager they look; the cap stops them teleporting
 * when the cursor jumps across the screen.
 *
 * # On prefers-reduced-motion
 *
 * Deliberately NOT honoured here, and this is the one place in the app that
 * ignores it. The setting exists to stop *unrequested* motion; these fish are
 * unchecked by default and exist only because someone went to Settings and
 * asked for swimming fish. Freezing them would mean the feature silently does
 * nothing for anyone whose OS has animations turned down — which is exactly
 * how the first version of this failed. The ambient sea life, which is on by
 * default and therefore unrequested, does still honour it.
 */

const FISH_COUNT = 7;
/*
  Tuning note. The original values (speed 4.4, steer 0.055, drag 0.92) made the
  school dart: a low drag with a high steer builds up velocity faster than it
  bleeds off, so the fish overshoot the cursor and snap back — which reads as
  twitchy rather than as swimming.

  Lower steer means they commit to a heading more gently, and heavier drag
  bleeds the overshoot instead of storing it. The speed cap is what stops a
  fast cursor flick from launching them across the screen.
*/
const MAX_SPEED = 2.6;
const STEER = 0.032;
const DRAG = 0.86;
/** How long the cursor must hold still before the school starts circling. */
const IDLE_AFTER_MS = 900;
const ORBIT_RADIUS = 46;

interface Fish {
  x: number;
  y: number;
  vx: number;
  vy: number;
  /** Where this fish sits in the orbit, in radians. */
  phase: number;
  /** Per-fish size and speed jitter so the school isn't a rigid formation. */
  scale: number;
  wobble: number;
  hue: number;
}

export function FishSchool() {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

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

    // Start the school centred so it doesn't fly in from (0,0) on first paint.
    const cursor = { x: window.innerWidth / 2, y: window.innerHeight / 2 };
    // Seeded in the past so the school is already orbiting on the first frame
    // rather than waiting for the user to move and then stop.
    let lastMove = performance.now() - IDLE_AFTER_MS * 2;

    const fish: Fish[] = Array.from({ length: FISH_COUNT }, (_, i) => {
      const phase = (i / FISH_COUNT) * Math.PI * 2;
      return {
        // Spread around the orbit immediately. Starting them all at one point
        // made the school a single blob for the first second.
        x: cursor.x + Math.cos(phase) * ORBIT_RADIUS,
        y: cursor.y + Math.sin(phase) * ORBIT_RADIUS * 0.62,
        vx: 0,
        vy: 0,
        phase,
        scale: 0.75 + (i % 3) * 0.2,
        wobble: Math.random() * Math.PI * 2,
        // Cyan through green — the bioluminescent band of the palette.
        hue: 165 + ((i * 37) % 60),
      };
    });

    function onMove(e: MouseEvent) {
      cursor.x = e.clientX;
      cursor.y = e.clientY;
      lastMove = performance.now();
    }

    window.addEventListener("mousemove", onMove, { passive: true });
    window.addEventListener("resize", resize);

    let raf = 0;
    let t = 0;

    function frame() {
      if (!ctx || !canvas) return;
      t += 1;
      // Clear in CSS pixels: the context carries a dpr transform, so passing
      // the backing-store size here would clear a region dpr times too large.
      ctx.clearRect(0, 0, window.innerWidth, window.innerHeight);

      const idle = performance.now() - lastMove > IDLE_AFTER_MS;

      for (const f of fish) {
        let tx: number;
        let ty: number;

        if (idle) {
          // Orbit: advance this fish's angle and aim at the resulting point.
          // Radius breathes a little per fish so the ring isn't a hard circle.
          f.phase += 0.013;
          const r = ORBIT_RADIUS + Math.sin(t * 0.02 + f.wobble) * 8 + f.scale * 6;
          tx = cursor.x + Math.cos(f.phase) * r;
          ty = cursor.y + Math.sin(f.phase) * r * 0.62; // squashed = seen at an angle
        } else {
          // Chase: aim around the cursor, fanned out per fish so seven fish
          // don't converge on one pixel.
          const spread = 26 + f.scale * 14;
          tx = cursor.x + Math.cos(f.phase) * spread;
          ty = cursor.y + Math.sin(f.phase) * spread;
          f.phase += 0.004;
        }

        // Steer: nudge velocity toward the target, cap it, then apply drag.
        f.vx += (tx - f.x) * STEER;
        f.vy += (ty - f.y) * STEER;

        const speed = Math.hypot(f.vx, f.vy);
        if (speed > MAX_SPEED) {
          f.vx = (f.vx / speed) * MAX_SPEED;
          f.vy = (f.vy / speed) * MAX_SPEED;
        }
        f.vx *= DRAG;
        f.vy *= DRAG;

        f.x += f.vx;
        f.y += f.vy;

        drawFish(ctx, f, t);
      }

      raf = requestAnimationFrame(frame);
    }

    raf = requestAnimationFrame(frame);

    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener("mousemove", onMove);
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
        // Above ordinary page content (so the fish actually follow your cursor
        // across the UI rather than hiding behind panels), but below modals
        // (z-index 200) and the title bar (1000) — a fish swimming over a
        // dialog you're reading is just in the way.
        zIndex: 150,
        pointerEvents: "none",
      }}
    />
  );
}

/** Screen pixels per sprite pixel. */
const PIXEL = 3;

/*
  Two tail-beat frames, facing right.
    T = tail (dimmer), B = body, E = eye
  Drawn as sprites rather than curves so the school matches the pixel-art
  restyle — a smooth vector fish next to a blocky UI reads as a bug.
*/
const FISH_FRAMES: string[][] = [
  [
    "T   BBB  ",
    "TT BBBBB ",
    "TTTBBBBBE",
    "TT BBBBB ",
    "T   BBB  ",
  ],
  [
    "  T BBB  ",
    " TTBBBBB ",
    "TTTBBBBBE",
    " TTBBBBB ",
    "  T BBB  ",
  ],
];

/**
 * One fish, drawn as a sprite.
 *
 * The canvas is rotated into the fish's heading and the sprite is blitted
 * facing +x, so the sprite itself needs no directional variants. Rotation does
 * soften the pixel grid at odd angles, which is the honest trade for having
 * them actually point where they're swimming — snapping headings to 45° looked
 * worse than the slight softening.
 */
function drawFish(ctx: CanvasRenderingContext2D, f: Fish, t: number) {
  const speed = Math.hypot(f.vx, f.vy);
  // atan2(0,0) is 0, so a motionless fish faces right rather than glitching.
  const angle = speed > 0.01 ? Math.atan2(f.vy, f.vx) : 0;

  // Tail beats faster the faster it swims, with a floor so a hovering fish
  // still idles rather than freezing mid-water.
  const frame = Math.floor(t * (0.12 + speed * 0.05) + f.wobble) % FISH_FRAMES.length;

  const body = `hsl(${f.hue} 78% 66%)`;
  const tail = `hsl(${f.hue} 70% 48%)`;
  const colors: Record<string, string> = { B: body, T: tail, E: "#04222c" };

  ctx.save();
  ctx.translate(Math.round(f.x), Math.round(f.y));
  ctx.rotate(angle);
  ctx.globalAlpha = 0.9;

  const rows = FISH_FRAMES[frame];
  const w = Math.max(...rows.map((r) => r.length));
  const ox = -Math.floor(w / 2) * PIXEL * f.scale;
  const oy = -Math.floor(rows.length / 2) * PIXEL * f.scale;
  const px = PIXEL * f.scale;

  for (let y = 0; y < rows.length; y++) {
    for (let x = 0; x < rows[y].length; x++) {
      const ch = rows[y][x];
      if (ch === " ") continue;
      ctx.fillStyle = colors[ch];
      // +0.6 closes the hairline seams rotation opens between adjacent rects.
      ctx.fillRect(ox + x * px, oy + y * px, px + 0.6, px + 0.6);
    }
  }

  ctx.restore();
}
