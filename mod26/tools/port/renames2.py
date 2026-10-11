"""Second pass of 26.2 renames: Minecraft/Gui accessors and vanilla render calls."""
import os
import re
import sys

ROOT = sys.argv[1]
P = r'(client|Minecraft\.getInstance\(\)|mc|f\.client|minecraft|this\.minecraft|this\.client)'
RULES = [
    (r'\b(super|this\.input|this\.nickField)\.render\((\w+), (\w+), (\w+), (\w+)\)', r'\1.extractRenderState(\2, \3, \4, \5)'),
    (P + r'\.setScreen\(', r'\1.gui.setScreen('),
    (P + r'\.screen\b(?!\s*\()', r'\1.gui.screen()'),
    (P + r'\.options\.hideGui\b', r'\1.gui.hud.isHidden()'),
    (r'\.getMainCamera\(\)', '.mainCamera()'),
    (P + r'\.getMainRenderTarget\(\)', r'\1.gameRenderer.mainRenderTarget()'),
    (P + r'\.getOverlay\(\)', r'\1.gui.overlay()'),
    (r'\.gui\.getChat\(\)', '.gui.hud.getChat()'),
    (r'\.getDayTime\(\)', '.getOverworldClockTime()'),
    (r'Screens\.getButtons\(', 'Screens.getWidgets('),
]

for d, _, fs in os.walk(ROOT):
    for f in fs:
        if not f.endswith('.java'):
            continue
        p = os.path.join(d, f)
        s = open(p, encoding='utf-8').read()
        o = s
        for a, b in RULES:
            s = re.sub(a, b, s)
        s = s.replace('api.client.gui.screen().v1', 'api.client.screen.v1')
        if s != o:
            open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('ok')
