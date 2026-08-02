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
