"""Generates Blurred's sword model packs (resourcepacks/swords_*/).

Each pack swaps every sword for another weapon (katana, claymore, tanto,
scythe, karambit, lightsaber) drawn as Minecraft-style pixel art: a 32x32
sprite (twice vanilla's resolution) laid along the same diagonal as vanilla's
swords, a few flat shades per material, and a dark outline in the material's
own colour, like vanilla's items. The game extrudes it into a held item
exactly as it does vanilla's swords, so it looks at home in your hand, in
item frames and in the hotbar.

Each pixel is decided by where it sits along the weapon (u, from the handle
end to the tip) and across it (v), so the shapes are written as profiles.

Run from the mod folder: python tools/gen_sword_models.py
"""
import json
import math
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'resourcepacks')
SIZE = 32

# Per material: blade shades (outline, dark, mid, light, shine), handle (dark, light), saber colour.
MATERIALS = {
    'wooden': dict(blade=[(40, 26, 12), (89, 61, 31), (137, 103, 58), (176, 138, 84), (206, 172, 112)],
                   wrap=[(52, 34, 18), (120, 86, 48)], saber=(80, 255, 110)),
    'stone': dict(blade=[(38, 38, 40), (82, 82, 86), (120, 120, 124), (154, 154, 158), (190, 190, 194)],
                  wrap=[(44, 36, 30), (110, 94, 78)], saber=(190, 90, 255)),
    'iron': dict(blade=[(52, 54, 60), (130, 132, 140), (188, 190, 198), (226, 228, 234), (255, 255, 255)],
                 wrap=[(28, 28, 34), (196, 188, 170)], saber=(235, 240, 255)),
    'golden': dict(blade=[(92, 56, 6), (186, 124, 22), (234, 186, 54), (252, 226, 104), (255, 250, 190)],
                   wrap=[(90, 18, 20), (196, 52, 46)], saber=(255, 220, 60)),
    'diamond': dict(blade=[(10, 52, 58), (32, 130, 132), (74, 206, 196), (152, 240, 228), (220, 255, 250)],
                    wrap=[(18, 26, 56), (64, 98, 168)], saber=(60, 150, 255)),
    'netherite': dict(blade=[(20, 16, 18), (52, 44, 48), (78, 68, 74), (110, 98, 104), (150, 138, 144)],
                      wrap=[(72, 12, 16), (150, 40, 40)], saber=(255, 50, 50)),
    'copper': dict(blade=[(64, 30, 16), (150, 78, 46), (200, 112, 70), (234, 156, 104), (255, 200, 150)],
                   wrap=[(30, 56, 50), (82, 156, 136)], saber=(255, 140, 40)),
}
GOLD = [(84, 52, 8), (176, 120, 24), (232, 184, 60), (255, 230, 130)]
IRON = [(40, 40, 46), (98, 98, 108), (150, 150, 160), (208, 208, 216)]
BLACK = [(14, 14, 18), (34, 34, 40), (60, 60, 70)]
WOOD = [(46, 30, 14), (92, 62, 32), (128, 90, 50), (160, 118, 70)]


def katana_like(u, v, m, short=False):
    blade_end = 30 if short else 43
    if 2 <= u < 3.6:
        return GOLD[2] if v < 0 else GOLD[1]                      # pommel
    if 3.6 <= u < (10 if short else 12) and abs(v) < 1.3:
        # Cord wrap: a diamond pattern over the handle.
        return m['wrap'][0] if (int(u * 1.4) + (v > 0)) % 2 == 0 else m['wrap'][1]
    guard = 10 if short else 12
    if guard <= u < guard + 1.6 and abs(v) < (2.6 if short else 3.2):
        return BLACK[2] if abs(v) > 2.2 else BLACK[1]             # tsuba
    if guard + 1.6 <= u < guard + 2.6 and abs(v) < 1.4:
        return GOLD[2]                                            # habaki
    start = guard + 2.6
    if start <= u < blade_end:
        t = (u - start) / (blade_end - start)
        bend = 1.1 * t * t                                        # curves back towards the spine
        w = 1.35 - 0.35 * t
        vv = v + bend
        tip = blade_end - 3.5
        if u > tip:
            # The point: the edge cuts back to the spine.
            k = (u - tip) / 3.5
            if vv > w - 2.6 * w * k or vv < -w:
                return None
        elif abs(vv) > w:
            return None
        b = m['blade']
        if vv > w - 0.55:
            return b[4]                                           # the edge
        if vv > 0.15 * math.sin(u * 0.9):
            return b[3]                                           # temper line, wavy
        return b[2] if vv > -w + 0.6 else b[1]                    # body, spine
    return None


def claymore(u, v, m):
    b = m['blade']
    if 2 <= u < 4.4 and abs(v) < 1.6:
        return IRON[3] if v < -0.3 else IRON[2]                   # round pommel
    if 4.4 <= u < 11 and abs(v) < 1.0:
        return m['wrap'][1] if int(u * 1.5) % 2 == 0 else m['wrap'][0]
    if 11 <= u < 12.6 and abs(v) < 5.6:
        # Crossguard, its ends flaring up towards the blade.
        return IRON[3] if v < 0 else IRON[2]
    if 12.6 <= u < 14.2 and 4.0 < abs(v) < 5.6:
        return IRON[2]
    if 12.6 <= u < 15 and abs(v) < 1.6:
        return IRON[2]                                            # ricasso
    if 15 <= u < 43:
        t = (u - 15) / 28
        w = 2.4 - 1.0 * t
        if u > 40:
            w *= (43 - u) / 3
        if abs(v) > w:
            return None
        if abs(v) < 0.45 and u < 37:
            return b[1]                                           # fuller
        if abs(v) > w - 0.6:
            return b[4] if v < 0 else b[3]                        # edges
        return b[3] if v < 0 else b[2]
    return None


