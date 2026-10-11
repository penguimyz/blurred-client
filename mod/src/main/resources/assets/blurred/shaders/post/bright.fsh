#version 330

// Blurred Ambience: the bright parts of the image, the source of bloom.
// Output is linear.

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BrightConfig {
    vec4 Params; // threshold, knee, unused, unused
};

out vec4 fragColor;

void main() {
    // Linear light, so the bloom adds up like real light in composite.fsh.
    vec3 c = pow(max(texture(InSampler, texCoord).rgb, 0.0), vec3(2.2));
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    float soft = clamp(luma - Params.x + Params.y, 0.0, 2.0 * Params.y);
    soft = soft * soft / (4.0 * Params.y + 0.0001);
    float contribution = max(soft, luma - Params.x) / max(luma, 0.0001);
    fragColor = vec4(c * contribution, 1.0);
}
