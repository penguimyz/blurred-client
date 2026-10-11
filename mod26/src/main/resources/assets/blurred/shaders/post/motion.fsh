#version 330

// Blurred motion blur: each pixel is smeared along the path its point took on
// screen since the last frame (reprojected from the depth buffer with last
// frame's camera), like a shader pack's camera motion blur. Your hand and
// things right in front of the camera stay sharp.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BlurredMotion {
    mat4 Reproject;
    vec4 Params; // strength, longest smear (fraction of screen), unused, unused
};

out vec4 fragColor;

const float NEAR = 0.05;
const float FAR = 1024.0;

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    float z = depth * 2.0 - 1.0;
    float linear = (2.0 * NEAR * FAR) / (FAR + NEAR - z * (FAR - NEAR));
    vec4 here = vec4(texCoord * 2.0 - 1.0, z, 1.0);
    vec4 before = Reproject * here;
    vec2 last = before.xy / max(before.w, 0.0001) * 0.5 + 0.5;
    vec2 velocity = (texCoord - last) * Params.x;
    float len = length(velocity);
    if (len > Params.y) {
        velocity *= Params.y / len;
    }
    // Keep the hand and anything within a block or so sharp.
    velocity *= smoothstep(1.0, 2.5, linear);
    vec3 sum = texture(InSampler, texCoord).rgb;
    float weight = 1.0;
    const int SAMPLES = 12;
    float noise = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    for (int i = 1; i <= SAMPLES; i++) {
        float t = (float(i) - noise) / float(SAMPLES) - 0.5;
        vec2 uv = clamp(texCoord + velocity * t, vec2(0.001), vec2(0.999));
        // Don't smear the hand into the scene behind it.
        float zs = texture(DepthSampler, uv).r * 2.0 - 1.0;
        float ls = (2.0 * NEAR * FAR) / (FAR + NEAR - zs * (FAR - NEAR));
        float w = (1.0 - abs(t)) * smoothstep(1.0, 2.0, ls);
        sum += texture(InSampler, uv).rgb * w;
        weight += w;
    }
    fragColor = vec4(sum / weight, 1.0);
}
