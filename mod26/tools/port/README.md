# Porting the 1.21.x mod to 26.x

`mod26/src` is generated from the Stonecutter tree in `mod/`, then fixed by
hand. These scripts are the generated half, kept so the next port starts from
them rather than from scratch.

1. `pipeline.sh` copies `mod/src/main/java`, renames the UI kit's `Component`
   to `KitComponent` (it clashes with Minecraft's text class under Mojang
   names), converts the 1.21.11 source from yarn to Mojang names with Loom's
   `migrateMappings`, flattens the `//?` branches to 1.21.11
   (`resolve_sc.py`), and applies the bulk 26.x renames (`renames.py`,
   `renames2.py`). The paths at the top are this machine's; change them.
2. Compile, and work through what's left with `errs.py` (one line per
   distinct javac error).
3. Mixin targets are strings, so the compiler can't check them:
   `checkmixins.py` reports targets that no longer exist in the decompiled
   26.x source, and `sigs.py` lists each handler's parameters beside its
   target's, for spotting changed signatures.

The hand-fixed half is everything those don't cover. As of 26.2:

- `WorldBuffers` replaces the removed buffer source: world effects record
  vertices per render type and submit them as custom geometry.
- `WorldDraw` hooks Fabric's `LevelRenderEvents.COLLECT_SUBMITS`.
- `LiquidGlass` builds its pipeline from bind group layouts.
- The terrain shaders work out the face normal in the fragment shader (26.x
  block vertices have no normal).
- Many mixins were retargeted (`render*` methods became `extract*`/`submit*`,
  the HUD moved to `Hud`, `setScreen` moved to `Gui`, the FOV moved to
  `Camera.calculateFov`).
