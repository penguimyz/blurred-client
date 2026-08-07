import { useEffect, useRef } from "react";
import { recordBubblePop } from "../lib/bubbleCounter";

/**
 * Bubbles that drift up through the water — and pop when you click them.
 *
 * # Making them clickable without eating clicks
 *
 * The canvas is `pointer-events: none`, so it never intercepts anything. Pops
 * are detected by a `window` listener in the *capture* phase that hit-tests the
 * click against the bubble list. If it hits, that bubble pops; either way the
 * event continues to whatever button was underneath.
 *
 * That's the whole trick: a canvas that could be clicked would sit over the
 * entire UI and swallow every press. This way the bubbles are a layer you can
 * interact with that costs the rest of the app nothing.
 *
 * # Why the canvas sits behind the UI
 *
 * The bubbles used to float over the panels. They now drift *behind* them, with
 * the sea life, so the water reads as something the interface is submerged in
 * rather than something smeared across it. That makes the hit test do slightly
 * more work: a bubble hidden under a card must not pop, because popping
 * something you cannot see is just a click that swallowed itself. See
 * {@link occluded}.
 *
 * Drawn as pixel blocks on a grid to match the rest of the art.
 */

const PIXEL = 3;
const MAX_BUBBLES = 14;
const SPAWN_EVERY_MS = 1400;
/** Extra forgiveness on the hit test, in px — a 12px bubble is a small target. */
const HIT_PAD = 6;

interface Bubble {
  x: number;
  y: number;
  /** Radius in sprite pixels (not screen px). */
  r: number;
  speed: number;
  /** Horizontal wobble phase. */
  phase: number;
  drift: number;
  /** Counts down while the pop animation plays; 0 means alive. */
  popping: number;
}

export function Bubbles() {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

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

    const bubbles: Bubble[] = [];

    function spawn(): Bubble {
      return {
        x: Math.random() * window.innerWidth,
        y: window.innerHeight + 20,
        r: 1 + Math.floor(Math.random() * 3),
        speed: 0.25 + Math.random() * 0.5,
        phase: Math.random() * Math.PI * 2,
        drift: 0.3 + Math.random() * 0.6,
        popping: 0,
      };
    }

    // A couple already mid-rise so the screen isn't empty on arrival.
    for (let i = 0; i < 4; i++) {
      const b = spawn();
      b.y = Math.random() * window.innerHeight;
      bubbles.push(b);
    }

    function onPointerDown(e: MouseEvent) {
      // The canvas is behind the interface now, so a bubble under a panel is
      // invisible and must not be poppable.
      if (occluded(e.clientX, e.clientY)) return;

      // Nearest bubble wins, so overlapping ones pop predictably.
      let best = -1;
      let bestDist = Infinity;
      for (let i = 0; i < bubbles.length; i++) {
        const b = bubbles[i];
        if (b.popping > 0) continue;
        const rad = b.r * PIXEL + HIT_PAD;
        const d = Math.hypot(e.clientX - b.x, e.clientY - b.y);
        if (d <= rad && d < bestDist) {
          best = i;
          bestDist = d;
        }
      }
      // No preventDefault and no stopPropagation: the click must still reach
      // whatever UI is underneath.
      if (best >= 0) {
        bubbles[best].popping = 6;
        recordBubblePop();
      }
    }

    window.addEventListener("mousedown", onPointerDown, true);

    let raf = 0;
    let lastSpawn = performance.now();

    function frame(now: number) {
      if (!ctx) return;
      ctx.clearRect(0, 0, window.innerWidth, window.innerHeight);

      if (now - lastSpawn > SPAWN_EVERY_MS && bubbles.length < MAX_BUBBLES) {
        lastSpawn = now;
        bubbles.push(spawn());
      }

      for (let i = bubbles.length - 1; i >= 0; i--) {
        const b = bubbles[i];

        if (b.popping > 0) {
          b.popping--;
          drawPop(ctx, b);
          if (b.popping === 0) bubbles.splice(i, 1);
          continue;
        }

        b.y -= b.speed;
        b.phase += 0.04;
        b.x += Math.sin(b.phase) * b.drift;

        if (b.y < -20) {
          bubbles.splice(i, 1);
          continue;
        }
        drawBubble(ctx, b);
      }

      raf = requestAnimationFrame(frame);
    }

    if (reduced) {
      // Still drawn, just not moving — they remain poppable.
      for (const b of bubbles) drawBubble(ctx, b);
    } else {
      raf = requestAnimationFrame(frame);
    }

    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener("resize", resize);
      window.removeEventListener("mousedown", onPointerDown, true);
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      aria-hidden
      style={{
        position: "fixed",
        inset: 0,
        // Behind the interface, on the same layer as the sea life. DOM order in
        // App.tsx puts the bubbles in front of the backdrop gradient and the
        // creatures, and every panel in front of all three.
        zIndex: -1,
        pointerEvents: "none",
      }}
    />
  );
}

