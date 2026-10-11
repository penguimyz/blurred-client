#version 330
#define BLURRED_WET
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

vec4 sampleNearest(sampler2D sampler, vec2 uv, vec2 pixelSize, vec2 du, vec2 dv, vec2 texelScreenSize) {
    vec2 uvTexelCoords = uv / pixelSize;
    vec2 texelCenter = round(uvTexelCoords) - 0.5f;
    vec2 texelOffset = uvTexelCoords - texelCenter;
    texelOffset = (texelOffset - 0.5f) * pixelSize / texelScreenSize + 0.5f;
    texelOffset = clamp(texelOffset, 0.0f, 1.0f);
    uv = (texelCenter + texelOffset) * pixelSize;
    return textureGrad(sampler, uv, du, dv);
}

vec4 sampleNearest(sampler2D source, vec2 uv, vec2 pixelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);
    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    return sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);
}

vec4 sampleRGSS(sampler2D source, vec2 uv, vec2 pixelSize) {
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
    for (int i = 0; i < 4; ++i) {
        vec2 sampleUV = uv + offsets[i] * pixelSize;
        rgssColorLow += textureLod(source, sampleUV, mipLevelLow);
        rgssColorHigh += textureLod(source, sampleUV, mipLevelHigh);
    }
    rgssColorLow *= 0.25;
    rgssColorHigh *= 0.25;
    vec4 rgssColor = mix(rgssColorLow, rgssColorHigh, mipBlend);
    vec4 nearestColor = sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);
    return mix(nearestColor, rgssColor, blendFactor);
}

void main() {
    vec4 color = (UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize)) * vertexColor;
    color = mix(FogColor * vec4(1, 1, 1, color.a), color, ChunkVisibility);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    color = blurredWet(color);
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
