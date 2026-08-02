import type { CSSProperties } from "react";
import type { Account } from "../types/account";

/**
 * A Minecraft head, cropped out of the raw skin PNG.
 *
 * # The crop
 *
 * A skin sheet is 64 wide. The face is the 8x8 block at (8,8), and the "hat"
 * (second) layer that many skins use for hair, glasses and headwear is the 8x8
 * block at (40,8). Both are drawn, hat on top, or skins with headwear render
 * bald.
 *
 * Scaling so an 8px block fills `size` means the whole 64px-wide sheet becomes
 * `size * 8` — hence the background-size — and the block's origin lands at
 * exactly `size` in that scaled space, hence the offset.
 *
 * # Three things that made the first version misalign
 *
 * 1. **`box-sizing: border-box` plus a `border`.** The global reset makes the
 *    element's background area `size - 2*border`, so a percentage
 *    background-size and a `size`-based offset were both computed against a box
 *    smaller than `size`. The ring is now a `box-shadow`, which draws outside
 *    the layout box and can't shift the crop — and `backgroundOrigin:
 *    border-box` pins the origin regardless.
 * 2. **`background-size: 800% 800%` on legacy sheets.** Old skins are 64x32,
 *    not 64x64. Forcing the height to 8x the element stretched them vertically.
 *    Sizing the width in px and letting the height be `auto` preserves the
 *    aspect ratio, and — because the face sits in the top half either way — the
 *    same offset is correct for both sheet sizes.
 * 3. **No hat layer**, so any skin with headwear rendered without it.
 */
export function Avatar({
  account,
  size = 32,
  style,
}: {
  account: Pick<Account, "username" | "skinUrl">;
  size?: number;
  style?: CSSProperties;
}) {
  const frame: CSSProperties = {
    position: "relative",
    width: size,
    height: size,
    flexShrink: 0,
    overflow: "hidden",
    // Ring drawn outside the layout box, so it can never affect the crop.
    boxShadow: "0 0 0 2px var(--glass-border)",
    ...style,
  };

  if (account.skinUrl) {
    // Width in px with `auto` height: correct for both 64x64 and 64x32 sheets.
    const sheetWidth = size * 8;
    const layer = (offsetX: number): CSSProperties => ({
      position: "absolute",
      inset: 0,
      backgroundImage: `url(${account.skinUrl})`,
      backgroundSize: `${sheetWidth}px auto`,
      backgroundPosition: `-${offsetX}px -${size}px`,
      backgroundRepeat: "no-repeat",
      backgroundOrigin: "border-box",
      imageRendering: "pixelated",
    });

    return (
      <div title={account.username} style={frame}>
        {/* Face: source (8,8). */}
        <div style={layer(size)} />
        {/* Hat overlay: source (40,8) — five face-widths further right. */}
        <div style={layer(size * 5)} />
      </div>
    );
  }

  // No skin (offline accounts, or a Microsoft account whose skin hasn't
  // resolved): a deterministic initial on a tinted tile. Same letter and shade
  // every time for a given name, so it still works as a recognizable identity.
  const hue = hashHue(account.username);
  return (
    <div
      title={account.username}
      style={{
        ...frame,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background: `hsl(${hue} 45% 28%)`,
        color: `hsl(${hue} 70% 82%)`,
        fontFamily: "var(--font-pixel)",
        fontSize: size * 0.4,
        userSelect: "none",
      }}
    >
      {account.username.charAt(0).toUpperCase() || "?"}
    </div>
  );
}

// Cheap string hash -> hue. Restricted to the 160-260 band so generated
// avatars stay in the ocean's blue-green-teal range instead of landing on a
// red or yellow that fights the palette.
function hashHue(name: string): number {
  let h = 0;
  for (let i = 0; i < name.length; i++) {
    h = (h * 31 + name.charCodeAt(i)) | 0;
  }
  return 160 + (Math.abs(h) % 100);
}
