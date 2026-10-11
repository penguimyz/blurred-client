"""Generates Blurred's world shader packs (resourcepacks/world_*/).

Each pack overrides Minecraft 1.21.11's terrain shader with the same code
and different switches: waving foliage, wet ground, or both. Only one pack
is on at a time (they replace the same files). Run from the mod folder:
python tools/gen_world_shaders.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'resourcepacks')

VSH = r'''#version 330
{defines}
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec3 blurredWorld;
out vec3 blurredNormal;
out vec3 blurredView;

vec4 minecraft_sample_lightmap(sampler2D lightMap, ivec2 uv) {{
    return texture(lightMap, clamp((uv / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}}

void main() {{
    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    vec3 world = Position + vec3(ChunkPosition);

    // Leaves, vines and plants are marked with an alpha just under opaque:
    // 254 minus how far the vertex may move (see FoliageMarks). Everything
    // else, including grass blocks and water, is left alone.
    vec4 color = Color;
    float mark = 254.0 - floor(Color.a * 255.0 + 0.5);
    bool marked = mark > -0.5 && mark < 10.5;
    if (marked) {{
        color.a = 1.0;
    }}
#ifdef BLURRED_FOLIAGE
    if (marked && mark > 0.5) {{
        // Displacement depends only on world position and time, so leaves
        // next to each other move together and no gaps open.
        float weight = mark / 10.0;
        float t = GameTime * 1800.0;
        float gust = 0.6 + 0.4 * sin(GameTime * 260.0 + world.x * 0.05 + world.z * 0.03);
        vec2 sway = vec2(sin(t + world.x * 0.7 + world.z * 0.4), cos(t * 0.8 + world.z * 0.6 + world.x * 0.3));
        pos.xz += sway * 0.1 * gust * weight;
        pos.y += sin(t * 1.3 + world.x + world.z) * 0.015 * weight;
    }}
#endif

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
    blurredWorld = world;
    blurredNormal = Normal;
    blurredView = pos;
}}
'''

FSH_HEAD = r'''#version 330
{defines}
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 blurredWorld;
in vec3 blurredNormal;
in vec3 blurredView;

out vec4 fragColor;
'''

FSH_WET = r'''
float blurredHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float blurredNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(blurredHash(i), blurredHash(i + vec2(1.0, 0.0)), f.x),
               mix(blurredHash(i + vec2(0.0, 1.0)), blurredHash(i + vec2(1.0, 1.0)), f.x), f.y);
}

// Wet ground: darker surfaces, puddles that mirror the sky, raindrop ripples.
vec4 blurredWet(vec4 color) {
    // Soaked surfaces are darker and a little richer in colour; the sides of
    // blocks get some of it too, tops (where rain lands) most of it.
    float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    if (blurredNormal.y < 0.9) {
        color.rgb = mix(vec3(luma), color.rgb, 1.08) * 0.82;
        return color;
    }
    color.rgb = mix(vec3(luma), color.rgb, 1.18);
    vec2 p = blurredWorld.xz;
    float n = blurredNoise(p * 0.45) * 0.7 + blurredNoise(p * 1.6) * 0.3;
    float puddle = smoothstep(0.66, 0.71, n);
    color.rgb *= mix(0.62, 0.48, puddle);
    vec3 view = normalize(-blurredView);
    float fresnel = pow(1.0 - clamp(view.y, 0.0, 1.0), 3.0);
    float light = max(vertexColor.r, max(vertexColor.g, vertexColor.b));
    vec3 sky = FogColor.rgb * light * 1.15;
    // Rings spreading from random spots, a few at a time.
    float t = GameTime * 1200.0;
    vec2 cell = floor(p * 1.5);
    vec2 local = fract(p * 1.5) - 0.5;
    float phase = fract(t * 0.35 + blurredHash(cell) * 7.0);
    float ring = abs(length(local - (vec2(blurredHash(cell + 3.1), blurredHash(cell + 7.7)) - 0.5) * 0.5) - phase * 0.45);
    float ripple = (1.0 - smoothstep(0.0, 0.03, ring)) * (1.0 - phase) * puddle;
    color.rgb = mix(color.rgb, sky, puddle * (0.22 + 0.6 * fresnel));
    color.rgb += ripple * 0.18 * light;
    return color;
}
'''

FSH_MAIN = r'''
vec4 sampleNearest(sampler2D sampler, vec2 uv, vec2 pixelSize, vec2 du, vec2 dv, vec2 texelScreenSize) {{
    vec2 uvTexelCoords = uv / pixelSize;
    vec2 texelCenter = round(uvTexelCoords) - 0.5f;
    vec2 texelOffset = uvTexelCoords - texelCenter;
    texelOffset = (texelOffset - 0.5f) * pixelSize / texelScreenSize + 0.5f;
    texelOffset = clamp(texelOffset, 0.0f, 1.0f);
    uv = (texelCenter + texelOffset) * pixelSize;
    return textureGrad(sampler, uv, du, dv);
}}

vec4 sampleNearest(sampler2D source, vec2 uv, vec2 pixelSize) {{
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);
    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    return sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);
}}

vec4 sampleRGSS(sampler2D source, vec2 uv, vec2 pixelSize) {{
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);
    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    float maxTexelSize = max(texelScreenSize.x, texelScreenSize.y);
    float minPixelSize = min(pixelSize.x, pixelSize.y);
    float transitionStart = minPixelSize * 1.0;
    float transitionEnd = minPixelSize * 2.0;
    float blendFactor = smoothstep(transitionStart, transitionEnd, maxTexelSize);
    float duLength = length(du);
    float dvLength = length(dv);
    float minDerivative = min(duLength, dvLength);
    float maxDerivative = max(duLength, dvLength);
    float effectiveDerivative = sqrt(minDerivative * maxDerivative);
    float mipLevelExact = max(0.0, log2(effectiveDerivative / minPixelSize));
    float mipLevelLow = floor(mipLevelExact);
    float mipLevelHigh = mipLevelLow + 1.0;
    float mipBlend = fract(mipLevelExact);
    const vec2 offsets[4] = vec2[](vec2(0.125, 0.375), vec2(-0.125, -0.375), vec2(0.375, -0.125), vec2(-0.375, 0.125));
    vec4 rgssColorLow = vec4(0.0);
    vec4 rgssColorHigh = vec4(0.0);
    for (int i = 0; i < 4; ++i) {{
        vec2 sampleUV = uv + offsets[i] * pixelSize;
        rgssColorLow += textureLod(source, sampleUV, mipLevelLow);
        rgssColorHigh += textureLod(source, sampleUV, mipLevelHigh);
    }}
    rgssColorLow *= 0.25;
    rgssColorHigh *= 0.25;
    vec4 rgssColor = mix(rgssColorLow, rgssColorHigh, mipBlend);
    vec4 nearestColor = sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);
    return mix(nearestColor, rgssColor, blendFactor);
}}

void main() {{
    vec4 color = (UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize)) * vertexColor;
    color = mix(FogColor * vec4(1, 1, 1, color.a), color, ChunkVisibility);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {{
        discard;
    }}
#endif
{wet_call}
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}}
'''

VARIANTS = {
    'world_foliage': ['BLURRED_FOLIAGE'],
    'world_wet': ['BLURRED_WET'],
    'world_both': ['BLURRED_FOLIAGE', 'BLURRED_WET'],
}

for name, flags in VARIANTS.items():
    base = os.path.join(ROOT, name)
    core = os.path.join(base, 'assets', 'minecraft', 'shaders', 'core')
    os.makedirs(core, exist_ok=True)
    defines = '\n'.join('#define ' + f for f in flags)
    wet = 'BLURRED_WET' in flags
    with open(os.path.join(core, 'terrain.vsh'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(VSH.format(defines=defines))
    with open(os.path.join(core, 'terrain.fsh'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(FSH_HEAD.format(defines=defines))
        if wet:
            f.write(FSH_WET)
        f.write(FSH_MAIN.format(wet_call='    color = blurredWet(color);' if wet else ''))
    meta = {'pack': {'description': 'Blurred world shaders (' + ', '.join(flags).lower().replace('blurred_', '') + ')',
                     'pack_format': 75, 'min_format': 75, 'max_format': 75}}
    with open(os.path.join(base, 'pack.mcmeta'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump(meta, f, indent=2)
        f.write('\n')
print('wrote', len(VARIANTS), 'packs')
