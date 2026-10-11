#version 330

// Blurred Ambience: puts it together like a shader pack. Ambient occlusion,
// bloom, distance haze, filmic (ACES) tone mapping, then the look's colour
// grade, vignette and optional letterbox.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
uniform sampler2D AoSampler;
uniform sampler2D BloomSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform LookConfig {
    vec4 Mix;      // ao strength, bloom amount, haze amount, haze density
    vec4 Tone;     // exposure, saturation, contrast, warmth
    vec4 Frame;    // vignette, letterbox, tint amount, sky boost
    vec4 Tint;     // rgb
    vec4 Haze;     // rgb
};

out vec4 fragColor;

const float NEAR = 0.05;
const float FAR = 1024.0;

float linearDepth(float d) {
    float z = d * 2.0 - 1.0;
    return (2.0 * NEAR * FAR) / (FAR + NEAR - z * (FAR - NEAR));
}

vec3 aces(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

// The game's colours are gamma-encoded; light adds up (and tone maps)
// properly only in linear space.
vec3 toLinear(vec3 c) {
    return pow(max(c, 0.0), vec3(2.2));
}

vec3 toGamma(vec3 c) {
    return pow(max(c, 0.0), vec3(1.0 / 2.2));
}

void main() {
    vec3 c = toLinear(texture(InSampler, texCoord).rgb);
    float d = texture(DepthSampler, texCoord).r;
    bool sky = d >= 0.99999;
    if (!sky) {
        float ao = texture(AoSampler, texCoord).r;
        c *= mix(1.0, ao, Mix.x);
        float z = linearDepth(d);
        float haze = (1.0 - exp(-z * Mix.w)) * Mix.z;
        float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
        c = mix(c, toLinear(Haze.rgb) * (0.35 + 0.65 * sqrt(luma)), haze);
    } else {
        c *= Frame.w;
    }
    // Bloom is already linear (see bright.fsh).
    c += texture(BloomSampler, texCoord).rgb * Mix.y;
    c *= Tone.x;
    c *= vec3(1.0 + Tone.w * 0.08, 1.0, 1.0 - Tone.w * 0.08);
    c = toGamma(aces(c));
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(vec3(luma), c, Tone.y);
    c = (c - 0.5) * Tone.z + 0.5;
    c = mix(c, c * Tint.rgb, Frame.z);
    vec2 v = (texCoord - 0.5) * vec2(1.0, InSize.y / InSize.x) * 1.4;
    c *= mix(1.0, smoothstep(1.05, 0.25, length(v)), Frame.x);
    if (texCoord.y < Frame.y || texCoord.y > 1.0 - Frame.y) {
        c = vec3(0.0);
    }
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
