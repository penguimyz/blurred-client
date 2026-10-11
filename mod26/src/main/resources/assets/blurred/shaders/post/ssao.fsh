#version 330

// Blurred Ambience: screen-space ambient occlusion from the depth buffer.
// Darkens creases, corners and where things meet the ground.

uniform sampler2D DepthSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform AoConfig {
    vec4 Params; // radius (blocks), strength, unused, unused
};

out vec4 fragColor;

const float NEAR = 0.05;
const float FAR = 1024.0;

float linearDepth(float d) {
    float z = d * 2.0 - 1.0;
    return (2.0 * NEAR * FAR) / (FAR + NEAR - z * (FAR - NEAR));
}

void main() {
    float d = texture(DepthSampler, texCoord).r;
    if (d >= 0.99999) {
        fragColor = vec4(1.0);
        return;
    }
    float z = linearDepth(d);
    // Interleaved gradient noise, to rotate the sample pattern per pixel.
    float noise = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float radius = clamp(Params.x * 1.3 / z, 0.0015, 0.06);
    vec2 aspect = vec2(OutSize.y / OutSize.x, 1.0);
    float occlusion = 0.0;
    const int SAMPLES = 14;
    for (int i = 0; i < SAMPLES; i++) {
        float angle = (float(i) + noise) * 2.3999632;
        float r = radius * sqrt((float(i) + 0.5) / float(SAMPLES));
        vec2 offset = vec2(cos(angle), sin(angle)) * r * aspect;
        float zs = linearDepth(texture(DepthSampler, texCoord + offset).r);
        float diff = z - zs;
        // Closer samples occlude, but not ones far in front (that's a different object).
        occlusion += smoothstep(0.02, 0.15, diff) * (1.0 - smoothstep(0.6, 2.0, diff));
    }
    float ao = 1.0 - occlusion / float(SAMPLES) * Params.y;
    fragColor = vec4(vec3(clamp(ao, 0.0, 1.0)), 1.0);
}
