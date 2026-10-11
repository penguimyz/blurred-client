// The geometry of a Minecraft skin: which rectangle of the 64x64 sheet is
// which side of which body part.
//
// One table, read by two things that must agree exactly — the editor (which
// paints a rectangle) and the 3D viewer (which maps it onto a face). Deriving
// both from the same source is the point: a viewer built from a second,
// hand-written copy of these numbers looks right until one box is a pixel out,
// and then you're comparing two lists of magic numbers to find it.

export const SKIN_W = 64;
export const SKIN_H = 64;

/** Classic has 4px-wide arms; slim has 3px. Nothing else differs. */
export type SkinVariant = "classic" | "slim";

export interface SkinFace {
  key: string;
  label: string;
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface SkinPart {
  key: string;
  label: string;
  /** Box size in model units, which is also its size in texture pixels. */
  size: { w: number; h: number; d: number };
  /** Where the box sits in the model, in model units, relative to the body. */
  origin: { x: number; y: number; z: number };
  faces: SkinFace[];
  /** True for the hat/jacket/sleeve layers drawn slightly larger over the base. */
  overlay: boolean;
  /** The part this overlays, for parts where `overlay` is true. */
  base?: string;
}

/**
 * The six faces of a box, in Minecraft's texture unwrap.
 *
 * Every part of a skin uses the same layout at a different origin: the top and
 * bottom caps side by side on the row above, then right, front, left, back in a
 * strip below. Writing it once and calling it eight times is the difference
 * between one place to be wrong and eight.
 */
function unwrap(u: number, v: number, w: number, h: number, d: number): SkinFace[] {
  return [
    { key: "top", label: "Top", x: u + d, y: v, w, h: d },
    { key: "bottom", label: "Bottom", x: u + d + w, y: v, w, h: d },
    { key: "right", label: "Right", x: u, y: v + d, w: d, h },
    { key: "front", label: "Front", x: u + d, y: v + d, w, h },
    { key: "left", label: "Left", x: u + d + w, y: v + d, w: d, h },
    { key: "back", label: "Back", x: u + d + w + d, y: v + d, w, h },
  ];
}

/**
 * Every box in the player model, base layers and overlays.
 *
 * Arm width is the one thing that varies: `slim` narrows both arms to 3px,
 * which moves the UVs of every face after the first. That's why this is a
 * function of the variant rather than a constant.
 *
 * Origins are in model units with (0,0,0) at the centre of the body's top edge,
 * y growing downward — the same convention the CSS viewer uses, chosen so the
 * head sits at negative y and the numbers read like the model looks.
 */
export function skinParts(variant: SkinVariant): SkinPart[] {
  const arm = variant === "slim" ? 3 : 4;

  return [
    {
      key: "head",
      label: "Head",
      size: { w: 8, h: 8, d: 8 },
      origin: { x: -4, y: -8, z: -4 },
      faces: unwrap(0, 0, 8, 8, 8),
      overlay: false,
    },
    {
      key: "hat",
      label: "Hat",
      size: { w: 8, h: 8, d: 8 },
      origin: { x: -4, y: -8, z: -4 },
      faces: unwrap(32, 0, 8, 8, 8),
      overlay: true,
      base: "head",
    },
    {
      key: "body",
      label: "Body",
      size: { w: 8, h: 12, d: 4 },
      origin: { x: -4, y: 0, z: -2 },
      faces: unwrap(16, 16, 8, 12, 4),
      overlay: false,
    },
    {
      key: "jacket",
      label: "Jacket",
      size: { w: 8, h: 12, d: 4 },
      origin: { x: -4, y: 0, z: -2 },
      faces: unwrap(16, 32, 8, 12, 4),
      overlay: true,
      base: "body",
    },
    {
      key: "rightArm",
      label: "Right arm",
      size: { w: arm, h: 12, d: 4 },
      origin: { x: -4 - arm, y: 0, z: -2 },
      faces: unwrap(40, 16, arm, 12, 4),
      overlay: false,
    },
    {
      key: "rightSleeve",
      label: "Right sleeve",
      size: { w: arm, h: 12, d: 4 },
      origin: { x: -4 - arm, y: 0, z: -2 },
      faces: unwrap(40, 32, arm, 12, 4),
      overlay: true,
      base: "rightArm",
    },
    {
      key: "leftArm",
      label: "Left arm",
      size: { w: arm, h: 12, d: 4 },
      origin: { x: 4, y: 0, z: -2 },
      faces: unwrap(32, 48, arm, 12, 4),
      overlay: false,
    },
    {
      key: "leftSleeve",
      label: "Left sleeve",
      size: { w: arm, h: 12, d: 4 },
      origin: { x: 4, y: 0, z: -2 },
      faces: unwrap(48, 48, arm, 12, 4),
      overlay: true,
      base: "leftArm",
    },
    {
      key: "rightLeg",
      label: "Right leg",
      size: { w: 4, h: 12, d: 4 },
      origin: { x: -4, y: 12, z: -2 },
      faces: unwrap(0, 16, 4, 12, 4),
      overlay: false,
    },
    {
      key: "rightPant",
      label: "Right trouser",
      size: { w: 4, h: 12, d: 4 },
      origin: { x: -4, y: 12, z: -2 },
      faces: unwrap(0, 32, 4, 12, 4),
      overlay: true,
      base: "rightLeg",
    },
    {
      key: "leftLeg",
      label: "Left leg",
      size: { w: 4, h: 12, d: 4 },
      origin: { x: 0, y: 12, z: -2 },
      faces: unwrap(16, 48, 4, 12, 4),
      overlay: false,
    },
    {
      key: "leftPant",
      label: "Left trouser",
      size: { w: 4, h: 12, d: 4 },
      origin: { x: 0, y: 12, z: -2 },
      faces: unwrap(0, 48, 4, 12, 4),
      overlay: true,
      base: "leftLeg",
    },
  ];
}

/** Total model height in units, for fitting the viewer to its container. */
export const MODEL_HEIGHT = 32;
