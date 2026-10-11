/**
 * A frame loop that runs at the same *speed* on every display.
 *
 * # The problem this exists to solve
 *
 * `requestAnimationFrame` fires once per display refresh, so a loop that
 * advances by a fixed amount each callback — `y -= speed` — moves at whatever
 * rate the monitor happens to run at. On the 60Hz panel the numbers were tuned
 * on it looks right; on a 144Hz laptop the same bubbles rise 2.4x too fast, and
 * on a 240Hz display 4x. That was reported as "the bubbles and the background
 * are really fast for some people", and "some people" meant "everyone with a
 * gaming monitor".
 *
 * The fix is to stop counting frames and start measuring time. `onFrame`
 * receives a `step`: how many 60Hz frames' worth of time has actually elapsed.
 * Multiply every per-frame delta by it and the motion is identical everywhere —
 * a fast display just draws the same movement in smaller, smoother increments.
 *
 * # Why a step multiplier rather than seconds
 *
 * Passing elapsed seconds would be the more conventional signature, but every
 * caller here was already tuned in per-frame units. A multiplier that is 1.0 at
 * 60Hz keeps all of those constants meaningful, so converting a loop is
 * mechanical (`x += vx` becomes `x += vx * step`) instead of a re-tune of every
 * magic number.
 *
 * # Quantities that decay
 *
 * A per-frame multiply like `v *= DRAG` is *not* linear in the step and must
 * not be scaled with one — see {@link decay}.
 */

/** The cadence the animation constants in this app are written against. */
const REFERENCE_FPS = 60;
const REFERENCE_FRAME_MS = 1000 / REFERENCE_FPS;

/**
 * Largest step a single frame may claim, in reference frames.
 *
 * Background tabs, a dragged window and a stalled GPU all produce one enormous
 * gap followed by normal frames. Without a ceiling that gap gets integrated in
 * one go and everything teleports — bubbles jump off the top of the screen, the
 * fish school snaps across the window. Clamping trades a little lost time for
 * continuity, which is the right trade for decoration.
 */
const MAX_STEP = 3;

/**
 * Run `onFrame` every display frame with a time-scaled step.
 *
 * @param onFrame receives `step` (elapsed time in 60Hz frames, clamped) and the
 *                raw `now` timestamp, for callers that also need wall clock.
 * @returns a cancel function; call it from the effect's cleanup.
 */
export function animate(onFrame: (step: number, now: number) => void): () => void {
  let raf = 0;
  let previous = performance.now();

  function frame(now: number) {
    // Guard against a non-monotonic or identical timestamp: a zero step is
    // harmless (nothing moves for one frame), a negative one is not.
    const elapsed = Math.max(0, now - previous);
    previous = now;
    onFrame(Math.min(MAX_STEP, elapsed / REFERENCE_FRAME_MS), now);
    raf = requestAnimationFrame(frame);
  }

  raf = requestAnimationFrame(frame);
  return () => cancelAnimationFrame(raf);
}

/**
 * Scale a per-frame decay factor to a step.
 *
 * Drag and smoothing are repeated multiplications, so the right answer over
 * `step` frames is the factor raised to that power — not multiplied by it.
 * Getting this wrong is subtle rather than obvious: `v * DRAG * 2` is not
 * "two frames of drag", it's more drag than the fish can ever recover from, and
 * the school goes limp on a fast display instead of going fast.
 */
export function decay(factor: number, step: number): number {
  return Math.pow(factor, step);
}
