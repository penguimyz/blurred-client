"""Generates Blurred's post-effect chains (assets/blurred/post_effect/*.json).

Ambience is built like a shader pack's final passes: ambient occlusion from
the depth buffer (blurred to remove noise), bloom (bright pass, two blur
rounds), then a composite with distance haze, ACES tone mapping and the
look's grade. Motion blur can follow it.

Minecraft 1.21.11 bakes post-shader settings into these files, so each
look/blur combination is its own chain: blurred:<look>_<blur>. Every uniform
is a vec4, because Minecraft doesn't pack floats into a vec3's spare slot.
Run from the mod folder: python tools/gen_post_effects.py
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'blurred', 'post_effect')

# mix: ao strength, bloom, haze amount, haze density
# tone: exposure, saturation, contrast, warmth
# frame: vignette, letterbox, tint amount, sky boost
LOOKS = {
    'none': None,
    'shader': dict(mix=[0.8, 0.28, 0.14, 0.008], tone=[1.2, 1.15, 1.05, 0.25], frame=[0.3, 0.0, 0.1, 1.0],
                   tint=[1.0, 0.97, 0.92], haze=[0.7, 0.8, 0.95], threshold=0.55),
    'golden': dict(mix=[0.8, 0.4, 0.2, 0.01], tone=[1.25, 1.2, 1.05, 1.0], frame=[0.4, 0.0, 0.25, 1.05],
                   tint=[1.0, 0.88, 0.7], haze=[1.0, 0.82, 0.6], threshold=0.5),
    'arctic': dict(mix=[0.75, 0.3, 0.22, 0.012], tone=[1.2, 0.95, 1.06, -0.8], frame=[0.35, 0.0, 0.25, 1.0],
                   tint=[0.8, 0.92, 1.0], haze=[0.8, 0.9, 1.0], threshold=0.55),
    'cinematic': dict(mix=[0.85, 0.32, 0.14, 0.008], tone=[1.15, 0.92, 1.14, 0.3], frame=[0.7, 0.085, 0.2, 1.0],
                      tint=[0.88, 0.97, 1.0], haze=[0.6, 0.72, 0.82], threshold=0.55),
    'noir': dict(mix=[0.9, 0.25, 0.18, 0.01], tone=[1.15, 0.0, 1.3, 0.0], frame=[0.85, 0.0, 0.0, 1.0],
                 tint=[1.0, 1.0, 1.0], haze=[0.7, 0.7, 0.7], threshold=0.55),
    'dreamy': dict(mix=[0.6, 0.8, 0.2, 0.01], tone=[1.25, 1.2, 0.96, 0.3], frame=[0.3, 0.0, 0.2, 1.05],
                   tint=[1.0, 0.92, 1.0], haze=[0.96, 0.84, 0.96], threshold=0.35),
    'midnight': dict(mix=[0.85, 0.35, 0.35, 0.014], tone=[0.66, 0.8, 1.15, -1.0], frame=[0.65, 0.0, 0.6, 0.6],
                     tint=[0.45, 0.6, 1.0], haze=[0.1, 0.14, 0.32], threshold=0.4),
    'moody': dict(mix=[0.95, 0.25, 0.22, 0.01], tone=[0.82, 0.72, 1.22, 0.1], frame=[0.55, 0.0, 0.18, 0.9],
                  tint=[0.82, 0.95, 0.98], haze=[0.3, 0.34, 0.38], threshold=0.6),
    'horror': dict(mix=[1.0, 0.2, 0.4, 0.02], tone=[0.68, 0.5, 1.28, 0.15], frame=[0.95, 0.0, 0.4, 0.75],
                   tint=[0.8, 1.0, 0.72], haze=[0.12, 0.16, 0.1], threshold=0.6),
    'dusk': dict(mix=[0.85, 0.45, 0.28, 0.012], tone=[0.85, 1.12, 1.08, 0.7], frame=[0.5, 0.0, 0.3, 0.9],
                 tint=[1.0, 0.78, 0.88], haze=[0.55, 0.36, 0.5], threshold=0.5),
    'vivid': dict(mix=[0.8, 0.3, 0.1, 0.006], tone=[1.2, 1.4, 1.1, 0.1], frame=[0.25, 0.0, 0.0, 1.0],
                  tint=[1.0, 1.0, 1.0], haze=[0.7, 0.8, 0.95], threshold=0.55),
}
BLUR = {'off': None, 'on': 0.5}


def u(name, typ, value):
    return {'name': name, 'type': typ, 'value': value}


def v4(values):
    return values + [0.0] * (4 - len(values))


def pass_(frag, inputs, output, uniforms):
    return {'vertex_shader': 'minecraft:core/screenquad', 'fragment_shader': frag,
            'inputs': inputs, 'output': output, 'uniforms': uniforms}


def blit(src, dst):
    return pass_('minecraft:post/blit', [{'sampler_name': 'In', 'target': src}], dst,
                 {'BlitConfig': [u('ColorModulate', 'vec4', [1.0, 1.0, 1.0, 1.0])]})


def box_blur(src, dst, direction, radius):
    return pass_('minecraft:post/box_blur', [{'sampler_name': 'In', 'target': src, 'bilinear': True}], dst,
                 {'BlurConfig': [u('BlurDir', 'vec2', direction), u('Radius', 'float', radius)]})


def look_passes(p):
    depth = {'sampler_name': 'Depth', 'target': 'minecraft:main', 'use_depth_buffer': True}
    return [
        pass_('blurred:post/ssao', [depth], 'ao',
              {'AoConfig': [u('Params', 'vec4', v4([0.55, 1.35]))]}),
        box_blur('ao', 'ao2', [1.0, 0.0], 2.0),
        box_blur('ao2', 'ao', [0.0, 1.0], 2.0),
        pass_('blurred:post/bright', [{'sampler_name': 'In', 'target': 'minecraft:main'}], 'b1',
              {'BrightConfig': [u('Params', 'vec4', v4([p['threshold'], 0.2]))]}),
        box_blur('b1', 'b2', [1.0, 0.0], 6.0),
        box_blur('b2', 'b1', [0.0, 1.0], 6.0),
        box_blur('b1', 'b2', [1.0, 0.0], 14.0),
        box_blur('b2', 'b1', [0.0, 1.0], 14.0),
        pass_('blurred:post/composite', [
            {'sampler_name': 'In', 'target': 'minecraft:main'}, depth,
            {'sampler_name': 'Ao', 'target': 'ao', 'bilinear': True},
            {'sampler_name': 'Bloom', 'target': 'b1', 'bilinear': True}], 'swap',
            {'LookConfig': [u('Mix', 'vec4', p['mix']), u('Tone', 'vec4', p['tone']), u('Frame', 'vec4', p['frame']),
                            u('Tint', 'vec4', v4(p['tint'])), u('Haze', 'vec4', v4(p['haze']))]}),
    ]


def chain(look, blur):
    targets = {'swap': {}}
    passes = []
    current = 'minecraft:main'
    p = LOOKS[look]
    if p is not None:
        for t in ('ao', 'ao2', 'b1', 'b2'):
            targets[t] = {}
        passes += look_passes(p)
        current = 'swap'
    strength = BLUR[blur]
    if strength is not None:
        # Camera motion blur: reprojected from depth with numbers written every frame.
        targets['mixed'] = {}
        depth = {'sampler_name': 'Depth', 'target': 'minecraft:main', 'use_depth_buffer': True}
        identity = [1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0]
        passes.append(pass_('blurred:post/motion', [{'sampler_name': 'In', 'target': current, 'bilinear': True}, depth],
                            'mixed', {'BlurredMotion': [u('Reproject', 'matrix4x4', identity),
                                                        u('Params', 'vec4', [strength, 0.045, 0.0, 0.0])]}))
        passes.append(blit('mixed', 'minecraft:main'))
    else:
        passes.append(blit(current, 'minecraft:main'))
    return {'targets': targets, 'passes': passes}


os.makedirs(OUT, exist_ok=True)
for old in os.listdir(OUT):
    if old.endswith('.json'):
        os.remove(os.path.join(OUT, old))
count = 0
for look in LOOKS:
    for blur in BLUR:
        if look == 'none' and blur == 'off':
            continue
        with open(os.path.join(OUT, f'{look}_{blur}.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump(chain(look, blur), f, indent=2)
            f.write('\n')
        count += 1
print('wrote', count, 'chains')
