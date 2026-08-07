import { useSyncExternalStore } from "react";

/**
 * How many bubbles you've ever popped.
 *
 * Kept in `localStorage` rather than in the settings file on the Rust side:
 * it's a toy tally that changes several times a second while someone is
 * clicking, and round-tripping that through a Tauri command and a disk write
 * would be absurd. It also genuinely doesn't matter if it's lost.
 *
 * Exposed as an external store so any component can subscribe without the
 * counter having to live in React state owned by one of them — `Bubbles`
 * writes, the title bar and Settings read, and none of them re-render each
 * other.
 */

const KEY = "blurred.bubblesPopped";

function load(): number {
  try {
    const raw = window.localStorage.getItem(KEY);
    const n = raw === null ? 0 : Number.parseInt(raw, 10);
    // A corrupt or hand-edited value must not poison the tally forever.
    return Number.isFinite(n) && n >= 0 ? n : 0;
  } catch {
    // localStorage can throw outright when storage is disabled.
    return 0;
  }
}

let count = load();
const listeners = new Set<() => void>();

/** Coalesce writes: popping is bursty and every write hits the disk. */
let flushTimer: number | undefined;
function scheduleFlush() {
  if (flushTimer !== undefined) return;
  flushTimer = window.setTimeout(() => {
    flushTimer = undefined;
    try {
      window.localStorage.setItem(KEY, String(count));
    } catch {
      // Nothing to do — the in-memory tally still works for this session.
    }
  }, 400);
}

export function bubblesPopped(): number {
  return count;
}

/** Record a pop. Called from the bubble canvas' hit test. */
export function recordBubblePop(): void {
  count += 1;
  scheduleFlush();
  listeners.forEach((fn) => fn());
}

export function resetBubbleCount(): void {
  count = 0;
  scheduleFlush();
  listeners.forEach((fn) => fn());
}

function subscribe(fn: () => void): () => void {
  listeners.add(fn);
  return () => {
    listeners.delete(fn);
  };
}

/** Live tally, re-rendering the caller whenever a bubble pops. */
export function useBubbleCount(): number {
  return useSyncExternalStore(subscribe, bubblesPopped, bubblesPopped);
}

// Persist immediately on the way out, so the last few pops before a close
// aren't lost inside the debounce window.
if (typeof window !== "undefined") {
  window.addEventListener("beforeunload", () => {
    try {
      window.localStorage.setItem(KEY, String(count));
    } catch {
      /* ignore */
    }
  });
}
