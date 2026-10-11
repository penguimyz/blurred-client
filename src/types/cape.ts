// Mirrors src-tauri/src/commands/capes.rs.

export interface Cape {
  id: string;
  name: string;
  createdAt: string;
  /** SHA-1 of the PNG bytes; also its identity on the wire when shared. */
  hash: string;
}

/**
 * Cape sheet geometry. Minecraft reads a 64x32 sheet, and the cape itself is
 * only the 10x16 block at (1,1) — the rest of the sheet is the elytra texture
 * and padding. The editor works on the whole sheet but highlights this region,
 * because painting outside it does nothing visible on a cape.
 */
export const CAPE_SHEET_W = 64;
export const CAPE_SHEET_H = 32;
export const CAPE_AREA = { x: 1, y: 1, w: 10, h: 16 } as const;

/**
 * The cape is a box, and a box has six sides.
 *
 * A cape is a 10x16x1 cuboid, and Minecraft unwraps a cuboid onto the sheet in
 * a fixed cross: the two large faces either side of a one-pixel edge strip,
 * with the top and bottom caps on the row above. {@link CAPE_AREA} is only the
 * first of those — the outside, where every cape's artwork goes — which is why
 * the editor started with just that one.
 *
 * The others are genuinely visible in game, though. The lining shows whenever
 * the cape lifts as you walk or fly, and the edges are the cape's thickness
 * seen side-on. Leaving them unpaintable meant they stayed whatever the blank
 * sheet had in them, which is a transparent seam down each side of an otherwise
 * finished cape.
 *
 * Offsets follow Minecraft's standard cuboid unwrap for a box of width 10,
 * height 16 and depth 1 at texture origin (0,0).
 */
export interface CapeFace {
  key: string;
  label: string;
  hint: string;
  x: number;
  y: number;
  w: number;
  h: number;
}

export const CAPE_FACES: readonly CapeFace[] = [
  {
    key: "back",
    label: "Back",
    hint: "The outside — the face everyone sees. This is the one people mean by 'the cape'.",
    ...CAPE_AREA,
  },
  {
    key: "front",
    label: "Front",
    hint: "The lining, against your back. Shows when the cape lifts as you move.",
    x: 12,
    y: 1,
    w: 10,
    h: 16,
  },
  {
    key: "left",
    label: "Left",
    hint: "The cape's thickness down one side. One pixel wide — it's an edge, not a panel.",
    x: 11,
    y: 1,
    w: 1,
    h: 16,
  },
  {
    key: "right",
    label: "Right",
    hint: "The other edge. Also one pixel wide.",
    x: 0,
    y: 1,
    w: 1,
    h: 16,
  },
  {
    key: "top",
    label: "Top",
    hint: "The strip along the top, at your shoulders. Ten by one.",
    x: 1,
    y: 0,
    w: 10,
    h: 1,
  },
  {
    key: "bottom",
    label: "Bottom",
    hint: "The hem along the bottom. Ten by one.",
    x: 11,
    y: 0,
    w: 10,
    h: 1,
  },
] as const;