def scythe(u, v, m):
    b = m['blade']
    # The snath (handle) runs the whole diagonal.
    if 1 <= u < 41 and abs(v) < 0.75:
        if 14 <= u < 17:
            return m['wrap'][1]                                   # grip
        return WOOD[2] if v < 0 else WOOD[1]
    if 40 <= u < 42 and abs(v) < 1.3:
        return IRON[2]
    return None


def scythe_blade(x, y, m):
    """The blade sweeps out from the top of the handle, in image pixels."""
    b = m['blade']
    cx, cy = 21.0, 13.0
    d = math.hypot(x - cx, y - cy)
    ang = math.atan2(y - cy, x - cx)
    if 7.0 < d < 11.0 and -math.pi < ang < -0.25 * math.pi:
        inner = d < 8.0
        return b[4] if inner else (b[3] if d < 9.5 else b[2])
    return None


def karambit(u, v, m):
    b = m['blade']
    # The finger ring at the base.
    ring = math.hypot(u - 5.0, v)
    if 1.6 < ring < 3.2:
        return IRON[3] if v < 0 else IRON[2]
    if 7.5 <= u < 17 and abs(v) < 1.4:
        return m['wrap'][1] if int(u * 1.3) % 2 == 0 else m['wrap'][0]
    if 17 <= u < 18.2 and abs(v) < 1.8:
        return IRON[2]
    if 18.2 <= u < 31:
        t = (u - 18.2) / 12.8
        hook = 7.0 * t * t                                        # curls round, towards the edge side
        w = 1.7 - 1.0 * t
        vv = v + hook
        if abs(vv) > w:
            return None
        if vv > w - 0.6:
            return b[4]                                           # the inside edge
        return b[3] if vv > -0.2 else b[2]
    return None


def lightsaber(u, v, m):
    if 2 <= u < 14 and abs(v) < 1.4:
        if u < 3.2 or 12.6 <= u:
            return IRON[3]
        if int(u) % 3 == 0:
            return IRON[2]                                        # chrome bands
        if 8 <= u < 9 and v > 0.6:
            return (220, 40, 40)                                  # activator
        return BLACK[1] if v > 0 else BLACK[2]
    if 14 <= u < 43:
        w = 1.9 if u < 41 else 1.9 * (43 - u) / 2
        a = abs(v)
        if a > w:
            return None
        c = m['saber']
        if a < 0.7:
            return (255, 255, 255)
        if a < 1.3:
            return tuple(min(255, int(ch * 0.5 + 255 * 0.5)) for ch in c)
        return c
    return None


STYLES = {
    'katana': lambda u, v, m: katana_like(u, v, m),
    'tanto': lambda u, v, m: katana_like(u, v, m, short=True),
    'claymore': claymore,
    'scythe': scythe,
    'karambit': karambit,
    'lightsaber': lightsaber,
}


def sprite(style, mat):
    m = MATERIALS[mat]
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    filled = [[False] * SIZE for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            # Bottom-left is the handle end, top-right the tip, as vanilla's swords.
            fx = x + 0.5
            fy = SIZE - (y + 0.5)
            u = (fx + fy) / math.sqrt(2)
            v = (fx - fy) / math.sqrt(2)
            c = STYLES[style](u, v, m)
            if c is None and style == 'scythe':
                c = scythe_blade(x, y, m)
            if c is not None:
                px[x, y] = tuple(c) + (255,)
                filled[y][x] = True
    # A dark outline in the material's own colour, as on vanilla's items.
    outline = m['blade'][0] + (255,)
    if style == 'lightsaber':
        outline = tuple(int(ch * 0.35) for ch in m['saber']) + (255,)
    edge = []
    for y in range(SIZE):
        for x in range(SIZE):
            if filled[y][x]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < SIZE and 0 <= ny < SIZE and filled[ny][nx]:
                    edge.append((x, y))
                    break
    for x, y in edge:
        px[x, y] = outline
    return img


# Vanilla's handheld display, scaled up a little for the longer weapons.
SCALE = {'katana': 1.0, 'claymore': 1.0, 'scythe': 1.05, 'tanto': 0.95, 'karambit': 0.95, 'lightsaber': 1.0}


def display(style):
    k = SCALE[style]

    def d(rot, tr, sc):
        return {'rotation': rot, 'translation': tr, 'scale': [round(sc * k, 3)] * 3}
    return {
        'thirdperson_righthand': d([0, -90, 55], [0, 4.0, 0.5], 0.85),
        'thirdperson_lefthand': d([0, 90, -55], [0, 4.0, 0.5], 0.85),
        'firstperson_righthand': d([0, -90, 25], [1.13, 3.2, 1.13], 0.68),
        'firstperson_lefthand': d([0, 90, -25], [1.13, 3.2, 1.13], 0.68),
    }


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=1)
        f.write('\n')


for style in STYLES:
    base = os.path.join(ROOT, 'swords_' + style)
    for mat in MATERIALS:
        item = mat + '_sword'
        tex = f'item/swords/{style}_{mat}'
        path = os.path.join(base, 'assets', 'blurred', 'textures', tex + '.png')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        sprite(style, mat).save(path)
        write_json(os.path.join(base, 'assets', 'minecraft', 'models', 'item', item + '.json'),
                   {'parent': 'minecraft:item/handheld', 'textures': {'layer0': 'blurred:' + tex},
                    'display': display(style)})
    write_json(os.path.join(base, 'pack.mcmeta'), {'pack': {
        'description': 'Blurred ' + style + ' swords',
        'pack_format': 34, 'supported_formats': [34, 99], 'min_format': 34, 'max_format': 99}})
print('wrote', len(STYLES), 'sword packs')
