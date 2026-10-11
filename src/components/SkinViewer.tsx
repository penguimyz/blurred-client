import { useEffect, useRef, useState } from "react";
import { MODEL_HEIGHT, SKIN_H, SKIN_W, skinParts, type SkinPart, type SkinVariant } from "../types/skin";
import { animate } from "../lib/animation";

/**
 * The player model in 3D, turnable with the mouse.
 *
 * # Why CSS and not a 3D library
 *
 * A player is twelve axis-aligned boxes with a texture atlas. That is the exact
 * case CSS 3D transforms handle natively: `preserve-3d` gives real perspective
 * and depth sorting, each face is a `<div>` with the skin as a background
 * cropped to that face's rectangle, and the browser rasterises it on the GPU.
 *
 * The alternative was three.js — a megabyte of dependency, a WebGL context and
 * a render loop, to draw seventy-two textured quads that never deform. It would
 * also break the build: this project vendors nothing and builds offline, so a
 * new npm dependency is a real cost rather than a line in package.json.
 *
 * The honest limit of this approach is that CSS depth-sorts per element rather
 * than per fragment, so two coplanar faces can flicker over each other. That's
 * why the overlay layers are inflated (see `OVERLAY_INFLATE`) rather than sat
 * exactly on the base — which is also what Minecraft itself does.
 *
 * # Cropping a face out of the atlas
 *
 * A face shows one rectangle of a 64x64 sheet. `background-size` scales the
 * whole sheet so that rectangle covers the element, and `background-position`
 * shifts the right part into view. With `image-rendering: pixelated` the result
 * is exact — no filtering across the seam into the neighbouring face, which is
 * what you get if you scale a cropped canvas instead.
 */

/** Screen pixels per model unit at rest. */
const DEFAULT_UNIT = 7;

/**
 * How far overlay layers stand off the base, in model units.
 *
 * Minecraft inflates the hat and jacket layers for the same reason: two
 * surfaces at exactly the same depth have no correct draw order, and the
 * result is z-fighting that flickers as the model turns.
 */
const OVERLAY_INFLATE = 0.28;

/** Degrees per second of idle turn. Slow — it's a preview, not a carousel. */
const IDLE_SPIN = 18;

interface Props {
  /** The skin sheet as a data/blob URL, or null while loading. */
  src: string | null;
  variant: SkinVariant;
  /** Screen pixels per model unit. The model is 32 units tall. */
  unit?: number;
  /** Turn slowly on its own until the user grabs it. */
  spin?: boolean;
  /** Draw the hat/jacket/sleeve layers. */
  showOverlay?: boolean;
}

export function SkinViewer({
  src,
  variant,
  unit = DEFAULT_UNIT,
  spin = true,
  showOverlay = true,
}: Props) {
  const [yaw, setYaw] = useState(-25);
  const [pitch, setPitch] = useState(-8);
  const dragging = useRef<{ x: number; y: number } | null>(null);
  /** Set once the user turns it; idle spin never resumes after that. */
  const [grabbed, setGrabbed] = useState(false);

  // Idle turn. Time-based like every other animation here, so it turns at the
  // same speed on a 60Hz panel and a 240Hz one — see lib/animation.
  useEffect(() => {
    if (!spin || grabbed) return;
    return animate((step) => {
      setYaw((y) => (y + (IDLE_SPIN * step) / 60) % 360);
    });
  }, [spin, grabbed]);

  useEffect(() => {
    function onMove(e: MouseEvent) {
      if (!dragging.current) return;
      const dx = e.clientX - dragging.current.x;
      const dy = e.clientY - dragging.current.y;
      dragging.current = { x: e.clientX, y: e.clientY };
      setYaw((y) => y + dx * 0.6);
      // Clamped so the model can't be tipped past vertical, which reads as the
      // viewer being broken rather than as a feature.
      setPitch((p) => Math.max(-45, Math.min(45, p - dy * 0.4)));
    }
    function onUp() {
      dragging.current = null;
    }
    window.addEventListener("mousemove", onMove);
    window.addEventListener("mouseup", onUp);
    return () => {
      window.removeEventListener("mousemove", onMove);
      window.removeEventListener("mouseup", onUp);
    };
  }, []);

  const parts = skinParts(variant).filter((p) => showOverlay || !p.overlay);
  const height = MODEL_HEIGHT * unit;

  return (
    <div
      onMouseDown={(e) => {
        dragging.current = { x: e.clientX, y: e.clientY };
        setGrabbed(true);
      }}
      title="Drag to turn"
      style={{
        // The scene needs room for the model at any angle: turned side-on it's
        // as deep as it is wide, and the perspective origin sits at the centre.
        width: "100%",
        height: height + unit * 8,
        display: "grid",
        placeItems: "center",
        perspective: `${height * 2.2}px`,
        cursor: dragging.current ? "grabbing" : "grab",
        userSelect: "none",
        overflow: "hidden",
      }}
    >
      <div
        style={{
          position: "relative",
          transformStyle: "preserve-3d",
          transform: `rotateX(${pitch}deg) rotateY(${yaw}deg)`,
          // No transition: it's driven by a frame loop and by the pointer, and
          // interpolating on top of either makes the drag feel like it's on a
          // spring.
          width: 0,
          height: 0,
        }}
      >
        {parts.map((part) => (
          <Box key={part.key} part={part} src={src} unit={unit} />
        ))}
      </div>
    </div>
  );
}

