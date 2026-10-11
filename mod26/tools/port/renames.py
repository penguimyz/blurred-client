"""Bulk 1.21.11 (Mojang names) -> 26.2 renames over the port's source.

Usage: python renames.py <java dir>
"""
import os
import re
import sys

ROOT = sys.argv[1]

# Whole-word identifier renames.
WORDS = {
    'GuiGraphics': 'GuiGraphicsExtractor',
    'drawString': 'text',
    'drawCenteredString': 'centeredText',
    'drawWordWrap': 'textWithWordWrap',
    'renderItem': 'item',
    'renderFakeItem': 'fakeItem',
    'renderItemDecorations': 'itemDecorations',
    'hLine': 'horizontalLine',
    'vLine': 'verticalLine',
    'renderOutline': 'outline',
    'submitOutline': 'outline',
    'renderTooltip': 'tooltip',
    'renderWidget': 'extractWidgetRenderState',
    'renderBackground': 'extractBackground',
    'renderBlurredBackground': 'extractBlurredBackground',
    'renderPanorama': 'extractPanorama',
    'renderMenuBackground': 'extractMenuBackground',
    'renderTransparentBackground': 'extractTransparentBackground',
    'renderWithTooltipAndSubtitles': 'extractRenderStateWithTooltipAndSubtitles',
    'KeyBindingHelper': 'KeyMappingHelper',
    'registerKeyBinding': 'registerKeyMapping',
}

# `render(GuiGraphicsExtractor ...)` overrides on screens/widgets/renderables.
RENDER_DEF = re.compile(r'\bvoid render\((\s*(?:final\s+)?(?:net\.minecraft\.client\.gui\.)?GuiGraphicsExtractor\s+\w+,\s*(?:final\s+)?int\s+\w+,\s*(?:final\s+)?int\s+\w+,\s*(?:final\s+)?float\s+\w+\))')
# Calls like `super.render(ctx, ...)` / `widget.render(ctx, mouseX, ...)` with 4 args.
RENDER_CALL = re.compile(r'(\.)render\((\w+, [^;()]*?, [^;()]*?, [^;()]*?)\)')

for d, _, files in os.walk(ROOT):
    for f in files:
        if not f.endswith('.java'):
            continue
        p = os.path.join(d, f)
        s = open(p, encoding='utf-8').read()
        o = s
        for a, b in WORDS.items():
            s = re.sub(r'\b' + a + r'\b', b, s)
        s = s.replace('net.fabricmc.fabric.api.client.keybinding.v1', 'net.fabricmc.fabric.api.client.keymapping.v1')
        s = RENDER_DEF.sub(r'void extractRenderState(\1', s)
        if s != o:
            open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('ok')