/**
 * Is this point covered by a piece of interface?
 *
 * Walks up from whatever is under the cursor looking for an ancestor that
 * actually paints something — a non-transparent background or a backdrop
 * filter. Deliberately generic rather than a list of class names: the test is
 * "would this hide a bubble", and any future card, modal or toast should be
 * caught without anyone remembering to add it here.
 */
function occluded(x: number, y: number): boolean {
  let el = document.elementFromPoint(x, y) as HTMLElement | null;
  while (el && el !== document.body && el !== document.documentElement) {
    const style = window.getComputedStyle(el);
    if (style.backdropFilter && style.backdropFilter !== "none") return true;

    const bg = style.backgroundColor;
    if (bg && bg !== "transparent") {
      // rgb(...) is fully opaque; rgba(...) needs its alpha read out.
      const alpha = bg.startsWith("rgba")
        ? Number.parseFloat(bg.slice(bg.lastIndexOf(",") + 1))
        : 1;
      if (Number.isFinite(alpha) && alpha > 0.05) return true;
    }
    if (style.backgroundImage && style.backgroundImage !== "none") return true;

    el = el.parentElement;
  }
  return false;
}

/** A ring of blocks with a highlight — a bubble at pixel-art resolution. */
function drawBubble(ctx: CanvasRenderingContext2D, b: Bubble) {
  const cx = Math.round(b.x / PIXEL) * PIXEL;
  const cy = Math.round(b.y / PIXEL) * PIXEL;

  ctx.fillStyle = "rgba(190, 245, 255, 0.4)";
  // Scanline rasterisation of a ring: for each row, fill the two spans either
  // side of the hollow centre. Same approach as the mod's porthole icon.
  for (let dy = -b.r; dy <= b.r; dy++) {
    const half = Math.round(Math.sqrt(Math.max(0, b.r * b.r - dy * dy)));
    if (half <= 0) continue;
    const y = cy + dy * PIXEL;
    if (Math.abs(dy) === b.r) {
      ctx.fillRect(cx - half * PIXEL, y, half * 2 * PIXEL + PIXEL, PIXEL);
    } else {
      ctx.fillRect(cx - half * PIXEL, y, PIXEL, PIXEL);
      ctx.fillRect(cx + half * PIXEL, y, PIXEL, PIXEL);
    }
  }

  // Highlight block, upper-left, the way a lit sphere reads.
  ctx.fillStyle = "rgba(255, 255, 255, 0.55)";
  ctx.fillRect(cx - (b.r - 1) * PIXEL, cy - (b.r - 1) * PIXEL, PIXEL, PIXEL);
}

/** Pop: a burst of blocks flying outward, fading over the remaining frames. */
function drawPop(ctx: CanvasRenderingContext2D, b: Bubble) {
  const cx = Math.round(b.x / PIXEL) * PIXEL;
  const cy = Math.round(b.y / PIXEL) * PIXEL;
  const age = 6 - b.popping;
  const spread = (b.r + age) * PIXEL;

  ctx.fillStyle = `rgba(210, 250, 255, ${b.popping / 8})`;
  for (let i = 0; i < 8; i++) {
    const a = (i / 8) * Math.PI * 2;
    ctx.fillRect(
      cx + Math.round((Math.cos(a) * spread) / PIXEL) * PIXEL,
      cy + Math.round((Math.sin(a) * spread) / PIXEL) * PIXEL,
      PIXEL,
      PIXEL
    );
  }
}