/** One body part: six textured faces arranged into a cuboid. */
function Box({ part, src, unit }: { part: SkinPart; src: string | null; unit: number }) {
  const inflate = part.overlay ? OVERLAY_INFLATE : 0;
  const w = (part.size.w + inflate * 2) * unit;
  const h = (part.size.h + inflate * 2) * unit;
  const d = (part.size.d + inflate * 2) * unit;

  // The box's centre, relative to the scene origin.
  const cx = (part.origin.x + part.size.w / 2) * unit;
  const cy = (part.origin.y + part.size.h / 2) * unit;
  const cz = (part.origin.z + part.size.d / 2) * unit;

  return (
    <div
      style={{
        position: "absolute",
        transformStyle: "preserve-3d",
        transform: `translate3d(${cx}px, ${cy}px, ${cz}px)`,
        width: 0,
        height: 0,
      }}
    >
      {part.faces.map((face) => {
        // Each face is positioned at the centre and then pushed out along its
        // own normal by half the box, which is why every transform below is a
        // rotation followed by a single translateZ.
        const placement: Record<string, { size: [number, number]; transform: string }> = {
          front: { size: [w, h], transform: `translateZ(${d / 2}px)` },
          back: { size: [w, h], transform: `rotateY(180deg) translateZ(${d / 2}px)` },
          right: { size: [d, h], transform: `rotateY(-90deg) translateZ(${w / 2}px)` },
          left: { size: [d, h], transform: `rotateY(90deg) translateZ(${w / 2}px)` },
          top: { size: [w, d], transform: `rotateX(90deg) translateZ(${h / 2}px)` },
          bottom: { size: [w, d], transform: `rotateX(-90deg) translateZ(${h / 2}px)` },
        };
        const place = placement[face.key];
        if (!place) return null;
        const [fw, fh] = place.size;

        return (
          <div
            key={face.key}
            style={{
              position: "absolute",
              width: fw,
              height: fh,
              // Centre the face on the box's origin before its own transform.
              left: -fw / 2,
              top: -fh / 2,
              transform: place.transform,
              backfaceVisibility: "hidden",
              backgroundImage: src ? `url(${src})` : undefined,
              // Scale the whole sheet so this face's rectangle fills the
              // element, then slide that rectangle into place.
              backgroundSize: `${(SKIN_W / face.w) * fw}px ${(SKIN_H / face.h) * fh}px`,
              backgroundPosition: `${(-face.x / face.w) * fw}px ${(-face.y / face.h) * fh}px`,
              backgroundRepeat: "no-repeat",
              imageRendering: "pixelated",
              // Transparent overlay pixels must stay transparent — that's the
              // whole point of a hat layer.
              backgroundColor: "transparent",
            }}
          />
        );
      })}
    </div>
  );
}
